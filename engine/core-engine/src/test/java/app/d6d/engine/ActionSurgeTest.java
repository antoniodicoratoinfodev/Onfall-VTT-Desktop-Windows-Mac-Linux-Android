package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.AbilityEffect;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.TurnBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSurgeTest {
    private static final String SURGE = "azione-impetuosa";
    private static final String SURGE_RESOURCE = "guerriero:azione-impetuosa";

    @Test
    void grantsOneNonMagicActionAndSpendsOneUseAtomically() {
        CombatSession session = activeFighter(1);
        session.spendAction("fighter", ActivationCost.ACTION);

        session.activateAbility("fighter", SURGE);

        TurnBudget granted = session.currentState().turnBudgets().get("fighter");
        assertTrue(granted.additionalActionAvailable());
        assertTrue(granted.additionalActionMagicRestricted());
        assertEquals(0, fighterResource(session).remaining());

        session.attack(app.d6d.domain.combat.AttackRequest.digital(
                "fighter", "target", "sword", app.d6d.domain.combat.D20Mode.NORMAL));
        TurnBudget spent = session.currentState().turnBudgets().get("fighter");
        assertFalse(spent.actionAvailable());
        assertFalse(spent.additionalActionAvailable());
    }

    @Test
    void additionalActionCannotBeUsedForMagicEvenWhenMagicIsUsedFirst() {
        CombatSession session = activeFighter(1);
        session.activateAbility("fighter", SURGE);

        session.attack(app.d6d.domain.combat.AttackRequest.digital(
                "fighter", "target", "fire-bolt", app.d6d.domain.combat.D20Mode.NORMAL));

        TurnBudget afterMagic = session.currentState().turnBudgets().get("fighter");
        assertFalse(afterMagic.actionAvailable());
        assertTrue(afterMagic.additionalActionAvailable());
        assertTrue(afterMagic.additionalActionMagicRestricted());
        assertThrows(CombatRuleException.class, () -> session.attack(
                app.d6d.domain.combat.AttackRequest.digital(
                        "fighter", "target", "fire-bolt", app.d6d.domain.combat.D20Mode.NORMAL)));

        session.attack(app.d6d.domain.combat.AttackRequest.digital(
                "fighter", "target", "sword", app.d6d.domain.combat.D20Mode.NORMAL));
        assertFalse(session.currentState().turnBudgets().get("fighter").additionalActionAvailable());
    }

    @Test
    void cannotUseActionSurgeTwiceInOneTurnAndUndoRestoresItsUse() {
        CombatSession session = activeFighter(2);
        session.activateAbility("fighter", SURGE);

        assertThrows(CombatRuleException.class, () -> session.activateAbility("fighter", SURGE));
        assertEquals(1, fighterResource(session).spent());

        assertTrue(session.undo());
        assertEquals(0, fighterResource(session).spent());
        assertFalse(session.currentState().turnBudgets().get("fighter").actionSurgeUsedThisTurn());
    }

    private static CombatResourceState fighterResource(CombatSession session) {
        return session.currentState().combatant("fighter").resources().stream()
                .filter(resource -> resource.id().equals(SURGE_RESOURCE))
                .findFirst()
                .orElseThrow();
    }

    private static CombatSession activeFighter(int uses) {
        AbilityDefinition sword = AbilityDefinition.builder("sword", "Sword")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.STRENGTH)
                .attackBonus(100)
                .damage(List.of(DamageFormula.fixed(DamageType.SLASHING, 1)))
                .build();
        AbilityDefinition fireBolt = AbilityDefinition.builder("fire-bolt", "Fire Bolt")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.INTELLIGENCE)
                .spellOrCantrip(true)
                .attackBonus(100)
                .damage(List.of(DamageFormula.fixed(DamageType.FIRE, 1)))
                .build();
        AbilityDefinition actionSurge = AbilityDefinition.builder(SURGE, "Azione impetuosa")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .effect(AbilityEffect.GRANT_NON_MAGIC_ACTION)
                .resource(SURGE_RESOURCE, 1)
                .build();
        ActorDefinition fighter = ActorDefinition.builder("fighter-definition", "Fighter")
                .armorClass(16)
                .maxHitPoints(40)
                .initiativeModifier(5)
                .abilities(List.of(sword, fireBolt, actionSurge))
                .resources(List.of(new CombatResourceState(SURGE_RESOURCE, "Azione impetuosa", uses, 0)))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-definition", "Target")
                .armorClass(10)
                .maxHitPoints(20)
                .initiativeModifier(0)
                .build();

        CombatSession session = CombatSession.create("action-surge", 91L);
        session.addCombatant("fighter", fighter);
        session.addCombatant("target", target);
        session.setInitiative("fighter", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();
        return session;
    }
}
