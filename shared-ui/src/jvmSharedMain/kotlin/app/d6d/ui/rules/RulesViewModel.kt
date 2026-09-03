package app.d6d.ui.rules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.content.srd521it.Srd521Ruleset
import app.d6d.content.srd521it.SrdRulesetCharacterAdapter
import app.d6d.rules.authoring.AuthoringMode
import app.d6d.rules.authoring.FormulaDraft
import app.d6d.rules.authoring.ProjectionStatus
import app.d6d.rules.authoring.RuleAuthoringMetadata
import app.d6d.rules.authoring.RulesetAuthoringState
import app.d6d.rules.model.CoreRuleIds
import app.d6d.rules.model.GenericRulesetFoundation
import app.d6d.rules.model.LocalizedRuleText
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleFieldRef
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulePatch
import app.d6d.rules.model.RulesetDraft
import app.d6d.rules.model.RulesetComposer
import app.d6d.rules.model.RulesetCanonicalizer
import app.d6d.rules.model.RulesetCompiler
import app.d6d.rules.model.RulesetCompositionException
import app.d6d.rules.model.RulesetCompositionIssue
import app.d6d.rules.model.RulesetCompositionResult
import app.d6d.rules.model.RulesetConflictResolution
import app.d6d.rules.model.RulesetModule
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.model.RulesetRevision
import app.d6d.rules.model.RulesetRuntimeConfig
import app.d6d.rules.persistence.LocalRulesetRepository
import app.d6d.ui.i18n.AppLocale
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

enum class RulesetOriginFilter { ALL, STANDARD, HOMEBREW }
enum class RuleEnabledFilter { ALL, ENABLED, DISABLED }
enum class DraftEntityChange { INHERITED, MODIFIED, ADDED }

data class DraftChangeSummary(val modified: Int, val added: Int, val disabled: Int)

data class ModuleCompositionDraft(
    val baseCanonicalHash: String,
    val orderedModuleHashes: List<String>,
    val resolutions: List<RulesetConflictResolution>,
    val runtime: RulesetRuntimeConfig,
    val name: String,
    val description: String,
    val version: String,
)

data class CompositionFieldChange(
    val path: String,
    val before: String?,
    val after: String?,
)

private data class AuthoringGroupSnapshot(
    val id: String,
    val metadata: RuleAuthoringMetadata,
)

data class RulesetChoice(
    val key: String,
    val name: String,
    val version: String,
    val origin: RulesetOrigin,
    val draftId: String? = null,
    val revision: RulesetRevision,
) {
    val isDraft: Boolean get() = draftId != null
    val readOnly: Boolean get() = origin == RulesetOrigin.BUNDLED_STANDARD
}

/** Stato del catalogo Regole e dei fork homebrew locali. */
class RulesViewModel(dataDirectory: Path) {
    private val standard = Srd521Ruleset.revision
    private val blankFoundation = GenericRulesetFoundation.revision()

    var status by mutableStateOf<String?>(null)
        private set

    private val repository: LocalRulesetRepository? = runCatching {
        LocalRulesetRepository(dataDirectory.resolve("rulesets"), listOf(standard, blankFoundation))
    }.onFailure { status = it.message }.getOrNull()

    private var storedRevisions by mutableStateOf<List<RulesetRevision>>(listOf(standard, blankFoundation))
    private var storedDrafts by mutableStateOf<List<RulesetDraft>>(emptyList())
    private var storedModules by mutableStateOf<List<RulesetModule>>(emptyList())
    private var storedAuthoring by mutableStateOf(RulesetAuthoringState.empty())

    var moduleComposition by mutableStateOf<ModuleCompositionDraft?>(null)
        private set
    var compositionIssues by mutableStateOf<List<RulesetCompositionIssue>>(emptyList())
        private set
    var compositionError by mutableStateOf<String?>(null)
        private set
    var compositionPreview by mutableStateOf<RulesetCompositionResult?>(null)
        private set
    var compositionChanges by mutableStateOf<List<CompositionFieldChange>>(emptyList())
        private set

    /** Revisioni stabili selezionabili da una nuova partita; le bozze non entrano nel runtime. */
    val publishedRevisions: List<RulesetRevision> get() = storedRevisions
    val installedModules: List<RulesetModule> get() = storedModules

    fun preferredAuthoringMode(entityId: String): AuthoringMode? {
        val raw = validAuthoringGroup(entityId)?.metadata?.visualSections()?.get("mode") ?: return null
        return runCatching { AuthoringMode.valueOf(raw) }.getOrNull()
    }

    fun protectedAuthoringFields(entityId: String): Set<String> {
        val entity = selected?.revision?.entity(entityId) ?: return emptySet()
        return validAuthoringGroup(entityId)?.metadata?.protectedFields().orEmpty() +
            projectedProtectedFields(entity)
    }
    val canPublishComposition: Boolean
        get() = moduleComposition?.let {
            it.orderedModuleHashes.isNotEmpty() && it.name.isNotBlank() && it.version.isNotBlank() &&
                compositionIssues.isEmpty() && compositionError == null && compositionPreview != null
        } == true
    val compositionHasLegacyRuntimeControls: Boolean
        get() = moduleComposition?.let { draft ->
            storedRevisions.firstOrNull { it.canonicalHash() == draft.baseCanonicalHash }
                ?.entities().orEmpty().any { it.enabled() && it.id() in legacyRuntimeEntityIds }
        } == true

    val selectedCompositionLock
        get() = selected?.takeUnless { it.isDraft }?.let {
            repository?.findCompositionLock(it.revision.canonicalHash())
        }
    val selectedCompositionBaseAvailable: Boolean
        get() = selectedCompositionLock?.let { lock ->
            storedRevisions.any { it.canonicalHash() == lock.baseCanonicalHash() }
        } == true
    val canExportSelectedBundle: Boolean
        get() = selected?.isDraft == false && selectedCompositionLock != null

    /**
     * Default esplicito del catalogo. L'ordine delle revisioni e il nome localizzato
     * non hanno semantica: in particolare la fondazione vuota è una base di
     * authoring, non deve diventare il regolamento di una scheda o partita legacy.
     */
    val defaultPublishedRevisionHash: String get() = standard.canonicalHash()

    var selectedKey by mutableStateOf(standard.canonicalHash())
        private set

    var selectedEntityId by mutableStateOf<String?>(null)
        private set

    var originFilter by mutableStateOf(RulesetOriginFilter.ALL)
        private set
    var kindFilter by mutableStateOf<RuleKind?>(null)
    internal var intentFamilyFilter by mutableStateOf<RuleIntentFamily?>(null)
    var automationFilter by mutableStateOf<RuleAutomationLevel?>(null)
    var enabledFilter by mutableStateOf(RuleEnabledFilter.ALL)
    var search by mutableStateOf("")
    var showGeneratedParts by mutableStateOf(false)

    init {
        reload()
    }

    val choices: List<RulesetChoice>
        get() {
            val published = storedRevisions.map { revision ->
                RulesetChoice(
                    key = revision.canonicalHash(),
                    name = revision.name(),
                    version = revision.version(),
                    origin = revision.origin(),
                    revision = revision,
                )
            }
            val drafts = storedDrafts.mapNotNull { draft ->
                runCatching { repository?.preview(draft.id()) }.getOrNull()?.let { preview ->
                    RulesetChoice(
                        key = draft.id(),
                        name = draft.name(),
                        version = "draft",
                        origin = draft.origin(),
                        draftId = draft.id(),
                        revision = preview,
                    )
                }
            }
            return (published + drafts)
                .filter { choice ->
                    when (originFilter) {
                        RulesetOriginFilter.ALL -> true
                        RulesetOriginFilter.STANDARD -> choice.origin == RulesetOrigin.BUNDLED_STANDARD
                        RulesetOriginFilter.HOMEBREW -> choice.origin != RulesetOrigin.BUNDLED_STANDARD
                    }
                }
                .sortedWith(compareBy<RulesetChoice> { it.readOnly.not() }.thenBy { it.name.lowercase() })
        }

