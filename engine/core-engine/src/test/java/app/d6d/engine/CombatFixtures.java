package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;

import java.util.List;

final class CombatFixtures {
    private CombatFixtures() { }

    static AbilityDefinition sword() {
        return AbilityDefinition.attack("sword", "Sword", ActivationCost.ACTION, 100,
                DamageFormula.dice(DamageType.SLASHING, 1, 6, 3));
    }

    static AbilityDefinition fixedStrike() {
        return AbilityDefinition.attack("fixed", "Fixed strike", ActivationCost.ACTION, 100,
                DamageFormula.fixed(DamageType.FORCE, 5));
    }

    static AbilityDefinition focus(String id) {
        return AbilityDefinition.builder(id, id)
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .build();
    }

    static ActorDefinition hero() {
        return ActorDefinition.builder("hero-definition", "Hero")
                .armorClass(16)
                .maxHitPoints(40)
                .speedFeet(30)
                .initiativeModifier(3)
                .constitutionSaveBonus(2)
                .abilities(List.of(sword(), fixedStrike(), focus("focus-1"), focus("focus-2")))
                .build();
    }

    static ActorDefinition goblin() {
        return ActorDefinition.builder("goblin-definition", "Goblin")
                .armorClass(13)
                .maxHitPoints(25)
                .speedFeet(30)
                .initiativeModifier(2)
                .abilities(List.of(sword()))
                .build();
    }

    static CombatSession active(long seed) {
        CombatSession session = CombatSession.create("encounter", seed);
        session.addCombatant("hero", hero());
        session.addCombatant("goblin", goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 10);
        session.markReady();
        session.start();
        return session;
    }
}
