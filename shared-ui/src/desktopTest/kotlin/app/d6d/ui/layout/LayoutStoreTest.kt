package app.d6d.ui.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * La disposizione dell'interfaccia deve sopravvivere alla chiusura.
 *
 * Non basta scriverla: dopo la ricarica i pannelli devono ritrovare larghezza,
 * collasso e posizione delle targhe, e un file assente o rovinato non deve mai
 * impedire l'avvio.
 */
class LayoutStoreTest {

    @TempDir
    lateinit var directory: Path

    private fun store() = LayoutStore(directory.resolve("layout.json"))

    @Test
    fun `un file assente ricade sui valori predefiniti`() {
        assertEquals(UiLayout(), store().load())
    }

    @Test
    fun `la disposizione salvata torna identica dopo la ricarica`() {
        val saved = UiLayout(
            railWidthDp = 120f,
            railOpen = false,
            squadWidthDp = 300f,
            enemyWidthDp = 260f,
            logHeightDp = 180f,
            logCollapsed = true,
            turnsCollapsed = true,
            turnsShowInitiative = false,
            topBarHeightDp = 90f,
            commandBarHeightDp = 220f,
            commandsCollapsed = true,
            mapCellSizeDp = 70f,
            mapShowGrid = false,
            targetPlate = PlateFraction(0.25f, 0.75f),
            activePlate = PlateFraction(0.5f, 0.5f),
        )

        val store = store()
        store.save(saved)

        assertEquals(saved, store.load())
    }

    @Test
    fun `un file danneggiato non impedisce l'avvio`() {
        val file = directory.resolve("layout.json")
        Files.createDirectories(directory)
        Files.writeString(file, "{ questo non è json valido")

        assertEquals(UiLayout(), store().load())
    }

    @Test
    fun `una preferenza precedente mantiene la visualizzazione con iniziativa`() {
        val file = directory.resolve("layout.json")
        Files.createDirectories(directory)
        Files.writeString(file, """{"turnsCollapsed":false}""")

        val loaded = store().load()

        assertFalse(loaded.turnsCollapsed)
        assertTrue(loaded.turnsShowInitiative)
    }

    @Test
    fun `un file danneggiato recupera l'ultima disposizione dal backup`() {
        val file = directory.resolve("layout.json")
        val store = store()
        val recoverable = UiLayout(railWidthDp = 144f, logCollapsed = true)
        store.save(recoverable)
        store.save(UiLayout(railWidthDp = 180f))
        Files.writeString(file, "{ preferenze interrotte")

        assertEquals(recoverable, store.load())
    }

    @Test
    fun `un file principale mancante recupera l'ultima disposizione dal backup`() {
        val file = directory.resolve("layout.json")
        val store = store()
        val recoverable = UiLayout(railWidthDp = 144f, logCollapsed = true)
        store.save(recoverable)
        store.save(UiLayout(railWidthDp = 180f))
        Files.delete(file)

        assertEquals(recoverable, store.load())
    }

    @Test
    fun `i valori fuori scala vengono riportati entro i limiti`() {
        val store = store()
        store.save(
            UiLayout(
                railWidthDp = 5_000f,
                squadWidthDp = -40f,
                mapCellSizeDp = Float.NaN,
                targetPlate = PlateFraction(4f, -2f),
            ),
        )

        val loaded = store.load()

        assertTrue(loaded.railWidthDp <= 400f, "la barra non deve superare il limite")
        assertTrue(loaded.squadWidthDp >= 100f, "la colonna non deve andare sotto il minimo")
        assertEquals(46f, loaded.mapCellSizeDp, "un NaN deve ricadere sul valore predefinito")
        assertEquals(PlateFraction(1f, 0f), loaded.targetPlate, "la frazione resta fra 0 e 1")
    }

    @Test
    fun `lo zoom molto ampio della mappa sopravvive alla ricarica`() {
        val store = store()

        store.save(UiLayout(mapCellSizeDp = 1f))

        assertEquals(1f, store.load().mapCellSizeDp)
    }

    @Test
    fun `senza posizione salvata le targhe restano prive di offset`() {
        val loaded = store().load()
        assertNull(loaded.targetPlate)
        assertNull(loaded.activePlate)
        assertFalse(loaded.logCollapsed)
    }

    @Test
    fun `un clic attraversa le tre modalita dell ordine dei turni`() {
        val state = UiLayoutState(UiLayout(turnsCollapsed = true))

        assertEquals(TurnOrderDisplayMode.HIDDEN, state.turnOrderDisplayMode)

        state.cycleTurnOrderDisplayMode()
        assertEquals(TurnOrderDisplayMode.ORDER_ONLY, state.turnOrderDisplayMode)

        state.cycleTurnOrderDisplayMode()
        assertEquals(TurnOrderDisplayMode.WITH_INITIATIVE, state.turnOrderDisplayMode)

        state.cycleTurnOrderDisplayMode()
        assertEquals(TurnOrderDisplayMode.HIDDEN, state.turnOrderDisplayMode)
    }
}
