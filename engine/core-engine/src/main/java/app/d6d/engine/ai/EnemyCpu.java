package app.d6d.engine.ai;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.AbilityEffect;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.AreaSpellResult;
import app.d6d.domain.combat.AreaTargetResult;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.AttackOutcome;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.CombatantState;
import app.d6d.domain.combat.ConditionType;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.HealingDefinition;
import app.d6d.domain.combat.HealingTarget;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.SpellSlotResourceId;
import app.d6d.domain.combat.TurnBudget;
import app.d6d.domain.space.BattleMap;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import app.d6d.domain.space.TokenPlacement;
import app.d6d.engine.CombatSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * CPU deterministica che controlla soltanto lo schieramento avversario.
 *
 * <p>Il pianificatore legge esclusivamente {@link CombatState}; l'esecutore usa
 * soltanto i comandi pubblici di {@link CombatSession}. Non modifica tiri, CA,
 * punti ferita o budget in base alla difficolta': i tre livelli cambiano soltanto
 * la qualita' delle scelte.</p>
 */
public final class EnemyCpu {
    public static final int MAX_DECISIONS_PER_ACTOR = 16;

    /** @deprecated il limite e' ora applicato separatamente a ogni attore. */
    @Deprecated
    public static final int MAX_DECISIONS_PER_GROUP = MAX_DECISIONS_PER_ACTOR;

    private static final double SCORE_EPSILON = 1.0e-9;
    private static final String ENEMY_VICTORY = "La squadra nemica ha sconfitto il gruppo";

    /** Premio per un attacco che puo' concludere il bersaglio; il livello ne pesa l'importanza. */
    private static final double KILL_BONUS = 65.0;

    /** Il posizionamento a distanza non dipende dal livello: la gittata utile e' una sola. */
    private static final double RANGED_MOVE_BASE = 180.0;
    private static final double RANGED_MOVE_ERROR_WEIGHT = 4.0;
    private static final double RANGED_MOVE_TRAVEL_WEIGHT = 0.02;

    /** Premi dell'accerchiamento, usati solo dai profili che lo cercano. */
    private static final double SURROUND_ADJACENT_BONUS = 230.0;
    private static final double SURROUND_COVERAGE_WEIGHT = 32.0;
    private static final double SURROUND_FOCUS_BONUS = 90.0;

    private final EnemyCpuDifficulty difficulty;
    private final EnemyCpuProfile profile;

