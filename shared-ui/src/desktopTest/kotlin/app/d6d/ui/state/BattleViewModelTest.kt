package app.d6d.ui.state

import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.CombatResourceState
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.DiceExpression
import app.d6d.domain.combat.EventType
import app.d6d.domain.combat.HealingDefinition
import app.d6d.domain.combat.HealingSlotScaling
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility
import app.d6d.domain.combat.SpellSlotResourceId
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuActionReport
import app.d6d.engine.ai.EnemyCpuActionType
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.engine.ai.EnemyCpuReason
import app.d6d.engine.ai.EnemyCpuTargetReport
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.ui.content.SampleEncounter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifica lo strato di presentazione, non le regole: quelle hanno gia' i propri
 * test nel motore. Qui interessa che la UI non aggiri il motore e non trasformi
 * una violazione delle regole in un crash.
 */
class BattleViewModelTest {

    private companion object {
        const val ACTION_SURGE_ID = "srd521-it:feature:guerriero:azione-impetuosa"
        const val ACTION_SURGE_RESOURCE = "srd521-it:resource:guerriero:azione-impetuosa"
        const val CPU_ATTACK_ID = "cpu-attacco-limitato"
        const val CPU_ATTACK_RESOURCE = "cpu-risorsa-attacco"
    }

    private fun viewModel() = BattleViewModel(SampleEncounter.startedSession(seed = 4242L))

    @Test
    fun `la difficolta cpu fa round trip nella presentation`() {
        val session = SampleEncounter.startedSession(seed = 4242L)
        val model = BattleViewModel(session)

        model.adopt(
            session,
            mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.SORRY_FOR_YOU.name),
        )