    val selected: RulesetChoice?
        get() = (storedRevisions.map { revision ->
            RulesetChoice(
                revision.canonicalHash(), revision.name(), revision.version(), revision.origin(), null, revision,
            )
        } + storedDrafts.mapNotNull { draft ->
            runCatching { repository?.preview(draft.id()) }.getOrNull()?.let { preview ->
                RulesetChoice(draft.id(), draft.name(), "draft", draft.origin(), draft.id(), preview)
            }
        }).firstOrNull { it.key == selectedKey }

    val selectedEntity: RuleEntity?
        get() = selected?.revision?.entity(selectedEntityId ?: "")

    val selectedEntityChange: DraftEntityChange
        get() {
            val draft = selectedDraft() ?: return DraftEntityChange.INHERITED
            val id = selectedEntityId ?: return DraftEntityChange.INHERITED
            return when {
                draft.additions().any { it.id() == id } -> DraftEntityChange.ADDED
                draft.patches().any { it.targetEntityId() == id } -> DraftEntityChange.MODIFIED
                else -> DraftEntityChange.INHERITED
            }
        }

    val draftChangeSummary: DraftChangeSummary?
        get() {
            val draft = selectedDraft() ?: return null
            val preview = selected?.revision ?: return null
            return DraftChangeSummary(
                modified = draft.patches().size,
                added = draft.additions().size,
                disabled = preview.entities().count { !it.enabled() },
            )
        }

    /** I controlli legacy compaiono soltanto se il regolamento li dichiara. */
    val hasLegacyRuntimeControls: Boolean
        get() = selected?.revision?.entities().orEmpty().any {
            it.enabled() && it.id() in legacyRuntimeEntityIds
        }

    val visibleEntities: List<RuleEntity>
        get() {
            val query = search.trim().lowercase()
            val language = AppLocale.language.tag
            return selected?.revision?.entities().orEmpty().filter { entity ->
                (showGeneratedParts || !isGeneratedHelper(entity.id())) &&
                    (intentFamilyFilter == null || intentFamilyFor(entity.kind()) == intentFamilyFilter) &&
                    (kindFilter == null || entity.kind() == kindFilter) &&
                    (automationFilter == null || entity.automationLevel() == automationFilter) &&
                    when (enabledFilter) {
                        RuleEnabledFilter.ALL -> true
                        RuleEnabledFilter.ENABLED -> entity.enabled()
                        RuleEnabledFilter.DISABLED -> !entity.enabled()
                    } &&
                    (query.isEmpty() || entity.name().text(language).lowercase().contains(query) ||
                        entity.description().text(language).lowercase().contains(query) ||
                        entity.id().lowercase().contains(query) ||
                        entity.kind().name.lowercase().contains(query) ||
                        entity.tags().any { it.lowercase().contains(query) } ||
                        entity.attributes().any { (key, value) ->
                            key.lowercase().contains(query) || value.lowercase().contains(query)
                        })
            }.sortedBy { it.name().text(language).lowercase() }
        }

    val hasGeneratedParts: Boolean
        get() = selected?.revision?.entities().orEmpty().any { isGeneratedHelper(it.id()) }

    fun authoringGroupMembers(entityId: String): List<RuleEntity> {
        val metadata = validAuthoringGroup(entityId)?.metadata ?: return emptyList()
        return metadata.generatedEntityIds().mapNotNull { selected?.revision?.entity(it) }
    }

    fun isGeneratedHelper(entityId: String): Boolean {
        val group = validAuthoringGroup(entityId) ?: return false
        val primaryId = group.id.removePrefix("entity:")
        return entityId != primaryId && primaryId in group.metadata.generatedEntityIds()
    }

    fun changeOriginFilter(filter: RulesetOriginFilter) {
        if (originFilter == filter) return
        originFilter = filter
        val visible = choices
        if (visible.none { it.key == selectedKey }) {
            cancelModuleComposition()
            selectedKey = visible.firstOrNull()?.key.orEmpty()
            selectedEntityId = null
        }
    }

    fun selectRuleset(key: String) {
        if (selectedKey == key) return
        cancelModuleComposition()
        selectedKey = key
        selectedEntityId = null
        status = null
    }

    fun selectEntity(id: String) {
        selectedEntityId = id
    }

    fun clearEntitySelection() {
        selectedEntityId = null
    }

    fun forkSelected(entityToEdit: String? = null) {
        val source = selected?.revision ?: return
        val repo = repository ?: return
        cancelModuleComposition()
        guarded {
            val draft = if (source.readOnly()) {
                val name = AppLocale.current.rules.forkName(source.name())
                repo.createHomebrew(source.canonicalHash(), name, source.description())
            } else {
                repo.createNextDraft(source.canonicalHash())
            }
            reload()
            selectedKey = draft.id()
            selectedEntityId = entityToEdit?.takeIf { source.entity(it) != null }
            status = AppLocale.current.rules.saved(draft.name())
        }
    }

    /** Crea sempre una nuova linea homebrew dalla revisione SRD inclusa, indipendentemente dalla selezione corrente. */
    fun createSrdBasedRuleset() {
        val repo = repository ?: return
        cancelModuleComposition()
        guarded {
            val words = AppLocale.current.rules
            val draft = repo.createHomebrew(
                standard.canonicalHash(),
                words.forkName(standard.name()),
                standard.description(),
            )
            reload()
            originFilter = RulesetOriginFilter.HOMEBREW
            selectedKey = draft.id()
            selectedEntityId = null
            status = words.saved(draft.name())
        }
    }

    /** Crea una linea homebrew senza ereditare alcun contenuto SRD/D&D. */
    fun createBlankRuleset() {
        val repo = repository ?: return
        cancelModuleComposition()
        guarded {
            val words = AppLocale.current.rules
            val draft = repo.createHomebrew(
                blankFoundation.canonicalHash(),
                words.blankRulesetName,
                words.blankRulesetDescription,
            )
            reload()
            originFilter = RulesetOriginFilter.HOMEBREW
            selectedKey = draft.id()
            selectedEntityId = null
            status = words.saved(draft.name())
        }
    }

    fun updateRuntime(transform: (RulesetRuntimeConfig) -> RulesetRuntimeConfig) {
        val draft = selectedDraft() ?: return
        val repo = repository ?: return
        guarded {
            val changed = draft.withContent(
                draft.name(), draft.description(), transform(draft.runtime()),
                draft.patches(), draft.additions(), Instant.now().toString(),
            )
            repo.saveDraft(changed)
            reload(keepSelection = true)
        }
    }

    fun updateDraftMetadata(name: String, description: String) {
        val draft = selectedDraft() ?: return
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return
        val repo = repository ?: return
        guarded {
            repo.saveDraft(
                draft.withContent(
                    normalizedName,
                    description.trim(),
                    draft.runtime(),
                    draft.patches(),
                    draft.additions(),
                    Instant.now().toString(),
                ),
            )
            reload(keepSelection = true)
            status = AppLocale.current.rules.saved(normalizedName)
        }
    }

