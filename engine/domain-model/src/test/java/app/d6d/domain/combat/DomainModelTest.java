package app.d6d.domain.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {
    @Test
    void abilityClassificationIsOptionalAndAvailableThroughTheBuilder() {
        AbilityDefinition classified = AbilityDefinition.builder("ray", "Ray")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.CHARISMA)
                .spellOrCantrip(true)
                .damage(List.of(DamageFormula.dice(DamageType.FORCE, 1, 10, 0)))
                .build();

        assertEquals(SaveAbility.CHARISMA, classified.attackAbility());
        assertTrue(classified.spellOrCantrip());

        AbilityDefinition legacy = new AbilityDefinition(
                "legacy",
                "1",
                "user",
                "srd-5.2.1",
                "Legacy attack",
                ActivationCost.ACTION,
                ResolutionMethod.ATTACK_ROLL,
                4,
                30,
                1,
                List.of(DamageFormula.dice(DamageType.FORCE, 1, 6, 0)),
                AutomationStatus.AUTOMATED,
                "",
                0,
                null,
                false,
                false);

        assertNull(legacy.attackAbility());
        assertFalse(legacy.spellOrCantrip());
    }

    @Test
    void definitionsAndSnapshotsDefensivelyCopyCollections() {
        List<DamageFormula> damage = new ArrayList<>();
        damage.add(DamageFormula.dice(DamageType.SLASHING, 1, 8, 3));
        AbilityDefinition ability = AbilityDefinition.builder("longsword", "Longsword")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(5)
                .damage(damage)
                .build();
        damage.clear();

        List<AbilityDefinition> abilities = new ArrayList<>();
        abilities.add(ability);
        ActorDefinition actor = ActorDefinition.builder("fighter", "Fighter")
                .armorClass(18)
                .maxHitPoints(30)
                .currentHitPoints(21)
                .temporaryHitPoints(4)
                .initiativeModifier(2)
                .resistances(Set.of(DamageType.FIRE))
                .abilities(abilities)
                .build();
        CombatantSnapshot snapshot = CombatantSnapshot.from("fighter-1", actor);
        abilities.clear();

        assertEquals(1, ability.damage().size());
        assertEquals(1, actor.abilities().size());
        assertEquals(21, snapshot.initialHitPoints());
        assertEquals(4, snapshot.initialTemporaryHitPoints());
        assertEquals(12, snapshot.initiativeScore());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.abilities().add(ability));
    }

    @Test
    void damageFormulaRequiresExactlyOneRepresentation() {
        assertThrows(IllegalArgumentException.class,
                () -> new DamageFormula(DamageType.FIRE, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageFormula(DamageType.FIRE, DiceExpression.of(1, 6), 3));
        assertThrows(IllegalArgumentException.class,
                () -> new DiceExpression(0, 6, 0));
    }

    @Test
    void openDamageAndConditionIdsHaveAnOrderingConsistentWithEquality() {
        DamageType lowerDamage = DamageType.of("homebrew:void");
        DamageType upperDamage = DamageType.of("HOMEBREW:VOID");
        ConditionType lowerCondition = ConditionType.of("homebrew:marked");
        ConditionType upperCondition = ConditionType.of("HOMEBREW:MARKED");

        assertFalse(lowerDamage.equals(upperDamage));
        assertTrue(lowerDamage.compareTo(upperDamage) != 0);
        assertFalse(lowerCondition.equals(upperCondition));
        assertTrue(lowerCondition.compareTo(upperCondition) != 0);
    }

    @Test
    void turnBudgetTracksIndependentResources() {
        TurnBudget budget = TurnBudget.fresh(30)
                .spendMovement(10)
                .useAction()
                .useBonusAction()
                .useReaction()
                .useObjectInteraction()
                .useAttack()
                .markSpellSlotSpent();

        assertEquals(20, budget.movementRemainingFeet());
        assertFalse(budget.actionAvailable());
        assertFalse(budget.bonusActionAvailable());
        assertFalse(budget.reactionAvailable());
        assertFalse(budget.objectInteractionAvailable());
        assertEquals(0, budget.attacksRemaining());
        assertTrue(budget.spellSlotSpentThisTurn());
        assertThrows(IllegalStateException.class, budget::useAction);
        assertThrows(IllegalArgumentException.class, () -> budget.spendMovement(21));
    }

    @Test
    void turnBudgetCanStartWithEveryAttackGrantedByTheAttackAction() {
        TurnBudget budget = TurnBudget.fresh(30, 3);

        assertEquals(3, budget.attacksRemaining());
        assertEquals(1, TurnBudget.fresh(30).attacksRemaining());
        assertThrows(IllegalArgumentException.class, () -> TurnBudget.fresh(30, 0));
    }

    @Test
    void conditionDurationCannotTickPastItsExpiry() {
        ConditionDuration duration = ConditionDuration.rounds(2);
        assertEquals(1, duration.decrement().remainingOccurrences());
        assertThrows(IllegalStateException.class, () -> duration.decrement().decrement());
        assertThrows(IllegalArgumentException.class,
                () -> new ConditionDuration(ConditionExpiry.MANUAL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ConditionDuration(ConditionExpiry.CONCENTRATION, 1));
    }

    @Test
    void numericFactoriesRejectOverflowInsteadOfWrapping() {
        assertThrows(IllegalArgumentException.class,
                () -> ActorDefinition.builder("overflow", "Overflow")
                        .initiativeModifier(Integer.MAX_VALUE)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new D20RollResult(RollSource.MANUAL, D20Mode.NORMAL, List.of(20),
                        20, Integer.MAX_VALUE, Integer.MIN_VALUE + 19));
    }
}
