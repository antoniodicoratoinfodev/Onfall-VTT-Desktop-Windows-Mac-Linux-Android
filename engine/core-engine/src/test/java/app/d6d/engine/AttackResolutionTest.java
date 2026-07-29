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
import app.d6d.domain.combat.SaveAbility;
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
    void armorTrainingPenaltyImposesDisadvantageAndCancelsAdvantage() {
        CombatSession disadvantaged = activeWithArmorPenalty(21L);

        AttackResult normalRequest = disadvantaged.attack(
                AttackRequest.digital("hero", "goblin", "sword", D20Mode.NORMAL));

        assertEquals(D20Mode.DISADVANTAGE, normalRequest.attackRoll().mode());
        assertEquals(2, normalRequest.attackRoll().dice().size());
        assertEquals(
                normalRequest.attackRoll().dice().stream().mapToInt(Integer::intValue).min().orElseThrow(),
                normalRequest.attackRoll().naturalRoll());

        CombatSession cancelled = activeWithArmorPenalty(22L);
        AttackResult advantageRequest = cancelled.attack(
                AttackRequest.digital("hero", "goblin", "sword", D20Mode.ADVANTAGE));

        assertEquals(D20Mode.NORMAL, advantageRequest.attackRoll().mode());
        assertEquals(1, advantageRequest.attackRoll().dice().size());
    }

    @Test
    void armorTrainingPenaltyDoesNotAffectAnAttackBasedOnAnotherAbility() {
        AbilityDefinition charismaAttack = AbilityDefinition.builder("charisma-ray", "Charisma ray")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.CHARISMA)
                .attackBonus(100)
                .damage(List.of(DamageFormula.dice(DamageType.FORCE, 1, 6, 0)))
                .build();
        CombatSession session = activeWithArmorPenalty(23L, charismaAttack);

        AttackResult result = session.attack(
                AttackRequest.digital("hero", "goblin", charismaAttack.id(), D20Mode.NORMAL));

        assertEquals(D20Mode.NORMAL, result.attackRoll().mode());
        assertEquals(1, result.attackRoll().dice().size());
    }

    @Test
    void armorTrainingPenaltyBlocksSpellAttacksBeforeSpendingTheAction() {
        AbilityDefinition spellAttack = AbilityDefinition.builder("spell-ray", "Spell ray")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.CHARISMA)
                .spellOrCantrip(true)
                .attackBonus(100)
                .damage(List.of(DamageFormula.dice(DamageType.FORCE, 1, 6, 0)))
                .build();
        CombatSession session = activeWithArmorPenalty(24L, spellAttack);

        assertThrows(
                CombatRuleException.class,
                () -> session.attack(
                        AttackRequest.digital("hero", "goblin", spellAttack.id(), D20Mode.NORMAL)));
        assertTrue(session.currentState().turnBudgets().get("hero").actionAvailable());
    }

    private static CombatSession activeWithArmorPenalty(long seed) {
        return activeWithArmorPenalty(seed, CombatFixtures.sword());
    }

    private static CombatSession activeWithArmorPenalty(long seed, AbilityDefinition ability) {
        ActorDefinition hero = ActorDefinition.builder("armored-hero", "Armored hero")
                .maxHitPoints(30)
                .strengthDexterityD20Disadvantage(true)
                .abilities(List.of(ability))
                .build();
        CombatSession session = CombatSession.create("armor-training", seed);
        session.addCombatant("hero", hero);
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 10);
        session.markReady();
        session.start();
        return session;
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

    @Test
    void aPassiveTraitCannotBeActivatedAndLeavesTheTurnUntouched() {
        AbilityDefinition weaponMastery = AbilityDefinition.builder("mastery", "Weapon mastery")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.MANUAL)
                .automationStatus(AutomationStatus.MANUAL_REQUIRED)
                .passive(true)
                .build();
        ActorDefinition fighter = ActorDefinition.builder("fighter-definition", "Fighter")
                .maxHitPoints(30)
                .abilities(List.of(CombatFixtures.sword(), weaponMastery))
                .build();
        CombatSession session = CombatSession.create("passive", 7L);
        session.addCombatant("fighter", fighter);
        session.addCombatant("target", CombatFixtures.goblin());
        session.setInitiative("fighter", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();

        int auditSize = session.auditTrail().size();
        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.manual("fighter", "target", "mastery", 1, List.of(1))));

        assertEquals(auditSize, session.auditTrail().size());
        assertTrue(session.currentState().turnBudgets().get("fighter").actionAvailable());
    }

    @Test
    void extraAttackSpendsTheActionOnceAndRejectsAThirdStrike() {
        ActorDefinition fighter = ActorDefinition.builder("fighter-definition", "Fighter")
                .maxHitPoints(30)
                .attacksPerAction(2)
                .abilities(List.of(CombatFixtures.sword()))
                .build();
        CombatSession session = CombatSession.create("extra-attack", 10L);
        session.addCombatant("fighter", fighter);
        session.addCombatant("target", CombatFixtures.goblin());
        session.setInitiative("fighter", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();

        session.attack(AttackRequest.manual("fighter", "target", "sword", 1, List.of(1)));
        assertFalse(session.currentState().turnBudgets().get("fighter").actionAvailable());
        assertEquals(1, session.currentState().turnBudgets().get("fighter").attacksRemaining());

        session.attack(AttackRequest.manual("fighter", "target", "sword", 1, List.of(1)));
        assertEquals(0, session.currentState().turnBudgets().get("fighter").attacksRemaining());
        assertEquals(1, session.auditTrail().stream()
                .filter(event -> event.type() == EventType.ACTION_SPENT)
                .count());

        int auditSize = session.auditTrail().size();
        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.manual("fighter", "target", "sword", 1, List.of(1))));
        assertEquals(auditSize, session.auditTrail().size());
    }
}
