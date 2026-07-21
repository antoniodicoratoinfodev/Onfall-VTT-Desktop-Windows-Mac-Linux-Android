package app.d6d.domain.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.d6d.domain.rules.CompatibilityMode;
import app.d6d.domain.rules.ContentPackVersion;
import app.d6d.domain.rules.ControlMode;
import app.d6d.domain.rules.EncounterDifficulty;
import app.d6d.domain.rules.MeasurementSystem;
import app.d6d.domain.rules.PlayConfiguration;
import app.d6d.domain.rules.RulesetProfile;
import app.d6d.domain.rules.SpatialMode;
import app.d6d.domain.rules.ValidationMode;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CampaignModelTest {

    @Test
    void profileCreatesAStableSortedVersionManifest() {
        RulesetProfile profile = profile();

        assertEquals("srd-5.2.1-it", profile.versionManifest()
                .contentPackVersions().get(0).contentPackId());
        assertThrows(
                UnsupportedOperationException.class,
                () -> profile.contentPackVersions().add(new ContentPackVersion("x", "1")));
    }

    @Test
    void allThreePlayAxesAreIndependent() {
        PlayConfiguration configuration = new PlayConfiguration(
                ControlMode.AUTOMATIC,
                ValidationMode.STRICT,
                SpatialMode.ABSTRACT_ZONES);

        assertEquals(ControlMode.AUTOMATIC, configuration.controlMode());
        assertEquals(ValidationMode.STRICT, configuration.validationMode());
        assertEquals(SpatialMode.ABSTRACT_ZONES, configuration.spatialMode());
    }

    @Test
    void sessionPlanSupportsConditionalBranches() {
        Scene fight = Scene.encounter("fight", "Bandits", "bandits");
        Scene loot = new Scene("loot", "Loot", SceneType.REWARD);
        Scene prison = new Scene("prison", "Prisoners", SceneType.NARRATIVE);

        SessionPlan plan = new SessionPlan(
                "session-1",
                "The ambush",
                "fight",
                List.of(fight, loot, prison),
                List.of(
                        new SceneTransition(
                                "fight", "loot", "Victory",
                                TransitionCondition.onOutcome(EncounterOutcome.VICTORY)),
                        new SceneTransition(
                                "fight", "prison", "Defeat",
                                TransitionCondition.onOutcome(EncounterOutcome.DEFEAT))));

        assertEquals(2, plan.transitions().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.scenes().add(new Scene("x", "x", SceneType.NARRATIVE)));
    }

    @Test
    void sessionPlanRejectsEdgesToUnknownScenes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionPlan(
                        "session",
                        "Broken",
                        "start",
                        List.of(new Scene("start", "Start", SceneType.NARRATIVE)),
                        List.of(new SceneTransition("start", "missing"))));
    }

    @Test
    void campaignPinsProfileManifestAndChecksReferences() {
        RulesetProfile profile = profile();
        ActorTemplate hero = new ActorTemplate(
                "hero-template", "Hero", ActorKind.PLAYER_CHARACTER, 3);
        ActorRosterEntry rosterEntry = new ActorRosterEntry(
                "hero", "hero-template", "Hero");
        EncounterDefinition encounter = new EncounterDefinition(
                "bandits",
                "Bandit ambush",
                profile.versionManifest(),
                List.of(new EncounterPartyMember("hero", "Hero", 3)),
                List.of(EncounterEnemyGroup.base("bandit", "Bandit", 0.125, 25, 2)),
                EncounterDifficulty.LOW);
        SessionPlan plan = new SessionPlan(
                "session",
                "Session",
                "fight",
                List.of(Scene.encounter("fight", "Fight", "bandits")),
                List.of());

        Campaign campaign = new Campaign(
                "campaign",
                "Test campaign",
                profile,
                PlayConfiguration.defaultGuidedTracker(),
                List.of(hero),
                List.of(rosterEntry),
                List.of(encounter),
                List.of(plan),
                Map.of("critical-hits", "table ruling"));

        assertEquals(profile.versionManifest(), campaign.lockedRulesetManifest());
        assertEquals(ValidationMode.GUIDED, campaign.playConfiguration().validationMode());
    }

    private static RulesetProfile profile() {
        return new RulesetProfile(
                "srd-5.2.1",
                "5.5e — SRD 5.2.1",
                "5.2.1",
                "2025-09",
                List.of(
                        new ContentPackVersion("srd-5.2.1-monsters-it", "1.0"),
                        new ContentPackVersion("srd-5.2.1-it", "1.0")),
                CompatibilityMode.REVISED_2024,
                "it-IT",
                MeasurementSystem.IMPERIAL);
    }
}
