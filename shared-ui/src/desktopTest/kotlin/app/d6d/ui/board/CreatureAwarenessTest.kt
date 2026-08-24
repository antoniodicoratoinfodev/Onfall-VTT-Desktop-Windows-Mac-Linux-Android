package app.d6d.ui.board

import app.d6d.board.VisionSettings
import app.d6d.board.WallMask
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement
import app.d6d.engine.CombatSession
import app.d6d.ui.state.BattleViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'attivazione vista dal lato dell'interfaccia: chi tiene i muri e i raggi è il
 * Lucido, quindi è qui — non nel motore — che si decide chi si è accorto di chi.
 */
class CreatureAwarenessTest {

    private val columns = 40
    private val rows = 12

    // --- la regola pura --------------------------------------------------------

    @Test
    fun bastaCheUnoDeiDueVedaLAltro() {
        val walls = WallMask.empty(columns, rows)
        val scout = viewer("scout", 0, 0, radius = 12)
        // Il goblin è cieco: non vede nessuno, ma resta guardabile.
        val goblin = viewer("goblin", 8, 0, radius = 0)
        val troll = viewer("troll", 30, 0, radius = 0)

        val noticed = awareOfParty(listOf(scout), listOf(goblin, troll), walls)

        assertEquals(setOf("goblin"), noticed)
    }

    @Test
    fun anchePerUnaSolaCreaturaCheGuardaDaLontano() {
        val walls = WallMask.empty(columns, rows)
        // La squadra ha lo sguardo corto, il lupo no: è lui ad accorgersi di loro.
        val hero = viewer("hero", 0, 0, radius = 2)
        val wolf = viewer("wolf", 9, 0, radius = 12)

        assertEquals(setOf("wolf"), awareOfParty(listOf(hero), listOf(wolf), walls))
    }

    @Test
    fun ilMuroTieneLaCreaturaAlSuoPosto() {
        var walls = WallMask.empty(columns, rows)
        for (row in 0 until rows) walls = walls.withCell(5, row, true)

        val noticed = awareOfParty(
            listOf(viewer("hero", 0, 0, radius = 12)),
            listOf(viewer("goblin", 8, 0, radius = 12)),
            walls,
        )

        assertTrue(noticed.isEmpty())
    }

    // --- la regola dentro il tavolo --------------------------------------------

