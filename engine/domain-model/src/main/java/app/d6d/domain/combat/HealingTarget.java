package app.d6d.domain.combat;

/** Defines which friendly combatant a healing ability may target. */
public enum HealingTarget {
    /** The healer only. */
    SELF,
    /** A different combatant on the healer's side. */
    ALLY,
    /** Either the healer or another combatant on the same side. */
    SELF_OR_ALLY
}
