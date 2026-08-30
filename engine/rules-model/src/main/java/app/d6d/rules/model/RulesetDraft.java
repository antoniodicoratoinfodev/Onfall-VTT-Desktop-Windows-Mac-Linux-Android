package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Bozza mutabile per sostituzione: il repository ne salva ogni revisione atomica. */
public record RulesetDraft(
        String id,
        String projectId,
        String baseCanonicalHash,
        String name,
        String description,
        RulesetOrigin origin,
        RulesetRuntimeConfig runtime,
        List<RulePatch> patches,
        List<RuleEntity> additions,
        long saveRevision,
        String modifiedAt) {

    public RulesetDraft {
        id = requireText(id, "id");
        projectId = requireText(projectId, "projectId");
        baseCanonicalHash = requireText(baseCanonicalHash, "baseCanonicalHash");
        name = requireText(name, "name");
        description = description == null ? "" : description.trim();
        origin = Objects.requireNonNull(origin, "origin");
        if (origin == RulesetOrigin.BUNDLED_STANDARD) {
            throw new IllegalArgumentException("A bundled standard cannot be a draft");
        }
        runtime = Objects.requireNonNull(runtime, "runtime");
        if (saveRevision < 0) throw new IllegalArgumentException("saveRevision cannot be negative");
        modifiedAt = modifiedAt == null ? "" : modifiedAt.trim();

        ArrayList<RulePatch> sortedPatches = new ArrayList<>(Objects.requireNonNull(patches, "patches"));
        sortedPatches.sort(java.util.Comparator.comparing(RulePatch::id));
        HashSet<String> patchIds = new HashSet<>();
        HashSet<String> targets = new HashSet<>();
        for (RulePatch patch : sortedPatches) {
            if (!patchIds.add(patch.id())) throw new IllegalArgumentException("Duplicate patch id: " + patch.id());
            if (!targets.add(patch.targetEntityId())) {
                throw new IllegalArgumentException("More than one patch targets " + patch.targetEntityId());
            }
        }
        patches = List.copyOf(sortedPatches);

        ArrayList<RuleEntity> sortedAdditions = new ArrayList<>(Objects.requireNonNull(additions, "additions"));
        sortedAdditions.sort(java.util.Comparator.comparing(RuleEntity::id));
        HashSet<String> additionIds = new HashSet<>();
        for (RuleEntity addition : sortedAdditions) {
            if (addition.origin() == RulesetOrigin.BUNDLED_STANDARD) {
                throw new IllegalArgumentException("A draft addition cannot be bundled standard content");
            }
            if (!additionIds.add(addition.id())) {
                throw new IllegalArgumentException("Duplicate addition id: " + addition.id());
            }
        }
        additions = List.copyOf(sortedAdditions);
    }

    public RulesetDraft withContent(
            String changedName,
            String changedDescription,
            RulesetRuntimeConfig changedRuntime,
            List<RulePatch> changedPatches,
            List<RuleEntity> changedAdditions,
            String changedAt) {
        return new RulesetDraft(id, projectId, baseCanonicalHash, changedName, changedDescription,
                origin, changedRuntime, changedPatches, changedAdditions, saveRevision + 1, changedAt);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
