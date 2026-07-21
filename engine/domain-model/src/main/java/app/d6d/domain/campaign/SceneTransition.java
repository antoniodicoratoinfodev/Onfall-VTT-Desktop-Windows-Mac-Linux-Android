package app.d6d.domain.campaign;

import java.util.Objects;

/** A directed edge in a session plan graph. */
public record SceneTransition(
        String fromSceneId,
        String toSceneId,
        String label,
        TransitionCondition condition) {

    public SceneTransition {
        fromSceneId = CampaignValues.requireText(fromSceneId, "fromSceneId");
        toSceneId = CampaignValues.requireText(toSceneId, "toSceneId");
        label = CampaignValues.optionalText(label, "label");
        condition = Objects.requireNonNull(condition, "condition");
    }

    public SceneTransition(String fromSceneId, String toSceneId) {
        this(fromSceneId, toSceneId, "", TransitionCondition.always());
    }
}
