package app.d6d.domain.rules;

import java.util.Objects;

/** The three independent play axes selected by the game master. */
public record PlayConfiguration(
        ControlMode controlMode,
        ValidationMode validationMode,
        SpatialMode spatialMode) {

    public PlayConfiguration {
        Objects.requireNonNull(controlMode, "controlMode");
        Objects.requireNonNull(validationMode, "validationMode");
        Objects.requireNonNull(spatialMode, "spatialMode");
    }

    /** Default configuration for an assisted tabletop session. */
    public static PlayConfiguration defaultGuidedTracker() {
        return new PlayConfiguration(
                ControlMode.TRACKER,
                ValidationMode.GUIDED,
                SpatialMode.TABLETOP_MANUAL);
    }
}
