package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compone base, patch e aggiunte in una revisione senza conflitti impliciti. */
public final class RulesetResolver {
    private RulesetResolver() {
    }

    public static RulesetRevision resolve(
            RulesetRevision base,
            RulesetDraft draft,
            String revisionId,
            String version,
            String publishedAt) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(draft, "draft");
        if (!base.canonicalHash().equals(draft.baseCanonicalHash())) {
            throw new IllegalArgumentException("Draft base hash does not match the selected revision");
        }

        LinkedHashMap<String, RuleEntity> effective = new LinkedHashMap<>();
        base.entities().forEach(entity -> effective.put(entity.id(), entity));
        for (RulePatch patch : draft.patches()) {
            RuleEntity source = effective.get(patch.targetEntityId());
            if (source == null) {
                throw new IllegalArgumentException("Patch target does not exist: " + patch.targetEntityId());
            }
            effective.put(source.id(), patch.apply(source, draft.origin()));
        }
        for (RuleEntity addition : draft.additions()) {
            if (effective.putIfAbsent(addition.id(), addition) != null) {
                throw new IllegalArgumentException("Addition collides with an existing rule: " + addition.id());
            }
        }

        synchronizeRuntimeAttributes(effective, draft.runtime());
        return RulesetRevision.create(
                draft.projectId(),
                revisionId,
                version,
                draft.name(),
                draft.description(),
                draft.origin(),
                base.canonicalHash(),
                draft.runtime(),
                List.copyOf(effective.values()),
                publishedAt);
    }

    public static RulesetRevision preview(RulesetRevision base, RulesetDraft draft) {
        return resolve(base, draft, "draft:" + draft.id(), "draft", draft.modifiedAt());
    }

    static void synchronizeRuntimeAttributes(
            Map<String, RuleEntity> entities,
            RulesetRuntimeConfig runtime) {
        for (String id : List.of(CoreRuleIds.CRITICAL_HIT, CoreRuleIds.EXHAUSTION, CoreRuleIds.PROFICIENCY)) {
            RuleEntity entity = entities.get(id);
            if (entity == null) continue;
            LinkedHashMap<String, String> attributes = new LinkedHashMap<>(entity.attributes());
            attributes.putAll(runtime.attributesFor(id));
            entities.put(id, entity.withAttributes(attributes));
        }
    }
}
