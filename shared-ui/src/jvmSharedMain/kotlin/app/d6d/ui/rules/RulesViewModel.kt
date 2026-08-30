package app.d6d.ui.rules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.content.srd521it.Srd521Ruleset
import app.d6d.content.srd521it.SrdRulesetCharacterAdapter
import app.d6d.rules.model.CoreRuleIds
import app.d6d.rules.model.LocalizedRuleText
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulePatch
import app.d6d.rules.model.RulesetDraft
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.model.RulesetRevision
import app.d6d.rules.model.RulesetRuntimeConfig
import app.d6d.rules.persistence.LocalRulesetRepository
import app.d6d.ui.i18n.AppLocale
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

enum class RulesetOriginFilter { ALL, STANDARD, HOMEBREW }
enum class RuleEnabledFilter { ALL, ENABLED, DISABLED }
enum class DraftEntityChange { INHERITED, MODIFIED, ADDED }

data class DraftChangeSummary(val modified: Int, val added: Int, val disabled: Int)

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

    var status by mutableStateOf<String?>(null)
        private set

    private val repository: LocalRulesetRepository? = runCatching {
        LocalRulesetRepository(dataDirectory.resolve("rulesets"), listOf(standard))
    }.onFailure { status = it.message }.getOrNull()

    private var storedRevisions by mutableStateOf<List<RulesetRevision>>(listOf(standard))
    private var storedDrafts by mutableStateOf<List<RulesetDraft>>(emptyList())

    /** Revisioni stabili selezionabili da una nuova partita; le bozze non entrano nel runtime. */
    val publishedRevisions: List<RulesetRevision> get() = storedRevisions

    var selectedKey by mutableStateOf(standard.canonicalHash())
        private set

    var selectedEntityId by mutableStateOf<String?>(null)
        private set

    var originFilter by mutableStateOf(RulesetOriginFilter.ALL)
        private set
    var kindFilter by mutableStateOf<RuleKind?>(null)
    var automationFilter by mutableStateOf<RuleAutomationLevel?>(null)
    var enabledFilter by mutableStateOf(RuleEnabledFilter.ALL)
    var search by mutableStateOf("")

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

    val visibleEntities: List<RuleEntity>
        get() {
            val query = search.trim().lowercase()
            val language = AppLocale.language.tag
            return selected?.revision?.entities().orEmpty().filter { entity ->
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

    fun changeOriginFilter(filter: RulesetOriginFilter) {
        if (originFilter == filter) return
        originFilter = filter
        val visible = choices
        if (visible.none { it.key == selectedKey }) {
            selectedKey = visible.firstOrNull()?.key.orEmpty()
            selectedEntityId = null
        }
    }

    fun selectRuleset(key: String) {
        if (selectedKey == key) return
        selectedKey = key
        selectedEntityId = null
        status = null
    }

    fun selectEntity(id: String) {
        selectedEntityId = id
    }

    fun forkSelected(entityToEdit: String? = null) {
        val source = selected?.revision ?: return
        val repo = repository ?: return
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
    ) {
        val draft = selectedDraft() ?: return
        val repo = repository ?: return
        val effective = selected?.revision?.entity(entityId) ?: return
        val language = AppLocale.language.tag
        guarded {
            val changedName = effective.name().withText(language, name)
            val changedDescription = effective.description().withText(language, description)
            val additionIndex = draft.additions().indexOfFirst { it.id() == entityId }
            if (additionIndex >= 0) {
                // Gli editor specializzati inviano l'intero schema, mentre API e
                // test possono aggiornare soltanto i campi interessati. Una
                // regola appena creata non deve perdere gli identificativi e i
                // default eseguibili che non compaiono nell'aggiornamento.
                val storedAttributes = if (kind == effective.kind()) {
                    effective.attributes() + attributes
                } else {
                    defaultAttributes(kind, entityId) + attributes
                }
                val additions = draft.additions().toMutableList()
                additions[additionIndex] = RuleEntity(
                    effective.id(), kind, effective.origin(), changedName, changedDescription,
                    effective.derivedFrom(), enabled, automation, storedAttributes, tags,
                    effective.source(), effective.license(), effective.sourcePage(),
                )
                repo.saveDraft(
                    draft.withContent(
                        draft.name(), draft.description(),
                        runtimeAfterEntityEdit(draft.runtime(), entityId, storedAttributes),
                        draft.patches(),
                        additions, Instant.now().toString(),
                    ),
                )
                reload(keepSelection = true)
                selectedEntityId = entityId
                status = AppLocale.current.rules.saved(draft.name())
                return@guarded
            }

            val baseEntity = repo.findRevision(draft.baseCanonicalHash())?.entity(entityId)
                ?: error("La regola di base non è più disponibile")
            val patches = draft.patches().toMutableList()
            val index = patches.indexOfFirst { it.targetEntityId() == entityId }
            val old = patches.getOrNull(index)
            val patch = RulePatch(
                old?.id() ?: "patch:${UUID.randomUUID()}",
                entityId,
                changedName,
                changedDescription,
                attributes,
                baseEntity.attributes().keys - attributes.keys,
                enabled,
                kind,
                automation,
                tags,
            )
            if (index >= 0) patches[index] = patch else patches += patch
            repo.saveDraft(
                draft.withContent(
                    draft.name(), draft.description(),
                    runtimeAfterEntityEdit(draft.runtime(), entityId, attributes),
                    patches,
                    draft.additions(), Instant.now().toString(),
                ),
            )
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
            repo.saveDraft(changed)
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

    private fun addRule(
        kind: RuleKind,
        name: String,
        description: String,
        initialAttributes: Map<String, String> = emptyMap(),
        legacyModifier: Boolean = false,
    ) {
        val draft = selectedDraft() ?: return
        val repo = repository ?: return
        guarded {
            val language = AppLocale.language.tag
            val entityId = "local:${kind.name.lowercase()}:${UUID.randomUUID()}"
            val addition = RuleEntity(
                entityId, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single(language, name), LocalizedRuleText.single(language, description),
                "", true,
                if (kind in executableKinds) {
                    RuleAutomationLevel.ASSISTED
                } else {
                    RuleAutomationLevel.MANUAL
                },
                defaultAttributes(kind, entityId, legacyModifier) + initialAttributes, listOf("homebrew"),
                "Homebrew", "", 0,
            )
            repo.saveDraft(
                draft.withContent(
                    draft.name(), draft.description(), draft.runtime(), draft.patches(),
                    draft.additions() + addition, Instant.now().toString(),
                ),
            )
            reload(keepSelection = true)
            selectedEntityId = addition.id()
        }
    }

    fun publishSelected(version: String) {
        val draft = selectedDraft() ?: return
        val normalized = version.trim()
        if (normalized.isEmpty()) return
        val repo = repository ?: return
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

    fun dismissStatus() {
        status = null
    }

    private fun selectedDraft(): RulesetDraft? =
        storedDrafts.firstOrNull { it.id() == selected?.draftId }

    private fun defaultAttributes(
        kind: RuleKind,
        entityId: String,
        legacyModifier: Boolean = false,
    ): Map<String, String> = when (kind) {
        RuleKind.CLASS -> linkedMapOf(
            "classId" to entityId,
            "hitDieSides" to "8",
            "fixedHitPointsPerLevel" to "5",
            "primaryAbilities" to "STRENGTH",
            "multiclassPrerequisiteGroups" to "STRENGTH",
            "savingThrowProficiencies" to "STRENGTH",
            "spellcastingKind" to "NONE",
            "spellcastingAbility" to "",
            "subclassIds" to "",
            "levelFeatureIds" to "",
            "maximumLevel" to "20",
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
            "maximumCharacterLevel" to "20",
            "enforceExperienceThresholds" to "false",
            "experienceTableRef" to "",
        )
        RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE -> linkedMapOf(
            "statId" to entityId,
            "abbreviation" to "NEW",
            "defaultFormula" to "10",
            "minimumFormula" to "1",
            "maximumFormula" to "30",
            "advancementMaximum" to "20",
            "modifierFormula" to "floor((\${score} - 10) / 2)",
            "rounding" to "NONE",
        )
        RuleKind.VALUE -> linkedMapOf(
            "valueType" to "TEXT",
            "defaultValue" to "",
            "allowedValues" to "",
            "mutable" to "true",
        )
        RuleKind.SKILL -> linkedMapOf(
            "skillId" to entityId,
            "statRef" to "",
            "formula" to "",
            "trainedBonusFormula" to "\${proficiency}",
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
        RuleKind.ACTION_ECONOMY -> linkedMapOf("budgets" to "action=1")
        RuleKind.ACTION -> linkedMapOf(
            "actionId" to entityId,
            "ownerRef" to "",
            "conditionFormula" to "1",
            "costs" to "turn:action=1",
            "effectRefs" to "",
        )
        RuleKind.TRIGGER -> linkedMapOf(
            "event" to "MANUAL",
            "conditionFormula" to "1",
            "effectRefs" to "",
            "priority" to "0",
            "maximumExecutions" to "1",
        )
        RuleKind.ROLL, RuleKind.RANDOMIZER -> linkedMapOf(
            "mode" to "DICE",
            "countFormula" to "1",
            "sidesFormula" to "20",
            "keep" to "SUM",
            "successThresholdFormula" to "1",
            "tableRef" to "",
        )
        RuleKind.DAMAGE_TYPE -> linkedMapOf("damageTypeId" to entityId)
        RuleKind.CONDITION -> linkedMapOf("conditionId" to entityId, "maximumStacks" to "1")
        else -> emptyMap()
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
        }
        val keys = storedRevisions.map { it.canonicalHash() }.toSet() + storedDrafts.map { it.id() }
        selectedKey = if (keepSelection && previous in keys) previous else standard.canonicalHash()
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (failure: Exception) {
            status = failure.message ?: failure::class.simpleName
        }
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
            RuleKind.CLASS, RuleKind.MODIFIER, RuleKind.FEATURE, RuleKind.STAT, RuleKind.SKILL,
            RuleKind.SAVE, RuleKind.DEFENSE, RuleKind.VALUE, RuleKind.TABLE, RuleKind.RESOURCE, RuleKind.TRACK,
            RuleKind.ACTION, RuleKind.ACTION_ECONOMY, RuleKind.TRIGGER, RuleKind.ROLL,
            RuleKind.RANDOMIZER, RuleKind.PROGRESSION, RuleKind.DAMAGE_TYPE, RuleKind.CONDITION,
        )
    }
}
