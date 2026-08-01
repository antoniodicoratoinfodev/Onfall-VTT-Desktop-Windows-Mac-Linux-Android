package app.d6d.ui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.content.srd521it.Srd521ItContent
import app.d6d.content.srd521it.SrdChoiceOption
import app.d6d.content.srd521it.SrdChoiceResolver
import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.SpellcastingKind
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.SheetLibrary
import app.d6d.sheet.SheetStore
import app.d6d.ui.content.SessionTemplates
import java.io.IOException

/** Quale delle due schede si sta redigendo. */
enum class SheetKind(val label: String) {
    PERSONAGGIO("Personaggi"),
    MOSTRO("Mostri"),
}

/** Sezione strutturata della scheda modificabile dal Compendio. */
enum class CharacterTraitSection(
    val label: String,
    val catalogKinds: Set<RuleElementKind>,
) {
    FEATURE(
        "privilegi",
        setOf(
            RuleElementKind.CLASS_FEATURE,
            RuleElementKind.SUBCLASS_FEATURE,
            RuleElementKind.CLASS_OPTION,
            RuleElementKind.METAMAGIC,
            RuleElementKind.ELDRITCH_INVOCATION,
        ),
    ),
    FEAT(
        "talenti",
        setOf(
            RuleElementKind.ORIGIN_FEAT,
            RuleElementKind.GENERAL_FEAT,
            RuleElementKind.FIGHTING_STYLE_FEAT,
            RuleElementKind.EPIC_BOON_FEAT,
        ),
    ),
}

