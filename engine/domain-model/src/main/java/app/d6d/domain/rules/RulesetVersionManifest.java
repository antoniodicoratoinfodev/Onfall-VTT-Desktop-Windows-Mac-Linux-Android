package app.d6d.domain.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable version snapshot used by campaigns, encounters and replays. */
public record RulesetVersionManifest(
        String rulesetId,
        String rulesVersion,
        String errataVersion,
        List<ContentPackVersion> contentPackVersions) {

    public RulesetVersionManifest {
        rulesetId = requireText(rulesetId, "rulesetId");
        rulesVersion = requireText(rulesVersion, "rulesVersion");
        errataVersion = requireText(errataVersion, "errataVersion");
        Objects.requireNonNull(contentPackVersions, "contentPackVersions");

        List<ContentPackVersion> sorted = new ArrayList<>(contentPackVersions.size());
        Set<String> ids = new HashSet<>();
        for (ContentPackVersion pack : contentPackVersions) {
            Objects.requireNonNull(pack, "contentPackVersions contains null");
            if (!ids.add(pack.contentPackId())) {
                throw new IllegalArgumentException(
                        "duplicate content pack: " + pack.contentPackId());
            }
            sorted.add(pack);
        }
        sorted.sort(Comparator.comparing(ContentPackVersion::contentPackId));
        contentPackVersions = List.copyOf(sorted);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
