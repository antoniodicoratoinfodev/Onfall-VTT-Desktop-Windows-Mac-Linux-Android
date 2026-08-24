package app.d6d.engine.ai;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AttackOutcome;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import app.d6d.engine.CombatSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Attivazione delle creature: chi non si e' accorto del gruppo lascia passare il
 * proprio turno, e chi lo scopre — a vista o a proprie spese — smette di aspettare.
 */
class CreatureActivationTest {

    @Test
    void unaCreaturaInattivaLasciaPassareIlTurnoSenzaAgire() {
        CombatSession session = encounter();
        session.setDormantCombatants(List.of("goblin"));
        int heroHitPoints = session.currentState().combatant("hero").currentHitPoints();

        EnemyCpuResult result = new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        CombatState after = session.currentState();
        assertEquals(heroHitPoints, after.combatant("hero").currentHitPoints());
        assertTrue(result.actions().stream().noneMatch(report ->
                report.type() == EnemyCpuActionType.ATTACK || report.type() == EnemyCpuActionType.MOVE));
        // Il turno deve comunque avanzare: un gruppo che non agisce non e' un
        // gruppo che blocca l'iniziativa.
        assertTrue(result.turnAdvanced());
        assertTrue(after.dormant("goblin"));
    }

    @Test
    void loStessoNemicoAgisceAppenaEAttivo() {
        CombatSession session = encounter();
        int heroHitPoints = session.currentState().combatant("hero").currentHitPoints();

        new EnemyCpu(EnemyCpuDifficulty.MEDIUM).actCurrentGroup(session);

        assertTrue(session.currentState().combatant("hero").currentHitPoints() < heroHitPoints);
    }

    @Test
    void laPianificazioneSingolaSiFermaSullaCreaturaInattiva() {
        CombatSession session = encounter();
        session.setDormantCombatants(List.of("goblin"));

        EnemyCpuDecision decision = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .decide(session.currentState(), "goblin");

        assertEquals(
                EnemyCpuReason.ACTOR_DORMANT,
                assertInstanceOf(EnemyCpuDecision.Done.class, decision).reason());
    }

    @Test
    void subireUnColpoSvegliaLaCreatura() {
        CombatSession session = encounter();
        session.setDormantCombatants(List.of("goblin"));

        session.applyDamage("hero", "goblin", List.of(new DamageComponent(DamageType.FORCE, 3)), false);

        assertFalse(session.currentState().dormant("goblin"));
    }

    @Test
    void ancheUnColpoMancatoSiSentePassare() {
        CombatSession session = encounter();
        session.setDormantCombatants(List.of("goblin"));
        session.setCurrentTurn("hero");

        // Bonus zero contro una corazza irraggiungibile: il tiro non puo' andare a
        // segno, e resta soltanto la constatazione che qualcuno ha attaccato.
        AttackResult result = session.attack(
                new AttackRequest("hero", "goblin", "hopeless", D20RollInput.digital(), List.of()));

        assertEquals(AttackOutcome.MISS, result.outcome());
        assertFalse(session.currentState().dormant("goblin"));
    }

    @Test
    void chiSiSvegliaDaLAllarmeAChiHaVicinoMaNonAChiELontano() {
        CombatSession session = encounter();
        session.configureMap(MapGrid.standard(60, 20));
        session.placeCombatant("hero", new GridPosition(0, 0), 1);
        session.placeCombatant("goblin", new GridPosition(2, 0), 1);
        // Sei caselle da cinque piedi: trenta piedi, dentro il raggio dell'allarme.
        session.placeCombatant("orco", new GridPosition(8, 0), 1);
        // Quarantasei caselle: oltre duecento piedi, ben fuori portata di voce.
        session.placeCombatant("troll", new GridPosition(48, 0), 1);
        session.setDormantCombatants(List.of("goblin", "orco", "troll"));

        session.applyDamage("hero", "goblin", List.of(new DamageComponent(DamageType.FORCE, 3)), false);

        CombatState after = session.currentState();
        assertFalse(after.dormant("goblin"));
        assertFalse(after.dormant("orco"));
        assertTrue(after.dormant("troll"));
    }

