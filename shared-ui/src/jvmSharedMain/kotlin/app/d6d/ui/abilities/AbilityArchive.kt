package app.d6d.ui.abilities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CatalogDamage
import app.d6d.sheet.italianLabel
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.sheet.SheetBox
import app.d6d.ui.sheet.SheetCheck
import app.d6d.ui.sheet.SheetField
import app.d6d.ui.sheet.SheetFeetField
import app.d6d.ui.sheet.SheetNumberField
import app.d6d.ui.sheet.SheetTextArea
import app.d6d.ui.sheet.SheetViewModel
import app.d6d.ui.theme.Palette

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
    val abilities = viewModel.library.abilities.sortedBy { it.name.lowercase() }
    val first = abilities.firstOrNull()
    var selectedId by remember { mutableStateOf(first?.id) }
    var draft by remember { mutableStateOf(first ?: newAbility()) }
    var compactDetail by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CatalogAbility?>(null) }

    fun select(ability: CatalogAbility) {
        selectedId = ability.id
        draft = ability
        compactDetail = true
    }

    fun create() {
        val created = newAbility()
        selectedId = null
        draft = created
        compactDetail = true
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
                    GameButton("← Abilità", accent = Palette.TextMuted, onClick = { compactDetail = false })
                    Text(
                        text = draft.name.ifBlank { "Nuova abilità" },
                        color = Palette.Text,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AbilityEditor(
                    draft = draft,
                    onChange = { draft = it },
                    onSave = {
                        if (viewModel.upsertAbility(draft)) {
                            selectedId = draft.id
                        }
                    },
                    onDelete = if (selectedId == null) null else {
                        { pendingDelete = draft }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                AbilityList(
                    abilities = abilities,
                    selectedId = selectedId,
                    onSelect = ::select,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(Modifier.weight(1f)) {
                AbilityList(
                    abilities = abilities,
                    selectedId = selectedId,
                    onSelect = ::select,
                    modifier = Modifier.width(286.dp),
                )
                AbilityEditor(
                    draft = draft,
                    onChange = { draft = it },
                    onSave = {
                        if (viewModel.upsertAbility(draft)) {
                            selectedId = draft.id
                        }
                    },
                    onDelete = if (selectedId == null) null else {
                        { pendingDelete = draft }
                    },
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
            title = { Text("Eliminare l’abilità?", color = Palette.Text) },
            text = {
                Text(
                    if (usage == 0) {
                        "«${ability.name}» verrà rimossa dal catalogo."
                    } else {
                        "«${ability.name}» è usata da $usage " +
                            if (usage == 1) "scheda. Rimuovila prima dal personaggio." else
                                "schede. Rimuovila prima dai personaggi."
                    },
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                if (usage == 0) {
                    GameButton("Elimina", accent = Palette.Enemy, onClick = {
                        if (viewModel.deleteAbility(ability.id)) {
                            val remaining = viewModel.library.abilities.firstOrNull()
                            selectedId = remaining?.id
                            draft = remaining ?: newAbility()
                        }
                        pendingDelete = null
                    })
                }
            },
            dismissButton = {
                GameButton("Annulla", accent = Palette.TextMuted, onClick = { pendingDelete = null })
            },
        )
    }
}

@Composable
private fun AbilityArchiveHeader(compact: Boolean, onCreate: () -> Unit) {
    val title = @Composable {
        Column {
            Text(
                text = "Abilità",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Attacchi, incantesimi e capacità riusabili nelle schede dei personaggi.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (compact) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            title()
            GameButton("＋ Nuova abilità", accent = Palette.Party, onClick = onCreate)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { title() }
            GameButton("＋ Nuova abilità", accent = Palette.Party, onClick = onCreate)
        }
    }
}

@Composable
private fun AbilityList(
    abilities: List<CatalogAbility>,
    selectedId: String?,
    onSelect: (CatalogAbility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Surface.copy(alpha = 0.45f))
            .padding(9.dp),
    ) {
        if (abilities.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nessuna abilità nel catalogo.",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return
        }
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Eyebrow("Abilità (${abilities.size})", color = Palette.Party) }
                items(abilities, key = { it.id }) { ability ->
                    AbilityListRow(ability, selected = ability.id == selectedId) { onSelect(ability) }
                }
            }
            PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun AbilityListRow(ability: CatalogAbility, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Palette.Gold.copy(alpha = 0.11f) else Palette.Night, shape)
            .border(1.dp, if (selected) Palette.Gold else Palette.Line, shape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = ability.name.ifBlank { "Senza nome" },
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Chip(ability.activationCost.label, Palette.Gold)
            Chip(ability.resolutionMethod.label, Palette.Party)
            if (ability.dealsDamage) Chip(ability.damageText, Palette.Enemy)
            if (ability.isArea) Chip("Area ${ability.areaRadiusFeet} ft", Palette.Crit)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityEditor(
    draft: CatalogAbility,
    onChange: (CatalogAbility) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetBox("Informazioni") {
                SheetField("Nome", draft.name) { onChange(draft.copy(name = it)) }
                Text(
                    text = "ID ${draft.id}",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
                SheetTextArea(draft.rulesText, minLines = 4) { onChange(draft.copy(rulesText = it)) }
            }

            SheetBox("Funzionamento") {
                Text("COSTO", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ActivationCost.entries.forEach { cost ->
                        GameButton(
                            label = cost.label,
                            accent = if (draft.activationCost == cost) Palette.Gold else Palette.TextMuted,
                            selected = draft.activationCost == cost,
                            dense = true,
                            onClick = { onChange(draft.copy(activationCost = cost)) },
                        )
                    }
                }

                Text("RISOLUZIONE", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ResolutionMethod.entries.forEach { method ->
                        GameButton(
                            label = method.label,
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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (draft.resolutionMethod == ResolutionMethod.ATTACK_ROLL) {
                        SheetNumberField("Bonus attacco", draft.attackBonus, Modifier.width(130.dp)) {
                            onChange(draft.copy(attackBonus = it))
                        }
                    }
                    SheetFeetField("Gittata", draft.rangeFeet, Modifier.width(150.dp)) {
                        onChange(draft.copy(rangeFeet = it.coerceAtLeast(0)))
                    }
                    if (!draft.isArea) {
                        SheetNumberField("Bersagli", draft.maxTargets, Modifier.width(120.dp)) {
                            onChange(draft.copy(maxTargets = it.coerceAtLeast(1)))
                        }
                    }
                }

                SheetCheck(
                    "Risoluzione manuale al tavolo",
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

            SheetBox("Danno") {
                SheetCheck(
                    "Infligge danno",
                    draft.dealsDamage,
                ) { enabled ->
                    if (draft.resolutionMethod != ResolutionMethod.ATTACK_ROLL || enabled) {
                        onChange(draft.copy(dealsDamage = enabled))
                    }
                }
                if (draft.dealsDamage) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SheetNumberField("Numero dadi", draft.diceCount, Modifier.width(120.dp)) {
                            onChange(draft.copy(diceCount = it.coerceAtLeast(1)))
                        }
                        SheetNumberField("Facce", draft.diceSides, Modifier.width(105.dp)) {
                            onChange(draft.copy(diceSides = it.coerceAtLeast(2)))
                        }
                        SheetNumberField("Modificatore", draft.damageModifier, Modifier.width(130.dp)) {
                            onChange(draft.copy(damageModifier = it))
                        }
                    }
                    DamageTypeSelector("TIPO PRINCIPALE", draft.damageType) { type ->
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
                                    "COMPONENTE AGGIUNTIVA ${index + 1}",
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                GameButton("Rimuovi", accent = Palette.Enemy, dense = true, onClick = {
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
                                SheetNumberField("Numero dadi", component.diceCount, Modifier.width(120.dp)) {
                                    onChange(draft.withAdditionalDamage(index, component.copy(diceCount = it.coerceAtLeast(1))))
                                }
                                SheetNumberField("Facce", component.diceSides, Modifier.width(105.dp)) {
                                    onChange(draft.withAdditionalDamage(index, component.copy(diceSides = it.coerceAtLeast(2))))
                                }
                                SheetNumberField("Modificatore", component.modifier, Modifier.width(130.dp)) {
                                    onChange(draft.withAdditionalDamage(index, component.copy(modifier = it)))
                                }
                            }
                            DamageTypeSelector("TIPO AGGIUNTIVO", component.type) { type ->
                                onChange(draft.withAdditionalDamage(index, component.copy(type = type)))
                            }
                        }
                    }
                    GameButton("＋ Aggiungi componente di danno", accent = Palette.Party, onClick = {
                        onChange(draft.copy(additionalDamage = draft.additionalDamage + CatalogDamage()))
                    })
                }
            }

            SheetBox("Area e tiro salvezza") {
                SheetCheck("Effetto ad area", draft.isArea) { enabled ->
                    onChange(
                        draft.copy(
                            areaRadiusFeet = if (enabled) draft.areaRadiusFeet.takeIf { it > 0 } ?: 20 else 0,
                            resolutionMethod = if (enabled) ResolutionMethod.SAVING_THROW else draft.resolutionMethod,
                            saveAbility = if (enabled) draft.saveAbility ?: Ability.DEXTERITY else draft.saveAbility,
                            dealsDamage = if (enabled) true else draft.dealsDamage,
                        ),
                    )
                }
                if (draft.isArea) {
                    SheetFeetField("Raggio", draft.areaRadiusFeet, Modifier.width(150.dp)) {
                        onChange(draft.copy(areaRadiusFeet = it.coerceAtLeast(1)))
                    }
                }
                if (draft.resolutionMethod == ResolutionMethod.SAVING_THROW || draft.isArea) {
                    Text("TIRO SALVEZZA", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
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
                    SheetCheck("Metà danni con TS superato", draft.halfOnSave) {
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
            GameButton("Salva abilità", accent = Palette.Heal, onClick = onSave)
            onDelete?.let { delete ->
                GameButton("Elimina", accent = Palette.Enemy, onClick = delete)
            }
        }
    }
}

private fun newAbility(): CatalogAbility = CatalogAbility(
    id = "abilita-${System.currentTimeMillis()}",
    name = "Nuova abilità",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DamageTypeSelector(label: String, selected: DamageType, onSelect: (DamageType) -> Unit) {
    Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DamageType.entries.forEach { type ->
            GameButton(
                label = type.italianLabel.replaceFirstChar { it.uppercase() },
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

private val ActivationCost.label: String
    get() = when (this) {
        ActivationCost.ACTION -> "Azione"
        ActivationCost.BONUS_ACTION -> "Azione bonus"
        ActivationCost.REACTION -> "Reazione"
        ActivationCost.LEGENDARY_ACTION -> "Azione leggendaria"
        ActivationCost.NONE -> "Nessun costo"
    }

private val ResolutionMethod.label: String
    get() = when (this) {
        ResolutionMethod.ATTACK_ROLL -> "Tiro per colpire"
        ResolutionMethod.SAVING_THROW -> "Tiro salvezza"
        ResolutionMethod.ABILITY_CHECK -> "Prova"
        ResolutionMethod.AUTOMATIC -> "Automatica"
        ResolutionMethod.MANUAL -> "Manuale"
    }
