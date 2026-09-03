package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.content.srd521it.SrdChoiceOption
import app.d6d.rules.character.Ability
import app.d6d.rules.character.BackgroundDefinition
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterStatDefinition
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.RuleEffect
import app.d6d.sheet.formatModifier
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.DialogTitle
import app.d6d.ui.components.Eyebrow
import app.d6d.i18n.abbreviationIn
import app.d6d.i18n.label
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SrdProgressionDialog(
    viewModel: SheetViewModel,
    onDismiss: () -> Unit,
) {
    val words = strings.sheet
    val sheet = viewModel.character
    val statDefinitions = viewModel.statDefinitionsFor(sheet)
    val initialClass = sheet.progression.classLevels.firstOrNull()?.classId
        ?: viewModel.srdClasses.firstOrNull()?.id
        ?: CharacterClassId.BARBARIAN
    var selectedClass by remember(
        sheet.id,
        sheet.effectiveLevel,
        viewModel.selectedCharacterRulesetHash,
    ) { mutableStateOf(initialClass) }
    var selected by remember(sheet.id, sheet.effectiveLevel, selectedClass) {
        mutableStateOf<Map<String, List<String>>>(emptyMap())
    }
    var abilityIncreases by remember(sheet.id, sheet.effectiveLevel) {
        mutableStateOf<Map<Ability, Int>>(emptyMap())
    }
    var backgroundIncreases by remember(sheet.id, sheet.effectiveLevel) {
        mutableStateOf<Map<Ability, Int>>(emptyMap())
    }
    var useFixedHitPoints by remember(sheet.id, sheet.effectiveLevel) { mutableStateOf(true) }
    var attemptedApply by remember(sheet.id, sheet.effectiveLevel) { mutableStateOf(false) }
    var rolledHitPoints by remember(sheet.id, sheet.effectiveLevel, selectedClass) {
        mutableStateOf(viewModel.fixedHitPointIncrease(selectedClass))
    }
    fun requirementsFor(provisional: List<ChoiceSelection>): List<ChoiceDefinition> =
        runCatching {
            viewModel.progressionRequirements(selectedClass, provisional)
        }.getOrDefault(emptyList())

    val draft = stabilizeProgressionDraft(selected, ::requirementsFor)
    val activeSelections = draft.selections
    val provisionalSelections = activeSelections.toChoiceSelections()
    val requirements = draft.requirements
    val firstLevel = !sheet.progression.configured
    val selectedBackground = activeSelections.values
        .asSequence()
        .flatten()
        .mapNotNull(viewModel::backgroundDefinition)
        .firstOrNull()
    val complete = requirements.all { activeSelections[it.id].orEmpty().size == it.count }
    val hasAbilityScoreIncrease = activeSelections.values.flatten().any {
        it.endsWith(":aumento-punteggi-caratteristica")
    }
    val conditionalAbilityIncreases = requirements
        .filter { it.kind == ChoiceKind.ABILITY_SCORE_INCREASE }
        .flatMap { activeSelections[it.id].orEmpty() }
        .mapNotNull { abilityFromChoiceOption(it, statDefinitions) }
        .groupingBy { it }
        .eachCount()
    val abilityAllocationValid = !hasAbilityScoreIncrease || abilityIncreases.values.sum() == 2
    val backgroundAllocationValid = !firstLevel || selectedBackground?.let { background ->
        val distribution = backgroundIncreases.filterValues { it > 0 }.values.sorted()
        (distribution == listOf(1, 2) || distribution == listOf(1, 1, 1)) &&
            backgroundIncreases.keys.all { it in background.abilityOptions } &&
            backgroundIncreases.all { (ability, amount) ->
                val definition = statDefinitions.firstOrNull { it.id == ability }
                (sheet.abilityScores[ability] ?: definition?.defaultScore ?: 10) + amount <=
                    (definition?.advancementMaximum ?: 20)
            }
    } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DialogTitle(if (firstLevel) words.guidedCreationTitle else words.srdLevelUpTitle)
                Text(
                    if (firstLevel) {
                        words.chooseExactlyForFirstLevel
                    } else {
                        words.levelAndExperience(sheet.effectiveLevel + 1, sheet.experiencePoints)
                    },
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 650.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (firstLevel) {
                    Eyebrow(strings.rules.ruleset)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        viewModel.availableRulesets.forEach { revision ->
                            val chosen = revision.canonicalHash() == viewModel.selectedCharacterRulesetHash
                            GameButton(
                                label = revision.name(),
                                subtitle = revision.version(),
                                accent = if (chosen) Palette.Heal else Palette.TextMuted,
                                selected = chosen,
                                dense = true,
                                onClick = {
                                    if (viewModel.selectCharacterRuleset(revision.canonicalHash())) {
                                        selectedClass = viewModel.srdClasses.firstOrNull()?.id
                                            ?: CharacterClassId.BARBARIAN
                                        selected = emptyMap()
                                        abilityIncreases = emptyMap()
                                        backgroundIncreases = emptyMap()
                                        rolledHitPoints = viewModel.fixedHitPointIncrease(selectedClass)
                                    }
                                },
                            )
                        }
                    }
                }
                Eyebrow(strings.abilities.classCaps)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    viewModel.srdClasses
                        .filter { sheet.progression.levelIn(it.id) < it.maximumLevel }
                        .forEach { definition ->
                            val chosen = selectedClass == definition.id
                            GameButton(
                                label = buildString {
                                    append(definition.name)
                                    val current = sheet.progression.levelIn(definition.id)
                                    if (current > 0) append(" $current→${current + 1}")
                                },
                                accent = if (chosen) Palette.Gold else Palette.TextMuted,
                                selected = chosen,
                                dense = true,
                                onClick = {
                                    selectedClass = definition.id
                                    selected = emptyMap()
                                    abilityIncreases = emptyMap()
                                    backgroundIncreases = emptyMap()
                                    rolledHitPoints = viewModel.fixedHitPointIncrease(definition.id)
                                },
                            )
                        }
                }

                if (!firstLevel && sheet.progression.levelIn(selectedClass) == 0) {
                    Text(
                        words.multiclassNote,
                        color = Palette.Temporary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                requirements.forEach { choice ->
                    ChoicePicker(
                        choice = choice,
                        options = viewModel.progressionOptions(
                            choice,
                            selectedClass,
                            provisionalSelections,
                        ),
                        selectedIds = activeSelections[choice.id].orEmpty(),
                        onChange = { ids ->
                            if (choice.kind == ChoiceKind.BACKGROUND) {
                                backgroundIncreases = emptyMap()
                            }
                            val changed = activeSelections + (choice.id to ids)
                            selected = stabilizeProgressionDraft(
                                changed,
                                ::requirementsFor,
                            ).selections
                        },
                    )
                }

                selectedBackground?.let { background ->
                    BackgroundAbilityScorePicker(
                        background = background,
                        sheetScores = sheet.abilityScores,
                        statDefinitions = statDefinitions,
                        increases = backgroundIncreases,
                        onChange = { backgroundIncreases = it },
                    )
                }

                if (hasAbilityScoreIncrease) {
                    AbilityScoreIncreasePicker(
                        sheetScores = sheet.abilityScores,
                        statDefinitions = statDefinitions,
                        increases = abilityIncreases,
                        onChange = { abilityIncreases = it },
                    )
                }

                SheetBox(words.newLevelHitPoints) {
                    if (firstLevel) {
                        Text(
                            words.firstLevelUsesMaximum,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Chip(words.fixedHitPoints(viewModel.fixedHitPointIncrease(selectedClass)), Palette.Heal)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameButton(
                                words.fixedValue,
                                accent = if (useFixedHitPoints) Palette.Heal else Palette.TextMuted,
                                selected = useFixedHitPoints,
                                dense = true,
                                onClick = { useFixedHitPoints = true },
                            )
                            GameButton(
                                words.rollTheDie,
                                accent = if (!useFixedHitPoints) Palette.Heal else Palette.TextMuted,
                                selected = !useFixedHitPoints,
                                dense = true,
                                onClick = { useFixedHitPoints = false },
                            )
                        }
                        if (useFixedHitPoints) {
                            Chip(words.fixedHitPoints(viewModel.fixedHitPointIncrease(selectedClass)), Palette.Heal)
                        } else {
                            SheetNumberField(words.dieResultPlusConstitution, rolledHitPoints) {
                                rolledHitPoints = it.coerceAtLeast(1)
                            }
                        }
                    }
                }

                viewModel.status?.takeIf { attemptedApply }?.let {
                    Text(it, color = Palette.Bloodied, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            GameButton(
                label = if (firstLevel) words.createCharacter else words.applyLevel,
                accent = if (complete && abilityAllocationValid && backgroundAllocationValid) {
                    Palette.Heal
                } else {
                    Palette.TextFaint
                },
                onClick = {
                    if (!complete || !abilityAllocationValid || !backgroundAllocationValid) return@GameButton
                    attemptedApply = true
                    val applied = viewModel.advanceCharacter(
                        LevelUpRequest(
                            classId = selectedClass,
                            hitPointIncrease = if (firstLevel || useFixedHitPoints) {
                                viewModel.fixedHitPointIncrease(selectedClass)
                            } else {
                                rolledHitPoints
                            },
                            usedFixedHitPoints = firstLevel || useFixedHitPoints,
                            selections = requirements.map {
                                ChoiceSelection(it.id, activeSelections[it.id].orEmpty())
                            },
                            abilityScoreIncreases = resolvedAbilityScoreIncreases(
                                hasExplicitIncreaseFeat = hasAbilityScoreIncrease,
                                explicitIncreases = abilityIncreases,
                                conditionalIncreases = conditionalAbilityIncreases,
                            ),
                            backgroundAbilityScoreIncreases = backgroundIncreases,
                        ),
                    )
                    if (applied) onDismiss()
                },
            )
        },
        dismissButton = {
            GameButton(strings.common.cancel, accent = Palette.TextMuted, onClick = onDismiss)
        },
    )
}

internal data class StabilizedProgressionDraft(
    val selections: Map<String, List<String>>,
    val requirements: List<ChoiceDefinition>,
)

/**
 * Elimina le scelte figlie che non sono più richieste dopo il cambio di
 * un'opzione padre.
 *
 * Una figlia obsoleta può a sua volta tenere viva una nipote (per esempio il
 * talento Iniziato alla magia e le sue scelte di incantesimo), quindi si
 * ricalcolano i requisiti finché non viene più rimossa alcuna chiave.
 */
internal fun stabilizeProgressionDraft(
    selections: Map<String, List<String>>,
    requirementsFor: (List<ChoiceSelection>) -> List<ChoiceDefinition>,
): StabilizedProgressionDraft {
    var active = selections
    while (true) {
        val requirements = requirementsFor(active.toChoiceSelections())
        val activeChoiceIds = requirements.mapTo(mutableSetOf()) { it.id }
        val filtered = active.filterKeys { it in activeChoiceIds }
        if (filtered.size == active.size) {
            return StabilizedProgressionDraft(filtered, requirements)
        }
        active = filtered
    }
}

/**
 * Gli aumenti inseriti nel picker valgono soltanto finché è selezionato il
 * talento Aumento dei punteggi di caratteristica. I +1 richiesti da talenti
 * come Lottatore o dai Doni epici arrivano invece dalle scelte condizionali.
 */
internal fun resolvedAbilityScoreIncreases(
    hasExplicitIncreaseFeat: Boolean,
    explicitIncreases: Map<Ability, Int>,
    conditionalIncreases: Map<Ability, Int>,
): Map<Ability, Int> = when {
    conditionalIncreases.isNotEmpty() -> conditionalIncreases
    hasExplicitIncreaseFeat -> explicitIncreases
    else -> emptyMap()
}

private fun Map<String, List<String>>.toChoiceSelections(): List<ChoiceSelection> =
    map { (choiceId, optionIds) -> ChoiceSelection(choiceId, optionIds) }

private fun abilityFromChoiceOption(
    optionId: String,
    statDefinitions: List<CharacterStatDefinition>,
): Ability? {
    val slug = optionId.substringAfterLast(':')
    return statDefinitions.map { it.id }.firstOrNull {
        it.name.lowercase() == slug ||
            it.value.substringAfterLast(':').replace('-', '_').lowercase() == slug
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoicePicker(
    choice: ChoiceDefinition,
    options: List<SrdChoiceOption>,
    selectedIds: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val words = strings.sheet
    var search by remember(choice.id) { mutableStateOf("") }
    var previewed by remember(choice.id) { mutableStateOf<String?>(null) }
    val visible = options.filter {
        search.isBlank() ||
            it.label.contains(search, ignoreCase = true) ||
            it.secondaryLabel.contains(search, ignoreCase = true)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (selectedIds.size == choice.count) Palette.Heal.copy(alpha = 0.65f) else Palette.Line,
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(choice.title, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
        Text(
            words.choicesMade(selectedIds.size, choice.count),
            color = if (selectedIds.size == choice.count) Palette.Heal else Palette.Gold,
            style = MaterialTheme.typography.labelSmall,
        )
        if (options.size > 18) {
            SheetField(words.searchAmongOptions(options.size), search) { search = it }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            visible.forEach { option ->
                val isSelected = option.id in selectedIds
                GameButton(
                    label = buildString {
                        append(option.label)
                        if (option.secondaryLabel.isNotBlank()) append(" · ${option.secondaryLabel}")
                    },
                    accent = if (isSelected) Palette.Party else Palette.TextMuted,
                    selected = isSelected,
                    dense = true,
                    onClick = {
                        onChange(
                            when {
                                isSelected -> selectedIds - option.id
                                choice.count == 1 -> listOf(option.id)
                                selectedIds.size < choice.count -> selectedIds + option.id
                                else -> selectedIds
                            },
                        )
                    },
                    onHoverChange = { hovered ->
                        previewed = if (hovered) option.id else previewed.takeIf { it != option.id }
                    },
                )
            }
        }
        // Senza il testo si sceglie fra nomi. Stamparle tutte renderebbe pero'
        // la finestra illeggibile quanto non averne nessuna: si legge quella
        // sotto il puntatore e, quando il puntatore e' altrove, quelle prese.
        val detailed = visible.filter { it.id == previewed }
            .ifEmpty { visible.filter { it.id in selectedIds } }
        detailed.forEach { option -> OptionDetails(option) }
        if (visible.isEmpty()) {
            Text(
                words.noOptionForClassAndLevel,
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Testo di una singola opzione: cosa fa, cosa richiede, cosa cambia nei numeri.
 *
 * Gli effetti compaiono come riga a parte perche' sono la differenza fra un
 * privilegio che l'app applica da sola e uno che resta da applicare al tavolo.
 */
@Composable
private fun OptionDetails(option: SrdChoiceOption) {
    val strings = strings
    val words = strings.sheet
    if (option.description.isBlank() && option.effects.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(option.label, color = Palette.GoldBright, style = MaterialTheme.typography.labelSmall)
        if (option.description.isNotBlank()) {
            Text(
                text = option.description,
                color = Palette.Text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        option.effects.forEach { effect ->
            Text(
                text = words.applied(effect.readableText(strings)),
                color = Palette.Heal,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Come si legge un effetto: "+1 alla Classe Armatura con un'armatura indossata". */
internal fun RuleEffect.readableText(strings: Strings): String {
    val strings = strings
    val language = strings.language
    val words = strings.sheet
    // La condizione, quando c'e', arriva gia' preceduta dallo spazio: cosi' la
    // frase resta pulita anche quando l'effetto vale sempre.
    val when_ = if (condition == EffectCondition.ALWAYS) "" else " " + condition.label(language)
    return when (target) {
        EffectTarget.ARMOR_CLASS -> words.effectOnArmorClass(formatModifier(amount), when_)
        // L'importo e' in piedi, come dice il nome del bersaglio: va convertito
        // nella misura di chi legge, non solo etichettato con un'altra unita'.
        EffectTarget.SPEED_FEET -> words.effectOnSpeed(
            (if (amount >= 0) "+" else "−") + distanceLabel(kotlin.math.abs(amount), language),
            when_,
        )
        else -> words.effectOnAttack(formatModifier(amount), target.label(language), when_)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundAbilityScorePicker(
    background: BackgroundDefinition,
    sheetScores: Map<Ability, Int>,
    statDefinitions: List<CharacterStatDefinition>,
    increases: Map<Ability, Int>,
    onChange: (Map<Ability, Int>) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    SheetBox(words.backgroundAbilityScoresTitle(background.name)) {
        Text(
            words.backgroundAbilityScoresBody,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            background.abilityOptions.sortedBy { it.ordinal }.forEach { ability ->
                val definition = statDefinitions.firstOrNull { it.id == ability }
                val current = sheetScores[ability] ?: definition?.defaultScore ?: 10
                val value = increases[ability] ?: 0
                GameButton(
                    "${definition?.abbreviation ?: ability.abbreviationIn(language)} $current${if (value > 0) " +$value" else ""}",
                    accent = if (value > 0) Palette.Gold else Palette.TextMuted,
                    selected = value > 0,
                    dense = true,
                    onClick = {
                        val totalWithout = increases.values.sum() - value
                        val next = when {
                            value == 0 && totalWithout < 3 && current < (definition?.advancementMaximum ?: 20) -> 1
                            value == 1 && totalWithout <= 1 && current <= (definition?.advancementMaximum ?: 20) - 2 -> 2
                            else -> 0
                        }
                        onChange((increases + (ability to next)).filterValues { it > 0 })
                    },
                )
            }
        }
        val distribution = increases.values.sorted()
        val valid = distribution == listOf(1, 2) || distribution == listOf(1, 1, 1)
        Text(
            words.assignedOutOf(increases.values.sum(), 3),
            color = if (valid) Palette.Heal else Palette.Gold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityScoreIncreasePicker(
    sheetScores: Map<Ability, Int>,
    statDefinitions: List<CharacterStatDefinition>,
    increases: Map<Ability, Int>,
    onChange: (Map<Ability, Int>) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    SheetBox(words.abilityScoreIncreaseTitle) {
        Text(
            words.abilityScoreIncreaseBody,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            statDefinitions.forEach { definition ->
                val ability = definition.id
                val current = sheetScores[ability] ?: definition.defaultScore
                val value = increases[ability] ?: 0
                GameButton(
                    "${definition.abbreviation} $current${if (value > 0) " +$value" else ""}",
                    accent = if (value > 0) Palette.Gold else Palette.TextMuted,
                    selected = value > 0,
                    dense = true,
                    onClick = {
                        val totalWithout = increases.values.sum() - value
                        val next = when {
                            value == 0 && totalWithout < 2 && current < definition.advancementMaximum -> 1
                            value == 1 && totalWithout == 0 && current <= definition.advancementMaximum - 2 -> 2
                            else -> 0
                        }
                        onChange((increases + (ability to next)).filterValues { it > 0 })
                    },
                )
            }
        }
        Text(
            words.assignedOutOf(increases.values.sum(), 2),
            color = if (increases.values.sum() == 2) Palette.Heal else Palette.Gold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
