package app.d6d.engine.ai;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.HealingDefinition;
import app.d6d.domain.combat.HealingSlotScaling;
import app.d6d.domain.combat.HealingTarget;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.SpellSlotResourceId;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import app.d6d.domain.space.TokenPlacement;
import app.d6d.engine.CombatSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyCpuTest {

    @Test
    void easyUsesTheFirstAttackWhileHigherDifficultiesChooseTheBestDamage() {
        ActorDefinition enemy = actor("enemy", 30, List.of(
                attack("weak", 60, 1),
                attack("strong", 60, 9)));
        CombatSession session = active(
                List.of(setup("enemy", enemy, false, 20), setup("hero", actor("hero", 30, List.of()), true, 10)),
                false,
                11L);

        EnemyCpuDecision easy = new EnemyCpu(EnemyCpuDifficulty.EASY)
                .decide(session.currentState(), "enemy");
        EnemyCpuDecision medium = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .decide(session.currentState(), "enemy");
        EnemyCpuDecision hard = new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU)
                .decide(session.currentState(), "enemy");

        assertEquals("weak", assertInstanceOf(EnemyCpuDecision.Attack.class, easy).abilityId());
        assertEquals("strong", assertInstanceOf(EnemyCpuDecision.Attack.class, medium).abilityId());
        assertEquals("strong", assertInstanceOf(EnemyCpuDecision.Attack.class, hard).abilityId());
    }

    @Test
    void aHitForZeroDamageIsNotReportedAsAMiss() {
        AbilityDefinition variable = AbilityDefinition.builder("variable", "Variable")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .automationStatus(AutomationStatus.AUTOMATED)
                .attackBonus(100)
                .rangeFeet(60)
                .damage(List.of(DamageFormula.dice(DamageType.FORCE, 1, 20, -10)))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-def", "Target")
                .maxHitPoints(20)
                .armorClass(10)
                .build();
        for (long seed = 1L; seed <= 100L; seed++) {
            CombatSession session = active(List.of(
                    setup("enemy", actor("enemy", 20, List.of(variable)), false, 20),
                    setup("target", target, true, 10)), false, seed);
            EnemyCpuActionReport report = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                    .actCurrentGroup(session)
                    .actions().stream()
                    .filter(action -> action.type() == EnemyCpuActionType.ATTACK)
                    .findFirst().orElseThrow();
            if (report.amount() == 0) {
                assertTrue(report.hit());
                assertEquals(20, session.currentState().combatant("target").currentHitPoints());
                return;
            }
        }
        throw new AssertionError("Nessun seed di prova ha prodotto un colpo da zero danni");
    }

    @Test
    void hardDifficultyMakesTheWholeSimultaneousSquadFocusTheKillPriority() {
        ActorDefinition striker = actor("striker", 30, List.of(attack("bolt", 60, 3)));
        ActorDefinition fragile = ActorDefinition.builder("fragile-def", "Fragile")
                .maxHitPoints(40)
                .currentHitPoints(7)
                .armorClass(12)
                .build();
        ActorDefinition healthy = ActorDefinition.builder("healthy-def", "Healthy")
                .maxHitPoints(40)
                .armorClass(12)
                .build();
        CombatSession session = active(List.of(
                setup("enemy-a", striker, false, 20),
                setup("enemy-b", striker, false, 20),
                setup("healthy", healthy, true, 10),
                setup("fragile", fragile, true, 9)), true, 12L);
        EnemyCpu cpu = new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU);

        EnemyCpuDecision first = cpu.decide(session.currentState(), "enemy-a");
        EnemyCpuDecision second = cpu.decide(session.currentState(), "enemy-b");

        assertEquals("fragile", assertInstanceOf(EnemyCpuDecision.Attack.class, first).targetId());
        assertEquals("fragile", assertInstanceOf(EnemyCpuDecision.Attack.class, second).targetId());
    }

    @Test
    void hardMeleeMovementTakesAFreeDifferentSquareAroundTheFocus() {
        ActorDefinition melee = actor("melee", 30, List.of(attack("blade", 5, 5)));
        CombatSession session = active(List.of(
                setup("mover", melee, false, 20),
                setup("blocker", melee, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 13L);
        session.configureMap(MapGrid.standard(12, 12));
        session.placeCombatant("mover", new GridPosition(2, 5), 1);
        session.placeCombatant("blocker", new GridPosition(5, 5), 1);
        session.placeCombatant("hero", new GridPosition(6, 5), 1);

        EnemyCpuDecision.Move move = assertInstanceOf(
                EnemyCpuDecision.Move.class,
                new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU)
                        .decide(session.currentState(), "mover"));

        TokenPlacement destination = TokenPlacement.single("mover", move.destination());
        TokenPlacement target = session.currentState().battleMap().placementOf("hero").orElseThrow();
        assertTrue(session.currentState().battleMap().isFree(destination));
        assertEquals(5, session.currentState().battleMap().grid().feetFor(destination.squaresTo(target)));
        assertFalse(move.destination().equals(new GridPosition(5, 5)));
    }

    @Test
    void mediumUsesAStructuredHealingAbilityAndReportsTheActualRestoredAmount() {
        AbilityDefinition healing = AbilityDefinition.builder("field-heal", "Field heal")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .automationStatus(AutomationStatus.AUTOMATED)
                .rangeFeet(30)
                .healing(HealingDefinition.fixed(HealingTarget.ALLY, 10))
                .resource("healing-charge", 1)
                .build();
        ActorDefinition healer = ActorDefinition.builder("healer-def", "Healer")
                .maxHitPoints(25)
                .abilities(List.of(healing, attack("staff", 60, 5)))
                .resources(List.of(new CombatResourceState("healing-charge", "Healing charge", 1, 0)))
                .build();
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(1)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 14L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        EnemyCpuActionReport report = result.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.HEAL)
                .findFirst().orElseThrow();
        assertEquals("healer", report.actorId());
        assertEquals("wounded", report.targetId());
        assertEquals("field-heal", report.abilityId());
        assertEquals(10, report.amount());
        assertEquals(11, session.currentState().combatant("wounded").currentHitPoints());
        assertEquals(0, session.currentState().combatant("healer")
                .resource("healing-charge").orElseThrow().remaining());
        assertTrue(result.turnAdvanced());
    }

    @Test
    void healingDifficultyChoosesTheLowestSlotNeededToLeaveTheDangerZone() {
        AbilityDefinition healing = scalableHealing("cure", ActivationCost.ACTION);
        String level1 = SpellSlotResourceId.standard(1).id();
        String level2 = SpellSlotResourceId.standard(2).id();
        String level3 = SpellSlotResourceId.standard(3).id();
        ActorDefinition healer = ActorDefinition.builder("healer-def", "Healer")
                .maxHitPoints(25)
                .abilities(List.of(healing))
                .resources(List.of(
                        new CombatResourceState(level1, "Slot 1", 1, 0),
                        new CombatResourceState(level2, "Slot 2", 1, 0),
                        new CombatResourceState(level3, "Slot 3", 1, 0)))
                .build();
        ActorDefinition critical = ActorDefinition.builder("critical-def", "Critical")
                .maxHitPoints(20)
                .currentHitPoints(1)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("critical", critical, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 140L);

        EnemyCpuDecision.Heal easy = assertInstanceOf(
                EnemyCpuDecision.Heal.class,
                new EnemyCpu(EnemyCpuDifficulty.EASY).decide(session.currentState(), "healer"));
        EnemyCpuDecision.Heal medium = assertInstanceOf(
                EnemyCpuDecision.Heal.class,
                new EnemyCpu(EnemyCpuDifficulty.MEDIUM).decide(session.currentState(), "healer"));
        EnemyCpuDecision.Heal hard = assertInstanceOf(
                EnemyCpuDecision.Heal.class,
                new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU).decide(session.currentState(), "healer"));

        assertEquals(1, easy.slotLevel());
        assertEquals(level1, easy.resourceId());
        assertEquals(2, medium.slotLevel());
        assertEquals(level2, medium.resourceId());
        assertEquals(3, hard.slotLevel());
        assertEquals(level3, hard.resourceId());
    }

    @Test
    void anExhaustedBaseSlotFallsBackToAnAvailablePactUpcast() {
        AbilityDefinition healing = scalableHealing("word", ActivationCost.BONUS_ACTION);
        String level1 = SpellSlotResourceId.standard(1).id();
        String pact2 = SpellSlotResourceId.pact(2).id();
        ActorDefinition healer = ActorDefinition.builder("healer-def", "Healer")
                .maxHitPoints(25)
                .abilities(List.of(healing))
                .resources(List.of(
                        new CombatResourceState(level1, "Slot 1", 1, 1),
                        new CombatResourceState(pact2, "Slot del Patto 2", 1, 0)))
                .build();
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(4)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 141L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        EnemyCpuActionReport report = result.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.HEAL)
                .findFirst()
                .orElseThrow();
        assertEquals(pact2, report.consumedResourceId());
        assertEquals(2, report.slotLevel());
        assertEquals(0, session.currentState().combatant("healer")
                .resource(pact2).orElseThrow().remaining());
        assertEquals(0, session.currentState().combatant("healer")
                .resource(level1).orElseThrow().remaining());
        // Lo slot scelto e' un dato del resoconto, non una frase da leggere.
        assertEquals(EnemyCpuReason.PROTECT_ALLY, report.reason());
        assertTrue(report.detail().isEmpty());
    }

    @Test
    void equalStandardAndPactSlotsHaveADeterministicStandardPreference() {
        AbilityDefinition healing = scalableHealing("cure", ActivationCost.ACTION);
        String level1 = SpellSlotResourceId.standard(1).id();
        String standard2 = SpellSlotResourceId.standard(2).id();
        String pact2 = SpellSlotResourceId.pact(2).id();
        ActorDefinition healer = ActorDefinition.builder("healer-def", "Healer")
                .maxHitPoints(25)
                .abilities(List.of(healing))
                .resources(List.of(
                        new CombatResourceState(pact2, "Slot del Patto 2", 1, 0),
                        new CombatResourceState(standard2, "Slot 2", 1, 0),
                        new CombatResourceState(level1, "Slot 1", 1, 1)))
                .build();
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(4)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 142L);

        EnemyCpuDecision.Heal decision = assertInstanceOf(
                EnemyCpuDecision.Heal.class,
                new EnemyCpu(EnemyCpuDifficulty.MEDIUM).decide(session.currentState(), "healer"));

        assertEquals(2, decision.slotLevel());
        assertEquals(standard2, decision.resourceId());
    }

    @Test
    void theCpuNeverSpendsASecondSpellSlotAfterHealingInTheSameTurn() {
        String level1 = SpellSlotResourceId.standard(1).id();
        String level2 = SpellSlotResourceId.standard(2).id();
        AbilityDefinition healing = scalableHealing("word", ActivationCost.BONUS_ACTION);
        AbilityDefinition slottedRay = AbilityDefinition.builder("slotted-ray", "Slotted ray")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .automationStatus(AutomationStatus.AUTOMATED)
                .spellOrCantrip(true)
                .attackBonus(100)
                .rangeFeet(60)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 10)))
                .resource(level2, 1)
                .build();
        ActorDefinition healer = ActorDefinition.builder("healer-def", "Healer")
                .maxHitPoints(25)
                .abilities(List.of(healing, slottedRay))
                .resources(List.of(
                        new CombatResourceState(level1, "Slot 1", 1, 0),
                        new CombatResourceState(level2, "Slot 2", 1, 0)))
                .build();
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(7)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 143L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertEquals(1, result.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.HEAL)
                .count());
        assertFalse(result.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.ATTACK));
        assertEquals(1, session.currentState().combatant("healer")
                .resource(level2).orElseThrow().remaining());
    }

    @Test
    void equivalentHealingPrefersAnAtWillAbilityEvenWhenTheSlottedSpellComesFirst() {
        String level1 = SpellSlotResourceId.standard(1).id();
        AbilityDefinition slotted = scalableHealing("slotted", ActivationCost.ACTION);
        AbilityDefinition atWill = AbilityDefinition.builder("at-will", "At will")
                .activationCost(ActivationCost.ACTION)
                .rangeFeet(60)
                .healing(HealingDefinition.fixed(HealingTarget.SELF_OR_ALLY, 5))
                .build();
        ActorDefinition healer = ActorDefinition.builder("healer-def", "Healer")
                .maxHitPoints(25)
                .abilities(List.of(slotted, atWill))
                .resources(List.of(new CombatResourceState(level1, "Slot 1", 1, 0)))
                .build();
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(7)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 144L);

        EnemyCpuDecision.Heal decision = assertInstanceOf(
                EnemyCpuDecision.Heal.class,
                new EnemyCpu(EnemyCpuDifficulty.MEDIUM).decide(session.currentState(), "healer"));

        assertEquals("at-will", decision.abilityId());
        assertEquals("", decision.resourceId());
        assertEquals(0, decision.slotLevel());
    }

    @Test
    void healingUtilityDoesNotOverflowForLargeButValidDiceExpressions() {
        AbilityDefinition massiveHeal = AbilityDefinition.builder("massive-heal", "Massive heal")
                .activationCost(ActivationCost.ACTION)
                .rangeFeet(60)
                .healing(HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY,
                        new DiceExpression(5_000, 500_000, 0)))
                .build();
        ActorDefinition healer = actor("healer", 20, List.of(massiveHeal));
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(1)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 15),
                setup("hero", actor("hero", 40, List.of()), true, 10)), false, 145L);

        EnemyCpuDecision.Heal decision = assertInstanceOf(
                EnemyCpuDecision.Heal.class,
                new EnemyCpu(EnemyCpuDifficulty.MEDIUM).decide(session.currentState(), "healer"));

        assertEquals("massive-heal", decision.abilityId());
        assertEquals("wounded", decision.targetId());
    }

    @Test
    void areaScoringRejectsFriendlyFireAndUsesAreaAgainstACluster() {
        AbilityDefinition burst = area("burst", 10, 5);
        ActorDefinition caster = ActorDefinition.builder("caster-def", "Caster")
                .maxHitPoints(30)
                .spellSaveDc(100)
                .abilities(List.of(burst, attack("ray", 60, 5)))
                .build();

        CombatSession risky = active(List.of(
                setup("caster", caster, false, 20),
                setup("ally", actor("ally", 30, List.of()), false, 15),
                setup("hero", actor("hero", 30, List.of()), true, 10)), false, 15L);
        risky.configureMap(MapGrid.standard(15, 15));
        risky.placeCombatant("caster", new GridPosition(1, 1), 1);
        risky.placeCombatant("ally", new GridPosition(6, 5), 1);
        risky.placeCombatant("hero", new GridPosition(7, 5), 1);

        EnemyCpuDecision riskyChoice = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .decide(risky.currentState(), "caster");
        assertInstanceOf(EnemyCpuDecision.Attack.class, riskyChoice);

        CombatSession cluster = active(List.of(
                setup("caster", caster, false, 20),
                setup("hero-a", actor("hero-a", 30, List.of()), true, 10),
                setup("hero-b", actor("hero-b", 30, List.of()), true, 9)), false, 16L);
        cluster.configureMap(MapGrid.standard(15, 15));
        cluster.placeCombatant("caster", new GridPosition(1, 1), 1);
        cluster.placeCombatant("hero-a", new GridPosition(7, 5), 1);
        cluster.placeCombatant("hero-b", new GridPosition(8, 5), 1);

        EnemyCpuDecision clusterChoice = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .decide(cluster.currentState(), "caster");
        assertInstanceOf(EnemyCpuDecision.AreaAttack.class, clusterChoice);

        EnemyCpuResult clusterResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(cluster);
        EnemyCpuActionReport areaReport = clusterResult.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.AREA_ATTACK)
                .findFirst().orElseThrow();
        assertEquals(1, clusterResult.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.AREA_ATTACK)
                .count(), "un lancio ad area produce un report e un checkpoint, non uno per bersaglio");
        assertTrue(areaReport.targetId().contains("hero-a"));
        assertTrue(areaReport.targetId().contains("hero-b"));
        assertEquals(List.of("hero-a", "hero-b"), areaReport.targets().stream()
                .map(EnemyCpuTargetReport::targetId)
                .toList());
        assertEquals(2, areaReport.targets().size());
        assertEquals(2, clusterResult.checkpointCount(), "un'area e la chiusura turno sono due comandi");
    }

    /**
     * L'interfaccia annulla un turno CPU ripetendo {@code undo()} esattamente
     * {@code checkpointCount()} volte: un conteggio in eccesso riporterebbe
     * indietro anche i comandi del tavolo, uno in difetto lascerebbe mezzo turno.
     */
    @Test
    void undoingExactlyTheReportedCheckpointsRestoresTheStartOfTheCpuTurn() {
        ActorDefinition melee = ActorDefinition.builder("melee-def", "Melee")
                .maxHitPoints(30)
                .armorClass(12)
                .abilities(List.of(attack("blade", 5, 4)))
                .build();
        CombatSession session = active(List.of(
                setup("hero", actor("hero", 30, List.of(attack("bow", 60, 3))), true, 20),
                setup("enemy", melee, false, 10)), false, 77L);
        session.configureMap(MapGrid.standard(15, 15));
        session.placeCombatant("hero", new GridPosition(2, 2), 1);
        session.placeCombatant("enemy", new GridPosition(5, 2), 1);

        // Un comando del tavolo prima della CPU: se l'undo del batch andasse
        // oltre i propri checkpoint, questo danno sparirebbe.
        session.attack(AttackRequest.digital("hero", "enemy", "bow", D20Mode.NORMAL));
        session.endTurn();

        CombatState before = session.currentState();
        int enemyHitPointsBefore = before.combatant("enemy").currentHitPoints();
        assertTrue(enemyHitPointsBefore < 30, "il comando del giocatore deve aver lasciato un segno");

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.checkpointCount() >= 2,
                "servono piu' comandi perche' il conteggio sia significativo");
        for (int index = 0; index < result.checkpointCount(); index++) {
            assertTrue(session.undo(), "ogni checkpoint dichiarato deve esistere davvero");
        }

        CombatState restored = session.currentState();
        assertEquals(before.round(), restored.round());
        assertEquals(before.turnIndex(), restored.turnIndex());
        assertEquals(before.status(), restored.status());
        assertEquals(before.combatants(), restored.combatants());
        assertEquals(before.turnBudgets(), restored.turnBudgets());
        assertEquals(before.battleMap(), restored.battleMap());
        assertEquals(enemyHitPointsBefore, restored.combatant("enemy").currentHitPoints());
    }

    @Test
    void legacyTurnsWithoutConfiguredFactionsAreNeverMutated() {
        CombatSession legacy = active(List.of(
                setup("first", actor("first", 20, List.of(attack("hit", 60, 2))), false, 20),
                setup("second", actor("second", 20, List.of()), false, 10)), false, 17L, false);
        long legacyRevision = legacy.currentState().revision();

        EnemyCpuResult legacyResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(legacy);

        assertFalse(legacyResult.acted());
        assertEquals(legacyRevision, legacy.currentState().revision());
    }

    @Test
    void mixedSimultaneousTurnRunsOnlyEnemiesAndStaysOpenForThePlayer() {
        CombatSession mixed = active(List.of(
                setup("enemy", actor("enemy", 20, List.of(attack("hit", 60, 2))), false, 20),
                setup("hero", actor("hero", 20, List.of()), true, 20)), true, 18L);
        int round = mixed.currentState().round();
        int turnIndex = mixed.currentState().turnIndex();

        EnemyCpu cpu = new EnemyCpu(EnemyCpuDifficulty.MEDIUM);
        EnemyCpuResult mixedResult = cpu.actCurrentGroup(mixed);

        assertTrue(mixedResult.waitingForPlayer());
        assertTrue(mixedResult.partialMixedGroup());
        assertTrue(mixedResult.acted());
        assertFalse(mixedResult.turnAdvanced());
        assertEquals(1, mixedResult.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.ATTACK)
                .count());
        assertFalse(mixedResult.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.TURN_ENDED));
        assertEquals(round, mixed.currentState().round());
        assertEquals(turnIndex, mixed.currentState().turnIndex());

        EnemyCpuResult repeated = cpu.actCurrentGroup(mixed);
        assertEquals(0, repeated.checkpointCount(), "l'azione gia' spesa non viene ripetuta");
        assertTrue(repeated.waitingForPlayer());
        assertEquals(round, mixed.currentState().round());
        assertEquals(turnIndex, mixed.currentState().turnIndex());
    }

    @Test
    void mixedGroupClosesWhenNoPlayerActorRemainsActiveInsideIt() {
        ActorDefinition fragile = ActorDefinition.builder("fragile-def", "Fragile")
                .maxHitPoints(10)
                .currentHitPoints(1)
                .armorClass(12)
                .build();
        CombatSession mixed = active(List.of(
                setup("enemy", actor("enemy", 20, List.of(attack("hit", 60, 5))), false, 20),
                setup("fragile", fragile, true, 20),
                setup("reserve", actor("reserve", 40, List.of()), true, 10)), true, 189L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(mixed);

        assertTrue(result.partialMixedGroup());
        assertFalse(result.waitingForPlayer());
        assertTrue(result.turnAdvanced());
        assertTrue(result.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.TURN_ENDED));
        assertEquals(List.of("reserve"), mixed.currentState().currentCombatantIds());
    }

    @Test
    void aLargeLegitimateSimultaneousGroupDoesNotLoseActorsToTheSafetyLimit() {
        ActorDefinition soldier = actor("soldier", 20, List.of(attack("strike", 60, 1)));
        List<Setup> setups = new ArrayList<>();
        for (int index = 0; index < EnemyCpu.MAX_DECISIONS_PER_ACTOR + 3; index++) {
            setups.add(setup("enemy-" + index, soldier, false, 20));
        }
        setups.add(setup("hero", actor("hero", 1_000, List.of()), true, 10));
        CombatSession session = active(setups, true, 180L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertEquals(EnemyCpu.MAX_DECISIONS_PER_ACTOR + 3, result.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.ATTACK)
                .count());
        assertFalse(result.decisionLimitReached());
        assertTrue(result.turnAdvanced());
    }

    @Test
    void deadCombatantsNeverActOrKeepThePartyStanding() {
        CombatSession deadEnemy = active(List.of(
                setup("enemy", actor("enemy", 20, List.of(attack("hit", 60, 2))), false, 20),
                setup("hero", actor("hero", 20, List.of()), true, 10)), false, 181L);
        deadEnemy.setExhaustion("enemy", 6);

        EnemyCpuResult enemyResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(deadEnemy);

        assertFalse(enemyResult.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.ATTACK));
        assertTrue(enemyResult.turnAdvanced());

        CombatSession deadParty = active(List.of(
                setup("enemy", actor("enemy", 20, List.of(attack("hit", 60, 2))), false, 20),
                setup("hero", actor("hero", 20, List.of()), true, 10)), false, 182L);
        deadParty.setExhaustion("hero", 6);

        EnemyCpuResult partyResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(deadParty);

        assertTrue(partyResult.encounterResolved());
        assertFalse(partyResult.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.ATTACK));
    }

    @Test
    void aDeadPartyMemberDoesNotKeepAMixedGroupOpenWhenAReserveIsAlive() {
        CombatSession session = active(List.of(
                setup("enemy", actor("enemy", 20, List.of(attack("hit", 60, 2))), false, 20),
                setup("dead-tie", actor("dead-tie", 20, List.of()), true, 20),
                setup("reserve", actor("reserve", 20, List.of()), true, 10)), true, 1821L);
        session.setExhaustion("dead-tie", 6);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.turnAdvanced());
        assertFalse(result.waitingForPlayer());
        assertEquals(List.of("reserve"), session.currentState().currentCombatantIds());
        assertFalse(result.actions().stream().anyMatch(action -> action.actorId().equals("dead-tie")));
    }

    @Test
    void rangedEnemiesKeepAUsefulDistanceAndRetreatWhenEngaged() {
        ActorDefinition archer = actor("archer", 20, List.of(attack("bow", 60, 2)));
        CombatSession comfortable = active(List.of(
                setup("archer", archer, false, 20),
                setup("hero", actor("hero", 50, List.of()), true, 10)), false, 183L);
        comfortable.configureMap(MapGrid.standard(15, 15));
        comfortable.placeCombatant("archer", new GridPosition(1, 5), 1);
        comfortable.placeCombatant("hero", new GridPosition(7, 5), 1);

        EnemyCpuResult comfortableResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .actCurrentGroup(comfortable);

        assertFalse(comfortableResult.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.MOVE));

        CombatSession engaged = active(List.of(
                setup("archer", archer, false, 20),
                setup("hero", actor("hero", 50, List.of()), true, 10)), false, 184L);
        engaged.configureMap(MapGrid.standard(15, 15));
        engaged.placeCombatant("archer", new GridPosition(5, 5), 1);
        engaged.placeCombatant("hero", new GridPosition(7, 5), 1);

        EnemyCpuResult engagedResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(engaged);

        assertTrue(engagedResult.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.MOVE));
        assertEquals(30, engaged.currentState().distanceFeet("archer", "hero").orElseThrow());
    }

    @Test
    void aHybridCasterWithAMeleeBackupStillKeepsSpellRange() {
        ActorDefinition caster = actor("hybrid", 20, List.of(
                attack("dagger", 5, 3),
                attack("bolt", 60, 8)));
        CombatSession session = active(List.of(
                setup("hybrid", caster, false, 20),
                setup("hero", actor("hero", 50, List.of()), true, 10)), false, 1841L);
        session.configureMap(MapGrid.standard(15, 15));
        session.placeCombatant("hybrid", new GridPosition(1, 5), 1);
        session.placeCombatant("hero", new GridPosition(7, 5), 1);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.actions().stream().anyMatch(action ->
                action.type() == EnemyCpuActionType.ATTACK && action.abilityId().equals("bolt")));
        assertFalse(result.actions().stream().anyMatch(action -> action.type() == EnemyCpuActionType.MOVE));
        assertEquals(30, session.currentState().distanceFeet("hybrid", "hero").orElseThrow());
    }

    @Test
    void aHybridRangedHealerDoesNotChargeAfterHelpingAnAlly() {
        AbilityDefinition healing = AbilityDefinition.builder("word", "Healing word")
                .activationCost(ActivationCost.BONUS_ACTION)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .automationStatus(AutomationStatus.AUTOMATED)
                .rangeFeet(60)
                .healing(HealingDefinition.fixed(HealingTarget.ALLY, 10))
                .build();
        ActorDefinition healer = actor("healer", 20, List.of(attack("mace", 5, 5), healing));
        ActorDefinition wounded = ActorDefinition.builder("wounded-def", "Wounded")
                .maxHitPoints(20)
                .currentHitPoints(1)
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("wounded", wounded, false, 20),
                setup("hero", actor("hero", 50, List.of()), true, 10)), true, 1842L);
        session.configureMap(MapGrid.standard(15, 15));
        session.placeCombatant("healer", new GridPosition(1, 5), 1);
        session.placeCombatant("wounded", new GridPosition(2, 5), 1);
        session.placeCombatant("hero", new GridPosition(7, 5), 1);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.actions().stream().anyMatch(action ->
                action.type() == EnemyCpuActionType.HEAL && action.targetId().equals("wounded")));
        assertFalse(result.actions().stream().anyMatch(action ->
                action.type() == EnemyCpuActionType.MOVE && action.actorId().equals("healer")));
        assertEquals(30, session.currentState().distanceFeet("healer", "hero").orElseThrow());
    }

    @Test
    void hardMeleeAlreadyAdjacentRepositionsIntoTheOppositeFreeSector() {
        ActorDefinition melee = actor("melee", 30, List.of(attack("blade", 5, 1)));
        CombatSession session = active(List.of(
                setup("mover", melee, false, 20),
                setup("ally", melee, false, 20),
                setup("hero", actor("hero", 40, List.of()), true, 10)), true, 185L);
        session.configureMap(MapGrid.standard(12, 12));
        session.placeCombatant("mover", new GridPosition(6, 4), 1);
        session.placeCombatant("ally", new GridPosition(5, 5), 1);
        session.placeCombatant("hero", new GridPosition(6, 5), 1);

        EnemyCpuDecision.Move move = assertInstanceOf(
                EnemyCpuDecision.Move.class,
                new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU)
                        .decide(session.currentState(), "mover"));

        assertEquals(new GridPosition(7, 5), move.destination());
    }

    @Test
    void anEnemyRevivedInsideItsSimultaneousGroupStillGetsItsAvailableBudget() {
        AbilityDefinition healing = AbilityDefinition.builder("revive", "Revive")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .automationStatus(AutomationStatus.AUTOMATED)
                .rangeFeet(30)
                .healing(HealingDefinition.fixed(HealingTarget.ALLY, 10))
                .build();
        ActorDefinition healer = actor("healer", 20, List.of(healing));
        ActorDefinition fallen = ActorDefinition.builder("fallen-def", "Fallen")
                .maxHitPoints(20)
                .currentHitPoints(0)
                .armorClass(12)
                .abilities(List.of(attack("return-hit", 60, 3)))
                .build();
        CombatSession session = active(List.of(
                setup("healer", healer, false, 20),
                setup("fallen", fallen, false, 20),
                setup("hero", actor("hero", 50, List.of()), true, 10)), true, 186L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.actions().stream().anyMatch(action ->
                action.type() == EnemyCpuActionType.HEAL && action.targetId().equals("fallen")));
        assertTrue(result.actions().stream().anyMatch(action ->
                action.type() == EnemyCpuActionType.ATTACK && action.actorId().equals("fallen")));
        assertTrue(result.turnAdvanced());
    }

    @Test
    void turnAdvancedAlsoDetectsAnAdvancePerformedInsideAnAreaCommand() {
        ActorDefinition caster = ActorDefinition.builder("caster-def", "Caster")
                .maxHitPoints(1)
                .spellSaveDc(100)
                .abilities(List.of(area("self-risk", 10, 10)))
                .build();
        CombatSession session = active(List.of(
                setup("caster", caster, false, 20),
                setup("hero-a", actor("hero-a", 100, List.of()), true, 10),
                setup("hero-b", actor("hero-b", 100, List.of()), true, 9)), false, 187L);
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("caster", new GridPosition(1, 1), 1);
        session.placeCombatant("hero-a", new GridPosition(2, 1), 1);
        session.placeCombatant("hero-b", new GridPosition(2, 2), 1);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.turnAdvanced());
        assertEquals(0, session.currentState().combatant("caster").currentHitPoints());
        assertFalse(result.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.TURN_ENDED));
    }

    @Test
    void victoryKeepsTheAdvancePerformedByASelfDestructiveArea() {
        ActorDefinition caster = ActorDefinition.builder("caster-def", "Caster")
                .maxHitPoints(1)
                .spellSaveDc(100)
                .abilities(List.of(area("last-blast", 10, 10)))
                .build();
        ActorDefinition victim = ActorDefinition.builder("victim-def", "Victim")
                .maxHitPoints(1)
                .savingThrowBonuses(Map.of(SaveAbility.DEXTERITY, -100))
                .build();
        CombatSession session = active(List.of(
                setup("caster", caster, false, 20),
                setup("victim", victim, true, 10)), false, 1871L);
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("caster", new GridPosition(1, 1), 1);
        session.placeCombatant("victim", new GridPosition(2, 1), 1);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU).actCurrentGroup(session);

        assertTrue(result.encounterResolved());
        assertTrue(result.turnAdvanced());
        assertEquals(0, session.currentState().combatant("caster").currentHitPoints());
        assertEquals(0, session.currentState().combatant("victim").currentHitPoints());
    }

    @Test
    void areaExecutionIsDeterministicAfterRoundTripsWithDifferentPlacementInsertionOrder() {
        ActorDefinition caster = ActorDefinition.builder("caster-def", "Caster")
                .maxHitPoints(30)
                .spellSaveDc(15)
                .abilities(List.of(area("burst", 10, 5)))
                .build();
        List<Setup> setups = List.of(
                setup("caster", caster, false, 20),
                setup("hero-z", actor("hero-z", 40, List.of()), true, 10),
                setup("hero-a", actor("hero-a", 40, List.of()), true, 9));
        CombatSession firstOriginal = active(setups, false, 188L);
        firstOriginal.configureMap(MapGrid.standard(12, 12));
        firstOriginal.placeCombatant("caster", new GridPosition(1, 1), 1);
        firstOriginal.placeCombatant("hero-z", new GridPosition(7, 5), 1);
        firstOriginal.placeCombatant("hero-a", new GridPosition(8, 5), 1);

        CombatSession secondOriginal = active(setups, false, 188L);
        secondOriginal.configureMap(MapGrid.standard(12, 12));
        // Stessi token, inseriti in un ordine volutamente diverso.
        secondOriginal.placeCombatant("hero-a", new GridPosition(8, 5), 1);
        secondOriginal.placeCombatant("caster", new GridPosition(1, 1), 1);
        secondOriginal.placeCombatant("hero-z", new GridPosition(7, 5), 1);

        assertEquals(
                firstOriginal.currentState().battleMap().orderedPlacements(),
                secondOriginal.currentState().battleMap().orderedPlacements());
        CombatSession first = CombatSession.restore(firstOriginal.currentState(), firstOriginal.auditTrail());
        CombatSession second = CombatSession.restore(secondOriginal.currentState(), secondOriginal.auditTrail());

        EnemyCpuResult firstResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(first);
        EnemyCpuResult secondResult = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(second);

        assertEquals(firstResult.actions(), secondResult.actions());
        assertEquals(first.currentState(), second.currentState());
    }

    @Test
    void defeatingTheLastPartyMemberResolvesInsteadOfLoopingEnemyTurns() {
        ActorDefinition caster = ActorDefinition.builder("caster-def", "Caster")
                .maxHitPoints(30)
                .spellSaveDc(100)
                .abilities(List.of(area("finisher", 10, 5)))
                .build();
        ActorDefinition victim = ActorDefinition.builder("victim-def", "Victim")
                .maxHitPoints(10)
                .currentHitPoints(1)
                .savingThrowBonuses(Map.of(SaveAbility.DEXTERITY, -100))
                .build();
        CombatSession session = active(List.of(
                setup("caster", caster, false, 20),
                setup("victim", victim, true, 10)), false, 19L);
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("caster", new GridPosition(0, 0), 1);
        session.placeCombatant("victim", new GridPosition(3, 0), 1);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.SORRY_FOR_YOU).actCurrentGroup(session);

        assertTrue(result.encounterResolved());
        assertEquals(CombatStatus.RESOLVED, session.currentState().status());
        assertEquals(0, session.currentState().combatant("victim").currentHitPoints());
        assertTrue(result.actions().stream()
                .anyMatch(action -> action.type() == EnemyCpuActionType.ENCOUNTER_RESOLVED));
        assertFalse(result.decisionLimitReached());
    }

    @Test
    void malformedFreeAttackCollectionsStopAtTheDecisionLimitAndStillAdvance() {
        List<AbilityDefinition> freeAttacks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            freeAttacks.add(AbilityDefinition.builder("free-" + index, "Free " + index)
                    .activationCost(ActivationCost.NONE)
                    .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                    .attackBonus(100)
                    .rangeFeet(60)
                    .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                    .build());
        }
        CombatSession session = active(List.of(
                setup("enemy", actor("enemy", 30, freeAttacks), false, 20),
                setup("hero", actor("hero", 1_000, List.of()), true, 10)), false, 20L);

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(result.decisionLimitReached());
        assertEquals(EnemyCpu.MAX_DECISIONS_PER_ACTOR, result.actions().stream()
                .filter(action -> action.type() == EnemyCpuActionType.ATTACK)
                .count());
        assertTrue(result.turnAdvanced());
    }

    @Test
    void everyDifficultyIsJustADifferentSetOfWeights() {
        for (EnemyCpuDifficulty difficulty : EnemyCpuDifficulty.values()) {
            EnemyCpuProfile profile = EnemyCpuProfile.of(difficulty);
            assertEquals(profile, new EnemyCpu(difficulty).profile());
            assertTrue(profile.healing().dangerRatio() > 0.0 && profile.healing().dangerRatio() < 1.0);
        }

        // Il livello semplice sceglie il vicino e rifiuta il fuoco amico invece di pesarlo;
        // il piu' aggressivo accerchia e insiste sul bersaglio prioritario.
        EnemyCpuProfile easy = EnemyCpuProfile.of(EnemyCpuDifficulty.EASY);
        assertTrue(easy.focus().nearestOnly());
        assertTrue(easy.area().refusesFriendlyFire());
        assertFalse(easy.usesActivations());
        assertFalse(easy.movement().seeksSurround());

        EnemyCpuProfile hard = EnemyCpuProfile.of(EnemyCpuDifficulty.SORRY_FOR_YOU);
        assertFalse(hard.focus().nearestOnly());
        assertTrue(hard.movement().seeksSurround());
        assertTrue(hard.attack().focusBonus()
                > EnemyCpuProfile.of(EnemyCpuDifficulty.MEDIUM).attack().focusBonus());
        assertTrue(hard.area().friendlyHitPenalty()
                > EnemyCpuProfile.of(EnemyCpuDifficulty.MEDIUM).area().friendlyHitPenalty());
    }

    @Test
    void theResumableRunnerPlaysExactlyTheSameTurnAsTheAtomicBatch() {
        CombatSession atomicSession = meleeSquadBattlefield(21L);
        CombatSession steppedSession = meleeSquadBattlefield(21L);

        EnemyCpuResult atomic = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(atomicSession);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).startCurrentGroup(steppedSession);
        while (runner.advance()) {
            // Un comando alla volta, come fa l'interfaccia fra una pausa e l'altra.
        }
        EnemyCpuResult stepped = runner.result();

        assertEquals(atomic.actions(), stepped.actions());
        assertEquals(atomic.outcome(), stepped.outcome());
        assertEquals(atomic.focusTargetId(), stepped.focusTargetId());
        assertEquals(atomic.turnAdvanced(), stepped.turnAdvanced());
        assertEquals(atomic.endingRevision(), stepped.endingRevision());
        assertEquals(
                atomicSession.currentState().combatant("hero").currentHitPoints(),
                steppedSession.currentState().combatant("hero").currentHitPoints());
        assertEquals(
                atomicSession.currentState().battleMap().placementOf("enemy-a").orElseThrow().origin(),
                steppedSession.currentState().battleMap().placementOf("enemy-a").orElseThrow().origin());
    }

    @Test
    void eachAdvanceAppliesOneSingleCommandSoTheTableCanSeeIt() {
        CombatSession session = meleeSquadBattlefield(22L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).startCurrentGroup(session);

        int steps = 0;
        long revision = session.currentState().revision();
        int checkpoints = 0;
        while (runner.advance()) {
            steps++;
            assertTrue(session.currentState().revision() > revision);
            assertEquals(checkpoints + 1, checkpointsOf(runner.reports()));
            assertTrue(runner.actingCombatantId().startsWith("enemy-"));
            assertFalse(runner.finished());
            revision = session.currentState().revision();
            checkpoints = checkpointsOf(runner.reports());
        }

        // Due nemici che devono avvicinarsi: almeno uno spostamento e un attacco a testa.
        assertTrue(steps >= 4, "Passi eseguiti: " + steps);
        assertTrue(runner.finished());
        assertEquals(steps + 1, runner.result().checkpointCount(), "Il turno chiuso vale un checkpoint in piu'");
        assertTrue(runner.result().turnAdvanced());
    }

    private static int checkpointsOf(List<EnemyCpuActionReport> reports) {
        return (int) reports.stream().filter(report -> report.type() != EnemyCpuActionType.SKIPPED).count();
    }

    /** Due nemici lontani dal bersaglio: il gruppo produce piu' comandi consecutivi. */
    private static CombatSession meleeSquadBattlefield(long seed) {
        ActorDefinition melee = actor("melee", 30, List.of(attack("blade", 5, 5)));
        CombatSession session = active(List.of(
                setup("enemy-a", melee, false, 20),
                setup("enemy-b", melee, false, 20),
                setup("hero", actor("hero", 200, List.of()), true, 10)), true, seed);
        session.configureMap(MapGrid.standard(12, 12));
        session.placeCombatant("enemy-a", new GridPosition(2, 5), 1);
        session.placeCombatant("enemy-b", new GridPosition(2, 7), 1);
        session.placeCombatant("hero", new GridPosition(8, 6), 1);
        return session;
    }

    private static AbilityDefinition attack(String id, int rangeFeet, int fixedDamage) {
        return AbilityDefinition.builder(id, id)
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .automationStatus(AutomationStatus.AUTOMATED)
                .attackBonus(100)
                .rangeFeet(rangeFeet)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, fixedDamage)))
                .build();
    }

    private static AbilityDefinition area(String id, int fixedDamage, int radiusFeet) {
        return AbilityDefinition.builder(id, id)
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.SAVING_THROW)
                .automationStatus(AutomationStatus.AUTOMATED)
                .rangeFeet(60)
                .areaRadiusFeet(radiusFeet)
                .saveAbility(SaveAbility.DEXTERITY)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, fixedDamage)))
                .build();
    }

    private static AbilityDefinition scalableHealing(String id, ActivationCost cost) {
        return AbilityDefinition.builder(id, id)
                .activationCost(cost)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .automationStatus(AutomationStatus.AUTOMATED)
                .spellOrCantrip(true)
                .rangeFeet(60)
                .resource(SpellSlotResourceId.standard(1).id(), 1)
                .healing(HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY,
                        new app.d6d.domain.combat.DiceExpression(2, 4, 0),
                        new HealingSlotScaling(1, 2)))
                .build();
    }

    private static ActorDefinition actor(String id, int hitPoints, List<AbilityDefinition> abilities) {
        return ActorDefinition.builder(id + "-definition", id)
                .maxHitPoints(hitPoints)
                .armorClass(12)
                .abilities(abilities)
                .build();
    }

    private static Setup setup(
            String instanceId,
            ActorDefinition actor,
            boolean party,
            int initiative) {
        return new Setup(instanceId, actor, party, initiative);
    }

    private static CombatSession active(List<Setup> setups, boolean simultaneous, long seed) {
        return active(setups, simultaneous, seed, true);
    }

    private static CombatSession active(
            List<Setup> setups,
            boolean simultaneous,
            long seed,
            boolean configureParty) {
        CombatSession session = CombatSession.create("cpu-test", seed);
        List<String> party = new ArrayList<>();
        for (Setup setup : setups) {
            session.addCombatant(setup.instanceId, setup.actor);
            session.setInitiative(setup.instanceId, setup.initiative);
            if (setup.party) party.add(setup.instanceId);
        }
        if (configureParty) session.setPartyCombatants(party);
        session.setSimultaneousTies(simultaneous);
        session.markReady();
        session.start();
        return session;
    }

    private record Setup(String instanceId, ActorDefinition actor, boolean party, int initiative) { }
}
