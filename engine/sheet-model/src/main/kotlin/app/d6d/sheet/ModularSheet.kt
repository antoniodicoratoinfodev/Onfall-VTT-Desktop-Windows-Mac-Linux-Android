package app.d6d.sheet

import app.d6d.rules.model.CompiledRuleset
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RuleRuntimeState
import app.d6d.rules.model.RuleValue
import app.d6d.rules.model.RulesetRevision
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/** Forma persistita di un campo generato dal regolamento. */
@Serializable
enum class ModularSheetFieldKind {
    NUMBER,
    BOOLEAN,
    TEXT,
    REFERENCE,
    RESOURCE,
    CONDITION,
    RULE_TEXT,
}

@Serializable
data class ModularSheetValue(
    val kind: ModularSheetFieldKind,
    val current: String = "",
    val maximum: String = "",
)

@Serializable
data class ModularSheetField(
    val id: String,
    val label: String,
    val description: String = "",
    val kind: ModularSheetFieldKind,
    val mutable: Boolean,
    val dimension: String = "SCALAR",
    val canonicalUnit: String = "",
)

@Serializable
data class ModularSheetSection(
    val id: String,
    val title: String,
    val description: String = "",
    val order: Int = 0,
    val columns: Int = 1,
    val layout: String = "LIST",
    val fields: List<ModularSheetField> = emptyList(),
)

/**
 * Snapshot leggibile anche quando il relativo pack non e' più installato.
 * Le etichette sono materializzate, mentre ID e hash conservano la semantica.
 */
@Serializable
data class ModularSheetState(
    val rulesetProjectId: String = "",
    val rulesetRevisionId: String = "",
    val rulesetCanonicalHash: String = "",
    val runtimeHash: String = "",
    val languageTag: String = "it",
    val sections: List<ModularSheetSection> = emptyList(),
    val values: Map<String, ModularSheetValue> = emptyMap(),
) {
    val configured: Boolean get() = rulesetCanonicalHash.isNotBlank()
}

/** Adatta SHEET_SECTION e i campi collegati senza dipendere dal content pack SRD. */
object RuleDrivenSheetProjector {

    fun project(
        revision: RulesetRevision,
        languageTag: String,
        previous: ModularSheetState? = null,
    ): ModularSheetState {
        val rules = revision.compile()
        val restored = restoreRuntime(rules, previous?.takeIf {
            it.rulesetCanonicalHash == revision.canonicalHash()
        })
        return snapshot(revision, languageTag, rules, restored)
    }

    fun update(
        revision: RulesetRevision,
        state: ModularSheetState,
        fieldId: String,
        current: String,
        maximum: String = "",
    ): ModularSheetState {
        require(state.rulesetCanonicalHash == revision.canonicalHash()) {
            "The modular sheet and ruleset revision differ"
        }
        val rules = revision.compile()
        var runtime = restoreRuntime(rules, state)
        val resolved = rules.resolveId(fieldId)
        val entity = rules.entities()[resolved] ?: error("Unknown modular sheet field $fieldId")
        runtime = when (entity.kind()) {
            RuleKind.VALUE -> {
                val definition = requireNotNull(rules.valueDefinitions()[resolved])
                rules.setRuleValue(resolved, RuleValue(definition.type(), current), runtime)
            }
            RuleKind.STAT,
            RuleKind.SKILL,
            RuleKind.SAVE,
            RuleKind.DEFENSE,
            -> rules.setNumericValue(resolved, current.toBigDecimal(), runtime)
            RuleKind.RESOURCE,
            RuleKind.TRACK,
            -> {
                val before = requireNotNull(runtime.resources()[resolved])
                rules.setResource(
                    resolved,
                    current.toBigDecimal(),
                    maximum.takeIf(String::isNotBlank)?.toBigDecimal() ?: before.maximum(),
                    runtime,
                )
            }
            RuleKind.CONDITION -> rules.setConditionStacks(resolved, current.toInt(), runtime)
            else -> error("Field $fieldId is read only")
        }
        return snapshot(revision, state.languageTag, rules, runtime)
    }

    private fun restoreRuntime(
        rules: CompiledRuleset,
        previous: ModularSheetState?,
    ): RuleRuntimeState {
        var runtime = rules.initialState(emptyMap(), emptySet())
        if (previous == null) return runtime
        previous.values.toSortedMap().forEach { (rawId, saved) ->
            val id = runCatching { rules.resolveId(rawId) }.getOrNull() ?: return@forEach
            val entity = rules.entities()[id] ?: return@forEach
            runtime = runCatching {
                when (entity.kind()) {
                    RuleKind.VALUE -> {
                        val definition = rules.valueDefinitions()[id] ?: return@runCatching runtime
                        if (!definition.mutable()) return@runCatching runtime
                        rules.setRuleValue(id, RuleValue(definition.type(), saved.current), runtime)
                    }
                    RuleKind.STAT,
                    RuleKind.SKILL,
                    RuleKind.SAVE,
                    RuleKind.DEFENSE,
                    -> rules.setNumericValue(id, saved.current.toBigDecimal(), runtime)
                    RuleKind.RESOURCE,
                    RuleKind.TRACK,
                    -> {
                        val created = runtime.resources()[id] ?: return@runCatching runtime
                        val oldMaximum = saved.maximum.toBigDecimalOrNull() ?: created.maximum()
                        val oldCurrent = saved.current.toBigDecimalOrNull() ?: created.current()
                        rules.setResource(
                            id,
                            oldCurrent.coerceAtLeast(BigDecimal.ZERO),
                            oldMaximum.coerceAtLeast(BigDecimal.ZERO),
                            runtime,
                        )
                    }
                    RuleKind.CONDITION -> {
                        val maximum = rules.conditionDefinitions()[id]?.maximumStacks() ?: 1
                        rules.setConditionStacks(id, saved.current.toInt().coerceIn(0, maximum), runtime)
                    }
                    else -> runtime
                }
            }.getOrDefault(runtime)
        }
        return runtime
    }

