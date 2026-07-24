package app.d6d.ui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.defaultAbilityCatalog
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

/** Esito di un cambio editor che potrebbe scartare una bozza. */
enum class SheetNavigationResult {
    APPLIED,
    UNSAVED_CHANGES,
    NOT_FOUND,
    FAILED,
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

    private var currentKind by mutableStateOf(SheetKind.PERSONAGGIO)

    /**
     * Il setter resta compatibile con gli editor esistenti, ma non scarta mai una
     * bozza: per forzare il cambio la UI deve usare [requestKind] esplicitamente.
     */
    var kind: SheetKind
        get() = currentKind
        set(value) {
            requestKind(value)
        }

    var character by mutableStateOf(CharacterSheet())

    var monster by mutableStateOf(MonsterStatBlock())

    var selectedId by mutableStateOf<String?>(null)
        private set

    var status by mutableStateOf<String?>(null)

    private var pristineNewCharacter: CharacterSheet? = null
    private var pristineNewMonster: MonsterStatBlock? = null

    /** Vero quando il modulo aperto differisce dalla copia persistita. */
    val isDirty: Boolean
        get() = when (kind) {
            SheetKind.PERSONAGGIO -> {
                val baseline = selectedId
                    ?.let { id -> library.characters.firstOrNull { it.id == id } }
                    ?: pristineNewCharacter
                baseline == null || character != baseline
            }

            SheetKind.MOSTRO -> {
                val baseline = selectedId
                    ?.let { id -> library.monsters.firstOrNull { it.id == id } }
                    ?: pristineNewMonster
                baseline == null || monster != baseline
            }
        }

    val hasUnsavedChanges: Boolean get() = isDirty

    private var initialized = false

    /**
     * Notificato dopo un salvataggio riuscito.
     *
     * Il coordinatore del roster lo usa per rigenerare il catalogo da combattimento:
     * la scheda e' autorevole, quindi ogni volta che cambia il catalogo va riderivato.
     */
    var onSaved: ((SheetKind) -> Unit)? = null

    /** Notificato dopo un'eliminazione riuscita. */
    var onDeleted: ((SheetKind, String) -> Unit)? = null

    /** Notificato quando cambia il catalogo delle capacità riusabili. */
    var onAbilitiesChanged: (() -> Unit)? = null

    init {
        load()
    }

    fun load(discardUnsavedChanges: Boolean = false): SheetNavigationResult {
        if (initialized && isDirty && !discardUnsavedChanges) return unsavedResult()
        val loaded = try {
            if (store.exists()) store.load() else seeded().also { store.save(it) }
        } catch (failure: IOException) {
            status = "Errore su disco: ${failure.message}"
            return SheetNavigationResult.FAILED
        } catch (failure: IllegalArgumentException) {
            status = "Scheda non valida: ${failure.message}"
            return SheetNavigationResult.FAILED
        }

        // Il nuovo archivio diventa visibile soltanto dopo che lettura (ed eventuale
        // prima scrittura del seed) sono terminate con successo.
        library = loaded
        initialized = true
        when (kind) {
            SheetKind.PERSONAGGIO -> loaded.characters.firstOrNull()?.let(::selectCharacterInternal)
                ?: newSheetInternal()
            SheetKind.MOSTRO -> loaded.monsters.firstOrNull()?.let(::selectMonsterInternal)
                ?: newSheetInternal()
        }
        status = "Archivio caricato."
        return SheetNavigationResult.APPLIED
    }

    fun requestKind(
        requested: SheetKind,
        discardUnsavedChanges: Boolean = false,
    ): SheetNavigationResult {
        if (requested == kind) return SheetNavigationResult.APPLIED
        if (isDirty && !discardUnsavedChanges) return unsavedResult()
        currentKind = requested
        selectedId = null
        when (requested) {
            SheetKind.PERSONAGGIO -> library.characters.firstOrNull()?.let(::selectCharacterInternal)
                ?: newSheetInternal()
            SheetKind.MOSTRO -> library.monsters.firstOrNull()?.let(::selectMonsterInternal)
                ?: newSheetInternal()
        }
        status = null
        return SheetNavigationResult.APPLIED
    }