    fun updateEntity(
        entityId: String,
        name: String,
        description: String,
        kind: RuleKind,
        automation: RuleAutomationLevel,
        enabled: Boolean,
        attributes: Map<String, String>,
        tags: List<String>,
        replaceAttributes: Boolean = false,
        authoringMode: AuthoringMode = AuthoringMode.EXPERT,
    ): Boolean {
        val draft = selectedDraft() ?: return false
        val repo = repository ?: return false
        val effective = selected?.revision?.entity(entityId) ?: return false
        val language = AppLocale.language.tag
        return guardedResult {
            val changedName = effective.name().withText(language, name)
            val changedDescription = effective.description().withText(language, description)
            val additionIndex = draft.additions().indexOfFirst { it.id() == entityId }
            if (additionIndex >= 0) {
                // Gli editor specializzati inviano l'intero schema, mentre API e
                // test possono aggiornare soltanto i campi interessati. Una
                // regola appena creata non deve perdere gli identificativi e i
                // default eseguibili che non compaiono nell'aggiornamento.
                val storedAttributes = if (kind == effective.kind()) {
                    if (replaceAttributes) attributes else effective.attributes() + attributes
                } else {
                    defaultAttributes(kind, entityId) + attributes
                }
                val additions = draft.additions().toMutableList()
                val updatedEntity = RuleEntity(
                    effective.id(), kind, effective.origin(), changedName, changedDescription,
                    effective.derivedFrom(), enabled, automation, storedAttributes, tags,
                    effective.source(), effective.license(), effective.sourcePage(),
                )
                additions[additionIndex] = updatedEntity
                val changed = draft.withContent(
                        draft.name(), draft.description(),
                        runtimeAfterEntityEdit(draft.runtime(), entityId, storedAttributes),
                        draft.patches(),
                        additions, Instant.now().toString(),
                    )
                repo.saveDraft(changed, authoringAfterEdit(draft.id(), updatedEntity, authoringMode))
                reload(keepSelection = true)
                selectedEntityId = entityId
                status = AppLocale.current.rules.saved(draft.name())
                return@guardedResult
            }

            val baseEntity = repo.findRevision(draft.baseCanonicalHash())?.entity(entityId)
                ?: error("La regola di base non è più disponibile")
            val patches = draft.patches().toMutableList()
            val index = patches.indexOfFirst { it.targetEntityId() == entityId }
            val old = patches.getOrNull(index)
            val submittedAttributes = if (replaceAttributes || kind != effective.kind()) {
                attributes
            } else {
                effective.attributes() + attributes
            }
            val patch = RulePatch(
                old?.id() ?: "patch:${UUID.randomUUID()}",
                entityId,
                changedName,
                changedDescription,
                submittedAttributes,
                baseEntity.attributes().keys - submittedAttributes.keys,
                enabled,
                kind,
                automation,
                tags,
            )
            if (index >= 0) patches[index] = patch else patches += patch
            val changed = draft.withContent(
                    draft.name(), draft.description(),
                    runtimeAfterEntityEdit(draft.runtime(), entityId, attributes),
                    patches,
                    draft.additions(), Instant.now().toString(),
            )
            val updatedEntity = RuleEntity(
                effective.id(), kind, effective.origin(), changedName, changedDescription,
                effective.derivedFrom(), enabled, automation, submittedAttributes, tags,
                effective.source(), effective.license(), effective.sourcePage(),
            )
            repo.saveDraft(changed, authoringAfterEdit(draft.id(), updatedEntity, authoringMode))
            reload(keepSelection = true)
            selectedEntityId = entityId
            status = AppLocale.current.rules.saved(draft.name())
        }
    }

    fun resetSelectedEntityChange() {
        val draft = selectedDraft() ?: return
        val entityId = selectedEntityId ?: return
        val repo = repository ?: return
        guarded {
            val change = selectedEntityChange
            val changed = when (change) {
                DraftEntityChange.INHERITED -> return@guarded
                DraftEntityChange.MODIFIED -> draft.withContent(
                    draft.name(), draft.description(), draft.runtime(),
                    draft.patches().filterNot { it.targetEntityId() == entityId },
                    draft.additions(), Instant.now().toString(),
                )
                DraftEntityChange.ADDED -> draft.withContent(
                    draft.name(), draft.description(), draft.runtime(), draft.patches(),
                    draft.additions().filterNot { it.id() == entityId }, Instant.now().toString(),
                )
            }
            repo.saveDraft(
                changed,
                storedAuthoring.withoutGroup(
                    draft.id(),
                    rawAuthoringGroup(entityId)?.id ?: authoringGroupId(entityId),
                ),
            )
            reload(keepSelection = true)
            selectedEntityId = if (change == DraftEntityChange.ADDED) null else entityId
            status = AppLocale.current.rules.saved(draft.name())
        }
    }

    fun addRule(kind: RuleKind) {
        val italian = AppLocale.language.tag == "it"
        val name = when (kind) {
            RuleKind.CLASS -> if (italian) "Nuova classe" else "New class"
            RuleKind.MODIFIER -> if (italian) "Nuovo modificatore" else "New modifier"
            RuleKind.FEATURE -> if (italian) "Nuovo privilegio" else "New feature"
            else -> AppLocale.current.rules.customRuleName
        }
        addRule(kind, name, AppLocale.current.rules.customRuleDescription)
    }

    /** Le ricette possono creare più primitive già collegate senza esporre ID tecnici all'utente. */
    fun addGuidedRule(kind: RuleKind) {
        val italian = AppLocale.language.tag == "it"
        val name = when (kind) {
            RuleKind.ROLL -> if (italian) "Nuova prova" else "New check"
            RuleKind.CLASS -> if (italian) "Nuova classe" else "New class"
            RuleKind.MODIFIER -> if (italian) "Nuovo modificatore" else "New modifier"
            RuleKind.FEATURE -> if (italian) "Nuovo privilegio" else "New feature"
            else -> AppLocale.current.rules.customRuleName
        }
        addGuidedRule(kind, name, AppLocale.current.rules.customRuleDescription)
    }

    fun addGuidedRule(
        kind: RuleKind,
        name: String,
        description: String,
        initialAttributes: Map<String, String> = emptyMap(),
    ) {
        if (kind != RuleKind.ROLL) {
            addRule(kind, name.trim(), description.trim(), initialAttributes)
            return
        }
        val draft = selectedDraft() ?: return
        val repo = repository ?: return
        val italian = AppLocale.language.tag == "it"
        guarded {
            val randomizerId = "local:${RuleKind.RANDOMIZER.name.lowercase()}:${UUID.randomUUID()}"
            val rollId = "local:${RuleKind.ROLL.name.lowercase()}:${UUID.randomUUID()}"
            val randomizer = newRuleEntity(
                RuleKind.RANDOMIZER,
                randomizerId,
                if (italian) "Dadi della prova" else "Check dice",
                if (italian) "Generatore casuale collegato alla prova." else "Randomizer linked to the check.",
                initialAttributes.filterKeys { it in setOf("mode", "diceCount", "countFormula", "dieSides", "sidesFormula", "keep") },
            )
            val roll = newRuleEntity(
                RuleKind.ROLL,
                rollId,
                name.trim(),
                description.trim(),
                initialAttributes.filterKeys { it !in setOf("mode", "diceCount", "countFormula", "dieSides", "sidesFormula", "keep") } +
                    mapOf("randomizerRef" to randomizerId),
            )
            val changed = draft.withContent(
                draft.name(), draft.description(), draft.runtime(), draft.patches(),
                draft.additions() + listOf(randomizer, roll), Instant.now().toString(),
            )
            val metadata = RuleAuthoringMetadata(
                "builtin.roll",
                1,
                listOf(randomizerId, rollId),
                mapOf("mode" to AuthoringMode.GUIDED.name),
                emptySet(),
                mapOf(
                    randomizerId to RulesetCanonicalizer.entityContentHash(randomizer),
                    rollId to RulesetCanonicalizer.entityContentHash(roll),
                ),
                emptyList(),
            )
            repo.saveDraft(
                changed,
                storedAuthoring.withGroup(draft.id(), authoringGroupId(rollId), metadata),
            )
            reload(keepSelection = true)
            selectedEntityId = rollId
        }
    }

