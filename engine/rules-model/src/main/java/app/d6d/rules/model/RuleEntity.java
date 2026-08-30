package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Una definizione indirizzabile del catalogo Regole. */
public record RuleEntity(
        String id,
        RuleKind kind,
        RulesetOrigin origin,
        LocalizedRuleText name,
        LocalizedRuleText description,
        String derivedFrom,
        boolean enabled,
        RuleAutomationLevel automationLevel,
        Map<String, String> attributes,
        List<String> tags,
        String source,
        String license,
        int sourcePage) {

    public RuleEntity {
        id = requireText(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        origin = Objects.requireNonNull(origin, "origin");
        name = Objects.requireNonNull(name, "name");
        description = Objects.requireNonNull(description, "description");
        derivedFrom = derivedFrom == null ? "" : derivedFrom.trim();
        automationLevel = Objects.requireNonNull(automationLevel, "automationLevel");
        source = source == null ? "" : source.trim();
        license = license == null ? "" : license.trim();
        if (sourcePage < 0) throw new IllegalArgumentException("sourcePage cannot be negative");

        TreeMap<String, String> sortedAttributes = new TreeMap<>();
        Objects.requireNonNull(attributes, "attributes").forEach((key, value) ->
                sortedAttributes.put(requireText(key, "attribute key"),
                        Objects.requireNonNull(value, "attribute value").trim()));
        attributes = Map.copyOf(new LinkedHashMap<>(sortedAttributes));

        ArrayList<String> sortedTags = new ArrayList<>();
        Objects.requireNonNull(tags, "tags").forEach(tag -> sortedTags.add(requireText(tag, "tag")));
        sortedTags.sort(String::compareTo);
        tags = List.copyOf(sortedTags.stream().distinct().toList());
    }

    public RuleEntity withLocalizedText(LocalizedRuleText changedName, LocalizedRuleText changedDescription) {
        return new RuleEntity(id, kind, origin, changedName, changedDescription, derivedFrom, enabled,
                automationLevel, attributes, tags, source, license, sourcePage);
    }

    public RuleEntity withAttributes(Map<String, String> changedAttributes) {
        return new RuleEntity(id, kind, origin, name, description, derivedFrom, enabled,
                automationLevel, changedAttributes, tags, source, license, sourcePage);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
