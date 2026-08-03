package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.AbilityEffect;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AreaSpellResult;
import app.d6d.domain.combat.AreaTargetResult;
import app.d6d.domain.combat.AttackOutcome;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.CombatantSetup;
import app.d6d.domain.combat.CombatantSnapshot;
import app.d6d.domain.combat.CombatantState;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.ConcentrationCheckResult;
import app.d6d.domain.combat.ConcentrationState;
import app.d6d.domain.combat.ConditionDuration;
import app.d6d.domain.combat.ConditionExpiry;
import app.d6d.domain.combat.ConditionInstance;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.D20RollResult;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageComponentResult;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageResult;
import app.d6d.domain.combat.DeathSaveState;
import app.d6d.domain.combat.DiceRollResult;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.RollSource;
import app.d6d.domain.combat.TurnBudget;
import app.d6d.domain.space.TokenPlacement;
import app.d6d.domain.space.MapGrid;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.BattleMap;
import app.d6d.domain.space.MapBackground;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UI-independent command facade for one encounter. All successful commands are atomic,
 * audited and undoable; rejected commands change neither game state nor RNG.
 */
public final class CombatSession {
    private MutableState state;
    private final DeterministicDice dice;
    private final List<CombatEvent> audit;
    private final Deque<Checkpoint> undoStack = new ArrayDeque<>();
    private long nextEventSequence;
    private long revisionCounter;

    private CombatSession(String encounterId, long seed, String rulesetVersion, String contentVersion) {
        this.state = MutableState.empty(encounterId, rulesetVersion, contentVersion);
        this.dice = new DeterministicDice(seed);
        this.audit = new ArrayList<>();
        this.nextEventSequence = 0;
        this.revisionCounter = 0;
        append(EventType.ENCOUNTER_CREATED, "", "", details(
                "seed", seed,
                "rulesetVersion", rulesetVersion,
                "contentVersion", contentVersion));
    }

    private CombatSession(CombatState savedState, List<CombatEvent> savedAudit) {
        this.state = MutableState.from(savedState);
        this.dice = DeterministicDice.fromState(savedState.randomSeed(), savedState.randomState());
        this.audit = new ArrayList<>(List.copyOf(savedAudit));
        this.nextEventSequence = audit.stream().mapToLong(CombatEvent::sequence).max().orElse(-1L) + 1;
        this.revisionCounter = Math.max(savedState.revision(),
                audit.stream().mapToLong(CombatEvent::revision).max().orElse(0L));
    }

    public static CombatSession create(String encounterId, long seed) {
        return create(encounterId, seed, "srd-5.2.1", "local-1");
    }

    public static CombatSession create(
            String encounterId, long seed, String rulesetVersion, String contentVersion) {
        requireText(encounterId, "encounterId");
        requireText(rulesetVersion, "rulesetVersion");
        requireText(contentVersion, "contentVersion");
        return new CombatSession(encounterId, seed, rulesetVersion, contentVersion);
    }

    /** Uses actor ids as encounter instance ids; duplicate definitions should use fromCombatants instead. */
    public static CombatSession fromActors(String encounterId, long seed, List<ActorDefinition> actors) {
        Objects.requireNonNull(actors, "actors");
        CombatSession result = create(encounterId, seed);
        for (ActorDefinition actor : actors) {
            result.addCombatant(actor.id(), actor);
        }
        return result;
    }

    public static CombatSession fromCombatants(String encounterId, long seed, List<CombatantSetup> combatants) {
        Objects.requireNonNull(combatants, "combatants");
        CombatSession result = create(encounterId, seed);
        for (CombatantSetup setup : combatants) {
            result.addCombatant(setup.instanceId(), setup.actor());
        }
        return result;
    }

    /** Restores a persistence snapshot. Undo history starts empty; the supplied audit remains append-only. */
    public static CombatSession restore(CombatState savedState, List<CombatEvent> savedAudit) {
        Objects.requireNonNull(savedState, "savedState");
        Objects.requireNonNull(savedAudit, "savedAudit");
        return new CombatSession(savedState, savedAudit);
    }

    public synchronized CombatState currentState() {
        return state.toDomain(dice.seed(), dice.state());
    }

