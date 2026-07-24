package app.d6d.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AreaSpellResult;
import app.d6d.domain.combat.AreaTargetResult;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Palla di Fuoco e affini: area, tiro salvezza per metà danno, e colpisce chiunque sia dentro. */
class AreaSpellTest {

    private static AbilityDefinition fireball() {
        return AbilityDefinition.builder("fireball", "Fireball")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.SAVING_THROW)
                .rangeFeet(150)
                .damage(List.of(DamageFormula.dice(DamageType.FIRE, 8, 6, 0)))
                .areaRadiusFeet(20)
                .saveAbility(SaveAbility.DEXTERITY)
                .halfOnSave(true)
                .build();
    }

    private static ActorDefinition caster() {
        return ActorDefinition.builder("wizard-def", "Wizard")
                .armorClass(12)
                .maxHitPoints(30)
                .speedFeet(30)
                .initiativeModifier(2)
                .spellSaveDc(14)
                .abilities(List.of(fireball()))
                .build();
    }

    /** Bersaglio robusto con un bonus al TS di Destrezza fissato per rendere l'esito certo. */
    private static ActorDefinition target(String id, int dexteritySaveBonus) {
        return ActorDefinition.builder(id + "-def", id)
                .armorClass(10)
                .maxHitPoints(100)
                .speedFeet(30)
                .savingThrowBonuses(Map.of(SaveAbility.DEXTERITY, dexteritySaveBonus))
                .abilities(List.of())
                .build();
    }

    private static CombatSession scene(long seed) {
        CombatSession session = CombatSession.create("area", seed);
        session.addCombatant("wizard", caster());
        // +100 supera sempre il TS, -100 lo fallisce sempre: gli esiti non dipendono dal d20.
        session.addCombatant("centre", target("centre", -100));
        session.addCombatant("nimble", target("nimble", 100));
        session.addCombatant("ally", target("ally", -100));
        session.addCombatant("faraway", target("faraway", 0));
        session.setInitiative("wizard", 20);
        session.setInitiative("centre", 15);
        session.setInitiative("nimble", 14);
        session.setInitiative("ally", 13);
        session.setInitiative("faraway", 12);
        session.markReady();
        session.start();
        session.configureMap(MapGrid.standard(40, 40));
        session.placeCombatant("wizard", new GridPosition(1, 1), 1);
        session.placeCombatant("centre", new GridPosition(10, 10), 1);
        session.placeCombatant("nimble", new GridPosition(11, 11), 1);
        session.placeCombatant("ally", new GridPosition(9, 10), 1);
        session.placeCombatant("faraway", new GridPosition(25, 25), 1);
        return session;
    }

    private static Map<String, AreaTargetResult> byTarget(AreaSpellResult result) {
        return result.targets().stream()
                .collect(Collectors.toMap(AreaTargetResult::targetId, Function.identity()));
    }

    private static int rolledTotal(AreaSpellResult result) {
        return result.rolledDamage().stream().mapToInt(DamageComponent::amount).sum();
    }

    @Test
    void hits_every_creature_in_radius_and_halves_on_a_successful_save() {
        CombatSession session = scene(42L);

        AreaSpellResult result = session.castArea("wizard", new GridPosition(10, 10), "fireball");

        int full = rolledTotal(result);
        Map<String, AreaTargetResult> byId = byTarget(result);
        // Chiunque nel raggio: bersaglio, agile e perfino l'alleato. Fuori raggio, no.
        assertEquals(3, byId.size());
        assertFalse(byId.containsKey("faraway"));
        // Ha fallito il TS: danno pieno.
        assertFalse(byId.get("centre").saved());
        assertEquals(full, byId.get("centre").damage().get().totalAdjustedDamage());
        // Ha superato il TS: metà danno.
        assertTrue(byId.get("nimble").saved());
        assertEquals(full / 2, byId.get("nimble").damage().get().totalAdjustedDamage());
        // L'alleato è colpito come tutti gli altri.
        assertFalse(byId.get("ally").saved());
        assertEquals(full, byId.get("ally").damage().get().totalAdjustedDamage());
        // Il danno base è nel range di 8d6.
        assertTrue(full >= 8 && full <= 48, "8d6 out of range: " + full);
    }

    @Test
    void manual_resolution_applies_the_table_decisions_without_rolling() {
        CombatSession session = scene(7L);

        AreaSpellResult result = session.castAreaManual(
                "wizard", new GridPosition(10, 10), "fireball",
                Map.of("centre", false, "nimble", true, "ally", false));

        int full = rolledTotal(result);
        Map<String, AreaTargetResult> byId = byTarget(result);
        assertEquals(full, byId.get("centre").damage().get().totalAdjustedDamage());
        assertEquals(full / 2, byId.get("nimble").damage().get().totalAdjustedDamage());
        assertEquals(full, byId.get("ally").damage().get().totalAdjustedDamage());
        // Nessun tiro è stato eseguito: la risoluzione manuale non porta un d20.
        assertTrue(byId.get("centre").saveRoll().isEmpty());
    }

    @Test
    void the_area_centre_must_be_within_range() {
        CombatSession session = scene(1L);
        // Gittata 150 piedi = 30 caselle; il lanciatore è in (1,1), quindi (39,39) — a
        // 38 caselle, 190 piedi — è oltre la gittata.
        assertThrows(RuntimeException.class,
                () -> session.castArea("wizard", new GridPosition(39, 39), "fireball"));
    }
}
