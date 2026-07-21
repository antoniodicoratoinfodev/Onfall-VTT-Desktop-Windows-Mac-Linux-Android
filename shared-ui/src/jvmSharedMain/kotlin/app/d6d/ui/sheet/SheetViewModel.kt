package app.d6d.ui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.SheetLibrary
import app.d6d.sheet.SheetStore
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.roster.characterSheetFrom
import app.d6d.ui.roster.monsterStatBlockFrom
import java.io.IOException

/** Quale delle due schede si sta redigendo. */
enum class SheetKind(val label: String) {
    PERSONAGGIO("Personaggi"),
    MOSTRO("Mostri"),
}

/**
 * Stato dell'archivio di schede.
 *
 * Personaggi e mostri condividono l'archivio ma non il modulo: la scheda del
 * personaggio e' completa, lo stat block del mostro e' la versione ridotta.
 */
class SheetViewModel(private val store: SheetStore) {

    var library by mutableStateOf(SheetLibrary())
        private set

    var kind by mutableStateOf(SheetKind.PERSONAGGIO)

    var character by mutableStateOf(CharacterSheet())

    var monster by mutableStateOf(MonsterStatBlock())

    var selectedId by mutableStateOf<String?>(null)
        private set

    var status by mutableStateOf<String?>(null)

    /**
     * Notificato dopo un salvataggio riuscito.
     *
     * Il coordinatore del roster lo usa per rigenerare il catalogo da combattimento:
     * la scheda e' autorevole, quindi ogni volta che cambia il catalogo va riderivato.
     */
    var onSaved: ((SheetKind) -> Unit)? = null

    /** Notificato dopo un'eliminazione riuscita. */
    var onDeleted: ((SheetKind, String) -> Unit)? = null

    init {
        load()
    }

    fun load() = guard("Archivio caricato.") {
        library = if (store.exists()) store.load() else seeded().also { store.save(it) }
        when (kind) {
            SheetKind.PERSONAGGIO -> library.characters.firstOrNull()?.let { selectCharacter(it.id) }
            SheetKind.MOSTRO -> library.monsters.firstOrNull()?.let { selectMonster(it.id) }
        }
    }

    fun selectCharacter(id: String) {
        library.characters.firstOrNull { it.id == id }?.let {
            character = it
            selectedId = id
            status = null
        }
    }

    fun selectMonster(id: String) {
        library.monsters.firstOrNull { it.id == id }?.let {
            monster = it
            selectedId = id
            status = null
        }
    }

    fun newSheet() {
        selectedId = null
        val stamp = System.currentTimeMillis()
        when (kind) {
            SheetKind.PERSONAGGIO -> character = CharacterSheet(id = "pg-$stamp")
            SheetKind.MOSTRO -> monster = MonsterStatBlock(id = "mostro-$stamp")
        }
        status = "Nuova scheda: compila e salva."
    }

    fun save() = guard("Scheda salvata.") {
        library = when (kind) {
            SheetKind.PERSONAGGIO -> library.copy(
                characters = library.characters.filterNot { it.id == character.id } + character,
            )

            SheetKind.MOSTRO -> library.copy(
                monsters = library.monsters.filterNot { it.id == monster.id } + monster,
            )
        }
        store.save(library)
        selectedId = if (kind == SheetKind.PERSONAGGIO) character.id else monster.id
        onSaved?.invoke(kind)
    }

    fun delete(id: String) = guard("Scheda eliminata.") {
        val deletedKind = kind
        library = when (kind) {
            SheetKind.PERSONAGGIO -> library.copy(characters = library.characters.filterNot { it.id == id })
            SheetKind.MOSTRO -> library.copy(monsters = library.monsters.filterNot { it.id == id })
        }
        store.save(library)
        selectedId = null
        newSheet()
        onDeleted?.invoke(deletedKind, id)
    }

    /**
     * Aggiorna una scheda senza toccare l'editor aperto.
     *
     * Serve alla propagazione delle correzioni fatte in combattimento: la scheda
     * resta autorevole, quindi un'edit al tavolo deve confluire nella scheda, non
     * solo nel catalogo. Non sposta la selezione ne' la scheda in modifica.
     */
    fun upsertCharacterSilently(sheet: CharacterSheet) = guard("Scheda aggiornata dalla battaglia.") {
        library = library.copy(
            characters = library.characters.filterNot { it.id == sheet.id } + sheet,
        )
        store.save(library)
        if (selectedId == sheet.id && kind == SheetKind.PERSONAGGIO) character = sheet
        onSaved?.invoke(SheetKind.PERSONAGGIO)
    }

    fun upsertMonsterSilently(block: MonsterStatBlock) = guard("Stat block aggiornato dalla battaglia.") {
        library = library.copy(
            monsters = library.monsters.filterNot { it.id == block.id } + block,
        )
        store.save(library)
        if (selectedId == block.id && kind == SheetKind.MOSTRO) monster = block
        onSaved?.invoke(SheetKind.MOSTRO)
    }

    /**
     * Roster iniziale.
     *
     * Rispecchia l'incontro dimostrativo: la squadra come schede complete e gli
     * avversari come stat block, con gli stessi identificatori, cosi' roster e
     * battaglia partono coerenti. Kaelen e il Mastino sono redatti a mano come
     * esempi ricchi; gli altri sono ricostruiti dalla stessa proiezione da
     * combattimento e riproducono statistiche identiche.
     */
    private fun seeded(): SheetLibrary {
        val handwritten = SheetSamples.character()
        val handwrittenMonster = SheetSamples.monster()
        val party = SampleEncounter.party()
            .filterNot { it.id() == handwritten.id }
            .map { characterSheetFrom(it) }
        val enemies = SampleEncounter.enemies()
            .filterNot { it.id() == handwrittenMonster.id }
            .map { monsterStatBlockFrom(it, challengeRating = "1", baseXp = 200) }
        return SheetLibrary(
            characters = listOf(handwritten) + party,
            monsters = listOf(handwrittenMonster) + enemies,
        )
    }

    private fun guard(successMessage: String, block: () -> Unit) {
        status = try {
            block()
            successMessage
        } catch (failure: IOException) {
            "Errore su disco: ${failure.message}"
        } catch (failure: IllegalArgumentException) {
            "Scheda non valida: ${failure.message}"
        }
    }
}
