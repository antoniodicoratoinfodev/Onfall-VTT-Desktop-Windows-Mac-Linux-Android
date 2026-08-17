package app.d6d.ui.state

import androidx.compose.runtime.snapshotFlow
import app.d6d.domain.combat.CombatResourceState
import app.d6d.engine.ai.EnemyCpu
import app.d6d.engine.ai.EnemyCpuActionReport
import app.d6d.engine.ai.EnemyCpuActionType
import app.d6d.engine.ai.EnemyCpuOutcome
import app.d6d.engine.ai.EnemyCpuReason
import app.d6d.engine.ai.EnemyCpuResult
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.LocalizedText
import app.d6d.ui.i18n.literalText
import kotlinx.coroutines.flow.first

/**
 * Delta autorevole delle risorse di un combattente prodotto da un turno CPU.
 * Conserva le liste complete perche' persistenza e rollback non debbano dedurre
 * quale pool sia stato scelto dal report dell'azione.
 */
internal data class EnemyCpuResourceDelta(
    val definitionId: String,
    val before: List<CombatResourceState>,
    val after: List<CombatResourceState>,
)

/**
 * Rileva i pool realmente mutati, indipendentemente dall'ordine delle liste.
 *
 * Più istanze della stessa definizione (le quantità del wizard) hanno risorse
 * indipendenti nella sessione, ma una sola scheda esterna: non esiste un valore
 * corretto da scrivere su quella scheda. I loro consumi restano quindi locali
 * al salvataggio dell'incontro e non vengono inviati al sink condiviso.
 */
internal fun changedEnemyCpuResources(
    before: Map<String, Pair<String, List<CombatResourceState>>>,
    after: Map<String, List<CombatResourceState>>,
): Map<String, EnemyCpuResourceDelta> {
    val clonedDefinitionIds = before.values
        .groupingBy { snapshot -> snapshot.first }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
    return buildMap {
        before.forEach { (combatantId, snapshot) ->
            if (snapshot.first in clonedDefinitionIds) return@forEach
            val current = after[combatantId] ?: return@forEach
            val previousById = snapshot.second.associateBy { it.id() }
            val currentById = current.associateBy { it.id() }
            if (previousById != currentById) {
                put(
                    combatantId,
                    EnemyCpuResourceDelta(
                        definitionId = snapshot.first,
                        before = snapshot.second,
                        after = current,
                    ),
                )
            }
        }
    }
}

/**
 * Sospende finche' un eventuale turno CPU in corso non e' concluso.
 *
 * La usa chi scrive su disco in sottofondo: un salvataggio che cade fra un
 * comando e il successivo fotograferebbe mezzo turno, e alla riapertura la CPU
 * ripianificherebbe da capo la parte mancante. Aspettare costa qualche decimo di
 * secondo e non toglie nulla al ritmo che il tavolo sta guardando.
 */
internal suspend fun BattleViewModel.awaitEnemyCpuTurnEnd() {
    if (!enemyCpuBusy) return
    snapshotFlow { enemyCpuBusy }.first { busy -> !busy }
}

/**
 * Turno nemico in corso, giocato un comando alla volta.
 *
 * Per il motore sono piu' comandi; per il tavolo resta una sola operazione:
 * risorse della scheda e checkpoint di Undo vengono consolidati soltanto alla
 * fine, come quando il batch era atomico.
 *
 * Vive accanto a [BattleViewModel] e non al suo interno perche' l'esecuzione di
 * un turno CPU e' una procedura lunga con uno stato proprio (quanto e' stato
 * mostrato, quale attore sta agendo, se il turno e' stato abbandonato); il
 * modello resta il proprietario dello stato di presentazione che essa aggiorna.
 */
