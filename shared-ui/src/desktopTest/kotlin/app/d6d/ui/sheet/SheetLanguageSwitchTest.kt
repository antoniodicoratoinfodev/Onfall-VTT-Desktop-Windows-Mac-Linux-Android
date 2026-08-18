package app.d6d.ui.sheet

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ClassLevelState
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.SheetStore
import app.d6d.ui.content.SessionTemplates
import app.d6d.ui.i18n.AppLocale
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Cambiare lingua non e' una modifica alla scheda.
 *
 * Il difetto che questi controlli sorvegliano e' sottile: tradurre la scheda
 * aperta senza tradurre anche la copia dentro l'archivio la faceva risultare
 * *modificata*, perche' `isDirty` confronta le due. Chi avesse solo premuto
 * l'interruttore della lingua si trovava un avviso di modifiche non salvate e la
 * navigazione bloccata, su un personaggio che non aveva toccato.
 */
class SheetLanguageSwitchTest {

    private fun rogue(id: String, name: String) = CharacterSheet(
        id = id,
        characterName = name,
        className = "Ladro 3",
        background = "Criminale",
        toolProficiencies = "Arnesi da scasso",
        progression = CharacterProgression(
            backgroundId = "srd521-it:background:criminale",
            classLevels = listOf(ClassLevelState(CharacterClassId.ROGUE, 3)),
        ),
    )

    /**
     * L'archivio non parte vuoto: la prima apertura vi semina i personaggi
     * modello. I controlli qui sotto cercano quindi le proprie schede per
     * identificativo, invece di dare per scontato di essere sole.
     */
    private fun modelWith(directory: Path, vararg sheets: CharacterSheet): SheetViewModel {
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        sheets.forEach { sheet ->
            model.character = sheet
            model.save()
        }
        model.load()
        model.selectCharacter(sheets.first().id)
        return model
    }

    private fun SheetViewModel.characterNamed(id: String) =
        library.characters.first { it.id == id }

    @Test
    fun `il solo cambio lingua non rende la scheda modificata`(@TempDir directory: Path) = runTest {
        val model = modelWith(directory, rogue("pg-1", "Aria"))
        assertFalse(model.isDirty, "la scheda risulta modificata gia' prima del cambio lingua")

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        assertEquals("Rogue 3", model.character.className)
        assertFalse(model.isDirty, "il solo cambio di lingua ha reso la scheda modificata")
    }

    @Test
    fun `anche gli altri personaggi seguono la lingua`(@TempDir directory: Path) = runTest {
        val model = modelWith(directory, rogue("pg-1", "Aria"), rogue("pg-2", "Bruno"))

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        assertEquals("Rogue 3", model.characterNamed("pg-1").className)
        assertEquals("Rogue 3", model.characterNamed("pg-2").className)
        // E anche i personaggi modello seminati dall'archivio, che nessuno ha
        // aperto: la lingua vale per tutto cio' che e' in memoria.
        assertTrue(
            model.library.characters.none { it.contentLanguage == AppLanguage.ITALIAN },
            "sono rimaste schede nella lingua precedente",
        )
    }

    @Test
    fun `selezionare un altro personaggio dopo il cambio non lo riporta indietro`(
        @TempDir directory: Path,
    ) = runTest {
        val model = modelWith(directory, rogue("pg-1", "Aria"), rogue("pg-2", "Bruno"))

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()
        model.selectCharacter("pg-2")

        assertEquals("Rogue 3", model.character.className)
        assertEquals("Criminal", model.character.background)
        assertFalse(model.isDirty, "la selezione ha reintrodotto la scheda non tradotta")
    }

    @Test
    fun `un archivio italiano riaperto con la preferenza inglese si mostra in inglese`(
        @TempDir directory: Path,
    ) = runTest {
        modelWith(directory, rogue("pg-1", "Aria"))

        // Riavvio: nuovo view model sulla stessa cartella, lingua gia' inglese.
        AppLocale.use(AppLanguage.ENGLISH)
        val reopened = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        reopened.load()
        reopened.selectCharacter("pg-1")

        assertEquals("Rogue 3", reopened.character.className)
        assertEquals("Criminal", reopened.character.background)
        assertFalse(reopened.isDirty, "l'archivio tradotto al caricamento risulta modificato")
    }