    public synchronized List<CombatEvent> auditTrail() {
        return List.copyOf(audit);
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized void addCombatant(String instanceId, ActorDefinition actor) {
        requireStatus(CombatStatus.DRAFT);
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(actor, "actor");
        if (state.combatants.containsKey(instanceId)) {
            throw rule("Duplicate combatant instance id: " + instanceId);
        }
        CombatantSnapshot snapshot = CombatantSnapshot.from(instanceId, actor);
        beginCommand();
        state.rosterOrder.add(instanceId);
        state.combatants.put(instanceId, MutableCombatant.from(snapshot));
        state.turnBudgets.put(instanceId, TurnBudget.fresh(snapshot.speedFeet(), snapshot.attacksPerAction()));
        append(EventType.COMBATANT_ADDED, instanceId, "", details(
                "definitionId", actor.id(), "definitionVersion", actor.definitionVersion(), "name", actor.name()));
    }

    /**
     * Adds a combatant while an encounter is already being prepared or played.
     *
     * <p>The new entry receives its static initiative immediately. In live play the
     * initiative list is rebuilt around the existing current turn, so reinforcements
     * enter the queue without stealing the action in progress.</p>
     */
    public synchronized void addCombatantToEncounter(String instanceId, ActorDefinition actor, boolean party) {
        if (state.status == CombatStatus.RESOLVED) {
            throw rule("A resolved encounter cannot receive new combatants");
        }
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(actor, "actor");
        if (state.combatants.containsKey(instanceId)) {
            throw rule("Duplicate combatant instance id: " + instanceId);
        }
        String anchor = (state.status == CombatStatus.ACTIVE || state.status == CombatStatus.PAUSED)
                ? currentTurnAnchor()
                : "";
        CombatantSnapshot snapshot = CombatantSnapshot.from(instanceId, actor);
        beginCommand();
        state.rosterOrder.add(instanceId);
        state.combatants.put(instanceId, MutableCombatant.from(snapshot));
        state.turnBudgets.put(instanceId, TurnBudget.fresh(snapshot.speedFeet(), snapshot.attacksPerAction()));
        state.initiativeScores.put(instanceId, actor.initiativeScore());
        if (party) {
            state.partyCombatantIds.add(instanceId);
        }
        if (state.status == CombatStatus.ACTIVE || state.status == CombatStatus.PAUSED) {
            rebuildInitiativeOrder();
            List<List<String>> groups = turnGroups();
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).contains(anchor)) {
                    state.turnIndex = i;
                    break;
                }
            }
        } else if (state.status == CombatStatus.READY &&
                state.initiativeScores.keySet().containsAll(state.combatants.keySet())) {
            rebuildInitiativeOrder();
        }
        append(EventType.COMBATANT_ADDED, instanceId, "", details(
                "definitionId", actor.id(),
                "definitionVersion", actor.definitionVersion(),
                "name", actor.name(),
                "faction", party ? "party" : "enemy",
                "initiative", actor.initiativeScore(),
                "live", state.status == CombatStatus.ACTIVE || state.status == CombatStatus.PAUSED));
    }

    public synchronized void markReady() {
        requireStatus(CombatStatus.DRAFT);
        if (state.combatants.isEmpty()) throw rule("An encounter needs at least one combatant");
        beginCommand();
        state.status = CombatStatus.READY;
        append(EventType.ENCOUNTER_READY, "", "", Map.of());
    }

    /** Records encounter sides in the portable snapshot before initiative begins. */
    public synchronized void setPartyCombatants(Collection<String> combatantIds) {
        requireStatus(CombatStatus.DRAFT);
        Objects.requireNonNull(combatantIds, "combatantIds");
        List<String> ids = List.copyOf(combatantIds);
        if (new HashSet<>(ids).size() != ids.size()) {
            throw rule("Party combatants must be unique");
        }
        for (String id : ids) combatant(id);
        beginCommand();
        state.partyCombatantIds.clear();
        state.partyCombatantIds.addAll(ids);
        append(EventType.PARTY_SET, "", "", details("combatantIds", String.join(",", ids)));
    }

    /** Sets an already-computed initiative total, useful in Tracker mode. */
    public synchronized void setInitiative(String combatantId, int total) {
        requireSetupPhase();
        combatant(combatantId);
        beginCommand();
        state.initiativeScores.put(combatantId, total);
        state.initiativeOrder.clear();
        append(EventType.INITIATIVE_SET, combatantId, "", details("total", total));
    }

    public synchronized D20RollResult rollInitiative(String combatantId, D20Mode mode) {
        requireSetupPhase();
        MutableCombatant combatant = combatant(combatantId);
        Objects.requireNonNull(mode, "mode");
        beginCommand();
        try {
            D20Mode effectiveMode = imposeDisadvantage(
                    mode,
                    combatant.snapshot.strengthDexterityD20Disadvantage());
            D20RollResult roll = rollD20(
                    D20RollInput.digital(effectiveMode),
                    combatant.snapshot.initiativeModifier());
            state.initiativeScores.put(combatantId, roll.total());
            state.initiativeOrder.clear();
            append(EventType.INITIATIVE_ROLLED, combatantId, "", rollDetails(roll));
            return roll;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * The same dice pool is shared, while each combatant applies its own modifier
     * and selects the die using its effective advantage/disadvantage state.
     */
    public synchronized Map<String, D20RollResult> rollSharedInitiative(
            Collection<String> combatantIds, D20Mode mode) {
        requireSetupPhase();
        Objects.requireNonNull(combatantIds, "combatantIds");
        Objects.requireNonNull(mode, "mode");
        List<String> ids = List.copyOf(combatantIds);
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            throw rule("Shared initiative needs a non-empty set of unique combatants");
        }
        for (String id : ids) combatant(id);
        beginCommand();
        try {
            boolean anyImposedDisadvantage = ids.stream()
                    .map(this::combatant)
                    .anyMatch(it -> it.snapshot.strengthDexterityD20Disadvantage());
            D20Mode diceMode =
                    mode == D20Mode.NORMAL && !anyImposedDisadvantage ? D20Mode.NORMAL : D20Mode.DISADVANTAGE;
            List<Integer> rolledDice = dice.rollD20(diceMode);
            Map<String, D20RollResult> results = new LinkedHashMap<>();
            for (String id : ids) {
                CombatantSnapshot snapshot = combatant(id).snapshot;
                D20Mode effectiveMode =
                        imposeDisadvantage(mode, snapshot.strengthDexterityD20Disadvantage());
                int natural = selectedD20(rolledDice, effectiveMode);
                int modifier = snapshot.initiativeModifier();
                int total = checkedTotal(natural, modifier, "Initiative total");
                D20RollResult result = new D20RollResult(RollSource.DIGITAL, effectiveMode, rolledDice,
                        natural, modifier, total);
                results.put(id, result);
                state.initiativeScores.put(id, result.total());
                append(EventType.INITIATIVE_ROLLED, id, "", merge(
                        rollDetails(result), details("shared", true)));
            }
            state.initiativeOrder.clear();
            return Map.copyOf(results);
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized int useStaticInitiative(String combatantId, D20Mode mode) {
        requireSetupPhase();
        MutableCombatant combatant = combatant(combatantId);
        Objects.requireNonNull(mode, "mode");
        D20Mode effectiveMode = imposeDisadvantage(
                mode,
                combatant.snapshot.strengthDexterityD20Disadvantage());
        int adjustment =
                effectiveMode == D20Mode.ADVANTAGE ? 5 : effectiveMode == D20Mode.DISADVANTAGE ? -5 : 0;
        int total = checkedTotal(combatant.snapshot.initiativeScore(), adjustment, "Static initiative total");
        beginCommand();
        state.initiativeScores.put(combatantId, total);
        state.initiativeOrder.clear();
        append(EventType.INITIATIVE_SET, combatantId, "", details(
                "total", total, "mode", effectiveMode, "static", true));
        return total;
    }

    /** Explicit ordering is how the DM/player resolves ties. */
    public synchronized void setInitiativeOrder(List<String> orderedCombatantIds) {
        requireSetupPhase();
        Objects.requireNonNull(orderedCombatantIds, "orderedCombatantIds");
        List<String> order = List.copyOf(orderedCombatantIds);
        Set<String> expected = state.combatants.keySet();
        if (order.size() != expected.size() || new HashSet<>(order).size() != order.size()
                || !new HashSet<>(order).equals(expected) || !state.initiativeScores.keySet().containsAll(expected)) {
            throw rule("Initiative order must contain every initialized combatant exactly once");
        }
        beginCommand();
        state.initiativeOrder.clear();
        state.initiativeOrder.addAll(order);
        append(EventType.INITIATIVE_ORDER_SET, "", "", details("order", String.join(",", order)));
    }

    public synchronized void start() {
        requireStatus(CombatStatus.READY);
        if (!state.initiativeScores.keySet().containsAll(state.combatants.keySet())) {
            throw rule("Every combatant needs initiative before starting");
        }
        beginCommand();
        if (state.initiativeOrder.size() != state.combatants.size()) {
            rebuildInitiativeOrder();
        }
        state.status = CombatStatus.ACTIVE;
        state.round = 1;
        state.turnIndex = 0;
        append(EventType.ENCOUNTER_STARTED, "", "", details(
                "initiativeOrder", String.join(",", state.initiativeOrder)));
        append(EventType.ROUND_STARTED, "", "", details("round", state.round));
        if (seekFirstPlayableTurn()) {
            startTurnInternal();
        }
        // Setup is the immutable baseline of live play: Undo must never return the combat UI to READY/DRAFT.
        undoStack.clear();
    }

    public synchronized void pause() {
        requireStatus(CombatStatus.ACTIVE);
        beginCommand();
        state.status = CombatStatus.PAUSED;
        append(EventType.ENCOUNTER_PAUSED, "", "", Map.of());
    }

    public synchronized void resume() {
        requireStatus(CombatStatus.PAUSED);
        beginCommand();
        state.status = CombatStatus.ACTIVE;
        append(EventType.ENCOUNTER_RESUMED, "", "", Map.of());
    }

    public synchronized void resolve(String outcome) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("Only an active or paused encounter can be resolved");
        }
        beginCommand();
        state.status = CombatStatus.RESOLVED;
        append(EventType.ENCOUNTER_RESOLVED, "", "", details("outcome", outcome == null ? "" : outcome));
    }

    public synchronized AttackResult attack(AttackRequest request) {
        requireStatus(CombatStatus.ACTIVE);
        Objects.requireNonNull(request, "request");
        MutableCombatant attacker = combatant(request.attackerId());
        MutableCombatant target = combatant(request.targetId());
        if (attacker.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot attack");
        }
        if (attacker.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot attack");
        }
        AbilityDefinition ability = ability(attacker, request.abilityId());
        if (ability.resolutionMethod() != ResolutionMethod.ATTACK_ROLL) {
            throw rule("Ability does not use an attack roll: " + ability.id());
        }
        if (attacker.snapshot.strengthDexterityD20Disadvantage() && ability.spellOrCantrip()) {
            throw rule("The combatant cannot cast spells while wearing armor without training");
        }
        if (ability.automationStatus() == AutomationStatus.MANUAL_REQUIRED
                && request.attackRoll().source() == RollSource.DIGITAL) {
            throw rule("Ability requires manual resolution: " + ability.id());
        }
        if (!request.manualDamageValues().isEmpty()
                && request.manualDamageValues().size() != ability.damage().size()) {
            throw rule("Manual damage must contain one value per damage component");
        }
        validateRange(request.attackerId(), request.targetId(), ability);
        validateAttackActivationCost(
                request.attackerId(), ability.activationCost(), ability.spellOrCantrip());

        beginCommand();
        try {
            consumeAttackActivationCost(
                    request.attackerId(), ability.activationCost(), ability.spellOrCantrip());
            D20RollInput attackInput = imposeDisadvantage(
                    request.attackRoll(),
                    attacker.snapshot.strengthDexterityD20Disadvantage()
                            && usesStrengthOrDexterityForAttack(ability));
            D20RollResult attackRoll = rollD20For(attacker, attackInput, ability.attackBonus());
            AttackOutcome outcome = attackOutcome(attackRoll, target.snapshot.armorClass());
            Map<String, String> attackDetails = merge(
                    rollDetails(attackRoll),
                    details(
                            "abilityId", ability.id(),
                            "abilityName", ability.name(),
                            "armorClass", target.snapshot.armorClass()));
            append(EventType.ATTACK_ROLLED, request.attackerId(), request.targetId(), attackDetails);

            if (outcome == AttackOutcome.MISS) {
                append(EventType.ATTACK_MISSED, request.attackerId(), request.targetId(), attackDetails);
                return new AttackResult(request.attackerId(), request.targetId(), ability.id(), attackRoll,
                        outcome, List.of(), Optional.empty());
            }

            boolean critical = outcome == AttackOutcome.CRITICAL_HIT;
            append(critical ? EventType.CRITICAL_HIT : EventType.ATTACK_HIT,
                    request.attackerId(), request.targetId(), attackDetails);
            List<DamageComponent> rolledDamage = resolveAttackDamage(ability, request.manualDamageValues(), critical,
                    request.attackerId(), request.targetId());
            DamageResult damage = applyDamageInternal(request.attackerId(), request.targetId(), rolledDamage,
                    critical, D20RollInput.digital());
            return new AttackResult(request.attackerId(), request.targetId(), ability.id(), attackRoll,
                    outcome, rolledDamage, Optional.of(damage));
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * Lancia un incantesimo ad area risolvendo i tiri salvezza automaticamente.
     *
     * <p>Il danno si tira una sola volta; poi ogni creatura entro la sfera di raggio
     * indicato, centrata sulla casella scelta, tira il proprio tiro salvezza contro
     * la CD incantesimi del lanciatore. Chi fallisce subisce il danno pieno, chi
     * supera ne subisce meta' quando l'incantesimo lo prevede. Come una vera area,
     * colpisce chiunque sia dentro il raggio, alleati compresi.</p>
     */
    public synchronized AreaSpellResult castArea(String casterId, GridPosition center, String abilityId) {
        return castAreaInternal(casterId, center, abilityId, null);
    }

    /**
     * Lancia un incantesimo ad area con i tiri salvezza decisi al tavolo.
     *
     * <p>{@code savedByTarget} dice, per ciascun bersaglio, se ha superato il tiro
     * salvezza; chi non compare vale come fallito (danno pieno). Non si tira alcun
     * d20: e' la risoluzione manuale, quella usata con la modalita' modifica attiva.</p>
     */
    public synchronized AreaSpellResult castAreaManual(
            String casterId, GridPosition center, String abilityId, Map<String, Boolean> savedByTarget) {
        Objects.requireNonNull(savedByTarget, "savedByTarget");
        return castAreaInternal(casterId, center, abilityId, Map.copyOf(savedByTarget));
    }

    /**
     * Bersagli che l'area coprirebbe, senza tirare nulla ne' toccare lo stato.
     *
     * <p>Serve all'interfaccia per elencare chi verrebbe colpito prima di confermare
     * — in particolare per costruire i tiri salvezza da decidere a mano.</p>
     */
    public synchronized List<String> areaTargets(String casterId, GridPosition center, String abilityId) {
        Objects.requireNonNull(center, "center");
        if (!state.battleMap.configured()) return List.of();
        AbilityDefinition ability = ability(combatant(casterId), abilityId);
        if (!ability.isArea()) return List.of();
        return combatantsInArea(center, ability.areaRadiusFeet());
    }

    private AreaSpellResult castAreaInternal(
            String casterId, GridPosition center, String abilityId, Map<String, Boolean> savedByTarget) {
        requireStatus(CombatStatus.ACTIVE);
        Objects.requireNonNull(center, "center");
        requireConfiguredMap();
        MutableCombatant caster = combatant(casterId);
        if (caster.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot cast");
        }
        if (caster.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot cast");
        }
        AbilityDefinition ability = ability(caster, abilityId);
        if (!ability.isArea()) {
            throw rule("Ability is not an area effect: " + ability.id());
        }
        if (caster.snapshot.strengthDexterityD20Disadvantage() && ability.spellOrCantrip()) {
            throw rule("The combatant cannot cast spells while wearing armor without training");
        }
        if (!state.battleMap.grid().contains(center)) {
            throw rule("The area centre is outside the map");
        }
        validateAreaRange(casterId, center, ability);
        validateActivationCost(casterId, ability.activationCost(), ability.spellOrCantrip());

        beginCommand();
        try {
            consumeActivationCost(casterId, ability.activationCost(), ability.spellOrCantrip());
            // Un solo tiro di danno per tutta l'area; i critici non toccano i tiri
            // salvezza, quindi i dadi non si raddoppiano mai qui.
            List<DamageComponent> rolled = resolveAttackDamage(ability, List.of(), false, casterId, "");
            int saveDc = caster.snapshot.spellSaveDc();
            List<String> targets = combatantsInArea(center, ability.areaRadiusFeet());
            append(EventType.AREA_SPELL_CAST, casterId, "", details(
                    "abilityId", ability.id(),
                    "abilityName", ability.name(),
                    "center", center,
                    "radiusFeet", ability.areaRadiusFeet(),
                    "saveDc", saveDc,
                    "targets", targets.size()));

            List<AreaTargetResult> perTarget = new ArrayList<>();
            for (String targetId : targets) {
                perTarget.add(resolveAreaTarget(casterId, targetId, ability, saveDc, rolled, savedByTarget));
            }
            AreaSpellResult result = new AreaSpellResult(
                    casterId, ability.id(), center, ability.areaRadiusFeet(), saveDc, rolled, perTarget);
            // Un'area puo' comprendere il lanciatore. Se questi si porta a 0 PF e
            // nel suo gruppo non resta nessun altro attore vivo, il turno non deve
            // restare sospeso senza un combattente corrente: si chiude e si passa
            // al prossimo gruppo giocabile nello stesso comando (e nello stesso
            // passo di Undo) del lancio.
            if (caster.currentHitPoints == 0 && currentCombatantIds().isEmpty()) {
                endTurnInternal();
            }
            return result;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    private AreaTargetResult resolveAreaTarget(
            String casterId, String targetId, AbilityDefinition ability, int saveDc,
            List<DamageComponent> rolled, Map<String, Boolean> savedByTarget) {
        D20RollResult saveRoll = null;
        boolean saved;
        if (savedByTarget != null) {
            // Risoluzione manuale: il tavolo ha gia' deciso, nessun d20.
            saved = Boolean.TRUE.equals(savedByTarget.get(targetId));
        } else if (ability.hasSavingThrow()) {
            MutableCombatant target = combatant(targetId);
            int bonus = target.snapshot.saveBonus(ability.saveAbility());
            boolean strengthOrDexteritySave =
                    ability.saveAbility() == SaveAbility.STRENGTH
                            || ability.saveAbility() == SaveAbility.DEXTERITY;
            D20RollInput saveInput = imposeDisadvantage(
                    D20RollInput.digital(),
                    strengthOrDexteritySave && target.snapshot.strengthDexterityD20Disadvantage());
            saveRoll = rollD20For(target, saveInput, bonus);
            saved = saveRoll.total() >= saveDc;
        } else {
            saved = false;
        }
        if (ability.hasSavingThrow()) {
            Map<String, String> saveDetails = details(
                    "abilityId", ability.id(),
                    "abilityName", ability.name(),
                    "save", ability.saveAbility(),
                    "dc", saveDc,
                    "saved", saved);
            append(EventType.SAVING_THROW_ROLLED, casterId, targetId, saveRoll == null
                    ? merge(saveDetails, details("source", "MANUAL"))
                    : merge(rollDetails(saveRoll), saveDetails));
        }
        List<DamageComponent> applied = damageAfterSave(rolled, ability, saved);
        DamageResult damage = applied.isEmpty()
                ? null
                : applyDamageInternal(casterId, targetId, applied, false, D20RollInput.digital());
        return new AreaTargetResult(targetId, saved, Optional.ofNullable(saveRoll), Optional.ofNullable(damage));
    }

    /** Danno dopo il tiro salvezza: pieno se fallito, meta' (o nullo) se superato. */
    private static List<DamageComponent> damageAfterSave(
            List<DamageComponent> rolled, AbilityDefinition ability, boolean saved) {
        if (!saved || !ability.hasSavingThrow()) return rolled;
        if (!ability.halfOnSave()) return List.of();
        List<DamageComponent> halved = new ArrayList<>(rolled.size());
        for (DamageComponent component : rolled) {
            int half = component.amount() / 2;
            if (half > 0) halved.add(new DamageComponent(component.type(), half));
        }
        return halved;
    }

    private void validateAreaRange(String casterId, GridPosition center, AbilityDefinition ability) {
        TokenPlacement caster = state.battleMap.placementOf(casterId).orElse(null);
        if (caster == null) return; // lanciatore non sulla mappa: nessuna gittata da imporre
        int nearest = Integer.MAX_VALUE;
        for (GridPosition square : caster.occupiedSquares()) {
            nearest = Math.min(nearest, square.squaresTo(center));
        }
        int distance = state.battleMap.grid().feetFor(nearest);
        if (distance > ability.rangeFeet()) {
            throw rule("The area centre is " + distance + " feet away, beyond the range of "
                    + ability.rangeFeet() + " feet");
        }
    }

    /** Combattenti la cui sagoma tocca la sfera di raggio [radiusFeet] centrata su [center]. */
    private List<String> combatantsInArea(GridPosition center, int radiusFeet) {
        BattleMap map = state.battleMap;
        int feetPerSquare = map.grid().feetPerSquare();
        double radiusSquares = (double) radiusFeet / feetPerSquare;
        double centerX = center.column() + 0.5;
        double centerY = center.row() + 0.5;
        List<String> caught = new ArrayList<>();
        for (TokenPlacement placement : map.orderedPlacements()) {
            boolean within = placement.occupiedSquares().stream().anyMatch(square -> {
                double dx = (square.column() + 0.5) - centerX;
                double dy = (square.row() + 0.5) - centerY;
                return Math.sqrt(dx * dx + dy * dy) <= radiusSquares + 1e-9;
            });
            if (within) caught.add(placement.combatantId());
        }
        return caught;
    }

    public synchronized DamageResult applyDamage(
            String sourceCombatantId, String targetCombatantId, List<DamageComponent> components, boolean critical) {
        return applyDamage(sourceCombatantId, targetCombatantId, components, critical, D20RollInput.digital());
    }

    /** concentrationRoll is ignored when the target is not concentrating or adjusted damage is zero. */
    public synchronized DamageResult applyDamage(
            String sourceCombatantId,
            String targetCombatantId,
            List<DamageComponent> components,
            boolean critical,
            D20RollInput concentrationRoll) {
        requireStatus(CombatStatus.ACTIVE);
        combatant(targetCombatantId);
        Objects.requireNonNull(components, "components");
        List<DamageComponent> copied = List.copyOf(components);
        if (copied.isEmpty()) throw rule("Damage needs at least one component");
        Objects.requireNonNull(concentrationRoll, "concentrationRoll");
        beginCommand();
        try {
            return applyDamageInternal(sourceCombatantId, targetCombatantId, copied, critical, concentrationRoll);
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized int heal(String targetCombatantId, int amount) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        if (amount <= 0) throw rule("Healing must be positive");
        beginCommand();
        int before = target.currentHitPoints;
        int room = target.snapshot.maxHitPoints() - before;
        target.currentHitPoints = before + Math.min(room, amount);
        int restored = target.currentHitPoints - before;
        append(EventType.HEALED, "", targetCombatantId, details(
                "requested", amount, "restored", restored, "hitPointsAfter", target.currentHitPoints));
        // Recuperare punti ferita azzera successi e fallimenti contro morte e
        // annulla lo stato Stable: la creatura torna cosciente.
        if (restored > 0 && before == 0) {
            target.deathSaves = DeathSaveState.none();
        }
        return restored;
    }

    /**
     * Correzione manuale dei punti ferita attuali, pensata per la modalità
     * Modifica del tavolo.
     *
     * <p>Portare una creatura a 0 PF con questa correzione la dichiara morta
     * esplicitamente: non la lascia nel normale stato di tiri salvezza contro
     * morte. Qualunque valore positivo azzera invece lo stato dei tiri contro
     * morte; una morte dovuta a Exhaustion resta invariata.</p>
     */
    public synchronized void setCurrentHitPoints(String combatantId, int hitPoints) {
        MutableCombatant target = combatant(combatantId);
        if (hitPoints < 0 || hitPoints > target.snapshot.maxHitPoints()) {
            throw rule("Current hit points must be between 0 and " + target.snapshot.maxHitPoints());
        }

        beginCommand();
        int before = target.currentHitPoints;
        int temporaryBefore = target.temporaryHitPoints;
        boolean wasDead = target.deathSaves.dead() || target.exhaustionLevel >= CombatantState.MAX_EXHAUSTION;
        target.currentHitPoints = hitPoints;
        if (hitPoints == 0) {
            // L'azione e' una dichiarazione del tavolo, non danno: 0 significa
            // morto come richiesto dalla modalità Modifica.
            target.temporaryHitPoints = 0;
            target.deathSaves = new DeathSaveState(0, DeathSaveState.REQUIRED, false);
            if (target.concentration != null) {
                endConcentrationInternal(combatantId, "manual current hit points edit");
            }
        } else {
            target.deathSaves = DeathSaveState.none();
        }
        append(EventType.CURRENT_HIT_POINTS_SET, "", combatantId, details(
                "before", before,
                "after", hitPoints,
                "temporaryBefore", temporaryBefore,
                "temporaryAfter", target.temporaryHitPoints,
                "zeroMeansDead", hitPoints == 0));
        if (hitPoints == 0 && !wasDead) {
            append(EventType.DIED, "", combatantId, details("cause", "manual current hit points edit"));
        }
    }

    /**
     * Tiro salvezza contro morte all'inizio del turno di una creatura a 0 punti ferita.
     *
     * <p>10 o piu' e' un successo, meno di 10 un fallimento. Il 20 naturale fa
     * recuperare 1 punto ferita e riporta la creatura cosciente; l'1 naturale vale
     * due fallimenti. Tre successi rendono Stable, tre fallimenti causano la morte.</p>
     */
    public synchronized DeathSaveState rollDeathSave(String combatantId, D20RollInput input) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        Objects.requireNonNull(input, "input");
        if (target.currentHitPoints > 0) {
            throw rule("Only a creature at zero hit points rolls death saves");
        }
        if (target.deathSaves.dead()) {
            throw rule("A dead creature does not roll death saves");
        }
        if (target.deathSaves.stable()) {
            throw rule("A stable creature does not roll death saves");
        }

        beginCommand();
        try {
            // Il tiro contro morte non ha modificatori propri, ma resta un D20 Test:
            // la penalita' di Exhaustion si applica anche qui.
            D20RollResult roll = rollD20For(target, input, 0);
            int natural = roll.naturalRoll();

            if (natural == 20) {
                target.deathSaves = DeathSaveState.none();
                target.currentHitPoints = 1;
                append(EventType.DEATH_SAVE_ROLLED, combatantId, "", merge(rollDetails(roll), details(
                        "outcome", "natural20", "hitPointsAfter", target.currentHitPoints)));
                return target.deathSaves;
            }

            if (natural == 1) {
                target.deathSaves = target.deathSaves.withFailures(2);
            } else if (roll.total() >= 10) {
                target.deathSaves = target.deathSaves.withSuccess();
            } else {
                target.deathSaves = target.deathSaves.withFailures(1);
            }

            append(EventType.DEATH_SAVE_ROLLED, combatantId, "", merge(rollDetails(roll), details(
                    "successes", target.deathSaves.successes(),
                    "failures", target.deathSaves.failures())));

            if (target.deathSaves.stable()) {
                append(EventType.STABILIZED, combatantId, "", details("cause", "three successes"));
            } else if (target.deathSaves.dead()) {
                append(EventType.DIED, combatantId, "", details("cause", "three failures"));
            }
            return target.deathSaves;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * Effettua una prova di caratteristica generica senza consumare automaticamente
     * risorse del turno.
     *
     * <p>Il modificatore e' quello dichiarato dal tavolo per la prova specifica.
     * Il motore aggiunge la penalita' di Sfinimento e, soltanto per Forza o
     * Destrezza, impone lo Svantaggio dell'armatura indossata senza competenza.
     * Vantaggio e Svantaggio imposti si annullano come per gli altri D20 Test.</p>
     */
    public synchronized D20RollResult rollAbilityCheck(
            String combatantId,
            SaveAbility ability,
            int modifier,
            D20RollInput input) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant roller = combatant(combatantId);
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(input, "input");

        boolean armorDisadvantage =
                roller.snapshot.strengthDexterityD20Disadvantage()
                        && (ability == SaveAbility.STRENGTH || ability == SaveAbility.DEXTERITY);
        D20RollInput effectiveInput = imposeDisadvantage(input, armorDisadvantage);

        beginCommand();
        try {
            D20RollResult roll = rollD20For(roller, effectiveInput, modifier);
            append(EventType.ABILITY_CHECK_ROLLED, combatantId, "", merge(
                    rollDetails(roll),
                    details("ability", ability, "requestedModifier", modifier)));
            return roll;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * Rende Stable una creatura morente, per esempio con una prova di Medicina CD 10.
     *
     * <p>Resta priva di sensi ma smette di tirare contro morte. Il motore non
     * decide come la stabilizzazione sia stata ottenuta: registra la causa fornita.</p>
     */
    public synchronized void stabilize(String combatantId, String cause) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        if (target.currentHitPoints > 0) {
            throw rule("Only a creature at zero hit points can be stabilized");
        }
        if (target.deathSaves.dead()) {
            throw rule("A dead creature cannot be stabilized");
        }
        beginCommand();
        target.deathSaves = DeathSaveState.stabilized();
        append(EventType.STABILIZED, combatantId, "", details(
                "cause", cause == null || cause.isBlank() ? "manual" : cause));
    }

    /**
     * Mette fuori combattimento senza uccidere.
     *
     * <p>Un attacco in mischia puo' lasciare il bersaglio a 1 punto ferita e privo
     * di sensi invece di portarlo a 0: la creatura non entra nei tiri contro morte.</p>
     */
    public synchronized void knockOut(String combatantId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        beginCommand();
        target.currentHitPoints = Math.max(1, Math.min(1, target.snapshot.maxHitPoints()));
        target.deathSaves = DeathSaveState.none();
        append(EventType.KNOCKED_OUT, "", combatantId, details("hitPointsAfter", target.currentHitPoints));
    }

    /**
     * Imposta il livello di Exhaustion.
     *
     * <p>Exhaustion e' l'eccezione cumulativa fra le condizioni: da 1 a 6, con −2 a
     * tutti i D20 Test e −5 piedi di Speed per livello. Al sesto livello la creatura
     * muore.</p>
     */
    public synchronized void setExhaustion(String combatantId, int level) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        if (level < 0 || level > CombatantState.MAX_EXHAUSTION) {
            throw rule("Exhaustion must be between 0 and " + CombatantState.MAX_EXHAUSTION);
        }
        beginCommand();
        int before = target.exhaustionLevel;
        target.exhaustionLevel = level;
        append(EventType.EXHAUSTION_CHANGED, combatantId, "", details(
                "before", before,
                "after", level,
                "d20Penalty", -2 * level,
                "speedPenaltyFeet", -5 * level));
        if (level >= CombatantState.MAX_EXHAUSTION) {
            append(EventType.DIED, combatantId, "", details("cause", "exhaustion"));
        }
    }

    /** Aggiunge livelli di Exhaustion, senza superare il massimo. */
    public synchronized void addExhaustion(String combatantId, int levels) {
        MutableCombatant target = combatant(combatantId);
        setExhaustion(combatantId, Math.min(CombatantState.MAX_EXHAUSTION, target.exhaustionLevel + levels));
    }

    /**
     * Modifica la fotografia di un combattente durante lo scontro.
     *
     * <p>Il documento vuole che un combattimento usi una fotografia degli attori e
     * che modificare la scheda originale non alteri retroattivamente uno scontro
     * gia' giocato. Questo comando va nella direzione opposta e consentita: e' una
     * correzione dichiarata al tavolo, quindi resta un comando esplicito, registrato
     * nell'audit e annullabile come ogni altro, non una mutazione silenziosa.</p>
     *
     * <p>I punti ferita correnti vengono riportati entro il nuovo massimo.</p>
     *
     * <p>L'evento registra ogni statistica sia prima sia dopo, e la revisione della
     * definizione da cui la fotografia proviene. Il registro resta cosi' sufficiente
     * da solo a ricostruire la correzione: una rilettura della cronologia sa quale
     * fotografia era in uso in ogni punto della partita, invece di vedere soltanto
     * quella finale.</p>
     */
    public synchronized void editCombatant(String combatantId, CombatantSnapshot updated) {
        MutableCombatant existing = combatant(combatantId);
        Objects.requireNonNull(updated, "updated");
        CombatantSnapshot previous = existing.snapshot;
        if (!previous.instanceId().equals(updated.instanceId())) {
            throw rule("A combatant edit cannot change its instance id");
        }
        if (!previous.definitionId().equals(updated.definitionId())) {
            throw rule("A combatant edit cannot change its definition id");
        }
        if (updated.maxHitPoints() < 1) {
            throw rule("Maximum hit points must be positive");
        }

        beginCommand();
        existing.snapshot = updated;
        existing.currentHitPoints = Math.min(existing.currentHitPoints, updated.maxHitPoints());
        append(EventType.COMBATANT_EDITED, combatantId, "", details(
                "previousVersion", previous.definitionVersion(),
                "version", updated.definitionVersion(),
                "previousName", previous.name(),
                "name", updated.name(),
                "previousArmorClass", previous.armorClass(),
                "armorClass", updated.armorClass(),
                "previousMaxHitPoints", previous.maxHitPoints(),
                "maxHitPoints", updated.maxHitPoints(),
                "previousSpeedFeet", previous.speedFeet(),
                "speedFeet", updated.speedFeet(),
                "previousInitiativeModifier", previous.initiativeModifier(),
                "initiativeModifier", updated.initiativeModifier(),
                "previousInitiativeScore", previous.initiativeScore(),
                "initiativeScore", updated.initiativeScore(),
                "previousConstitutionSaveBonus", previous.constitutionSaveBonus(),
                "constitutionSaveBonus", updated.constitutionSaveBonus(),
                "hitPointsAfter", existing.currentHitPoints));
    }

    // --- mappa tattica -------------------------------------------------------------

    /**
     * Configura o riconfigura la griglia.
     *
     * <p>Restringere la mappa scarta i segnaposti rimasti fuori dal nuovo bordo:
     * andranno riposizionati esplicitamente, cosi' nessun combattente resta a
     * coordinate impossibili.</p>
     */
    public synchronized void configureMap(MapGrid grid) {
        Objects.requireNonNull(grid, "grid");
        beginCommand();
        int placedBefore = state.battleMap.placements().size();
        state.battleMap = state.battleMap.withGrid(grid);
        append(EventType.MAP_CONFIGURED, "", "", details(
                "columns", grid.columns(),
                "rows", grid.rows(),
                "feetPerSquare", grid.feetPerSquare(),
                "droppedPlacements", placedBefore - state.battleMap.placements().size()));
    }

    /** Immagine di sfondo, indicata per nome nell'archivio locale delle immagini. */
    public synchronized void setMapBackground(String imageName) {
        beginCommand();
        state.battleMap = state.battleMap.withBackground(imageName == null ? "" : imageName);
        append(EventType.MAP_BACKGROUND_SET, "", "", details("image", state.battleMap.backgroundImage()));
    }

    /**
     * Collocazione dello sfondo sulla griglia: dove sta e quanto e' grande.
     *
     * <p>Le misure sono in caselle. Un solo evento per gesto completo — non uno per
     * ogni scatto del trascinamento — cosi' spostare o stirare l'immagine resta un
     * passo solo da annullare, come muovere un segnaposto.</p>
     */
    public synchronized void setMapBackgroundTransform(
            double offsetX, double offsetY, double width, double height) {
        beginCommand();
        MapBackground transform = new MapBackground(offsetX, offsetY, width, height);
        state.battleMap = state.battleMap.withBackgroundTransform(transform);
        append(EventType.MAP_BACKGROUND_SET, "", "", details(
                "offsetX", transform.offsetX(),
                "offsetY", transform.offsetY(),
                "width", transform.width(),
                "height", transform.height()));
    }

    /**
     * Colloca un combattente sulla griglia.
     *
     * <p>{@code squaresPerSide} e' l'ingombro: una creatura Grande ne occupa due,
     * una Enorme tre. Posizionare non consuma movimento: e' preparazione, non
     * spostamento.</p>
     */
    public synchronized void placeCombatant(String combatantId, GridPosition position, int squaresPerSide) {
        combatant(combatantId);
        Objects.requireNonNull(position, "position");
        requireConfiguredMap();
        TokenPlacement placement = new TokenPlacement(combatantId, position, squaresPerSide);
        if (!state.battleMap.fitsInsideGrid(placement)) {
            throw rule("The token does not fit inside the grid");
        }
        if (!state.battleMap.isFree(placement)) {
            throw rule("That space is already occupied");
        }
        beginCommand();
        state.battleMap = state.battleMap.withPlacement(placement);
        append(EventType.COMBATANT_PLACED, combatantId, "", details(
                "position", position.toString(), "squaresPerSide", squaresPerSide));
    }

    /**
     * Sposta un combattente consumando il suo movimento.
     *
     * <p>La distanza percorsa si conta dall'origine alla destinazione con la metrica
     * della griglia — una diagonale vale una casella — e viene sottratta dal budget
     * del turno, che e' la stessa risorsa spesa da {@code spendMovement}. Restituisce
     * i piedi effettivamente spesi.</p>
     */
    public synchronized int moveCombatant(String combatantId, GridPosition destination) {
        requireStatus(CombatStatus.ACTIVE);
        requireCurrentCombatant(combatantId);
        Objects.requireNonNull(destination, "destination");
        requireConfiguredMap();

        TokenPlacement current = state.battleMap.placementOf(combatantId)
                .orElseThrow(() -> rule("The combatant is not on the map"));
        TokenPlacement moved = current.movedTo(destination);
        if (!state.battleMap.fitsInsideGrid(moved)) {
            throw rule("The destination is outside the grid");
        }
        if (!state.battleMap.isFree(moved)) {
            throw rule("The destination is already occupied");
        }

        int squares = current.origin().squaresTo(destination);
        int feet = state.battleMap.grid().feetFor(squares);
        TurnBudget budget = budget(combatantId);
        if (feet > budget.movementRemainingFeet()) {
            throw rule("Movement exceeds the remaining budget");
        }

        beginCommand();
        state.battleMap = state.battleMap.withPlacement(moved);
        state.turnBudgets.put(combatantId, budget.spendMovement(feet));
        append(EventType.COMBATANT_MOVED, combatantId, "", details(
                "from", current.origin().toString(),
                "to", destination.toString(),
                "squares", squares,
                "feet", feet,
                "remainingFeet", budget(combatantId).movementRemainingFeet()));
        return feet;
    }

    public synchronized void removeFromMap(String combatantId) {
        if (!state.battleMap.isPlaced(combatantId)) {
            throw rule("The combatant is not on the map");
        }
        beginCommand();
        state.battleMap = state.battleMap.without(combatantId);
        append(EventType.COMBATANT_REMOVED_FROM_MAP, combatantId, "", Map.of());
    }

    /**
     * Verifica la gittata quando la mappa lo consente.
     *
     * <p>Senza mappa, o quando anche uno solo dei due combattenti non e' posizionato,
     * non si puo' dichiarare una distanza: il documento vuole che in quel caso resti
     * il tavolo a stabilire se il bersaglio e' raggiungibile, e il motore non
     * inventa un vincolo che non sa verificare.</p>
     */
    private void validateRange(String attackerId, String targetId, AbilityDefinition ability) {
        if (!state.battleMap.configured()) return;
        state.battleMap.distanceFeet(attackerId, targetId).ifPresent(distance -> {
            if (distance > ability.rangeFeet()) {
                throw rule("Target is " + distance + " feet away, beyond the ability range of "
                        + ability.rangeFeet() + " feet");
            }
        });
    }

    private void requireConfiguredMap() {
        if (!state.battleMap.configured()) {
            throw rule("No map has been configured for this encounter");
        }
    }

    /** Temporary hit points never stack; the greater old/new value is retained. */
    public synchronized int grantTemporaryHitPoints(String targetCombatantId, int amount) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        if (amount < 0) throw rule("Temporary hit points cannot be negative");
        beginCommand();
        int before = target.temporaryHitPoints;
        target.temporaryHitPoints = Math.max(before, amount);
        append(EventType.TEMPORARY_HIT_POINTS_GRANTED, "", targetCombatantId, details(
                "offered", amount, "before", before, "retained", target.temporaryHitPoints));
        return target.temporaryHitPoints;
    }

    /** Returns false when the target is immune; the immunity decision is still audited. */
    public synchronized boolean applyCondition(String targetCombatantId, ConditionInstance condition) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        Objects.requireNonNull(condition, "condition");
        combatant(condition.sourceCombatantId());
        if (conditionIdExists(condition.id())) throw rule("Duplicate condition instance id: " + condition.id());
        if (condition.duration().expiry() == ConditionExpiry.CONCENTRATION) {
            MutableCombatant owner = combatant(condition.concentrationOwnerId());
            if (owner.concentration == null) {
                throw rule("The condition's concentration owner is not concentrating");
            }
        }
        beginCommand();
        if (target.snapshot.conditionImmunities().contains(condition.type())) {
            append(EventType.CONDITION_IMMUNE, condition.sourceCombatantId(), targetCombatantId,
                    details("conditionId", condition.id(), "condition", condition.type()));
            return false;
        }
        target.conditions.add(condition);
        append(EventType.CONDITION_APPLIED, condition.sourceCombatantId(), targetCombatantId, details(
                "conditionId", condition.id(),
                "condition", condition.type(),
                "sourceAbilityId", condition.sourceAbilityId(),
                "expiry", condition.duration().expiry(),
                "remaining", condition.duration().remainingOccurrences()));
        if (target.concentration != null && incapacitates(condition.type())) {
            endConcentrationInternal(targetCombatantId, "incapacitated by " + condition.type());
        }
        return true;
    }

    public synchronized ConditionInstance addCondition(
            String conditionId,
            String targetCombatantId,
            app.d6d.domain.combat.ConditionType type,
            String sourceCombatantId,
            String sourceAbilityId,
            ConditionDuration duration,
            String concentrationOwnerId,
            String note) {
        ConditionInstance condition = new ConditionInstance(conditionId, type, sourceCombatantId, sourceAbilityId,
                state.round, duration, concentrationOwnerId, note);
        applyCondition(targetCombatantId, condition);
        return condition;
    }

    public synchronized boolean removeCondition(String targetCombatantId, String conditionInstanceId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        ConditionInstance condition = target.conditions.stream()
                .filter(candidate -> candidate.id().equals(conditionInstanceId)).findFirst().orElse(null);
        if (condition == null) return false;
        beginCommand();
        target.conditions.remove(condition);
        append(EventType.CONDITION_REMOVED, "", targetCombatantId,
                details("conditionId", condition.id(), "condition", condition.type()));
        return true;
    }

    /** Replaces any existing concentration and expires all effects tied to the previous one. */
    public synchronized void beginConcentration(String ownerCombatantId, String abilityId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant owner = combatant(ownerCombatantId);
        ability(owner, abilityId);
        if (owner.currentHitPoints == 0 || owner.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot begin concentration");
        }
        beginCommand();
        if (owner.concentration != null) {
            endConcentrationInternal(ownerCombatantId, "replaced");
        }
        owner.concentration = new ConcentrationState(abilityId, state.round);
        append(EventType.CONCENTRATION_STARTED, ownerCombatantId, "",
                details("abilityId", abilityId));
    }

    public synchronized boolean endConcentration(String ownerCombatantId, String reason) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant owner = combatant(ownerCombatantId);
        if (owner.concentration == null) return false;
        beginCommand();
        endConcentrationInternal(ownerCombatantId, reason == null ? "manual" : reason);
        return true;
    }

    public synchronized void spendMovement(String combatantId, int feet) {
        requireStatus(CombatStatus.ACTIVE);
        requireCurrentCombatant(combatantId);
        TurnBudget budget = budget(combatantId);
        if (feet < 0 || feet > budget.movementRemainingFeet()) {
            throw rule("Movement exceeds the remaining budget");
        }
        beginCommand();
        state.turnBudgets.put(combatantId, budget.spendMovement(feet));
        append(EventType.MOVEMENT_SPENT, combatantId, "", details(
                "feet", feet, "remaining", state.turnBudgets.get(combatantId).movementRemainingFeet()));
    }

    public synchronized void spendAction(String combatantId, ActivationCost cost) {
        requireStatus(CombatStatus.ACTIVE);
        Objects.requireNonNull(cost, "cost");
        validateActivationCost(combatantId, cost, false);
        if (cost == ActivationCost.NONE) throw rule("NONE does not spend a turn resource");
        beginCommand();
        consumeActivationCost(combatantId, cost, false);
    }

    /** Applica una capacità automatica priva di bersaglio, consumandone costo e risorsa in un solo comando. */
    public synchronized void activateAbility(String combatantId, String abilityId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant combatant = combatant(combatantId);
        AbilityDefinition ability = ability(combatant, abilityId);
        if (ability.passive()) {
            throw rule("A passive trait cannot be activated: " + ability.id());
        }
        if (ability.effect() == AbilityEffect.NONE) {
            throw rule("Ability has no automatic effect: " + ability.id());
        }
        if (ability.automationStatus() != AutomationStatus.AUTOMATED
                || ability.resolutionMethod() != ResolutionMethod.AUTOMATIC) {
            throw rule("Ability requires a different resolution: " + ability.id());
        }
        validateActivationCost(combatantId, ability.activationCost(), ability.spellOrCantrip());
        validateAbilityResource(combatant, ability);
        if (ability.effect() == AbilityEffect.GRANT_NON_MAGIC_ACTION) {
            requireCurrentCombatant(combatantId);
            if (budget(combatantId).actionSurgeUsedThisTurn()) {
                throw rule("Action Surge can be used only once in the same turn");
            }
        }

        beginCommand();
        try {
            consumeActivationCost(combatantId, ability.activationCost(), ability.spellOrCantrip());
            consumeAbilityResource(combatantId, combatant, ability);
            switch (ability.effect()) {
                case GRANT_NON_MAGIC_ACTION -> {
                    state.turnBudgets.put(combatantId, budget(combatantId).grantNonMagicAction());
                    append(EventType.ACTION_GRANTED, combatantId, "", details(
                            "abilityId", ability.id(),
                            "abilityName", ability.name(),
                            "restriction", "NON_MAGIC"));
                }
                case NONE -> throw new IllegalStateException("Validated effect unexpectedly disappeared");
            }
            append(EventType.ABILITY_ACTIVATED, combatantId, "", details(
                    "abilityId", ability.id(), "abilityName", ability.name()));
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized void markSpellSlotSpent(String combatantId) {
        requireStatus(CombatStatus.ACTIVE);
        requireCurrentCombatant(combatantId);
        TurnBudget budget = budget(combatantId);
        if (budget.spellSlotSpentThisTurn()) throw rule("A spell slot was already spent this turn");
        beginCommand();
        state.turnBudgets.put(combatantId, budget.markSpellSlotSpent());
        append(EventType.SPELL_SLOT_SPENT, combatantId, "", Map.of());
    }

    public synchronized void endTurn() {
        requireStatus(CombatStatus.ACTIVE);
        beginCommand();
        endTurnInternal();
    }

    /** Chiude il gruppo corrente; il chiamante ha gia' aperto il comando annullabile. */
    private void endTurnInternal() {
        List<String> ending = currentCombatantIds();
        List<String> endingGroup = currentTurnGroup();
        // Il turno finisce per tutto il gruppo: in parita' i combattenti hanno
        // giocato insieme e chiudono insieme. Anche il membro a 0 PF attraversa
        // comunque la soglia temporale di fine turno, pur senza ricevere azioni.
        for (String endingCombatant : endingGroup) {
            if (ending.contains(endingCombatant)) {
                append(EventType.TURN_ENDED, endingCombatant, "", details("round", state.round));
            }
            processConditionBoundary(endingCombatant, false);
        }
        // I gruppi composti soltanto da combattenti a 0 PF restano visibili
        // nell'iniziativa, ma non ricevono un turno. La scansione e' limitata a un
        // giro completo: anche uno stato eccezionale con tutti a 0 PF non puo'
        // innescare una ricorsione o un ciclo infinito.
        if (advanceToNextPlayableTurn()) {
            startTurnInternal();
        }
    }

    /**
     * Riordina i turni a scontro gia' avviato.
     *
     * <p>E' una correzione dichiarata del tavolo: chi sta agendo ora resta il
     * combattente corrente, cambia solo la sua posizione nella coda. Passa dal
     * registro ed e' annullabile come ogni altro comando.</p>
     */
    public synchronized void reorderTurns(List<String> orderedCombatantIds) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("Turns can only be reordered during active play");
        }
        Objects.requireNonNull(orderedCombatantIds, "orderedCombatantIds");
        List<String> order = List.copyOf(orderedCombatantIds);
        Set<String> expected = state.combatants.keySet();
        if (order.size() != expected.size() || new HashSet<>(order).size() != order.size()
                || !new HashSet<>(order).equals(expected)) {
            throw rule("Turn order must contain every combatant exactly once");
        }
        String anchor = currentTurnAnchor();
        beginCommand();
        state.initiativeOrder.clear();
        state.initiativeOrder.addAll(order);
        List<List<String>> groups = turnGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(anchor)) {
                state.turnIndex = i;
                break;
            }
        }
        state.turnIndex = Math.max(0, Math.min(state.turnIndex, groups.size() - 1));
        append(EventType.INITIATIVE_ORDER_SET, "", "", details("order", String.join(",", order)));
    }

    /**
     * Sposta a mano il turno corrente su un combattente scelto.
     *
     * <p>Il gruppo scelto riceve un budget fresco cosi' puo' agire; gli effetti
     * d'inizio turno delle condizioni non vengono riattivati, perche' e' una
     * correzione del tavolo e non un turno giocato per intero.</p>
     */
    public synchronized void setCurrentTurn(String combatantId) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("The current turn can only be changed during active play");
        }
        if (combatant(combatantId).currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot take a turn");
        }
        List<List<String>> groups = turnGroups();
        int target = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(combatantId)) {
                target = i;
                break;
            }
        }
        if (target < 0) throw rule("Combatant is not in the current initiative order");
        if (target == state.turnIndex) {
            return;
        }
        beginCommand();
        state.turnIndex = target;
        for (String id : livingCombatants(groups.get(target))) {
            MutableCombatant occupant = combatant(id);
            int speed = Math.max(0, occupant.snapshot.speedFeet() - 5 * occupant.exhaustionLevel);
            state.turnBudgets.put(id, TurnBudget.fresh(speed, occupant.snapshot.attacksPerAction()));
            append(EventType.TURN_STARTED, id, "", details("round", state.round));
        }
    }

    /**
     * Cambia a mano l'iniziativa di un combattente a scontro avviato e riordina la
     * coda di conseguenza.
     *
     * <p>La coda viene riordinata per punteggio decrescente (le parita' seguono
     * l'ordine di inserimento, come all'avvio). Chi sta agendo ora resta il
     * combattente corrente, anche se la sua iniziativa e' cambiata.</p>
     */
    public synchronized void overrideInitiative(String combatantId, int total) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("Initiative can only be overridden during active play");
        }
        combatant(combatantId);
        String anchor = currentTurnAnchor();
        beginCommand();
        state.initiativeScores.put(combatantId, total);
        rebuildInitiativeOrder();
        List<List<String>> groups = turnGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(anchor)) {
                state.turnIndex = i;
                break;
            }
        }
        state.turnIndex = Math.max(0, Math.min(state.turnIndex, groups.size() - 1));
        append(EventType.INITIATIVE_SET, combatantId, "", details("total", total));
    }

    /**
     * Dichiara se i pareggi d'iniziativa vengono giocati insieme.
     *
     * <p>Il regolamento fa risolvere le parita' al DM: renderle simultanee resta
     * una scelta del tavolo, quindi e' una bandiera esplicita e registrata.</p>
     */
    public synchronized void setSimultaneousTies(boolean simultaneous) {
        requireSetupPhase();
        if (state.simultaneousTies == simultaneous) return;
        beginCommand();
        state.simultaneousTies = simultaneous;
        append(EventType.INITIATIVE_ORDER_SET, "", "", details(
                "simultaneousTies", simultaneous,
                "groups", turnGroups().size()));
    }

    /**
     * Restores the last pre-command game snapshot and exact RNG state. Previous audit events are retained and an
     * UNDO_PERFORMED event is appended, so the audit itself is never rewritten.
     */
    public synchronized boolean undo() {
        if (undoStack.isEmpty()) return false;
        long revertedRevision = state.revision;
        Checkpoint checkpoint = undoStack.pop();
        state = checkpoint.state.copy();
        dice.restore(checkpoint.randomState);
        state.revision = ++revisionCounter;
        append(EventType.UNDO_PERFORMED, "", "", details(
                "revertedRevision", revertedRevision,
                "restoredRandomState", checkpoint.randomState));
        return true;
    }

    private List<DamageComponent> resolveAttackDamage(
            AbilityDefinition ability,
            List<Integer> manualValues,
            boolean critical,
            String attackerId,
            String targetId) {
        List<DamageComponent> result = new ArrayList<>();
        for (int index = 0; index < ability.damage().size(); index++) {
            DamageFormula formula = ability.damage().get(index);
            int amount;
            Map<String, String> eventDetails;
            if (!manualValues.isEmpty()) {
                amount = manualValues.get(index);
                eventDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "type", formula.type(),
                        "formula", formula.usesDice() ? formula.dice().notation() : formula.fixedAmount(),
                        "amount", amount,
                        "total", amount,
                        "source", "manual");
            } else if (formula.usesDice()) {
                DiceRollResult roll = dice.roll(formula.dice(), critical);
                amount = Math.max(0, roll.total());
                eventDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "type", formula.type(),
                        "formula", formula.dice().notation(),
                        "dice", roll.dice(),
                        "modifier", roll.modifier(),
                        "amount", amount,
                        "total", amount,
                        "critical", critical);
            } else {
                amount = formula.fixedAmount();
                eventDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "type", formula.type(),
                        "formula", formula.fixedAmount(),
                        "amount", amount,
                        "total", amount,
                        "source", "fixed",
                        "critical", critical);
            }
            result.add(new DamageComponent(formula.type(), amount));
            append(EventType.DAMAGE_ROLLED, attackerId, targetId, eventDetails);
        }
        return List.copyOf(result);
    }

    private DamageResult applyDamageInternal(
            String sourceCombatantId,
            String targetCombatantId,
            List<DamageComponent> components,
            boolean critical,
            D20RollInput concentrationRoll) {
        MutableCombatant target = combatant(targetCombatantId);
        List<DamageComponentResult> resolved = new ArrayList<>();
        long totalRawLong = 0;
        long totalAdjustedLong = 0;
        for (DamageComponent component : components) {
            totalRawLong = Math.addExact(totalRawLong, component.amount());
            boolean immune = target.snapshot.damageImmunities().contains(component.type());
            boolean resistant = !immune && target.snapshot.resistances().contains(component.type());
            boolean vulnerable = !immune && target.snapshot.vulnerabilities().contains(component.type());
            long adjustedLong = immune ? 0L : component.amount();
            if (resistant) adjustedLong /= 2;
            if (vulnerable) adjustedLong = Math.multiplyExact(adjustedLong, 2L);
            if (adjustedLong > Integer.MAX_VALUE) {
                throw rule("A damage component exceeds the supported range");
            }
            int adjusted = (int) adjustedLong;
            totalAdjustedLong = Math.addExact(totalAdjustedLong, adjustedLong);
            if (totalRawLong > Integer.MAX_VALUE || totalAdjustedLong > Integer.MAX_VALUE) {
                throw rule("Total damage exceeds the supported range");
            }
            DamageComponentResult componentResult = new DamageComponentResult(component.type(), component.amount(),
                    adjusted, immune, resistant, vulnerable);
            resolved.add(componentResult);
            append(EventType.DAMAGE_APPLIED, sourceCombatantId, targetCombatantId, details(
                    "type", component.type(),
                    "raw", component.amount(),
                    "adjusted", adjusted,
                    "immune", immune,
                    "resistant", resistant,
                    "vulnerable", vulnerable));
        }

        int totalRaw = (int) totalRawLong;
        int totalAdjusted = (int) totalAdjustedLong;

        int temporaryAbsorbed = Math.min(target.temporaryHitPoints, totalAdjusted);
        target.temporaryHitPoints -= temporaryAbsorbed;
        int remaining = totalAdjusted - temporaryAbsorbed;
        int hitPointsBefore = target.currentHitPoints;
        int hitPointsLost = Math.min(hitPointsBefore, remaining);
        target.currentHitPoints -= hitPointsLost;
        append(EventType.DAMAGE_APPLIED, sourceCombatantId, targetCombatantId, details(
                "totalRaw", totalRaw,
                "totalAdjusted", totalAdjusted,
                "temporaryAbsorbed", temporaryAbsorbed,
                "hitPointsLost", hitPointsLost,
                "hitPointsAfter", target.currentHitPoints,
                "critical", critical));
        if (target.currentHitPoints == 0 && hitPointsLost > 0) {
            append(EventType.ZERO_HIT_POINTS, sourceCombatantId, targetCombatantId, details("critical", critical));
        }

        applyDamageAtZeroHitPoints(sourceCombatantId, targetCombatantId, target, remaining, hitPointsBefore,
                hitPointsLost, critical);

        Optional<ConcentrationCheckResult> concentrationCheck = Optional.empty();
        if (totalAdjusted > 0 && target.concentration != null && target.currentHitPoints == 0) {
            endConcentrationInternal(targetCombatantId, "zero hit points");
        } else if (totalAdjusted > 0 && target.concentration != null) {
            int difficultyClass = Math.min(30, Math.max(10, totalAdjusted / 2));
            D20RollResult roll = rollD20For(target, concentrationRoll, target.snapshot.constitutionSaveBonus());
            boolean maintained = roll.total() >= difficultyClass;
            ConcentrationCheckResult check = new ConcentrationCheckResult(difficultyClass, roll, maintained);
            concentrationCheck = Optional.of(check);
            append(EventType.CONCENTRATION_CHECKED, targetCombatantId, "", merge(
                    rollDetails(roll), details("difficultyClass", difficultyClass, "maintained", maintained)));
            if (!maintained) endConcentrationInternal(targetCombatantId, "failed save");
        }

        return new DamageResult(sourceCombatantId, targetCombatantId, resolved, totalRaw, totalAdjusted,
                temporaryAbsorbed, hitPointsLost, target.currentHitPoints, critical, concentrationCheck);
    }

    /**
     * Conseguenze del danno su una creatura che si trova, o finisce, a 0 punti ferita.
     *
     * <p>Subire danni elimina lo stato Stable. Se il danno residuo dopo essere
     * arrivati a zero raggiunge i punti ferita massimi la morte e' immediata;
     * altrimenti un colpo incassato gia' a 0 PF causa un fallimento contro morte,
     * due se il colpo e' critico.</p>
     */
    private void applyDamageAtZeroHitPoints(
            String sourceCombatantId,
            String targetCombatantId,
            MutableCombatant target,
            int remaining,
            int hitPointsBefore,
            int hitPointsLost,
            boolean critical) {
        if (remaining <= 0 || target.currentHitPoints != 0) {
            return;
        }

        if (target.deathSaves.stable()) {
            target.deathSaves = DeathSaveState.none();
        }

        int excess = remaining - hitPointsLost;
        if (excess >= target.snapshot.maxHitPoints()) {
            target.deathSaves = target.deathSaves.withFailures(DeathSaveState.REQUIRED);
            append(EventType.DIED, sourceCombatantId, targetCombatantId, details(
                    "cause", "massive damage", "excess", excess));
            return;
        }

        if (hitPointsBefore == 0) {
            int failures = critical ? 2 : 1;
            target.deathSaves = target.deathSaves.withFailures(failures);
            append(EventType.DEATH_SAVE_ROLLED, sourceCombatantId, targetCombatantId, details(
                    "source", "damage",
                    "failures", failures,
                    "totalFailures", target.deathSaves.failures()));
            if (target.deathSaves.dead()) {
                append(EventType.DIED, sourceCombatantId, targetCombatantId, details("cause", "death saves"));
            }
        }
    }

    /** Avvia il turno per ogni membro del gruppo corrente. */
    private void startTurnInternal() {
        List<String> active = currentCombatantIds();
        for (String combatantId : currentTurnGroup()) {
            // Un membro a 0 PF non riceve budget ne' evento di turno, ma la soglia
            // d'inizio continua a far avanzare correttamente condizioni ed effetti.
            if (!active.contains(combatantId)) {
                processConditionBoundary(combatantId, true);
                continue;
            }
            MutableCombatant combatant = combatant(combatantId);
            // La velocita' e' ridotta da Exhaustion: il budget parte da quella effettiva.
            int speed = Math.max(0, combatant.snapshot.speedFeet() - 5 * combatant.exhaustionLevel);
            state.turnBudgets.put(combatantId, TurnBudget.fresh(speed, combatant.snapshot.attacksPerAction()));
            processConditionBoundary(combatantId, true);
            append(EventType.TURN_STARTED, combatantId, "", details("round", state.round));
        }
    }

    private void processConditionBoundary(String activeCombatantId, boolean start) {
        for (Map.Entry<String, MutableCombatant> targetEntry : state.combatants.entrySet()) {
            String targetId = targetEntry.getKey();
            MutableCombatant target = targetEntry.getValue();
            List<ConditionInstance> original = new ArrayList<>(target.conditions);
            for (ConditionInstance condition : original) {
                if (!matchesBoundary(condition, targetId, activeCombatantId, start)) continue;
                if (condition.duration().remainingOccurrences() == 1) {
                    target.conditions.remove(condition);
                    append(EventType.CONDITION_EXPIRED, condition.sourceCombatantId(), targetId, details(
                            "conditionId", condition.id(), "condition", condition.type()));
                } else {
                    int index = target.conditions.indexOf(condition);
                    target.conditions.set(index, condition.withDuration(condition.duration().decrement()));
                }
            }
        }
    }

    private static boolean matchesBoundary(
            ConditionInstance condition, String targetId, String activeCombatantId, boolean start) {
        return switch (condition.duration().expiry()) {
            case START_OF_TARGET_TURN -> start && targetId.equals(activeCombatantId);
            case END_OF_TARGET_TURN -> !start && targetId.equals(activeCombatantId);
            case START_OF_SOURCE_TURN -> start && condition.sourceCombatantId().equals(activeCombatantId);
            case END_OF_SOURCE_TURN -> !start && condition.sourceCombatantId().equals(activeCombatantId);
            case MANUAL, CONCENTRATION -> false;
        };
    }

    private void endConcentrationInternal(String ownerCombatantId, String reason) {
        MutableCombatant owner = combatant(ownerCombatantId);
        ConcentrationState ended = owner.concentration;
        if (ended == null) return;
        owner.concentration = null;
        append(EventType.CONCENTRATION_ENDED, ownerCombatantId, "", details(
                "abilityId", ended.abilityId(), "reason", reason));
        for (Map.Entry<String, MutableCombatant> targetEntry : state.combatants.entrySet()) {
            List<ConditionInstance> dependent = targetEntry.getValue().conditions.stream()
                    .filter(condition -> condition.duration().expiry() == ConditionExpiry.CONCENTRATION
                            && ownerCombatantId.equals(condition.concentrationOwnerId()))
                    .collect(Collectors.toList());
            for (ConditionInstance condition : dependent) {
                targetEntry.getValue().conditions.remove(condition);
                append(EventType.CONDITION_EXPIRED, ownerCombatantId, targetEntry.getKey(), details(
                        "conditionId", condition.id(), "condition", condition.type(), "reason", "concentration"));
            }
        }
    }

    private void validateActivationCost(String combatantId, ActivationCost cost) {
        validateActivationCost(combatantId, cost, false);
    }

    private void validateActivationCost(String combatantId, ActivationCost cost, boolean magicAction) {
        Objects.requireNonNull(cost, "cost");
        if (combatant(combatantId).currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot act");
        }
        TurnBudget budget = budget(combatantId);
        switch (cost) {
            case ACTION -> {
                requireCurrentCombatant(combatantId);
                if (!budget.canUseAction(magicAction)) throw rule("Action already spent");
            }
            case BONUS_ACTION -> {
                requireCurrentCombatant(combatantId);
                if (!budget.bonusActionAvailable()) throw rule("Bonus action already spent");
            }
            case REACTION -> {
                if (!budget.reactionAvailable()) throw rule("Reaction already spent");
            }
            case LEGENDARY_ACTION -> throw rule("Legendary action pools are not part of this vertical slice");
            case NONE -> { }
        }
    }

    /**
     * An attack paid with an Action starts the Attack action on its first strike.
     * Further strikes from that same action spend only its remaining attack count.
     */
    private void validateAttackActivationCost(
            String combatantId, ActivationCost cost, boolean magicAction) {
        if (cost != ActivationCost.ACTION || magicAction) {
            validateActivationCost(combatantId, cost, magicAction);
            return;
        }
        MutableCombatant combatant = combatant(combatantId);
        if (combatant.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot act");
        }
        requireCurrentCombatant(combatantId);
        TurnBudget budget = budget(combatantId);
        if (budget.attackActionInProgress()) {
            if (budget.attacksRemaining() == 0) {
                throw rule("No attacks remain in the Attack action");
            }
        } else if (!budget.canUseAction(false)) {
            throw rule("Action already spent");
        }
    }

    private void consumeAttackActivationCost(
            String combatantId, ActivationCost cost, boolean magicAction) {
        if (cost != ActivationCost.ACTION || magicAction) {
            consumeActivationCost(combatantId, cost, magicAction);
            return;
        }
        TurnBudget current = budget(combatantId);
        boolean startsAttackAction = !current.attackActionInProgress();
        TurnBudget updated = startsAttackAction
                ? current.startAttackAction(combatant(combatantId).snapshot.attacksPerAction())
                : current.useAttack();
        state.turnBudgets.put(combatantId, updated);
        if (startsAttackAction) {
            append(EventType.ACTION_SPENT, combatantId, "", details("cost", cost));
        }
    }

    private void consumeActivationCost(String combatantId, ActivationCost cost) {
        consumeActivationCost(combatantId, cost, false);
    }

    private void consumeActivationCost(String combatantId, ActivationCost cost, boolean magicAction) {
        TurnBudget budget = budget(combatantId);
        TurnBudget updated = switch (cost) {
            case ACTION -> budget.useAction(magicAction);
            case BONUS_ACTION -> budget.useBonusAction();
            case REACTION -> budget.useReaction();
            case NONE -> budget;
            case LEGENDARY_ACTION -> throw rule("Legendary action pools are not part of this vertical slice");
        };
        state.turnBudgets.put(combatantId, updated);
        if (cost != ActivationCost.NONE) {
            append(EventType.ACTION_SPENT, combatantId, "", details("cost", cost));
        }
    }

    private void validateAbilityResource(MutableCombatant combatant, AbilityDefinition ability) {
        if (ability.resourceCost() == 0) return;
        CombatResourceState resource = combatant.resources.get(ability.resourceId());
        if (resource == null) {
            throw rule("Ability resource is missing: " + ability.resourceId());
        }
        if (resource.remaining() < ability.resourceCost()) {
            throw rule("Not enough uses of " + resource.name());
        }
    }

    private void consumeAbilityResource(
            String combatantId, MutableCombatant combatant, AbilityDefinition ability) {
        if (ability.resourceCost() == 0) return;
        CombatResourceState resource = combatant.resources.get(ability.resourceId());
        CombatResourceState updated = resource.spend(ability.resourceCost());
        combatant.resources.put(updated.id(), updated);
        append(EventType.RESOURCE_SPENT, combatantId, "", details(
                "abilityId", ability.id(),
                "abilityName", ability.name(),
                "resourceId", updated.id(),
                "resourceName", updated.name(),
                "cost", ability.resourceCost(),
                "remaining", updated.remaining(),
                "maximum", updated.maximum()));
    }

    private D20RollResult rollD20(D20RollInput input, int modifier) {
        if (input.source() == RollSource.MANUAL) {
            int natural = input.manualNaturalRoll();
            int total = checkedTotal(natural, modifier, "D20 total");
            return new D20RollResult(RollSource.MANUAL, input.mode(), List.of(natural),
                    natural, modifier, total);
        }
        List<Integer> rolled = dice.rollD20(input.mode());
        int natural = selectedD20(rolled, input.mode());
        int total = checkedTotal(natural, modifier, "D20 total");
        return new D20RollResult(RollSource.DIGITAL, input.mode(), rolled, natural, modifier, total);
    }

    /**
     * Tiro d20 effettuato da un combattente identificato.
     *
     * <p>Applica la penalita' di Exhaustion, che vale per <em>tutti</em> i D20 Test
     * — prove, attacchi e tiri salvezza — nella misura di −2 per livello.</p>
     */
    private D20RollResult rollD20For(MutableCombatant roller, D20RollInput input, int modifier) {
        int penalty = -2 * roller.exhaustionLevel;
        return rollD20(input, checkedTotal(modifier, penalty, "D20 modifier"));
    }

    private static int checkedTotal(int first, int second, String label) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw rule(label + " exceeds the supported range");
        }
    }

    private static int selectedD20(List<Integer> dice, D20Mode mode) {
        return mode == D20Mode.ADVANTAGE
                ? dice.stream().mapToInt(Integer::intValue).max().orElseThrow()
                : mode == D20Mode.DISADVANTAGE
                        ? dice.stream().mapToInt(Integer::intValue).min().orElseThrow()
                        : dice.get(0);
    }

    /** Applica uno svantaggio imposto dalle regole, annullando un eventuale vantaggio. */
    private static D20Mode imposeDisadvantage(D20Mode requested, boolean imposed) {
        if (!imposed) return requested;
        return requested == D20Mode.ADVANTAGE ? D20Mode.NORMAL : D20Mode.DISADVANTAGE;
    }

    private static D20RollInput imposeDisadvantage(D20RollInput input, boolean imposed) {
        D20Mode effectiveMode = imposeDisadvantage(input.mode(), imposed);
        if (effectiveMode == input.mode()) return input;
        return new D20RollInput(input.source(), effectiveMode, input.manualNaturalRoll());
    }

    private static boolean usesStrengthOrDexterityForAttack(AbilityDefinition ability) {
        return ability.attackAbility() == SaveAbility.STRENGTH
                || ability.attackAbility() == SaveAbility.DEXTERITY;
    }

    private static AttackOutcome attackOutcome(D20RollResult roll, int armorClass) {
        if (roll.naturalRoll() == 1) return AttackOutcome.MISS;
        if (roll.naturalRoll() == 20) return AttackOutcome.CRITICAL_HIT;
        return roll.total() >= armorClass ? AttackOutcome.HIT : AttackOutcome.MISS;
    }

    private static boolean incapacitates(app.d6d.domain.combat.ConditionType type) {
        return switch (type) {
            case INCAPACITATED, PARALYZED, PETRIFIED, STUNNED, UNCONSCIOUS -> true;
            default -> false;
        };
    }

    private void beginCommand() {
        undoStack.push(new Checkpoint(
                state.copy(), dice.state(), audit.size(), nextEventSequence, revisionCounter));
        state.revision = ++revisionCounter;
    }

    /** Rolls back a command that failed after validation, including RNG, audit and revision counters. */
    private void rollbackFailedCommand() {
        Checkpoint checkpoint = undoStack.pop();
        state = checkpoint.state.copy();
        dice.restore(checkpoint.randomState);
        while (audit.size() > checkpoint.auditSize) {
            audit.remove(audit.size() - 1);
        }
        nextEventSequence = checkpoint.nextEventSequence;
        revisionCounter = checkpoint.revisionCounter;
    }

    private void append(EventType type, String actorId, String targetId, Map<String, String> details) {
        audit.add(new CombatEvent(nextEventSequence++, state.revision, type, state.round,
                actorId, targetId, details));
    }

    private MutableCombatant combatant(String id) {
        MutableCombatant result = state.combatants.get(id);
        if (result == null) throw rule("Unknown combatant: " + id);
        return result;
    }

    private AbilityDefinition ability(MutableCombatant combatant, String abilityId) {
        AbilityDefinition ability = combatant.snapshot.abilities().stream()
                .filter(candidate -> candidate.id().equals(abilityId)).findFirst()
                .orElseThrow(() -> rule("Unknown ability: " + abilityId));
        // Un tratto passivo vale sempre: non si attiva e non consuma il turno.
        if (ability.passive()) {
            throw rule("A passive trait cannot be activated: " + abilityId);
        }
        return ability;
    }

    private TurnBudget budget(String combatantId) {
        combatant(combatantId);
        return state.turnBudgets.get(combatantId);
    }

    private void rebuildInitiativeOrder() {
        List<String> resorted = state.rosterOrder.stream()
                .sorted(Comparator
                        .<String>comparingInt(id -> state.initiativeScores.get(id)).reversed()
                        .thenComparingInt(state.rosterOrder::indexOf))
                .collect(Collectors.toList());
        state.initiativeOrder.clear();
        state.initiativeOrder.addAll(resorted);
    }

    /**
     * L'ordine dei turni raggruppato.
     *
     * <p>Senza turni simultanei ogni combattente forma un gruppo da solo; con i
     * turni simultanei attivi i pareggi d'iniziativa consecutivi formano un gruppo
     * unico che agisce insieme.</p>
     */
    private List<List<String>> turnGroups() {
        if (!state.simultaneousTies) {
            return state.initiativeOrder.stream().map(List::<String>of).collect(Collectors.toList());
        }
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Integer currentScore = null;
        for (String id : state.initiativeOrder) {
            Integer score = state.initiativeScores.get(id);
            if (!current.isEmpty() && Objects.equals(score, currentScore)) {
                current.add(id);
            } else {
                if (!current.isEmpty()) groups.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(id);
                currentScore = score;
            }
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return groups;
    }

    /** Tutti i combattenti che stanno giocando il turno corrente. */
    private List<String> currentCombatantIds() {
        return livingCombatants(currentTurnGroup());
    }

    /** Gruppo strutturale corrente, inclusi i membri a 0 PF mostrati nella striscia. */
    private List<String> currentTurnGroup() {
        List<List<String>> groups = turnGroups();
        if (state.turnIndex < 0 || state.turnIndex >= groups.size()) throw rule("There is no current turn");
        return groups.get(state.turnIndex);
    }

    private String currentCombatantId() {
        return currentCombatantIds().get(0);
    }

    /** In un turno simultaneo ognuno dei combattenti in parita' puo' agire. */
    private void requireCurrentCombatant(String combatantId) {
        if (combatant(combatantId).currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot act");
        }
        if (!currentCombatantIds().contains(combatantId)) {
            throw rule("It is not " + combatantId + "'s turn");
        }
    }

    /** Membri ancora in piedi di un gruppo strutturale d'iniziativa. */
    private List<String> livingCombatants(List<String> group) {
        return group.stream()
                .filter(id -> combatant(id).currentHitPoints > 0)
                .collect(Collectors.toList());
    }

    /** Primo attore vivo del gruppo, oppure il suo primo membro per le sole correzioni d'ordine. */
    private String currentTurnAnchor() {
        List<List<String>> groups = turnGroups();
        if (state.turnIndex < 0 || state.turnIndex >= groups.size()) {
            throw rule("There is no current turn");
        }
        List<String> group = groups.get(state.turnIndex);
        List<String> living = livingCombatants(group);
        return living.isEmpty() ? group.get(0) : living.get(0);
    }

    /** All'avvio salta gli eventuali gruppi iniziali interamente a 0 PF senza chiudere il round. */
    private boolean seekFirstPlayableTurn() {
        List<List<String>> groups = turnGroups();
        for (int index = 0; index < groups.size(); index++) {
            if (!livingCombatants(groups.get(index)).isEmpty()) {
                state.turnIndex = index;
                return true;
            }
            processSkippedTurn(groups.get(index));
        }
        return false;
    }

    /**
     * Avanza fino al prossimo gruppo con almeno un membro vivo.
     *
     * Il passaggio oltre l'ultimo gruppo chiude e riapre il round esattamente una
     * volta. Se nessuno e' in piedi, dopo un giro completo restituisce {@code false}.
     */
    private boolean advanceToNextPlayableTurn() {
        List<List<String>> groups = turnGroups();
        for (int checked = 0; checked < groups.size(); checked++) {
            state.turnIndex++;
            if (state.turnIndex >= groups.size()) {
                append(EventType.ROUND_ENDED, "", "", details("round", state.round));
                state.round++;
                state.turnIndex = 0;
                append(EventType.ROUND_STARTED, "", "", details("round", state.round));
            }
            if (!livingCombatants(groups.get(state.turnIndex)).isEmpty()) return true;
            processSkippedTurn(groups.get(state.turnIndex));
        }
        return false;
    }

    /** Fa trascorrere le soglie temporali di un gruppo saltato, senza concedere azioni. */
    private void processSkippedTurn(List<String> group) {
        for (String combatantId : group) {
            processConditionBoundary(combatantId, true);
            processConditionBoundary(combatantId, false);
        }
    }

    private boolean conditionIdExists(String conditionId) {
        return state.combatants.values().stream()
                .flatMap(combatant -> combatant.conditions.stream())
                .anyMatch(condition -> condition.id().equals(conditionId));
    }

    private void requireSetupPhase() {
        if (state.status != CombatStatus.DRAFT && state.status != CombatStatus.READY) {
            throw rule("Initiative can only be changed in DRAFT or READY");
        }
    }

    private void requireStatus(CombatStatus required) {
        if (state.status != required) {
            throw rule("Command requires " + required + " but encounter is " + state.status);
        }
    }

    private static CombatRuleException rule(String message) {
        return new CombatRuleException(message);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    private static Map<String, String> rollDetails(D20RollResult roll) {
        return details(
                "source", roll.source(),
                "mode", roll.mode(),
                "dice", roll.dice(),
                "natural", roll.naturalRoll(),
                "modifier", roll.modifier(),
                "total", roll.total());
    }

    private static Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> result = new LinkedHashMap<>(first);
        result.putAll(second);
        return result;
    }

    private static Map<String, String> details(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Details need key/value pairs");
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), String.valueOf(pairs[index + 1]));
        }
        return result;
    }

    private record Checkpoint(
            MutableState state,
            long randomState,
            int auditSize,
            long nextEventSequence,
            long revisionCounter) { }

    private static final class MutableCombatant {
        private CombatantSnapshot snapshot;
        private int currentHitPoints;
        private int temporaryHitPoints;
        private final List<ConditionInstance> conditions;
        private ConcentrationState concentration;
        private DeathSaveState deathSaves = DeathSaveState.none();
        private int exhaustionLevel;
        private final LinkedHashMap<String, CombatResourceState> resources;

        private MutableCombatant(
                CombatantSnapshot snapshot,
                int currentHitPoints,
                int temporaryHitPoints,
                List<ConditionInstance> conditions,
                ConcentrationState concentration) {
            this.snapshot = snapshot;
            this.currentHitPoints = currentHitPoints;
            this.temporaryHitPoints = temporaryHitPoints;
            this.conditions = new ArrayList<>(conditions);
            this.concentration = concentration;
            this.resources = new LinkedHashMap<>();
            snapshot.resources().forEach(resource -> this.resources.put(resource.id(), resource));
        }

        private static MutableCombatant from(CombatantSnapshot snapshot) {
            return new MutableCombatant(snapshot, snapshot.initialHitPoints(), snapshot.initialTemporaryHitPoints(),
                    List.of(), null);
        }

        private static MutableCombatant from(CombatantState state) {
            MutableCombatant restored = new MutableCombatant(state.snapshot(), state.currentHitPoints(),
                    state.temporaryHitPoints(), state.conditions(), state.concentration());
            restored.deathSaves = state.deathSaves();
            restored.exhaustionLevel = state.exhaustionLevel();
            restored.resources.clear();
            state.resources().forEach(resource -> restored.resources.put(resource.id(), resource));
            return restored;
        }

        private MutableCombatant copy() {
            MutableCombatant duplicate =
                    new MutableCombatant(snapshot, currentHitPoints, temporaryHitPoints, conditions, concentration);
            duplicate.deathSaves = deathSaves;
            duplicate.exhaustionLevel = exhaustionLevel;
            duplicate.resources.clear();
            duplicate.resources.putAll(resources);
            return duplicate;
        }

        private CombatantState toDomain() {
            return new CombatantState(snapshot, currentHitPoints, temporaryHitPoints, conditions, concentration,
                    deathSaves, exhaustionLevel, List.copyOf(resources.values()));
        }
    }

    private static final class MutableState {
        private final String encounterId;
        private final String rulesetVersion;
        private final String contentVersion;
        private CombatStatus status;
        private long revision;
        private final List<String> rosterOrder;
        private final LinkedHashMap<String, MutableCombatant> combatants;
        private final LinkedHashMap<String, Integer> initiativeScores;
        private final List<String> initiativeOrder;
        private int round;
        private int turnIndex;
        private final LinkedHashMap<String, TurnBudget> turnBudgets;
        private final LinkedHashSet<String> partyCombatantIds;
        private boolean simultaneousTies;
        private BattleMap battleMap = BattleMap.none();

        private MutableState(
                String encounterId,
                String rulesetVersion,
                String contentVersion,
                CombatStatus status,
                long revision,
                List<String> rosterOrder,
                Map<String, MutableCombatant> combatants,
                Map<String, Integer> initiativeScores,
                List<String> initiativeOrder,
                int round,
                int turnIndex,
                Map<String, TurnBudget> turnBudgets,
                Collection<String> partyCombatantIds) {
            this.encounterId = encounterId;
            this.rulesetVersion = rulesetVersion;
            this.contentVersion = contentVersion;
            this.status = status;
            this.revision = revision;
            this.rosterOrder = new ArrayList<>(rosterOrder);
            this.combatants = new LinkedHashMap<>(combatants);
            this.initiativeScores = new LinkedHashMap<>(initiativeScores);
            this.initiativeOrder = new ArrayList<>(initiativeOrder);
            this.round = round;
            this.turnIndex = turnIndex;
            this.turnBudgets = new LinkedHashMap<>(turnBudgets);
            this.partyCombatantIds = new LinkedHashSet<>(partyCombatantIds);
        }

        private static MutableState empty(String encounterId, String rulesetVersion, String contentVersion) {
            return new MutableState(encounterId, rulesetVersion, contentVersion, CombatStatus.DRAFT, 0,
                    List.of(), Map.of(), Map.of(), List.of(), 0, -1, Map.of(), Set.of());
        }

        private static MutableState from(CombatState state) {
            Map<String, MutableCombatant> combatants = new LinkedHashMap<>();
            state.combatants().forEach((id, combatant) -> combatants.put(id, MutableCombatant.from(combatant)));
            MutableState restored = new MutableState(state.encounterId(), state.rulesetVersion(),
                    state.contentVersion(), state.status(), state.revision(), state.rosterOrder(), combatants,
                    state.initiativeScores(), state.initiativeOrder(), state.round(), state.turnIndex(),
                    state.turnBudgets(), state.partyCombatantIds());
            restored.simultaneousTies = state.simultaneousTies();
            restored.battleMap = state.battleMap();
            return restored;
        }

        private MutableState copy() {
            Map<String, MutableCombatant> copiedCombatants = new LinkedHashMap<>();
            combatants.forEach((id, combatant) -> copiedCombatants.put(id, combatant.copy()));
            MutableState duplicate = new MutableState(encounterId, rulesetVersion, contentVersion, status, revision,
                    rosterOrder, copiedCombatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                    partyCombatantIds);
            duplicate.simultaneousTies = simultaneousTies;
            duplicate.battleMap = battleMap;
            return duplicate;
        }

        private CombatState toDomain(long seed, long randomState) {
            Map<String, CombatantState> domainCombatants = new LinkedHashMap<>();
            combatants.forEach((id, combatant) -> domainCombatants.put(id, combatant.toDomain()));
            return new CombatState(encounterId, rulesetVersion, contentVersion, status, revision, seed, randomState,
                    rosterOrder, domainCombatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                    partyCombatantIds, simultaneousTies, battleMap);
        }
    }
}
