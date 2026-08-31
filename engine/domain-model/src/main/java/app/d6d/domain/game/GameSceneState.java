package app.d6d.domain.game;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Scena sociale, esplorativa, tattica o inventata dal regolamento. */
public record GameSceneState(
        String id,
        String name,
        String kind,
        String procedureRef,
        int phaseIndex,
        List<String> participantIds,
        Map<String, String> metadata,
        long revision) {

    public GameSceneState {
        id = requireText(id, "id");
        name = requireText(name, "name");
        kind = requireText(kind, "kind");
        procedureRef = procedureRef == null ? "" : procedureRef.trim();
        if (phaseIndex < 0) throw new IllegalArgumentException("phaseIndex cannot be negative");
        participantIds = List.copyOf(Objects.requireNonNull(participantIds, "participantIds"));
        if (participantIds.stream().anyMatch(value -> value == null || value.isBlank())
                || participantIds.stream().distinct().count() != participantIds.size()) {
            throw new IllegalArgumentException("Scene participant ids must be non-blank and unique");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        Objects.requireNonNull(metadata, "metadata").forEach((key, value) ->
                sorted.put(requireText(key, "metadata key"), Objects.requireNonNull(value, "metadata value")));
        metadata = Map.copyOf(sorted);
        if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
    }

    public GameSceneState withParticipant(String actorId) {
        String checked = requireText(actorId, "actorId");
        if (participantIds.contains(checked)) return this;
        java.util.ArrayList<String> changed = new java.util.ArrayList<>(participantIds);
        changed.add(checked);
        return new GameSceneState(id, name, kind, procedureRef, phaseIndex, changed, metadata, revision + 1);
    }

    public GameSceneState withoutParticipant(String actorId) {
        if (!participantIds.contains(actorId)) return this;
        return new GameSceneState(id, name, kind, procedureRef, phaseIndex,
                participantIds.stream().filter(value -> !value.equals(actorId)).toList(),
                metadata, revision + 1);
    }

    public GameSceneState withPhase(int nextPhase) {
        if (nextPhase < 0) throw new IllegalArgumentException("phaseIndex cannot be negative");
        if (nextPhase == phaseIndex) return this;
        return new GameSceneState(id, name, kind, procedureRef, nextPhase,
                participantIds, metadata, revision + 1);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