    @Test
    fun `anche una creatura nuova nasce nella lingua corrente`(@TempDir directory: Path) = runTest {
        AppLocale.use(AppLanguage.ENGLISH)
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        model.requestKind(SheetKind.MOSTRO)
        model.newSheet()
        assertEquals(AppLanguage.ENGLISH, model.monster.contentLanguage)
    }

    @Test
    fun `la narrativa dei personaggi modello segue la lingua, quella riscritta no`(
        @TempDir directory: Path,
    ) = runTest {
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        val template = model.library.characters.first { it.species.isNotBlank() }
        val italianSpecies = template.species

        // Un secondo personaggio, dalla stessa ricetta ma con la storia riscritta.
        model.selectCharacter(template.id)
        model.character = model.character.copy(backstory = "La mia versione dei fatti")
        model.save()
        model.load()

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        val migrated = model.library.characters.first { it.id == template.id }
        // La specie era ancora quella della ricetta: segue la lingua.
        assertNotEquals(italianSpecies, migrated.species)
        // La storia no: l'ha riscritta chi gioca.
        assertEquals("La mia versione dei fatti", migrated.backstory)
    }

    /** Lo stesso personaggio incluso, costruito da zero nella lingua indicata. */
    private fun seededIn(language: AppLanguage, id: String): CharacterSheet =
        SessionTemplates.of(language).all
            .flatMap { it.party }
            .first { it.id == id }

    @Test
    fun `un modello tradotto coincide con lo stesso modello nato in inglese`(
        @TempDir directory: Path,
    ) = runTest {
        // Il difetto era che la traduzione narrativa copriva specie, allineamento,
        // aspetto e storia, ma non il nome ne' il background ne' le voci di
        // dotazione della ricetta: restavano «Tarvos di Pietrafredda» e
        // «Sentinella di frontiera» in mezzo a una scheda inglese.
        //
        // Cercare «frammenti italiani» — un accento, un « di » — non era pero'
        // una prova: passava anche una traduzione a meta', purche' cio' che
        // restava indietro fosse scritto senza accenti. Il termine di paragone
        // giusto e' l'unico esatto che esista: la stessa ricetta costruita in
        // inglese. Se i due non coincidono campo per campo, qualcosa e' rimasto
        // nella lingua di prima — e il messaggio dice subito cosa.
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        val before = model.library.characters.first { it.id == "pg-tarvos" }

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()
        val after = model.library.characters.first { it.id == "pg-tarvos" }
        val born = seededIn(AppLanguage.ENGLISH, "pg-tarvos")

        assertNotEquals(before.characterName, after.characterName)
        assertEquals(born.characterName, after.characterName)
        assertEquals(born.background, after.background)
        assertEquals(born.equipment, after.equipment)
        assertEquals(born.species, after.species)
        assertEquals(born.alignment, after.alignment)
        assertEquals(born.languages, after.languages)
        assertEquals(born.className, after.className)
        assertEquals(born.subclass, after.subclass)
        assertEquals(born.toolProficiencies, after.toolProficiencies)
        assertEquals(born.weaponProficiencies, after.weaponProficiencies)
        assertEquals(born.weapons.map { it.name }, after.weapons.map { it.name })
        assertEquals(born.weapons.map { it.note }, after.weapons.map { it.note })
    }

