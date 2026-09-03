package app.d6d.rules.authoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Presentazione lossless di un gruppo di una o più entità generate. */
public record RuleAuthoringMetadata(
        String recipeId,
        int recipeVersion,
        List<String> generatedEntityIds,
        Map<String, String> visualSections,
        Set<String> protectedFields,
        Map<String, String> lastProjectedContentHashes,
        List<RuleAuthoringExample> examples
) {
    public RuleAuthoringMetadata {
        recipeId = requireText(recipeId, "recipeId");
        if (recipeVersion < 1) throw new IllegalArgumentException("recipeVersion must be positive");
        ArrayList<String> entities = new ArrayList<>();
        Objects.requireNonNull(generatedEntityIds, "generatedEntityIds")
                .forEach(value -> entities.add(requireText(value, "generatedEntityId")));
        generatedEntityIds = List.copyOf(entities.stream().distinct().toList());
        visualSections = sortedMap(visualSections, "visualSections");
        TreeMap<String, String> fields = new TreeMap<>();
        Objects.requireNonNull(protectedFields, "protectedFields")
                .forEach(value -> fields.put(requireText(value, "protectedField"), value.trim()));
        protectedFields = Set.copyOf(new LinkedHashSet<>(fields.values()));
        lastProjectedContentHashes = sortedMap(
                lastProjectedContentHashes, "lastProjectedContentHashes");
        examples = List.copyOf(Objects.requireNonNull(examples, "examples"));
        examples.forEach(value -> Objects.requireNonNull(value, "examples contains null"));
    }

    private static Map<String, String> sortedMap(Map<String, String> source, String field) {
        TreeMap<String, String> result = new TreeMap<>();
        Objects.requireNonNull(source, field).forEach((key, value) ->
                result.put(requireText(key, field + " key"),
                        Objects.requireNonNull(value, field + " value")));
        return Map.copyOf(result);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
