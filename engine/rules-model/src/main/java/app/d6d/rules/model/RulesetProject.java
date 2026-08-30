package app.d6d.rules.model;

import java.util.List;
import java.util.Objects;

/** Identità stabile e visibile di una linea homebrew. */
public record RulesetProject(
        String id,
        String name,
        String description,
        String baseCanonicalHash,
        List<String> revisionHashes,
        String defaultRevisionHash,
        boolean archived) {

    public RulesetProject {
        id = requireText(id, "id");
        name = requireText(name, "name");
        description = description == null ? "" : description.trim();
        baseCanonicalHash = requireText(baseCanonicalHash, "baseCanonicalHash");
        revisionHashes = List.copyOf(Objects.requireNonNull(revisionHashes, "revisionHashes"));
        defaultRevisionHash = defaultRevisionHash == null ? "" : defaultRevisionHash.trim();
        if (!defaultRevisionHash.isBlank() && !revisionHashes.contains(defaultRevisionHash)) {
            throw new IllegalArgumentException("Default revision is not part of the project");
        }
    }

    public RulesetProject withPublishedRevision(RulesetRevision revision) {
        if (!revision.projectId().equals(id)) {
            throw new IllegalArgumentException("Revision belongs to another project");
        }
        List<String> hashes = revisionHashes.contains(revision.canonicalHash())
                ? revisionHashes
                : java.util.stream.Stream.concat(revisionHashes.stream(),
                        java.util.stream.Stream.of(revision.canonicalHash())).toList();
        return new RulesetProject(id, name, description, baseCanonicalHash, hashes,
                revision.canonicalHash(), archived);
    }

    public RulesetProject withMetadata(String changedName, String changedDescription) {
        return new RulesetProject(id, changedName, changedDescription, baseCanonicalHash,
                revisionHashes, defaultRevisionHash, archived);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
