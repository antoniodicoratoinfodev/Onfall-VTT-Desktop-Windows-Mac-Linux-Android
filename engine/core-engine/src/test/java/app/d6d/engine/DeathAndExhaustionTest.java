package app.d6d.engine;

import app.d6d.domain.combat.CombatantState;
import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.DeathSaveState;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.RollSource;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tiri salvezza contro morte, stabilizzazione, knockout ed Exhaustion. */
class DeathAndExhaustionTest {

    private static D20RollInput manual(int natural) {
        return new D20RollInput(RollSource.MANUAL, D20Mode.NORMAL, natural);
    }

    private CombatSession downedHero() {
        CombatSession session = CombatFixtures.active(7L);
        // Esattamente i punti ferita massimi: arriva a 0 senza danno in eccesso.
        session.applyDamage("goblin", "hero", List.of(new DamageComponent(DamageType.SLASHING, 40)), false);
        return session;
    }

    private CombatantState hero(CombatSession session) {
        return session.currentState().combatants().get("hero");
    }

    @Test
    void aZeroPuntiFeritaLaCreaturaNonEMortaMaPrivaDiSensi() {
        CombatSession session = downedHero();

        assertEquals(0, hero(session).currentHitPoints());
        assertTrue(hero(session).unconscious());
        assertFalse(hero(session).dead());
    }

    @Test
    void dieciOPiuEUnSuccessoMenoDiDieciUnFallimento() {
        CombatSession session = downedHero();

        session.rollDeathSave("hero", manual(12));
        assertEquals(1, hero(session).deathSaves().successes());

        session.rollDeathSave("hero", manual(9));
        assertEquals(1, hero(session).deathSaves().failures());
    }

    @Test
    void treSuccessiRendonoStableEAzzeranoIContatori() {
        CombatSession session = downedHero();

        session.rollDeathSave("hero", manual(15));
        session.rollDeathSave("hero", manual(15));
        session.rollDeathSave("hero", manual(15));

        assertTrue(hero(session).stable());
        assertEquals(0, hero(session).deathSaves().successes());
        assertEquals(0, hero(session).deathSaves().failures());
        // Resta priva di sensi: Stable non fa recuperare punti ferita.
        assertEquals(0, hero(session).currentHitPoints());
    }

