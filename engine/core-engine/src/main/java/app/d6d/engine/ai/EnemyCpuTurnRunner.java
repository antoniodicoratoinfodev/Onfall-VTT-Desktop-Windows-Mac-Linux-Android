package app.d6d.engine.ai;

import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.engine.CombatSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turno del gruppo nemico eseguito un comando alla volta.
 *
 * <p>E' la stessa macchina a stati di {@link EnemyCpu#actCurrentGroup}, ma
 * riprendibile: {@link #advance()} esegue al massimo un comando e restituisce
 * subito il controllo, cosi' l'interfaccia puo' mostrare ogni spostamento e ogni
 * attacco prima del successivo. Guard di revisione, limite di decisioni per
 * attore e chiusura del turno restano quelli del batch atomico, che infatti e'
 * soltanto un ciclo su questa classe.</p>
 *
 * <p>Non e' riutilizzabile: un'istanza vale per un solo gruppo d'iniziativa.</p>
 */
public final class EnemyCpuTurnRunner {

    private final EnemyCpu cpu;
    private final CombatSession session;
    private final long startingRevision;

    /**
     * Ultima revisione prodotta (o inizialmente osservata) dal runner.
     *
     * <p>La revisione corrente della sessione non viene mai adottata implicitamente:
     * se fra due {@link #advance()} il tavolo applica un altro comando, quel comando
     * non appartiene al batch CPU e il runner si arresta.</p>
     */
    private long expectedRevision;
    private CombatState expectedState;

    private final List<EnemyCpuActionReport> reports = new ArrayList<>();
    private final Map<String, EnemyCpu.ActorMemory> memories = new HashMap<>();
    private final Set<String> processedActors = new LinkedHashSet<>();

    private final int initialRound;
    private final int initialTurnIndex;
    private String initialFocus = "";
    private boolean partialMixedGroup;

    private boolean decisionLimitReached;
    private boolean revisionGuardTriggered;

    /** Attore in corso; nullo quando il prossimo va ancora scelto. */
    private String actorId;
    private Set<String> rejected = new HashSet<>();
    private int actorDecisions;

    /** Attore dell'ultimo comando davvero eseguito, per l'evidenza sulla mappa. */
    private String actingCombatantId = "";

    /** La vittoria viene risolta in un advance distinto dal colpo conclusivo. */
    private boolean enemyVictoryPending;
    private String pendingVictoryFocus = "";

    private EnemyCpuResult result;

    EnemyCpuTurnRunner(EnemyCpu cpu, CombatSession session) {
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        this.session = Objects.requireNonNull(session, "session");
        synchronized (session) {
            CombatState initial = session.currentState();
            this.startingRevision = initial.revision();
            this.expectedRevision = initial.revision();
            this.expectedState = initial;
            this.initialRound = initial.round();
            this.initialTurnIndex = initial.turnIndex();
            prepare(initial);
        }
    }

    /** Vero quando il turno e' concluso e {@link #result()} e' disponibile. */
    public synchronized boolean finished() {
        return result != null;
    }

    /** Esito complessivo del gruppo; disponibile soltanto a turno concluso. */
    public synchronized EnemyCpuResult result() {
        if (result == null) throw new IllegalStateException("Il turno CPU non e' ancora concluso.");
        return result;
    }

    /** Report accumulati finora, nell'ordine di esecuzione. */
    public synchronized List<EnemyCpuActionReport> reports() {
        return List.copyOf(reports);
    }

    /** Nemico che ha eseguito l'ultimo comando; stringa vuota prima del primo. */
    public synchronized String actingCombatantId() {
        return actingCombatantId;
    }

    /** Ultima revisione che appartiene sicuramente a questo runner. */
    public synchronized long ownedRevision() {
        return expectedRevision;
    }

    /** Snapshot immutabile corrispondente a {@link #ownedRevision()}. */
    public synchronized CombatState ownedState() {
        return expectedState;
    }

    /**
     * Riavvolge il frammento gia' eseguito soltanto se nessun altro comando e'
     * stato inserito sopra il batch CPU.
     *
     * <p>Check e rollback condividono il lock della sessione, quindi la modalita'
     * Modifica non puo' cancellare per errore una revisione esterna arrivata fra
     * i due. Un rollback riuscito terminalizza il runner: eventuali chiamate ad
     * {@link #advance()} diventano no-op e i report dei comandi annullati vengono
     * scartati.</p>
     */
    public synchronized boolean rollbackOwnedCommandsIfCurrent() {
        synchronized (session) {
            if (session.currentState().revision() != expectedRevision) return false;
            if (expectedRevision != startingRevision) {
                if (!session.undoTo(startingRevision)) return false;
                rememberExpected(session.currentState());
            }
            stopAfterRollback();
            return true;
        }
    }

    /**
     * Esegue al massimo un comando.
     *
     * <p>Restituisce vero quando un comando e' stato applicato e il turno puo'
     * proseguire, falso quando non resta altro da fare: in quel caso il turno e'
     * gia' stato chiuso e l'esito e' leggibile con {@link #result()}.</p>
     */
    public synchronized boolean advance() {
        if (result != null) return false;
        synchronized (session) {
            CombatState current = session.currentState();
            if (current.revision() != expectedRevision) {
                stopForExternalRevision();
                return false;
            }

            if (enemyVictoryPending) {
                resolvePendingEnemyVictory();
                return false;
            }

            while (true) {
                if (actorId == null && !selectNextActor()) {
                    finish();
                    return false;
                }
                switch (stepCurrentActor()) {
                    case EXECUTED -> {
                        return true;
                    }
                    case ACTOR_DONE -> closeCurrentActor();
                    case RETRY -> { }
                }
            }
        }
    }

    /** Esito di una singola iterazione del ciclo di decisione di un attore. */
    private enum StepOutcome {
        /** Un comando e' stato applicato alla sessione. */
        EXECUTED,
        /** La decisione e' stata scartata: si ripianifica lo stesso attore. */
        RETRY,
        /** L'attore ha finito: si passa al successivo del gruppo. */
        ACTOR_DONE,
    }

    private void prepare(CombatState initial) {
        if (initial.status() != CombatStatus.ACTIVE) {
            result = cpu.noOp(startingRevision, EnemyCpuOutcome.ENCOUNTER_NOT_ACTIVE, false);
            return;
        }
        if (initial.partyCombatantIds().isEmpty()) {
            result = cpu.noOp(startingRevision, EnemyCpuOutcome.FACTIONS_NOT_CONFIGURED, false);
            return;
        }
        if (!EnemyCpu.hasStandingParty(initial)) {
            resolveEnemyVictory("");
            return;
        }

        List<String> initialActive = initial.currentCombatantIds();
        if (initialActive.isEmpty()) {
            result = skipLifelessGroup(initial);
            return;
        }
        List<String> initialEnemies = initialActive.stream()
                .filter(id -> !initial.partyCombatantIds().contains(id))
                .toList();
        if (initialEnemies.isEmpty()) {
            result = cpu.noOp(startingRevision, EnemyCpuOutcome.WAITING_FOR_PLAYER, true);
            return;
        }
        partialMixedGroup = initialActive.stream().anyMatch(initial.partyCombatantIds()::contains);
        initialFocus = cpu.selectFocusTarget(initial, initialEnemies);
    }

    /** Un gruppo nemico senza attori vivi va comunque consegnato al motore, che lo salta. */
    private EnemyCpuResult skipLifelessGroup(CombatState initial) {
        List<String> structuralGroup = initial.turnGroups().get(initial.turnIndex());
        List<String> structuralEnemies = structuralGroup.stream()
                .filter(id -> !initial.partyCombatantIds().contains(id))
                .toList();
        if (structuralEnemies.isEmpty()) {
            return cpu.noOp(startingRevision, EnemyCpuOutcome.NO_ACTOR_AVAILABLE, false);
        }
        session.endTurn();
        CombatState ending = session.currentState();
        rememberExpected(ending);
        reports.add(new EnemyCpuActionReport(
                EnemyCpuActionType.TURN_ENDED,
                String.join(",", structuralEnemies),
                "",
                "",
                0,
                EnemyCpuReason.LIFELESS_GROUP_SKIPPED));
        return new EnemyCpuResult(
                cpu.difficulty(),
                List.copyOf(reports),
                "",
                ending.round() != initialRound || ending.turnIndex() != initialTurnIndex,
                ending.status() == CombatStatus.RESOLVED,
                false,
                false,
                false,
                EnemyCpuOutcome.LIFELESS_GROUP,
                startingRevision,
                ending.revision());
    }

    private boolean selectNextActor() {
        CombatState groupState = session.currentState();
        if (groupState.status() != CombatStatus.ACTIVE
                || groupState.round() != initialRound
                || groupState.turnIndex() != initialTurnIndex) {
            return false;
        }
        String next = groupState.currentCombatantIds().stream()
                .filter(id -> !groupState.partyCombatantIds().contains(id))
                // Chi non si e' ancora accorto del gruppo non e' un attore: il suo
                // turno passa senza consumare decisioni e senza chiudere il gruppo
                // prima del tempo, esattamente come se non ci fosse.
                .filter(id -> !groupState.dormant(id))
                .filter(id -> !processedActors.contains(id))
                .findFirst()
                .orElse(null);
        if (next == null) return false;
        processedActors.add(next);
        actorId = next;
        rejected = new HashSet<>();
        actorDecisions = 0;
        return true;
    }

    private StepOutcome stepCurrentActor() {
        if (actorDecisions >= EnemyCpu.MAX_DECISIONS_PER_ACTOR) return StepOutcome.ACTOR_DONE;

        CombatState before = session.currentState();
        if (before.status() != CombatStatus.ACTIVE
                || before.round() != initialRound
                || before.turnIndex() != initialTurnIndex
                || !before.currentCombatantIds().contains(actorId)
                || before.partyCombatantIds().contains(actorId)) {
            return StepOutcome.ACTOR_DONE;
        }

        List<String> activeEnemies = before.currentCombatantIds().stream()
                .filter(id -> !before.partyCombatantIds().contains(id))
                .toList();
        String focus;
        EnemyCpuDecision decision;
        try {
            focus = cpu.selectFocusTarget(before, activeEnemies);
            decision = cpu.chooseDecision(before, actorId, focus, memory(), rejected);
        } catch (RuntimeException failure) {
            revisionGuardTriggered = true;
            reports.add(new EnemyCpuActionReport(
                    EnemyCpuActionType.SKIPPED,
                    actorId,
                    "",
                    "",
                    EnemyCpuReason.PLANNING_FAILED,
                    EnemyCpu.messageOf(failure)));
            return StepOutcome.ACTOR_DONE;
        }
        if (decision instanceof EnemyCpuDecision.Done) return StepOutcome.ACTOR_DONE;

        actorDecisions++;
        String signature = EnemyCpu.decisionSignature(decision);
        long revisionBefore = before.revision();
        try {
            reports.addAll(cpu.execute(session, decision));
            memory().remember(decision);
        } catch (RuntimeException failure) {
            rejected.add(signature);
            reports.add(new EnemyCpuActionReport(
                    EnemyCpuActionType.SKIPPED,
                    actorId,
                    EnemyCpu.decisionTarget(decision),
                    EnemyCpu.decisionAbility(decision),
                    EnemyCpuReason.COMMAND_REJECTED,
                    EnemyCpu.messageOf(failure)));
            // Una sola ripianificazione su stato immutato e' utile; una seconda
            // mancata progressione termina l'attore.
            if (rejected.size() >= 2) {
                revisionGuardTriggered = true;
                return StepOutcome.ACTOR_DONE;
            }
            return StepOutcome.RETRY;
        }

        CombatState after = session.currentState();
        if (after.revision() <= revisionBefore) {
            revisionGuardTriggered = true;
            reports.add(new EnemyCpuActionReport(
                    EnemyCpuActionType.SKIPPED,
                    actorId,
                    EnemyCpu.decisionTarget(decision),
                    EnemyCpu.decisionAbility(decision),
                    0,
                    EnemyCpuReason.NO_PROGRESS));
            return StepOutcome.ACTOR_DONE;
        }
        rememberExpected(after);
        actingCombatantId = actorId;
        if (!EnemyCpu.hasStandingParty(after)) {
            enemyVictoryPending = true;
            pendingVictoryFocus = focus.isBlank() ? initialFocus : focus;
        }
        return StepOutcome.EXECUTED;
    }

    /**
     * Raggiungere esattamente il numero massimo non significa necessariamente
     * averlo esaurito: una decisione pura aggiuntiva distingue un attore che ha
     * davvero altro da fare da uno che ha appena concluso legalmente.
     */
    private void closeCurrentActor() {
        CombatState afterActor = session.currentState();
        if (actorDecisions >= EnemyCpu.MAX_DECISIONS_PER_ACTOR
                && afterActor.status() == CombatStatus.ACTIVE
                && afterActor.round() == initialRound
                && afterActor.turnIndex() == initialTurnIndex
                && afterActor.currentCombatantIds().contains(actorId)) {
            List<String> activeEnemies = afterActor.currentCombatantIds().stream()
                    .filter(id -> !afterActor.partyCombatantIds().contains(id))
                    .toList();
            EnemyCpuDecision pending;
            try {
                pending = cpu.chooseDecision(
                        afterActor,
                        actorId,
                        cpu.selectFocusTarget(afterActor, activeEnemies),
                        memory(),
                        rejected);
            } catch (RuntimeException failure) {
                revisionGuardTriggered = true;
                reports.add(new EnemyCpuActionReport(
                        EnemyCpuActionType.SKIPPED,
                        actorId,
                        "",
                        "",
                        EnemyCpuReason.PLANNING_FAILED,
                        EnemyCpu.messageOf(failure)));
                pending = new EnemyCpuDecision.Done(actorId, EnemyCpuReason.PLANNING_FAILED);
            }
            if (!(pending instanceof EnemyCpuDecision.Done)) {
                decisionLimitReached = true;
                reports.add(new EnemyCpuActionReport(
                        EnemyCpuActionType.SKIPPED,
                        actorId,
                        EnemyCpu.decisionTarget(pending),
                        EnemyCpu.decisionAbility(pending),
                        0,
                        EnemyCpuReason.DECISION_LIMIT_REACHED));
            }
        }
        actorId = null;
    }

    private void finish() {
        CombatState beforeEnd = session.currentState();
        boolean playerStillActiveInGroup = beforeEnd.status() == CombatStatus.ACTIVE
                && beforeEnd.round() == initialRound
                && beforeEnd.turnIndex() == initialTurnIndex
                && beforeEnd.currentCombatantIds().stream()
                        .anyMatch(beforeEnd.partyCombatantIds()::contains);
        if ((!partialMixedGroup || !playerStillActiveInGroup)
                && beforeEnd.status() == CombatStatus.ACTIVE
                && beforeEnd.round() == initialRound
                && beforeEnd.turnIndex() == initialTurnIndex
                && !beforeEnd.currentCombatantIds().isEmpty()
                && beforeEnd.currentCombatantIds().stream()
                        .noneMatch(beforeEnd.partyCombatantIds()::contains)) {
            session.endTurn();
            rememberExpected(session.currentState());
            reports.add(new EnemyCpuActionReport(
                    EnemyCpuActionType.TURN_ENDED,
                    String.join(",", processedActors),
                    "",
                    "",
                    0,
                    decisionLimitReached
                            ? EnemyCpuReason.GROUP_TURN_LIMITED
                            : EnemyCpuReason.GROUP_TURN_COMPLETED));
        }

        CombatState ending = session.currentState();
        boolean turnAdvanced = ending.round() != initialRound || ending.turnIndex() != initialTurnIndex;
        boolean waitingForPlayer = partialMixedGroup
                && !turnAdvanced
                && ending.status() == CombatStatus.ACTIVE;
        EnemyCpuOutcome outcome = decisionLimitReached
                ? EnemyCpuOutcome.DECISION_LIMIT_REACHED
                : waitingForPlayer
                        ? EnemyCpuOutcome.WAITING_FOR_PLAYER
                : revisionGuardTriggered
                        ? EnemyCpuOutcome.REVISION_GUARD_TRIGGERED
                        : EnemyCpuOutcome.COMPLETED;
        result = new EnemyCpuResult(
                cpu.difficulty(),
                List.copyOf(reports),
                initialFocus,
                turnAdvanced,
                ending.status() == CombatStatus.RESOLVED,
                waitingForPlayer,
                partialMixedGroup,
                decisionLimitReached,
                outcome,
                startingRevision,
                ending.revision());
    }

    /**
     * Conclude immediatamente il runner quando la sessione non e' piu' alla
     * revisione lasciata dalla CPU. Non chiude il turno e non include nel proprio
     * risultato lo stato o la revisione del comando esterno.
     */
    private void stopForExternalRevision() {
        revisionGuardTriggered = true;
        enemyVictoryPending = false;
        boolean turnAdvanced = expectedState.round() != initialRound
                || expectedState.turnIndex() != initialTurnIndex;
        boolean waitingForPlayer = partialMixedGroup
                && !turnAdvanced
                && expectedState.status() == CombatStatus.ACTIVE;
        result = new EnemyCpuResult(
                cpu.difficulty(),
                List.copyOf(reports),
                initialFocus,
                turnAdvanced,
                expectedState.status() == CombatStatus.RESOLVED,
                waitingForPlayer,
                partialMixedGroup,
                decisionLimitReached,
                EnemyCpuOutcome.REVISION_GUARD_TRIGGERED,
                startingRevision,
                expectedRevision);
    }

    /** Dimentica ogni effetto ormai annullato e rende il runner definitivamente inerte. */
    private void stopAfterRollback() {
        reports.clear();
        actingCombatantId = "";
        actorId = null;
        enemyVictoryPending = false;
        pendingVictoryFocus = "";
        result = new EnemyCpuResult(
                cpu.difficulty(),
                List.of(),
                initialFocus,
                false,
                expectedState.status() == CombatStatus.RESOLVED,
                false,
                partialMixedGroup,
                false,
                EnemyCpuOutcome.ROLLED_BACK,
                startingRevision,
                expectedRevision);
    }

    /** Il comando di risoluzione e' deliberatamente separato dall'attacco finale. */
    private void resolvePendingEnemyVictory() {
        resolveEnemyVictory(pendingVictoryFocus);
        enemyVictoryPending = false;
    }

    /** Risolve la vittoria e mantiene la vista progressiva uguale all'esito finale. */
    private void resolveEnemyVictory(String focusTargetId) {
        EnemyCpuResult victory = cpu.resolveEnemyVictory(
                session,
                startingRevision,
                initialRound,
                initialTurnIndex,
                reports,
                focusTargetId);
        CombatState ending = session.currentState();
        rememberExpected(ending);
        reports.clear();
        reports.addAll(victory.actions());
        result = victory;
    }

    private void rememberExpected(CombatState state) {
        expectedState = state;
        expectedRevision = state.revision();
    }

    private EnemyCpu.ActorMemory memory() {
        return memories.computeIfAbsent(actorId, ignored -> new EnemyCpu.ActorMemory());
    }
}
