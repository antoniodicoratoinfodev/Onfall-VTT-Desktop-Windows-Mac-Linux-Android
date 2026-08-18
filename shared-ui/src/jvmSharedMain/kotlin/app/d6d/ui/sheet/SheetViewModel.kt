package app.d6d.ui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.ui.content.guidedCharacterService
import app.d6d.ui.content.guidedCharacterServiceFor
import app.d6d.ui.content.srdCatalog
import app.d6d.ui.content.srdCatalogFor
import app.d6d.ui.content.regeneratedIn
import app.d6d.ui.content.retranslatedTo
import app.d6d.ui.content.srdPack
import app.d6d.content.srd521it.SrdBeastForm
import app.d6d.content.srd521it.SrdBeasts
import app.d6d.content.srd521it.SrdChoiceOption
import app.d6d.content.srd521it.SrdChoiceResolver
import app.d6d.domain.combat.AbilityEffect
import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.Ability
import app.d6d.rules.character.BackgroundDefinition
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.SpellcastingKind
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CatalogAbility
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.LocalizedText
import app.d6d.ui.i18n.literalText
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.SheetLibrary
import app.d6d.sheet.SheetStore
import app.d6d.sheet.i18n.localizedSheetError
import app.d6d.ui.content.SessionTemplates
import java.io.IOException

/** Quale delle due schede si sta redigendo. */
enum class SheetKind {
    PERSONAGGIO,
    MOSTRO,
}

fun SheetKind.label(strings: Strings): String = when (this) {
    SheetKind.PERSONAGGIO -> strings.compendium.characters
    SheetKind.MOSTRO -> strings.sheet.monsters
}

