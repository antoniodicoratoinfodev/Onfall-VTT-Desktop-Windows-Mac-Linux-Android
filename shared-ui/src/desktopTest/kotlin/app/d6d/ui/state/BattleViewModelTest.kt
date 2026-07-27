package app.d6d.ui.state

import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
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

    private fun guaranteedHitViewModel(): BattleViewModel {
        val ability = AbilityDefinition.attack(
            "colpo-certo",
            "Colpo certo",
            ActivationCost.ACTION,
            100,
            DamageFormula.fixed(DamageType.FORCE, 5),
        )
        val attacker = ActorDefinition.builder("attaccante-def", "Attaccante")
            .armorClass(10)
            .maxHitPoints(20)
            .initiativeScore(20)
            .abilities(listOf(ability))
            .build()
        val target = ActorDefinition.builder("bersaglio-def", "Bersaglio")
            .armorClass(10)
            .maxHitPoints(20)
            .initiativeScore(10)
            .build()
        val ally = ActorDefinition.builder("alleato-def", "Alleato")
            .armorClass(10)
            .maxHitPoints(20)
            .initiativeScore(15)
            .build()
        val session = CombatSession.create("clic-immediato", 4242L)
        session.addCombatant("attaccante", attacker)
        session.addCombatant("alleato", ally)
        session.addCombatant("bersaglio", target)
        session.setPartyCombatants(listOf("attaccante", "alleato"))
        session.setInitiative("attaccante", 20)
        session.setInitiative("alleato", 15)
        session.setInitiative("bersaglio", 10)
        session.setInitiativeOrder(listOf("attaccante", "alleato", "bersaglio"))
        session.markReady()
        session.start()
        return BattleViewModel(session)
    }

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
    fun `in modifica si puo aggiungere una voce del grimorio alla squadra`() {
        val model = viewModel()
        val activeBefore = model.activeCombatantId
        val actor = ActorDefinition.builder("rinforzo-def", "Rinforzo")
            .armorClass(12)
            .maxHitPoints(18)
            .initiativeScore(30)
            .build()
        model.editMode = true

        val instanceId = model.addRosterCombatant(actor, party = true)
        val addedId = requireNotNull(instanceId)

        assertEquals("rinforzo-def", addedId)
        assertTrue(addedId in model.partyIds)
        assertEquals(30, model.initiativeScore(addedId))
        assertEquals(activeBefore, model.activeCombatantId)
        assertTrue(model.events.any { it.type() == EventType.COMBATANT_ADDED && it.actorId() == addedId })
    }

    @Test
    fun `togliere lo sfondo chiude anche l'editing mappa`() {
        val model = viewModel()
        model.setMapBackground("mappa.png")
        model.editMode = true
        model.mapEditMode = true

        model.setMapBackground("")

        assertEquals("", model.battleMap.backgroundImage())
        assertFalse(model.mapEditMode)
    }

    @Test
    fun `il bersaglio predefinito appartiene sempre allo schieramento opposto`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!

        assertTrue(model.isParty(active) != model.isParty(target))
    }

    @Test
    fun `un alleato puo essere selezionato come bersaglio ma non lo stesso attore`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ally = model.partyIds.first { it != active }.takeIf { model.isParty(active) }
            ?: model.enemyIds.first { it != active }

        model.selectedTargetId = active
        assertNull(model.selectedTargetId)
        model.selectedTargetId = ally
        assertEquals(ally, model.selectedTargetId)
        assertEquals(ally, model.effectiveTargetId())
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
        assertNull(model.message)
    }

    @Test
    fun `la portata compare al passaggio del mouse e resta durante la mira`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ability = model.abilities(active).first { !it.isArea }

        model.setAbilityRangeHovered(active, ability.id(), hovered = true)

        assertEquals(active, model.abilityRangePreview?.combatantId)
        assertEquals(ability.id(), model.abilityRangePreview?.abilityId)
        assertEquals(ability.rangeFeet(), model.abilityRangePreview?.rangeFeet)
        assertFalse(model.abilityRangePreview!!.targeting)

        model.beginAbilityTargeting(ability.id())
        model.setAbilityRangeHovered(active, ability.id(), hovered = false)

        assertEquals(ability.id(), model.abilityRangePreview?.abilityId)
        assertTrue(model.abilityRangePreview!!.targeting)

        model.cancelSingleTargeting()

        assertNull(model.abilityRangePreview)
    }

    @Test
    fun `ricliccare la capacita selezionata annulla la mira senza usare azioni`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ability = model.abilities(active).first { !it.isArea }
        val revisionBefore = model.state.revision()
        val eventsBefore = model.events.size

        model.beginAbilityTargeting(ability.id())
        assertEquals(ability.id(), model.singleTargeting?.abilityId)

        model.beginAbilityTargeting(ability.id())

        assertNull(model.singleTargeting)
        assertEquals(revisionBefore, model.state.revision())
        assertEquals(eventsBefore, model.events.size)
        assertNull(model.message)
    }

    @Test
    fun `lo stesso attore non usa il bersaglio predefinito e lascia attiva la mira`() {
        val model = viewModel()
        val active = model.activeCombatantId!!
        val ability = model.abilities(active).first { !it.isArea }
        val revisionBefore = model.state.revision()

        model.beginAbilityTargeting(ability.id())
        model.onCombatantClicked(active)

        assertNotNull(model.singleTargeting)
        assertEquals(revisionBefore, model.state.revision())
        assertNull(model.selectedTargetId)
        assertNotNull(model.message)
    }

    @Test
    fun `una capacita ostile applica immediatamente il danno anche a un alleato`() {
        val model = guaranteedHitViewModel()
        val ability = model.abilities("attaccante").first { !it.isArea }
        val hitPointsBefore = model.combatant("alleato")!!.currentHitPoints()

        model.beginAbilityTargeting(ability.id())
        model.onCombatantClicked("alleato")

        assertEquals(hitPointsBefore - 5, model.combatant("alleato")!!.currentHitPoints())
        assertEquals("alleato", model.selectedTargetId)
        assertNull(model.singleTargeting)
        assertNull(model.message)
        assertEquals(
            "alleato",
            model.events.last { it.type() == EventType.ATTACK_ROLLED }.targetId(),
        )
    }

    @Test
    fun `il clic sul bersaglio applica subito il danno senza terminare il turno`() {
        val model = guaranteedHitViewModel()
        val active = model.activeCombatantId!!
        val target = "bersaglio"
        val ability = model.abilities(active).first { !it.isArea }
        val hitPointsBefore = model.combatant(target)!!.currentHitPoints()
        val turnIndexBefore = model.turnIndex
        val eventsBefore = model.events.size

        model.beginAbilityTargeting(ability.id())
        model.onCombatantClicked(target)

        val resolutionEvents = model.events.drop(eventsBefore)
        assertEquals(hitPointsBefore - 5, model.combatant(target)!!.currentHitPoints())
        assertTrue(resolutionEvents.any { it.type() == EventType.DAMAGE_APPLIED })
        assertFalse(resolutionEvents.any { it.type() == EventType.TURN_ENDED })
        assertEquals(turnIndexBefore, model.turnIndex)
        assertEquals(active, model.activeCombatantId)
        assertNull(model.singleTargeting)
        assertEquals(active, model.inspectedCombatantId)
        assertTrue(model.actionResolution?.isHit == true)
        assertTrue(model.actionResolution?.text.orEmpty().contains("subisce 5 danni"))

        // Una volta risolta la mira, un altro clic ispeziona soltanto: non puo'
        // applicare per errore una seconda volta la stessa capacita'.
        val revisionAfterAttack = model.state.revision()
        val attacksAfterAttack = model.events.count { it.type() == EventType.ATTACK_ROLLED }
        model.onCombatantClicked(target)
        assertEquals(revisionAfterAttack, model.state.revision())
        assertEquals(attacksAfterAttack, model.events.count { it.type() == EventType.ATTACK_ROLLED })
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
    fun `togliere un token dalla mappa conserva l'ingombro della sessione`() {
        val largeId = SampleEncounter.enemies().first().id()
        var catalogFootprint = 2
        val model = BattleViewModel(
            SampleEncounter.startedSession(seed = 4242L),
            footprintProvider = { id -> if (id == largeId) catalogFootprint else 1 },
        )
        model.place(largeId, 2, 2, model.squaresPerSideFor(largeId))

        catalogFootprint = 4
        model.removeFromMap(largeId)

        assertNull(model.placementOf(largeId))
        assertEquals(2, model.squaresPerSideFor(largeId))
        assertTrue(model.presentationState()["footprints"]!!.contains("$largeId=2"))
    }

    @Test
    fun `ridurre la griglia conserva l'ingombro dei token rimasti fuori bordo`() {
        val largeId = SampleEncounter.enemies().first().id()
        var catalogFootprint = 2
        val model = BattleViewModel(
            SampleEncounter.startedSession(seed = 4242L),
            footprintProvider = { id -> if (id == largeId) catalogFootprint else 1 },
        )
        model.place(largeId, 18, 13, model.squaresPerSideFor(largeId))

        catalogFootprint = 4
        model.configureMap(columns = 10, rows = 10, feetPerSquare = 5)

        assertNull(model.placementOf(largeId))
        assertEquals(2, model.squaresPerSideFor(largeId))
        assertTrue(model.presentationState()["footprints"]!!.contains("$largeId=2"))
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
    fun `in modifica i PF attuali possono essere impostati e zero significa morto`() {
        val model = viewModel()
        val target = model.partyIds.first()
        val originalHitPoints = model.combatant(target)!!.currentHitPoints()

        model.setCurrentHitPoints(target, 0)

        assertEquals(0, model.combatant(target)!!.currentHitPoints())
        assertTrue(model.combatant(target)!!.dead())
        assertTrue(model.events.any { it.type() == EventType.CURRENT_HIT_POINTS_SET })
        assertTrue(model.events.any { it.type() == EventType.DIED })

        model.undo()

        assertEquals(originalHitPoints, model.combatant(target)!!.currentHitPoints())
        assertFalse(model.combatant(target)!!.dead())
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
