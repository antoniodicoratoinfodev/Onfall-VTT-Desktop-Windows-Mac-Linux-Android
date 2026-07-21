package app.d6d.domain.combat;

/** The turn boundary that consumes one duration occurrence. */
public enum ConditionExpiry {
    MANUAL,
    START_OF_TARGET_TURN,
    END_OF_TARGET_TURN,
    START_OF_SOURCE_TURN,
    END_OF_SOURCE_TURN,
    CONCENTRATION
}
