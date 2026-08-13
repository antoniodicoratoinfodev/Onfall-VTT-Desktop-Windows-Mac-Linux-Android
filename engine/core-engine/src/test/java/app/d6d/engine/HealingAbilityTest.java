package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.HealingDefinition;
import app.d6d.domain.combat.HealingSlotScaling;
import app.d6d.domain.combat.HealingTarget;
import app.d6d.domain.combat.SpellSlotResourceId;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealingAbilityTest {
    @Test
    void anUpcastUsesTheSelectedSlotAndAppliesTheScaledFormulaAtomically() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        String thirdLevel = SpellSlotResourceId.standard(3).id();
        AbilityDefinition cure = scalableHealing("cure", 1, 2, 2, 1);
        CombatSession session = active(101L, List.of(
                new CombatResourceState(firstLevel, "Slot 1", 2, 0),
                new CombatResourceState(thirdLevel, "Slot 3", 1, 0)), cure);
        CombatState before = session.currentState();

        int restored = session.useHealingAbility("healer", "ally", "cure", thirdLevel);

        assertTrue(restored >= 7 && restored <= 13);
        assertEquals(2, remaining(session, "healer", firstLevel));
        assertEquals(0, remaining(session, "healer", thirdLevel));
        CombatEvent healed = lastEvent(session, EventType.HEALED);
        assertEquals("6d2+1", healed.details().get("formula"));
        assertEquals(thirdLevel, healed.details().get("resourceId"));
        assertEquals("3", healed.details().get("slotLevel"));
        assertTrue(session.currentState().turnBudgets().get("healer").spellSlotSpentThisTurn());
        assertEquals(1, eventCount(session, EventType.SPELL_SLOT_SPENT));

        assertTrue(session.undo());
        assertEquals(before.randomState(), session.currentState().randomState());
        assertEquals(before.combatant("ally").currentHitPoints(),
                session.currentState().combatant("ally").currentHitPoints());
        assertEquals(1, remaining(session, "healer", thirdLevel));
        assertEquals(before.turnBudgets().get("healer"),
                session.currentState().turnBudgets().get("healer"));
    }

    @Test
    void aPactSlotCanPayForAnUpcastWithoutSpendingAStandardSlot() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        String pactSecondLevel = SpellSlotResourceId.pact(2).id();
        AbilityDefinition word = scalableHealing("word", 1, 2, 4, 2);
        CombatSession session = active(102L, List.of(
                new CombatResourceState(firstLevel, "Slot 1", 1, 0),
                new CombatResourceState(pactSecondLevel, "Slot del Patto 2", 2, 0)), word);

        int restored = session.useHealingAbility("healer", "ally", "word", pactSecondLevel);

        assertTrue(restored >= 6 && restored <= 18);
        assertEquals(1, remaining(session, "healer", firstLevel));
        assertEquals(1, remaining(session, "healer", pactSecondLevel));
        assertEquals("4d4+2", lastEvent(session, EventType.HEALED).details().get("formula"));
    }

    @Test
    void theLegacyHealingOverloadStillUsesOnlyTheBaseSlot() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        String secondLevel = SpellSlotResourceId.standard(2).id();
        AbilityDefinition cure = scalableHealing("cure", 1, 2, 2, 0);
        CombatSession session = active(103L, List.of(
                new CombatResourceState(firstLevel, "Slot 1", 1, 0),
                new CombatResourceState(secondLevel, "Slot 2", 1, 0)), cure);

        session.useHealingAbility("healer", "ally", "cure");

        assertEquals(0, remaining(session, "healer", firstLevel));
        assertEquals(1, remaining(session, "healer", secondLevel));
        assertEquals("2d2", lastEvent(session, EventType.HEALED).details().get("formula"));
    }

    @Test
    void illegalOrUnavailableUpcastResourcesAreRejectedWithoutMutation() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        String secondLevel = SpellSlotResourceId.standard(2).id();
        AbilityDefinition secondLevelCure = scalableHealing("greater-cure", 2, 1, 2, 0);
        CombatSession session = active(104L, List.of(
                new CombatResourceState(firstLevel, "Slot 1", 1, 0),
                new CombatResourceState(secondLevel, "Slot 2", 1, 1),
                new CombatResourceState("charges", "Charges", 3, 0)), secondLevelCure);
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();

        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "greater-cure", firstLevel));
        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "greater-cure", secondLevel));
        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "greater-cure", "charges"));

        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
        assertFalse(session.canUndo());
    }

    @Test
    void aNonScalingHealingFeatureCannotBorrowASpellSlot() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        AbilityDefinition secondWind = AbilityDefinition.builder("second-wind", "Second Wind")
                .activationCost(ActivationCost.NONE)
                .resource("second-wind-use", 1)
                .healing(HealingDefinition.fixed(HealingTarget.SELF, 5))
                .build();
        CombatSession session = active(105L, List.of(
                new CombatResourceState("second-wind-use", "Second Wind", 1, 0),
                new CombatResourceState(firstLevel, "Slot 1", 1, 0)), secondWind);
        CombatState before = session.currentState();

        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "healer", "second-wind", firstLevel));

        assertEquals(before, session.currentState());
        assertFalse(session.canUndo());
    }

    @Test
    void aSecondSlottedSpellInTheSameTurnIsRejectedWithoutMutation() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        AbilityDefinition word = AbilityDefinition.builder("word", "Word")
                .activationCost(ActivationCost.BONUS_ACTION)
                .rangeFeet(30)
                .spellOrCantrip(true)
                .resource(firstLevel, 1)
                .healing(HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY,
                        new DiceExpression(2, 2, 0),
                        new HealingSlotScaling(1, 2)))
                .build();
        AbilityDefinition cure = scalableHealing("cure", 1, 2, 2, 0);
        CombatSession session = active(106L, List.of(
                new CombatResourceState(firstLevel, "Slot 1", 2, 0)), word, cure);

        session.useHealingAbility("healer", "ally", "word");
        CombatState beforeSecondSpell = session.currentState();
        List<CombatEvent> auditBeforeSecondSpell = session.auditTrail();

        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "cure"));

        assertEquals(beforeSecondSpell, session.currentState());
        assertEquals(auditBeforeSecondSpell, session.auditTrail());
        assertEquals(1, remaining(session, "healer", firstLevel));
        assertEquals(1, eventCount(session, EventType.SPELL_SLOT_SPENT));
    }

    @Test
    void fixedSelfHealingSpendsItsTurnCostAndEmitsExistingEvents() {
        AbilityDefinition secondWind = healing(
                "second-wind", ActivationCost.BONUS_ACTION, 5,
                HealingDefinition.fixed(HealingTarget.SELF, 7));
        CombatSession session = active(1L, List.of(), secondWind);

        assertEquals(7, session.useHealingAbility("healer", "healer", "second-wind"));

        assertEquals(17, session.currentState().combatant("healer").currentHitPoints());
        assertFalse(session.currentState().turnBudgets().get("healer").bonusActionAvailable());
        assertEquals(1, eventCount(session, EventType.ACTION_SPENT));
        assertEquals(1, eventCount(session, EventType.HEALED));
        assertEquals(1, eventCount(session, EventType.ABILITY_ACTIVATED));
    }

    @Test
    void allyAndFactionScopesAreEnforcedBeforeAnyMutation() {
        AbilityDefinition self = healing(
                "self", ActivationCost.NONE, 30,
                HealingDefinition.fixed(HealingTarget.SELF, 2));
        AbilityDefinition ally = healing(
                "ally", ActivationCost.NONE, 30,
                HealingDefinition.fixed(HealingTarget.ALLY, 2));
        AbilityDefinition either = healing(
                "either", ActivationCost.NONE, 30,
                HealingDefinition.fixed(HealingTarget.SELF_OR_ALLY, 2));
        CombatSession session = active(2L, List.of(), self, ally, either);

        assertEquals(2, session.useHealingAbility("healer", "ally", "ally"));
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();

        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "self"));
        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "healer", "ally"));
        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "enemy", "either"));
        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
    }

    @Test
    void diceHealingIsDeterministicAndRestartsDeathSavesForAnUnconsciousAlly() {
        AbilityDefinition cure = healing(
                "cure", ActivationCost.NONE, 30,
                HealingDefinition.dice(HealingTarget.ALLY, new DiceExpression(1, 2, 10)));
        CombatSession first = active(91L, List.of(), cure);
        CombatSession repeated = active(91L, List.of(), cure);
        first.applyDamage("enemy", "ally", List.of(new DamageComponent(DamageType.FORCE, 5)), false);
        repeated.applyDamage("enemy", "ally", List.of(new DamageComponent(DamageType.FORCE, 5)), false);
        first.applyDamage("enemy", "ally", List.of(new DamageComponent(DamageType.FORCE, 1)), false);
        repeated.applyDamage("enemy", "ally", List.of(new DamageComponent(DamageType.FORCE, 1)), false);
        assertTrue(first.currentState().combatant("ally").unconscious());
        assertEquals(1, first.currentState().combatant("ally").deathSaves().failures());

        int firstRestored = first.useHealingAbility("healer", "ally", "cure");
        int repeatedRestored = repeated.useHealingAbility("healer", "ally", "cure");

        assertEquals(firstRestored, repeatedRestored);
        assertTrue(firstRestored >= 11 && firstRestored <= 12);
        assertEquals(first.currentState().randomState(), repeated.currentState().randomState());
        assertEquals(firstRestored, first.currentState().combatant("ally").currentHitPoints());
        assertEquals(0, first.currentState().combatant("ally").deathSaves().successes());
        assertEquals(0, first.currentState().combatant("ally").deathSaves().failures());
        assertFalse(first.currentState().combatant("ally").deathSaves().stable());
    }

    @Test
    void aDeadTargetCannotBeResurrectedByHealing() {
        AbilityDefinition cure = healing(
                "cure", ActivationCost.NONE, 30,
                HealingDefinition.fixed(HealingTarget.ALLY, 5));
        CombatSession session = active(3L, List.of(), cure);
        session.setCurrentHitPoints("ally", 0);
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();

        assertTrue(before.combatant("ally").dead());
        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "cure"));
        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
    }

    @Test
    void tacticalRangeIsCheckedWithoutCreatingACommand() {
        AbilityDefinition touch = healing(
                "touch", ActivationCost.ACTION, 5,
                HealingDefinition.fixed(HealingTarget.ALLY, 3));
        CombatSession session = active(4L, List.of(), touch);
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("healer", new GridPosition(0, 0), 1);
        session.placeCombatant("ally", new GridPosition(3, 0), 1);
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();
        boolean undoBefore = session.canUndo();

        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "ally", "touch"));

        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
        assertEquals(undoBefore, session.canUndo());
    }

    @Test
    void limitedHealingResourceIsConsumedAndCannotBeReused() {
        AbilityDefinition potion = AbilityDefinition.builder("potion", "Potion")
                .activationCost(ActivationCost.NONE)
                .rangeFeet(5)
                .resource("charges", 1)
                .healing(HealingDefinition.fixed(HealingTarget.SELF, 3))
                .build();
        CombatSession session = active(
                5L, List.of(new CombatResourceState("charges", "Charges", 1, 0)), potion);

        assertEquals(3, session.useHealingAbility("healer", "healer", "potion"));
        assertEquals(0, session.currentState().combatant("healer")
                .resource("charges").orElseThrow().remaining());
        assertEquals(1, eventCount(session, EventType.RESOURCE_SPENT));
        CombatState beforeRejection = session.currentState();
        List<CombatEvent> auditBeforeRejection = session.auditTrail();

        assertThrows(CombatRuleException.class,
                () -> session.useHealingAbility("healer", "healer", "potion"));
        assertEquals(beforeRejection, session.currentState());
        assertEquals(auditBeforeRejection, session.auditTrail());
    }

    @Test
    void aFailureAfterSpendingCostAndResourceRollsBackStateAuditUndoAndRng() {
        AbilityDefinition overflow = AbilityDefinition.builder("overflow", "Overflow")
                .activationCost(ActivationCost.BONUS_ACTION)
                .resource("charges", 1)
                .healing(HealingDefinition.dice(
                        HealingTarget.SELF, new DiceExpression(1, 2, Integer.MAX_VALUE)))
                .build();
        CombatSession session = active(
                6L, List.of(new CombatResourceState("charges", "Charges", 1, 0)), overflow);
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();

        assertFalse(session.canUndo());
        assertThrows(ArithmeticException.class,
                () -> session.useHealingAbility("healer", "healer", "overflow"));
        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
        assertFalse(session.canUndo());
    }

    private static AbilityDefinition healing(
            String id, ActivationCost cost, int rangeFeet, HealingDefinition healing) {
        return AbilityDefinition.builder(id, id)
                .activationCost(cost)
                .rangeFeet(rangeFeet)
                .healing(healing)
                .build();
    }

    private static AbilityDefinition scalableHealing(
            String id,
            int baseSlotLevel,
            int additionalDicePerSlotLevel,
            int sides,
            int modifier) {
        return AbilityDefinition.builder(id, id)
                .activationCost(ActivationCost.ACTION)
                .rangeFeet(30)
                .spellOrCantrip(true)
                .resource(SpellSlotResourceId.standard(baseSlotLevel).id(), 1)
                .healing(HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY,
                        new DiceExpression(2, sides, modifier),
                        new HealingSlotScaling(baseSlotLevel, additionalDicePerSlotLevel)))
                .build();
    }

    private static CombatSession active(
            long seed, List<CombatResourceState> resources, AbilityDefinition... abilities) {
        ActorDefinition healer = ActorDefinition.builder("healer-definition", "Healer")
                .maxHitPoints(20)
                .currentHitPoints(10)
                .abilities(Arrays.asList(abilities))
                .resources(resources)
                .build();
        ActorDefinition ally = ActorDefinition.builder("ally-definition", "Ally")
                .maxHitPoints(20)
                .currentHitPoints(5)
                .build();
        ActorDefinition enemy = ActorDefinition.builder("enemy-definition", "Enemy")
                .maxHitPoints(30)
                .build();
        CombatSession session = CombatSession.create("healing", seed);
        session.addCombatant("healer", healer);
        session.addCombatant("ally", ally);
        session.addCombatant("enemy", enemy);
        session.setPartyCombatants(List.of("healer", "ally"));
        session.setInitiative("healer", 20);
        session.setInitiative("ally", 15);
        session.setInitiative("enemy", 10);
        session.markReady();
        session.start();
        return session;
    }

    private static long eventCount(CombatSession session, EventType type) {
        return session.auditTrail().stream().filter(event -> event.type() == type).count();
    }

    private static CombatEvent lastEvent(CombatSession session, EventType type) {
        return session.auditTrail().stream()
                .filter(event -> event.type() == type)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private static int remaining(CombatSession session, String combatantId, String resourceId) {
        return session.currentState().combatant(combatantId)
                .resource(resourceId)
                .orElseThrow()
                .remaining();
    }
}
