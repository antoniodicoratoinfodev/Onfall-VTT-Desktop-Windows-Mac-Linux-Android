package app.d6d.engine.ai;

/** Tipo stabile di una scelta eseguita dalla CPU, adatto anche alla presentazione. */
public enum EnemyCpuActionType {
    MOVE,
    ATTACK,
    AREA_ATTACK,
    HEAL,
    ACTIVATE,
    TURN_ENDED,
    ENCOUNTER_RESOLVED,
    SKIPPED
}
