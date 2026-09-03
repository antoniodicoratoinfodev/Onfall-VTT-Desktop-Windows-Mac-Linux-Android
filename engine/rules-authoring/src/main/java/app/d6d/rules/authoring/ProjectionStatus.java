package app.d6d.rules.authoring;

/** Quanto fedelmente un dato esistente può essere modificato nell'editor visuale. */
public enum ProjectionStatus {
    EXACT,
    PARTIAL,
    EXPERT_ONLY;

    public ProjectionStatus combine(ProjectionStatus other) {
        if (this == EXPERT_ONLY || other == EXPERT_ONLY) return EXPERT_ONLY;
        if (this == PARTIAL || other == PARTIAL) return PARTIAL;
        return EXACT;
    }
}
