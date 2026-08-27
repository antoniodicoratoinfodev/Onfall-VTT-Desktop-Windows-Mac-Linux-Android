package app.d6d.ui.roster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.domain.combat.CombatResourceState
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.SheetStore
import app.d6d.sheet.InventoryItem
import app.d6d.sheet.NpcDisposition
import app.d6d.sheet.StatBlockActorKind
import app.d6d.sheet.isPactSpellSlot
import app.d6d.sheet.i18n.localizedSheetError
import app.d6d.sheet.spellSlotLevelOrNull
import app.d6d.ui.content.SessionTemplate
import app.d6d.ui.content.SessionTemplates
import app.d6d.ui.sheet.SheetKind
import app.d6d.ui.sheet.SheetViewModel
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.Strings
import app.d6d.rules.character.CharacterClassId
import app.d6d.i18n.label
import app.d6d.i18n.pick

/** Tipo di attore nel roster; PNG e creature condividono l'editor dello stat block. */
enum class RosterKind {
    PERSONAGGIO,
    NPC,
    CREATURA,
}

fun RosterKind.label(strings: Strings): String = when (this) {
    RosterKind.PERSONAGGIO -> strings.compendium.characters
    RosterKind.NPC -> strings.compendium.npcs
    RosterKind.CREATURA -> strings.compendium.creatures
}

/** Riga del roster unificato. */
data class RosterItem(
    val id: String,
    val name: String,
    val kind: RosterKind,
    val subtitle: String,
    val classId: CharacterClassId? = null,
    val npcDisposition: NpcDisposition? = null,
)