        assertTrue(model.enemyCpuEnabled)
        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU, model.enemyCpuDifficulty)
        assertEquals(
            EnemyCpuDifficulty.SORRY_FOR_YOU.name,
            model.presentationState()["enemyCpuDifficulty"],
        )
    }

    @Test
    fun `una sessione legacy o con difficolta sconosciuta non abilita la cpu`() {
        val legacySession = SampleEncounter.startedSession(seed = 4242L)
        val legacy = BattleViewModel(legacySession)
        legacy.adopt(legacySession, emptyMap())
        assertFalse(legacy.enemyCpuEnabled)
        assertFalse("enemyCpuDifficulty" in legacy.presentationState())

        val unknownSession = SampleEncounter.startedSession(seed = 4243L)
        val unknown = BattleViewModel(unknownSession)
        unknown.adopt(unknownSession, mapOf("enemyCpuDifficulty" to "IMPOSSIBILE"))
        assertFalse(unknown.enemyCpuEnabled)
        assertEquals(EnemyCpuDifficulty.MEDIUM, unknown.enemyCpuDifficulty)
    }

    private fun guaranteedHitViewModel(
        resourceSink: CombatResourceSink = CombatResourceSink { _, _ -> },
        extraTarget: Boolean = false,
    ): BattleViewModel {
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
        if (extraTarget) session.addCombatant("riserva", target)
        session.setPartyCombatants(listOf("attaccante", "alleato"))
        session.setInitiative("attaccante", 20)
        session.setInitiative("alleato", 15)
        session.setInitiative("bersaglio", 10)
        if (extraTarget) session.setInitiative("riserva", 5)
        session.setInitiativeOrder(
            if (extraTarget) {
                listOf("attaccante", "alleato", "bersaglio", "riserva")
            } else {
                listOf("attaccante", "alleato", "bersaglio")
            },
        )
        session.markReady()
        session.start()
        return BattleViewModel(session, resourceSink = resourceSink)
    }

    private fun actionSurgeSession(): CombatSession {
        val surge = AbilityDefinition.builder(ACTION_SURGE_ID, "Azione impetuosa")
            .activationCost(ActivationCost.NONE)
            .resolutionMethod(ResolutionMethod.AUTOMATIC)
            .automationStatus(AutomationStatus.AUTOMATED)
            .effect(AbilityEffect.GRANT_NON_MAGIC_ACTION)
            .resource(ACTION_SURGE_RESOURCE, 1)
            .build()
        val fighter = ActorDefinition.builder("fighter-def", "Guerriero")
            .armorClass(18)
            .maxHitPoints(30)
            .initiativeScore(20)
            .abilities(listOf(surge))
            .resources(listOf(CombatResourceState(ACTION_SURGE_RESOURCE, "Azione impetuosa", 1, 0)))
            .build()
        val target = ActorDefinition.builder("target-def", "Bersaglio")
            .armorClass(12)
            .maxHitPoints(20)
            .initiativeScore(10)
            .build()
        return CombatSession.create("azione-impetuosa-ui", 4242L).also { session ->
            session.addCombatant("fighter", fighter)
            session.addCombatant("target", target)
            session.setPartyCombatants(listOf("fighter"))
            session.setInitiative("fighter", 20)
            session.setInitiative("target", 10)
            session.setInitiativeOrder(listOf("fighter", "target"))
            session.markReady()
            session.start()
        }
    }

    /** Due segnalini dalla stessa scheda: la quantita' scelta nel wizard dell'incontro. */
    private fun clonedActionSurgeSession(): CombatSession {
        val surge = AbilityDefinition.builder(ACTION_SURGE_ID, "Azione impetuosa")
            .activationCost(ActivationCost.NONE)
            .resolutionMethod(ResolutionMethod.AUTOMATIC)
            .automationStatus(AutomationStatus.AUTOMATED)
            .effect(AbilityEffect.GRANT_NON_MAGIC_ACTION)
            .resource(ACTION_SURGE_RESOURCE, 1)
            .build()
        val fighter = ActorDefinition.builder("fighter-def", "Guerriero")
            .armorClass(18)
            .maxHitPoints(30)
            .initiativeScore(20)
            .abilities(listOf(surge))
            .resources(listOf(CombatResourceState(ACTION_SURGE_RESOURCE, "Azione impetuosa", 1, 0)))
            .build()
        val target = ActorDefinition.builder("target-def", "Bersaglio")
            .armorClass(12)
            .maxHitPoints(20)
            .initiativeScore(10)
            .build()
        return CombatSession.create("cloni-ui", 4242L).also { session ->
            session.addCombatant("fighter-1", fighter)
            session.addCombatant("fighter-2", fighter)
            session.addCombatant("target", target)
            session.setPartyCombatants(listOf("fighter-1", "fighter-2"))
            session.setInitiative("fighter-1", 20)
            session.setInitiative("fighter-2", 15)
            session.setInitiative("target", 10)
            session.setInitiativeOrder(listOf("fighter-1", "fighter-2", "target"))
            session.markReady()
            session.start()
        }
    }

    private fun atWillAbilitySession(ability: AbilityDefinition, currentHitPoints: Int = 20): CombatSession {
        val hero = ActorDefinition.builder("at-will-hero-def", "Eroe")
            .armorClass(15)
            .maxHitPoints(20)
            .currentHitPoints(currentHitPoints)
            .initiativeScore(20)
            .abilities(listOf(ability))
            .build()
        val enemy = ActorDefinition.builder("at-will-enemy-def", "Nemico")
            .armorClass(10)
            .maxHitPoints(20)
            .initiativeScore(10)
            .build()
        return CombatSession.create("at-will-ui", 4242L).also { session ->
            session.addCombatant("hero", hero)
            session.addCombatant("enemy", enemy)
            session.setPartyCombatants(listOf("hero"))
            session.setInitiative("hero", 20)
            session.setInitiative("enemy", 10)
            session.setInitiativeOrder(listOf("hero", "enemy"))
            session.markReady()
            session.start()
        }
    }

    private fun enemyCpuResourceSession(
        simultaneousWithParty: Boolean = false,
        extraEnemy: Boolean = false,
        heroHitPoints: Int = 20,
        // Allontanare il bersaglio costringe la CPU a spostarsi prima di colpire:
        // e' il turno a piu' comandi che serve per osservare il ritmo.
        heroColumn: Int = 2,
    ): CombatSession {
        val attack = AbilityDefinition.builder(CPU_ATTACK_ID, "Colpo CPU")
            .activationCost(ActivationCost.ACTION)
            .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
            .attackBonus(100)
            .damage(listOf(DamageFormula.fixed(DamageType.FORCE, 5)))
            .resource(CPU_ATTACK_RESOURCE, 1)
            .build()
        val enemy = ActorDefinition.builder("cpu-enemy-def", "CPU")
            .armorClass(12)
            .maxHitPoints(20)
            .initiativeScore(20)
            .abilities(listOf(attack))
            .resources(listOf(CombatResourceState(CPU_ATTACK_RESOURCE, "Colpo CPU", 1, 0)))
            .build()
        val hero = ActorDefinition.builder("cpu-hero-def", "Eroe")
            .armorClass(10)
            .maxHitPoints(heroHitPoints)
            .initiativeScore(10)
            .build()
        return CombatSession.create("cpu-ui", 4242L).also { session ->
            session.addCombatant("enemy", enemy)
            if (extraEnemy) session.addCombatant("enemy-2", enemy)
            session.addCombatant("hero", hero)
            session.setPartyCombatants(listOf("hero"))
            session.configureMap(MapGrid(10, 10, 5))
            session.placeCombatant("enemy", GridPosition(1, 1), 1)
            if (extraEnemy) session.placeCombatant("enemy-2", GridPosition(1, 2), 1)
            session.placeCombatant("hero", GridPosition(heroColumn, 1), 1)
            session.setInitiative("enemy", 20)
            if (extraEnemy) session.setInitiative("enemy-2", 20)
            session.setInitiative("hero", if (simultaneousWithParty) 20 else 10)
            session.setInitiativeOrder(
                if (extraEnemy) listOf("enemy", "enemy-2", "hero") else listOf("enemy", "hero"),
            )
            if (simultaneousWithParty) session.setSimultaneousTies(true)
            session.markReady()
            session.start()
        }
    }

    private fun enemyCpuAreaSession(): CombatSession {
        val area = AbilityDefinition.builder("cpu-area", "Onda CPU")
            .activationCost(ActivationCost.ACTION)
            .resolutionMethod(ResolutionMethod.SAVING_THROW)
            .automationStatus(AutomationStatus.AUTOMATED)
            .rangeFeet(60)
            .areaRadiusFeet(5)
            .saveAbility(SaveAbility.DEXTERITY)
            .damage(listOf(DamageFormula.fixed(DamageType.FORCE, 7)))
            .build()
        val enemy = ActorDefinition.builder("cpu-area-enemy", "CPU area")
            .maxHitPoints(20)
            .spellSaveDc(100)
            .abilities(listOf(area))
            .build()
        val hero = ActorDefinition.builder("cpu-area-hero", "Eroe area")
            .maxHitPoints(30)
            .build()
        return CombatSession.create("cpu-area-ui", 4242L).also { session ->
            session.addCombatant("enemy", enemy)
            session.addCombatant("hero", hero)
            session.setPartyCombatants(listOf("hero"))
            session.configureMap(MapGrid(10, 10, 5))
            session.placeCombatant("enemy", GridPosition(1, 1), 1)
            session.placeCombatant("hero", GridPosition(3, 1), 1)
            session.setInitiative("enemy", 20)
            session.setInitiative("hero", 10)
            session.setInitiativeOrder(listOf("enemy", "hero"))
            session.markReady()
            session.start()
        }
    }

    private fun enemyCpuAlternativeSlotHealingSession(): CombatSession {
        val nominalSlot = SpellSlotResourceId.standard(1).id()
        val alternateSlot = SpellSlotResourceId.pact(2).id()
        val healing = AbilityDefinition.builder("cpu-cura-slot", "Cura CPU")
            .activationCost(ActivationCost.ACTION)
            .resolutionMethod(ResolutionMethod.AUTOMATIC)
            .automationStatus(AutomationStatus.AUTOMATED)
            .spellOrCantrip(true)
            .healing(
                HealingDefinition.dice(
                    HealingTarget.SELF,
                    DiceExpression(1, 4, 2),
                    HealingSlotScaling(1, 1),
                ),
            )
            .resource(nominalSlot, 1)
            .build()
        val healer = ActorDefinition.builder("cpu-healer-def", "Guaritore CPU")
            .armorClass(12)
            .maxHitPoints(20)
            .currentHitPoints(1)
            .initiativeScore(20)
            .abilities(listOf(healing))
            .resources(
                listOf(
                    CombatResourceState(nominalSlot, "Slot 1", 1, 1),
                    CombatResourceState(alternateSlot, "Slot del patto 2", 1, 0),
                ),
            )
            .build()
        val hero = ActorDefinition.builder("cpu-slot-hero-def", "Eroe")
            .armorClass(12)
            .maxHitPoints(20)
            .initiativeScore(10)
            .build()
        return CombatSession.create("cpu-slot-ui", 4242L).also { session ->
            session.addCombatant("healer", healer)
            session.addCombatant("hero", hero)
            session.setPartyCombatants(listOf("hero"))
            session.setInitiative("healer", 20)
            session.setInitiative("hero", 10)
            session.setInitiativeOrder(listOf("healer", "hero"))
            session.markReady()
            session.start()
        }
    }

    @Test
    fun `nel gruppo misto la cpu agisce una volta blocca solo il nemico e seleziona il party`() {
        val session = enemyCpuResourceSession(simultaneousWithParty = true)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        assertTrue(model.shouldScheduleEnemyCpu)
        assertTrue(model.enemyCpuControlsActor("enemy"))
        assertFalse(model.enemyCpuControlsActor("hero"))

        model.selectActiveActor("hero")
        assertFalse(model.canUseAbilitiesOf("hero"))
        assertTrue(model.enemyCpuBatchPending)
        model.move("hero", 3, 1)
        assertEquals(GridPosition(2, 1), model.placementOf("hero")!!.origin())
        assertTrue(model.message.orEmpty().contains("Attendi"))
        model.applyManualDamage("hero", 3)
        model.grantTemporary("hero", 5)
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, model.combatant("hero")!!.temporaryHitPoints())
        model.selectActiveActor("enemy")
        assertFalse(model.canUseAbilitiesOf("enemy"))

        model.playEnemyCpuTurn()

        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals(listOf("enemy", "hero"), model.activeCombatantIds)
        assertEquals("hero", model.activeActorId)
        assertTrue(model.enemyCpuBatchCompleted)
        assertFalse(model.shouldScheduleEnemyCpu)
        assertFalse(model.enemyCpuBatchPending)
        assertTrue(model.canUseAbilitiesOf("hero"))
        model.move("hero", 3, 1)
        assertEquals(GridPosition(3, 1), model.placementOf("hero")!!.origin())

        model.playEnemyCpuTurn()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
    }

    @Test
    fun `la riproduzione ritmata mostra un comando alla volta`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.FAST
        val startingSquare = model.placementOf("enemy")!!.origin()

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }

        // Sospesa sulla prima pausa: il nemico si e' avvicinato ma non ha ancora colpito.
        assertTrue(model.enemyCpuBusy)
        assertNotEquals(startingSquare, model.placementOf("enemy")!!.origin())
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals("enemy", model.enemyCpuActingCombatantId)
        assertTrue(model.enemyCpuActionLabel.orEmpty().contains("si sposta"))

        turn.join()

        assertFalse(model.enemyCpuBusy)
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals("hero", model.activeActorId)
        assertNull(model.enemyCpuActingCombatantId)
    }

    @Test
    fun `entrare in modifica riavvolge il frammento cpu e ripianifica alla ripresa`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        val startingSquare = model.placementOf("enemy")!!.origin()

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        assertTrue(model.enemyCpuBusy)
        assertNotEquals(startingSquare, model.placementOf("enemy")!!.origin())

        model.editMode = true

        assertFalse(model.enemyCpuBusy)
        assertEquals(startingSquare, model.placementOf("enemy")!!.origin())
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertFalse(model.enemyCpuTurnSuppressed)
        assertFalse(model.shouldScheduleEnemyCpu)
        turn.cancelAndJoin()

        model.editMode = false
        assertTrue(model.shouldScheduleEnemyCpu)
        model.playEnemyCpuTurn()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertFalse(model.enemyCpuBatchPending)
    }

    @Test
    fun `annullare l attesa non lascia il turno nemico a meta`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        turn.cancelAndJoin()

        // Chi annulla l'attesa rinuncia alle pause, non al resto del turno.
        assertFalse(model.enemyCpuBusy)
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals("hero", model.activeActorId)
        assertFalse(model.enemyCpuBatchPending)
    }

    @Test
    fun `caricare un altra partita durante la pausa non contamina quella nuova`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        // Il tavolo apre un'altra partita mentre la CPU e' ferma sulla pausa: il
        // turno rimasto indietro appartiene a una sessione che non esiste piu'.
        val loaded = enemyCpuResourceSession()
        model.adopt(loaded, emptyMap())
        turn.cancelAndJoin()

        assertSame(loaded, model.session)
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        model.undo()
        assertFalse(model.message.orEmpty().contains("batch CPU"))
        assertFalse(model.enemyCpuTurnSuppressed)
    }

    @Test
    fun `il finally del playback adottato non spegne quello della nuova sessione`() = runTest {
        val original = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(original)
        val cpuPresentation = mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name)
        model.adopt(original, cpuPresentation)
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        val oldTurn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }

        val loaded = enemyCpuResourceSession(heroColumn = 5)
        model.adopt(loaded, cpuPresentation)
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        val newTurn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        assertTrue(model.enemyCpuBusy)

        oldTurn.cancelAndJoin()

        assertSame(loaded, model.session)
        assertTrue(model.enemyCpuBusy)
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())

        newTurn.join()
        assertFalse(model.enemyCpuBusy)
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
    }

    @Test
    fun `la guard revisione non annulla ne ingloba il comando esterno`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        val startingSquare = model.placementOf("enemy")!!.origin()

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        val movedSquare = model.placementOf("enemy")!!.origin()
        assertNotEquals(startingSquare, movedSquare)

        // Simula un mutatore esterno al ViewModel fra due frame del playback.
        session.applyDamage(
            "enemy",
            "hero",
            listOf(app.d6d.domain.combat.DamageComponent(DamageType.FORCE, 1)),
            false,
        )
        turn.join()

        assertEquals(movedSquare, model.placementOf("enemy")!!.origin())
        assertEquals(19, model.combatant("hero")!!.currentHitPoints())
        assertTrue(model.enemyCpuTurnSuppressed)
        assertTrue(model.message.orEmpty().contains("stato del tavolo è cambiato"))

        // Non esiste un Undo batch CPU che inglobi entrambe le revisioni: il
        // primo Undo rimuove soltanto il comando esterno.
        model.undo()
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals(movedSquare, model.placementOf("enemy")!!.origin())
    }

    @Test
    fun `la guard esterna sincronizza il delta risorsa dell ultima revisione cpu`() = runTest {
        val persistedSpent = mutableListOf<Int>()
        val session = enemyCpuResourceSession(heroColumn = 2)
        val model = BattleViewModel(
            session,
            resourceSink = CombatResourceSink { _, resources ->
                persistedSpent += resources.single { it.id() == CPU_ATTACK_RESOURCE }.spent()
            },
        )
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())

        session.applyDamage(
            "enemy",
            "hero",
            listOf(app.d6d.domain.combat.DamageComponent(DamageType.FORCE, 1)),
            false,
        )
        turn.join()

        assertEquals(14, model.combatant("hero")!!.currentHitPoints())
        assertEquals(listOf(1), persistedSpent)
        assertTrue(model.enemyCpuTurnSuppressed)
        model.undo()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())
    }

    @Test
    fun `un errore risorse nella guard esterna preserva entrambe le revisioni`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 2)
        val model = BattleViewModel(
            session,
            resourceSink = CombatResourceSink { _, _ -> error("sink esterno non disponibile") },
        )
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        session.applyDamage(
            "enemy",
            "hero",
            listOf(app.d6d.domain.combat.DamageComponent(DamageType.FORCE, 1)),
            false,
        )
        turn.join()

        assertEquals(14, model.combatant("hero")!!.currentHitPoints())
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())
        assertTrue(model.message.orEmpty().contains("sink esterno non disponibile"))
        model.undo()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())
    }

    @Test
    fun `undo dopo il consolidamento rimuove prima la revisione esterna`() {
        val session = enemyCpuResourceSession(heroColumn = 2)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.playEnemyCpuTurn()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())

        session.applyDamage(
            "enemy",
            "hero",
            listOf(app.d6d.domain.combat.DamageComponent(DamageType.FORCE, 1)),
            false,
        )
        model.undo()

        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())
        assertTrue(model.message.orEmpty().contains("batch resta"))

        model.undo()
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, model.combatant("enemy")!!.resources().single().spent())
    }

    @Test
    fun `undo toglie una per volta tutte le revisioni esterne prima del batch cpu`() {
        val session = enemyCpuResourceSession(heroColumn = 2)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.playEnemyCpuTurn()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())

        // Due comandi che non passano dal ViewModel: il batch resta in cima alla
        // pila degli effetti pur non essendo piu' in cima alla sessione.
        session.applyDamage(
            "enemy",
            "hero",
            listOf(app.d6d.domain.combat.DamageComponent(DamageType.FORCE, 1)),
            false,
        )
        session.applyDamage(
            "enemy",
            "hero",
            listOf(app.d6d.domain.combat.DamageComponent(DamageType.FORCE, 2)),
            false,
        )
        model.sync()
        assertEquals(12, model.combatant("hero")!!.currentHitPoints())

        model.undo()
        assertEquals(14, model.combatant("hero")!!.currentHitPoints())
        assertTrue(model.message.orEmpty().contains("batch resta"))

        model.undo()
        assertEquals(15, model.combatant("hero")!!.currentHitPoints(),
            "anche il secondo Undo tocca soltanto la revisione esterna")
        assertTrue(model.message.orEmpty().contains("batch resta"))
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())

        model.undo()
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, model.combatant("enemy")!!.resources().single().spent())
    }

    @Test
    fun `chi salva chiude il turno cpu invece di fotografarne meta`() = runTest {
        val session = enemyCpuResourceSession(heroColumn = 5)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW

        val turn = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        assertTrue(model.enemyCpuBusy)

        // La chiusura dell'applicazione non puo' aspettare il ritmo.
        model.settleEnemyCpuTurn()

        assertFalse(model.enemyCpuBusy)
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals("hero", model.activeActorId)
        turn.cancelAndJoin()
        // La riproduzione che riprende non deve rigiocare nulla né raddoppiare l'Undo.
        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        model.undo()
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
    }

    @Test
    fun `il ritmo della cpu fa round trip nella presentation`() {
        val session = enemyCpuResourceSession()
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        assertEquals(EnemyCpuSpeed.NORMAL, model.enemyCpuSpeed)

        model.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        val restored = BattleViewModel(session)
        restored.adopt(session, model.presentationState())

        assertEquals(EnemyCpuSpeed.SLOW, restored.enemyCpuSpeed)
    }

    @Test
    fun `il ritmo istantaneo non introduce pause di apertura o fra i comandi`() {
        assertEquals(0L, EnemyCpuSpeed.INSTANT.openingDelayMillis)
        assertEquals(0L, EnemyCpuSpeed.INSTANT.stepDelayMillis)
        assertTrue(EnemyCpuSpeed.FAST.stepDelayMillis > 0L)
    }

    @Test
    fun `il guard mixed non viene segnato se dopo la cpu non resta un party attivo`() {
        val session = enemyCpuResourceSession(
            simultaneousWithParty = true,
            heroHitPoints = 5,
        )
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        model.playEnemyCpuTurn()

        assertTrue(model.combatant("hero")!!.defeated())
        assertFalse(model.enemyCpuBatchCompleted)
        assertFalse(model.presentationState().containsKey("enemyCpuCompletedTurn"))
    }

    @Test
    fun `un attore morto per sfinimento con punti ferita non puo usare capacita`() {
        val session = enemyCpuResourceSession(simultaneousWithParty = true)
        session.setExhaustion("hero", 6)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.selectActiveActor("hero")

        assertTrue(model.combatant("hero")!!.dead())
        assertFalse(model.combatant("hero")!!.defeated())
        assertFalse(model.canUseAbilitiesOf("hero"))
    }

    @Test
    fun `la cpu salta un gruppo solo nemico senza attori vivi`() {
        val session = enemyCpuResourceSession()
        session.setExhaustion("enemy", 6)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        assertTrue(model.activeCombatantIds.isEmpty())
        assertTrue(model.shouldScheduleEnemyCpu)

        model.playEnemyCpuTurn()

        assertEquals("hero", model.activeActorId)
        assertFalse(model.shouldScheduleEnemyCpu)
    }

    @Test
    fun `la cpu non si avvia nel mixed se resta solo il party vivo`() {
        val session = enemyCpuResourceSession(simultaneousWithParty = true)
        session.setExhaustion("enemy", 6)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        assertEquals(listOf("hero"), model.activeCombatantIds)
        assertFalse(model.shouldScheduleEnemyCpu)
        assertFalse(model.enemyCpuBatchPending)
        assertTrue(model.canUseAbilitiesOf("hero"))
    }

    @Test
    fun `un morto per sfinimento non viene scelto ne confermato come bersaglio`() {
        val model = guaranteedHitViewModel(extraTarget = true)
        model.setExhaustion("bersaglio", 6)
        val ability = model.abilities("attaccante").single()

        assertTrue(model.combatant("bersaglio")!!.dead())
        assertTrue(model.combatant("bersaglio")!!.currentHitPoints() > 0)
        model.selectedTargetId = "bersaglio"
        assertNull(model.selectedTargetId)
        assertEquals("riserva", model.effectiveTargetId())

        model.beginAbilityTargeting(ability.id())
        val revisionBefore = model.state.revision()
        model.onCombatantClicked("bersaglio")

        assertEquals(revisionBefore, model.state.revision())
        assertNotNull(model.singleTargeting)
        assertTrue(model.message.orEmpty().contains("già sconfitto"))
    }

    @Test
    fun `guard completato e sospensione cpu sopravvivono alla presentation`() {
        val session = enemyCpuResourceSession(simultaneousWithParty = true)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.SORRY_FOR_YOU.name))
        model.playEnemyCpuTurn()

        val completedPresentation = model.presentationState()
        val completed = BattleViewModel(model.session)
        completed.adopt(model.session, completedPresentation)

        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU, completed.enemyCpuDifficulty)
        assertTrue(completed.enemyCpuBatchCompleted)
        assertFalse(completed.shouldScheduleEnemyCpu)

        model.undo()
        val suppressedPresentation = model.presentationState()
        val suppressed = BattleViewModel(model.session)
        suppressed.adopt(model.session, suppressedPresentation)

        assertTrue(suppressed.enemyCpuTurnSuppressed)
        assertFalse(suppressed.shouldScheduleEnemyCpu)
        assertEquals(20, suppressed.combatant("hero")!!.currentHitPoints())
    }

    @Test
    fun `il guard mixed resta stabile se cambia l insieme dei nemici vivi`() {
        val session = enemyCpuResourceSession(simultaneousWithParty = true, extraEnemy = true)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        model.playEnemyCpuTurn()
        assertTrue(model.enemyCpuBatchCompleted)

        model.applyManualDamage("enemy", 100)

        assertTrue(model.combatant("enemy")!!.defeated())
        assertTrue("enemy-2" in model.activeCombatantIds)
        assertTrue(model.enemyCpuBatchCompleted)
        assertFalse(model.shouldScheduleEnemyCpu)
    }

    @Test
    fun `un area cpu produce feedback sul singolo token colpito`() {
        val session = enemyCpuAreaSession()
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        model.playEnemyCpuTurn()

        assertTrue(model.floating["hero"].orEmpty().isNotEmpty())
        assertTrue(model.actionResolution?.text.orEmpty().contains("1 attacchi"))
    }

    @Test
    fun `un bersaglio immune a un area cpu mostra danno zero`() {
        val model = BattleViewModel(enemyCpuAreaSession())

        model.pushEnemyCpuActionFeedback(
            EnemyCpuActionReport(
                EnemyCpuActionType.AREA_ATTACK,
                "enemy",
                "",
                "cpu-area",
                0,
                EnemyCpuReason.AREA_COVERAGE,
                listOf(EnemyCpuTargetReport("hero", 0, false)),
            ),
        )

        assertEquals("0/Immune", model.floating["hero"]?.single()?.text)
    }

    @Test
    fun `un attacco cpu a segno contro un immune non appare mancato`() {
        val model = BattleViewModel(enemyCpuAreaSession())

        model.pushEnemyCpuActionFeedback(
            EnemyCpuActionReport(
                EnemyCpuActionType.ATTACK,
                "enemy",
                "hero",
                "cpu-attack",
                0,
                EnemyCpuReason.BEST_ATTACK,
                true,
            ),
        )

        val feedback = model.floating["hero"]?.single()
        assertEquals("0/Immune", feedback?.text)
        assertEquals(app.d6d.ui.components.FloatKind.INFO, feedback?.kind)
    }

    @Test
    fun `la cura cpu mostra il livello dello slot scelto`() {
        val model = BattleViewModel(enemyCpuAreaSession())

        model.pushEnemyCpuActionFeedback(
            EnemyCpuActionReport(
                EnemyCpuActionType.HEAL,
                "enemy",
                "hero",
                "cura-cpu",
                8,
                EnemyCpuReason.PROTECT_ALLY,
                "",
                emptyList(),
                false,
                "slot-incantesimo-3",
                3,
            ),
        )

        assertEquals("+8 · slot di 3° livello", model.floating["hero"]?.single()?.text)
    }

    @Test
    fun `la cura cpu senza slot mantiene il feedback compatto`() {
        val model = BattleViewModel(enemyCpuAreaSession())

        model.pushEnemyCpuActionFeedback(
            EnemyCpuActionReport(
                EnemyCpuActionType.HEAL,
                "enemy",
                "hero",
                "cura-cpu",
                5,
                EnemyCpuReason.PROTECT_ALLY,
            ),
        )

        assertEquals("+5", model.floating["hero"]?.single()?.text)
    }

    @Test
    fun `il turno cpu persiste la risorsa e undo annulla atomicamente tutto il batch`() {
        val persistedSpent = mutableListOf<Int>()
        val session = enemyCpuResourceSession()
        val model = BattleViewModel(
            session,
            resourceSink = CombatResourceSink { _, resources ->
                persistedSpent += resources.single { it.id() == CPU_ATTACK_RESOURCE }.spent()
            },
        )
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        assertTrue(model.shouldScheduleEnemyCpu)
        model.playEnemyCpuTurn()

        assertEquals(15, model.combatant("hero")!!.currentHitPoints())
        assertEquals(1, model.combatant("enemy")!!.resources().single().spent())
        assertEquals(listOf(1), persistedSpent)
        assertEquals("hero", model.activeCombatantId)

        model.undo()

        assertEquals("enemy", model.activeCombatantId)
        assertTrue(model.enemyCpuTurnSuppressed)
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, model.combatant("enemy")!!.resources().single().spent())
        assertEquals(listOf(1, 0), persistedSpent)
        assertTrue(model.message.orEmpty().contains("Intero batch CPU annullato"))
    }

    @Test
    fun `la cura cpu persiste e annulla il pool alternativo scelto`() {
        val nominalSlot = SpellSlotResourceId.standard(1).id()
        val alternateSlot = SpellSlotResourceId.pact(2).id()
        val persisted = mutableListOf<Pair<String, Map<String, Int>>>()
        val session = enemyCpuAlternativeSlotHealingSession()
        val model = BattleViewModel(
            session,
            resourceSink = CombatResourceSink { definitionId, resources ->
                persisted.add(definitionId to resources.associate { it.id() to it.spent() })
            },
        )
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        model.playEnemyCpuTurn()

        val spentAfter = model.combatant("healer")!!.resources().associate { it.id() to it.spent() }
        assertEquals(mapOf(nominalSlot to 1, alternateSlot to 1), spentAfter)
        assertEquals(listOf("cpu-healer-def" to spentAfter), persisted)
        assertTrue(model.floating["healer"]?.single()?.text.orEmpty().contains("slot di 2° livello"))

        model.undo()

        val spentBefore = model.combatant("healer")!!.resources().associate { it.id() to it.spent() }
        assertEquals(mapOf(nominalSlot to 1, alternateSlot to 0), spentBefore)
        assertEquals(
            listOf(
                "cpu-healer-def" to spentAfter,
                "cpu-healer-def" to spentBefore,
            ),
            persisted,
        )
    }

    @Test
    fun `se la persistenza cpu fallisce il turno intero viene annullato`() {
        val persistedSpent = mutableListOf<Int>()
        val session = enemyCpuResourceSession()
        val model = BattleViewModel(
            session,
            resourceSink = CombatResourceSink { _, resources ->
                val spent = resources.single { it.id() == CPU_ATTACK_RESOURCE }.spent()
                persistedSpent += spent
                if (spent > 0) error("risorsa CPU non salvata")
            },
        )
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))

        model.playEnemyCpuTurn()

        assertEquals("enemy", model.activeCombatantId)
        assertEquals(20, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, model.combatant("enemy")!!.resources().single().spent())
        assertEquals(listOf(1, 0), persistedSpent)
        assertFalse(model.canUndo)
        assertTrue(model.enemyCpuTurnSuppressed)
        assertTrue(model.message.orEmpty().contains("risorsa CPU non salvata"))
    }

    @Test
    fun `il delta cpu segue il pool alternativo realmente consumato`() {
        val nominalSlot = CombatResourceState("slot-incantesimo-1", "Slot 1", 3, 0)
        val alternateSlot = CombatResourceState("slot-patto-2", "Slot del patto 2", 1, 0)
        val spentAlternateSlot = CombatResourceState("slot-patto-2", "Slot del patto 2", 1, 1)
        val before = mapOf(
            "healer" to ("healer-definition" to listOf(nominalSlot, alternateSlot)),
        )

        val changes = changedEnemyCpuResources(
            before = before,
            // L'ordine cambia intenzionalmente: il delta deve dipendere dai pool, non dalla lista.
            after = mapOf("healer" to listOf(spentAlternateSlot, nominalSlot)),
        )

        val delta = requireNotNull(changes["healer"])
        assertEquals("healer-definition", delta.definitionId)
        assertEquals(listOf(nominalSlot, alternateSlot), delta.before)
        assertEquals(listOf(spentAlternateSlot, nominalSlot), delta.after)
        assertTrue(
            changedEnemyCpuResources(
                before = before,
                after = mapOf("healer" to listOf(alternateSlot, nominalSlot)),
            ).isEmpty(),
        )
    }

    @Test
    fun `le risorse di istanze clone restano locali alla sessione`() {
        val available = CombatResourceState("carica", "Carica", 2, 0)
        val spent = CombatResourceState("carica", "Carica", 2, 1)
        val before = mapOf(
            "clone-a" to ("stessa-definizione" to listOf(available)),
            "clone-b" to ("stessa-definizione" to listOf(available)),
        )

        val changes = changedEnemyCpuResources(
            before = before,
            after = mapOf(
                "clone-a" to listOf(spent),
                "clone-b" to listOf(available),
            ),
        )

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `anche il consumo manuale di un clone resta locale alla sessione`() {
        var sinkCalls = 0
        val model = BattleViewModel(
            clonedActionSurgeSession(),
            resourceSink = CombatResourceSink { _, _ ->
                sinkCalls++
                error("la scheda condivisa non ha un valore corretto da ricevere")
            },
        )

        model.beginAbilityTargeting(ACTION_SURGE_ID)

        assertTrue(model.budget("fighter-1")!!.additionalActionAvailable())
        assertEquals(1, model.combatant("fighter-1")!!.resources().single().spent())
        assertEquals(0, model.combatant("fighter-2")!!.resources().single().spent())
        assertEquals(0, sinkCalls)
        assertNull(model.message)

        model.undo()

        assertEquals(0, model.combatant("fighter-1")!!.resources().single().spent())
        assertEquals(0, sinkCalls)
    }

    private fun extraAttackViewModel(): BattleViewModel {
        val sword = AbilityDefinition.attack(
            "sword",
            "Spada",
            ActivationCost.ACTION,
            100,
            DamageFormula.fixed(DamageType.SLASHING, 1),
        )
        val fighter = ActorDefinition.builder("fighter-extra-def", "Guerriero")
            .armorClass(18)
            .maxHitPoints(30)
            .initiativeScore(20)
            .attacksPerAction(2)
            .abilities(listOf(sword))
            .build()
        val target = ActorDefinition.builder("target-extra-def", "Bersaglio")
            .armorClass(10)
            .maxHitPoints(20)
            .initiativeScore(10)
            .build()
        val session = CombatSession.create("attacco-extra-ui", 4242L)
        session.addCombatant("fighter", fighter)
        session.addCombatant("target", target)
        session.setPartyCombatants(listOf("fighter"))
        session.setInitiative("fighter", 20)
        session.setInitiative("target", 10)
        session.setInitiativeOrder(listOf("fighter", "target"))
        session.markReady()
        session.start()
        return BattleViewModel(session)
    }

    @Test
    fun `azione impetuosa si attiva direttamente persiste la spesa e undo la ripristina`() {
        val persistedSpent = mutableListOf<Int>()
        val model = BattleViewModel(
            actionSurgeSession(),
            resourceSink = CombatResourceSink { _, resources ->
                persistedSpent += resources.single { it.id() == ACTION_SURGE_RESOURCE }.spent()
            },
        )
        val surge = model.abilities("fighter").single()

        assertTrue(model.canAffordAbility("fighter", surge))
        assertEquals("1/1", model.abilityResourceLabel("fighter", surge))

        model.beginAbilityTargeting(ACTION_SURGE_ID)

        assertTrue(model.budget("fighter")!!.additionalActionAvailable())
        assertEquals("0/1", model.abilityResourceLabel("fighter", surge))
        assertEquals(listOf(1), persistedSpent)
        assertNotNull(model.actionResolution)

        model.undo()

        assertFalse(model.budget("fighter")!!.additionalActionAvailable())
        assertEquals("1/1", model.abilityResourceLabel("fighter", surge))
        assertEquals(listOf(1, 0), persistedSpent)
    }

    @Test
    fun `se la persistenza della risorsa fallisce azione impetuosa torna allo stato iniziale`() {
        val model = BattleViewModel(
            actionSurgeSession(),
            resourceSink = CombatResourceSink { _, _ -> error("salvataggio non disponibile") },
        )

        model.beginAbilityTargeting(ACTION_SURGE_ID)

        assertFalse(model.budget("fighter")!!.additionalActionAvailable())
        assertEquals(0, model.combatant("fighter")!!.resources().single().spent())
        assertNull(model.actionResolution)
        assertTrue(model.message.orEmpty().contains("salvataggio non disponibile"))
    }

    @Test
    fun `una cura senza costo non chiama il sink risorse e resta annullabile`() {
        val healing = AbilityDefinition.builder("cura-at-will", "Cura at-will")
            .activationCost(ActivationCost.BONUS_ACTION)
            .resolutionMethod(ResolutionMethod.AUTOMATIC)
            .automationStatus(AutomationStatus.AUTOMATED)
            .healing(HealingDefinition.fixed(HealingTarget.SELF, 4))
            .build()
        var sinkCalls = 0
        val model = BattleViewModel(
            atWillAbilitySession(healing, currentHitPoints = 10),
            resourceSink = CombatResourceSink { _, _ ->
                sinkCalls++
                error("sink non pertinente")
            },
        )

        model.beginAbilityTargeting(healing.id())

        assertEquals(14, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, sinkCalls)
        assertNull(model.message)

        model.undo()
        assertEquals(10, model.combatant("hero")!!.currentHitPoints())
        assertEquals(0, sinkCalls)
    }

    @Test
    fun `una attivazione senza costo non chiama il sink risorse e resta annullabile`() {
        val activation = AbilityDefinition.builder("slancio-at-will", "Slancio at-will")
            .activationCost(ActivationCost.NONE)
            .resolutionMethod(ResolutionMethod.AUTOMATIC)
            .automationStatus(AutomationStatus.AUTOMATED)
            .effect(AbilityEffect.GRANT_NON_MAGIC_ACTION)
            .build()
        var sinkCalls = 0
        val model = BattleViewModel(
            atWillAbilitySession(activation),
            resourceSink = CombatResourceSink { _, _ ->
                sinkCalls++
                error("sink non pertinente")
            },
        )

        model.beginAbilityTargeting(activation.id())

        assertTrue(model.budget("hero")!!.additionalActionAvailable())
        assertEquals(0, sinkCalls)
        assertNull(model.message)

        model.undo()
        assertFalse(model.budget("hero")!!.additionalActionAvailable())
        assertEquals(0, sinkCalls)
    }

    @Test
    fun `la scheda mantiene utilizzabile l arma finche restano attacchi dell azione`() {
        val model = extraAttackViewModel()
        val sword = model.abilities("fighter").single()

        model.beginAbilityTargeting(sword.id())
        model.onCombatantClicked("target")

        assertEquals(1, model.budget("fighter")!!.attacksRemaining())
        assertTrue(model.canAffordAbility("fighter", sword))

        model.beginAbilityTargeting(sword.id())
        model.onCombatantClicked("target")

        assertEquals(0, model.budget("fighter")!!.attacksRemaining())
        assertFalse(model.canAffordAbility("fighter", sword))
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
    fun `la prova generica usa la modalita della barra e non consuma azioni`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!
        val budgetBefore = model.budget(actor)
        model.rollMode = D20Mode.ADVANTAGE

        model.rollAbilityCheck(actor, SaveAbility.WISDOM, 4)

        val event = model.events.last { it.type() == EventType.ABILITY_CHECK_ROLLED }
        assertEquals(actor, event.actorId())
        assertEquals("WISDOM", event.details()["ability"])
        assertEquals("ADVANTAGE", event.details()["mode"])
        assertEquals("4", event.details()["modifier"])
        assertEquals(budgetBefore, model.budget(actor))
        assertNull(model.message)
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
    fun `un attacco senza costo non chiama il sink risorse e resta annullabile`() {
        var sinkCalls = 0
        val model = guaranteedHitViewModel(
            resourceSink = CombatResourceSink { _, _ ->
                sinkCalls++
                error("sink non pertinente")
            },
        )

        model.attack("colpo-certo")

        assertEquals(15, model.combatant("bersaglio")!!.currentHitPoints())
        assertEquals(0, sinkCalls)
        assertNull(model.message)

        model.undo()
        assertEquals(20, model.combatant("bersaglio")!!.currentHitPoints())
        assertEquals(0, sinkCalls)
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
    fun `l anteprima del movimento richiede il pulsante e non modifica il budget`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!

        // Senza un token sulla griglia il comando non inventa un'anteprima.
        model.toggleMovementReach()
        assertFalse(model.movementReachVisible)

        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        val feetBefore = model.budget(actor)!!.movementRemainingFeet()
        val squaresBefore = model.movementSquaresRemaining(actor)

        model.toggleMovementReach()

        assertTrue(model.movementReachVisible)
        assertEquals(feetBefore, model.budget(actor)!!.movementRemainingFeet())
        assertEquals(squaresBefore, model.movementSquaresRemaining(actor))

        model.toggleMovementReach()
        assertFalse(model.movementReachVisible)
    }

    @Test
    fun `riclassificare una capacita la sposta subito nel tavolo gia' aperto`() {
        // Il combattente e' fotografato all'inizio dell'incontro: qui si verifica
        // che la classificazione arrivi comunque dal Compendio, a partita aperta.
        var passiveIds = emptySet<String>()
        val model = BattleViewModel(
            SampleEncounter.startedSession(seed = 4242L),
            passiveProvider = { abilityId -> if (abilityId in passiveIds) true else null },
        )
        val actor = model.activeCombatantId!!
        val ability = model.activeAbilities(actor).first()

        assertTrue(model.passiveAbilities(actor).none { it.id() == ability.id() })

        passiveIds = setOf(ability.id())

        assertTrue(model.passiveAbilities(actor).any { it.id() == ability.id() })
        assertTrue(model.activeAbilities(actor).none { it.id() == ability.id() })
    }

    @Test
    fun `il passaggio del mouse mostra l anteprima e la restituisce com era`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!

        // Senza token sulla griglia non c'e' niente da anticipare.
        model.setMovementReachHovered(true)
        assertFalse(model.movementReachShown)
        model.setMovementReachHovered(false)

        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        val feetBefore = model.budget(actor)!!.movementRemainingFeet()

        model.setMovementReachHovered(true)
        assertTrue(model.movementReachShown)
        // Passare col mouse non e' una scelta: l'interruttore resta spento e il
        // budget non si tocca.
        assertFalse(model.movementReachVisible)
        assertEquals(feetBefore, model.budget(actor)!!.movementRemainingFeet())

        model.setMovementReachHovered(false)
        assertFalse(model.movementReachShown)

        // Con l'interruttore acceso, uscire col puntatore non lo spegne.
        model.toggleMovementReach()
        model.setMovementReachHovered(true)
        model.setMovementReachHovered(false)
        assertTrue(model.movementReachVisible)
        assertTrue(model.movementReachShown)
    }

    @Test
    fun `il cambio turno spegne l anteprima del movimento`() {
        val model = viewModel()
        val actor = model.activeCombatantId!!
        model.place(actor, 2, 2, model.squaresPerSideFor(actor))
        model.toggleMovementReach()
        assertTrue(model.movementReachVisible)

        model.endTurn()

        assertFalse(model.movementReachVisible)
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
        model.toggleMovementReach()
        assertTrue(model.movementReachVisible)
        model.move(actor, (2 + reach).coerceAtMost(grid.columns() - 1), 2)

        assertEquals(0, model.movementSquaresRemaining(actor))
        assertFalse(model.movementReachVisible)
    }
}
