package app.d6d.domain.campaign;

import java.util.Map;
import java.util.Objects;

/** A node in a session plan graph. referenceId points to an encounter when applicable. */
public record Scene(
        String id,
        String name,
        SceneType type,
        String referenceId,
        Map<String, String> metadata) {

    public Scene {
        id = CampaignValues.requireText(id, "id");
        name = CampaignValues.requireText(name, "name");
        type = Objects.requireNonNull(type, "type");
        referenceId = CampaignValues.optionalText(referenceId, "referenceId");
        if (type == SceneType.ENCOUNTER && referenceId.isEmpty()) {
            throw new IllegalArgumentException("an ENCOUNTER scene needs an encounter referenceId");
        }
        metadata = CampaignValues.copyStringMap(metadata, "metadata");
    }

    public Scene(String id, String name, SceneType type) {
        this(id, name, type, "", Map.of());
    }

    public static Scene encounter(String id, String name, String encounterId) {
        return new Scene(id, name, SceneType.ENCOUNTER, encounterId, Map.of());
    }
}
