package app.d6d.ui.abilities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.sheet.i18n.damageText
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.currentLanguage
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CatalogDamage
import app.d6d.sheet.CatalogHealing
import app.d6d.sheet.CatalogHealingBonusSource
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.sheet.SheetBox
import app.d6d.ui.sheet.SheetCheck
import app.d6d.ui.sheet.SheetField
import app.d6d.ui.sheet.SheetMetreField
import app.d6d.ui.sheet.SheetNumberField
import app.d6d.ui.sheet.SheetTextArea
import app.d6d.ui.sheet.SheetViewModel
import app.d6d.ui.sheet.readableText
import app.d6d.ui.runDiskIo
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Catalogo delle capacità riusabili.
 *
 * Le voci sono definizioni operative, non semplici note: ciò che viene compilato
 * qui è la stessa struttura che il combattimento riceve quando una scheda sceglie
 * la capacità tramite «Aggiungi abilità».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbilityArchive(
    viewModel: SheetViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = strings
    val words = strings.abilities
    val language = strings.language
    val scope = rememberCoroutineScope()
    val catalog = viewModel.abilityCatalog.sortedBy { it.name.lowercase() }
    val characterClasses = viewModel.availableCharacterClasses
    val damageTypes = viewModel.damageTypesFor()
    var categoryFilter by remember { mutableStateOf<RuleElementKind?>(null) }
    var classFilter by remember { mutableStateOf<CharacterClassId?>(null) }
    val categories = RuleElementKind.entries.filter { category ->
        catalog.any { it.category == category }
    }
    val abilities = catalog.filter { ability ->
        val matchesCategory = categoryFilter == null || ability.category == categoryFilter
        val matchesClass = classFilter == null ||
            ability.classEligibility.isEmpty() ||
            ability.classEligibility.any { it.classId == classFilter }
        matchesCategory && matchesClass
    }
    val first = catalog.firstOrNull()
    var selectedId by remember { mutableStateOf(first?.id) }
    var draft by remember { mutableStateOf(first ?: newAbility(strings)) }
    var compactDetail by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CatalogAbility?>(null) }

    fun select(ability: CatalogAbility) {
        selectedId = ability.id
        draft = ability
        compactDetail = true
    }

    fun create() {
        val created = newAbility(strings)
        selectedId = null
        draft = created
        compactDetail = true
    }

    fun duplicate(ability: CatalogAbility) {
        val copy = ability.asCustomCopy(strings)
        selectedId = null
        draft = copy
        compactDetail = true
    }

    /**
     * Riclassifica la voce SRD aperta. La bozza mostrata segue subito, altrimenti
     * i due tasti resterebbero indietro rispetto all'archivio appena salvato.
     */
    fun setPassive(passive: Boolean) {
        val abilityId = draft.id
        scope.launch {
            if (runDiskIo { viewModel.setAbilityPassive(abilityId, passive) } && draft.id == abilityId) {
                draft = draft.copy(passive = passive)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        AbilityArchiveHeader(compact, onCreate = ::create)

        viewModel.status?.let { note ->
            Text(
                text = note,
                color = Palette.Gold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (compact) {
            if (compactDetail) {
                Row(
                    Modifier.fillMaxWidth().background(Palette.Surface).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameButton(words.backToAbilities, accent = Palette.TextMuted, onClick = { compactDetail = false })
                    Text(
                        text = draft.name.ifBlank { words.newAbility },
                        color = Palette.Text,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AbilityDetails(
                    draft = draft,
                    onChange = { draft = it },
                    onSave = {
                        val saving = draft
                        scope.launch {
                            if (
                                !saving.immutable &&
                                runDiskIo { viewModel.upsertAbility(saving) } &&
                                draft.id == saving.id
                            ) {
                                selectedId = saving.id
                            }
                        }
                    },
                    onDelete = if (selectedId == null || draft.immutable) null else {
                        { pendingDelete = draft }
                    },
                    onDuplicate = { duplicate(draft) },
                    passiveOverridden = viewModel.abilityPassiveIsOverridden(draft.id),
                    onPassiveChange = ::setPassive,
                    characterClasses = characterClasses,
                    damageTypes = damageTypes,
                    modifier = Modifier.weight(1f),
                )
            } else {
                AbilityList(
                    abilities = abilities,
                    totalCount = catalog.size,
                    categories = categories,
                    categoryFilter = categoryFilter,
                    classFilter = classFilter,
                    characterClasses = characterClasses,
                    onCategoryFilter = { categoryFilter = it },
                    onClassFilter = { classFilter = it },
                    selectedId = selectedId,
                    onSelect = ::select,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(Modifier.weight(1f)) {
                AbilityList(
                    abilities = abilities,
                    totalCount = catalog.size,
                    categories = categories,
                    categoryFilter = categoryFilter,
                    classFilter = classFilter,
                    characterClasses = characterClasses,
                    onCategoryFilter = { categoryFilter = it },
                    onClassFilter = { classFilter = it },
                    selectedId = selectedId,
                    onSelect = ::select,
                    modifier = Modifier.width(286.dp),
                )
                AbilityDetails(
                    draft = draft,
                    onChange = { draft = it },
                    onSave = {
                        val saving = draft
                        scope.launch {
                            if (
                                !saving.immutable &&
                                runDiskIo { viewModel.upsertAbility(saving) } &&
                                draft.id == saving.id
                            ) {
                                selectedId = saving.id
                            }
                        }
                    },
                    onDelete = if (selectedId == null || draft.immutable) null else {
                        { pendingDelete = draft }
                    },
                    onDuplicate = { duplicate(draft) },
                    passiveOverridden = viewModel.abilityPassiveIsOverridden(draft.id),
                    onPassiveChange = ::setPassive,
                    characterClasses = characterClasses,
                    damageTypes = damageTypes,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    pendingDelete?.let { ability ->
        val usage = viewModel.abilityUsageCount(ability.id)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Palette.Surface,
            title = { Text(words.deleteAbilityTitle, color = Palette.Text) },
            text = {
                Text(
                    if (usage == 0) {
                        words.deleteAbilityBody(ability.name)
                    } else {
                        words.abilityInUse(ability.name, usage)
                    },
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                if (usage == 0 && !ability.immutable) {
                    GameButton(strings.common.delete, accent = Palette.Enemy, onClick = {
                        scope.launch {
                            if (runDiskIo { viewModel.deleteAbility(ability.id) }) {
                                val remaining = viewModel.abilityCatalog.firstOrNull()
                                selectedId = remaining?.id
                                draft = remaining ?: newAbility(strings)
                            }
                            pendingDelete = null
                        }
                    })
                }
            },
            dismissButton = {
                GameButton(strings.common.cancel, accent = Palette.TextMuted, onClick = { pendingDelete = null })
            },
        )
    }
}


@Composable
private fun AbilityDetails(
    draft: CatalogAbility,
    onChange: (CatalogAbility) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDuplicate: () -> Unit,
    passiveOverridden: Boolean,
    onPassiveChange: (Boolean) -> Unit,
    characterClasses: List<ClassDefinition>,
    damageTypes: List<DamageType>,
    modifier: Modifier = Modifier,
) {
    if (draft.immutable) {
        ReadOnlyAbilityDetails(
            ability = draft,
            onDuplicate = onDuplicate,
            overridden = passiveOverridden,
            onPassiveChange = onPassiveChange,
            characterClasses = characterClasses,
            modifier = modifier,
        )
    } else {
        AbilityEditor(
            draft = draft,
            onChange = { updated -> onChange(updated.enforceHealingConstraints()) },
            onSave = onSave,
            onDelete = onDelete,
            characterClasses = characterClasses,
            damageTypes = damageTypes,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadOnlyAbilityDetails(
    ability: CatalogAbility,
    onDuplicate: () -> Unit,
    overridden: Boolean,
    onPassiveChange: (Boolean) -> Unit,
    characterClasses: List<ClassDefinition>,
    modifier: Modifier = Modifier,
) {
    val words = strings.abilities
    val language = currentLanguage
    val strings = strings
    val classNames = characterClasses.associate { it.id to it.name }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetBox(words.srdContent) {
                Text(
                    text = ability.name.ifBlank { strings.compendium.unnamed },
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
                AbilityMetadataChips(ability, classNames)
                Text(
                    text = words.abilityId(ability.id),
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (ability.prerequisite.isNotBlank()) {
                    Text(
                        text = words.prerequisite(ability.prerequisite),
                        color = Palette.GoldBright,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SheetBox(language.pick("Regole", "Rules")) {
                Text(
                    text = ability.rulesText.ifBlank { words.noRulesText },
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SheetBox(strings.abilities.howItWorks) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Chip(ability.activationCost.labelIn(strings), Palette.Gold)
                    Chip(ability.resolutionMethod.labelIn(strings), Palette.Party)
                    if (ability.dealsDamage) Chip(ability.damageText(language), Palette.Enemy)
                    ability.healing?.let { healing ->
                        Chip(
                            words.healingSummary(
                                healing.amountText(strings, classNames),
                                healing.target.label(strings),
                            ),
                            Palette.Heal,
                        )
                    }
                    if (ability.isArea) Chip(words.areaOf(distanceLabel(ability.areaRadiusFeet, language)), Palette.Crit)
                }
                if (ability.healing == null) {
                    PassiveSelector(
                        passive = ability.passive,
                        overridden = overridden,
                        onChange = onPassiveChange,
                    )
                } else {
                    Text(
                        words.activeHealingNote,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (ability.effects.isNotEmpty()) {
                    Text(
                        words.appliedByApp,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ability.effects.forEach { effect ->
                            Chip(effect.readableText(strings), Palette.Heal)
                        }
                    }
                }
                if (ability.resourceId != null || ability.resourceCost > 0) {
                    Text(
                        text = buildString {
                            append(language.pick("Risorsa: ", "Resource: "))
                                .append(ability.resourceId ?: language.pick("specifica", "specific"))
                            if (ability.resourceCost > 0) append(words.costSuffix(ability.resourceCost.toString()))
                        },
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (
                ability.spellLevel != null ||
                ability.school.isNotBlank() ||
                ability.castingTime.isNotBlank() ||
                ability.components.isNotBlank() ||
                ability.duration.isNotBlank()
            ) {
                SheetBox(strings.abilities.spell) {
                    ability.spellLevel?.let { level ->
                        ReadOnlyProperty(
                            strings.common.level,
                            if (level == 0) strings.abilities.cantrip else "$level",
                        )
                    }
                    if (ability.school.isNotBlank()) {
                        ReadOnlyProperty(language.pick("Scuola", "School"), ability.school)
                    }
                    if (ability.castingTime.isNotBlank()) {
                        ReadOnlyProperty(words.castingTime, ability.castingTime)
                    }
                    if (ability.components.isNotBlank()) {
                        ReadOnlyProperty(language.pick("Componenti", "Components"), ability.components)
                    }
                    if (ability.duration.isNotBlank()) {
                        ReadOnlyProperty(language.pick("Durata", "Duration"), ability.duration)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (ability.concentration) Chip(strings.abilities.concentration, Palette.Gold)
                        if (ability.ritual) Chip(language.pick("Rituale", "Ritual"), Palette.Party)
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = if (ability.healing != null) {
                    words.srdHealingProtected
                } else {
                    words.srdEntryProtected
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton(
                label = words.duplicateAsCustom,
                accent = Palette.Party,
                onClick = onDuplicate,
            )
        }
    }
}

/**
 * Sceglie se la capacità si spende nel turno o vale sempre.
 *
 * E' l'unica cosa modificabile anche sulle voci SRD, che restano per il resto in
 * sola lettura: non cambia il contenuto del pacchetto — nome, regole e numeri
 * restano i suoi — ma dice a questo tavolo dove va messa. Una padronanza d'arme
 * fra i comandi da premere sarebbe rumore; un privilegio classificato come
 * permanente per sbaglio sparirebbe dai comandi che servono.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassiveSelector(
    passive: Boolean,
    overridden: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val words = strings.abilities
    Text(words.duringTurn, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GameButton(
            label = strings.abilities.activeLabel,
            accent = if (!passive) Palette.Gold else Palette.TextMuted,
            selected = !passive,
            dense = true,
            onClick = { onChange(false) },
        )
        GameButton(
            label = currentLanguage.pick("Passiva", "Passive"),
            accent = if (passive) Palette.Crit else Palette.TextMuted,
            selected = passive,
            dense = true,
            onClick = { onChange(true) },
        )
        if (overridden) Chip(words.tableChoice, Palette.Crit)
    }
    Text(
        text = if (passive) {
            words.passiveTraitHint
        } else {
            words.activeAbilityHint
        },
        color = Palette.TextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ReadOnlyProperty(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            color = Palette.Text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityEditor(
    draft: CatalogAbility,
    onChange: (CatalogAbility) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    characterClasses: List<ClassDefinition>,
    damageTypes: List<DamageType>,
    modifier: Modifier = Modifier,
) {
    val words = strings.abilities
    val language = currentLanguage
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetBox(strings.abilities.information) {
                SheetField(strings.common.nameLabel, draft.name) { onChange(draft.copy(name = it)) }
                Text(
                    text = words.abilityId(draft.id),
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
                SheetTextArea(draft.rulesText, minLines = 4) { onChange(draft.copy(rulesText = it)) }
            }

            SheetBox(strings.abilities.howItWorks) {
                if (draft.healing == null) {
                    PassiveSelector(
                        passive = draft.passive,
                        overridden = false,
                        onChange = { onChange(draft.copy(passive = it)) },
                    )
                } else {
                    Text(
                        words.healingIsActiveAutomated,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(language.pick("COSTO", "COST"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ActivationCost.entries.forEach { cost ->
                        GameButton(
                            label = cost.labelIn(strings),
                            accent = if (draft.activationCost == cost) Palette.Gold else Palette.TextMuted,
                            selected = draft.activationCost == cost,
                            dense = true,
                            onClick = { onChange(draft.copy(activationCost = cost)) },
                        )
                    }
                }

                Text(strings.abilities.resolutionCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                if (draft.healing != null) {
                    Chip(ResolutionMethod.AUTOMATIC.labelIn(strings), Palette.Heal)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ResolutionMethod.entries.forEach { method ->
                            GameButton(
                                label = method.labelIn(strings),
                                accent = if (draft.resolutionMethod == method) Palette.Party else Palette.TextMuted,
                                selected = draft.resolutionMethod == method,
                                dense = true,
                                onClick = {
                                    onChange(
                                        draft.copy(
                                            resolutionMethod = method,
                                            dealsDamage = draft.dealsDamage || method == ResolutionMethod.ATTACK_ROLL,
                                            saveAbility = if (method == ResolutionMethod.SAVING_THROW) {
                                                draft.saveAbility ?: Ability.DEXTERITY
                                            } else {
                                                draft.saveAbility
                                            },
                                            automationStatus = if (method == ResolutionMethod.MANUAL) {
                                                AutomationStatus.MANUAL_REQUIRED
                                            } else if (draft.automationStatus == AutomationStatus.MANUAL_REQUIRED) {
                                                AutomationStatus.AUTOMATED
                                            } else {
                                                draft.automationStatus
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (draft.resolutionMethod == ResolutionMethod.ATTACK_ROLL) {
                        SheetNumberField(words.attackBonus, draft.attackBonus, Modifier.width(130.dp)) {
                            onChange(draft.copy(attackBonus = it))
                        }
                    }
                    SheetMetreField(language.pick("Gittata", "Range"), draft.rangeFeet, Modifier.width(150.dp)) {
                        onChange(draft.copy(rangeFeet = it.coerceAtLeast(0)))
                    }
                    if (!draft.isArea) {
                        SheetNumberField(strings.abilities.targets, draft.maxTargets, Modifier.width(120.dp)) {
                            onChange(draft.copy(maxTargets = it.coerceAtLeast(1)))
                        }
                    }
                }

                SheetCheck(
                    words.spellOrCantrip,
                    draft.isSpellOrCantrip,
                ) { spell ->
                    onChange(draft.copy(spellOrCantrip = spell))
                }
                if (draft.resolutionMethod == ResolutionMethod.ATTACK_ROLL) {
                    Text(
                        words.attackAbilityCaps,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GameButton(
                            label = words.unspecified,
                            accent = if (draft.attackAbility == null) Palette.Gold else Palette.TextMuted,
                            selected = draft.attackAbility == null,
                            dense = true,
                            onClick = { onChange(draft.copy(attackAbility = null)) },
                        )
                        Ability.entries.forEach { ability ->
                            GameButton(
                                label = ability.abbreviation,
                                accent = if (draft.attackAbility == ability) Palette.Gold else Palette.TextMuted,
                                selected = draft.attackAbility == ability,
                                dense = true,
                                onClick = { onChange(draft.copy(attackAbility = ability)) },
                            )
                        }
                    }
                }

                if (draft.healing == null) {
                    SheetCheck(
                        words.manualResolution,
                        draft.automationStatus == AutomationStatus.MANUAL_REQUIRED,
                    ) { manual ->
                        onChange(
                            draft.copy(
                                automationStatus = if (manual) {
                                    AutomationStatus.MANUAL_REQUIRED
                                } else {
                                    AutomationStatus.AUTOMATED
                                },
                            ),
                        )
                    }
                }
            }

            SheetBox(strings.abilities.healing) {
                SheetCheck(words.restoresHitPoints, draft.healing != null) { enabled ->
                    onChange(
                        if (enabled) {
                            draft.copy(
                                healing = CatalogHealing.dice(HealingTarget.SELF_OR_ALLY, 1, 8),
                            ).enforceHealingConstraints()
                        } else {
                            draft.copy(healing = null)
                        },
                    )
                }
                draft.healing?.let { healing ->
                    Text(strings.abilities.targetCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        HealingTarget.entries.forEach { target ->
                            GameButton(
                                label = target.label(strings),
                                accent = if (healing.target == target) Palette.Heal else Palette.TextMuted,
                                selected = healing.target == target,
                                dense = true,
                                onClick = { onChange(draft.copy(healing = healing.copy(target = target))) },
                            )
                        }
                    }

                    Text(words.quantityCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GameButton(
                            label = language.pick("Dadi", "Dice"),
                            accent = if (healing.dice != null) Palette.Heal else Palette.TextMuted,
                            selected = healing.dice != null,
                            dense = true,
                            onClick = {
                                if (healing.dice == null) {
                                    onChange(
                                        draft.copy(
                                            healing = CatalogHealing.dice(healing.target, 1, 8),
                                        ),
                                    )
                                }
                            },
                        )
                        GameButton(
                            label = language.pick("Fissa", "Fixed"),
                            accent = if (healing.fixedAmount != null) Palette.Heal else Palette.TextMuted,
                            selected = healing.fixedAmount != null,
                            dense = true,
                            onClick = {
                                if (healing.fixedAmount == null) {
                                    onChange(
                                        draft.copy(
                                            healing = CatalogHealing.fixed(healing.target, 1),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    healing.dice?.let { dice ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            SheetNumberField(words.diceCount, dice.count, Modifier.width(120.dp)) {
                                onChange(
                                    draft.copy(
                                        healing = healing.copy(
                                            dice = dice.copy(count = it.coerceAtLeast(1)),
                                        ),
                                    ),
                                )
                            }
                            SheetNumberField(language.pick("Facce", "Sides"), dice.sides, Modifier.width(105.dp)) {
                                onChange(
                                    draft.copy(
                                        healing = healing.copy(
                                            dice = dice.copy(sides = it.coerceAtLeast(2)),
                                        ),
                                    ),
                                )
                            }
                            SheetNumberField(language.pick("Modificatore", "Modifier"), dice.modifier, Modifier.width(130.dp)) {
                                onChange(
                                    draft.copy(
                                        healing = healing.copy(dice = dice.copy(modifier = it)),
                                    ),
                                )
                            }
                        }
                        Text(
                            words.dynamicBonusCaps,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            CatalogHealingBonusSource.entries.forEach { source ->
                                GameButton(
                                    label = source.label(strings),
                                    accent = if (healing.bonusSource == source) Palette.Heal else Palette.TextMuted,
                                    selected = healing.bonusSource == source,
                                    dense = true,
                                    onClick = {
                                        onChange(
                                            draft.copy(
                                                healing = healing.copy(
                                                    bonusSource = source,
                                                    bonusClassId = if (
                                                        source == CatalogHealingBonusSource.CLASS_LEVEL
                                                    ) {
                                                        healing.bonusClassId
                                                            ?.takeIf { saved ->
                                                                characterClasses.any { it.id == saved }
                                                            }
                                                            ?: characterClasses.first().id
                                                    } else {
                                                        null
                                                    },
                                                ),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        if (healing.bonusSource == CatalogHealingBonusSource.CLASS_LEVEL) {
                            Text(
                                words.bonusClassCaps,
                                color = Palette.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                characterClasses.forEach { definition ->
                                    val classId = definition.id
                                    GameButton(
                                        label = definition.name,
                                        accent = if (healing.bonusClassId == classId) {
                                            Palette.Heal
                                        } else {
                                            Palette.TextMuted
                                        },
                                        selected = healing.bonusClassId == classId,
                                        dense = true,
                                        onClick = {
                                            onChange(
                                                draft.copy(
                                                    healing = healing.copy(bonusClassId = classId),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    healing.fixedAmount?.let { amount ->
                        SheetNumberField(words.hitPoints, amount, Modifier.width(150.dp)) {
                            onChange(
                                draft.copy(
                                    healing = CatalogHealing.fixed(healing.target, it.coerceAtLeast(1)),
                                ),
                            )
                        }
                    }
                    Text(
                        words.healingNoDamageNote,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SheetBox(strings.abilities.damage) {
                if (draft.healing != null) {
                    Text(
                        words.notApplicableHealing,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SheetCheck(
                        words.dealsDamage,
                        draft.dealsDamage,
                    ) { enabled ->
                        if (draft.resolutionMethod != ResolutionMethod.ATTACK_ROLL || enabled) {
                            onChange(draft.copy(dealsDamage = enabled))
                        }
                    }
                }
                if (draft.dealsDamage && draft.healing == null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SheetNumberField(words.diceCount, draft.diceCount, Modifier.width(120.dp)) {
                            onChange(draft.copy(diceCount = it.coerceAtLeast(1)))
                        }
                        SheetNumberField(language.pick("Facce", "Sides"), draft.diceSides, Modifier.width(105.dp)) {
                            onChange(draft.copy(diceSides = it.coerceAtLeast(2)))
                        }
                        SheetNumberField(language.pick("Modificatore", "Modifier"), draft.damageModifier, Modifier.width(130.dp)) {
                            onChange(draft.copy(damageModifier = it))
                        }
                    }
                    DamageTypeSelector(words.mainTypeCaps, draft.damageType, damageTypes) { type ->
                        onChange(draft.copy(damageType = type))
                    }

                    draft.additionalDamage.forEachIndexed { index, component ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Palette.Abyss.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                                .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    words.extraComponentCaps(index + 1),
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                GameButton(strings.common.remove, accent = Palette.Enemy, dense = true, onClick = {
                                    onChange(
                                        draft.copy(
                                            additionalDamage = draft.additionalDamage
                                                .filterIndexed { itemIndex, _ -> itemIndex != index },
                                        ),
                                    )
                                })
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                SheetNumberField(words.diceCount, component.diceCount, Modifier.width(120.dp)) {
                                    onChange(draft.withAdditionalDamage(index, component.copy(diceCount = it.coerceAtLeast(1))))
                                }
                                SheetNumberField(language.pick("Facce", "Sides"), component.diceSides, Modifier.width(105.dp)) {
                                    onChange(draft.withAdditionalDamage(index, component.copy(diceSides = it.coerceAtLeast(2))))
                                }
                                SheetNumberField(language.pick("Modificatore", "Modifier"), component.modifier, Modifier.width(130.dp)) {
                                    onChange(draft.withAdditionalDamage(index, component.copy(modifier = it)))
                                }
                            }
                            DamageTypeSelector(words.extraTypeCaps, component.type, damageTypes) { type ->
                                onChange(draft.withAdditionalDamage(index, component.copy(type = type)))
                            }
                        }
                    }
                    GameButton(words.addDamageComponent, accent = Palette.Party, onClick = {
                        onChange(draft.copy(additionalDamage = draft.additionalDamage + CatalogDamage()))
                    })
                }
            }

            SheetBox(words.areaAndSave) {
                if (draft.healing != null) {
                    Text(
                        words.notApplicableSingleTarget,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SheetCheck(words.areaEffect, draft.isArea) { enabled ->
                        onChange(
                            draft.copy(
                                areaRadiusFeet = if (enabled) draft.areaRadiusFeet.takeIf { it > 0 } ?: 20 else 0,
                                resolutionMethod = if (enabled) ResolutionMethod.SAVING_THROW else draft.resolutionMethod,
                                saveAbility = if (enabled) draft.saveAbility ?: Ability.DEXTERITY else draft.saveAbility,
                                dealsDamage = if (enabled) true else draft.dealsDamage,
                            ),
                        )
                    }
                }
                if (draft.isArea && draft.healing == null) {
                    SheetMetreField(strings.abilities.radius, draft.areaRadiusFeet, Modifier.width(150.dp)) {
                        onChange(draft.copy(areaRadiusFeet = it.coerceAtLeast(1)))
                    }
                }
                if (
                    draft.healing == null &&
                    (draft.resolutionMethod == ResolutionMethod.SAVING_THROW || draft.isArea)
                ) {
                    Text(words.savingThrowCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Ability.entries.forEach { ability ->
                            GameButton(
                                label = ability.abbreviation,
                                accent = if (draft.saveAbility == ability) Palette.Gold else Palette.TextMuted,
                                selected = draft.saveAbility == ability,
                                dense = true,
                                onClick = { onChange(draft.copy(saveAbility = ability)) },
                            )
                        }
                    }
                    SheetCheck(words.halfDamageOnSave, draft.halfOnSave) {
                        onChange(draft.copy(halfOnSave = it))
                    }
                }
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            GameButton(words.saveAbility, accent = Palette.Heal, onClick = onSave)
            onDelete?.let { delete ->
                GameButton(strings.common.delete, accent = Palette.Enemy, onClick = delete)
            }
        }
    }
}

private fun newAbility(strings: Strings): CatalogAbility = CatalogAbility(
    id = "abilita-${System.currentTimeMillis()}",
    name = strings.abilities.newAbility,
    attackAbility = Ability.STRENGTH,
)

private fun CatalogAbility.asCustomCopy(strings: Strings): CatalogAbility = copy(
    id = "abilita-${System.currentTimeMillis()}",
    name = strings.abilities.copyOf(name),
    spellOrCantrip = isSpellOrCantrip,
    category = RuleElementKind.CUSTOM,
    classEligibility = emptyList(),
    sourcePackId = null,
    sourcePackVersion = "1.0.0",
    sourcePage = 0,
    prerequisite = "",
    immutable = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DamageTypeSelector(
    label: String,
    selected: DamageType,
    available: List<DamageType>,
    onSelect: (DamageType) -> Unit,
) {
    val language = currentLanguage
    Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        (available + selected).distinct().forEach { type ->
            GameButton(
                label = type.label(language),
                accent = if (selected == type) Palette.Enemy else Palette.TextMuted,
                selected = selected == type,
                dense = true,
                onClick = { onSelect(type) },
            )
        }
    }
}

private fun CatalogAbility.withAdditionalDamage(index: Int, component: CatalogDamage): CatalogAbility =
    copy(
        additionalDamage = additionalDamage.toMutableList().also { items ->
            if (index in items.indices) items[index] = component
        },
    )

private fun CatalogAbility.enforceHealingConstraints(): CatalogAbility =
    if (healing == null) {
        this
    } else {
        copy(
            passive = false,
            resolutionMethod = ResolutionMethod.AUTOMATIC,
            dealsDamage = false,
            automationStatus = AutomationStatus.AUTOMATED,
            areaRadiusFeet = 0,
            maxTargets = 1,
            effect = AbilityEffect.NONE,
        )
    }

private fun CatalogHealing.amountText(
    strings: Strings,
    classNames: Map<CharacterClassId, String> = emptyMap(),
): String =
    dice?.let { value ->
        buildString {
            append(value.count).append('d').append(value.sides)
            if (value.modifier > 0) append('+')
            if (value.modifier != 0) append(value.modifier)
            when (bonusSource) {
                CatalogHealingBonusSource.NONE -> Unit
                CatalogHealingBonusSource.SPELLCASTING_ABILITY ->
                    append(strings.abilities.plusSpellcastingModifier)
                CatalogHealingBonusSource.CLASS_LEVEL -> append(
                    strings.abilities.plusClassLevel(
                        bonusClassId?.let { classNames[it] ?: it.label(strings.language) }
                            ?: strings.abilities.classLevel,
                    ),
                )
            }
            slotScaling?.let { scaling ->
                append(" · +")
                    .append(scaling.additionalDicePerSlotLevel)
                    .append('d')
                    .append(value.sides)
                    .append(strings.abilities.perLevelAbove(scaling.baseSlotLevel))
            }
        }
    } ?: fixedAmount.toString()

private fun CatalogHealingBonusSource.label(strings: Strings): String = when (this) {
    CatalogHealingBonusSource.NONE -> strings.common.none
    CatalogHealingBonusSource.SPELLCASTING_ABILITY -> strings.abilities.spellcastingModifier
    CatalogHealingBonusSource.CLASS_LEVEL -> strings.abilities.classLevel
}

private fun HealingTarget.label(strings: Strings): String = when (this) {
    HealingTarget.SELF -> strings.abilities.healingSelfOnly
    HealingTarget.ALLY -> strings.abilities.healingAllyOnly
    HealingTarget.SELF_OR_ALLY -> strings.abilities.healingSelfOrAlly
}

// Il costo e il metodo di risoluzione hanno gia' un nome nel vocabolario del
// motore: qui si rimanda a quello invece di tenerne una seconda copia.
internal fun ActivationCost.labelIn(strings: Strings): String = label(strings.language)

internal fun ResolutionMethod.labelIn(strings: Strings): String = label(strings.language)
