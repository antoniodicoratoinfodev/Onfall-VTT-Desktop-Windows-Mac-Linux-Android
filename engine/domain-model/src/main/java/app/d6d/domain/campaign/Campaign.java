package app.d6d.domain.campaign;

import app.d6d.domain.rules.PlayConfiguration;
import app.d6d.domain.rules.RulesetProfile;
import app.d6d.domain.rules.RulesetVersionManifest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Top-level aggregate for a version-pinned campaign. */
public record Campaign(
        String id,
        String name,
        RulesetProfile rulesetProfile,
        RulesetVersionManifest lockedRulesetManifest,
        PlayConfiguration playConfiguration,
        List<ActorTemplate> actorTemplates,
        List<ActorRosterEntry> roster,
        List<EncounterDefinition> encounters,
        List<SessionPlan> sessionPlans,
        Map<String, String> houseRules) {

    public Campaign {
        id = CampaignValues.requireText(id, "id");
        name = CampaignValues.requireText(name, "name");
        rulesetProfile = Objects.requireNonNull(rulesetProfile, "rulesetProfile");
        lockedRulesetManifest = Objects.requireNonNull(
                lockedRulesetManifest, "lockedRulesetManifest");
        if (!rulesetProfile.versionManifest().equals(lockedRulesetManifest)) {
            throw new IllegalArgumentException(
                    "the locked manifest must match the selected ruleset profile");
        }
        playConfiguration = Objects.requireNonNull(playConfiguration, "playConfiguration");
        Objects.requireNonNull(actorTemplates, "actorTemplates");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(encounters, "encounters");
        Objects.requireNonNull(sessionPlans, "sessionPlans");

        Set<String> templateIds = uniqueIds(
                actorTemplates, ActorTemplate::id, "actor template");
        Set<String> rosterIds = uniqueIds(roster, ActorRosterEntry::id, "roster entry");
        for (ActorRosterEntry entry : roster) {
            if (!templateIds.contains(entry.actorTemplateId())) {
                throw new IllegalArgumentException(
                        "roster entry refers to an unknown actor template: "
                                + entry.actorTemplateId());
            }
        }

        Set<String> encounterIds = uniqueIds(
                encounters, EncounterDefinition::id, "encounter");
        for (EncounterDefinition encounter : encounters) {
            if (!encounter.rulesetManifest().equals(lockedRulesetManifest)) {
                throw new IllegalArgumentException(
                        "encounter manifest differs from the campaign manifest: " + encounter.id());
            }
            for (EncounterPartyMember member : encounter.party()) {
                if (!rosterIds.contains(member.actorReferenceId())) {
                    throw new IllegalArgumentException(
                            "encounter party member is not in the campaign roster: "
                                    + member.actorReferenceId());
                }
            }
        }

        uniqueIds(sessionPlans, SessionPlan::id, "session plan");
        for (SessionPlan plan : sessionPlans) {
            for (Scene scene : plan.scenes()) {
                if (scene.type() == SceneType.ENCOUNTER
                        && !encounterIds.contains(scene.referenceId())) {
                    throw new IllegalArgumentException(
                            "scene refers to an unknown encounter: " + scene.referenceId());
                }
            }
        }

        actorTemplates = List.copyOf(actorTemplates);
        roster = List.copyOf(roster);
        encounters = List.copyOf(encounters);
        sessionPlans = List.copyOf(sessionPlans);
        houseRules = CampaignValues.copyStringMap(houseRules, "houseRules");
    }

    public Campaign(
            String id,
            String name,
            RulesetProfile rulesetProfile,
            PlayConfiguration playConfiguration,
            List<ActorTemplate> actorTemplates,
            List<ActorRosterEntry> roster,
            List<EncounterDefinition> encounters,
            List<SessionPlan> sessionPlans,
            Map<String, String> houseRules) {
        this(
                id,
                name,
                rulesetProfile,
                rulesetProfile == null ? null : rulesetProfile.versionManifest(),
                playConfiguration,
                actorTemplates,
                roster,
                encounters,
                sessionPlans,
                houseRules);
    }

    private static <T> Set<String> uniqueIds(
            List<T> values,
            java.util.function.Function<T, String> idExtractor,
            String description) {
        Set<String> ids = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, description + " list contains null");
            String valueId = idExtractor.apply(value);
            if (!ids.add(valueId)) {
                throw new IllegalArgumentException("duplicate " + description + " id: " + valueId);
            }
        }
        return ids;
    }
}
