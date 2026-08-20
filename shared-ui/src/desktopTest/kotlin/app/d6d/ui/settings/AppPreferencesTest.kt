package app.d6d.ui.settings

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import app.d6d.i18n.AppLanguage
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import app.d6d.ui.state.EnemyCpuSpeed
import app.d6d.board.StampKind
import app.d6d.board.TemplateShape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Le preferenze dell'applicazione devono sopravvivere alla chiusura.
 *
 * Valgono le stesse garanzie della disposizione: un file assente, rovinato o
 * scritto da una versione diversa costa i valori predefiniti, mai l'avvio.
 */
class AppPreferencesTest {

    @TempDir
    lateinit var directory: Path

    private fun store() = PreferencesStore(directory.resolve("preferences.json"))

    @Test
    fun `un file assente ricade sui valori predefiniti`() {
        val loaded = store().load()

        assertEquals(EnemyCpuSpeed.NORMAL, loaded.speedOrDefault())
        assertTrue(loaded.animatedBackdrop)
    }

    @Test
    fun `le preferenze salvate tornano identiche dopo la ricarica`() {
        val saved = AppPreferences(
            enemyCpuSpeed = EnemyCpuSpeed.INSTANT.name,
            animatedBackdrop = false,
        )

        val store = store()
        store.save(saved)

        assertEquals(saved, store.load())
    }

    @Test
    fun `un file danneggiato non impedisce l'avvio`() {
        val file = directory.resolve("preferences.json")
        Files.createDirectories(directory)
        Files.writeString(file, "{ questo non è json valido")

        assertEquals(AppPreferences(), store().load())
    }

    @Test
    fun `un file danneggiato recupera le ultime preferenze dal backup`() {
        val file = directory.resolve("preferences.json")
        val store = store()
        val recoverable = AppPreferences(enemyCpuSpeed = EnemyCpuSpeed.SLOW.name)
        store.save(recoverable)
        store.save(AppPreferences(enemyCpuSpeed = EnemyCpuSpeed.FAST.name))
        Files.writeString(file, "{ preferenze interrotte")

        assertEquals(recoverable, store.load())
    }

    @Test
    fun `un ritmo che non esiste piu ricade su quello predefinito`() {
        val file = directory.resolve("preferences.json")
        Files.createDirectories(directory)
        // Un nome scritto da una versione che aveva altri ritmi: il file resta
        // leggibile e l'app riparte da Normale invece di rifiutarsi di aprire.
        Files.writeString(file, """{"enemyCpuSpeed":"GLACIALE","animatedBackdrop":false}""")

        val loaded = store().load()

        assertEquals(EnemyCpuSpeed.NORMAL, loaded.speedOrDefault())
        assertFalse(loaded.animatedBackdrop, "le altre preferenze restano quelle salvate")
    }

    @Test
    fun `un campo aggiunto in futuro non impedisce la lettura`() {
        val file = directory.resolve("preferences.json")
        Files.createDirectories(directory)
        Files.writeString(file, """{"enemyCpuSpeed":"FAST","volumeDeiTuoni":0.4}""")

        assertEquals(EnemyCpuSpeed.FAST, store().load().speedOrDefault())
    }

    @Test
    fun `le preferenze del Lucido vengono sanificate e persistono`() {
        val store = store()
        store.save(
            AppPreferences(
                boardColorArgb = 0xff44aacc.toInt(),
                boardStrokeWidth = 99f,
                boardTemplateShape = TemplateShape.CONE.name,
                boardStampKind = StampKind.DOOR.name,
            ),
        )

        val loaded = store.load()

        assertEquals(0xff44aacc.toInt(), loaded.boardColorArgb)
        assertEquals(2f, loaded.boardStrokeWidth)
        assertEquals(TemplateShape.CONE, loaded.templateShapeOrDefault())
        assertEquals(StampKind.DOOR, loaded.stampKindOrDefault())
    }

