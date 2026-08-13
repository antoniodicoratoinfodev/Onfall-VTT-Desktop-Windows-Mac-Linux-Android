package app.d6d.domain.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealingDefinitionTest {
    @Test
    void supportsExactlyOnePositiveAmountRepresentation() {
        DiceExpression expression = new DiceExpression(2, 8, 3);
        HealingDefinition dice = HealingDefinition.dice(HealingTarget.ALLY, expression);
        HealingDefinition fixed = HealingDefinition.fixed(HealingTarget.SELF_OR_ALLY, 7);

        assertTrue(dice.usesDice());
        assertEquals(expression, dice.dice());
        assertNull(dice.fixedAmount());
        assertFalse(fixed.usesDice());
        assertEquals(7, fixed.fixedAmount());
        assertThrows(IllegalArgumentException.class,
                () -> new HealingDefinition(HealingTarget.SELF, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new HealingDefinition(HealingTarget.SELF, expression, 1));
        assertThrows(IllegalArgumentException.class,
                () -> HealingDefinition.fixed(HealingTarget.SELF, 0));
    }

    @Test
    void resolvesStructuredSpellSlotScalingWithoutChangingTheBaseFormula() {
        HealingDefinition scalable = HealingDefinition.dice(
                HealingTarget.SELF_OR_ALLY,
                new DiceExpression(2, 8, 4),
                new HealingSlotScaling(1, 2));

        HealingDefinition thirdLevel = scalable.resolveAtSlotLevel(3);

        assertTrue(scalable.scalesWithSlot());
        assertEquals(new DiceExpression(2, 8, 4), scalable.dice());
        assertEquals(new DiceExpression(6, 8, 4), thirdLevel.dice());
        assertFalse(thirdLevel.scalesWithSlot());
        assertThrows(IllegalArgumentException.class, () -> scalable.resolveAtSlotLevel(0));
        assertThrows(IllegalArgumentException.class, () -> scalable.resolveAtSlotLevel(10));
        assertThrows(IllegalStateException.class,
                () -> HealingDefinition.fixed(HealingTarget.SELF, 2).resolveAtSlotLevel(1));
        assertThrows(IllegalArgumentException.class,
                () -> new HealingDefinition(
                        HealingTarget.SELF,
                        null,
                        2,
                        new HealingSlotScaling(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new HealingSlotScaling(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new HealingSlotScaling(1, 0));
        assertThrows(IllegalArgumentException.class, () -> HealingDefinition.dice(
                HealingTarget.SELF,
                new DiceExpression(10_000, 2, 0),
                new HealingSlotScaling(1, 1)));

        HealingDefinition boundary = HealingDefinition.dice(
                HealingTarget.SELF,
                new DiceExpression(9_992, 2, 0),
                new HealingSlotScaling(1, 1));
        assertEquals(10_000, boundary.resolveAtSlotLevel(9).dice().count());
    }

    @Test
    void abilityBuilderCarriesHealingAndRejectsAmbiguousMechanics() {
        HealingDefinition healing = HealingDefinition.fixed(HealingTarget.SELF, 5);
        AbilityDefinition valid = AbilityDefinition.builder("second-wind", "Second Wind")
                .activationCost(ActivationCost.BONUS_ACTION)
                .healing(healing)
                .build();

        assertEquals(healing, valid.healing());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("passive", "Passive")
                .passive(true).healing(healing).build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("manual", "Manual")
                .resolutionMethod(ResolutionMethod.MANUAL).healing(healing).build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("assisted", "Assisted")
                .automationStatus(AutomationStatus.MANUAL_REQUIRED).healing(healing).build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("damage", "Damage")
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .healing(healing).build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("area", "Area")
                .areaRadiusFeet(5).healing(healing).build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("multi", "Multi")
                .maxTargets(2).healing(healing).build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("effect", "Effect")
                .effect(AbilityEffect.GRANT_NON_MAGIC_ACTION).healing(healing).build());
    }

    @Test
    void upcastHealingRequiresAMatchingSingleSpellSlotResource() {
        HealingDefinition scaling = HealingDefinition.dice(
                HealingTarget.SELF_OR_ALLY,
                new DiceExpression(2, 8, 3),
                new HealingSlotScaling(1, 2));

        AbilityDefinition valid = AbilityDefinition.builder("cure", "Cure")
                .spellOrCantrip(true)
                .resource(SpellSlotResourceId.standard(1).id(), 1)
                .healing(scaling)
                .build();

        assertTrue(valid.healing().scalesWithSlot());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("not-spell", "Not spell")
                .resource(SpellSlotResourceId.standard(1).id(), 1)
                .healing(scaling)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("wrong-cost", "Wrong cost")
                .spellOrCantrip(true)
                .resource(SpellSlotResourceId.standard(1).id(), 2)
                .healing(scaling)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("not-slot", "Not slot")
                .spellOrCantrip(true)
                .resource("charges", 1)
                .healing(scaling)
                .build());
        assertThrows(IllegalArgumentException.class, () -> AbilityDefinition.builder("wrong-level", "Wrong level")
                .spellOrCantrip(true)
                .resource(SpellSlotResourceId.standard(2).id(), 1)
                .healing(scaling)
                .build());
    }

    @Test
    void preHealingFullConstructorRemainsSourceCompatible() {
        AbilityDefinition legacy = new AbilityDefinition(
                "legacy", "1", "user", "srd-5.2.1", "Legacy",
                ActivationCost.NONE, ResolutionMethod.AUTOMATIC, 0, 5, 1, List.of(),
                AutomationStatus.AUTOMATED, "", 0, null, false, false, null, false,
                AbilityEffect.NONE, "", 0);

        assertNull(legacy.healing());
    }
}