/** Inventario reale di un personaggio del roster, pronto per la UI di battaglia. */
data class CharacterInventory(
    val characterId: String,
    val characterName: String,
    val items: List<InventoryItem>,
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

    /** Vocabolario in uso: qui non arriva `LocalStrings`, siamo fuori da Compose. */
    private val words get() = AppLocale.current.compendium

    /** Editor delle schede, passato agli editor esistenti senza modificarli. */
    val sheets = SheetViewModel(sheetStore, loadOnCreate)

    var status by mutableStateOf<String?>(null)

    internal fun onLanguageChanged() {
        status = null
        sheets.onLanguageChanged()
    }

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
    val items: List<RosterItem> by derivedStateOf {
        val people = sheets.library.characters.map {
            RosterItem(
                it.id,
                it.characterName.ifBlank { words.unnamed },
                RosterKind.PERSONAGGIO,
                // Una scheda guidata scrive gia' i livelli dentro la classe
                // ("Guerriero 3 / Ladro 2"): ripeterli darebbe "Guerriero 3 3".
                // Quelle manuali tengono il livello in un campo a parte.
                if (it.progression.configured) {
                    sheets.displayedClassName(it).trim().ifBlank { words.characterLabel }
                } else {
                    words.classAndLevel(it.className, it.level).trim().ifBlank { words.characterLabel }
                },
                classId = it.progression.classLevels.firstOrNull()?.classId,
            )
        }
        val creatures = sheets.library.monsters.map {
            val isNpc = it.actorKind == StatBlockActorKind.NPC
            val classSummary = it.characterClassId?.let { classId ->
                words.classAndLevel(classId.label(AppLocale.language), it.classLevel.coerceIn(1, 20))
            }
            val disposition = if (isNpc) it.npcDisposition.label(AppLocale.language) else null
            RosterItem(
                it.id,
                it.name.ifBlank { words.unnamed },
                if (isNpc) RosterKind.NPC else RosterKind.CREATURA,
                listOfNotNull(
                    disposition,
                    classSummary,
                    words.challengeRating(it.challengeRating),
                ).joinToString(" · "),
                classId = it.characterClassId,
                npcDisposition = it.npcDisposition.takeIf { isNpc },
            )
        }
        people + creatures
    }

    val selectedId: String? get() = sheets.selectedId

    fun characterInventory(id: String): CharacterInventory? =
        sheets.library.characters.firstOrNull { it.id == id }?.let { sheet ->
            CharacterInventory(
                characterId = sheet.id,
                characterName = sheet.characterName.ifBlank { words.unnamed },
                items = sheet.inventory,
            )
        }

    /**
     * Inserisce un loot una sola volta. L'ID di provenienza rende idempotente il
     * recupero dopo un'interruzione fra salvataggio della scheda e consumo della
     * pedina.
     */
    fun addInventoryItem(characterId: String, item: InventoryItem): Boolean {
        val character = sheets.library.characters.firstOrNull { it.id == characterId }
            ?: return false
        if (
            item.sourceTokenId != null &&
            sheets.library.characters.any { owner ->
                owner.inventory.any { it.sourceTokenId == item.sourceTokenId }
            }
        ) {
            return true
        }
        if (character.inventory.any {
                it.id == item.id ||
                    (item.sourceTokenId != null && it.sourceTokenId == item.sourceTokenId)
            }
        ) {
            return true
        }
        return sheets.updateCharacterInventorySilently(
            characterId,
            character.inventory + item,
        )
    }

    /**
     * Proiezione da combattimento aggiornata di una voce del Compendio.
     *
     * Il configuratore degli incontri passa sempre da qui, anziche' rileggere il
     * catalogo derivato su disco: in questo modo una scheda appena salvata e' la
     * fonte effettiva dei PF, dell'iniziativa e delle capacita' del combattente.
     */
    fun definitionFor(id: String): ActorDefinition? {
        // La lingua va passata: il valore predefinito e' l'italiano, e senza di
        // essa un attore senza nome tornerebbe «Senza nome» dentro una partita
        // in inglese.
        val language = AppLocale.language
        sheets.library.characters.firstOrNull { it.id == id }
            ?.let {
                return it.toActorDefinition(
                    abilityCatalog = sheets.abilityCatalog,
                    language = language,
                )
            }
        sheets.library.monsters.firstOrNull { it.id == id }
            ?.let { return it.toActorDefinition(language = language) }
        return null
    }

    /** Classe primaria mostrata sul combattente, risolta dalla scheda autorevole. */
    fun classIdFor(definitionId: String): CharacterClassId? =
        sheets.library.characters.firstOrNull { it.id == definitionId }
            ?.progression?.classLevels?.firstOrNull()?.classId
            ?: sheets.library.monsters.firstOrNull { it.id == definitionId }?.characterClassId

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
        val known = sheets.library.characters.mapTo(mutableSetOf()) { it.id }
        sheets.restoreMissing(template.buildMissingParty(known), template.monsters)
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
        val known = sheets.library.characters.mapTo(mutableSetOf()) { it.id }
        val missingCharacters = buildList {
            SessionTemplates.of(AppLocale.language).all.forEach { template ->
                val built = template.buildMissingParty(known)
                addAll(built)
                built.forEach { known += it.id }
            }
        }
        sheets.restoreMissing(
            missingCharacters,
            SessionTemplates.of(AppLocale.language).all.flatMap { it.monsters },
        )
    }

    /** Quale editor e' aperto, dedotto dal tipo di scheda in modifica. */
    val editorKind: RosterKind
        get() = when {
            sheets.kind == SheetKind.PERSONAGGIO -> RosterKind.PERSONAGGIO
            sheets.monster.actorKind == StatBlockActorKind.NPC -> RosterKind.NPC
            else -> RosterKind.CREATURA
        }

    fun select(item: RosterItem) {
        when (item.kind) {
            RosterKind.PERSONAGGIO -> {
                sheets.kind = SheetKind.PERSONAGGIO
                sheets.selectCharacter(item.id)
            }

            RosterKind.NPC,
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

    fun newNpc() {
        sheets.kind = SheetKind.MOSTRO
        sheets.newSheet()
        sheets.monster = sheets.monster.copy(
            actorKind = StatBlockActorKind.NPC,
            npcDisposition = NpcDisposition.NEUTRAL,
            id = sheets.monster.id.replaceFirst("mostro-", "npc-"),
        )
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

    /** Conserva nella scheda massimo e usi spesi delle risorse (anche dopo Undo). */
    fun applyCombatResources(
        definitionId: String,
        resources: List<CombatResourceState>,
    ): Boolean {
        val character = sheets.library.characters.firstOrNull { it.id == definitionId }
            ?: return true
        val resourcesById = resources.associateBy { it.id() }
        val updatedPools = character.progression.resourcePools.map { pool ->
            val resource = resourcesById[pool.resourceId] ?: return@map pool
            pool.copy(
                maximum = resource.maximum(),
                spent = resource.spent().coerceIn(0, resource.maximum()),
            )
        }
        val casting = character.spellcasting
        val standardSlots = resources
            .filterNot { it.isPactSpellSlot() }
            .mapNotNull { resource ->
                resource.spellSlotLevelOrNull()?.let { level -> level to resource }
            }
            .toMap()
        val pactSlot = resources
            .firstOrNull { it.isPactSpellSlot() }
            ?.let { resource -> resource.spellSlotLevelOrNull()?.let { it to resource } }
        val updatedCasting = casting?.copy(
            slots = casting.slots.map { slot ->
                standardSlots[slot.level]
                    ?.let {
                        slot.copy(
                            total = it.maximum(),
                            spent = it.spent().coerceIn(0, it.maximum()),
                        )
                    }
                    ?: slot
            },
            pactSlots = casting.pactSlots?.let { slot ->
                pactSlot
                    ?.takeIf { it.first == slot.level }
                    ?.let {
                        slot.copy(
                            total = it.second.maximum(),
                            spent = it.second.spent().coerceIn(0, it.second.maximum()),
                        )
                    }
                    ?: slot
            },
        )
        if (
            updatedPools == character.progression.resourcePools &&
            updatedCasting == character.spellcasting
        ) {
            return true
        }
        return sheets.upsertCharacterResourcesSilently(
            character.copy(
                progression = character.progression.copy(resourcePools = updatedPools),
                spellcasting = updatedCasting,
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
            words.catalogError(failure.message.orEmpty())
        } catch (failure: IllegalArgumentException) {
            words.invalidSheetForCatalog(
                localizedSheetError(failure.message.orEmpty(), AppLocale.language),
            )
        }
    }
}

fun NpcDisposition.label(language: app.d6d.i18n.AppLanguage): String = when (this) {
    NpcDisposition.FRIENDLY -> language.pick("Amichevole", "Friendly")
    NpcDisposition.HOSTILE -> language.pick("Ostile", "Hostile")
    NpcDisposition.NEUTRAL -> language.pick("Neutrale", "Neutral")
}
