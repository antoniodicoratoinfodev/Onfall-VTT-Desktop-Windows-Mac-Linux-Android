package app.d6d.domain.campaign;

/** A persistent roster slot referring to a lightweight actor template. */
public record ActorRosterEntry(
        String id,
        String actorTemplateId,
        String displayName,
        boolean active) {

    public ActorRosterEntry {
        id = CampaignValues.requireText(id, "id");
        actorTemplateId = CampaignValues.requireText(actorTemplateId, "actorTemplateId");
        displayName = CampaignValues.requireText(displayName, "displayName");
    }

    public ActorRosterEntry(String id, String actorTemplateId, String displayName) {
        this(id, actorTemplateId, displayName, true);
    }
}