    /** Crea un modificatore già collegato alla regola selezionata. */
    fun addLinkedModifier(ownerEntityId: String) {
        val owner = selected?.revision?.entity(ownerEntityId) ?: return
        if (
            owner.kind() !in modifierOwnerKinds &&
            !(owner.kind() == RuleKind.CUSTOM && owner.attributes().containsKey("elementKind"))
        ) return
        val italian = AppLocale.language.tag == "it"
        addRule(
            RuleKind.MODIFIER,
            if (italian) "Modificatore di ${owner.name().text("it")}" else "${owner.name().text("en")} modifier",
            AppLocale.current.rules.customRuleDescription,
            mapOf("ownerRef" to owner.id()),
            legacyModifier = true,
        )
    }

    /** Collega un effetto del runtime generico a qualunque regola, non soltanto agli elementi D&D. */
    fun addGenericLinkedModifier(ownerEntityId: String) {
        val owner = selected?.revision?.entity(ownerEntityId) ?: return
        val italian = AppLocale.language.tag == "it"
        addRule(
            RuleKind.MODIFIER,
            if (italian) "Effetto di ${owner.name().text("it")}" else "${owner.name().text("en")} effect",
            AppLocale.current.rules.customRuleDescription,
            mapOf("ownerRef" to owner.id()),
        )
    }

    private fun newRuleEntity(
        kind: RuleKind,
        entityId: String,
        name: String,
        description: String,
        initialAttributes: Map<String, String> = emptyMap(),
        legacyModifier: Boolean = false,
    ): RuleEntity {
        val language = AppLocale.language.tag
        return RuleEntity(
            entityId, kind, RulesetOrigin.HOMEBREW,
            LocalizedRuleText.single(language, name), LocalizedRuleText.single(language, description),
            "", true,
            if (kind in executableKinds) RuleAutomationLevel.ASSISTED else RuleAutomationLevel.MANUAL,
            defaultAttributes(kind, entityId, legacyModifier) + initialAttributes, listOf("homebrew"),
            "Homebrew", "", 0,
        )
    }

    private fun addRule(
        kind: RuleKind,
        name: String,
        description: String,
        initialAttributes: Map<String, String> = emptyMap(),
        legacyModifier: Boolean = false,
    ): String? {
        val draft = selectedDraft() ?: return null
        val repo = repository ?: return null
        var createdId: String? = null
        guarded {
            val entityId = "local:${kind.name.lowercase()}:${UUID.randomUUID()}"
            val addition = newRuleEntity(
                kind, entityId, name, description, initialAttributes, legacyModifier,
            )
            repo.saveDraft(
                draft.withContent(
                    draft.name(), draft.description(), draft.runtime(), draft.patches(),
                    draft.additions() + addition, Instant.now().toString(),
                ),
            )
            reload(keepSelection = true)
            selectedEntityId = addition.id()
            createdId = addition.id()
        }
        return createdId
    }

    fun publishSelected(version: String) {
        val draft = selectedDraft() ?: return
        val normalized = version.trim()
        if (normalized.isEmpty()) return
        val repo = repository ?: return
        cancelModuleComposition()
        guarded {
            // La pubblicazione è il confine fra testo di catalogo e dati
            // eseguibili: classi, feature e modificatori devono proiettarsi senza
            // riferimenti rotti o valori non validi prima di diventare selezionabili.
            val preview = repo.preview(draft.id())
            // I regolamenti classless restano pubblicabili e giocabili in
            // modalità manuale. Se dichiarano almeno una classe, invece, il
            // contratto guidato deve essere interamente valido.
            preview.compile()
            SrdRulesetCharacterAdapter.validateExecutableLinks(preview, AppLocale.language)
            if (preview.entities().any { it.enabled() && it.kind() == RuleKind.CLASS }) {
                SrdRulesetCharacterAdapter.project(preview, AppLocale.language)
            }
            val published = repo.publish(draft.id(), normalized)
            reload()
            selectedKey = published.canonicalHash()
            status = AppLocale.current.rules.publishedAs(published.name(), published.version())
        }
    }

    /** Preflight non distruttivo: usa lo stesso compilatore e gli stessi vincoli della pubblicazione. */
    fun validateSelectedDraft(): Boolean {
        val draft = selectedDraft() ?: return false
        val repo = repository ?: return false
        return guardedResult {
            val preview = repo.preview(draft.id())
            preview.compile()
            SrdRulesetCharacterAdapter.validateExecutableLinks(preview, AppLocale.language)
            if (preview.entities().any { it.enabled() && it.kind() == RuleKind.CLASS }) {
                SrdRulesetCharacterAdapter.project(preview, AppLocale.language)
            }
            status = if (AppLocale.language.tag == "it") {
                "Controllo completato: la bozza è pronta per la pubblicazione."
            } else {
                "Validation complete: the draft is ready to publish."
            }
        }
    }

    fun dismissStatus() {
        status = null
    }

    /** Esporta una revisione pubblicata nel formato portabile verificato dal repository. */
    fun exportSelected(destination: String): Boolean {
        val choice = selected ?: return false
        if (choice.isDraft || choice.readOnly || destination.isBlank()) return false
        val repo = repository ?: return false
        return guardedResult {
            repo.exportRevision(choice.revision.canonicalHash(), Path.of(destination.trim()))
            status = if (AppLocale.language.tag == "it") {
                "Regolamento esportato in ${destination.trim()}"
            } else {
                "Ruleset exported to ${destination.trim()}"
            }
        }
    }

    /** Importa e installa una revisione indipendente; hash identici sono idempotenti. */
    fun importRevision(source: String): Boolean {
        if (source.isBlank()) return false
        val repo = repository ?: return false
        cancelModuleComposition()
        return guardedResult {
            val installed = repo.importRevision(Path.of(source.trim()))
            reload()
            originFilter = RulesetOriginFilter.HOMEBREW
            selectedKey = installed.canonicalHash()
            selectedEntityId = null
            status = if (AppLocale.language.tag == "it") {
                "Regolamento installato: ${installed.name()} ${installed.version()}"
            } else {
                "Ruleset installed: ${installed.name()} ${installed.version()}"
            }
        }
    }

    /** Installa un modulo portabile verificato, senza selezionarlo implicitamente. */
    fun importModule(source: String): Boolean {
        if (source.isBlank()) return false
        val repo = repository ?: return false
        return guardedResult {
            val installed = repo.importModule(Path.of(source.trim()))
            reload(keepSelection = true)
            refreshModuleComposition()
            status = if (AppLocale.language.tag == "it") {
                "Modulo installato: ${installed.name().text("it")} ${installed.version()}"
            } else {
                "Module installed: ${installed.name().text("en")} ${installed.version()}"
            }
        }
    }

    /** Esporta snapshot, lock e grafo chiuso dei moduli della composizione selezionata. */
    fun exportSelectedBundle(destination: String): Boolean {
        val choice = selected ?: return false
        if (choice.isDraft || destination.isBlank() || selectedCompositionLock == null) return false
        val repo = repository ?: return false
        return guardedResult {
            repo.exportBundle(choice.revision.canonicalHash(), Path.of(destination.trim()))
            status = if (AppLocale.language.tag == "it") {
                "Bundle modulare esportato in ${destination.trim()}"
            } else {
                "Modular bundle exported to ${destination.trim()}"
            }
        }
    }

