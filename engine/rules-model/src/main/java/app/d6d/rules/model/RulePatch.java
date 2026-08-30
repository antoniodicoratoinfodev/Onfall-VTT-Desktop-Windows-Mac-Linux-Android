package app.d6d.rules.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Override copy-on-write di una singola regola della revisione base. */
public record RulePatch(
        String id,
        String targetEntityId,
        LocalizedRuleText nameOverride,
        LocalizedRuleText descriptionOverride,
        Map<String, String> attributeOverrides,
        Set<String> removedAttributes,
        Boolean enabledOverride,
        RuleKind kindOverride,
        RuleAutomationLevel automationLevelOverride,
        java.util.List<String> tagsOverride) {

    public RulePatch {
        id = requireText(id, "id");
        targetEntityId = requireText(targetEntityId, "targetEntityId");
        TreeMap<String, String> sorted = new TreeMap<>();
        Objects.requireNonNull(attributeOverrides, "attributeOverrides").forEach((key, value) ->
                sorted.put(requireText(key, "attribute key"), Objects.requireNonNull(value, "attribute value")));
        attributeOverrides = Map.copyOf(new LinkedHashMap<>(sorted));
        TreeSet<String> removed = new TreeSet<>();
        Objects.requireNonNull(removedAttributes, "removedAttributes")
                .forEach(key -> removed.add(requireText(key, "removed attribute")));
        removedAttributes = Set.copyOf(removed);
        if (tagsOverride != null) {
            TreeSet<String> tags = new TreeSet<>();
            tagsOverride.forEach(tag -> tags.add(requireText(tag, "tag")));
            tagsOverride = java.util.List.copyOf(tags);
        }
    }

    /** Costruttore del primo formato di patch, mantenuto per sorgenti e file precedenti. */
    public RulePatch(
            String id,
            String targetEntityId,
            LocalizedRuleText nameOverride,
            LocalizedRuleText descriptionOverride,
            Map<String, String> attributeOverrides,
            Set<String> removedAttributes,
            Boolean enabledOverride) {
        this(id, targetEntityId, nameOverride, descriptionOverride, attributeOverrides,
                removedAttributes, enabledOverride, null, null, null);
    }

    public static RulePatch copyOf(String patchId, RuleEntity source) {
        Objects.requireNonNull(source, "source");
        return new RulePatch(patchId, source.id(), source.name(), source.description(),
                Map.of(), Set.of(), null, null, null, null);
    }

    public RuleEntity apply(RuleEntity source, RulesetOrigin resultingOrigin) {
        Objects.requireNonNull(source, "source");
        if (!source.id().equals(targetEntityId)) {
            throw new IllegalArgumentException("Patch target does not match source entity");
        }
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>(source.attributes());
        removedAttributes.forEach(attributes::remove);
        attributes.putAll(attributeOverrides);
        return new RuleEntity(
                source.id(),
                kindOverride == null ? source.kind() : kindOverride,
                Objects.requireNonNull(resultingOrigin, "resultingOrigin"),
                nameOverride == null ? source.name() : nameOverride,
                descriptionOverride == null ? source.description() : descriptionOverride,
                source.derivedFrom().isBlank() ? source.id() : source.derivedFrom(),
                enabledOverride == null ? source.enabled() : enabledOverride,
                automationLevelOverride == null ? source.automationLevel() : automationLevelOverride,
                attributes,
                tagsOverride == null ? source.tags() : tagsOverride,
                source.source(),
                source.license(),
                source.sourcePage());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