    fun selectCharacter(
        id: String,
        discardUnsavedChanges: Boolean = false,
    ): SheetNavigationResult {
        if (kind == SheetKind.PERSONAGGIO && selectedId == id) return SheetNavigationResult.APPLIED
        if (isDirty && !discardUnsavedChanges) return unsavedResult()
        val selected = library.characters.firstOrNull { it.id == id }
            ?: return notFoundResult()
        currentKind = SheetKind.PERSONAGGIO
        selectCharacterInternal(selected)
        status = null
        return SheetNavigationResult.APPLIED
    }

    fun selectMonster(
        id: String,
        discardUnsavedChanges: Boolean = false,
    ): SheetNavigationResult {
        if (kind == SheetKind.MOSTRO && selectedId == id) return SheetNavigationResult.APPLIED
        if (isDirty && !discardUnsavedChanges) return unsavedResult()
        val selected = library.monsters.firstOrNull { it.id == id }
            ?: return notFoundResult()
        currentKind = SheetKind.MOSTRO
        selectMonsterInternal(selected)
        status = null
        return SheetNavigationResult.APPLIED
    }

    fun newSheet(discardUnsavedChanges: Boolean = false): SheetNavigationResult {
        if (isDirty && !discardUnsavedChanges) return unsavedResult()
        newSheetInternal()
        status = "Nuova scheda: compila e salva."
        return SheetNavigationResult.APPLIED
    }

    fun save(): Boolean = guard("Scheda salvata.") {
        val updatedLibrary = when (kind) {
            SheetKind.PERSONAGGIO -> library.copy(
                characters = library.characters.filterNot { it.id == character.id } + character,
            )

            SheetKind.MOSTRO -> library.copy(
                monsters = library.monsters.filterNot { it.id == monster.id } + monster,
            )
        }
        // Commit in memoria solo dopo la sostituzione atomica su disco.
        store.save(updatedLibrary)
        library = updatedLibrary
        selectedId = if (kind == SheetKind.PERSONAGGIO) character.id else monster.id
        when (kind) {
            SheetKind.PERSONAGGIO -> pristineNewCharacter = null
            SheetKind.MOSTRO -> pristineNewMonster = null
        }
        onSaved?.invoke(kind)
    }

    fun delete(id: String): Boolean = guard("Scheda eliminata.") {
        val deletedKind = kind
        val deletingSelection = selectedId == id
        val updatedLibrary = when (kind) {
            SheetKind.PERSONAGGIO -> library.copy(characters = library.characters.filterNot { it.id == id })
            SheetKind.MOSTRO -> library.copy(monsters = library.monsters.filterNot { it.id == id })
        }
        store.save(updatedLibrary)
        library = updatedLibrary
        if (deletingSelection) {
            when (kind) {
                SheetKind.PERSONAGGIO -> updatedLibrary.characters.firstOrNull()
                    ?.let(::selectCharacterInternal) ?: newSheetInternal()
                SheetKind.MOSTRO -> updatedLibrary.monsters.firstOrNull()
                    ?.let(::selectMonsterInternal) ?: newSheetInternal()
            }
        }
        onDeleted?.invoke(deletedKind, id)
    }

    /**
     * Aggiorna una scheda senza toccare l'editor aperto.
     *
     * Serve alla propagazione delle correzioni fatte in combattimento: la scheda
     * resta autorevole, quindi un'edit al tavolo deve confluire nella scheda, non
     * solo nel catalogo. Non sposta la selezione ne' la scheda in modifica.
     */
    fun upsertCharacterSilently(sheet: CharacterSheet): Boolean = guard("Scheda aggiornata dalla battaglia.") {
        val editingThisSheet = selectedId == sheet.id && kind == SheetKind.PERSONAGGIO
        val preserveDraft = editingThisSheet && isDirty
        val updatedLibrary = library.copy(
            characters = library.characters.filterNot { it.id == sheet.id } + sheet,
        )
        store.save(updatedLibrary)
        library = updatedLibrary
        if (editingThisSheet && !preserveDraft) character = sheet
        onSaved?.invoke(SheetKind.PERSONAGGIO)
    }

    fun upsertMonsterSilently(block: MonsterStatBlock): Boolean = guard("Stat block aggiornato dalla battaglia.") {
        val editingThisBlock = selectedId == block.id && kind == SheetKind.MOSTRO
        val preserveDraft = editingThisBlock && isDirty
        val updatedLibrary = library.copy(
            monsters = library.monsters.filterNot { it.id == block.id } + block,
        )
        store.save(updatedLibrary)
        library = updatedLibrary
        if (editingThisBlock && !preserveDraft) monster = block
        onSaved?.invoke(SheetKind.MOSTRO)
    }

