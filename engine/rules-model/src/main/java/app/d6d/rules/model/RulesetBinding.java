package app.d6d.rules.model;

import java.util.Objects;

/** Riferimento esatto e trasportabile alla revisione eseguita da una sessione. */
public record RulesetBinding(
        String projectId,
        String revisionId,
        String canonicalHash,
        String runtimeHash,
        String runtimeSemanticsVersion,
        String displayName,
        boolean legacy) {

    public RulesetBinding {
        projectId = requireText(projectId, "projectId");
        revisionId = requireText(revisionId, "revisionId");
        canonicalHash = requireText(canonicalHash, "canonicalHash");
        runtimeHash = requireText(runtimeHash, "runtimeHash");
        runtimeSemanticsVersion = requireText(runtimeSemanticsVersion, "runtimeSemanticsVersion");
        displayName = requireText(displayName, "displayName");
    }

    public static RulesetBinding legacySrd(String version) {
        String normalized = version == null || version.isBlank() ? "srd-5.2.1" : version.trim();
        return new RulesetBinding(
                "onfall:srd521",
                "legacy:" + normalized,
                "legacy:" + normalized,
                "legacy:srd521-runtime-v1",
                RulesetRuntimeConfig.CURRENT_SEMANTICS,
                "SRD 5.2.1",
                true);
    }

    public RulesetBinding asResolved(RulesetRevision revision) {
        Objects.requireNonNull(revision, "revision");
        return revision.binding();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
