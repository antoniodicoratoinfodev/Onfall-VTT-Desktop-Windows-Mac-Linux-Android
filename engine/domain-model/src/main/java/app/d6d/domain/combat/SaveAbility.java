package app.d6d.domain.combat;

/**
 * The six ability scores a saving throw can be based on.
 *
 * <p>Mirrors the sheet model's {@code Ability} enum but lives in the combat domain
 * so the engine can resolve area spells (a Fireball forces a Dexterity save)
 * without depending on the sheet layer.</p>
 */
public enum SaveAbility {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA
}