    /** Importa atomicamente snapshot, lock e moduli; la revisione base non fa parte del bundle. */
    fun importBundle(source: String): Boolean {
        if (source.isBlank()) return false
        val repo = repository ?: return false
        cancelModuleComposition()
        return guardedResult {
            val result = repo.importBundle(Path.of(source.trim()))
            reload()
            originFilter = RulesetOriginFilter.HOMEBREW
            selectedKey = result.revision().canonicalHash()
            selectedEntityId = null
            val baseAvailable = storedRevisions.any {
                it.canonicalHash() == result.lock().baseCanonicalHash()
            }
            status = if (AppLocale.language.tag == "it") {
                if (baseAvailable) {
                    "Bundle installato: snapshot giocabile e base esatta disponibile; rebase non ancora automatizzato."
                } else {
                    "Bundle installato: snapshot giocabile; base assente per futuri diff/rebase."
                }
            } else if (baseAvailable) {
                "Bundle installed: playable snapshot and exact base available; rebase is not automated yet."
            } else {
                "Bundle installed: playable snapshot; base missing for future diff/rebase."
            }
        }
    }

    fun beginModuleComposition() {
        val base = selected?.takeUnless { it.isDraft }?.revision ?: return
        val suffix = if (AppLocale.language.tag == "it") " — Composizione" else " — Composition"
        moduleComposition = ModuleCompositionDraft(
            baseCanonicalHash = base.canonicalHash(),
            orderedModuleHashes = emptyList(),
            resolutions = emptyList(),
            runtime = base.runtime(),
            name = base.name() + suffix,
            description = base.description(),
            version = "1.0.0",
        )
        refreshModuleComposition()
    }

    fun cancelModuleComposition() {
        moduleComposition = null
        compositionIssues = emptyList()
        compositionError = null
        compositionPreview = null
        compositionChanges = emptyList()
    }

    fun toggleCompositionModule(canonicalHash: String) {
        val draft = moduleComposition ?: return
        if (storedModules.none { it.canonicalHash() == canonicalHash }) return
        val selected = draft.orderedModuleHashes.toMutableList()
        if (!selected.remove(canonicalHash)) selected += canonicalHash
        val selectedSet = selected.toSet()
        moduleComposition = draft.copy(
            orderedModuleHashes = selected,
            resolutions = draft.resolutions.filter { it.winnerModuleHash() in selectedSet },
        )
        refreshModuleComposition()
    }

    fun moveCompositionModule(canonicalHash: String, offset: Int) {
        if (offset == 0) return
        val draft = moduleComposition ?: return
        val selected = draft.orderedModuleHashes.toMutableList()
        val from = selected.indexOf(canonicalHash)
        if (from < 0) return
        val to = (from + offset).coerceIn(0, selected.lastIndex)
        if (from == to) return
        selected.removeAt(from)
        selected.add(to, canonicalHash)
        moduleComposition = draft.copy(orderedModuleHashes = selected)
        refreshModuleComposition()
    }

    fun chooseCompositionWinner(issue: RulesetCompositionIssue, winnerModuleHash: String) {
        val field = issue.field() ?: return
        val draft = moduleComposition ?: return
        if (winnerModuleHash !in draft.orderedModuleHashes) return
        moduleComposition = draft.copy(
            resolutions = draft.resolutions.filterNot { it.field() == field } +
                RulesetConflictResolution(field, winnerModuleHash),
        )
        refreshModuleComposition()
    }

    fun updateCompositionMetadata(name: String, description: String, version: String) {
        val draft = moduleComposition ?: return
        moduleComposition = draft.copy(name = name, description = description, version = version)
        refreshModuleComposition()
    }

    fun updateCompositionRuntime(transform: (RulesetRuntimeConfig) -> RulesetRuntimeConfig) {
        val draft = moduleComposition ?: return
        moduleComposition = draft.copy(runtime = transform(draft.runtime))
        refreshModuleComposition()
    }

    fun publishModuleComposition(): Boolean {
        val draft = moduleComposition ?: return false
        val repo = repository ?: return false
        refreshModuleComposition()
        if (!canPublishComposition) {
            status = compositionError ?: if (AppLocale.language.tag == "it") {
                "Risolvi i problemi della composizione prima di pubblicare."
            } else {
                "Resolve composition issues before publishing."
            }
            return false
        }
        return guardedResult {
            val result = repo.publishComposition(
                draft.baseCanonicalHash,
                draft.orderedModuleHashes,
                draft.resolutions,
                draft.runtime,
                draft.name.trim(),
                draft.description.trim(),
                draft.version.trim(),
            )
            cancelModuleComposition()
            reload()
            originFilter = RulesetOriginFilter.HOMEBREW
            selectedKey = result.revision().canonicalHash()
            selectedEntityId = null
            status = if (AppLocale.language.tag == "it") {
                "Composizione pubblicata: ${result.revision().name()} ${result.revision().version()}"
            } else {
                "Composition published: ${result.revision().name()} ${result.revision().version()}"
            }
        }
    }

    fun module(canonicalHash: String): RulesetModule? =
        storedModules.firstOrNull { it.canonicalHash() == canonicalHash }

    private fun refreshModuleComposition() {
        val draft = moduleComposition ?: return
        val base = storedRevisions.firstOrNull { it.canonicalHash() == draft.baseCanonicalHash }
        if (base == null) {
            compositionPreview = null
            compositionChanges = emptyList()
            compositionIssues = emptyList()
            compositionError = "Composition base is not installed"
            return
        }
        val byHash = storedModules.associateBy { it.canonicalHash() }
        val modules = draft.orderedModuleHashes.mapNotNull(byHash::get)
        if (modules.size != draft.orderedModuleHashes.size) {
            compositionPreview = null
            compositionChanges = emptyList()
            compositionIssues = emptyList()
            compositionError = "One or more selected modules are not installed"
            return
        }
        try {
            val preview = RulesetComposer.compose(
                base,
                modules,
                draft.resolutions,
                draft.runtime,
                "preview:ruleset-composition",
                "preview:ruleset-composition:revision",
                draft.version.trim(),
                draft.name.trim(),
                draft.description.trim(),
                RulesetOrigin.HOMEBREW,
                "preview",
            )
            preview.revision().compile()
            SrdRulesetCharacterAdapter.validateExecutableLinks(preview.revision(), AppLocale.language)
            if (preview.revision().entities().any { it.enabled() && it.kind() == RuleKind.CLASS }) {
                SrdRulesetCharacterAdapter.project(preview.revision(), AppLocale.language)
            }
            compositionPreview = preview
            compositionChanges = buildCompositionChanges(base, preview.revision())
            compositionIssues = emptyList()
            compositionError = null
        } catch (failure: RulesetCompositionException) {
            compositionPreview = null
            compositionChanges = emptyList()
            compositionIssues = failure.issues()
            compositionError = null
        } catch (failure: Exception) {
            compositionPreview = null
            compositionChanges = emptyList()
            compositionIssues = emptyList()
            compositionError = failure.message ?: failure::class.simpleName
        }
    }

