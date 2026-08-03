package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.CombatantSnapshot;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.ResolutionMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildShapeTransformationTest {
    private static final String FORM = "srd521-it:beast:lupo";
    private static final String RESOURCE = "srd521-it:resource:druido:forma-selvatica";

    @Test
    void transformationSpendsBonusActionAndResourceButPreservesHitPoints() {
        AbilityDefinition chooseWolf = AbilityDefinition.builder(FORM, "Lupo")
                .activationCost(ActivationCost.BONUS_ACTION)
                .resolutionMethod(ResolutionMethod.MANUAL)
                .resource(RESOURCE, 1)
                .build();
        ActorDefinition druid = ActorDefinition.builder("druid", "Druido")
                .armorClass(15)
                .maxHitPoints(27)
                .currentHitPoints(19)
                .abilities(List.of(chooseWolf))
                .resources(List.of(new CombatResourceState(RESOURCE, "Forma selvatica", 2, 0)))
                .build();
        AbilityDefinition bite = AbilityDefinition.builder("wolf:bite", "Morso")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(4)
                .damage(List.of(DamageFormula.dice(DamageType.PIERCING, 1, 6, 2)))
                .build();
        ActorDefinition wolf = ActorDefinition.builder(FORM, "Lupo")
                .armorClass(13)
                .maxHitPoints(11)
                .speedFeet(40)
                .abilities(List.of(bite))
                .build();
        ActorDefinition target = ActorDefinition.builder("target", "Bersaglio")
                .maxHitPoints(10)
                .build();
        CombatSession session = CombatSession.create("wild-shape", 7L);
        session.addCombatant("druid", druid);
        session.addCombatant("target", target);
        session.setInitiative("druid", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();

        CombatantSnapshot transformed = CombatantSnapshot.wildShape("druid", druid, wolf);
        session.transformCombatant("druid", FORM, transformed, 5);

        var state = session.currentState().combatant("druid");
        assertEquals(27, state.snapshot().maxHitPoints());
        assertEquals(19, state.currentHitPoints());
        assertEquals(5, state.temporaryHitPoints());
        assertEquals(13, state.snapshot().armorClass());
        assertTrue(state.snapshot().abilities().stream().anyMatch(ability -> ability.name().equals("Morso")));
        assertEquals(1, state.resources().stream().filter(resource -> resource.id().equals(RESOURCE))
                .findFirst().orElseThrow().spent());
        assertFalse(session.currentState().turnBudgets().get("druid").bonusActionAvailable());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.COMBATANT_TRANSFORMED));

        assertTrue(session.undo());
        state = session.currentState().combatant("druid");
        assertEquals("Druido", state.snapshot().name());
        assertEquals(0, state.resources().stream().filter(resource -> resource.id().equals(RESOURCE))
                .findFirst().orElseThrow().spent());
    }
}