    @Test
    fun `la sorgente di un effetto segue la lingua`(@TempDir directory: Path) = runTest {
        // La sorgente e' il nome del privilegio che concede l'effetto, e la
        // scheda la mostra sotto il valore che modifica: lasciarla indietro
        // faceva leggere «Movimento senza armatura» sotto una Classe Armatura
        // inglese. Non e' un campo che l'utente scrive, quindi qui non c'e'
        // niente da preservare: deve seguire la lingua, tutta.
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        val id = model.library.characters
            .first { it.progression.effects.any { effect -> effect.source.isNotBlank() } }
            .id
        val before = model.library.characters.first { it.id == id }

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        val after = model.library.characters.first { it.id == id }
        val born = seededIn(AppLanguage.ENGLISH, id)
        assertNotEquals(
            before.progression.effects.map { it.source },
            after.progression.effects.map { it.source },
            "nessuna sorgente e' cambiata: la prova non guarderebbe nulla",
        )
        assertEquals(
            born.progression.effects.map { it.source },
            after.progression.effects.map { it.source },
        )
        // E i nomi dei pozzi di risorse, che stanno accanto e si rigenerano.
        assertEquals(
            born.progression.resourcePools.map { it.name },
            after.progression.resourcePools.map { it.name },
        )
    }

    @Test
    fun `andata e ritorno lascia intatto cio' che l'utente ha scritto`(
        @TempDir directory: Path,
    ) = runTest {
        // La regola e' che si sostituisce solo cio' che coincide *per intero* con
        // una voce canonica. Il giro completo la mette alla prova due volte: se
        // una passata riscrivesse un campo personalizzato, tornare indietro non
        // lo rimetterebbe a posto.
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        model.selectCharacter("pg-tarvos")
        model.character = model.character.copy(
            characterName = "Tarvos il Testardo",
            background = "Sentinella con licenza",
            equipment = "Corda impeciata, un dado truccato",
        )
        model.save()
        model.load()
        val italian = model.library.characters.first { it.id == "pg-tarvos" }

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()
        val english = model.library.characters.first { it.id == "pg-tarvos" }
        assertEquals("Tarvos il Testardo", english.characterName)
        assertEquals("Sentinella con licenza", english.background)
        assertEquals("Corda impeciata, un dado truccato", english.equipment)
        // Il resto invece ha seguito la lingua.
        assertEquals(seededIn(AppLanguage.ENGLISH, "pg-tarvos").className, english.className)

        AppLocale.use(AppLanguage.ITALIAN)
        model.onLanguageChanged()
        val back = model.library.characters.first { it.id == "pg-tarvos" }
        assertEquals(italian, back, "il giro di andata e ritorno non e' tornato al punto di partenza")
    }

    @Test
    fun `le creature incluse seguono la lingua, quelle modificate no`(
        @TempDir directory: Path,
    ) = runTest {
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        val included = model.library.monsters.first()
        val mine = included.copy(id = "mia-creatura", name = "Il mio orco")
        model.requestKind(SheetKind.MOSTRO)
        model.monster = mine
        model.save()
        model.load()

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        // Quella inclusa e non toccata si rigenera nell'altra lingua...
        val regenerated = model.library.monsters.first { it.id == included.id }
        assertEquals(AppLanguage.ENGLISH, regenerated.contentLanguage)
        assertNotEquals(included.name, regenerated.name)
        // ...quella modificata resta di chi l'ha scritta.
        val untouched = model.library.monsters.first { it.id == "mia-creatura" }
        assertEquals("Il mio orco", untouched.name)
    }

    @Test
    fun `un mostro aperto attraversa il cambio lingua senza risultare modificato`(
        @TempDir directory: Path,
    ) = runTest {
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        model.requestKind(SheetKind.MOSTRO)
        model.monster = MonsterStatBlock(id = "orco", name = "Orco")
        model.save()
        model.load()
        model.requestKind(SheetKind.MOSTRO)
        model.selectMonster("orco")
        assertFalse(model.isDirty)

        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        assertFalse(model.isDirty, "il cambio lingua ha reso modificato un mostro non toccato")
        assertEquals("Orco", model.monster.name)
    }

    @Test
    fun `la migrazione linguistica e' atomica e completa`(@TempDir directory: Path) = runTest {
        // Tenere la traduzione solo in memoria non era piu' prudente, era
        // incoerente: ogni salvataggio riscrive tutta la libreria, quindi la
        // traduzione finiva su disco lo stesso, per meta' e per caso. Scritta
        // tutta e subito, archivio e memoria non possono divergere.
        val model = modelWith(directory, rogue("pg-1", "Aria"))
        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        val onDisk = SheetStore(directory.resolve("schede.json")).load()
        assertTrue(
            onDisk.characters.none { it.contentLanguage == AppLanguage.ITALIAN },
            "l'archivio su disco e' rimasto a meta' fra due lingue",
        )
        assertEquals("Rogue 3", onDisk.characters.first { it.id == "pg-1" }.className)
    }