    @Test
    void treFallimentiCausanoLaMorte() {
        CombatSession session = downedHero();

        session.rollDeathSave("hero", manual(5));
        session.rollDeathSave("hero", manual(5));
        session.rollDeathSave("hero", manual(5));

        assertTrue(hero(session).dead());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.DIED));
    }

    @Test
    void unoNaturaleValeDueFallimenti() {
        CombatSession session = downedHero();

        session.rollDeathSave("hero", manual(1));

        assertEquals(2, hero(session).deathSaves().failures());
    }

    @Test
    void ventiNaturaleFaRecuperareUnPuntoFerita() {
        CombatSession session = downedHero();

        session.rollDeathSave("hero", manual(20));

        assertEquals(1, hero(session).currentHitPoints());
        assertFalse(hero(session).unconscious());
        assertEquals(DeathSaveState.none(), hero(session).deathSaves());
    }

    @Test
    void subireDanniAZeroPuntiFeritaCausaUnFallimento() {
        CombatSession session = downedHero();

        session.applyDamage("goblin", "hero", List.of(new DamageComponent(DamageType.SLASHING, 3)), false);

        assertEquals(1, hero(session).deathSaves().failures());
    }

    @Test
    void unColpoCriticoAZeroPuntiFeritaCausaDueFallimenti() {
        CombatSession session = downedHero();

        session.applyDamage("goblin", "hero", List.of(new DamageComponent(DamageType.SLASHING, 3)), true);

        assertEquals(2, hero(session).deathSaves().failures());
    }

    @Test
    void dannoResiduoPariAiPuntiFeritaMassimiUccideSubito() {
        CombatSession session = CombatFixtures.active(7L);

        // 40 per azzerare piu' altri 40 di eccesso: morte immediata.
        session.applyDamage("goblin", "hero", List.of(new DamageComponent(DamageType.SLASHING, 80)), false);

        assertTrue(hero(session).dead());
    }

    @Test
    void recuperarePuntiFeritaAzzeraSuccessiEFallimenti() {
        CombatSession session = downedHero();
        session.rollDeathSave("hero", manual(5));
        assertEquals(1, hero(session).deathSaves().failures());

        session.heal("hero", 5);

        assertEquals(DeathSaveState.none(), hero(session).deathSaves());
        assertEquals(5, hero(session).currentHitPoints());
    }

    @Test
    void subireDanniEliminaLoStatoStable() {
        CombatSession session = downedHero();
        session.stabilize("hero", "prova di Medicina");
        assertTrue(hero(session).stable());

        session.applyDamage("goblin", "hero", List.of(new DamageComponent(DamageType.SLASHING, 2)), false);

        assertFalse(hero(session).stable());
        assertEquals(1, hero(session).deathSaves().failures());
    }

    @Test
    void unaCreaturaStabileNonTiraPiuControMorte() {
        CombatSession session = downedHero();
        session.stabilize("hero", "prova di Medicina");

        assertThrows(CombatRuleException.class, () -> session.rollDeathSave("hero", manual(15)));
    }

    @Test
    void aPuntiFeritaPositiviNonSiTiraControMorte() {
        CombatSession session = CombatFixtures.active(7L);

        assertThrows(CombatRuleException.class, () -> session.rollDeathSave("hero", manual(15)));
    }

    @Test
    void ilKnockoutLasciaAUnPuntoFeritaSenzaTiriControMorte() {
        CombatSession session = downedHero();

        session.knockOut("hero");

        assertEquals(1, hero(session).currentHitPoints());
        assertEquals(DeathSaveState.none(), hero(session).deathSaves());
    }

    @Test
    void exhaustionRiduceD20DiDueELaVelocitaDiCinquePerLivello() {
        CombatSession session = CombatFixtures.active(7L);

        session.setExhaustion("hero", 3);

        CombatantState hero = hero(session);
        assertEquals(3, hero.exhaustionLevel());
        assertEquals(-6, hero.exhaustionD20Penalty());
        // La velocita' di base della fixture e' 30 piedi.
        assertEquals(15, hero.effectiveSpeedFeet());
    }

    @Test
    void exhaustionSiApplicaAiTiriPerColpire() {
        CombatSession senzaExhaustion = CombatFixtures.active(7L);
        int senza = senzaExhaustion.attack(
                app.d6d.domain.combat.AttackRequest.manual("hero", "goblin", "sword", 15, List.of(4)))
                .attackRoll().total();

        CombatSession conExhaustion = CombatFixtures.active(7L);
        conExhaustion.setExhaustion("hero", 2);
        int con = conExhaustion.attack(
                app.d6d.domain.combat.AttackRequest.manual("hero", "goblin", "sword", 15, List.of(4)))
                .attackRoll().total();

        // Due livelli di Exhaustion valgono −4 sul totale.
        assertEquals(senza - 4, con);
    }

    @Test
    void alSestoLivelloDiExhaustionLaCreaturaMuore() {
        CombatSession session = CombatFixtures.active(7L);

        session.setExhaustion("hero", 6);

        assertTrue(hero(session).dead());
        assertTrue(session.auditTrail().stream()
                .anyMatch(event -> event.type() == EventType.DIED
                        && "exhaustion".equals(event.details().get("cause"))));
    }

    @Test
    void unMortoPerExhaustionConPfPositiviNonPuoAgireONeppureEssereBersagliato() {
        CombatSession session = CombatFixtures.active(17L);
        session.setExhaustion("hero", CombatantState.MAX_EXHAUSTION);

        assertFalse(session.currentState().currentCombatantIds().contains("hero"));
        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.digital("hero", "goblin", "sword", D20Mode.NORMAL)));
        assertThrows(CombatRuleException.class, () -> session.setCurrentTurn("hero"));

        session.endTurn();
        assertEquals("goblin", session.currentState().currentCombatantId().orElseThrow());
        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.digital("goblin", "hero", "sword", D20Mode.NORMAL)));
    }

    @Test
    void leAreeIgnoranoUnMortoPerExhaustionAncheSeHaPfEToken() {
        AbilityDefinition burst = AbilityDefinition.builder("burst", "Burst")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.SAVING_THROW)
                .automationStatus(AutomationStatus.AUTOMATED)
                .rangeFeet(60)
                .areaRadiusFeet(5)
                .saveAbility(SaveAbility.DEXTERITY)
                .damage(List.of(app.d6d.domain.combat.DamageFormula.fixed(DamageType.FORCE, 5)))
                .build();
        ActorDefinition caster = ActorDefinition.builder("caster-def", "Caster")
                .maxHitPoints(20)
                .abilities(List.of(burst))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-def", "Target")
                .maxHitPoints(20)
                .build();
        CombatSession session = CombatSession.create("dead-area", 18L);
        session.addCombatant("caster", caster);
        session.addCombatant("target", target);
        session.configureMap(new MapGrid(10, 10, 5));
        session.placeCombatant("caster", new GridPosition(0, 0), 1);
        session.placeCombatant("target", new GridPosition(3, 0), 1);
        session.setInitiative("caster", 20);
        session.setInitiative("target", 10);
        session.setInitiativeOrder(List.of("caster", "target"));
        session.markReady();
        session.start();
        session.setExhaustion("target", CombatantState.MAX_EXHAUSTION);

        GridPosition center = new GridPosition(3, 0);
        assertTrue(session.areaTargets("caster", center, "burst").isEmpty());
        assertTrue(session.castArea("caster", center, "burst").targets().isEmpty());
        assertEquals(20, session.currentState().combatant("target").currentHitPoints());
    }

    @Test
    void exhaustionOltreIlSestoLivelloVieneRifiutata() {
        CombatSession session = CombatFixtures.active(7L);

        assertThrows(CombatRuleException.class, () -> session.setExhaustion("hero", 7));
    }

    @Test
    void annullareRipristinaLoStatoDiMorte() {
        CombatSession session = downedHero();
        session.rollDeathSave("hero", manual(5));
        assertEquals(1, hero(session).deathSaves().failures());

        session.undo();

        assertEquals(0, hero(session).deathSaves().failures());
    }
}