    @Test
    void lAllarmeNonAttraversaLoSchieramento() {
        CombatSession session = encounter();
        session.configureMap(MapGrid.standard(20, 20));
        session.placeCombatant("hero", new GridPosition(0, 0), 1);
        session.placeCombatant("goblin", new GridPosition(1, 0), 1);
        // Un personaggio puo' essere dichiarato inattivo dal master, ma il grido di
        // un mostro non lo riguarda: gli schieramenti non si passano l'allarme.
        session.setDormantCombatants(List.of("goblin", "hero"));

        session.applyDamage("hero", "goblin", List.of(new DamageComponent(DamageType.FORCE, 3)), false);

        assertFalse(session.currentState().dormant("goblin"));
        assertTrue(session.currentState().dormant("hero"));
    }

    @Test
    void annullareIlColpoRiportaLaCreaturaAlSuoPosto() {
        CombatSession session = encounter();
        session.setDormantCombatants(List.of("goblin"));

        session.applyDamage("hero", "goblin", List.of(new DamageComponent(DamageType.FORCE, 3)), false);
        assertFalse(session.currentState().dormant("goblin"));

        session.undo();

        // Il risveglio e' un effetto del comando, non un fatto a parte: se il tavolo
        // annulla il colpo, la creatura non puo' restare sveglia per averlo subito.
        assertTrue(session.currentState().dormant("goblin"));
    }

    @Test
    void ilRisveglioRestituisceChiSiEDavveroSvegliato() {
        CombatSession session = encounter();
        session.setDormantCombatants(List.of("goblin"));

        assertEquals(List.of("goblin"), session.awaken(List.of("goblin", "hero")));
        assertEquals(List.of(), session.awaken(List.of("goblin")));
        assertEquals(Set.of(), session.dormantCombatantIds());
    }

    @Test
    void unIdentificatoreSconosciutoNonEntraFraGliInattivi() {
        CombatSession session = encounter();

        session.setDormantCombatants(List.of("goblin", "creatura-che-non-esiste"));

        assertEquals(Set.of("goblin"), session.dormantCombatantIds());
    }

    /** Un goblin di turno, due nemici piu' indietro nell'iniziativa e l'eroe. */
    private CombatSession encounter() {
        CombatSession session = CombatSession.create("attivazione", 7L);
        session.addCombatant("hero", hero());
        session.addCombatant("goblin", enemy("goblin"));
        session.addCombatant("orco", enemy("orco"));
        session.addCombatant("troll", enemy("troll"));
        session.setPartyCombatants(List.of("hero"));
        session.setInitiative("goblin", 20);
        session.setInitiative("orco", 15);
        session.setInitiative("troll", 12);
        session.setInitiative("hero", 10);
        session.markReady();
        session.start();
        return session;
    }

    private ActorDefinition hero() {
        return ActorDefinition.builder("hero-definition", "Hero")
                .armorClass(12)
                .maxHitPoints(40)
                .abilities(List.of(strike(), hopeless()))
                .build();
    }

    /** Corazza irraggiungibile: l'attacco senza speranza del test non puo' andare a segno. */
    private ActorDefinition enemy(String id) {
        return ActorDefinition.builder(id + "-definition", id)
                .armorClass(99)
                .maxHitPoints(20)
                .abilities(List.of(strike()))
                .build();
    }

    private AbilityDefinition strike() {
        return AbilityDefinition.builder("strike", "Strike")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .automationStatus(AutomationStatus.AUTOMATED)
                .attackBonus(100)
                .rangeFeet(300)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 4)))
                .build();
    }

    private AbilityDefinition hopeless() {
        return AbilityDefinition.builder("hopeless", "Hopeless")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .automationStatus(AutomationStatus.AUTOMATED)
                .attackBonus(0)
                .rangeFeet(300)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, 1)))
                .build();
    }
}