    @Test
    fun aInizioCombattimentoRestaInattivoSoloChiNonVedeNessuno() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)

        model.start()

        assertFalse(model.isDormant("goblin"), "otto caselle: dentro i sessanta piedi")
        assertTrue(model.isDormant("troll"), "trenta caselle: nessuno lo ha ancora visto")
        assertFalse(model.isDormant("hero"), "la squadra non ha un'attivazione da aspettare")
    }

    @Test
    fun avvicinarsiLoSveglia() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()
        assertTrue(model.isDormant("troll"))

        model.reposition("hero", 20, 0)

        assertFalse(model.isDormant("troll"))
    }

    @Test
    fun dietroIlMuroNonSiSvegliaNemmenoDaVicino() {
        var walls = WallMask.empty(columns, rows)
        for (row in 0 until rows) walls = walls.withCell(25, row, true)
        val model = mapped(goblinColumn = 8, trollColumn = 30, walls = walls)
        model.start()

        model.reposition("hero", 24, 0)

        assertTrue(model.isDormant("troll"))
    }

    @Test
    fun unaForzaturaManualeNonNascondeUnaCreaturaGiaVisibile() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()
        assertFalse(model.isDormant("goblin"))

        model.setDormant("goblin", true)

        assertFalse(model.isDormant("goblin"))
    }

    @Test
    fun spegnereLAttivazioneSvegliaTutti() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()
        assertTrue(model.isDormant("troll"))

        model.awarenessEnabled = false

        assertTrue(model.dormantCombatantIds.isEmpty())

        // E riaccenderla riaddormenta chi in quel momento non vede e non è visto.
        model.awarenessEnabled = true
        assertTrue(model.isDormant("troll"))
    }

    @Test
    fun senzaAttivazioneNessunoRestaFermo() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.awarenessEnabled = false

        model.start()

        assertTrue(model.dormantCombatantIds.isEmpty())
    }

    @Test
    fun unaCreaturaToltaDallaMappaTornaAttiva() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()
        assertTrue(model.isDormant("troll"))

        // Fuori dalla mappa nessuno può accorgersi di lei: lasciarla inattiva la
        // toglierebbe dal combattimento per sempre.
        model.session.removeFromMap("troll")
        model.sync()

        assertFalse(model.isDormant("troll"))
    }

    @Test
    fun togliereDallaMappaLUnicoOcchioInPiediNonBloccaLincontro() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()
        assertTrue(model.isDormant("troll"))

        model.session.removeFromMap("hero")
        model.sync()

        assertTrue(model.placementOf("hero") == null)
        assertFalse(model.isDormant("troll"))
    }

    @Test
    fun unaCreaturaATerraNonVieneMaiRimessaInAttesa() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()

        model.applyManualDamage("troll", 999)
        assertTrue(model.combatant("troll")!!.defeated())

        model.awarenessEnabled = false
        model.awarenessEnabled = true
        model.setDormant("troll", true)

        assertFalse(model.isDormant("troll"))
    }

    @Test
    fun conLaSquadraInteraATerraNessunoSiSvegliaSoloPerLaVista() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()
        assertTrue(model.isDormant("troll"))

        model.applyManualDamage("hero", 999)

        assertTrue(model.combatant("hero")!!.defeated())
        assertTrue(model.isDormant("troll"))
    }

    @Test
    fun ilRipristinoAspettaIlLucidoPrimaDiControllareLaVista() {
        var walls = WallMask.empty(columns, rows)
        for (row in 0 until rows) walls = walls.withCell(5, row, true)
        val source = mapped(goblinColumn = 30, trollColumn = 8, walls = walls)
        source.start()
        assertTrue(source.isDormant("troll"))

        // In produzione sessione e Lucido vengono adottati in due passi. Senza
        // questa attesa, il sync intermedio vedeva una stanza priva di muri e il
        // risveglio irreversibile corrompeva lo stato appena caricato.
        val reopened = BattleViewModel(source.session)
        reopened.adopt(source.session, mapOf("awareness" to "true"))
        assertTrue(reopened.isDormant("troll"))

        reopened.setBoardSight(walls, VisionSettings.defaults())
        assertTrue(reopened.isDormant("troll"))
    }

    @Test
    fun senzaSquadraSullaMappaNessunoSiAddormenta() {
        val model = mapped(goblinColumn = 8, trollColumn = 30, placeParty = false)

        model.start()

        assertTrue(model.dormantCombatantIds.isEmpty())
    }

    @Test
    fun ilMasterPuoDecidereDaSolo() {
        val model = mapped(goblinColumn = 8, trollColumn = 30)
        model.start()

        model.setDormant("troll", false)
        assertFalse(model.isDormant("troll"))

        // Una creatura che nessuno vede resta dove il master l'ha messa.
        model.setDormant("troll", true)
        assertTrue(model.isDormant("troll"))
    }

    // --- impalcatura -----------------------------------------------------------

    private fun viewer(id: String, column: Int, row: Int, radius: Int) = VisionViewer(
        combatantId = id,
        placement = TokenPlacement.single(id, GridPosition(column, row)),
        radiusSquares = radius,
    )

    /** Eroe all'origine, un goblin vicino e un troll in fondo alla sala. */
    private fun mapped(
        goblinColumn: Int,
        trollColumn: Int,
        walls: WallMask = WallMask.empty(columns, rows),
        placeParty: Boolean = true,
    ): BattleViewModel {
        val session = CombatSession.create("attivazione", 11L)
        session.addCombatant("hero", actor("hero", 40))
        session.addCombatant("goblin", actor("goblin", 20))
        session.addCombatant("troll", actor("troll", 20))
        session.setPartyCombatants(listOf("hero"))
        session.setInitiative("hero", 20)
        session.setInitiative("goblin", 12)
        session.setInitiative("troll", 10)
        session.configureMap(MapGrid.standard(columns, rows))
        if (placeParty) session.placeCombatant("hero", GridPosition(0, 0), 1)
        session.placeCombatant("goblin", GridPosition(goblinColumn, 0), 1)
        session.placeCombatant("troll", GridPosition(trollColumn, 0), 1)
        session.markReady()
        val model = BattleViewModel(session)
        // È la stessa notifica che la Board manda quando il documento cambia; il
        // raggio è quello di mappa, sessanta piedi, e vale anche con la nebbia
        // dipinta a mano.
        model.setBoardSight(walls, VisionSettings.defaults())
        return model
    }

    private fun actor(id: String, hitPoints: Int): ActorDefinition =
        ActorDefinition.builder("$id-definition", id)
            .armorClass(12)
            .maxHitPoints(hitPoints)
            .abilities(
                listOf(
                    AbilityDefinition.builder("strike", "Strike")
                        .activationCost(ActivationCost.ACTION)
                        .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                        .automationStatus(AutomationStatus.AUTOMATED)
                        .attackBonus(100)
                        .rangeFeet(300)
                        .damage(listOf(DamageFormula.fixed(DamageType.FORCE, 4)))
                        .build(),
                ),
            )
            .build()
}