    private fun buildCompositionChanges(
        base: RulesetRevision,
        result: RulesetRevision,
    ): List<CompositionFieldChange> {
        val beforeById = base.entities().associateBy { it.id() }
        val afterById = result.entities().associateBy { it.id() }
        val changes = mutableListOf<CompositionFieldChange>()

        fun add(path: String, before: Any?, after: Any?) {
            if (before != after) {
                changes += CompositionFieldChange(path, before?.toString(), after?.toString())
            }
        }

        (beforeById.keys + afterById.keys).toSortedSet().forEach { id ->
            val before = beforeById[id]
            val after = afterById[id]
            if (before == null && after != null) {
                add(stableEntityPath(id), null, "${after.kind()} · ${localizedValue(after.name())}")
                return@forEach
            }
            if (before != null && after == null) {
                add(stableEntityPath(id), "${before.kind()} · ${localizedValue(before.name())}", null)
                return@forEach
            }
            before ?: return@forEach
            after ?: return@forEach
            add(RuleFieldRef.name(id).path(), localizedValue(before.name()), localizedValue(after.name()))
            add(
                RuleFieldRef.description(id).path(),
                localizedValue(before.description()),
                localizedValue(after.description()),
            )
            add(RuleFieldRef.kind(id).path(), before.kind(), after.kind())
            add(RuleFieldRef.enabled(id).path(), before.enabled(), after.enabled())
            add(
                RuleFieldRef.automationLevel(id).path(),
                before.automationLevel(),
                after.automationLevel(),
            )
            add(RuleFieldRef.tags(id).path(), before.tags().joinToString(", "), after.tags().joinToString(", "))
            (before.attributes().keys + after.attributes().keys).toSortedSet().forEach { attribute ->
                add(
                    RuleFieldRef.attribute(id, attribute).path(),
                    before.attributes()[attribute],
                    after.attributes()[attribute],
                )
            }
        }
        return changes
    }

    private fun localizedValue(value: LocalizedRuleText): String =
        value.values().toSortedMap().entries.joinToString(" | ") { (language, text) -> "$language: $text" }

    private fun stableEntityPath(entityId: String): String =
        entityId.replace("~", "~0").replace("/", "~1") + "/entity"

    private fun selectedDraft(): RulesetDraft? =
        storedDrafts.firstOrNull { it.id() == selected?.draftId }

    private fun defaultAttributes(
        kind: RuleKind,
        entityId: String,
        legacyModifier: Boolean = false,
    ): Map<String, String> {
        val selectedRevision = selected?.revision
        val srdDerived = selectedRevision?.let(SrdRulesetCharacterAdapter::inheritsSrdContent) == true
        val declaredStats = selectedRevision?.entities().orEmpty()
            .filter { it.enabled() && it.kind() == RuleKind.STAT }
            .map { it.attributes()["statId"].orEmpty().ifBlank { it.id() } }
        val defaultStat = declaredStats.firstOrNull { it.equals("STRENGTH", ignoreCase = true) }
            ?: declaredStats.firstOrNull().orEmpty()
        val defaultMaximumLevel = if (srdDerived) "20" else "1"
        val defaults = when (kind) {
        RuleKind.CLASS -> linkedMapOf(
            "classId" to entityId,
            "hitDieSides" to "8",
            "fixedHitPointsPerLevel" to "5",
            "primaryAbilities" to defaultStat,
            "multiclassPrerequisiteGroups" to defaultStat,
            "savingThrowProficiencies" to defaultStat,
            "spellcastingKind" to "NONE",
            "spellcastingAbility" to "",
            "subclassIds" to "",
            "levelFeatureIds" to "",
            "progressionEntityRef" to "",
            "maximumLevel" to defaultMaximumLevel,
            "skillChoiceCount" to "0",
            "weaponTraining" to "",
            "weaponCategories" to "",
            "martialWeaponProperties" to "",
            "armorTrainingLight" to "false",
            "armorTrainingMedium" to "false",
            "armorTrainingHeavy" to "false",
            "armorTrainingShields" to "false",
            "multiclassArmorTrainingLight" to "false",
            "multiclassArmorTrainingMedium" to "false",
            "multiclassArmorTrainingHeavy" to "false",
            "multiclassArmorTrainingShields" to "false",
            "startingEquipment" to "",
        )
        RuleKind.MODIFIER -> if (legacyModifier) {
            linkedMapOf(
                "ownerRef" to "",
                "target" to "ARMOR_CLASS",
                "amount" to "1",
                "condition" to "ALWAYS",
                "minimumLevel" to "1",
                "group" to "",
            )
        } else {
            linkedMapOf(
                "ownerRef" to "",
                "targetRef" to "",
                "operation" to "ADD",
                "valueFormula" to "1",
                "valueType" to "TEXT",
                "valueLiteral" to "",
                "conditionFormula" to "1",
                "application" to "STATIC",
                "recipient" to "SELF",
                "minimumLevel" to "1",
                "priority" to "0",
                "group" to "",
            )
        }
        RuleKind.FEATURE, RuleKind.SUBCLASS -> linkedMapOf(
            "elementKind" to if (kind == RuleKind.SUBCLASS) "SUBCLASS_FEATURE" else "CLASS_FEATURE",
            "activation" to "Passiva",
            "prerequisite" to "",
        )
        RuleKind.PROGRESSION -> linkedMapOf(
            "minimumLevel" to "1",
            "maximumLevel" to defaultMaximumLevel,
            "enforceExperienceThresholds" to "false",
            "experienceTableRef" to "",
            "defaultExperience" to "false",
        )
        RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE -> if (srdDerived) {
            linkedMapOf(
                "statId" to entityId,
                "abbreviation" to "NEW",
                "defaultFormula" to "10",
                "minimumFormula" to "1",
                "maximumFormula" to "30",
                "advancementMaximum" to "20",
                "modifierFormula" to "floor((\${score} - 10) / 2)",
                "rounding" to "NONE",
            )
        } else {
            linkedMapOf(
                "statId" to entityId,
                "abbreviation" to "NEW",
                "defaultFormula" to "0",
                "modifierFormula" to "\${score}",
                "rounding" to "NONE",
            )
        }
        RuleKind.VALUE -> linkedMapOf(
            "valueType" to "TEXT",
            "defaultValue" to "",
            "allowedValues" to "",
            "mutable" to "true",
            "dimension" to "SCALAR",
            "canonicalUnit" to "",
        )
        RuleKind.SKILL -> linkedMapOf(
            "skillId" to entityId,
            "statRef" to defaultStat,
            "formula" to "",
            "trainedBonusFormula" to if (srdDerived) "\${proficiency}" else "0",
        )
        RuleKind.TABLE -> linkedMapOf(
            "tableId" to entityId,
            "valueType" to "NUMBER",
            "lookup" to "FLOOR",
            "rows" to "1=0",
        )
        RuleKind.RESOURCE, RuleKind.TRACK -> linkedMapOf(
            "resourceId" to entityId,
            "maximumFormula" to "1",
            "initialFormula" to "\${maximum}",
            "recoveryEvent" to "MANUAL",
            "recoveryFormula" to "\${maximum}",
        )
        RuleKind.ACTION_ECONOMY -> linkedMapOf("budgets" to "")
        RuleKind.ACTION -> linkedMapOf(
            "actionId" to entityId,
            "ownerRef" to "",
            "conditionFormula" to "1",
            "costs" to "",
            "effectRefs" to "",
        )
        RuleKind.TRIGGER -> linkedMapOf(
            "event" to "MANUAL",
            "conditionFormula" to "1",
            "effectRefs" to "",
            "priority" to "0",
            "maximumExecutions" to "1",
        )
        RuleKind.RANDOMIZER -> linkedMapOf(
            "mode" to "DICE",
            "countFormula" to "1",
            "sidesFormula" to if (srdDerived) "20" else "6",
            "keep" to "SUM",
            "successThresholdFormula" to "1",
            "tableRef" to "",
        )
        RuleKind.ROLL -> linkedMapOf(
            "randomizerRef" to "",
            "totalFormula" to "\${roll}",
            "targetFormula" to "0",
            "comparison" to "MEET_OR_EXCEED",
            "naturalSuccessMinimum" to "0",
            "naturalFailureMaximum" to "0",
            "threatMinimumNatural" to "0",
            "confirmationRequired" to "false",
            "criticalMultiplier" to "2",
            "outcomeTableRef" to "",
            "opposedRollRef" to "",
        )
        RuleKind.DAMAGE_TYPE -> linkedMapOf("damageTypeId" to entityId)
        RuleKind.CONDITION -> linkedMapOf(
            "conditionId" to entityId,
            "maximumStacks" to "1",
            "stacking" to "REPLACE",
            "sourceScoped" to "false",
            "removalEvent" to "",
        )
        RuleKind.HEALTH_MODEL -> linkedMapOf(
            "primaryResourceRef" to "",
            "bufferResourceRefs" to "",
            "zeroConditionRef" to "",
            "deathConditionRef" to "",
            "allowsNegative" to "false",
            "zeroState" to "MANUAL",
        )
        RuleKind.MOVEMENT -> linkedMapOf(
            "topology" to "SQUARE",
            "diagonalRule" to "UNIFORM",
            "unitsPerCell" to if (srdDerived) "5" else "1",
            "canonicalUnit" to if (srdDerived) "ft" else "unit",
            "elevation" to "false",
            "occupancyRequired" to "true",
        )
        RuleKind.SHEET_SECTION -> linkedMapOf(
            "fieldRefs" to "",
            "order" to "0",
            "columns" to "1",
            "layout" to "LIST",
            "visibilityFormula" to "1",
        )
        RuleKind.SCENE_PROCEDURE -> linkedMapOf(
            "phases" to "SCENE",
            "actionRefs" to "",
            "trackerRefs" to "",
            "initiativeRequired" to "false",
            "boardRequired" to "false",
        )
        else -> emptyMap()
        }
        return if (RulesetCompiler.supportsStatePolicy(kind)) {
            defaults + linkedMapOf(
                "lifetime" to "PERMANENT",
                "owner" to "SCOPE",
                "syncPolicy" to "LOCAL_ONLY",
                "resetEvent" to "",
            )
        } else {
            defaults
        }
    }

