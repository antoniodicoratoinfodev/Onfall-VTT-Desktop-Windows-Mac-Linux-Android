package app.d6d.ui.state

import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.EventType
import app.d6d.engine.CombatSession
import app.d6d.ui.content.SampleEncounter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifica lo strato di presentazione, non le regole: quelle hanno gia' i propri
 * test nel motore. Qui interessa che la UI non aggiri il motore e non trasformi
 * una violazione delle regole in un crash.
 */
class BattleViewModelTest {

    private fun viewModel() = BattleViewModel(SampleEncounter.startedSession(seed = 4242L))

    @Test
    fun `l'incontro dimostrativo parte attivo e con gli schieramenti distinti`() {
        val model = viewModel()

        assertEquals(CombatStatus.ACTIVE, model.status)
        assertEquals(1, model.round)
        assertEquals(4, model.partyIds.size)
        assertEquals(4, model.enemyIds.size)
        assertNotNull(model.activeCombatantId)
        // Nessun combattente puo' stare in entrambi gli schieramenti.
        assertTrue(model.partyIds.none { it in model.enemyIds })
    }

    @Test
    fun `il bersaglio predefinito appartiene sempre allo schieramento opposto`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!

        assertTrue(model.isParty(active) != model.isParty(target))
    }

    @Test
    fun `un alleato o lo stesso attore non restano selezionati come bersaglio ostile`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ally = model.partyIds.first { it != active }.takeIf { model.isParty(active) }
            ?: model.enemyIds.first { it != active }

        model.selectedTargetId = active
        assertNull(model.selectedTargetId)
        model.selectedTargetId = ally
        assertNull(model.selectedTargetId)
        assertTrue(model.isParty(active) != model.isParty(model.effectiveTargetId()!!))
    }

    @Test
    fun `cliccare un combattente lo ispeziona senza cambiare turno o bersaglio`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val inspected = (model.partyIds + model.enemyIds).first { it != active }

        model.onCombatantClicked(inspected)

        assertEquals(inspected, model.inspectedCombatantId)
        assertEquals(active, model.activeCombatantId)
        assertNull(model.selectedTargetId)
        assertFalse(model.canUseAbilitiesOf(inspected))
    }

    @Test
    fun `scegliere una capacita singola aspetta il clic sul bersaglio`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ability = model.abilities(active).first { !it.isArea }
        val revisionBefore = model.state.revision()
        val eventsBefore = model.events.size

        model.beginAbilityTargeting(ability.id())

        assertEquals(ability.id(), model.singleTargeting?.abilityId)
        assertEquals(active, model.singleTargeting?.attackerId)
        assertEquals(revisionBefore, model.state.revision())
        assertEquals(eventsBefore, model.events.size)
        assertNull(model.selectedTargetId)
    }

    @Test
    fun `un bersaglio invalido non usa il nemico predefinito e lascia attiva la mira`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ally = (model.partyIds.takeIf { model.isParty(active) } ?: model.enemyIds)
            .first { it != active }
        val ability = model.abilities(active).first { !it.isArea }
        val revisionBefore = model.state.revision()

        model.beginAbilityTargeting(ability.id())
        model.onCombatantClicked(ally)

        assertNotNull(model.singleTargeting)
        assertEquals(revisionBefore, model.state.revision())
        assertNull(model.selectedTargetId)
        assertNotNull(model.message)
    }

    @Test
    fun `un bersaglio valido risolve esattamente la capacita selezionata`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!
        val ability = model.abilities(active).first { !it.isArea }
        val eventsBefore = model.events.size

        model.beginAbilityTargeting(ability.id())
        model.onCombatantClicked(target)

        assertNull(model.singleTargeting)
        assertTrue(model.events.size > eventsBefore)
        assertEquals(active, model.inspectedCombatantId)
    }

    @Test
    fun `le capacita del combattente ispezionato fuori turno restano solo consultabili`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val inspected = (model.partyIds + model.enemyIds).first { it != active }
        val ability = model.abilities(inspected).first()

        model.inspectCombatant(inspected)
        model.beginAbilityTargeting(ability.id())

        assertNull(model.singleTargeting)
        assertEquals(active, model.activeCombatantId)
        assertFalse(model.canUseAbilitiesOf(inspected))
        assertNotNull(model.message)
    }

    @Test
    fun `un combattente a zero PF resta ispezionabile ma non puo usare capacita`() {
        val model = viewModel()
        val target = model.effectiveTargetId()!!

        model.applyManualDamage(target, 999)
        model.inspectCombatant(target)

        assertTrue(model.combatant(target)!!.defeated())
        assertEquals(target, model.inspectedCombatantId)
        assertTrue(model.abilities(target).isNotEmpty())
        assertFalse(model.canUseAbilitiesOf(target))
    }

    @Test
    fun `il cambio turno annulla mira e ispezione esplicita`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ability = model.abilities(active).first { !it.isArea }

        model.beginAbilityTargeting(ability.id())
        model.endTurn()

        assertNull(model.singleTargeting)
        assertNotEquals(active, model.activeCombatantId)
        assertEquals(model.activeCombatantId, model.inspectedCombatantId)
    }

    @Test
    fun `il bersaglio esplicito viene azzerato al cambio turno`() {
        val model = viewModel()
        model.selectedTargetId = model.effectiveTargetId()
        assertNotNull(model.selectedTargetId)

        model.endTurn()

        assertNull(model.selectedTargetId)
    }

    @Test
    fun `un attacco produce un esito e lascia il registro coerente`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!
        val hitPointsBefore = model.combatant(target)!!.currentHitPoints()
        val eventsBefore = model.events.size

        model.attack(model.abilities(active).first().id())

        val hitPointsAfter = model.combatant(target)!!.currentHitPoints()
        val missed = model.floating[target].orEmpty().any { it.text == "Mancato" }

        // O il bersaglio ha perso PF, oppure l'attacco e' stato dichiarato mancato:
        // il motore non puo' restituire un esito silenzioso.
        assertTrue(hitPointsAfter < hitPointsBefore || missed)
        assertTrue(model.events.size > eventsBefore)
        assertNull(model.message)
    }

    @Test
    fun `una capacita' inesistente diventa un messaggio invece di un'eccezione`() {
        val model = viewModel()

        model.attack("capacita-che-non-esiste")

        assertNotNull(model.message)
        assertEquals(CombatStatus.ACTIVE, model.status)
    }

    @Test
    fun `annullare un attacco ripristina i punti ferita`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!
        val hitPointsBefore = model.combatant(target)!!.currentHitPoints()

        model.attack(model.abilities(active).first().id())
        model.undo()

        assertEquals(hitPointsBefore, model.combatant(target)!!.currentHitPoints())
    }

    @Test
    fun `terminare il turno passa al combattente successivo`() {
        val model = viewModel()
        val first = model.activeCombatantId

        model.endTurn()

        assertNotEquals(first, model.activeCombatantId)
    }

    @Test
    fun `una condizione applicata compare sul bersaglio con la durata indicata`() {
        val model = viewModel()
        val target = model.effectiveTargetId()!!

        model.addCondition(target, ConditionType.POISONED, rounds = 2)

        val conditions = model.combatant(target)!!.conditions()
        assertEquals(1, conditions.size)
        assertEquals(ConditionType.POISONED, conditions.first().type())
        assertEquals(2, conditions.first().duration().remainingOccurrences())
    }

    @Test
    fun `i numeri fluttuanti vengono rimossi quando scadono`() {
        val model = viewModel()
        val target = model.effectiveTargetId()!!

        model.addCondition(target, ConditionType.PRONE, rounds = 0)
        val produced = model.floating[target].orEmpty()
        assertTrue(produced.isNotEmpty())

        produced.forEach { model.expire(target, it.id) }
        assertTrue(model.floating[target].isNullOrEmpty())
    }

    @Test
    fun `l'ingombro del segnaposto viene dalla taglia fornita dal compendio`() {
        // Il provider simula il Compendio: questo attore e' Grande (2 caselle).
        val largeId = SampleEncounter.enemies().first().id()
        val model = BattleViewModel(
            SampleEncounter.startedSession(seed = 4242L),
            footprintProvider = { id -> if (id == largeId) 2 else 1 },
        )

        assertEquals(2, model.squaresPerSideFor(largeId))
        assertEquals(1, model.squaresPerSideFor(model.partyIds.first()))
    }

    @Test
    fun `le cure non superano i punti ferita massimi`() {
        val model = viewModel()
        val target = model.partyIds.first()
        val maximum = model.combatant(target)!!.snapshot().maxHitPoints()

        model.heal(target, 999)

        assertEquals(maximum, model.combatant(target)!!.currentHitPoints())
        assertFalse(model.combatant(target)!!.defeated())
    }

    @Test
    fun `l'ordine dei turni copre ogni combattente una volta sola`() {
        val model = viewModel()

        val ids = model.turnGroups.flatten()
        assertEquals(8, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(model.turnGroups[model.turnIndex].contains(model.activeCombatantId))
    }

    @Test
    fun `senza parita' ogni turno ha un solo combattente`() {
        val model = viewModel()

        // L'incontro dimostrativo usa punteggi statici tutti diversi.
        assertFalse(model.isSimultaneousTurn)
        assertEquals(1, model.activeCombatantIds.size)
    }

    @Test
    fun `la modalita parita non cambia ne salta attore durante il combattimento`() {
        val model = viewModel()
        val actor = model.activeCombatantId
        val groups = model.turnGroups

        model.simultaneousTies = !model.simultaneousTies

        assertEquals(actor, model.activeCombatantId)
        assertEquals(groups, model.turnGroups)
        assertNotNull(model.message)
    }

    @Test
    fun `in un turno simultaneo si puo scegliere quale attore agisce`() {
        val party = SampleEncounter.party().take(2)
        val enemy = SampleEncounter.enemies().first()
        val session = CombatSession.create("turno-simultaneo", 99L)
        session.addCombatant("alleato-1", party[0])
        session.addCombatant("alleato-2", party[1])
        session.addCombatant("nemico", enemy)
        session.setPartyCombatants(listOf("alleato-1", "alleato-2"))
        listOf("alleato-1", "alleato-2", "nemico").forEach { session.setInitiative(it, 15) }
        session.setInitiativeOrder(listOf("alleato-1", "alleato-2", "nemico"))
        session.setSimultaneousTies(true)
        session.markReady()
        session.start()
        val model = BattleViewModel(session)

        model.selectActiveActor("alleato-2")

        assertEquals("alleato-2", model.activeActorId)
        assertEquals("nemico", model.effectiveTargetId())
        assertNull(model.message)
    }

    @Test
    fun `la mira simultanea conserva il proprietario esatto della capacita`() {
        val party = SampleEncounter.party().take(2)
        val enemy = SampleEncounter.enemies().first()
        val session = CombatSession.create("mira-simultanea", 100L)
        session.addCombatant("alleato-1", party[0])
        session.addCombatant("alleato-2", party[1])
        session.addCombatant("nemico", enemy)
        session.setPartyCombatants(listOf("alleato-1", "alleato-2"))
        listOf("alleato-1", "alleato-2", "nemico").forEach { session.setInitiative(it, 15) }
        session.setInitiativeOrder(listOf("alleato-1", "alleato-2", "nemico"))
        session.setSimultaneousTies(true)
        session.markReady()
        session.start()
        val model = BattleViewModel(session)
        model.selectActiveActor("alleato-2")
        val ability = model.abilities("alleato-2").first { !it.isArea }

        model.beginAbilityTargeting(ability.id())
        model.onCombatantClicked("nemico")

        val attack = model.events.last { it.type() == EventType.ATTACK_ROLLED }
        assertEquals("alleato-2", attack.actorId())
        assertNull(model.singleTargeting)
    }

    @Test
    fun `modificare un combattente cambia la scheda e lo registra`() {
        val model = viewModel()
        val target = model.partyIds.first()
        val eventsBefore = model.events.size

        model.editCombatant(target, name = "Nome Corretto", armorClass = 21)

        assertEquals("Nome Corretto", model.combatant(target)!!.snapshot().name())
        assertEquals(21, model.combatant(target)!!.snapshot().armorClass())
        assertTrue(model.events.size > eventsBefore)
        assertNull(model.message)
    }

    @Test
    fun `la modifica viene inoltrata al catalogo`() {
        var receivedId: String? = null
        var receivedName: String? = null
        val model = BattleViewModel(SampleEncounter.startedSession(seed = 4242L)) { definitionId, snapshot ->
            receivedId = definitionId
            receivedName = snapshot.name()
        }
        val target = model.partyIds.first()
        val definitionId = model.combatant(target)!!.snapshot().definitionId()

        model.editCombatant(target, name = "Propagato")

        assertEquals(definitionId, receivedId)
        assertEquals("Propagato", receivedName)
    }

    @Test
    fun `ridurre i PF massimi non lascia i correnti oltre il nuovo tetto`() {
        val model = viewModel()
        val target = model.partyIds.first()

        model.editCombatant(target, maxHitPoints = 5)

        val combatant = model.combatant(target)!!
        assertEquals(5, combatant.snapshot().maxHitPoints())
        assertTrue(combatant.currentHitPoints() <= 5)
    }

    @Test
    fun `annullare ripristina la scheda precedente`() {
        val model = viewModel()
        val target = model.partyIds.first()
        val originalName = model.combatant(target)!!.snapshot().name()

        model.editCombatant(target, name = "Temporaneo")
        model.undo()

        assertEquals(originalName, model.combatant(target)!!.snapshot().name())
    }

    @Test
    fun `annullare una correzione risincronizza anche il sink persistente`() {
        val snapshots = mutableListOf<String>()
        val model = BattleViewModel(SampleEncounter.startedSession(seed = 4242L)) { _, snapshot ->
            snapshots += snapshot.name()
        }
        val target = model.partyIds.first()
        val originalName = model.combatant(target)!!.snapshot().name()

        model.editCombatant(target, name = "Temporaneo")
        model.undo()

        assertEquals(listOf("Temporaneo", originalName), snapshots)
    }

    @Test
    fun `riposizionare un token in modifica non consuma movimento`() {
        val model = viewModel()
        val actor = model.partyIds.first()
        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        val movementBefore = model.budget(actor)!!.movementRemainingFeet()

        model.editMode = true
        model.reposition(actor, 6, 5)

        assertEquals(6, model.placementOf(actor)!!.origin().column())
        assertEquals(5, model.placementOf(actor)!!.origin().row())
        assertEquals(movementBefore, model.budget(actor)!!.movementRemainingFeet())
    }

    @Test
    fun `il raggio percorribile deriva da budget e dimensione della casella`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!
        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        val grid = model.battleMap.grid()

        val expected = model.budget(actor)!!.movementRemainingFeet() / grid.feetPerSquare()
        assertEquals(expected, model.movementSquaresRemaining(actor))
        assertTrue(expected >= 4, "il combattente di turno deve poter percorrere il tragitto di prova")
    }

    @Test
    fun `trascinare il token attivo in gioco consuma il budget e riduce il raggio`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!
        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        val reachBefore = model.movementSquaresRemaining(actor)

        // Da (2,2) a (6,5): quattro caselle di distanza di Chebyshev, come il
        // rilascio del trascinamento con modifica disattivata.
        model.move(actor, 6, 5)

        assertEquals(6, model.placementOf(actor)!!.origin().column())
        assertEquals(5, model.placementOf(actor)!!.origin().row())
        assertEquals(reachBefore - 4, model.movementSquaresRemaining(actor))
    }

    @Test
    fun `con il raggio a zero il token attivo non ha piu' caselle da percorrere`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!
        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        val grid = model.battleMap.grid()

        // Esaurisce il budget spostandosi fino a dove arriva, poi verifica che non
        // resti spazio: e' il caso in cui il trascinamento non deve piu' muoverlo.
        val reach = model.movementSquaresRemaining(actor)
        model.move(actor, (2 + reach).coerceAtMost(grid.columns() - 1), 2)

        assertEquals(0, model.movementSquaresRemaining(actor))
    }
}
