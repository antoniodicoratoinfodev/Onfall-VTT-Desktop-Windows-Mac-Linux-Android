package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Layer di regole immutabile e content-addressed.
 *
 * <p>Un modulo non è eseguito direttamente: il composer lo applica a una base,
 * risolve esplicitamente i conflitti e produce una revisione appiattita.</p>
 */
public record RulesetModule(
        String id,
        String version,
        LocalizedRuleText name,
        LocalizedRuleText description,
        RulesetOrigin origin,
        String requiredSemanticsVersion,
        List<RulesetModuleRef> dependencies,
        Set<String> incompatibleModuleIds,
        List<RulePatch> patches,
        List<RuleEntity> additions,
        String canonicalHash) {

    public RulesetModule {
        id = requireText(id, "id");
        version = requireText(version, "version");
        name = Objects.requireNonNull(name, "name");
        description = Objects.requireNonNull(description, "description");
        origin = Objects.requireNonNull(origin, "origin");
        requiredSemanticsVersion = requireText(requiredSemanticsVersion, "requiredSemanticsVersion");

        ArrayList<RulesetModuleRef> normalizedDependencies = new ArrayList<>(
                Objects.requireNonNull(dependencies, "dependencies"));
        normalizedDependencies.forEach(value -> Objects.requireNonNull(value, "dependencies contains null"));
        normalizedDependencies.sort(Comparator.comparing(RulesetModuleRef::moduleId)
                .thenComparing(RulesetModuleRef::canonicalHash));
        HashSet<String> dependencyIds = new HashSet<>();
        for (RulesetModuleRef dependency : normalizedDependencies) {
            if (dependency.moduleId().equals(id)) {
                throw new IllegalArgumentException("A module cannot depend on itself");
            }
            if (!dependencyIds.add(dependency.moduleId())) {
                throw new IllegalArgumentException("Duplicate module dependency: " + dependency.moduleId());
            }
        }
        dependencies = List.copyOf(normalizedDependencies);

        TreeSet<String> normalizedIncompatible = new TreeSet<>();
        for (String value : Objects.requireNonNull(incompatibleModuleIds, "incompatibleModuleIds")) {
            String normalized = requireText(value, "incompatibleModuleId");
            if (normalized.equals(id)) throw new IllegalArgumentException("A module cannot be incompatible with itself");
            normalizedIncompatible.add(normalized);
        }
        incompatibleModuleIds = Set.copyOf(new LinkedHashSet<>(normalizedIncompatible));

        ArrayList<RulePatch> normalizedPatches = new ArrayList<>(Objects.requireNonNull(patches, "patches"));
        normalizedPatches.forEach(value -> Objects.requireNonNull(value, "patches contains null"));
        normalizedPatches.sort(Comparator.comparing(RulePatch::id));
        HashSet<String> patchIds = new HashSet<>();
        HashSet<String> patchTargets = new HashSet<>();
        for (RulePatch patch : normalizedPatches) {
            if (!patchIds.add(patch.id())) throw new IllegalArgumentException("Duplicate module patch: " + patch.id());
            if (!patchTargets.add(patch.targetEntityId())) {
                throw new IllegalArgumentException("More than one module patch targets " + patch.targetEntityId());
            }
        }
        patches = List.copyOf(normalizedPatches);

        ArrayList<RuleEntity> normalizedAdditions = new ArrayList<>(Objects.requireNonNull(additions, "additions"));
        normalizedAdditions.forEach(value -> Objects.requireNonNull(value, "additions contains null"));
        normalizedAdditions.sort(Comparator.comparing(RuleEntity::id));
        HashSet<String> additionIds = new HashSet<>();
        for (RuleEntity addition : normalizedAdditions) {
            if (!additionIds.add(addition.id())) {
                throw new IllegalArgumentException("Duplicate module addition: " + addition.id());
            }
            if (addition.origin() != origin) {
                throw new IllegalArgumentException("Module addition origin differs from its module: " + addition.id());
            }
            if (patchTargets.contains(addition.id())) {
                throw new IllegalArgumentException("A module cannot patch its own addition: " + addition.id());
            }
        }
        additions = List.copyOf(normalizedAdditions);

        String expected = RulesetCanonicalizer.moduleHash(
                id, version, name, description, origin, requiredSemanticsVersion,
                dependencies, incompatibleModuleIds, patches, additions);
        canonicalHash = canonicalHash == null || canonicalHash.isBlank() ? expected : canonicalHash.trim();
        if (!canonicalHash.equals(expected)) {
            throw new IllegalArgumentException("Ruleset module canonical hash does not match its content");
        }
    }

    public static RulesetModule create(
            String id,
            String version,
            LocalizedRuleText name,
            LocalizedRuleText description,
            RulesetOrigin origin,
            String requiredSemanticsVersion,
            List<RulesetModuleRef> dependencies,
            Set<String> incompatibleModuleIds,
            List<RulePatch> patches,
            List<RuleEntity> additions) {
        return new RulesetModule(id, version, name, description, origin, requiredSemanticsVersion,
                dependencies, incompatibleModuleIds, patches, additions, "");
    }

    public RulesetModuleRef reference() {
        return RulesetModuleRef.from(this);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
