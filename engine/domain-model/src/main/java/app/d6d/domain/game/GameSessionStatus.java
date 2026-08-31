package app.d6d.domain.game;

/** Ciclo di vita generale, indipendente dall'esistenza di un combattimento. */
public enum GameSessionStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED
}
