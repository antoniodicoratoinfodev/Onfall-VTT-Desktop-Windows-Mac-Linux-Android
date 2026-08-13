package app.d6d.ui.state

import androidx.compose.runtime.snapshotFlow
import app.d6d.domain.combat.CombatResourceState
import app.d6d.engine.ai.EnemyCpu
import app.d6d.engine.ai.EnemyCpuActionReport
import app.d6d.engine.ai.EnemyCpuActionType
import app.d6d.engine.ai.EnemyCpuReason
import app.d6d.engine.ai.EnemyCpuResult
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

    private val resourcesBefore = model.state.combatants().mapValues { (_, combatant) ->
        combatant.snapshot().definitionId() to combatant.resources()
    }
    private val playedGeneration = model.sessionGeneration
    private val runner = EnemyCpu(model.enemyCpuDifficulty).startCurrentGroup(model.session)
    private var shownReports = 0
    private var failure: String? = null
    private var completed = false

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
        if (failure != null || abandoned) return false
        val executed = try {
            runner.advance()
        } catch (failed: RuntimeException) {
            failure = failed.message ?: "La CPU non ha potuto completare il turno."
            false
        }
        publish()
        return executed
    }

    /**
     * Consolida risorse, Undo e riepilogo del turno appena giocato.
     *
     * E' idempotente: puo' arrivare sia dalla riproduzione ritmata sia da chi deve
     * salvare, e chi arriva secondo non deve rifare nulla.
     */
    fun complete() {
        if (completed) return
        completed = true
        try {
            // Un'attesa interrotta non deve lasciare il gruppo a meta' turno.
            while (!abandoned && failure == null && !runner.finished()) advance()
            if (abandoned) return
            val reason = failure
            if (reason != null) {
                suppressTurn(reason)
                return
            }
            consolidate(runner.result())
        } catch (failed: RuntimeException) {
            suppressTurn(failed.message ?: "La CPU non ha potuto completare il turno.")
        } finally {
            model.enemyCpuBusy = false
            model.enemyCpuActingCombatantId = null
            model.enemyCpuPlayback = null
            model.sync()
        }
    }

    private fun consolidate(result: EnemyCpuResult) {
        val resourceChanges = changedEnemyCpuResources(
            before = resourcesBefore,
            after = model.state.combatants().mapValues { (_, combatant) -> combatant.resources() },
        )
        val resourceSyncFailure = model.persistEnemyCpuResourceChanges(resourceChanges)
        if (resourceSyncFailure != null) {
            model.rollbackEnemyCpuCommands(
                startingRevision = result.startingRevision(),
                resourceChanges = resourceChanges,
            )
            // I numeri mostrati durante il turno appartengono a comandi annullati.
            model.floating = emptyMap()
            suppressTurn("Turno CPU annullato: $resourceSyncFailure")
            return
        }
        model.recordEnemyCpuUndoBatch(
            result = result,
            resourceChanges = resourceChanges,
            turnSignature = scheduledSignature,
        )
        if (result.acted()) summarize(result)
        if (result.decisionLimitReached() && model.enemyCpuTurnSignature == scheduledSignature) {
            model.suppressedEnemyCpuTurnSignature = scheduledSignature
            model.completedEnemyCpuTurnSignature = null
            model.message =
                "La CPU ha raggiunto il limite di sicurezza: il turno resta disponibile al tavolo."
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
            text = buildString {
                append("CPU ").append(model.enemyCpuDifficulty.italianLabel)
                focus?.let { append(" · bersaglio: ").append(it) }
                append(" · ").append(attacks).append(" attacchi")
                if (heals > 0) append(" · ").append(heals).append(" cure")
                if (moves > 0) append(" · ").append(moves).append(" movimenti")
                if (result.encounterResolved()) append(" · scontro concluso")
            },
            isHit = result.actions().any {
                (it.type() == EnemyCpuActionType.ATTACK && it.hit()) ||
                    (it.type() == EnemyCpuActionType.AREA_ATTACK && it.amount() > 0)
            },
        )
    }

    private fun suppressTurn(reason: String) {
        model.sync()
        model.suppressedEnemyCpuTurnSignature = model.enemyCpuTurnSignature ?: scheduledSignature
        model.completedEnemyCpuTurnSignature = null
        model.message = reason
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
        return when (action.type()) {
            EnemyCpuActionType.MOVE -> when (action.reason()) {
                EnemyCpuReason.SURROUND_TARGET -> "$actor accerchia il bersaglio"
                else -> "$actor si sposta"
            }
            EnemyCpuActionType.ATTACK -> when {
                target == null -> null
                !action.hit() -> "$actor manca $target"
                action.amount() <= 0 -> "$actor colpisce $target"
                action.reason() == EnemyCpuReason.FOCUS_FIRE ->
                    "$actor si concentra su $target · -${action.amount()}"
                else -> "$actor colpisce $target · -${action.amount()}"
            }
            EnemyCpuActionType.AREA_ATTACK -> "$actor colpisce ${action.targets().size} bersagli"
            EnemyCpuActionType.HEAL -> target?.let {
                val verb = if (action.reason() == EnemyCpuReason.RAISE_ALLY) "rialza" else "cura"
                "$actor $verb $it · +${action.amount()}"
            }
            EnemyCpuActionType.ACTIVATE -> "$actor usa una capacità"
            else -> null
        }
    }
}
