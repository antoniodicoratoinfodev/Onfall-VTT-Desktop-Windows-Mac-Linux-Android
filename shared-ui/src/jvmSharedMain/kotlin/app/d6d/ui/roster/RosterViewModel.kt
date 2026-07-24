package app.d6d.ui.roster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.SheetStore
import app.d6d.ui.sheet.SheetKind
import app.d6d.ui.sheet.SheetViewModel

/** Tipo di attore nel roster: le due categorie ora coincidono con i due editor. */
enum class RosterKind(val label: String) {
    PERSONAGGIO("Personaggi"),
    CREATURA("Creature"),
}

/** Riga del roster unificato. */
data class RosterItem(
    val id: String,
    val name: String,
    val kind: RosterKind,
    val subtitle: String,
)

/**
 * Roster unificato: schede dei personaggi e stat block delle creature in un solo posto.
 *
 * La libreria delle schede E' il roster. Il catalogo da combattimento
 * (`catalog.json`, quello che la battaglia consuma) e' interamente **derivato** dalle
 * schede e viene rigenerato a ogni modifica: non esiste piu' un dato di personaggio
 * indipendente dalla scheda. Questo realizza il principio che la scheda sovrascrive
 * la parte del compendio relativa ai personaggi giocanti — qui la sovrascrive per
 * intero, perche' il compendio non ha piu' una propria copia modificabile a parte.
 *
 * L'editor vero e' delegato a [SheetViewModel]: la scheda completa per i personaggi,
 * lo stat block per le creature. Questa classe coordina, elenca e riconcilia.
 */
class RosterViewModel(
    private val catalogStore: ActorCatalogStore,
    sheetStore: SheetStore,
) {

    /** Editor delle schede, passato agli editor esistenti senza modificarli. */
    val sheets = SheetViewModel(sheetStore)

    var status by mutableStateOf<String?>(null)

    init {
        // La scheda e' autorevole: ogni salvataggio o eliminazione rigenera il catalogo.
        sheets.onSaved = { reconcileCatalog() }
        sheets.onDeleted = { _, _ -> reconcileCatalog() }
        sheets.onAbilitiesChanged = { reconcileCatalog() }
        reconcileCatalog()
    }

    /** Roster unificato, derivato dalla libreria delle schede. */
    val items: List<RosterItem>
        get() {
            val people = sheets.library.characters.map {
                RosterItem(
                    it.id,
                    it.characterName.ifBlank { "Senza nome" },
                    RosterKind.PERSONAGGIO,
                    "${it.className} ${it.level}".trim().ifBlank { "Personaggio" },
                )
            }
            val creatures = sheets.library.monsters.map {
                RosterItem(
                    it.id,
                    it.name.ifBlank { "Senza nome" },
                    RosterKind.CREATURA,
                    "GS ${it.challengeRating}",
                )
            }
            return people + creatures
        }

    val selectedId: String? get() = sheets.selectedId

    /**
     * Proiezione da combattimento aggiornata di una voce del Compendio.
     *
     * Il configuratore degli incontri passa sempre da qui, anziche' rileggere il
     * catalogo derivato su disco: in questo modo una scheda appena salvata e' la
     * fonte effettiva dei PF, dell'iniziativa e delle capacita' del combattente.
     */
    fun definitionFor(id: String): ActorDefinition? {
        sheets.library.characters.firstOrNull { it.id == id }
            ?.let { return it.toActorDefinition(abilityCatalog = sheets.library.abilities) }
        sheets.library.monsters.firstOrNull { it.id == id }
            ?.let { return it.toActorDefinition() }
        return null
    }

    /** Quale editor e' aperto, dedotto dal tipo di scheda in modifica. */
    val editorKind: RosterKind
        get() = if (sheets.kind == SheetKind.PERSONAGGIO) RosterKind.PERSONAGGIO else RosterKind.CREATURA

    fun select(item: RosterItem) {
        when (item.kind) {
            RosterKind.PERSONAGGIO -> {
                sheets.kind = SheetKind.PERSONAGGIO
                sheets.selectCharacter(item.id)
            }

            RosterKind.CREATURA -> {
                sheets.kind = SheetKind.MOSTRO
                sheets.selectMonster(item.id)
            }
        }
    }

    fun newCharacter() {
        sheets.kind = SheetKind.PERSONAGGIO
        sheets.newSheet()
    }

    fun newCreature() {
        sheets.kind = SheetKind.MOSTRO
        sheets.newSheet()
    }

    /**
     * Ingombro del segnaposto in caselle per lato, dedotto dalla taglia dell'attore.
     *
     * La taglia e' un'informazione dell'attore e si imposta nel Compendio, non al
     * tavolo: qui si legge dalla scheda o dallo stat block. Un attore fuori dal
     * roster ricade su una casella.
     */
    fun footprintFor(definitionId: String): Int {
        sheets.library.characters.firstOrNull { it.id == definitionId }
            ?.let { return it.size.squaresPerSide }
        sheets.library.monsters.firstOrNull { it.id == definitionId }
            ?.let { return it.size.squaresPerSide }
        return 1
    }

    /**
     * Recepisce una correzione fatta durante il combattimento.
     *
     * La scheda resta la fonte: la modifica confluisce nella scheda del personaggio
     * o nello stat block della creatura, poi il catalogo si rigenera da li'. Se
     * l'attore non e' nel roster non succede nulla.
     */
    fun applyCombatEdit(definitionId: String, snapshot: CombatantSnapshot): Boolean {
        val character = sheets.library.characters.firstOrNull { it.id == definitionId }
        if (character != null) {
            return sheets.upsertCharacterSilently(
                character.copy(
                    // Le copie numerate di un incontro hanno un nome di istanza
                    // (per esempio "Guardia 2"): non deve rinominare la scheda.
                    characterName = if (snapshot.instanceId() == definitionId) {
                        snapshot.name()
                    } else {
                        character.characterName
                    },
                    armorClass = snapshot.armorClass(),
                    maxHitPoints = snapshot.maxHitPoints(),
                    currentHitPoints = character.currentHitPoints.coerceAtMost(snapshot.maxHitPoints()),
                    speedFeet = snapshot.speedFeet(),
                ),
            )
        }
        val monster = sheets.library.monsters.firstOrNull { it.id == definitionId }
        if (monster != null) {
            return sheets.upsertMonsterSilently(
                monster.copy(
                    name = if (snapshot.instanceId() == definitionId) snapshot.name() else monster.name,
                    armorClass = snapshot.armorClass(),
                    averageHitPoints = snapshot.maxHitPoints(),
                    speeds = monster.speeds.copy(walk = snapshot.speedFeet()),
                ),
            )
        }
        return false
    }

    /**
     * Rigenera il catalogo da combattimento dalle schede.
     *
     * E' l'unico punto in cui il catalogo viene scritto: cosi' non puo' contenere
     * dati di personaggio scollegati dalle schede.
     */
    private fun reconcileCatalog() {
        status = try {
            val entries = sheets.library.characters.map { it.toCatalogEntry(sheets.library.abilities) } +
                sheets.library.monsters.map { it.toCatalogEntry() }
            catalogStore.save(entries)
            null
        } catch (failure: java.io.IOException) {
            "Errore nel catalogo: ${failure.message}"
        } catch (failure: IllegalArgumentException) {
            "Scheda non valida per il catalogo: ${failure.message}"
        }
    }
}
