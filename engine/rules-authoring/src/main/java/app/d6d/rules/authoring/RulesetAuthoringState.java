package app.d6d.rules.authoring;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Metadati UI separati dalla semantica e dagli hash del regolamento. */
public record RulesetAuthoringState(
        int schemaVersion,
        Map<String, Map<String, RuleAuthoringMetadata>> byDraftId
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RulesetAuthoringState {
        if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported authoring schema version " + schemaVersion);
        }
        TreeMap<String, Map<String, RuleAuthoringMetadata>> drafts = new TreeMap<>();
        Objects.requireNonNull(byDraftId, "byDraftId").forEach((draftId, groups) -> {
            TreeMap<String, RuleAuthoringMetadata> sortedGroups = new TreeMap<>();
            Objects.requireNonNull(groups, "authoring groups").forEach((groupId, metadata) ->
                    sortedGroups.put(requireText(groupId, "authoringGroupId"),
                            Objects.requireNonNull(metadata, "authoring metadata")));
            drafts.put(requireText(draftId, "draftId"), Map.copyOf(new LinkedHashMap<>(sortedGroups)));
        });
        byDraftId = Map.copyOf(new LinkedHashMap<>(drafts));
    }

    public static RulesetAuthoringState empty() {
        return new RulesetAuthoringState(CURRENT_SCHEMA_VERSION, Map.of());
    }

    public Map<String, RuleAuthoringMetadata> groups(String draftId) {
        return byDraftId.getOrDefault(Objects.requireNonNull(draftId, "draftId"), Map.of());
    }

    public RulesetAuthoringState withGroup(
            String draftId,
            String groupId,
            RuleAuthoringMetadata metadata
    ) {
        LinkedHashMap<String, Map<String, RuleAuthoringMetadata>> drafts =
                new LinkedHashMap<>(byDraftId);
        LinkedHashMap<String, RuleAuthoringMetadata> groups =
                new LinkedHashMap<>(groups(draftId));
        groups.put(requireText(groupId, "authoringGroupId"),
                Objects.requireNonNull(metadata, "metadata"));
        drafts.put(requireText(draftId, "draftId"), groups);
        return new RulesetAuthoringState(CURRENT_SCHEMA_VERSION, drafts);
    }

    public RulesetAuthoringState withoutDraft(String draftId) {
        LinkedHashMap<String, Map<String, RuleAuthoringMetadata>> drafts =
                new LinkedHashMap<>(byDraftId);
        drafts.remove(Objects.requireNonNull(draftId, "draftId"));
        return new RulesetAuthoringState(CURRENT_SCHEMA_VERSION, drafts);
    }

    public RulesetAuthoringState withoutGroup(String draftId, String groupId) {
        LinkedHashMap<String, Map<String, RuleAuthoringMetadata>> drafts =
                new LinkedHashMap<>(byDraftId);
        LinkedHashMap<String, RuleAuthoringMetadata> groups =
                new LinkedHashMap<>(groups(draftId));
        groups.remove(Objects.requireNonNull(groupId, "groupId"));
        if (groups.isEmpty()) drafts.remove(Objects.requireNonNull(draftId, "draftId"));
        else drafts.put(requireText(draftId, "draftId"), groups);
        return new RulesetAuthoringState(CURRENT_SCHEMA_VERSION, drafts);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
