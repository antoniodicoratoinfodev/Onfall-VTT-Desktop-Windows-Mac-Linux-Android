package app.d6d.domain.campaign;

import java.util.Map;
import java.util.Objects;

/** Lightweight campaign actor definition, deliberately independent from combat state. */
public record ActorTemplate(
        String id,
        String name,
        ActorKind kind,
        int level,
        Map<String, String> attributes) {

    public ActorTemplate {
        id = CampaignValues.requireText(id, "id");
        name = CampaignValues.requireText(name, "name");
        kind = Objects.requireNonNull(kind, "kind");
        if (level < 0 || level > 20) {
            throw new IllegalArgumentException("level must be between 0 and 20");
        }
        if (kind == ActorKind.PLAYER_CHARACTER && level == 0) {
            throw new IllegalArgumentException("a player character must have level 1-20");
        }
        attributes = CampaignValues.copyStringMap(attributes, "attributes");
    }

    public ActorTemplate(String id, String name, ActorKind kind, int level) {
        this(id, name, kind, level, Map.of());
    }
}
