package app.d6d.domain.campaign;

import app.d6d.domain.rules.EncounterDifficulty;
import app.d6d.domain.rules.RulesetVersionManifest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable encounter plan; live combat creates instances from this snapshot. */
public record EncounterDefinition(
        String id,
        String name,
        RulesetVersionManifest rulesetManifest,
        List<EncounterPartyMember> party,
        List<EncounterEnemyGroup> enemies,
        EncounterDifficulty targetDifficulty,
        boolean allowOverBudget,
        Map<String, String> metadata) {

    public EncounterDefinition {
        id = CampaignValues.requireText(id, "id");
        name = CampaignValues.requireText(name, "name");
        rulesetManifest = Objects.requireNonNull(rulesetManifest, "rulesetManifest");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(enemies, "enemies");
        targetDifficulty = Objects.requireNonNull(targetDifficulty, "targetDifficulty");

        Set<String> actorReferences = new HashSet<>();
        for (EncounterPartyMember member : party) {
            Objects.requireNonNull(member, "party contains null");
            if (!actorReferences.add(member.actorReferenceId())) {
                throw new IllegalArgumentException(
                        "duplicate party actor reference: " + member.actorReferenceId());
            }
        }
        enemies.forEach(enemy -> Objects.requireNonNull(enemy, "enemies contains null"));
        party = List.copyOf(party);
        enemies = List.copyOf(enemies);
        metadata = CampaignValues.copyStringMap(metadata, "metadata");
    }

    public EncounterDefinition(
            String id,
            String name,
            RulesetVersionManifest rulesetManifest,
            List<EncounterPartyMember> party,
            List<EncounterEnemyGroup> enemies,
            EncounterDifficulty targetDifficulty) {
        this(id, name, rulesetManifest, party, enemies, targetDifficulty, false, Map.of());
    }
}
