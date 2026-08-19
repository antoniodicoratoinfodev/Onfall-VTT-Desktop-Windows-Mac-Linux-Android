package app.d6d.domain.rules;

import java.util.List;
import java.util.Objects;

/** Describes a selectable ruleset and the exact content revisions it uses. */
public record RulesetProfile(
        String rulesetId,
        String displayLabel,
        String rulesVersion,
        String errataVersion,
        List<ContentPackVersion> contentPackVersions,
        CompatibilityMode compatibilityMode,
        String languageTag,
        MeasurementSystem measurementSystem) {

    public RulesetProfile {
        rulesetId = requireText(rulesetId, "rulesetId");
        displayLabel = requireText(displayLabel, "displayLabel");
        rulesVersion = requireText(rulesVersion, "rulesVersion");
        errataVersion = requireText(errataVersion, "errataVersion");
        compatibilityMode = Objects.requireNonNull(compatibilityMode, "compatibilityMode");
        languageTag = requireText(languageTag, "languageTag");
        measurementSystem = Objects.requireNonNull(measurementSystem, "measurementSystem");

        // Reuse manifest validation to reject duplicate packs and create a stable order.
        contentPackVersions = new RulesetVersionManifest(
                rulesetId, rulesVersion, errataVersion, contentPackVersions)
                .contentPackVersions();
    }

    public RulesetVersionManifest versionManifest() {
        return new RulesetVersionManifest(
                rulesetId, rulesVersion, errataVersion, contentPackVersions);
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
