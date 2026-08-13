package app.d6d.engine.ai;

import java.util.Objects;

/**
 * Numeri che distinguono un livello di difficolta' dall'altro.
 *
 * <p>{@link EnemyCpu} applica sempre le stesse formule: quanto vale una cura, un
 * attacco, un'area, uno spostamento e chi diventa il bersaglio prioritario del
 * gruppo. Cambiano soltanto i pesi raccolti qui, cosi' un nuovo livello e' un
 * blocco di dati e non una nuova ramificazione sparsa per il pianificatore.</p>
 *
 * <p>Non tocca le regole: tiri, CA, punti ferita e budget del turno restano quelli
 * del motore per tutti e tre i livelli.</p>
 *
 * <p>I livelli semplici esprimono la propria priorita' con costanti dominanti
 * invece che con una somma pesata: un {@code base} da centinaia di migliaia rende
 * la categoria vincente in blocco, e i pesi minori ordinano soltanto al suo
 * interno. E' lo stesso meccanismo che il pianificatore usava prima, ora dichiarato
 * invece che nascosto in uno {@code switch}.</p>
 */
public record EnemyCpuProfile(
        Healing healing,
        Attack attack,
        Area area,
        Movement movement,
        Focus focus,
        /** Falso per chi non spende risorse in potenziamenti. */
        boolean usesActivations,
        double activationScore) {

    public EnemyCpuProfile {
        Objects.requireNonNull(healing, "healing");
        Objects.requireNonNull(attack, "attack");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(focus, "focus");
    }

    /**
     * Cure.
     *
     * <p>{@code dangerRatio} e' la frazione di punti ferita sotto la quale un
     * alleato viene considerato in pericolo: decide sia se curarlo sia quanto
     * potenziare la cura.</p>
     */
    public record Healing(
            double dangerRatio,
            double base,
            double usefulHealingWeight,
            double woundedWeight,
            double defeatedBonus,
            double healerBonus) { }

    /**
     * Attacchi singoli.
     *
     * <p>{@code nearestFirstWeight} e {@code abilityOrderWeight} sottraggono: chi
     * gioca semplice sceglie il bersaglio piu' vicino e la prima capacita' utile
     * invece di confrontare i danni attesi.</p>
     */
    public record Attack(
            double base,
            double expectedDamageWeight,
            double killBonusWeight,
            double woundedWeight,
            double surroundedWeight,
            double focusBonus,
            double healerBonus,
            double nearestFirstWeight,
            double abilityOrderWeight) { }

    /**
     * Effetti ad area.
     *
     * <p>{@code refusesFriendlyFire} scarta ogni centro che tocchi un alleato;
     * chi non lo fa pesa il danno amico con le due penalita'.
     * {@code requiresPositiveScore} scarta invece i centri che non convengono.</p>
     */
    public record Area(
            boolean refusesFriendlyFire,
            boolean requiresPositiveScore,
            double base,
            double multiTargetBonus,
            double damageWeight,
            double hitWeight,
            double friendlyDamagePenalty,
            double friendlyHitPenalty,
            double focusBonus,
            double abilityOrderWeight) { }

    /**
     * Spostamento verso il bersaglio.
     *
     * <p>{@code seeksSurround} cerca la casella libera che accerchia invece della
     * semplice riduzione di distanza; {@code halvesDistance} avvicina soltanto di
     * meta' del movimento disponibile. Il posizionamento a distanza non dipende
     * dal livello: chi tira usa la stessa gittata preferita per tutti.</p>
     */
    public record Movement(
            boolean seeksSurround,
            boolean halvesDistance,
            double base,
            double distanceWeight,
            double travelWeight) { }

    /**
     * Bersaglio prioritario del gruppo.
     *
     * <p>{@code nearestOnly} sceglie il piu' vicino e ignora i pesi seguenti.
     * {@code lowHitPointsWeight} e' diviso per i punti ferita correnti: premia chi
     * puo' essere eliminato subito.</p>
     */
    public record Focus(
            boolean nearestOnly,
            double lowHitPointsWeight,
            double woundedWeight,
            double surroundedWeight,
            double threatWeight,
            double healerBonus,
            double concentrationBonus,
            double distancePenalty) { }

    private static final EnemyCpuProfile EASY = new EnemyCpuProfile(
            new Healing(
                    /* dangerRatio */ 0.25,
                    /* base */ 300_000.0,
                    /* usefulHealingWeight */ 0.0,
                    /* woundedWeight */ 1_000.0,
                    /* defeatedBonus */ 50_000.0,
                    /* healerBonus */ 0.0),
            new Attack(
                    /* base */ 200_000.0,
                    /* expectedDamageWeight */ 0.0,
                    /* killBonusWeight */ 0.0,
                    /* woundedWeight */ 0.0,
                    /* surroundedWeight */ 0.0,
                    /* focusBonus */ 0.0,
                    /* healerBonus */ 0.0,
                    /* nearestFirstWeight */ 1_000.0,
                    /* abilityOrderWeight */ 1.0),
            new Area(
                    /* refusesFriendlyFire */ true,
                    /* requiresPositiveScore */ false,
                    /* base */ 190_000.0,
                    /* multiTargetBonus */ 40_000.0,
                    /* damageWeight */ 1.0,
                    /* hitWeight */ 0.0,
                    /* friendlyDamagePenalty */ 0.0,
                    /* friendlyHitPenalty */ 0.0,
                    /* focusBonus */ 0.0,
                    /* abilityOrderWeight */ 1.0),
            new Movement(
                    /* seeksSurround */ false,
                    /* halvesDistance */ true,
                    /* base */ 100_000.0,
                    /* distanceWeight */ 100.0,
                    /* travelWeight */ 1.0),
            new Focus(
                    /* nearestOnly */ true,
                    /* lowHitPointsWeight */ 0.0,
                    /* woundedWeight */ 0.0,
                    /* surroundedWeight */ 0.0,
                    /* threatWeight */ 0.0,
                    /* healerBonus */ 0.0,
                    /* concentrationBonus */ 0.0,
                    /* distancePenalty */ 0.0),
            /* usesActivations */ false,
            /* activationScore */ 0.0);

    private static final EnemyCpuProfile MEDIUM = new EnemyCpuProfile(
            new Healing(
                    /* dangerRatio */ 0.42,
                    /* base */ 145.0,
                    /* usefulHealingWeight */ 2.0,
                    /* woundedWeight */ 95.0,
                    /* defeatedBonus */ 180.0,
                    /* healerBonus */ 0.0),
            new Attack(
                    /* base */ 120.0,
                    /* expectedDamageWeight */ 3.0,
                    /* killBonusWeight */ 1.0,
                    /* woundedWeight */ 35.0,
                    /* surroundedWeight */ 14.0,
                    /* focusBonus */ 25.0,
                    /* healerBonus */ 0.0,
                    /* nearestFirstWeight */ 0.0,
                    /* abilityOrderWeight */ 0.0),
            new Area(
                    /* refusesFriendlyFire */ false,
                    /* requiresPositiveScore */ true,
                    /* base */ 130.0,
                    /* multiTargetBonus */ 0.0,
                    /* damageWeight */ 2.2,
                    /* hitWeight */ 16.0,
                    /* friendlyDamagePenalty */ 3.5,
                    /* friendlyHitPenalty */ 42.0,
                    /* focusBonus */ 0.0,
                    /* abilityOrderWeight */ 0.0),
            new Movement(
                    /* seeksSurround */ false,
                    /* halvesDistance */ false,
                    /* base */ 110.0,
                    /* distanceWeight */ 1.5,
                    /* travelWeight */ 0.02),
            new Focus(
                    /* nearestOnly */ false,
                    /* lowHitPointsWeight */ 0.0,
                    /* woundedWeight */ 85.0,
                    /* surroundedWeight */ 20.0,
                    /* threatWeight */ 1.2,
                    /* healerBonus */ 0.0,
                    /* concentrationBonus */ 0.0,
                    /* distancePenalty */ 0.08),
            /* usesActivations */ true,
            /* activationScore */ 150.0);

    private static final EnemyCpuProfile SORRY_FOR_YOU = new EnemyCpuProfile(
            new Healing(
                    /* dangerRatio */ 0.68,
                    /* base */ 190.0,
                    /* usefulHealingWeight */ 2.6,
                    /* woundedWeight */ 130.0,
                    /* defeatedBonus */ 260.0,
                    /* healerBonus */ 18.0),
            new Attack(
                    /* base */ 165.0,
                    /* expectedDamageWeight */ 4.2,
                    /* killBonusWeight */ 1.8,
                    /* woundedWeight */ 70.0,
                    /* surroundedWeight */ 0.0,
                    /* focusBonus */ 240.0,
                    /* healerBonus */ 30.0,
                    /* nearestFirstWeight */ 0.0,
                    /* abilityOrderWeight */ 0.0),
            new Area(
                    /* refusesFriendlyFire */ false,
                    /* requiresPositiveScore */ true,
                    /* base */ 175.0,
                    /* multiTargetBonus */ 0.0,
                    /* damageWeight */ 3.0,
                    /* hitWeight */ 24.0,
                    /* friendlyDamagePenalty */ 4.5,
                    /* friendlyHitPenalty */ 58.0,
                    /* focusBonus */ 180.0,
                    /* abilityOrderWeight */ 0.0),
            new Movement(
                    /* seeksSurround */ true,
                    /* halvesDistance */ false,
                    /* base */ 75.0,
                    /* distanceWeight */ 1.5,
                    /* travelWeight */ 0.02),
            new Focus(
                    /* nearestOnly */ false,
                    /* lowHitPointsWeight */ 650.0,
                    /* woundedWeight */ 150.0,
                    /* surroundedWeight */ 0.0,
                    /* threatWeight */ 2.8,
                    /* healerBonus */ 55.0,
                    /* concentrationBonus */ 20.0,
                    /* distancePenalty */ 0.04),
            /* usesActivations */ true,
            /* activationScore */ 245.0);

    /** Pesi del livello richiesto. */
    public static EnemyCpuProfile of(EnemyCpuDifficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> EASY;
            case MEDIUM -> MEDIUM;
            case SORRY_FOR_YOU -> SORRY_FOR_YOU;
        };
    }
}