private val fightingStyleFeatureKinds = setOf(
    RuleElementKind.CLASS_FEATURE,
    RuleElementKind.SUBCLASS_FEATURE,
)

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
class SheetViewModel(
    private val store: SheetStore,
    loadOnCreate: Boolean = true,
) {
    private val guidedCharacters by lazy { GuidedCharacterService(Srd521ItContent.pack) }

    val srdClasses get() = Srd521ItContent.pack.classes

    var library by mutableStateOf(SheetLibrary())
        private set

    /**
     * Le voci SRD sono distribuite dal content pack e restano immutabili; nel
     * file dell'utente persistono soltanto capacità private e riferimenti scelti.
     */
    val abilityCatalog: List<CatalogAbility>
        get() = (library.abilities + bundledSrdAbilities)
            .distinctBy { it.id }
            .map { ability ->
                // La riclassificazione del tavolo vince sul pacchetto, che resta
                // immutato: e' una scelta d'uso, non una modifica al contenuto.
                library.passiveOverrides[ability.id]
                    ?.takeIf { it != ability.passive }
                    ?.let { ability.copy(passive = it) }
                    ?: ability
            }

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

    var initialized by mutableStateOf(false)
        private set

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
        if (loadOnCreate) load()
    }

    fun load(discardUnsavedChanges: Boolean = false): SheetNavigationResult {
        if (initialized && isDirty && !discardUnsavedChanges) return unsavedResult()
        val loaded = try {
            val fromDisk = if (store.exists()) store.load() else seeded().also { store.save(it) }
            val refreshCatalog = (fromDisk.abilities + bundledSrdAbilities).distinctBy { it.id }
            // Gli effetti dei privilegi sono derivati dal pacchetto: una scheda
            // salvata prima che esistessero, o con un pacchetto piu' vecchio, li
            // ha vuoti. Si ricalcolano leggendo, e si riscrivono solo se davvero
            // cambiati, cosi' un archivio gia' allineato non viene toccato.
            val refreshed = fromDisk.copy(
                characters = fromDisk.characters
                    .map { guidedCharacters.withRefreshedEffects(it, refreshCatalog) }
                    .map(guidedCharacters::withoutGeneratedFeatureText),
            )
            // Riscrivere e' un'ottimizzazione, non una condizione: se il disco non
            // e' scrivibile l'archivio deve aprirsi lo stesso, con gli effetti
            // ricalcolati in memoria.
            if (refreshed != fromDisk) {
                runCatching { store.save(refreshed) }
                refreshed
            } else {
                fromDisk
            }
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

    /**
     * Rimette nell'archivio le schede di un template che non ci sono piu'.
     *
     * Un template incluso non porta con se' delle copie: nomina schede del
     * Compendio, che l'utente puo' legittimamente avere cancellato. Qui vengono
     * reinstallate solo quelle mancanti — chi le ha modificate se le tiene — e in
     * una sola scrittura, perche' salvarne dodici di fila riscriverebbe dodici
     * volte lo stesso file.
     */
    internal fun restoreMissing(
        characters: List<CharacterSheet>,
        monsters: List<MonsterStatBlock>,
    ): Boolean {
        val knownCharacters = library.characters.mapTo(mutableSetOf()) { it.id }
        val knownMonsters = library.monsters.mapTo(mutableSetOf()) { it.id }
        val missingCharacters = characters.filterNot { it.id in knownCharacters }
        val missingMonsters = monsters.filterNot { it.id in knownMonsters }
        if (missingCharacters.isEmpty() && missingMonsters.isEmpty()) return false
        return guard("Schede del template ripristinate.") {
            val updatedLibrary = library.copy(
                characters = library.characters + missingCharacters,
                monsters = library.monsters + missingMonsters,
            )
            store.save(updatedLibrary)
            library = updatedLibrary
            onSaved?.invoke(kind)
        }
    }

    fun setCharacterResourceSpent(resourceId: String, spent: Int) {
        val progression = character.progression
        character = character.copy(
            progression = progression.copy(
                resourcePools = progression.resourcePools.map { pool ->
                    if (pool.resourceId == resourceId) {
                        pool.copy(spent = spent.coerceIn(0, pool.maximum))
                    } else {
                        pool
                    }
                },
            ),
        )
    }

    fun recoverCharacterResources(period: RecoveryPeriod) {
        val restored = character.progression.resourcePools.map { it.recoveredAfter(period) }
        val casting = character.spellcasting
        character = character.copy(
            progression = character.progression.copy(resourcePools = restored),
            spellcasting = casting?.copy(
                slots = if (period == RecoveryPeriod.LONG_REST) {
                    casting.slots.map { it.copy(spent = 0) }
                } else {
                    casting.slots
                },
                pactSlots = casting.pactSlots?.let {
                    if (
                        period == RecoveryPeriod.SHORT_REST ||
                        period == RecoveryPeriod.LONG_REST
                    ) {
                        it.copy(spent = 0)
                    } else {
                        it
                    }
                },
            ),
        )
        status = if (period == RecoveryPeriod.LONG_REST) {
            "Risorse da riposo lungo recuperate."
        } else {
            "Risorse da riposo breve recuperate."
        }
    }

    fun progressionRequirements(
        classId: CharacterClassId,
        provisionalSelections: List<app.d6d.rules.character.ChoiceSelection> = emptyList(),
    ): List<ChoiceDefinition> =
        guidedCharacters.requirements(character, classId, provisionalSelections)

    fun progressionOptions(
        choice: ChoiceDefinition,
        classId: CharacterClassId,
        provisionalSelections: List<app.d6d.rules.character.ChoiceSelection> = emptyList(),
    ): List<SrdChoiceOption> =
        SrdChoiceResolver.options(
            choice = choice,
            classId = classId,
            classLevel = character.progression.levelIn(classId) + 1,
            sheet = character,
            provisionalSelections = provisionalSelections,
        )

    fun fixedHitPointIncrease(classId: CharacterClassId): Int =
        guidedCharacters.fixedHitPointIncrease(character, classId)

    fun advanceCharacter(request: LevelUpRequest): Boolean {
        val validation = guidedCharacters.validate(character, request)
        if (!validation.valid) {
            status = validation.issues.joinToString("\n") { it.message }
            return false
        }
        character = guidedCharacters.withRefreshedEffects(
            guidedCharacters.advance(character, request),
            abilityCatalog,
        )
        status = if (character.effectiveLevel == 1) {
            "Personaggio SRD creato: completa i dettagli narrativi e salva."
        } else {
            "Livello ${character.effectiveLevel} applicato. Controlla e salva la scheda."
        }
        return true
    }

    /** Voci del Compendio proponibili nella sezione richiesta. */
    fun characterTraitCandidates(section: CharacterTraitSection): List<CatalogAbility> =
        abilityCatalog
            .filter { it.category in section.catalogKinds }
            .sortedWith(
                compareByDescending<CatalogAbility> { characterTraitIsCompatible(it) }
                    .thenBy { it.name.lowercase() },
            )

    /**
     * Talenti o privilegi effettivamente visibili sulla scheda.
     *
     * Le categorie del Compendio decidono in quale riquadro compare una voce:
     * per esempio uno Stile di combattimento rimane un talento anche se una
     * progressione precedente lo aveva accumulato fra i privilegi scelti.
     */
    fun characterTraitIds(section: CharacterTraitSection): List<String> {
        val catalogById = abilityCatalog.associateBy { it.id }
        return (
            character.progression.selectedFeatureIds +
                character.progression.featIds +
                character.abilityIds
            )
            .distinct()
            .filterNot { it in character.excludedTraitIds }
            .filter { id ->
                val ability = catalogById[id]
                ability?.category in section.catalogKinds ||
                    section == CharacterTraitSection.FEATURE &&
                    ability == null &&
                    id.contains(":weapon:")
            }
    }

    /**
     * Vero se la scheda soddisfa i requisiti strutturabili della voce.
     *
     * [CatalogAbility.prerequisite] resta testo da mostrare: categoria, livello,
     * caratteristiche e privilegi attivi permettono invece di applicare qui le
     * stesse regole sui talenti validate dalla progressione guidata.
     */
    fun characterTraitIsCompatible(ability: CatalogAbility): Boolean {
        if (
            ability.classEligibility.isNotEmpty() &&
            ability.classEligibility.none { eligibility ->
                character.progression.levelIn(eligibility.classId) >= eligibility.minimumLevel
            }
        ) {
            return false
        }

        return when {
            ability.category == RuleElementKind.GENERAL_FEAT &&
                character.effectiveLevel < 4 -> false
            ability.category == RuleElementKind.EPIC_BOON_FEAT &&
                character.effectiveLevel < 19 -> false
            ability.category == RuleElementKind.FIGHTING_STYLE_FEAT &&
                !hasActiveFightingStyleFeature() -> false
            ability.id.endsWith(":feat:general:lottatore") &&
                character.score(Ability.STRENGTH) < 13 &&
                character.score(Ability.DEXTERITY) < 13 -> false
            ability.id.endsWith(":feat:epic-boon:dono-richiamo-incantesimi") &&
                !hasSpellcastingFeature() -> false
            else -> true
        }
    }

    private fun hasActiveFightingStyleFeature(): Boolean {
        val activeIds = character.activeRuleElementIds.toSet()
        return abilityCatalog.any { candidate ->
            candidate.id in activeIds &&
                candidate.category in fightingStyleFeatureKinds &&
                candidate.name.startsWith("Stile di combattimento", ignoreCase = true)
        }
    }

    private fun hasSpellcastingFeature(): Boolean =
        character.spellcasting != null ||
            character.progression.classLevels.any { classLevel ->
                srdClasses
                    .firstOrNull { it.id == classLevel.classId }
                    ?.spellcastingKind
                    ?.let { it != SpellcastingKind.NONE } == true
            }

    /**
     * Collega o esclude un talento/privilegio senza riscrivere la cronologia.
     *
     * L'ID operativo, l'elenco leggibile e gli effetti numerici vengono aggiornati
     * insieme; il salvataggio resta esplicito come per ogni altro campo della
     * scheda.
     */
    fun setCharacterTraitSelected(
        section: CharacterTraitSection,
        abilityId: String,
        selected: Boolean,
    ): Boolean {
        val ability = abilityCatalog.firstOrNull { it.id == abilityId }
        if (ability == null || ability.category !in section.catalogKinds) {
            status = "La voce scelta non appartiene alla lista dei ${section.label}."
            return false
        }

        val progressionIds =
            character.progression.selectedFeatureIds + character.progression.featIds
        val excluded = if (selected) {
            character.excludedTraitIds - abilityId
        } else if (abilityId in progressionIds) {
            character.excludedTraitIds + abilityId
        } else {
            character.excludedTraitIds - abilityId
        }
        val linked = if (selected) {
            (character.abilityIds + abilityId).distinct()
        } else {
            character.abilityIds.filterNot { it == abilityId }
        }
        character = guidedCharacters.withRefreshedEffects(
            character.copy(
                abilityIds = linked,
                excludedTraitIds = excluded,
            ),
            abilityCatalog,
        )
        status = if (selected) {
            "«${ability.name}» aggiunto alla scheda."
        } else {
            "«${ability.name}» rimosso dalla scheda."
        }
        return true
    }

    /** Crea o aggiorna una capacità del Compendio senza toccare la scheda aperta. */
    fun upsertAbility(ability: CatalogAbility): Boolean {
        if (ability.immutable || bundledSrdAbilities.any { it.id == ability.id }) {
            status = "Il contenuto SRD è in sola lettura. Duplicalo per creare una variante personale."
            return false
        }
        return guard("Abilità salvata.") {
        // La conversione applica in un solo punto tutte le validazioni meccaniche.
            ability.toDefinition()
            val updatedLibrary = library.copy(
                abilities = library.abilities.filterNot { it.id == ability.id } + ability,
            )
            store.save(updatedLibrary)
            library = updatedLibrary
            onAbilitiesChanged?.invoke()
        }
    }

    /**
     * Riclassifica una capacità fra tratto permanente e capacità da spendere.
     *
     * Una voce personale cambia in se stessa. Una voce del pacchetto SRD non si
     * tocca: la scelta viene annotata a parte, e riportarla al valore del
     * pacchetto cancella l'annotazione invece di lasciarne una identica.
     */
    fun setAbilityPassive(abilityId: String, passive: Boolean): Boolean {
        val own = library.abilities.firstOrNull { it.id == abilityId }
        if (own != null && !own.immutable && bundledSrdAbilities.none { it.id == abilityId }) {
            return upsertAbility(own.copy(passive = passive))
        }
        val bundled = bundledSrdAbilities.firstOrNull { it.id == abilityId }
        if (bundled == null) {
            status = "Abilità non trovata nel Compendio."
            return false
        }
        val updated = if (bundled.passive == passive) {
            library.passiveOverrides - abilityId
        } else {
            library.passiveOverrides + (abilityId to passive)
        }
        if (updated == library.passiveOverrides) return true
        val message = if (passive) {
            "«${bundled.name}» ora vale come tratto permanente."
        } else {
            "«${bundled.name}» torna fra le capacità da spendere nel turno."
        }
        return guard(message) {
            val updatedLibrary = library.copy(passiveOverrides = updated)
            store.save(updatedLibrary)
            library = updatedLibrary
            onAbilitiesChanged?.invoke()
        }
    }

    /** Vero quando la classificazione di una capacità SRD e' stata cambiata qui. */
    fun abilityPassiveIsOverridden(abilityId: String): Boolean =
        abilityId in library.passiveOverrides

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
        if (bundledSrdAbilities.any { it.id == id }) {
            status = "Il contenuto SRD incluso non può essere eliminato."
            return false
        }
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
     * Roster iniziale: il contenuto delle tre partite incluse.
     *
     * Le squadre e il loro bestiario entrano nel Compendio con gli stessi
     * identificatori che usano i template, cosi' chi ne sceglie uno ritrova le
     * schede al posto di riferimenti a vuoto. Sono dodici personaggi, uno per
     * ciascuna classe dello SRD. Le capacita' restano fuori dall'archivio: quelle
     * SRD le distribuisce il content pack, non il file dell'utente.
     */
    private fun seeded(): SheetLibrary = SheetLibrary(
        characters = SessionTemplates.all.flatMap { it.party },
        monsters = SessionTemplates.all.flatMap { it.monsters },
        abilities = emptyList(),
    )

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
                character = CharacterSheet(
                    id = "pg-$stamp",
                    armorClassMethod = ArmorClassMethod.UNARMORED,
                )
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

private val bundledSrdAbilities: List<CatalogAbility> by lazy {
    Srd521ItContent.catalog
}
