package app.d6d.domain.rules;

import java.util.Objects;

/** An exact content-pack revision pinned by a ruleset manifest. */
public record ContentPackVersion(String contentPackId, String version, String contentHash) {

    public ContentPackVersion {
        contentPackId = requireText(contentPackId, "contentPackId");
        version = requireText(version, "version");
        contentHash = Objects.requireNonNull(contentHash, "contentHash").trim();
    }

    public ContentPackVersion(String contentPackId, String version) {
        this(contentPackId, version, "");
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