    @Test
    fun `lo stato vivo scrive su disco solo cio che e cambiato`() {
        val store = store()
        val state = AppPreferencesState(store = store)

        // Nessuna modifica: il file non deve nemmeno nascere.
        state.persist()
        assertFalse(Files.exists(directory.resolve("preferences.json")))

        state.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        state.animatedBackdrop = false
        state.persist()

        val reloaded = AppPreferencesState(initial = store.load())
        assertEquals(EnemyCpuSpeed.SLOW, reloaded.enemyCpuSpeed)
        assertFalse(reloaded.animatedBackdrop)
    }

    @Test
    fun `il ripristino riporta ogni preferenza al valore di fabbrica`() {
        val state = AppPreferencesState(
            initial = AppPreferences(
                enemyCpuSpeed = EnemyCpuSpeed.INSTANT.name,
                animatedBackdrop = false,
            ),
        )

        state.resetToDefaults()

        assertEquals(EnemyCpuSpeed.NORMAL, state.enemyCpuSpeed)
        assertTrue(state.animatedBackdrop)
    }
}

/**
 * La lingua scelta deve arrivare al disco.
 *
 * Lo switch nelle Impostazioni ridipinge lo schermo all'istante, il che rende
 * facile crederlo funzionante anche quando non salva nulla: la prova che conta
 * e' il giro completo — scelgo, scrivo, rileggo.
 */
class LanguagePreferenceTest {

    @TempDir
    lateinit var directory: Path

    private fun store() = PreferencesStore(directory.resolve("preferences.json"))

    @Test
    fun `una lingua mai scelta non viene scritta e ricade sul sistema`() {
        val state = AppPreferencesState(store = store())

        assertEquals("", state.snapshot().language)
        assertEquals(AppLanguage.systemDefault(), state.language)
    }

    @Test
    fun `la lingua scelta finisce nella fotografia e nel file`() {
        val store = store()
        val state = AppPreferencesState(store = store)

        state.chooseLanguage(AppLanguage.ITALIAN)

        assertEquals("ITALIAN", state.snapshot().language)

        state.persist()
        assertEquals(AppLanguage.ITALIAN, store.load().languageOrSystemDefault())
    }

    @Test
    fun `la scelta sopravvive alla chiusura e alla riapertura`() {
        val store = store()
        AppPreferencesState(store = store).apply {
            chooseLanguage(AppLanguage.ENGLISH)
            persist()
        }

        val reopened = AppPreferencesState(store.load(), store)

        assertEquals(AppLanguage.ENGLISH, reopened.language)
        // Riaprendo, la scelta resta esplicita: non deve tornare a seguire il
        // sistema solo perche' nessuno l'ha ritoccata in questa sessione.
        assertEquals("ENGLISH", reopened.snapshot().language)
    }
}

/**
 * Il salvataggio automatico osserva davvero la lingua.
 *
 * La radice dell'applicazione scrive su disco reagendo a `snapshotFlow { snapshot() }`,
 * e un flusso del genere si iscrive soltanto agli stati che il blocco **legge**.
 * Una fotografia che salta la lingua — perche' un `if` cortocircuita, o perche' il
 * campo non e' uno stato osservabile — cambia comunque l'interfaccia, che la legge
 * per conto proprio, e non salva niente. E' esattamente il difetto che questo caso
 * riproduce: senza di lui si vede solo lo schermo cambiare e lo si crede fatto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageAutosaveTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `scegliere la lingua produce una nuova fotografia da salvare`() = runTest {
        val state = AppPreferencesState(store = PreferencesStore(directory.resolve("p.json")))
        val seen = mutableListOf<AppPreferences>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            snapshotFlow { state.snapshot() }.collect { seen += it }
        }
        runCurrent()

        state.chooseLanguage(AppLanguage.ITALIAN)
        Snapshot.sendApplyNotifications()
        runCurrent()
        job.cancel()

        assertTrue(
            seen.any { it.language == "ITALIAN" },
            "il cambio di lingua non ha prodotto una fotografia nuova: viste $seen",
        )
    }
}
