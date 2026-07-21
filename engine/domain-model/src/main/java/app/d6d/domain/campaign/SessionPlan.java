package app.d6d.domain.campaign;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A validated directed graph of narrative, encounter, rest and reward scenes. */
public record SessionPlan(
        String id,
        String name,
        String entrySceneId,
        List<Scene> scenes,
        List<SceneTransition> transitions) {

    public SessionPlan {
        id = CampaignValues.requireText(id, "id");
        name = CampaignValues.requireText(name, "name");
        entrySceneId = CampaignValues.optionalText(entrySceneId, "entrySceneId");
        Objects.requireNonNull(scenes, "scenes");
        Objects.requireNonNull(transitions, "transitions");

        Set<String> sceneIds = new HashSet<>();
        for (Scene scene : scenes) {
            Objects.requireNonNull(scene, "scenes contains null");
            if (!sceneIds.add(scene.id())) {
                throw new IllegalArgumentException("duplicate scene id: " + scene.id());
            }
        }
        if (sceneIds.isEmpty() && !entrySceneId.isEmpty()) {
            throw new IllegalArgumentException("an empty plan cannot have an entry scene");
        }
        if (!sceneIds.isEmpty() && !sceneIds.contains(entrySceneId)) {
            throw new IllegalArgumentException("entrySceneId must identify a scene in this plan");
        }
        for (SceneTransition transition : transitions) {
            Objects.requireNonNull(transition, "transitions contains null");
            if (!sceneIds.contains(transition.fromSceneId())
                    || !sceneIds.contains(transition.toSceneId())) {
                throw new IllegalArgumentException(
                        "transition endpoints must identify scenes in this plan");
            }
        }
        scenes = List.copyOf(scenes);
        transitions = List.copyOf(transitions);
    }
}