    /**
     * I tre parametri già estratti nel runtime non possono avere una seconda
     * verità dentro la mappa attributi. Modificarli dalla scheda della regola o
     * dai controlli dedicati produce quindi lo stesso identico stato.
     */
    private fun runtimeAfterEntityEdit(
        current: RulesetRuntimeConfig,
        entityId: String,
        attributes: Map<String, String>,
    ): RulesetRuntimeConfig {
        fun integer(key: String, fallback: Int): Int = attributes[key]
            ?.takeIf(String::isNotBlank)
            ?.toIntOrNull()
            ?: if (attributes[key].isNullOrBlank()) fallback else error("$entityId.$key must be an integer")
        fun boolean(key: String, fallback: Boolean): Boolean = attributes[key]
            ?.takeIf(String::isNotBlank)
            ?.toBooleanStrictOrNull()
            ?: if (attributes[key].isNullOrBlank()) fallback else error("$entityId.$key must be true or false")

        return when (entityId) {
            CoreRuleIds.CRITICAL_HIT -> current
                .withCriticalHitMinimumNatural(
                    integer("criticalHitMinimumNatural", current.criticalHitMinimumNatural()),
                )
                .withNaturalOneAlwaysMisses(
                    boolean("naturalOneAlwaysMisses", current.naturalOneAlwaysMisses()),
                )
            CoreRuleIds.EXHAUSTION -> current
                .withMaximumExhaustion(integer("maximumExhaustion", current.maximumExhaustion()))
                .withExhaustionD20PenaltyPerLevel(
                    integer("d20PenaltyPerLevel", current.exhaustionD20PenaltyPerLevel()),
                )
                .withExhaustionSpeedPenaltyFeetPerLevel(
                    integer("speedPenaltyFeetPerLevel", current.exhaustionSpeedPenaltyFeetPerLevel()),
                )
            CoreRuleIds.PROFICIENCY -> current.withProficiency(
                integer("base", current.proficiencyBonusBase()),
                integer("levelsPerIncrease", current.proficiencyLevelsPerIncrease()),
                integer("maximum", current.proficiencyBonusMaximum()),
            )
            else -> current
        }
    }

    private fun reload(keepSelection: Boolean = true) {
        val previous = selectedKey
        val repo = repository
        if (repo != null) {
            storedRevisions = repo.revisions()
            storedDrafts = repo.drafts()
            storedModules = repo.modules()
            storedAuthoring = repo.authoringState()
        }
        val keys = storedRevisions.map { it.canonicalHash() }.toSet() + storedDrafts.map { it.id() }
        selectedKey = if (keepSelection && previous in keys) previous else standard.canonicalHash()
    }

    private fun rawAuthoringGroup(entityId: String): AuthoringGroupSnapshot? {
        val draftId = selected?.draftId ?: return null
        val groups = storedAuthoring.groups(draftId)
        val directId = authoringGroupId(entityId)
        groups[directId]?.let { return AuthoringGroupSnapshot(directId, it) }
        return groups.entries.firstOrNull { entityId in it.value.generatedEntityIds() }
            ?.let { AuthoringGroupSnapshot(it.key, it.value) }
    }

    private fun validAuthoringGroup(entityId: String): AuthoringGroupSnapshot? {
        val group = rawAuthoringGroup(entityId) ?: return null
        return group.takeIf { authoringMetadataMatchesCurrentContent(it.metadata) }
    }

    private fun authoringMetadataMatchesCurrentContent(metadata: RuleAuthoringMetadata): Boolean {
        val revision = selected?.revision ?: return false
        val generatedIds = metadata.generatedEntityIds()
        val hashes = metadata.lastProjectedContentHashes()
        if (generatedIds.isEmpty() || hashes.keys != generatedIds.toSet()) return false
        return generatedIds.all { entityId ->
            val entity = revision.entity(entityId) ?: return@all false
            hashes[entityId] == RulesetCanonicalizer.entityContentHash(entity)
        }
    }

    private fun projectedProtectedFields(entity: RuleEntity): Set<String> =
        entity.attributes().keys.filterTo(linkedSetOf()) { key ->
            !guidedAttribute(entity.kind(), key) ||
                guidedValueIsProtected(entity.kind(), key, entity.attributes().getValue(key))
        }

    private fun authoringAfterEdit(
        draftId: String,
        entity: RuleEntity,
        mode: AuthoringMode,
    ): RulesetAuthoringState {
        val rawGroup = rawAuthoringGroup(entity.id())
        val currentGroup = rawGroup?.takeIf { authoringMetadataMatchesCurrentContent(it.metadata) }
        val groupId = currentGroup?.id ?: authoringGroupId(entity.id())
        val withoutPrevious = rawGroup?.let { storedAuthoring.withoutGroup(draftId, it.id) }
            ?: storedAuthoring
        if (mode == AuthoringMode.EXPERT) return withoutPrevious

        val generatedIds = currentGroup?.metadata?.generatedEntityIds() ?: listOf(entity.id())
        val generatedEntities = generatedIds.mapNotNull { entityId ->
            if (entityId == entity.id()) entity else selected?.revision?.entity(entityId)
        }
        val completeEntities = if (generatedEntities.size == generatedIds.size) {
            generatedEntities
        } else {
            listOf(entity)
        }
        val existing = currentGroup?.metadata
        val metadata = RuleAuthoringMetadata(
            existing?.recipeId() ?: "builtin.${entity.kind().name.lowercase()}",
            existing?.recipeVersion() ?: 1,
            completeEntities.map(RuleEntity::id),
            existing?.visualSections().orEmpty() + ("mode" to mode.name),
            completeEntities.flatMapTo(linkedSetOf(), ::projectedProtectedFields),
            completeEntities.associate { it.id() to RulesetCanonicalizer.entityContentHash(it) },
            existing?.examples().orEmpty(),
        )
        return withoutPrevious.withGroup(draftId, groupId, metadata)
    }