    @Test
    fun `salvare un mostro non lascia l'archivio a meta'`(@TempDir directory: Path) = runTest {
        val model = modelWith(directory, rogue("pg-1", "Aria"))
        AppLocale.use(AppLanguage.ENGLISH)
        model.onLanguageChanged()

        model.requestKind(SheetKind.MOSTRO)
        model.monster = MonsterStatBlock(id = "orco", name = "Orc")
        model.save()

        val onDisk = SheetStore(directory.resolve("schede.json")).load()
        assertTrue(onDisk.characters.none { it.contentLanguage == AppLanguage.ITALIAN })
    }

    @Test
    fun `una creatura personalizzata non fa riscrivere l'archivio a ogni avvio`(
        @TempDir directory: Path,
    ) = runTest {
        // Il difetto: l'allineamento decideva guardando il *marcatore*. Una
        // creatura che l'utente ha modificato non si rigenera e conserva il
        // proprio, quindi risultava «da allineare» per sempre: l'archivio veniva
        // riscritto a ogni apertura, e il backup ruotava su una modifica che non
        // c'era. Si traduce prima e si confronta poi.
        val store = SheetStore(directory.resolve("schede.json"))
        val model = SheetViewModel(store = store, loadOnCreate = false)
        model.load()
        val included = model.library.monsters.first()
        model.requestKind(SheetKind.MOSTRO)
        model.monster = included.copy(id = "mia-creatura", name = "Il mio orco")
        model.save()

        // Primo avvio in inglese: qui una riscrittura ci sta, l'archivio e' italiano.
        AppLocale.use(AppLanguage.ENGLISH)
        SheetViewModel(store = store, loadOnCreate = false).load()
        val archive = directory.resolve("schede.json")
        val backup = directory.resolve("schede.json.bak")
        val settled = archive.readText()
        val settledBackup = backup.readText()

        // Secondo avvio, stessa lingua: non deve toccare neppure il backup. Il
        // solo confronto del file principale non basta, perche' una scrittura
        // ridondante vi rimette gli stessi byte ma ruota comunque il `.bak`.
        SheetViewModel(store = store, loadOnCreate = false).load()
        assertEquals(
            settled,
            archive.readText(),
            "il riavvio ha riscritto l'archivio senza che nulla fosse cambiato",
        )
        assertEquals(
            settledBackup,
            backup.readText(),
            "il riavvio ha ruotato il backup senza che nulla fosse cambiato",
        )
    }

    @Test
    fun `un personaggio creato in inglese non viene marcato italiano`(
        @TempDir directory: Path,
    ) = runTest {
        // Il difetto: una scheda nuova ereditava il predefinito del modello —
        // italiano, giusto per i salvataggi vecchi — anche se nasceva in inglese.
        // Salvata cosi', al riavvio la ritraduzione la peggiorava invece di
        // sistemarla, perche' credeva di partire dall'italiano.
        AppLocale.use(AppLanguage.ENGLISH)
        val model = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        model.load()
        model.newSheet()
        model.character = model.character.copy(characterName = "Ash", className = "Rogue 3")
        assertEquals(AppLanguage.ENGLISH, model.character.contentLanguage)
        model.save()

        // Riavvio in italiano: il testo inglese deve tradursi, non restare.
        AppLocale.use(AppLanguage.ITALIAN)
        val reopened = SheetViewModel(store = SheetStore(directory.resolve("schede.json")), loadOnCreate = false)
        reopened.load()
        val migrated = reopened.library.characters.first { it.characterName == "Ash" }
        assertEquals(AppLanguage.ITALIAN, migrated.contentLanguage)
        assertEquals("Ladro 3", migrated.className)
    }
}