    public EnemyCpu(EnemyCpuDifficulty difficulty) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.profile = EnemyCpuProfile.of(difficulty);
    }

    public EnemyCpuDifficulty difficulty() {
        return difficulty;
    }

    /** Pesi in uso: le formule sono comuni, i numeri no. */
    public EnemyCpuProfile profile() {
        return profile;
    }

    /**
     * Sceglie il prossimo comando senza modificare la sessione.
     *
     * <p>Come l'esecuzione completa, rifiuta turni legacy senza fazioni e non
     * prende mai decisioni per un membro del gruppo. In un turno simultaneo misto
     * puo' invece pianificare il singolo nemico richiesto.</p>
     */
    public EnemyCpuDecision decide(CombatState state, String actorId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actorId, "actorId");
        if (state.status() != CombatStatus.ACTIVE) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.ENCOUNTER_NOT_ACTIVE);
        }
        if (state.partyCombatantIds().isEmpty()) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.FACTIONS_NOT_CONFIGURED);
        }
        List<String> active = state.currentCombatantIds();
        if (!active.contains(actorId)) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.NOT_IN_CURRENT_TURN);
        }
        if (state.partyCombatantIds().contains(actorId)) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.NOT_CONTROLLED);
        }
        if (state.dormant(actorId)) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.ACTOR_DORMANT);
        }
        List<String> activeEnemies = active.stream()
                .filter(id -> !state.partyCombatantIds().contains(id))
                .toList();
        String focus = selectFocusTarget(state, activeEnemies);
        return chooseDecision(state, actorId, focus, new ActorMemory(), Set.of());
    }

    /**
     * Gioca i nemici del gruppo d'iniziativa corrente, tutto in una volta.
     *
     * <p>In un gruppo simultaneo misto agiscono soltanto i nemici e il gruppo resta
     * aperto per il giocatore. In un gruppo interamente nemico il turno viene invece
     * chiuso una volta sola. Il limite e il controllo di revisione sono per attore:
     * un contenuto malformato non blocca l'applicazione e non sottrae il turno agli
     * altri membri del gruppo.</p>
     *
     * <p>E' il ciclo completo di {@link EnemyCpuTurnRunner}: chi deve mostrare il
     * turno un comando alla volta usa invece {@link #startCurrentGroup(CombatSession)}.</p>
     */
    public EnemyCpuResult actCurrentGroup(CombatSession session) {
        EnemyCpuTurnRunner runner = startCurrentGroup(session);
        while (runner.advance()) {
            // Ogni giro applica un solo comando: qui non serve mostrarli.
        }
        return runner.result();
    }

    /**
     * Avvia lo stesso turno di gruppo in forma riprendibile.
     *
     * <p>Il chiamante decide quando eseguire il comando successivo, cosi'
     * l'interfaccia puo' inserire una pausa fra uno e l'altro. Le regole, i limiti
     * e la chiusura del turno sono quelli di {@link #actCurrentGroup(CombatSession)}.</p>
     */
    public EnemyCpuTurnRunner startCurrentGroup(CombatSession session) {
        Objects.requireNonNull(session, "session");
        return new EnemyCpuTurnRunner(this, session);
    }

    EnemyCpuResult resolveEnemyVictory(
            CombatSession session,
            long startingRevision,
            int startingRound,
            int startingTurnIndex,
            List<EnemyCpuActionReport> preceding,
            String focusTargetId) {
        List<EnemyCpuActionReport> reports = new ArrayList<>(preceding);
        CombatState before = session.currentState();
        if (before.status() == CombatStatus.ACTIVE) {
            // La nota di risoluzione non e' testo di presentazione: resta nella
            // storia della sessione, come quella che scriverebbe il tavolo.
            session.resolve(ENEMY_VICTORY);
            reports.add(new EnemyCpuActionReport(
                    EnemyCpuActionType.ENCOUNTER_RESOLVED,
                    "",
                    "",
                    "",
                    0,
                    EnemyCpuReason.ENEMY_VICTORY));
        }
        CombatState ending = session.currentState();
        boolean turnAdvanced = ending.round() != startingRound || ending.turnIndex() != startingTurnIndex;
        return new EnemyCpuResult(
                difficulty,
                reports,
                focusTargetId,
                turnAdvanced,
                ending.status() == CombatStatus.RESOLVED,
                false,
                false,
                false,
                EnemyCpuOutcome.ENEMY_VICTORY,
                startingRevision,
                ending.revision());
    }

    EnemyCpuResult noOp(long revision, EnemyCpuOutcome outcome, boolean waitingForPlayer) {
        return new EnemyCpuResult(
                difficulty,
                List.of(),
                "",
                false,
                false,
                waitingForPlayer,
                false,
                false,
                outcome,
                revision,
                revision);
    }

    EnemyCpuDecision chooseDecision(
            CombatState state,
            String actorId,
            String focusTargetId,
            ActorMemory memory,
            Set<String> rejected) {
        CombatantState actor = state.combatants().get(actorId);
        if (actor == null || actor.defeated() || actor.dead() || incapacitates(actor)) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.ACTOR_CANNOT_ACT);
        }
        // Chi non si e' accorto del gruppo non pianifica: non e' un attore che non
        // trova niente di utile, e' un attore che non sa che c'e' una battaglia.
        if (state.dormant(actorId)) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.ACTOR_DORMANT);
        }
        if (!state.currentCombatantIds().contains(actorId)) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.NOT_IN_CURRENT_TURN);
        }

        List<String> opponents = standingParty(state);
        if (opponents.isEmpty()) {
            return new EnemyCpuDecision.Done(actorId, EnemyCpuReason.NO_OPPONENTS_LEFT);
        }

        List<Candidate> immediate = new ArrayList<>();
        addHealingCandidates(immediate, state, actorId, actor, memory);
        addAttackCandidates(immediate, state, actorId, actor, opponents, focusTargetId, memory);
        addAreaCandidates(immediate, state, actorId, actor, focusTargetId, memory);
        addActivationCandidates(immediate, state, actorId, actor, opponents, memory);
        immediate.removeIf(candidate -> rejected.contains(decisionSignature(candidate.decision)));

        Candidate selected = best(immediate);
        boolean offenseAvailable = immediate.stream().anyMatch(candidate ->
                candidate.decision instanceof EnemyCpuDecision.Attack
                        || candidate.decision instanceof EnemyCpuDecision.AreaAttack
                        || candidate.decision instanceof EnemyCpuDecision.Heal);

        if (!memory.moved) {
            Optional<Candidate> movement = movementCandidate(
                    state, actorId, actor, opponents, focusTargetId, offenseAvailable, memory);
            if (movement.isPresent()
                    && !rejected.contains(decisionSignature(movement.get().decision))
                    && (selected == null || movement.get().score > selected.score + SCORE_EPSILON)) {
                selected = movement.get();
            }
        }

        return selected == null
                ? new EnemyCpuDecision.Done(actorId, EnemyCpuReason.NOTHING_USEFUL)
                : selected.decision;
    }

    private void addHealingCandidates(
            List<Candidate> candidates,
            CombatState state,
            String actorId,
            CombatantState actor,
            ActorMemory memory) {
        for (int abilityIndex = 0; abilityIndex < actor.snapshot().abilities().size(); abilityIndex++) {
            AbilityDefinition ability = actor.snapshot().abilities().get(abilityIndex);
            HealingDefinition healing = ability.healing();
            if (healing == null
                    || ability.passive()
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || ability.resolutionMethod() != ResolutionMethod.AUTOMATIC) {
                continue;
            }
            List<HealingCastOption> castOptions = healingCastOptions(actor, ability, healing).stream()
                    .filter(option -> canAfford(
                            state, actorId, actor, ability, false, memory, option.resourceId()))
                    .toList();
            if (castOptions.isEmpty()) continue;
            for (String targetId : state.rosterOrder()) {
                CombatantState target = state.combatants().get(targetId);
                if (target == null
                        || target.dead()
                        || !sameSide(state, actorId, targetId)
                        || !healingTargetAllows(healing.target(), actorId, targetId)) {
                    continue;
                }
                int missing = target.snapshot().maxHitPoints() - target.currentHitPoints();
                if (missing <= 0 || !withinRange(state, actorId, targetId, ability.rangeFeet())) continue;

                double ratio = hitPointRatio(target);
                EnemyCpuProfile.Healing weights = profile.healing();
                if (!target.defeated() && ratio > weights.dangerRatio()) continue;

                HealingCastOption cast = selectHealingCast(castOptions, healing, target);
                double expected = cast.expectedHealing();
                if (expected <= 0.0) continue;
                double useful = Math.min(expected, missing);
                double score = weights.base()
                        + useful * weights.usefulHealingWeight()
                        + (1.0 - ratio) * weights.woundedWeight()
                        + (target.defeated() ? weights.defeatedBonus() : 0.0)
                        + (isHealer(target) ? weights.healerBonus() : 0.0);
                String key = "heal:" + rosterIndex(state, targetId) + ':'
                        + String.format("%03d", healingResourceRank(ability, cast)) + ':' + abilityIndex
                        + ':' + String.format("%02d", cast.slotLevel()) + ':' + cast.resourceId();
                candidates.add(new Candidate(
                        new EnemyCpuDecision.Heal(
                                actorId,
                                targetId,
                                ability.id(),
                                cast.resourceId(),
                                cast.slotLevel(),
                                target.defeated()
                                        ? EnemyCpuReason.RAISE_ALLY
                                        : EnemyCpuReason.PROTECT_ALLY),
                        score,
                        key));
            }
        }
    }

    private void addAttackCandidates(
            List<Candidate> candidates,
            CombatState state,
            String actorId,
            CombatantState actor,
            List<String> opponents,
            String focusTargetId,
            ActorMemory memory) {
        List<String> easyOrder = new ArrayList<>(opponents);
        easyOrder.sort(Comparator
                .comparingInt((String target) -> distanceForOrdering(state, actorId, target))
                .thenComparingInt(target -> rosterIndex(state, target)));
        Map<String, Integer> easyRank = new HashMap<>();
        for (int index = 0; index < easyOrder.size(); index++) easyRank.put(easyOrder.get(index), index);

        for (int abilityIndex = 0; abilityIndex < actor.snapshot().abilities().size(); abilityIndex++) {
            AbilityDefinition ability = actor.snapshot().abilities().get(abilityIndex);
            if (ability.passive()
                    || ability.isArea()
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || ability.resolutionMethod() != ResolutionMethod.ATTACK_ROLL
                    || ability.damage().isEmpty()
                    || !canAfford(state, actorId, actor, ability, true, memory)) {
                continue;
            }
            for (String targetId : opponents) {
                if (!withinRange(state, actorId, targetId, ability.rangeFeet())) continue;
                CombatantState target = state.combatant(targetId);
                double expected = expectedAttackDamage(actor, ability, target);
                if (expected <= 0.0) continue;
                double hpRatio = survivabilityRatio(target);
                double killBonus = expected + SCORE_EPSILON >= hitPointsToDefeat(target) ? KILL_BONUS : 0.0;
                EnemyCpuProfile.Attack weights = profile.attack();
                double score = weights.base()
                        + expected * weights.expectedDamageWeight()
                        + killBonus * weights.killBonusWeight()
                        + (1.0 - hpRatio) * weights.woundedWeight()
                        + adjacentEnemyCount(state, targetId, actorId) * weights.surroundedWeight()
                        + (targetId.equals(focusTargetId) ? weights.focusBonus() : 0.0)
                        + (isHealer(target) ? weights.healerBonus() : 0.0)
                        - easyRank.getOrDefault(targetId, 1_000) * weights.nearestFirstWeight()
                        - abilityIndex * weights.abilityOrderWeight();
                String key = "attack:" + rosterIndex(state, targetId) + ':' + abilityIndex;
                candidates.add(new Candidate(
                        new EnemyCpuDecision.Attack(
                                actorId,
                                targetId,
                                ability.id(),
                                targetId.equals(focusTargetId)
                                        ? EnemyCpuReason.FOCUS_FIRE
                                        : EnemyCpuReason.BEST_ATTACK),
                        score,
                        key));
            }
        }
    }

    private void addAreaCandidates(
            List<Candidate> candidates,
            CombatState state,
            String actorId,
            CombatantState actor,
            String focusTargetId,
            ActorMemory memory) {
        if (!state.battleMap().configured() || !state.battleMap().isPlaced(actorId)) return;
        for (int abilityIndex = 0; abilityIndex < actor.snapshot().abilities().size(); abilityIndex++) {
            AbilityDefinition ability = actor.snapshot().abilities().get(abilityIndex);
            if (ability.passive()
                    || !ability.isArea()
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || !hasAutomatedAreaResolution(ability)
                    || ability.damage().isEmpty()
                    || !canAfford(state, actorId, actor, ability, false, memory)) {
                continue;
            }
            AreaChoice choice = bestAreaChoice(
                    state, actorId, ability, abilityIndex, focusTargetId, true);
            if (choice == null) continue;
            candidates.add(new Candidate(
                    new EnemyCpuDecision.AreaAttack(
                            actorId,
                            choice.center(),
                            ability.id(),
                            EnemyCpuReason.AREA_COVERAGE),
                    choice.score(),
                    "area:" + abilityIndex + ':' + positionKey(choice.center())));
        }
    }

    private double areaCandidateScore(AreaEvaluation evaluation, int abilityIndex) {
        if (evaluation.enemyHits == 0 || evaluation.enemyDamage <= 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        EnemyCpuProfile.Area weights = profile.area();
        if (weights.refusesFriendlyFire() && evaluation.friendlyHits > 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = weights.base()
                + (evaluation.enemyHits >= 2 ? weights.multiTargetBonus() : 0.0)
                + evaluation.enemyDamage * weights.damageWeight()
                + evaluation.enemyHits * weights.hitWeight()
                - evaluation.friendlyDamage * weights.friendlyDamagePenalty()
                - evaluation.friendlyHits * weights.friendlyHitPenalty()
                + (evaluation.containsFocus ? weights.focusBonus() : 0.0)
                - abilityIndex * weights.abilityOrderWeight();
        return weights.requiresPositiveScore() && score <= 0.0
                ? Double.NEGATIVE_INFINITY
                : score;
    }

    private void addActivationCandidates(
            List<Candidate> candidates,
            CombatState state,
            String actorId,
            CombatantState actor,
            List<String> opponents,
            ActorMemory memory) {
        if (!profile.usesActivations() || opponents.isEmpty()) return;
        for (int abilityIndex = 0; abilityIndex < actor.snapshot().abilities().size(); abilityIndex++) {
            AbilityDefinition ability = actor.snapshot().abilities().get(abilityIndex);
            if (ability.passive()
                    || ability.effect() == AbilityEffect.NONE
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || ability.resolutionMethod() != ResolutionMethod.AUTOMATIC
                    || !canAfford(state, actorId, actor, ability, false, memory)
                    || !activationEnablesUsefulAction(state, actorId, actor, opponents, ability, memory)) {
                continue;
            }
            candidates.add(new Candidate(
                    new EnemyCpuDecision.Activate(
                            actorId,
                            ability.id(),
                            EnemyCpuReason.EXTRA_PRESSURE),
                    profile.activationScore(),
                    "activate:" + abilityIndex));
        }
    }

    /**
     * Un'azione aggiuntiva viene comprata soltanto quando l'azione ordinaria e'
     * gia' terminata e il kit contiene davvero qualcosa di non magico da farne.
     * In questo modo l'attivazione non precede alla cieca un attacco che potrebbe
     * poi risultare fuori gittata, immune o privo della risorsa necessaria.
     */
    private boolean activationEnablesUsefulAction(
            CombatState state,
            String actorId,
            CombatantState actor,
            List<String> opponents,
            AbilityDefinition activation,
            ActorMemory memory) {
        if (activation.effect() != AbilityEffect.GRANT_NON_MAGIC_ACTION) return false;
        TurnBudget budget = state.turnBudgets().get(actorId);
        if (budget == null
                || budget.actionAvailable()
                || budget.additionalActionAvailable()
                || budget.actionSurgeUsedThisTurn()
                || budget.attackActionInProgress()) {
            return false;
        }

        List<AbilityDefinition> abilities = actor.snapshot().abilities();
        for (int abilityIndex = 0; abilityIndex < abilities.size(); abilityIndex++) {
            AbilityDefinition ability = abilities.get(abilityIndex);
            if (ability.passive()
                    || ability.spellOrCantrip()
                    || ability.activationCost() != ActivationCost.ACTION
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || !resourceAvailableAfterActivation(actor, ability, activation)
                    || (SpellSlotResourceId.parse(ability.resourceId()).isPresent()
                            && budget.spellSlotSpentThisTurn())) {
                continue;
            }

            if (!ability.isArea()
                    && ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL
                    && !ability.damage().isEmpty()
                    && opponents.stream().anyMatch(targetId ->
                            withinRange(state, actorId, targetId, ability.rangeFeet())
                                    && expectedAttackDamage(actor, ability, state.combatant(targetId)) > 0.0)) {
                return true;
            }

            if (ability.isArea()
                    && hasAutomatedAreaResolution(ability)
                    && !ability.damage().isEmpty()
                    && state.battleMap().configured()
                    && state.battleMap().isPlaced(actorId)) {
                if (bestAreaChoice(
                        state, actorId, ability, abilityIndex, "", true) != null) return true;
            }

            HealingDefinition healing = ability.healing();
            if (healing != null
                    && ability.resolutionMethod() == ResolutionMethod.AUTOMATIC
                    && hasUsefulHealingTargetAfterActivation(
                            state, actorId, actor, ability, healing, activation)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsefulHealingTargetAfterActivation(
            CombatState state,
            String actorId,
            CombatantState actor,
            AbilityDefinition ability,
            HealingDefinition healing,
            AbilityDefinition activation) {
        boolean payable = healingCastOptions(actor, ability, healing).stream().anyMatch(option ->
                option.expectedHealing() > 0.0
                        && resourceAvailableAfterActivation(
                                actor, ability, option.resourceId(), activation));
        if (!payable) return false;
        for (String targetId : state.rosterOrder()) {
            CombatantState target = state.combatants().get(targetId);
            if (target == null
                    || target.dead()
                    || !sameSide(state, actorId, targetId)
                    || !healingTargetAllows(healing.target(), actorId, targetId)
                    || target.snapshot().maxHitPoints() <= target.currentHitPoints()
                    || (!target.defeated() && hitPointRatio(target) > profile.healing().dangerRatio())
                    || !withinRange(state, actorId, targetId, ability.rangeFeet())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean resourceAvailableAfterActivation(
            CombatantState actor,
            AbilityDefinition ability,
            AbilityDefinition activation) {
        return resourceAvailableAfterActivation(actor, ability, ability.resourceId(), activation);
    }

    private static boolean resourceAvailableAfterActivation(
            CombatantState actor,
            AbilityDefinition ability,
            String resourceId,
            AbilityDefinition activation) {
        if (ability.resourceCost() == 0) return true;
        int remaining = actor.resource(resourceId).map(CombatResourceState::remaining).orElse(0);
        if (activation.resourceCost() > 0 && activation.resourceId().equals(resourceId)) {
            remaining -= activation.resourceCost();
        }
        return remaining >= ability.resourceCost();
    }

    private Optional<Candidate> movementCandidate(
            CombatState state,
            String actorId,
            CombatantState actor,
            List<String> opponents,
            String focusTargetId,
            boolean usefulImmediateAction,
            ActorMemory memory) {
        BattleMap map = state.battleMap();
        TurnBudget budget = state.turnBudgets().get(actorId);
        TokenPlacement current = map.placementOf(actorId).orElse(null);
        if (!map.configured() || current == null || budget == null || budget.movementRemainingFeet() <= 0) {
            return Optional.empty();
        }

        String targetId = movementTarget(state, actorId, opponents, focusTargetId);
        TokenPlacement target = map.placementOf(targetId).orElse(null);
        if (target == null) return Optional.empty();

        boolean hasMelee = hasAutomatedMeleeAttack(state, actorId, actor, memory);
        MapGrid grid = map.grid();
        int currentDistance = grid.feetFor(current.squaresTo(target));
        Map<String, Boolean> tacticalAreas = new HashMap<>();
        int rangedRange = Math.max(
                bestAutomatedRangedRange(
                        state, actorId, actor, memory, focusTargetId, tacticalAreas),
                bestAutomatedRangedHealingRange(state, actorId, actor, memory));
        boolean rangedPositioning = prefersRangedPositioning(
                state,
                actorId,
                actor,
                targetId,
                focusTargetId,
                hasMelee,
                rangedRange,
                memory,
                tacticalAreas);
        boolean meleePositioning = hasMelee && !rangedPositioning;
        EnemyCpuProfile.Movement weights = profile.movement();
        boolean seekSurround = weights.seeksSurround() && meleePositioning;
        int meleeRange = meleePositioning ? bestMeleeRange(state, actorId, actor, memory) : 0;

        // Chi combatte a distanza non avanza dopo aver gia' eseguito una buona
        // azione. Si muove soltanto per entrare in gittata o per ricreare una
        // distanza prudente quando il bersaglio gli e' arrivato addosso.
        int preferredRangedDistance = rangedPositioning ? Math.min(30, rangedRange) : 0;
        if (rangedPositioning
                && currentDistance >= preferredRangedDistance
                && currentDistance <= rangedRange) {
            return Optional.empty();
        }
        if (!meleePositioning && !rangedPositioning) return Optional.empty();
        if (usefulImmediateAction && !seekSurround) return Optional.empty();
        boolean currentlyAdjacent = meleePositioning && currentDistance <= meleeRange;
        if (currentlyAdjacent && !seekSurround) {
            return Optional.empty();
        }

        int maxSquares = budget.movementRemainingFeet() / grid.feetPerSquare();
        if (weights.halvesDistance()) {
            maxSquares = Math.max(1, maxSquares / 2);
        }
        if (maxSquares <= 0) return Optional.empty();

        Candidate best = null;
        double currentSurround = seekSurround
                ? surroundScore(state, actorId, targetId, current, meleeRange)
                : 0.0;
        int rangedGoal = currentDistance < preferredRangedDistance
                ? preferredRangedDistance
                : rangedRange;
        int currentRangedError = rangedPositioning
                ? Math.abs(currentDistance - rangedGoal)
                : Integer.MAX_VALUE;
        int minimumColumn = Math.max(0, current.origin().column() - maxSquares);
        int maximumColumn = Math.min(grid.columns() - 1, current.origin().column() + maxSquares);
        int minimumRow = Math.max(0, current.origin().row() - maxSquares);
        int maximumRow = Math.min(grid.rows() - 1, current.origin().row() + maxSquares);
        for (int column = minimumColumn; column <= maximumColumn; column++) {
            for (int row = minimumRow; row <= maximumRow; row++) {
                GridPosition destination = new GridPosition(column, row);
                if (destination.equals(current.origin())) continue;
                int travelSquares = current.origin().squaresTo(destination);
                int feet = grid.feetFor(travelSquares);
                if (feet > budget.movementRemainingFeet()) continue;
                TokenPlacement moved = current.movedTo(destination);
                if (!map.fitsInsideGrid(moved) || !map.isFree(moved)) continue;

                int distanceFeet = grid.feetFor(moved.squaresTo(target));
                if (meleePositioning && !seekSurround && distanceFeet >= currentDistance) continue;
                if (rangedPositioning
                        && Math.abs(distanceFeet - rangedGoal) >= currentRangedError) {
                    continue;
                }

                double score;
                if (rangedPositioning) {
                    score = RANGED_MOVE_BASE
                            - Math.abs(distanceFeet - rangedGoal) * RANGED_MOVE_ERROR_WEIGHT
                            - feet * RANGED_MOVE_TRAVEL_WEIGHT;
                } else {
                    score = weights.base()
                            - distanceFeet * weights.distanceWeight()
                            - feet * weights.travelWeight();
                    if (seekSurround) {
                        boolean adjacent = distanceFeet <= meleeRange;
                        double candidateSurround = surroundScore(
                                state, actorId, targetId, moved, meleeRange);
                        if (currentlyAdjacent
                                && (!adjacent || candidateSurround <= currentSurround + SCORE_EPSILON)) {
                            continue;
                        }
                        score += adjacent ? SURROUND_ADJACENT_BONUS : 0.0;
                        score += candidateSurround * SURROUND_COVERAGE_WEIGHT;
                        if (targetId.equals(focusTargetId)) score += SURROUND_FOCUS_BONUS;
                    }
                }
                Candidate candidate = new Candidate(
                        new EnemyCpuDecision.Move(
                                actorId,
                                destination,
                                seekSurround
                                        ? EnemyCpuReason.SURROUND_TARGET
                                        : EnemyCpuReason.CLOSE_DISTANCE),
                        score,
                        "move:" + positionKey(destination));
                if (better(candidate, best)) best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    List<EnemyCpuActionReport> execute(CombatSession session, EnemyCpuDecision decision) {
        if (decision instanceof EnemyCpuDecision.Move move) {
            int feet = session.moveCombatant(move.actorId(), move.destination());
            return List.of(new EnemyCpuActionReport(
                    EnemyCpuActionType.MOVE,
                    move.actorId(),
                    "",
                    "",
                    feet,
                    move.reason()));
        }
        if (decision instanceof EnemyCpuDecision.Attack attack) {
            AttackResult result = session.attack(AttackRequest.digital(
                    attack.actorId(), attack.targetId(), attack.abilityId(), D20Mode.NORMAL));
            int damage = result.damageResult().map(value -> value.totalAdjustedDamage()).orElse(0);
            return List.of(new EnemyCpuActionReport(
                    EnemyCpuActionType.ATTACK,
                    attack.actorId(),
                    attack.targetId(),
                    attack.abilityId(),
                    damage,
                    attack.reason(),
                    result.outcome() != AttackOutcome.MISS));
        }
        if (decision instanceof EnemyCpuDecision.AreaAttack area) {
            AreaSpellResult result = session.castArea(area.actorId(), area.center(), area.abilityId());
            long totalDamage = result.targets().stream()
                    .map(AreaTargetResult::damage)
                    .flatMap(Optional::stream)
                    .mapToLong(value -> value.totalAdjustedDamage())
                    .sum();
            int reportedDamage = (int) Math.min(Integer.MAX_VALUE, totalDamage);
            String targets = result.targets().stream().map(AreaTargetResult::targetId).reduce(
                    (first, second) -> first + "," + second).orElse("");
            List<EnemyCpuTargetReport> targetReports = result.targets().stream()
                    .map(target -> new EnemyCpuTargetReport(
                            target.targetId(),
                            target.damage().map(value -> value.totalAdjustedDamage()).orElse(0),
                            target.saved()))
                    .toList();
            return List.of(new EnemyCpuActionReport(
                    EnemyCpuActionType.AREA_ATTACK,
                    area.actorId(),
                    targets,
                    area.abilityId(),
                    reportedDamage,
                    area.reason(),
                    targetReports));
        }
        if (decision instanceof EnemyCpuDecision.Heal heal) {
            int restored = heal.slotLevel() > 0
                    ? session.useHealingAbility(
                            heal.actorId(), heal.targetId(), heal.abilityId(), heal.resourceId())
                    : session.useHealingAbility(heal.actorId(), heal.targetId(), heal.abilityId());
            return List.of(new EnemyCpuActionReport(
                    EnemyCpuActionType.HEAL,
                    heal.actorId(),
                    heal.targetId(),
                    heal.abilityId(),
                    restored,
                    heal.reason(),
                    "",
                    List.of(),
                    false,
                    heal.resourceId(),
                    heal.slotLevel()));
        }
        if (decision instanceof EnemyCpuDecision.Activate activate) {
            session.activateAbility(activate.actorId(), activate.abilityId());
            return List.of(new EnemyCpuActionReport(
                    EnemyCpuActionType.ACTIVATE,
                    activate.actorId(),
                    "",
                    activate.abilityId(),
                    0,
                    activate.reason()));
        }
        throw new IllegalArgumentException("A Done decision cannot be executed");
    }

    String selectFocusTarget(CombatState state, List<String> activeEnemies) {
        List<String> targets = standingParty(state);
        if (targets.isEmpty()) return "";
        EnemyCpuProfile.Focus weights = profile.focus();
        if (weights.nearestOnly()) {
            String actor = activeEnemies.isEmpty() ? "" : activeEnemies.get(0);
            return targets.stream()
                    .min(Comparator
                            .comparingInt((String target) -> distanceForOrdering(state, actor, target))
                            .thenComparingInt(target -> rosterIndex(state, target)))
                    .orElse(targets.get(0));
        }

        String best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String targetId : targets) {
            CombatantState target = state.combatant(targetId);
            double ratio = survivabilityRatio(target);
            double averageDistance = activeEnemies.stream()
                    .mapToInt(actor -> distanceForOrdering(state, actor, targetId))
                    .average()
                    .orElse(0.0);
            double score = weights.lowHitPointsWeight() / Math.max(1L, hitPointsToDefeat(target))
                    + (1.0 - ratio) * weights.woundedWeight()
                    + adjacentEnemyCount(state, targetId, "") * weights.surroundedWeight()
                    + threatRating(target) * weights.threatWeight()
                    + (isHealer(target) ? weights.healerBonus() : 0.0)
                    + (target.concentration() != null ? weights.concentrationBonus() : 0.0)
                    - averageDistance * weights.distancePenalty();
            if (score > bestScore + SCORE_EPSILON
                    || (Math.abs(score - bestScore) <= SCORE_EPSILON
                            && rosterIndex(state, targetId) < rosterIndex(state, best))) {
                best = targetId;
                bestScore = score;
            }
        }
        return best == null ? targets.get(0) : best;
    }

    private String movementTarget(
            CombatState state,
            String actorId,
            List<String> opponents,
            String focusTargetId) {
        // Chi non insegue semplicemente il piu' vicino punta al bersaglio del gruppo.
        if (!profile.focus().nearestOnly()
                && focusTargetId != null
                && !focusTargetId.isBlank()
                && opponents.contains(focusTargetId)) {
            return focusTargetId;
        }
        return opponents.stream()
                .min(Comparator
                        .comparingInt((String target) -> distanceForOrdering(state, actorId, target))
                        .thenComparingInt(target -> rosterIndex(state, target)))
                .orElse(opponents.get(0));
    }

    /** Forme d'area che {@link CombatSession#castArea(String, GridPosition, String)} risolve da solo. */
    private static boolean hasAutomatedAreaResolution(AbilityDefinition ability) {
        return ability.resolutionMethod() == ResolutionMethod.SAVING_THROW
                || (ability.resolutionMethod() == ResolutionMethod.AUTOMATIC
                        && !ability.hasSavingThrow());
    }

    /**
     * Trova esattamente il centro migliore senza creare un {@link Candidate} per
     * ogni casella. Ogni sagoma contribuisce, riga per riga, a un intervallo di
     * centri: somme prefisse trasformano quindi il costo da
     * O(centri * combattenti) a O(combattenti * raggio + griglia).
     */
    private AreaChoice bestAreaChoice(
            CombatState state,
            String actorId,
            AbilityDefinition ability,
            int abilityIndex,
            String focusTargetId,
            boolean enforceCasterRange) {
        BattleMap map = state.battleMap();
        TokenPlacement caster = map.placementOf(actorId).orElse(null);
        if (!map.configured() || caster == null) return null;

        MapGrid grid = map.grid();
        int columns = grid.columns();
        int rows = grid.rows();
        int stride = columns + 1;
        int cells = Math.multiplyExact(rows, stride);
        int[] enemyHits = new int[cells];
        int[] friendlyHits = new int[cells];
        int[] standingEnemyHits = new int[cells];
        int[] focusHits = new int[cells];
        double[] enemyDamage = new double[cells];
        double[] friendlyDamage = new double[cells];
        double radiusSquares = (double) ability.areaRadiusFeet() / grid.feetPerSquare();
        CombatantState casterState = state.combatant(actorId);

        for (TokenPlacement placement : map.orderedPlacements()) {
            CombatantState target = state.combatants().get(placement.combatantId());
            if (target == null || target.dead()) continue;
            boolean enemy = state.partyCombatantIds().contains(placement.combatantId());
            addAreaContribution(
                    grid,
                    stride,
                    placement,
                    radiusSquares,
                    enemy,
                    enemy && !target.defeated(),
                    placement.combatantId().equals(focusTargetId),
                    expectedAreaDamage(casterState, ability, target),
                    enemyHits,
                    friendlyHits,
                    standingEnemyHits,
                    focusHits,
                    enemyDamage,
                    friendlyDamage);
        }

        prefixAreaRows(
                rows,
                columns,
                stride,
                enemyHits,
                friendlyHits,
                standingEnemyHits,
                focusHits,
                enemyDamage,
                friendlyDamage);

        int minimumColumn = 0;
        int maximumColumn = columns - 1;
        int minimumRow = 0;
        int maximumRow = rows - 1;
        if (enforceCasterRange) {
            int rangeSquares = ability.rangeFeet() / grid.feetPerSquare();
            minimumColumn = (int) Math.max(
                    0L, (long) caster.origin().column() - rangeSquares);
            maximumColumn = (int) Math.min(
                    columns - 1L,
                    (long) caster.origin().column() + caster.squaresPerSide() - 1L + rangeSquares);
            minimumRow = (int) Math.max(
                    0L, (long) caster.origin().row() - rangeSquares);
            maximumRow = (int) Math.min(
                    rows - 1L,
                    (long) caster.origin().row() + caster.squaresPerSide() - 1L + rangeSquares);
        }

        AreaChoice best = null;
        // Colonna e poi riga conserva lo stesso tie-break di positionKey().
        for (int column = minimumColumn; column <= maximumColumn; column++) {
            for (int row = minimumRow; row <= maximumRow; row++) {
                int index = row * stride + column;
                // I vecchi centri erano generati soltanto attorno a membri del
                // party ancora in piedi; i caduti inclusi incidentalmente
                // nell'area continuano invece a comparire nella valutazione.
                if (standingEnemyHits[index] == 0) continue;
                AreaEvaluation evaluation = new AreaEvaluation(
                        enemyHits[index],
                        friendlyHits[index],
                        enemyDamage[index],
                        friendlyDamage[index],
                        focusHits[index] > 0);
                double score = areaCandidateScore(evaluation, abilityIndex);
                if (!Double.isFinite(score)) continue;
                if (best == null || score > best.score() + SCORE_EPSILON) {
                    best = new AreaChoice(new GridPosition(column, row), score);
                }
            }
        }
        return best;
    }

    /** Aggiunge a campi-differenza tutti i centri che intersecano una sagoma. */
    private static void addAreaContribution(
            MapGrid grid,
            int stride,
            TokenPlacement placement,
            double radiusSquares,
            boolean enemy,
            boolean standingEnemy,
            boolean focus,
            double damage,
            int[] enemyHits,
            int[] friendlyHits,
            int[] standingEnemyHits,
            int[] focusHits,
            double[] enemyDamage,
            double[] friendlyDamage) {
        double expandedRadius = radiusSquares + 1.0e-9;
        double radiusSquared = expandedRadius * expandedRadius;
        int maximumReach = Math.min(
                Math.max(grid.columns(), grid.rows()),
                (int) Math.floor(expandedRadius));
        int left = placement.origin().column();
        int right = left + placement.squaresPerSide() - 1;
        int top = placement.origin().row();
        int bottom = top + placement.squaresPerSide() - 1;
        int minimumRow = (int) Math.max(0L, (long) top - maximumReach);
        int maximumRow = (int) Math.min(grid.rows() - 1L, (long) bottom + maximumReach);

        for (int row = minimumRow; row <= maximumRow; row++) {
            int verticalDistance = row < top ? top - row : row > bottom ? row - bottom : 0;
            double horizontalSquared = radiusSquared
                    - (double) verticalDistance * verticalDistance;
            if (horizontalSquared < 0.0) continue;
            int horizontalReach = Math.min(
                    grid.columns(),
                    (int) Math.floor(Math.sqrt(horizontalSquared)));
            int from = (int) Math.max(0L, (long) left - horizontalReach);
            int through = (int) Math.min(
                    grid.columns() - 1L, (long) right + horizontalReach);
            int start = row * stride + from;
            int end = row * stride + through + 1;
            if (enemy) {
                enemyHits[start]++;
                enemyHits[end]--;
                enemyDamage[start] += damage;
                enemyDamage[end] -= damage;
                if (standingEnemy) {
                    standingEnemyHits[start]++;
                    standingEnemyHits[end]--;
                }
                if (focus) {
                    focusHits[start]++;
                    focusHits[end]--;
                }
            } else {
                friendlyHits[start]++;
                friendlyHits[end]--;
                friendlyDamage[start] += damage;
                friendlyDamage[end] -= damage;
            }
        }
    }

    private static void prefixAreaRows(
            int rows,
            int columns,
            int stride,
            int[] enemyHits,
            int[] friendlyHits,
            int[] standingEnemyHits,
            int[] focusHits,
            double[] enemyDamage,
            double[] friendlyDamage) {
        for (int row = 0; row < rows; row++) {
            int runningEnemyHits = 0;
            int runningFriendlyHits = 0;
            int runningStandingEnemyHits = 0;
            int runningFocusHits = 0;
            double runningEnemyDamage = 0.0;
            double runningFriendlyDamage = 0.0;
            int rowStart = row * stride;
            for (int column = 0; column < columns; column++) {
                int index = rowStart + column;
                runningEnemyHits += enemyHits[index];
                runningFriendlyHits += friendlyHits[index];
                runningStandingEnemyHits += standingEnemyHits[index];
                runningFocusHits += focusHits[index];
                runningEnemyDamage += enemyDamage[index];
                runningFriendlyDamage += friendlyDamage[index];
                enemyHits[index] = runningEnemyHits;
                friendlyHits[index] = runningFriendlyHits;
                standingEnemyHits[index] = runningStandingEnemyHits;
                focusHits[index] = runningFocusHits;
                enemyDamage[index] = runningEnemyDamage;
                friendlyDamage[index] = runningFriendlyDamage;
            }
        }
    }

    private double expectedAttackDamage(
            CombatantState attacker,
            AbilityDefinition ability,
            CombatantState target) {
        int modifier = ability.attackBonus() + attacker.exhaustionD20Penalty();
        boolean disadvantage = attacker.snapshot().strengthDexterityD20Disadvantage()
                && (ability.attackAbility() == SaveAbility.STRENGTH
                        || ability.attackAbility() == SaveAbility.DEXTERITY);
        int normalHits = d20OutcomeCount(disadvantage, natural ->
                natural >= 2
                        && natural <= 19
                        && natural + modifier >= target.snapshot().armorClass());
        int criticalHits = d20OutcomeCount(disadvantage, natural -> natural == 20);
        double samples = disadvantage ? 400.0 : 20.0;
        double normalProbability = normalHits / samples;
        double criticalProbability = criticalHits / samples;
        double normalDamage = expectedAdjustedDamage(ability.damage(), target, false, false, ability.halfOnSave());
        double criticalDamage = expectedAdjustedDamage(ability.damage(), target, true, false, ability.halfOnSave());
        return normalProbability * normalDamage + criticalProbability * criticalDamage;
    }

    private double expectedAreaDamage(
            CombatantState caster,
            AbilityDefinition ability,
            CombatantState target) {
        if (!ability.hasSavingThrow()) {
            return expectedAdjustedDamage(ability.damage(), target, false, false, ability.halfOnSave());
        }
        int bonus = target.snapshot().saveBonus(ability.saveAbility()) + target.exhaustionD20Penalty();
        boolean disadvantage = target.snapshot().strengthDexterityD20Disadvantage()
                && (ability.saveAbility() == SaveAbility.STRENGTH
                        || ability.saveAbility() == SaveAbility.DEXTERITY);
        int successes = d20OutcomeCount(disadvantage,
                natural -> natural + bonus >= caster.snapshot().spellSaveDc());
        double saveProbability = successes / (disadvantage ? 400.0 : 20.0);
        double full = expectedAdjustedDamage(ability.damage(), target, false, false, ability.halfOnSave());
        double saved = expectedAdjustedDamage(ability.damage(), target, false, true, ability.halfOnSave());
        return (1.0 - saveProbability) * full + saveProbability * saved;
    }

    private static int d20OutcomeCount(boolean disadvantage, IntPredicate outcome) {
        int matches = 0;
        if (!disadvantage) {
            for (int natural = 1; natural <= 20; natural++) {
                if (outcome.test(natural)) matches++;
            }
            return matches;
        }
        for (int first = 1; first <= 20; first++) {
            for (int second = 1; second <= 20; second++) {
                if (outcome.test(Math.min(first, second))) matches++;
            }
        }
        return matches;
    }

    private static double expectedAdjustedDamage(
            List<DamageFormula> formulas,
            CombatantState target,
            boolean critical,
            boolean saved,
            boolean halfOnSave) {
        double total = 0.0;
        for (DamageFormula formula : formulas) {
            double amount = expectedFormula(formula, critical);
            if (saved) amount = halfOnSave ? Math.floor(amount / 2.0) : 0.0;
            if (target.snapshot().damageImmunities().contains(formula.type())) {
                amount = 0.0;
            } else {
                if (target.snapshot().resistances().contains(formula.type())) amount = Math.floor(amount / 2.0);
                if (target.snapshot().vulnerabilities().contains(formula.type())) amount *= 2.0;
            }
            total += Math.max(0.0, amount);
        }
        return total;
    }

    private static double expectedFormula(DamageFormula formula, boolean critical) {
        if (!formula.usesDice()) return formula.fixedAmount();
        DiceExpression dice = formula.dice();
        int count = dice.count() * (critical ? 2 : 1);
        return Math.max(0.0, (double) count * (dice.sides() + 1) / 2.0 + dice.modifier());
    }

    private static double expectedHealing(HealingDefinition healing) {
        if (!healing.usesDice()) return healing.fixedAmount();
        DiceExpression dice = healing.dice();
        return Math.max(0.0,
                (double) dice.count() * (dice.sides() + 1) / 2.0 + dice.modifier());
    }

    /**
     * Elenca in ordine stabile i modi legali di pagare una cura. Una cura
     * scalabile puo' usare sia slot standard sia slot del Patto, ma mai una
     * risorsa arbitraria o uno slot sotto il proprio livello base.
     */
    private static List<HealingCastOption> healingCastOptions(
            CombatantState actor,
            AbilityDefinition ability,
            HealingDefinition healing) {
        if (!healing.scalesWithSlot()) {
            if (ability.resourceCost() == 0) {
                return List.of(new HealingCastOption("", 0, healing, expectedHealing(healing)));
            }
            CombatResourceState resource = actor.resource(ability.resourceId()).orElse(null);
            if (resource == null || resource.remaining() < ability.resourceCost()) return List.of();
            return List.of(new HealingCastOption(
                    ability.resourceId(), 0, healing, expectedHealing(healing)));
        }

        Optional<SpellSlotResourceId> baseSlot = SpellSlotResourceId.parse(ability.resourceId());
        if (!ability.spellOrCantrip()
                || ability.resourceCost() != 1
                || baseSlot.isEmpty()
                || baseSlot.orElseThrow().level() != healing.slotScaling().baseSlotLevel()) {
            return List.of();
        }

        return actor.resources().stream()
                .map(resource -> Map.entry(resource, SpellSlotResourceId.parse(resource.id())))
                .filter(entry -> entry.getValue().isPresent())
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().orElseThrow()))
                .filter(entry -> entry.getValue().level() >= healing.slotScaling().baseSlotLevel())
                .filter(entry -> entry.getKey().remaining() >= ability.resourceCost())
                .sorted(Comparator
                        .comparingInt((Map.Entry<CombatResourceState, SpellSlotResourceId> entry) ->
                                entry.getValue().level())
                        .thenComparingInt(entry -> entry.getValue().kind().ordinal())
                        .thenComparing(entry -> entry.getKey().id()))
                .map(entry -> {
                    int level = entry.getValue().level();
                    HealingDefinition resolved = healing.resolveAtSlotLevel(level);
                    return new HealingCastOption(
                            entry.getKey().id(), level, resolved, expectedHealing(resolved));
                })
                .toList();
    }

    /** Sceglie lo slot piu' basso che porta il bersaglio fuori dalla fascia di pericolo. */
    private HealingCastOption selectHealingCast(
            List<HealingCastOption> options,
            HealingDefinition baseHealing,
            CombatantState target) {
        if (!baseHealing.scalesWithSlot() || options.size() == 1) return options.get(0);

        int missing = target.snapshot().maxHitPoints() - target.currentHitPoints();
        // La fascia di pericolo e' la stessa che decide se curare: uno slot va speso
        // per uscirne, non per superarla di slancio.
        double safetyRatio = profile.healing().dangerRatio();
        double baseExpected = expectedHealing(baseHealing);
        double safeHitPoints = Math.ceil(target.snapshot().maxHitPoints() * safetyRatio);
        double desiredRecovery = Math.min(
                missing,
                Math.max(baseExpected, safeHitPoints - target.currentHitPoints()));

        for (HealingCastOption option : options) {
            if (option.expectedHealing() + SCORE_EPSILON >= desiredRecovery) return option;
        }
        int highestLevel = options.get(options.size() - 1).slotLevel();
        return options.stream()
                .filter(option -> option.slotLevel() == highestLevel)
                .findFirst()
                .orElseThrow();
    }

    /** Tie-break conservativo: a parita' di utilita' usa prima cure senza consumo. */
    private static int healingResourceRank(AbilityDefinition ability, HealingCastOption cast) {
        if (ability.resourceCost() == 0) return 0;
        if (cast.slotLevel() > 0) return 100 + cast.slotLevel();
        return 50 + Math.min(49, ability.resourceCost());
    }

    private boolean canAfford(
            CombatState state,
            String actorId,
            CombatantState actor,
            AbilityDefinition ability,
            boolean attack,
            ActorMemory memory) {
        return canAfford(state, actorId, actor, ability, attack, memory, ability.resourceId());
    }

    private boolean canAfford(
            CombatState state,
            String actorId,
            CombatantState actor,
            AbilityDefinition ability,
            boolean attack,
            ActorMemory memory,
            String resourceId) {
        if (ability.spellOrCantrip() && actor.snapshot().strengthDexterityD20Disadvantage()) return false;
        if (ability.resourceCost() > 0) {
            int remaining = actor.resource(resourceId).map(resource -> resource.remaining()).orElse(0);
            if (remaining < ability.resourceCost()) return false;
        }
        TurnBudget budget = state.turnBudgets().get(actorId);
        if (budget == null) return false;
        if (ability.resourceCost() > 0
                && SpellSlotResourceId.parse(resourceId).isPresent()
                && budget.spellSlotSpentThisTurn()) {
            return false;
        }
        String onceKey = "ability:" + ability.id();
        return switch (ability.activationCost()) {
            case ACTION -> {
                if (attack && !ability.spellOrCantrip() && budget.attackActionInProgress()) {
                    yield budget.attacksRemaining() > 0;
                }
                yield budget.canUseAction(ability.spellOrCantrip());
            }
            case BONUS_ACTION -> budget.bonusActionAvailable();
            case NONE -> !memory.usedOnce.contains(onceKey);
            case REACTION, LEGENDARY_ACTION -> false;
        };
    }

    private static boolean healingTargetAllows(HealingTarget target, String healerId, String targetId) {
        return switch (target) {
            case SELF -> healerId.equals(targetId);
            case ALLY -> !healerId.equals(targetId);
            case SELF_OR_ALLY -> true;
        };
    }

    private static boolean withinRange(CombatState state, String first, String second, int rangeFeet) {
        return state.distanceFeet(first, second).map(distance -> distance <= rangeFeet).orElse(true);
    }

    private static boolean sameSide(CombatState state, String first, String second) {
        return state.partyCombatantIds().contains(first) == state.partyCombatantIds().contains(second);
    }

    private static List<String> standingParty(CombatState state) {
        return state.rosterOrder().stream()
                .filter(state.partyCombatantIds()::contains)
                .filter(id -> !state.combatant(id).defeated() && !state.combatant(id).dead())
                .toList();
    }

    static boolean hasStandingParty(CombatState state) {
        return state.partyCombatantIds().stream()
                .map(state.combatants()::get)
                .filter(Objects::nonNull)
                .anyMatch(combatant -> !combatant.defeated() && !combatant.dead());
    }

    private static boolean incapacitates(CombatantState combatant) {
        return combatant.conditions().stream().anyMatch(condition ->
                condition.type().equals(ConditionType.INCAPACITATED)
                        || condition.type().equals(ConditionType.PARALYZED)
                        || condition.type().equals(ConditionType.PETRIFIED)
                        || condition.type().equals(ConditionType.STUNNED)
                        || condition.type().equals(ConditionType.UNCONSCIOUS));
    }

    private boolean hasAutomatedMeleeAttack(
            CombatState state,
            String actorId,
            CombatantState actor,
            ActorMemory memory) {
        return actor.snapshot().abilities().stream().anyMatch(ability ->
                !ability.passive()
                        && !ability.isArea()
                        && ability.automationStatus() == AutomationStatus.AUTOMATED
                        && ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL
                        && !ability.damage().isEmpty()
                        && ability.rangeFeet() <= 5
                        && usableOffenseForPositioning(state, actorId, actor, ability, true, memory));
    }

    private int bestMeleeRange(
            CombatState state,
            String actorId,
            CombatantState actor,
            ActorMemory memory) {
        return actor.snapshot().abilities().stream()
                .filter(ability -> !ability.passive())
                .filter(ability -> !ability.isArea())
                .filter(ability -> ability.automationStatus() == AutomationStatus.AUTOMATED)
                .filter(ability -> ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL)
                .filter(ability -> !ability.damage().isEmpty())
                .filter(ability -> ability.rangeFeet() <= 5)
                .filter(ability -> usableOffenseForPositioning(
                        state, actorId, actor, ability, true, memory))
                .mapToInt(AbilityDefinition::rangeFeet)
                .max()
                .orElse(5);
    }

    /** Gittata offensiva utile per una creatura priva di un attacco melee automatico. */
    private int bestAutomatedRangedRange(
            CombatState state,
            String actorId,
            CombatantState actor,
            ActorMemory memory,
            String focusTargetId,
            Map<String, Boolean> tacticalAreas) {
        int best = 0;
        List<AbilityDefinition> abilities = actor.snapshot().abilities();
        for (int abilityIndex = 0; abilityIndex < abilities.size(); abilityIndex++) {
            AbilityDefinition ability = abilities.get(abilityIndex);
            boolean supported = !ability.isArea()
                    ? ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL
                    : hasAutomatedAreaResolution(ability);
            if (ability.passive()
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || ability.damage().isEmpty()
                    || ability.rangeFeet() <= 5
                    || !supported
                    || !usableOffenseForPositioning(
                            state, actorId, actor, ability, !ability.isArea(), memory)
                    || (ability.isArea()
                            && !tacticallyUsableAreaForPositioning(
                                    state,
                                    actorId,
                                    ability,
                                    abilityIndex,
                                    focusTargetId,
                                    tacticalAreas))) {
                continue;
            }
            best = Math.max(best, ability.rangeFeet());
        }
        return best;
    }

    /** Gittata di supporto utile: una cura a distanza non trasforma il guaritore in un assaltatore melee. */
    private int bestAutomatedRangedHealingRange(
            CombatState state,
            String actorId,
            CombatantState actor,
            ActorMemory memory) {
        return actor.snapshot().abilities().stream()
                .filter(ability -> !ability.passive())
                .filter(ability -> ability.automationStatus() == AutomationStatus.AUTOMATED)
                .filter(ability -> ability.resolutionMethod() == ResolutionMethod.AUTOMATIC)
                .filter(ability -> ability.healing() != null)
                .filter(ability -> ability.healing().target() != HealingTarget.SELF)
                .filter(ability -> usableHealingForPositioning(
                        state, actorId, actor, ability, memory))
                .mapToInt(AbilityDefinition::rangeFeet)
                .filter(range -> range > 5)
                .max()
                .orElse(0);
    }

    private boolean usableOffenseForPositioning(
            CombatState state,
            String actorId,
            CombatantState actor,
            AbilityDefinition ability,
            boolean attack,
            ActorMemory memory) {
        // Un'opzione consumata dalla CPU in questo stesso turno continua a
        // descriverne il ruolo: evita che un arciere carichi in melee subito dopo
        // aver tirato. Un'opzione gia' esaurita all'inizio, invece, non conta.
        return canAfford(state, actorId, actor, ability, attack, memory)
                || memory.usedOnce.contains("ability:" + ability.id());
    }

    private boolean usableHealingForPositioning(
            CombatState state,
            String actorId,
            CombatantState actor,
            AbilityDefinition ability,
            ActorMemory memory) {
        if (memory.usedOnce.contains("ability:" + ability.id())) return true;
        HealingDefinition healing = ability.healing();
        boolean payable = healingCastOptions(actor, ability, healing).stream().anyMatch(option ->
                option.expectedHealing() > 0.0
                        && canAfford(
                                state,
                                actorId,
                                actor,
                                ability,
                                false,
                                memory,
                                option.resourceId()));
        if (!payable) return false;

        // Una cura disponibile sulla scheda e' supporto tattico soltanto se puo'
        // essere usata adesso. Altrimenti un ibrido con tutti gli alleati sani
        // resterebbe a distanza pur avendo come unica azione utile il melee.
        for (String targetId : state.rosterOrder()) {
            CombatantState target = state.combatants().get(targetId);
            if (target == null
                    || target.dead()
                    || !sameSide(state, actorId, targetId)
                    || !healingTargetAllows(healing.target(), actorId, targetId)
                    || target.currentHitPoints() >= target.snapshot().maxHitPoints()
                    || (!target.defeated() && hitPointRatio(target) > profile.healing().dangerRatio())
                    || !withinRange(state, actorId, targetId, ability.rangeFeet())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Sceglie il ruolo dalla parte utile del kit, non dalla semplice presenza di
     * un pugnale. Un vero specialista melee conserva il suo ingaggio; un caster o
     * guaritore con opzioni a distanza comparabili mantiene invece spazio.
     */
    private boolean prefersRangedPositioning(
            CombatState state,
            String actorId,
            CombatantState actor,
            String targetId,
            String focusTargetId,
            boolean hasMelee,
            int rangedRange,
            ActorMemory memory,
            Map<String, Boolean> tacticalAreas) {
        if (rangedRange <= 5) return false;
        boolean rangedSupport = actor.snapshot().abilities().stream()
                .filter(ability -> !ability.passive())
                .filter(ability -> ability.automationStatus() == AutomationStatus.AUTOMATED)
                .filter(ability -> ability.resolutionMethod() == ResolutionMethod.AUTOMATIC)
                .filter(ability -> ability.healing() != null)
                .filter(ability -> ability.healing().target() != HealingTarget.SELF)
                .filter(ability -> ability.rangeFeet() > 5)
                .anyMatch(ability -> usableHealingForPositioning(
                        state, actorId, actor, ability, memory));
        if (!hasMelee || rangedSupport) return true;

        CombatantState target = state.combatants().get(targetId);
        if (target == null || target.dead()) return false;
        double bestMelee = actor.snapshot().abilities().stream()
                .filter(ability -> !ability.passive())
                .filter(ability -> ability.automationStatus() == AutomationStatus.AUTOMATED)
                .filter(ability -> ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL)
                .filter(ability -> ability.rangeFeet() <= 5)
                .filter(ability -> usableOffenseForPositioning(
                        state, actorId, actor, ability, true, memory))
                .mapToDouble(ability -> expectedAttackDamage(actor, ability, target))
                .max()
                .orElse(0.0);
        double bestRanged = 0.0;
        List<AbilityDefinition> abilities = actor.snapshot().abilities();
        for (int abilityIndex = 0; abilityIndex < abilities.size(); abilityIndex++) {
            AbilityDefinition ability = abilities.get(abilityIndex);
            boolean supported = !ability.isArea()
                    ? ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL
                    : hasAutomatedAreaResolution(ability);
            if (ability.passive()
                    || ability.automationStatus() != AutomationStatus.AUTOMATED
                    || ability.rangeFeet() <= 5
                    || ability.damage().isEmpty()
                    || !supported
                    || !usableOffenseForPositioning(
                            state, actorId, actor, ability, !ability.isArea(), memory)
                    || (ability.isArea()
                            && !tacticallyUsableAreaForPositioning(
                                    state,
                                    actorId,
                                    ability,
                                    abilityIndex,
                                    focusTargetId,
                                    tacticalAreas))) {
                continue;
            }
            double expected = ability.isArea()
                    ? expectedAreaDamage(actor, ability, target)
                    : expectedAttackDamage(actor, ability, target);
            bestRanged = Math.max(bestRanged, expected);
        }
        return bestRanged + SCORE_EPSILON >= bestMelee * 0.8;
    }

    private boolean tacticallyUsableAreaForPositioning(
            CombatState state,
            String actorId,
            AbilityDefinition ability,
            int abilityIndex,
            String focusTargetId,
            Map<String, Boolean> tacticalAreas) {
        return tacticalAreas.computeIfAbsent(
                ability.id(),
                ignored -> bestAreaChoice(
                        state, actorId, ability, abilityIndex, focusTargetId, false) != null);
    }

    private static int adjacentEnemyCount(CombatState state, String targetId, String exceptActorId) {
        int count = 0;
        for (String id : state.rosterOrder()) {
            if (id.equals(exceptActorId) || state.partyCombatantIds().contains(id)) continue;
            CombatantState combatant = state.combatants().get(id);
            if (combatant == null || combatant.defeated() || combatant.dead()) continue;
            if (state.distanceFeet(id, targetId).map(distance -> distance <= 5).orElse(false)) count++;
        }
        return count;
    }

    private static double surroundScore(
            CombatState state,
            String actorId,
            String targetId,
            TokenPlacement candidate,
            int meleeRangeFeet) {
        TokenPlacement target = state.battleMap().placementOf(targetId).orElse(null);
        if (target == null) return 0.0;
        if (state.battleMap().grid().feetFor(candidate.squaresTo(target)) > meleeRangeFeet) {
            return 0.0;
        }
        String candidateSector = sector(candidate, target);
        Set<String> occupied = new HashSet<>();
        for (String id : state.rosterOrder()) {
            if (id.equals(actorId) || state.partyCombatantIds().contains(id)) continue;
            CombatantState ally = state.combatants().get(id);
            TokenPlacement placement = state.battleMap().placementOf(id).orElse(null);
            if (ally == null || ally.defeated() || ally.dead() || placement == null) continue;
            if (state.battleMap().grid().feetFor(placement.squaresTo(target)) <= meleeRangeFeet) {
                occupied.add(sector(placement, target));
            }
        }
        double score = occupied.contains(candidateSector) ? 0.0 : 2.0;
        if (occupied.contains(oppositeSector(candidateSector))) score += 3.0;
        score += occupied.size() * 0.25;
        return score;
    }

    private static String sector(TokenPlacement placement, TokenPlacement target) {
        double x = placement.origin().column() + placement.squaresPerSide() / 2.0;
        double y = placement.origin().row() + placement.squaresPerSide() / 2.0;
        double targetX = target.origin().column() + target.squaresPerSide() / 2.0;
        double targetY = target.origin().row() + target.squaresPerSide() / 2.0;
        return Integer.signum((int) Math.signum(x - targetX)) + ":"
                + Integer.signum((int) Math.signum(y - targetY));
    }

    private static String oppositeSector(String sector) {
        String[] parts = sector.split(":", -1);
        return (-Integer.parseInt(parts[0])) + ":" + (-Integer.parseInt(parts[1]));
    }

    private static double threatRating(CombatantState combatant) {
        double bestDamage = combatant.snapshot().abilities().stream()
                .filter(ability -> !ability.passive() && ability.automationStatus() == AutomationStatus.AUTOMATED)
                .mapToDouble(ability -> ability.damage().stream()
                        .mapToDouble(formula -> expectedFormula(formula, false))
                        .sum())
                .max()
                .orElse(0.0);
        return bestDamage + combatant.snapshot().armorClass() * 0.25 + combatant.snapshot().spellSaveDc() * 0.2;
    }

    private static boolean isHealer(CombatantState combatant) {
        return combatant.snapshot().abilities().stream().anyMatch(ability -> ability.healing() != null);
    }

    private static double hitPointRatio(CombatantState combatant) {
        return (double) combatant.currentHitPoints() / combatant.snapshot().maxHitPoints();
    }

    private static long hitPointsToDefeat(CombatantState combatant) {
        return (long) combatant.currentHitPoints() + combatant.temporaryHitPoints();
    }

    /** PF effettivi per le sole priorita' offensive; le cure continuano a usare i PF reali. */
    private static double survivabilityRatio(CombatantState combatant) {
        return Math.min(1.0,
                (double) hitPointsToDefeat(combatant) / combatant.snapshot().maxHitPoints());
    }

    private static int distanceForOrdering(CombatState state, String first, String second) {
        if (first == null || first.isBlank()) return 0;
        return state.distanceFeet(first, second).orElse(0);
    }

    private static int rosterIndex(CombatState state, String id) {
        if (id == null) return Integer.MAX_VALUE;
        int index = state.rosterOrder().indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static Candidate best(List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (better(candidate, best)) best = candidate;
        }
        return best;
    }

    private static boolean better(Candidate candidate, Candidate incumbent) {
        if (incumbent == null) return true;
        if (candidate.score > incumbent.score + SCORE_EPSILON) return true;
        return Math.abs(candidate.score - incumbent.score) <= SCORE_EPSILON
                && candidate.key.compareTo(incumbent.key) < 0;
    }

    static String decisionSignature(EnemyCpuDecision decision) {
        if (decision instanceof EnemyCpuDecision.Move move) {
            return "move:" + positionKey(move.destination());
        }
        if (decision instanceof EnemyCpuDecision.Attack attack) {
            return "attack:" + attack.actorId() + ':' + attack.targetId() + ':' + attack.abilityId();
        }
        if (decision instanceof EnemyCpuDecision.AreaAttack area) {
            return "area:" + area.actorId() + ':' + area.abilityId() + ':' + positionKey(area.center());
        }
        if (decision instanceof EnemyCpuDecision.Heal heal) {
            return "heal:" + heal.actorId() + ':' + heal.targetId() + ':' + heal.abilityId()
                    + ':' + heal.resourceId();
        }
        if (decision instanceof EnemyCpuDecision.Activate activate) {
            return "activate:" + activate.actorId() + ':' + activate.abilityId();
        }
        return "done:" + decision.actorId();
    }

    static String decisionTarget(EnemyCpuDecision decision) {
        if (decision instanceof EnemyCpuDecision.Attack attack) return attack.targetId();
        if (decision instanceof EnemyCpuDecision.Heal heal) return heal.targetId();
        if (decision instanceof EnemyCpuDecision.AreaAttack area) return area.center().toString();
        if (decision instanceof EnemyCpuDecision.Move move) return move.destination().toString();
        return "";
    }

    static String decisionAbility(EnemyCpuDecision decision) {
        if (decision instanceof EnemyCpuDecision.Attack attack) return attack.abilityId();
        if (decision instanceof EnemyCpuDecision.Heal heal) return heal.abilityId();
        if (decision instanceof EnemyCpuDecision.AreaAttack area) return area.abilityId();
        if (decision instanceof EnemyCpuDecision.Activate activate) return activate.abilityId();
        return "";
    }

    private static String positionKey(GridPosition position) {
        return String.format("%05d:%05d", position.column(), position.row());
    }

    static String messageOf(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Candidate(EnemyCpuDecision decision, double score, String key) { }

    private record HealingCastOption(
            String resourceId,
            int slotLevel,
            HealingDefinition healing,
            double expectedHealing) { }

    private record AreaEvaluation(
            int enemyHits,
            int friendlyHits,
            double enemyDamage,
            double friendlyDamage,
            boolean containsFocus) { }

    private record AreaChoice(GridPosition center, double score) { }

    /** Memoria di un attore per la durata del suo turno; la tiene chi guida il gruppo. */
    static final class ActorMemory {
        private boolean moved;
        private final Set<String> usedOnce = new HashSet<>();

        void remember(EnemyCpuDecision decision) {
            if (decision instanceof EnemyCpuDecision.Move) moved = true;
            String ability = decisionAbility(decision);
            if (!ability.isBlank()) usedOnce.add("ability:" + ability);
        }
    }
}