    private fun snapshot(
        revision: RulesetRevision,
        languageTag: String,
        rules: CompiledRuleset,
        runtime: RuleRuntimeState,
    ): ModularSheetState {
        val values = linkedMapOf<String, ModularSheetValue>()
        val sections = rules.sheetSections().values
            .filter { runCatching { rules.isSheetSectionVisible(it.id(), runtime) }.getOrDefault(true) }
            .sortedWith(compareBy({ it.order() }, { it.id() }))
            .map { definition ->
                val entity = requireNotNull(rules.entities()[definition.id()])
                ModularSheetSection(
                    id = definition.id(),
                    title = entity.name().text(languageTag),
                    description = entity.description().text(languageTag),
                    order = definition.order(),
                    columns = definition.columns(),
                    layout = definition.layout().name,
                    fields = definition.fieldRefs().map { rawField ->
                        val id = rules.resolveId(rawField)
                        val fieldEntity = requireNotNull(rules.entities()[id])
                        field(fieldEntity.kind(), id, fieldEntity.name().text(languageTag),
                            fieldEntity.description().text(languageTag), rules).also { field ->
                            values.putIfAbsent(id, value(field, fieldEntity.kind(), id, rules, runtime))
                        }
                    },
                )
            }
        val binding = revision.binding()
        return ModularSheetState(
            rulesetProjectId = binding.projectId(),
            rulesetRevisionId = binding.revisionId(),
            rulesetCanonicalHash = binding.canonicalHash(),
            runtimeHash = binding.runtimeHash(),
            languageTag = languageTag,
            sections = sections,
            values = values,
        )
    }

    private fun field(
        kind: RuleKind,
        id: String,
        label: String,
        description: String,
        rules: CompiledRuleset,
    ): ModularSheetField {
        val valueDefinition = rules.valueDefinitions()[id]
        val fieldKind = when (kind) {
            RuleKind.VALUE -> when (valueDefinition?.type()) {
                RuleValue.Type.NUMBER -> ModularSheetFieldKind.NUMBER
                RuleValue.Type.BOOLEAN -> ModularSheetFieldKind.BOOLEAN
                RuleValue.Type.REFERENCE -> ModularSheetFieldKind.REFERENCE
                else -> ModularSheetFieldKind.TEXT
            }
            RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE -> ModularSheetFieldKind.NUMBER
            RuleKind.RESOURCE, RuleKind.TRACK -> ModularSheetFieldKind.RESOURCE
            RuleKind.CONDITION -> ModularSheetFieldKind.CONDITION
            else -> ModularSheetFieldKind.RULE_TEXT
        }
        return ModularSheetField(
            id = id,
            label = label,
            description = description,
            kind = fieldKind,
            mutable = when (kind) {
                RuleKind.VALUE -> valueDefinition?.mutable() == true
                RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE,
                RuleKind.RESOURCE, RuleKind.TRACK, RuleKind.CONDITION,
                -> true
                else -> false
            },
            dimension = valueDefinition?.dimension() ?: "SCALAR",
            canonicalUnit = valueDefinition?.canonicalUnit().orEmpty(),
        )
    }

    private fun value(
        field: ModularSheetField,
        kind: RuleKind,
        id: String,
        rules: CompiledRuleset,
        runtime: RuleRuntimeState,
    ): ModularSheetValue = when (kind) {
        RuleKind.VALUE -> rules.ruleValue(id, runtime).let {
            ModularSheetValue(field.kind, it.canonicalValue())
        }
        RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE -> ModularSheetValue(
            field.kind,
            runCatching { rules.value(id, runtime).stripTrailingZeros().toPlainString() }.getOrDefault(""),
        )
        RuleKind.RESOURCE, RuleKind.TRACK -> runtime.resources()[id]?.let {
            ModularSheetValue(
                field.kind,
                it.current().stripTrailingZeros().toPlainString(),
                it.maximum().stripTrailingZeros().toPlainString(),
            )
        } ?: ModularSheetValue(field.kind)
        RuleKind.CONDITION -> ModularSheetValue(
            field.kind,
            runtime.conditionStacks().getOrDefault(id, 0).toString(),
            (rules.conditionDefinitions()[id]?.maximumStacks() ?: 1).toString(),
        )
        else -> ModularSheetValue(field.kind, field.description)
    }
}

private fun BigDecimal.coerceAtLeast(minimum: BigDecimal): BigDecimal = max(minimum)
