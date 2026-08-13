package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SpellSlotResourceId;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityResourceConsumptionTest {
    @Test
    void attacksConsumeTheirLimitedResourceAndRejectReuseAtomically() {
        AbilityDefinition strike = AbilityDefinition.builder("charged-strike", "Charged strike")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(100)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .resource("charges", 1)
                .build();
        CombatSession session = active(strike);

        session.attack(AttackRequest.manual(
                "actor", "target", "charged-strike", 10, List.of(1)));

        assertEquals(0, remaining(session));
        assertEquals(1, eventCount(session, EventType.RESOURCE_SPENT));
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();
        assertThrows(CombatRuleException.class, () -> session.attack(AttackRequest.manual(
                "actor", "target", "charged-strike", 10, List.of(1))));
        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
    }

    @Test
    void areaAbilitiesConsumeTheirLimitedResourceAndRejectReuseAtomically() {
        AbilityDefinition burst = AbilityDefinition.builder("charged-burst", "Charged burst")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.SAVING_THROW)
                .rangeFeet(30)
                .areaRadiusFeet(5)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .resource("charges", 1)
                .build();
        CombatSession session = active(burst);
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("actor", new GridPosition(0, 0), 1);
        session.placeCombatant("target", new GridPosition(2, 0), 1);

        session.castArea("actor", new GridPosition(2, 0), "charged-burst");

        assertEquals(0, remaining(session));
        assertEquals(1, eventCount(session, EventType.RESOURCE_SPENT));
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();
        assertThrows(CombatRuleException.class,
                () -> session.castArea("actor", new GridPosition(2, 0), "charged-burst"));
        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
    }

    @Test
    void automatedAttacksAndAreasShareTheOneSpellSlotPerTurnLimit() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        String secondLevel = SpellSlotResourceId.standard(2).id();
        AbilityDefinition ray = AbilityDefinition.builder("ray", "Ray")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .spellOrCantrip(true)
                .attackBonus(100)
                .rangeFeet(60)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .resource(firstLevel, 1)
                .build();
        AbilityDefinition burst = AbilityDefinition.builder("burst", "Burst")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.SAVING_THROW)
                .spellOrCantrip(true)
                .rangeFeet(30)
                .areaRadiusFeet(5)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .resource(secondLevel, 1)
                .build();
        ActorDefinition actor = ActorDefinition.builder("actor-definition", "Actor")
                .maxHitPoints(20)
                .abilities(List.of(ray, burst))
                .resources(List.of(
                        new CombatResourceState(firstLevel, "Slot 1", 1, 0),
                        new CombatResourceState(secondLevel, "Slot 2", 1, 0)))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-definition", "Target")
                .maxHitPoints(100)
                .build();
        CombatSession session = CombatSession.create("slotted-ability-resources", 19L);
        session.addCombatant("actor", actor);
        session.addCombatant("target", target);
        session.setInitiative("actor", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("actor", new GridPosition(0, 0), 1);
        session.placeCombatant("target", new GridPosition(2, 0), 1);

        session.attack(AttackRequest.manual("actor", "target", "ray", 10, List.of(1)));
        CombatState beforeSecondSpell = session.currentState();
        List<CombatEvent> auditBeforeSecondSpell = session.auditTrail();

        assertTrue(beforeSecondSpell.turnBudgets().get("actor").spellSlotSpentThisTurn());
        assertEquals(1, eventCount(session, EventType.SPELL_SLOT_SPENT));
        assertThrows(CombatRuleException.class,
                () -> session.castArea("actor", new GridPosition(2, 0), "burst"));
        assertEquals(beforeSecondSpell, session.currentState());
        assertEquals(auditBeforeSecondSpell, session.auditTrail());
        assertEquals(1, session.currentState().combatant("actor")
                .resource(secondLevel).orElseThrow().remaining());
    }

    @Test
    void aReactionSpellCanSpendASlotOnTheFollowingCombatantsTurn() {
        String firstLevel = SpellSlotResourceId.standard(1).id();
        AbilityDefinition actionSpell = slottedAttack("action-spell", ActivationCost.ACTION, firstLevel);
        AbilityDefinition reactionSpell = slottedAttack("reaction-spell", ActivationCost.REACTION, firstLevel);
        ActorDefinition actor = ActorDefinition.builder("actor-definition", "Actor")
                .maxHitPoints(20)
                .abilities(List.of(actionSpell, reactionSpell))
                .resources(List.of(new CombatResourceState(firstLevel, "Slot 1", 3, 0)))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-definition", "Target")
                .maxHitPoints(100)
                .build();
        CombatSession session = CombatSession.create("reaction-spell-resources", 20L);
        session.addCombatant("actor", actor);
        session.addCombatant("target", target);
        session.setInitiative("actor", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();

        session.attack(AttackRequest.manual("actor", "target", "action-spell", 10, List.of(1)));
        session.endTurn();
        assertFalse(session.currentState().turnBudgets().get("actor").spellSlotSpentThisTurn());

        session.attack(AttackRequest.manual("actor", "target", "reaction-spell", 10, List.of(1)));

        assertEquals(1, session.currentState().combatant("actor")
                .resource(firstLevel).orElseThrow().remaining());
        assertFalse(session.currentState().turnBudgets().get("actor").reactionAvailable());
        assertTrue(session.currentState().turnBudgets().get("actor").spellSlotSpentThisTurn());
        assertEquals(2, eventCount(session, EventType.SPELL_SLOT_SPENT));
        CombatState beforeRejectedReaction = session.currentState();
        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.manual("actor", "target", "reaction-spell", 10, List.of(1))));
        assertEquals(beforeRejectedReaction, session.currentState());

        assertTrue(session.undo());
        assertEquals(2, session.currentState().combatant("actor")
                .resource(firstLevel).orElseThrow().remaining());
        assertTrue(session.currentState().turnBudgets().get("actor").reactionAvailable());
        assertFalse(session.currentState().turnBudgets().get("actor").spellSlotSpentThisTurn());
        // L'undo ripristina lo stato ma non riscrive l'audit: i due consumi
        // restano registrati e l'annullamento e' tracciato come evento a se'.
        assertEquals(2, eventCount(session, EventType.SPELL_SLOT_SPENT));
        assertEquals(1, eventCount(session, EventType.UNDO_PERFORMED));
    }

    private static AbilityDefinition slottedAttack(
            String id, ActivationCost cost, String resourceId) {
        return AbilityDefinition.builder(id, id)
                .activationCost(cost)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .spellOrCantrip(true)
                .attackBonus(100)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .resource(resourceId, 1)
                .build();
    }

    private static CombatSession active(AbilityDefinition ability) {
        ActorDefinition actor = ActorDefinition.builder("actor-definition", "Actor")
                .maxHitPoints(20)
                .abilities(List.of(ability))
                .resources(List.of(new CombatResourceState("charges", "Charges", 1, 0)))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-definition", "Target")
                .maxHitPoints(100)
                .build();
        CombatSession session = CombatSession.create("ability-resources", 18L);
        session.addCombatant("actor", actor);
        session.addCombatant("target", target);
        session.setInitiative("actor", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();
        return session;
    }

    private static int remaining(CombatSession session) {
        return session.currentState().combatant("actor")
                .resource("charges").orElseThrow().remaining();
    }

    private static long eventCount(CombatSession session, EventType type) {
        return session.auditTrail().stream().filter(event -> event.type() == type).count();
    }
}