    private fun authoringGroupId(entityId: String): String = "entity:$entityId"

    private fun guidedAttribute(kind: RuleKind, key: String): Boolean {
        if (key in setOf("lifetime", "owner", "syncPolicy", "resetEvent")) {
            return RulesetCompiler.supportsStatePolicy(kind)
        }
        if (key in setOf("activeByDefault", "links")) return true
        return when (kind) {
            RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE -> key in setOf(
                "statId", "abbreviation", "default", "defaultFormula", "derivedFormula",
                "minimum", "minimumFormula", "maximum", "maximumFormula", "modifierFormula",
                "rounding", "advancementMaximum",
            )
            RuleKind.SKILL -> key in setOf(
                "skillId", "statRef", "abilityRef", "ability", "formula", "trainedBonusFormula",
            )
            RuleKind.VALUE -> key in setOf(
                "valueType", "defaultValue", "allowedValues", "mutable", "dimension", "canonicalUnit",
            )
            RuleKind.MODIFIER -> key in setOf(
                "ownerRef", "targetRef", "operation", "valueFormula", "conditionFormula", "group",
                "stacking", "sourceRef", "phase", "priority", "minimumLevel", "application",
                "recipient", "valueType", "valueLiteral", "amount", "target", "condition",
            )
            RuleKind.CONDITION -> key in setOf(
                "maximumStacks", "stacking", "sourceScoped", "removalEvent",
            )
            RuleKind.RESOURCE, RuleKind.TRACK -> key in setOf(
                "resourceId", "maximumFormula", "maximum", "initialFormula", "recoveryEvent",
                "recoveryFormula",
            )
            RuleKind.RANDOMIZER -> key in setOf(
                "mode", "countFormula", "diceCount", "sidesFormula", "dieSides", "keep",
                "successThresholdFormula", "successThreshold", "tableRef",
            )
            RuleKind.ROLL -> key in setOf(
                "mode", "countFormula", "diceCount", "sidesFormula", "dieSides", "keep",
                "successThresholdFormula", "successThreshold", "tableRef", "randomizerRef",
                "totalFormula", "targetFormula", "comparison", "naturalSuccessMinimum",
                "naturalFailureMaximum", "threatMinimumNatural", "confirmationRequired",
                "criticalMultiplier", "outcomeTableRef", "opposedRollRef",
            )
            RuleKind.ACTION_ECONOMY -> key in setOf(
                "budgets", "actions", "bonusActions", "reactions", "moveActions", "swiftActions",
                "immediateActions", "fullRoundActions",
            )
            RuleKind.ACTION -> key in setOf(
                "actionId", "ownerRef", "conditionFormula", "costs", "effectRefs",
            )
            RuleKind.TRIGGER -> key in setOf(
                "event", "conditionFormula", "effectRefs", "priority", "maximumExecutions",
            )
            RuleKind.TABLE -> key in setOf(
                "tableId", "valueType", "lookup", "rows",
            )
            RuleKind.DAMAGE_TYPE -> key == "damageTypeId"
            RuleKind.TEXT_RULE -> false
            RuleKind.HEALTH_MODEL -> key in setOf(
                "primaryResourceRef", "bufferResourceRefs", "zeroConditionRef", "deathConditionRef",
                "zeroState", "allowsNegative",
            )
            RuleKind.MOVEMENT -> key in setOf(
                "topology", "diagonalRule", "unitsPerCell", "canonicalUnit", "elevation",
                "occupancyRequired",
            )
            RuleKind.SHEET_SECTION -> key in setOf(
                "layout", "order", "columns", "fieldRefs", "visibilityFormula",
            )
            RuleKind.SCENE_PROCEDURE -> key in setOf(
                "phases", "initiativeRequired", "boardRequired", "actionRefs", "trackerRefs",
            )
            RuleKind.PROGRESSION -> key in setOf(
                "minimumLevel", "maximumLevel", "maximumCharacterLevel", "enforceExperienceThresholds",
                "experienceTableRef", "defaultExperience",
            ) || key.startsWith("track.")
            else -> false
        }
    }

    private fun guidedValueIsProtected(kind: RuleKind, key: String, value: String): Boolean {
        if (value.isBlank()) return false
        val isFormula = key.endsWith("Formula") || key in when (kind) {
            RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE -> setOf("default", "minimum", "maximum")
            RuleKind.RANDOMIZER, RuleKind.ROLL -> setOf("diceCount", "dieSides", "successThreshold")
            RuleKind.ACTION_ECONOMY -> setOf(
                "actions", "bonusActions", "reactions", "moveActions", "swiftActions",
                "immediateActions", "fullRoundActions",
            )
            else -> emptySet()
        }
        if (isFormula) return !formulaIsExactlyProjectable(value)
        if (key == "budgets" || key == "costs") {
            val targets = mutableSetOf<String>()
            return value.split(';').filter(String::isNotBlank).any { row ->
                val separator = row.indexOf('=')
                if (separator <= 0 || separator == row.lastIndex) return@any true
                val target = row.substring(0, separator).trim()
                val formula = row.substring(separator + 1).trim()
                target.isBlank() || !targets.add(target) || !formulaIsExactlyProjectable(formula)
            }
        }
        if (kind == RuleKind.TABLE && key == "rows") {
            val thresholds = mutableSetOf<BigDecimal>()
            return value.split(';').filter(String::isNotBlank).any { row ->
                val separator = row.indexOf('=')
                if (separator <= 0 || separator == row.lastIndex) return@any true
                val threshold = row.substring(0, separator).trim().toBigDecimalOrNull()
                val result = row.substring(separator + 1).trim()
                threshold == null || result.isBlank() || !thresholds.add(threshold.stripTrailingZeros())
            }
        }
        return false
    }

    private fun formulaIsExactlyProjectable(source: String): Boolean =
        runCatching { FormulaDraft.parse(source) }.fold(
            onSuccess = { it.projectionStatus() == ProjectionStatus.EXACT },
            onFailure = { false },
        )

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (failure: Exception) {
            status = failure.message ?: failure::class.simpleName
        }
    }

    private inline fun guardedResult(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (failure: Exception) {
        status = failure.message ?: failure::class.simpleName
        false
    }

    private companion object {
        val modifierOwnerKinds = setOf(
            RuleKind.CLASS,
            RuleKind.FEATURE,
            RuleKind.SUBCLASS,
            RuleKind.FEAT,
            RuleKind.SPELL,
            RuleKind.ACTION,
        )
        val executableKinds = setOf(
            RuleKind.CLASS, RuleKind.MODIFIER, RuleKind.FEATURE, RuleKind.SUBCLASS, RuleKind.FEAT,
            RuleKind.SPELL, RuleKind.STAT, RuleKind.SKILL,
            RuleKind.SAVE, RuleKind.DEFENSE, RuleKind.VALUE, RuleKind.TABLE, RuleKind.RESOURCE, RuleKind.TRACK,
            RuleKind.ACTION, RuleKind.ACTION_ECONOMY, RuleKind.TRIGGER, RuleKind.ROLL,
            RuleKind.RANDOMIZER, RuleKind.PROGRESSION, RuleKind.DAMAGE_TYPE, RuleKind.CONDITION,
            RuleKind.HEALTH_MODEL, RuleKind.MOVEMENT, RuleKind.SHEET_SECTION, RuleKind.SCENE_PROCEDURE,
        )
        val legacyRuntimeEntityIds = setOf(
            CoreRuleIds.CRITICAL_HIT,
            CoreRuleIds.EXHAUSTION,
            CoreRuleIds.PROFICIENCY,
        )
    }
}
