package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AttackOutcome;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.ResolutionMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackResolutionTest {
    @Test
    void naturalOneAlwaysMissesEvenWithAHighModifier() {
        CombatSession session = CombatFixtures.active(1L);
        int hpBefore = session.currentState().combatant("goblin").currentHitPoints();

        AttackResult result = session.attack(AttackRequest.manual(
                "hero", "goblin", "sword", 1, List.of(99)));

        assertEquals(AttackOutcome.MISS, result.outcome());
        assertTrue(result.damageResult().isEmpty());
        assertEquals(hpBefore, session.currentState().combatant("goblin").currentHitPoints());
        assertFalse(session.currentState().turnBudgets().get("hero").actionAvailable());
        assertEquals(0, session.currentState().turnBudgets().get("hero").attacksRemaining());

        CombatEvent miss = session.auditTrail().stream()
                .filter(event -> event.type() == EventType.ATTACK_MISSED)
                .findFirst().orElseThrow();
        assertEquals("sword", miss.details().get("abilityId"));
        assertEquals(result.attackRoll().naturalRoll(), Integer.parseInt(miss.details().get("natural")));
        assertEquals(result.attackRoll().modifier(), Integer.parseInt(miss.details().get("modifier")));
        assertEquals(result.attackRoll().total(), Integer.parseInt(miss.details().get("total")));
        assertEquals(
                session.currentState().combatant("goblin").snapshot().armorClass(),
                Integer.parseInt(miss.details().get("armorClass")));
    }

    @Test
    void manualAttackUsesThePhysicalDamageTotals() {
        CombatSession session = CombatFixtures.active(2L);

        AttackResult result = session.attack(AttackRequest.manual(
                "hero", "goblin", "sword", 2, List.of(9)));

        assertEquals(AttackOutcome.HIT, result.outcome());
        assertEquals(9, result.rolledDamage().get(0).amount());
        assertEquals(16, session.currentState().combatant("goblin").currentHitPoints());
        assertEquals("MANUAL", session.auditTrail().stream()
                .filter(event -> event.type() == EventType.ATTACK_ROLLED).findFirst().orElseThrow()
                .details().get("source"));
        assertEquals("sword", session.auditTrail().stream()
                .filter(event -> event.type() == EventType.DAMAGE_ROLLED).findFirst().orElseThrow()
                .details().get("abilityId"));
    }

    @Test
    void naturalTwentyDoublesDiceButNotTheModifier() {
        CombatSession session = CombatFixtures.active(3L);

        AttackResult result = session.attack(AttackRequest.manual(
                "hero", "goblin", "sword", 20, List.of()));

        assertEquals(AttackOutcome.CRITICAL_HIT, result.outcome());
        String dice = session.auditTrail().stream()
                .filter(event -> event.type() == EventType.DAMAGE_ROLLED)
                .findFirst().orElseThrow().details().get("dice");
        assertEquals(2, dice.substring(1, dice.length() - 1).split(",").length);
        int rolledTotal = result.rolledDamage().get(0).amount();
        assertTrue(rolledTotal >= 5 && rolledTotal <= 15, "2d6 + 3");
    }

    @Test
    void criticalHitDoesNotDoubleFixedDamage() {
        CombatSession session = CombatFixtures.active(4L);

        AttackResult result = session.attack(AttackRequest.manual(
                "hero", "goblin", "fixed", 20, List.of()));

        assertEquals(AttackOutcome.CRITICAL_HIT, result.outcome());
        assertEquals(5, result.rolledDamage().get(0).amount());
    }

    @Test
    void aManualRequiredAbilityRejectsDigitalResolutionAtomically() {
        AbilityDefinition ability = AbilityDefinition.builder("manual", "Manual ability")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(5)
                .damage(List.of(DamageFormula.fixed(DamageType.FIRE, 4)))
                .automationStatus(AutomationStatus.MANUAL_REQUIRED)
                .build();
        ActorDefinition actor = ActorDefinition.builder("manual-actor", "Manual actor")
                .maxHitPoints(10)
                .abilities(List.of(ability))
                .build();
        CombatSession session = CombatSession.create("manual-required", 9L);
        session.addCombatant("actor", actor);
        session.addCombatant("target", CombatFixtures.goblin());
        session.setInitiative("actor", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();
        int auditSize = session.auditTrail().size();
        long randomState = session.currentState().randomState();

        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.digital("actor", "target", "manual", D20Mode.NORMAL)));
        assertEquals(auditSize, session.auditTrail().size());
        assertEquals(randomState, session.currentState().randomState());
        assertTrue(session.currentState().turnBudgets().get("actor").actionAvailable());
    }
}
