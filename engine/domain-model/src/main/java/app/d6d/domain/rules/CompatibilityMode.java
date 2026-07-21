package app.d6d.domain.rules;

/** Prevents legacy and revised rules from being mixed implicitly. */
public enum CompatibilityMode {
    LEGACY_2014,
    REVISED_2024,
    CUSTOM
}