internal class EnemyCpuTurnPlayback(
    private val model: BattleViewModel,
    private val scheduledSignature: String,
) {

    private val playedGeneration = model.sessionGeneration
    private val runner = EnemyCpu(model.enemyCpuDifficulty).startCurrentGroup(model.session)
    private val resourcesBefore = runner.ownedState().combatants().mapValues { (_, combatant) ->
        combatant.snapshot().definitionId() to combatant.resources()
    }
    private var shownReports = 0
    private var failure: LocalizedText? = null
    private var completed = false
    private var completing = false
    private var persistedResourceChanges = emptyMap<String, EnemyCpuResourceDelta>()

    /**
     * Vero quando nel frattempo il tavolo ha caricato un'altra partita.
     *
     * Il turno appartiene a una sessione che il modello non ospita piu': i suoi
     * comandi, il suo checkpoint di Undo e le sue risorse non devono finire su
     * quella nuova. Con il batch atomico non poteva succedere; con le pause fra
     * un comando e l'altro, invece, la finestra esiste.
     */
    private val abandoned: Boolean get() = model.sessionGeneration != playedGeneration

    init {
        model.enemyCpuBusy = true
        model.enemyCpuActionLabel = null
        // Un gruppo nemico senza attori vivi viene gia' saltato all'avvio.
        publish()
    }

    /** Esegue il comando successivo; falso quando non resta altro da mostrare. */
    fun advance(): Boolean {
        if (completed || completing || failure != null || abandoned) return false
        return advanceOne()
    }

    private fun advanceOne(): Boolean {
        if (failure != null || abandoned) return false
        return try {
            val executed = runner.advance()
            publish()
            executed
        } catch (failed: RuntimeException) {
            failure = failed.message?.let(::literalText)
                ?: LocalizedText { it.battle.cpuCouldNotFinishTurn }
            false
        }
    }

    /**
     * Consolida risorse, Undo e riepilogo del turno appena giocato.
     *
     * E' idempotente: puo' arrivare sia dalla riproduzione ritmata sia da chi deve
     * salvare, e chi arriva secondo non deve rifare nulla.
     */
    fun complete() {
        if (completed || completing) return
        completing = true
        try {
            // Un'attesa interrotta non deve lasciare il gruppo a meta' turno.
            while (!abandoned && failure == null && !runner.finished()) advanceOne()
            if (abandoned) return
            val reason = failure
            if (reason != null) {
                rollbackOrPreserve(reason)
                return
            }
            consolidate(runner.result())
        } catch (failed: RuntimeException) {
            rollbackOrPreserve(
                failed.message?.let(::literalText)
                    ?: LocalizedText { it.battle.cpuCouldNotFinishTurn },
            )
        } finally {
            completing = false
            completed = true
            releaseIfOwned()
        }
    }

    /**
     * Entrare in Modifica non puo' lasciare sul tavolo mezzo piano CPU.
     *
     * Si riavvolgono i comandi gia' mostrati e si abbandona questo runner. Finche'
     * Modifica resta attiva lo scheduler e' fermo; quando il tavolo ne esce la CPU
     * ripianifica dallo stato eventualmente corretto dall'utente.
     */
    fun pauseForEdit() {
        if (completed || completing || abandoned) return
        completed = true
        val resourceChanges = ownedResourceChanges()
        if (runner.rollbackOwnedCommandsIfCurrent()) {
            model.floating = emptyMap()
            model.actionResolution = null
            model.enemyCpuActionLabel = null
            model.sync()
        } else {
            preserveOwnedCommandsAndSuppress(
                LocalizedText { it.battle.cpuSuspendedTableChangedBeforeEdit },
                resourceChanges,
            )
        }
        releaseIfOwned()
    }

    private fun consolidate(result: EnemyCpuResult) {
        if (
            result.outcome() == EnemyCpuOutcome.REVISION_GUARD_TRIGGERED &&
            externalRevisionPresent()
        ) {
            // Il runner dichiara come propria soltanto endingRevision: quella
            // corrente appartiene invece al comando esterno che ha fatto scattare
            // la guardia. Non va attribuita alle risorse CPU, inglobata nell'Undo
            // del batch o rimossa tornando a startingRevision.
            preserveOwnedCommandsAndSuppress(
                LocalizedText { it.battle.cpuSuspendedTableChangedDuringPlayback },
                ownedResourceChanges(),
            )
            return
        }
        val resourceChanges = ownedResourceChanges()
        // Se una fase successiva fallisse dopo aver scritto nel Compendio, il
        // percorso di errore deve sapere quali valori esterni ripristinare.
        persistedResourceChanges = resourceChanges
        val resourceSyncFailure = model.persistEnemyCpuResourceChanges(resourceChanges)
        if (resourceSyncFailure != null) {
            rollbackOrPreserve(literalText("$resourceSyncFailure"))
            return
        }

        // Il sink puo' essere lento e dare a un mutatore esterno il tempo di
        // inserire una revisione: ricontrollare la sessione viva evita che quella
        // revisione finisca nel checkpoint CPU.
        if (externalRevisionPresent()) {
            persistedResourceChanges = emptyMap()
            suppressTurn(LocalizedText { it.battle.cpuSuspendedTableChangedDuringCommit })
            return
        }
        if (result.acted()) summarize(result)
        if (result.decisionLimitReached() && model.enemyCpuTurnSignature == scheduledSignature) {
            model.suppressedEnemyCpuTurnSignature = scheduledSignature
            model.completedEnemyCpuTurnSignature = null
            model.showMessage(LocalizedText { it.battle.cpuSafetyLimitReached })
        } else if (
            result.partialMixedGroup() && result.waitingForPlayer() &&
            model.enemyCpuTurnSignature == scheduledSignature
        ) {
            // Nei gruppi misti il motore lascia intenzionalmente aperto il turno
            // al party. La firma evita che ogni ricomposizione faccia agire di
            // nuovo gli stessi nemici.
            model.completedEnemyCpuTurnSignature = scheduledSignature
            model.activeCombatantIds.firstOrNull(model::isParty)?.let { partyActor ->
                model.followEnemyCpuActor(partyActor, keepTarget = true)
            }
        }
        // Da qui in poi non restano operazioni fallibili: se una delle fasi sopra
        // lancia, rollbackOrPreserve non puo' lasciare un UndoEffect gia' inserito.
        persistedResourceChanges = emptyMap()
        if (externalRevisionPresent()) {
            suppressTurn(LocalizedText { it.battle.cpuSuspendedTableChangedDuringCommit })
            return
        }
        model.recordEnemyCpuUndoBatch(
            result = result,
            resourceChanges = resourceChanges,
            turnSignature = scheduledSignature,
        )
    }

    private fun summarize(result: EnemyCpuResult) {
        val attacks = result.actions().count {
            it.type() == EnemyCpuActionType.ATTACK || it.type() == EnemyCpuActionType.AREA_ATTACK
        }
        val heals = result.actions().count { it.type() == EnemyCpuActionType.HEAL }
        val moves = result.actions().count { it.type() == EnemyCpuActionType.MOVE }
        val focus = result.focusTargetId()
            .takeIf(String::isNotBlank)
            ?.let(model::name)
        model.actionResolution = ActionResolution(
            text = AppLocale.current.battle.cpuSummary(
                difficulty = model.enemyCpuDifficulty.label(AppLocale.current),
                focus = focus,
                attacks = attacks,
                heals = heals,
                moves = moves,
                encounterResolved = result.encounterResolved(),
            ),
            isHit = result.actions().any {
                (it.type() == EnemyCpuActionType.ATTACK && it.hit()) ||
                    (it.type() == EnemyCpuActionType.AREA_ATTACK && it.amount() > 0)
            },
        )
    }

    private fun suppressTurn(reason: LocalizedText) {
        model.sync()
        model.suppressedEnemyCpuTurnSignature = model.enemyCpuTurnSignature ?: scheduledSignature
        model.completedEnemyCpuTurnSignature = null
        model.showMessage(reason)
    }

    /** Delta prodotto soltanto dalle revisioni che il runner dichiara proprie. */
    private fun ownedResourceChanges(): Map<String, EnemyCpuResourceDelta> =
        changedEnemyCpuResources(
            before = resourcesBefore,
            after = runner.ownedState().combatants().mapValues { (_, combatant) ->
                combatant.resources()
            },
        )

    /** La sorgente autorevole e' la sessione, non la cache Compose del modello. */
    private fun externalRevisionPresent(): Boolean =
        model.session.currentState().revision() != runner.ownedRevision()

    /**
     * Annulla il frammento CPU soltanto se e' ancora in cima alla sessione.
     * Se una revisione esterna e' gia' arrivata, conserva entrambi i contributi e
     * sincronizza esclusivamente le risorse osservate nell'ultima state owned.
     */
    private fun rollbackOrPreserve(reason: LocalizedText) {
        val ownedChanges = ownedResourceChanges()
        if (runner.rollbackOwnedCommandsIfCurrent()) {
            val restoreFailure = model.restoreEnemyCpuResourceChanges(persistedResourceChanges)
            persistedResourceChanges = emptyMap()
            model.floating = emptyMap()
            model.actionResolution = null
            suppressTurn(
                LocalizedText { strings ->
                    val suffix = restoreFailure
                        ?.let { strings.battle.cpuResourcesNotRealigned(it) }
                        .orEmpty()
                    strings.battle.cpuTurnUndone(reason.resolve(strings), suffix)
                },
            )
            return
        }

        persistedResourceChanges = emptyMap()
        preserveOwnedCommandsAndSuppress(
            LocalizedText { strings ->
                strings.battle.cpuSuspendedKeepingChanges(reason.resolve(strings))
            },
            ownedChanges,
        )
    }

    private fun preserveOwnedCommandsAndSuppress(
        reason: LocalizedText,
        resourceChanges: Map<String, EnemyCpuResourceDelta>,
    ) {
        val syncFailure = model.persistEnemyCpuResourceChanges(resourceChanges)
        persistedResourceChanges = emptyMap()
        suppressTurn(
            LocalizedText { strings ->
                val suffix = syncFailure
                    ?.let { strings.battle.cpuResourcesOutOfSync(it) }
                    .orEmpty()
                reason.resolve(strings) + suffix
            },
        )
    }

    /**
     * Un playback vecchio puo' terminare dopo che [BattleViewModel.adopt] ha gia'
     * avviato quello della nuova sessione. Soltanto il proprietario corrente puo'
     * quindi liberare busy, evidenza e riferimento condiviso.
     */
    private fun releaseIfOwned() {
        if (model.enemyCpuPlayback !== this) return
        model.enemyCpuPlayback = null
        model.enemyCpuBusy = false
        model.enemyCpuActingCombatantId = null
        model.sync()
    }

    /** Allinea stato, evidenza e feedback a quanto il motore ha appena eseguito. */
    private fun publish() {
        model.sync()
        val reports = runner.reports()
        while (shownReports < reports.size) {
            val report = reports[shownReports++]
            model.pushEnemyCpuActionFeedback(report)
            summaryOf(report)?.let { model.enemyCpuActionLabel = it }
        }
        val acting = runner.actingCombatantId().takeIf { it.isNotBlank() } ?: return
        model.enemyCpuActingCombatantId = acting
        if (acting in model.activeCombatantIds) {
            // Con piu' nemici in parita' d'iniziativa la scheda e l'alone devono
            // seguire chi sta davvero agendo, non il primo del gruppo.
            model.followEnemyCpuActor(acting, keepTarget = false)
        }
    }

    /**
     * Riga breve che descrive il comando appena eseguito.
     *
     * Serve a rendere leggibile il turno mentre scorre: i numeri fluttuanti dicono
     * quanto, non chi né perché. Il motore dichiara la motivazione in vocabolario;
     * le parole sono di questo strato. I report che non sono un comando di gioco
     * (turno chiuso, decisione scartata) non hanno nulla da annunciare.
     */
    private fun summaryOf(action: EnemyCpuActionReport): String? {
        val actor = action.actorId().takeIf { it.isNotBlank() }?.let(model::name) ?: return null
        val target = action.targetId().takeIf { it.isNotBlank() }?.let(model::name)
        val words = AppLocale.current.battle
        return when (action.type()) {
            EnemyCpuActionType.MOVE -> when (action.reason()) {
                EnemyCpuReason.SURROUND_TARGET -> words.cpuFlanks(actor)
                else -> words.cpuMoves(actor)
            }
            EnemyCpuActionType.ATTACK -> when {
                target == null -> null
                !action.hit() -> words.cpuMisses(actor, target)
                action.amount() <= 0 -> words.cpuHits(actor, target)
                action.reason() == EnemyCpuReason.FOCUS_FIRE ->
                    words.cpuConcentratesOn(actor, target, action.amount())
                else -> words.cpuHitsForDamage(actor, target, action.amount())
            }
            EnemyCpuActionType.AREA_ATTACK -> words.cpuHitsSeveral(actor, action.targets().size)
            EnemyCpuActionType.HEAL -> target?.let {
                words.cpuHeals(
                    actor = actor,
                    target = it,
                    amount = action.amount(),
                    raising = action.reason() == EnemyCpuReason.RAISE_ALLY,
                )
            }
            EnemyCpuActionType.ACTIVATE -> words.cpuUsesAbility(actor)
            else -> null
        }
    }
}