    /** Crea o aggiorna una capacità del Compendio senza toccare la scheda aperta. */
    fun upsertAbility(ability: CatalogAbility): Boolean = guard("Abilità salvata.") {
        // La conversione applica in un solo punto tutte le validazioni meccaniche.
        ability.toDefinition()
        val updatedLibrary = library.copy(
            abilities = library.abilities.filterNot { it.id == ability.id } + ability,
        )
        store.save(updatedLibrary)
        library = updatedLibrary
        onAbilitiesChanged?.invoke()
    }

    /** Numero di schede persistite o in modifica che usano la capacità. */
    fun abilityUsageCount(id: String): Int {
        val persistedIds = library.characters
            .filter { id in it.abilityIds }
            .mapTo(mutableSetOf()) { it.id }
        if (kind == SheetKind.PERSONAGGIO && id in character.abilityIds) {
            persistedIds += character.id
        }
        return persistedIds.size
    }

    /**
     * Elimina una capacità soltanto se nessuna scheda la usa.
     *
     * Un riferimento non viene mai spezzato silenziosamente: prima la capacità va
     * rimossa dai personaggi interessati, che restano così esplicitamente sotto il
     * controllo dell'utente.
     */
    fun deleteAbility(id: String): Boolean {
        val usedBy = abilityUsageCount(id)
        if (usedBy > 0) {
            status = "Impossibile eliminare: l'abilità è usata da $usedBy " +
                if (usedBy == 1) "scheda." else "schede."
            return false
        }
        return guard("Abilità eliminata.") {
            val updatedLibrary = library.copy(abilities = library.abilities.filterNot { it.id == id })
            store.save(updatedLibrary)
            library = updatedLibrary
            onAbilitiesChanged?.invoke()
        }
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
        val abilities = defaultAbilityCatalog()
        val handwritten = SheetSamples.character().copy(
            weapons = emptyList(),
            abilityIds = listOf(
                "arma-spadone",
                "arma-giavellotto",
                "abilita-recuperare-energie",
                "abilita-azione-impetuosa",
            ),
        )
        val handwrittenMonster = SheetSamples.monster()
        val party = SampleEncounter.party()
            .filterNot { it.id() == handwritten.id }
            .map { characterSheetFrom(it, abilities) }
        val enemies = SampleEncounter.enemies()
            .filterNot { it.id() == handwrittenMonster.id }
            .map { monsterStatBlockFrom(it, challengeRating = "1", baseXp = 200) }
        return SheetLibrary(
            characters = listOf(handwritten) + party,
            monsters = listOf(handwrittenMonster) + enemies,
            abilities = abilities,
        )
    }

    private fun selectCharacterInternal(sheet: CharacterSheet) {
        character = sheet
        selectedId = sheet.id
        pristineNewCharacter = null
    }

    private fun selectMonsterInternal(block: MonsterStatBlock) {
        monster = block
        selectedId = block.id
        pristineNewMonster = null
    }

    private fun newSheetInternal() {
        selectedId = null
        val stamp = System.currentTimeMillis()
        when (kind) {
            SheetKind.PERSONAGGIO -> {
                character = CharacterSheet(id = "pg-$stamp")
                pristineNewCharacter = character
            }
            SheetKind.MOSTRO -> {
                monster = MonsterStatBlock(id = "mostro-$stamp")
                pristineNewMonster = monster
            }
        }
    }

    private fun unsavedResult(): SheetNavigationResult {
        status = "Ci sono modifiche non salvate: salva oppure conferma di volerle scartare."
        return SheetNavigationResult.UNSAVED_CHANGES
    }

    private fun notFoundResult(): SheetNavigationResult {
        status = "Scheda non trovata."
        return SheetNavigationResult.NOT_FOUND
    }

    private fun guard(successMessage: String, block: () -> Unit): Boolean {
        status = try {
            block()
            successMessage
        } catch (failure: IOException) {
            "Errore su disco: ${failure.message}"
        } catch (failure: IllegalArgumentException) {
            "Scheda non valida: ${failure.message}"
        }
        return status == successMessage
    }
}
