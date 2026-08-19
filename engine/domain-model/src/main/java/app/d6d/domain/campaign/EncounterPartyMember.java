package app.d6d.domain.campaign;

/** Character snapshot used by encounter budgeting. */
public record EncounterPartyMember(
        String actorReferenceId,
        String displayName,
        int level,
        boolean present) {

    public EncounterPartyMember {
        actorReferenceId = CampaignValues.requireText(actorReferenceId, "actorReferenceId");
        displayName = CampaignValues.requireText(displayName, "displayName");
        if (level < 1 || level > 20) {
            throw new IllegalArgumentException("level must be between 1 and 20");
        }
    }

    public EncounterPartyMember(String actorReferenceId, String displayName, int level) {
        this(actorReferenceId, displayName, level, true);
    }
}
