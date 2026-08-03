package app.d6d.ui.roster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.domain.combat.CombatResourceState
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.SheetStore
import app.d6d.ui.content.SessionTemplate
import app.d6d.ui.content.SessionTemplates
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
    loadOnCreate: Boolean = true,
) {

    /** Editor delle schede, passato agli editor esistenti senza modificarli. */
    val sheets = SheetViewModel(sheetStore, loadOnCreate)

    var status by mutableStateOf<String?>(null)

    init {
        // La scheda e' autorevole: ogni salvataggio o eliminazione rigenera il catalogo.
        sheets.onSaved = { reconcileCatalog() }
        sheets.onDeleted = { _, _ -> reconcileCatalog() }
        sheets.onAbilitiesChanged = { reconcileCatalog() }
        if (loadOnCreate) reconcileCatalog()
    }

    /** Completa il caricamento quando la shell lo ha deliberatamente rinviato al dispatcher I/O. */
    internal fun initialize() {
        if (!sheets.initialized) sheets.load()
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
                    // Una scheda guidata scrive gia' i livelli dentro la classe
                    // ("Guerriero 3 / Ladro 2"): ripeterli darebbe "Guerriero 3 3".
                    // Quelle manuali tengono il livello in un campo a parte.
                    if (it.progression.configured) {
                        it.className.trim().ifBlank { "Personaggio" }
                    } else {
                        "${it.className} ${it.level}".trim().ifBlank { "Personaggio" }
                    },
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
            ?.let { return it.toActorDefinition(abilityCatalog = sheets.abilityCatalog) }
        sheets.library.monsters.firstOrNull { it.id == id }
            ?.let { return it.toActorDefinition() }
        return null
    }

    fun druidLevelFor(id: String): Int =
        sheets.library.characters.firstOrNull { it.id == id }
            ?.progression?.levelIn(app.d6d.rules.character.CharacterClassId.DRUID)
            ?: 0

    /**
     * Dice se una capacita' vale come tratto permanente, secondo il Compendio.
     *
     * Null quando l'identificatore non e' in catalogo — per esempio le armi
     * derivate dalla scheda — cosi' chi la usa puo' ricadere sulla definizione.
     */
    fun abilityIsPassive(abilityId: String): Boolean? =
        sheets.abilityCatalog.firstOrNull { it.id == abilityId }?.passive

    /**
     * Assicura che il contenuto di un template incluso sia nel Compendio.
     *
     * I template nominano schede del roster invece di portarne una copia: se
     * l'utente ne ha cancellata qualcuna, sceglierlo la rimette al suo posto,
     * senza toccare quelle che ha modificato.
     */
    internal fun installTemplateContent(template: SessionTemplate) {
        sheets.restoreMissing(template.party, template.monsters)
    }

    /**
     * Mette nel Compendio tutto il contenuto distribuito con l'app.
     *
     * L'archivio viene popolato solo alla prima installazione: chi usava l'app
     * da prima non vedrebbe mai le partite incluse, e aprirne una direbbe che le
     * schede non ci sono. Qui si rimettono in una sola scrittura, e solo quelle
     * che mancano: le schede dell'utente, anche se modificate, restano intatte.
     */
    internal fun installIncludedContent() {
        sheets.restoreMissing(
            SessionTemplates.all.flatMap { it.party },
            SessionTemplates.all.flatMap { it.monsters },
        )
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
                    // Una correzione esplicita fatta durante lo scontro diventa un
                    // override rimovibile: la formula dettagliata resta intatta.
                    // Se il valore coincide gia' col calcolo, non creiamo un
                    // override soltanto perche' e' stato modificato un altro campo.
                    armorClassOverride = snapshot.armorClass()
                        .takeUnless { it == character.calculatedArmorClass },
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
        // Una sessione importata può contenere attori che non appartengono al
        // Compendio locale: la loro modifica resta valida nel salvataggio della
        // sessione, anche se qui non esiste una scheda da aggiornare.
        return true
    }

    /** Conserva nella scheda gli usi limitati spesi (o ripristinati con Undo). */
    fun applyCombatResources(
        definitionId: String,
        resources: List<CombatResourceState>,
    ): Boolean {
        val character = sheets.library.characters.firstOrNull { it.id == definitionId }
            ?: return true
        val spentById = resources.associate { it.id() to it.spent() }
        val updatedPools = character.progression.resourcePools.map { pool ->
            val spent = spentById[pool.resourceId] ?: return@map pool
            pool.copy(spent = spent.coerceIn(0, pool.maximum))
        }
        if (updatedPools == character.progression.resourcePools) return true
        return sheets.upsertCharacterSilently(
            character.copy(
                progression = character.progression.copy(resourcePools = updatedPools),
            ),
        )
    }

    /**
     * Rigenera il catalogo da combattimento dalle schede.
     *
     * E' l'unico punto in cui il catalogo viene scritto: cosi' non puo' contenere
     * dati di personaggio scollegati dalle schede.
     */
    private fun reconcileCatalog() {
        status = try {
            val entries = sheets.library.characters.map { it.toCatalogEntry(sheets.abilityCatalog) } +
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