/** Sezione strutturata della scheda modificabile dal Compendio. */
enum class CharacterTraitSection(
    val catalogKinds: Set<RuleElementKind>,
) {
    FEATURE(
        setOf(
            RuleElementKind.CLASS_FEATURE,
            RuleElementKind.SUBCLASS_FEATURE,
            RuleElementKind.CLASS_OPTION,
            RuleElementKind.METAMAGIC,
            RuleElementKind.ELDRITCH_INVOCATION,
        ),
    ),
    FEAT(
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
    private val guidedCharacters: GuidedCharacterService get() = guidedCharacterService

    val srdClasses get() = srdPack.classes

    fun backgroundDefinition(id: String): BackgroundDefinition? =
        srdPack.background(id)

    /** Nome delle classi guidate derivato dagli ID, quindi sempre nella lingua corrente. */
    fun displayedClassName(sheet: CharacterSheet = character): String {
        if (!sheet.progression.configured) return sheet.className
        return sheet.progression.classLevels.joinToString(" / ") { classLevel ->
            val name = srdPack.classes.firstOrNull { it.id == classLevel.classId }?.name
                ?: classLevel.classId.name
            "$name ${classLevel.level}"
        }.ifBlank { sheet.className }
    }

    /** Come la classe, una sottoclasse guidata è contenuto SRD e non testo libero. */
    fun displayedSubclassName(sheet: CharacterSheet = character): String {
        if (!sheet.progression.configured || sheet.progression.subclasses.isEmpty()) {
            return sheet.subclass
        }
        val savedNames = sheet.subclass.split(" / ")
        return sheet.progression.subclasses.mapIndexed { index, subclass ->
            srdPack.element(subclass.subclassId)?.name
                ?: savedNames.getOrNull(index)
                ?: subclass.subclassId
        }.joinToString(" / ")
    }

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
                    ?.takeIf { ability.effect == AbilityEffect.NONE }
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

    /**
     * Esito dell'ultima operazione sull'archivio.
     *
     * Come il messaggio del tavolo, conserva il modo di dire la frase e non la
     * frase: cosi' un «Scheda salvata.» rimasto a schermo diventa «Sheet saved.»
     * appena si cambia lingua, invece di restare indietro.
     */
    private var statusText by mutableStateOf<LocalizedText?>(null)

    val status: String? get() = statusText?.resolve(AppLocale.current)

    private fun say(text: LocalizedText?) {
        statusText = text
    }

    /**
     * Riporta nella lingua corrente la scheda aperta **e tutto l'archivio**.
     *
     * Tradurre la sola scheda aperta sarebbe peggio che non tradurre niente:
     * [isDirty] la confronta con la copia dentro [library], e una copia rimasta
     * nella lingua di prima farebbe risultare non salvato un personaggio che
     * nessuno ha toccato — con l'avviso di modifiche pendenti che blocca la
     * navigazione. Le due cose si muovono insieme, sempre.
     *
     * L'archivio si traduce **e si riscrive**, in un colpo solo: e' una
     * migrazione dichiarata. Tenerla in memoria sembrava piu' prudente ma era
     * incoerente — ogni salvataggio riscrive tutta la libreria, quindi la
     * traduzione finiva su disco lo stesso, per meta' e per caso. Il dettaglio
     * sta in [alignSheetLanguage].
     *
     * Il messaggio invece si scarta: gli errori letterali non si ritraducono.
     */
    internal fun onLanguageChanged() {
        say(null)
        alignSheetLanguage()
    }

    /**
     * Allinea archivio e scheda aperta alla lingua corrente, **e lo scrive**.
     *
     * Si chiama al caricamento oltre che al cambio lingua: un archivio scritto
     * in italiano e riaperto con la preferenza inglese deve mostrarsi in inglese
     * subito. Ogni scheda porta con se' la lingua del proprio testo, quindi un
     * archivio misto si allinea voce per voce.
     *
     * Il salvataggio qui e' una migrazione esplicita e atomica, e c'e' per una
     * ragione precisa. Tenere la traduzione solo in memoria sembrava piu'
     * prudente, ma non lo era: `save`, `delete`, la sincronizzazione dal
     * combattimento e il ripristino dei contenuti inclusi riscrivono **tutta**
     * la libreria, quindi la traduzione finiva su disco lo stesso — per meta',
     * quando capitava, e come effetto collaterale del salvataggio di un mostro.
     * Fra una scrittura dichiarata e una accidentale, la dichiarata e' l'unica
     * difendibile. La conversione e' senza perdita: `SheetTranslationTest` lo
     * verifica con un giro di andata e ritorno.
     *
     * Se il disco non e' scrivibile la traduzione resta comunque in memoria:
     * leggere in inglese non deve dipendere dal permesso di scrittura.
     */
    private fun alignSheetLanguage() {
        val target = AppLocale.language
        // Si traduce prima e si confronta poi. Guardare il solo marcatore non
        // basta: una creatura che l'utente ha modificato non si rigenera *e*
        // conserva il proprio marcatore, quindi resterebbe «da allineare» per
        // sempre, facendo riscrivere l'archivio a ogni avvio — e ruotare il
        // backup su una modifica che non c'e'.
        val aligned = library.copy(
            characters = library.characters.map { it.retranslatedTo(target) },
            monsters = library.monsters.map { it.regeneratedIn(target) },
        )
        if (aligned != library) {
            library = aligned
            runCatching { store.save(library) }
        }
        if (monster.contentLanguage != target) {
            monster = monster.regeneratedIn(target)
        }
        pristineNewMonster = pristineNewMonster?.regeneratedIn(target)
        if (character.contentLanguage != target) {
            character = character.retranslatedTo(target)
        }
        pristineNewCharacter = pristineNewCharacter?.retranslatedTo(target)
    }

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
            // Gli effetti dei privilegi sono derivati dal pacchetto: una scheda
            // salvata prima che esistessero, o con un pacchetto piu' vecchio, li
            // ha vuoti. Si ricalcolano leggendo, e si riscrivono solo se davvero
            // cambiati, cosi' un archivio gia' allineato non viene toccato.
            //
            // Ogni scheda si rigenera col pacchetto della **propria** lingua, non
            // di quella a schermo: la pulizia del testo generato confronta con i
            // nomi del pacchetto, e col pacchetto sbagliato non riconosce nulla
            // di cio' che trova — quindi non ricalcolerebbe, riscriverebbe. Per
            // giunta prima dell'allineamento linguistico, e con la possibilita'
            // di finire su disco poche righe piu' sotto.
            //
            // Il catalogo segue il servizio, per la stessa ragione e con un danno
            // peggiore: gli effetti si fondono per uguaglianza, e uno derivato dal
            // pacchetto della scheda accanto al suo gemello preso dal catalogo
            // dell'altra lingua non e' un doppione — differisce nel nome della
            // sorgente. Restavano entrambi; l'allineamento poi traduceva il primo
            // nel secondo e li rendeva identici, ma ormai erano due. Da li' ogni
            // riapertura riscriveva l'archivio, e un privilegio contava due volte.
            val refreshCatalogs = HashMap<AppLanguage, List<CatalogAbility>>()
            fun refreshCatalogFor(language: AppLanguage) = refreshCatalogs.getOrPut(language) {
                (fromDisk.abilities + srdCatalogFor(language)).distinctBy { it.id }
            }
            val refreshed = fromDisk.copy(
                characters = fromDisk.characters.map { sheet ->
                    val service = guidedCharacterServiceFor(sheet.contentLanguage)
                    service.withoutGeneratedFeatureText(
                        service.withRefreshedEffects(sheet, refreshCatalogFor(sheet.contentLanguage)),
                    )
                },
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
            say { it.sheet.diskError(failure.message.orEmpty()) }
            return SheetNavigationResult.FAILED
        } catch (failure: IllegalArgumentException) {
            say {
                it.sheet.invalidSheet(
                    localizedSheetError(failure.message.orEmpty(), it.language),
                )
            }
            return SheetNavigationResult.FAILED
        }

        // Il nuovo archivio diventa visibile soltanto dopo che lettura (ed eventuale
        // prima scrittura del seed) sono terminate con successo.
        library = loaded
        initialized = true
        // Prima di selezionare: la selezione copia dalla libreria nella scheda
        // aperta, e copiare da un archivio non allineato riporterebbe dentro il
        // testo nella lingua sbagliata.
        alignSheetLanguage()
        // Da `library`, non da `loaded`: la selezione copia la scheda dentro
        // quella aperta, e `loaded` e' ancora com'era su disco.
        when (kind) {
            SheetKind.PERSONAGGIO -> library.characters.firstOrNull()?.let(::selectCharacterInternal)
                ?: newSheetInternal()
            SheetKind.MOSTRO -> library.monsters.firstOrNull()?.let(::selectMonsterInternal)
                ?: newSheetInternal()
        }
        say { it.sheet.archiveLoaded }
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
        say(null)
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
        say(null)
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
        say(null)
        return SheetNavigationResult.APPLIED
    }

    fun newSheet(discardUnsavedChanges: Boolean = false): SheetNavigationResult {
        if (isDirty && !discardUnsavedChanges) return unsavedResult()
        newSheetInternal()
        say { it.sheet.newSheetFillAndSave }
        return SheetNavigationResult.APPLIED
    }

    fun save(): Boolean = guard(LocalizedText { it.sheet.sheetSaved }) {
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

    fun delete(id: String): Boolean = guard(LocalizedText { it.sheet.sheetDeleted }) {
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
    fun upsertCharacterSilently(sheet: CharacterSheet): Boolean = guard(LocalizedText { it.sheet.sheetUpdatedFromBattle }) {
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

    fun upsertMonsterSilently(block: MonsterStatBlock): Boolean = guard(LocalizedText { it.sheet.statBlockUpdatedFromBattle }) {
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
        return guard(LocalizedText { it.sheet.templateSheetsRestored }) {
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
        character = character.recoveredAfter(period)
        say(
            if (period == RecoveryPeriod.LONG_REST) {
                LocalizedText { it.sheet.longRestResourcesRecovered }
            } else {
                LocalizedText { it.sheet.shortRestResourcesRecovered }
            },
        )
    }

    /** Forme attualmente apprese tramite le scelte di Forma selvatica. */
    fun knownWildShapeForms(): List<SrdBeastForm> =
        character.progression.selectedFeatureIds.mapNotNull { id ->
            SrdBeasts.byId(id, AppLocale.language)
        }

    /** Forme legali al livello attuale che il druido non conosce ancora. */
    fun wildShapeReplacementOptions(): List<SrdBeastForm> {
        val knownIds = knownWildShapeForms().mapTo(mutableSetOf()) { it.id }
        return SrdBeasts.availableAt(
            character.progression.levelIn(CharacterClassId.DRUID),
            AppLocale.language,
        )
            .filterNot { it.id in knownIds }
    }

    /**
     * Completa il riposo lungo e usa l'unica sostituzione di forma concessa al
     * suo termine. Le due modifiche sono preparate insieme: una scelta invalida
     * non recupera silenziosamente le risorse.
     */
    fun longRestAndReplaceWildShapeForm(oldFormId: String, newFormId: String): Boolean =
        runCatching {
            val legalIds = SrdBeasts
                .availableAt(
                    character.progression.levelIn(CharacterClassId.DRUID),
                    AppLocale.language,
                )
                .mapTo(mutableSetOf()) { it.id }
            guidedCharacters.replaceSelectedOption(
                sheet = character,
                oldOptionId = oldFormId,
                newOptionId = newFormId,
                allowedOptionIds = legalIds,
            ).recoveredAfter(RecoveryPeriod.LONG_REST)
        }.fold(
            onSuccess = { updated ->
                character = updated
                val oldName = SrdBeasts.byId(oldFormId, AppLocale.language)?.name ?: oldFormId
                val newName = SrdBeasts.byId(newFormId, AppLocale.language)?.name ?: newFormId
                say { it.sheet.longRestFormSwapped(oldName, newName) }
                true
            },
            onFailure = { failure ->
                say(
                    failure.message?.let(::literalText)
                        ?: LocalizedText { it.sheet.cannotSwapKnownForm },
                )
                false
            },
        )

    private fun CharacterSheet.recoveredAfter(period: RecoveryPeriod): CharacterSheet {
        val restored = progression.resourcePools.map { it.recoveredAfter(period) }
        val casting = spellcasting
        return copy(
            progression = progression.copy(resourcePools = restored),
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
            language = AppLocale.language,
        )

    fun fixedHitPointIncrease(classId: CharacterClassId): Int =
        guidedCharacters.fixedHitPointIncrease(character, classId)

    fun advanceCharacter(request: LevelUpRequest): Boolean {
        // Il livello si applica sempre col pacchetto della lingua a schermo, ed e'
        // in quella lingua che il servizio scrive. La scheda va portata li' prima,
        // altrimenti ne esce una meta' italiana e meta' inglese: il servizio lo
        // pretende, e qui e' un accostamento a vuoto ogni volta che la scheda e'
        // gia' allineata — cioe' sempre, fuori dalle rotte impreviste.
        if (character.contentLanguage != AppLocale.language) {
            character = character.retranslatedTo(AppLocale.language)
        }
        val validation = guidedCharacters.validate(character, request)
        if (!validation.valid) {
            say(literalText(validation.issues.joinToString("\n") { it.message }))
            return false
        }
        character = guidedCharacters.withRefreshedEffects(
            guidedCharacters.advance(character, request),
            abilityCatalog,
        )
        val level = character.effectiveLevel
        say(
            if (level == 1) {
                LocalizedText { it.sheet.srdCharacterCreated }
            } else {
                LocalizedText { it.sheet.levelApplied(level) }
            },
        )
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
                candidate.name.startsWith(AppLocale.current.sheet.fightingStyle, ignoreCase = true)
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
            say { it.sheet.entryNotInSection(section.label(it)) }
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
        val abilityName = ability.name
        say(
            if (selected) {
                LocalizedText { it.sheet.abilityAddedToSheet(abilityName) }
            } else {
                LocalizedText { it.sheet.abilityRemovedFromSheet(abilityName) }
            },
        )
        return true
    }

    /** Crea o aggiorna una capacità del Compendio senza toccare la scheda aperta. */
    fun upsertAbility(ability: CatalogAbility): Boolean {
        if (ability.immutable || bundledSrdAbilities.any { it.id == ability.id }) {
            say { it.sheet.bundledSrdReadOnly }
            return false
        }
        return guard(LocalizedText { it.sheet.abilitySaved }) {
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
        val bundled = bundledSrdAbilities.firstOrNull { it.id == abilityId }
        val classified = own ?: bundled
        if (passive && classified != null && classified.effect != AbilityEffect.NONE) {
            say { it.sheet.automaticAbilityMustStayActive }
            return false
        }
        if (own != null && !own.immutable && bundledSrdAbilities.none { it.id == abilityId }) {
            return upsertAbility(own.copy(passive = passive))
        }
        if (bundled == null) {
            say { it.sheet.abilityNotInCompendium }
            return false
        }
        val updated = if (bundled.passive == passive) {
            library.passiveOverrides - abilityId
        } else {
            library.passiveOverrides + (abilityId to passive)
        }
        if (updated == library.passiveOverrides) return true
        val bundledName = bundled.name
        val message = if (passive) {
            LocalizedText { it.sheet.abilityBecamePassive(bundledName) }
        } else {
            LocalizedText { it.sheet.abilityBecameActive(bundledName) }
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
            say { it.sheet.bundledSrdCannotBeDeleted }
            return false
        }
        val usedBy = abilityUsageCount(id)
        if (usedBy > 0) {
            say { it.sheet.cannotDeleteAbilityInUse(usedBy.toString()) }
            return false
        }
        return guard(LocalizedText { it.sheet.abilityDeleted }) {
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
        characters = SessionTemplates.of(AppLocale.language).all.flatMap { it.party },
        monsters = SessionTemplates.of(AppLocale.language).all.flatMap { it.monsters },
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
                    // Esplicita: il predefinito del modello e' l'italiano, che e'
                    // giusto per le schede salvate prima che il campo esistesse
                    // ma sbagliato per una scheda che nasce adesso. Senza questo,
                    // un personaggio creato in inglese veniva salvato come testo
                    // inglese marcato italiano, e al riavvio non si traduceva.
                    contentLanguage = AppLocale.language,
                )
                pristineNewCharacter = character
            }
            SheetKind.MOSTRO -> {
                // Come per il personaggio: una creatura che nasce adesso nasce
                // nella lingua di adesso, non nel predefinito del modello.
                monster = MonsterStatBlock(
                    id = "mostro-$stamp",
                    contentLanguage = AppLocale.language,
                )
                pristineNewMonster = monster
            }
        }
    }

    private fun unsavedResult(): SheetNavigationResult {
        say { it.sheet.unsavedChangesPrompt }
        return SheetNavigationResult.UNSAVED_CHANGES
    }

    private fun notFoundResult(): SheetNavigationResult {
        say { it.sheet.sheetNotFound }
        return SheetNavigationResult.NOT_FOUND
    }

    /**
     * Esegue l'operazione e ne racconta l'esito.
     *
     * Il successo arriva come [LocalizedText] e non come frase gia' fatta: e' lo
     * stesso motivo per cui [status] non conserva una stringa.
     */
    private fun guard(success: LocalizedText, block: () -> Unit): Boolean {
        var succeeded = false
        say(
            try {
                block()
                succeeded = true
                success
            } catch (failure: IOException) {
                LocalizedText { it.sheet.diskError(failure.message.orEmpty()) }
            } catch (failure: IllegalArgumentException) {
                LocalizedText {
                    it.sheet.invalidSheet(
                        localizedSheetError(failure.message.orEmpty(), it.language),
                    )
                }
            },
        )
        return succeeded
    }
}

private val bundledSrdAbilities: List<CatalogAbility> get() = srdCatalog

/**
 * Nome della sezione, per le frasi che la nominano.
 *
 * Vive qui e non nell'enum perche' il nome e' testo da mostrare, mentre l'enum
 * porta solo cio' che decide il comportamento: quali categorie del catalogo la
 * sezione accetta.
 */
fun CharacterTraitSection.label(strings: Strings): String = when (this) {
    CharacterTraitSection.FEATURE -> strings.sheet.sectionFeatures
    CharacterTraitSection.FEAT -> strings.sheet.sectionFeats
}
