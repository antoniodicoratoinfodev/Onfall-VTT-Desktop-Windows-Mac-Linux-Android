package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Grafo esatto e ordinato usato per produrre una revisione appiattita. */
public record RulesetCompositionLock(
        String baseCanonicalHash,
        List<RulesetModuleRef> modules,
        List<RulesetConflictResolution> resolutions,
        String canonicalHash) {

    public RulesetCompositionLock {
        baseCanonicalHash = requireText(baseCanonicalHash, "baseCanonicalHash");
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        HashSet<String> ids = new HashSet<>();
        HashSet<String> hashes = new HashSet<>();
        for (RulesetModuleRef module : modules) {
            Objects.requireNonNull(module, "modules contains null");
            if (!ids.add(module.moduleId())) {
                throw new IllegalArgumentException("Duplicate module id in composition lock: " + module.moduleId());
            }
            if (!hashes.add(module.canonicalHash())) {
                throw new IllegalArgumentException("Duplicate module hash in composition lock: " + module.canonicalHash());
            }
        }

        ArrayList<RulesetConflictResolution> normalizedResolutions = new ArrayList<>(
                Objects.requireNonNull(resolutions, "resolutions"));
        normalizedResolutions.forEach(value -> Objects.requireNonNull(value, "resolutions contains null"));
        normalizedResolutions.sort(Comparator.comparing(value -> value.field().path()));
        HashSet<RuleFieldRef> fields = new HashSet<>();
        for (RulesetConflictResolution resolution : normalizedResolutions) {
            if (!fields.add(resolution.field())) {
                throw new IllegalArgumentException(
                        "More than one conflict resolution targets " + resolution.field().path());
            }
            if (!hashes.contains(resolution.winnerModuleHash())) {
                throw new IllegalArgumentException(
                        "Conflict winner is not part of the composition: " + resolution.winnerModuleHash());
            }
        }
        resolutions = List.copyOf(normalizedResolutions);

        String expected = RulesetCanonicalizer.compositionLockHash(
                baseCanonicalHash, modules, resolutions);
        canonicalHash = canonicalHash == null || canonicalHash.isBlank() ? expected : canonicalHash.trim();
        if (!canonicalHash.equals(expected)) {
            throw new IllegalArgumentException("Ruleset composition lock hash does not match its content");
        }
    }

    public static RulesetCompositionLock create(
            String baseCanonicalHash,
            List<RulesetModuleRef> modules,
            List<RulesetConflictResolution> resolutions) {
        return new RulesetCompositionLock(baseCanonicalHash, modules, resolutions, "");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
