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
import app.d6d.sheet.metresLabel
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CatalogDamage
import app.d6d.sheet.CatalogHealing
import app.d6d.sheet.CatalogHealingBonusSource
import app.d6d.sheet.italianLabel
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
    val scope = rememberCoroutineScope()
    val catalog = viewModel.abilityCatalog.sortedBy { it.name.lowercase() }
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

    fun duplicate(ability: CatalogAbility) {
        val copy = ability.asCustomCopy()
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
                    GameButton("← Abilità", accent = Palette.TextMuted, onClick = { compactDetail = false })
                    Text(
                        text = draft.name.ifBlank { "Nuova abilità" },
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
                    modifier = Modifier.weight(1f),
                )
            } else {
                AbilityList(
                    abilities = abilities,
                    totalCount = catalog.size,
                    categories = categories,
                    categoryFilter = categoryFilter,
                    classFilter = classFilter,
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
                if (usage == 0 && !ability.immutable) {
                    GameButton("Elimina", accent = Palette.Enemy, onClick = {
                        scope.launch {
                            if (runDiskIo { viewModel.deleteAbility(ability.id) }) {
                                val remaining = viewModel.abilityCatalog.firstOrNull()
                                selectedId = remaining?.id
                                draft = remaining ?: newAbility()
                            }
                            pendingDelete = null
                        }
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
private fun AbilityDetails(
    draft: CatalogAbility,
    onChange: (CatalogAbility) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDuplicate: () -> Unit,
    passiveOverridden: Boolean,
    onPassiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (draft.immutable) {
        ReadOnlyAbilityDetails(
            ability = draft,
            onDuplicate = onDuplicate,
            overridden = passiveOverridden,
            onPassiveChange = onPassiveChange,
            modifier = modifier,
        )
    } else {
        AbilityEditor(
            draft = draft,
            onChange = { updated -> onChange(updated.enforceHealingConstraints()) },
            onSave = onSave,
            onDelete = onDelete,
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
            SheetBox("Contenuto SRD") {
                Text(
                    text = ability.name.ifBlank { "Senza nome" },
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
                AbilityMetadataChips(ability)
                Text(
                    text = "ID ${ability.id}",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (ability.prerequisite.isNotBlank()) {
                    Text(
                        text = "Prerequisito: ${ability.prerequisite}",
                        color = Palette.GoldBright,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SheetBox("Regole") {
                Text(
                    text = ability.rulesText.ifBlank { "Nessun testo di regole disponibile." },
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SheetBox("Funzionamento") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Chip(ability.activationCost.label, Palette.Gold)
                    Chip(ability.resolutionMethod.label, Palette.Party)
                    if (ability.dealsDamage) Chip(ability.damageText, Palette.Enemy)
                    ability.healing?.let { healing ->
                        Chip(
                            "Cura ${healing.amountText} · ${healing.target.label}",
                            Palette.Heal,
                        )
                    }
                    if (ability.isArea) Chip("Area ${metresLabel(ability.areaRadiusFeet)}", Palette.Crit)
                }
                if (ability.healing == null) {
                    PassiveSelector(
                        passive = ability.passive,
                        overridden = overridden,
                        onChange = onPassiveChange,
                    )
                } else {
                    Text(
                        "Cura attiva: viene risolta dall'app e non può essere resa passiva.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (ability.effects.isNotEmpty()) {
                    Text(
                        "APPLICATO DALL'APP",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ability.effects.forEach { effect ->
                            Chip(effect.readableText(), Palette.Heal)
                        }
                    }
                }
                if (ability.resourceId != null || ability.resourceCost > 0) {
                    Text(
                        text = buildString {
                            append("Risorsa: ").append(ability.resourceId ?: "specifica")
                            if (ability.resourceCost > 0) append(" · costo ").append(ability.resourceCost)
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
                SheetBox("Incantesimo") {
                    ability.spellLevel?.let { level ->
                        ReadOnlyProperty(
                            "Livello",
                            if (level == 0) "Trucchetto" else "$level",
                        )
                    }
                    if (ability.school.isNotBlank()) ReadOnlyProperty("Scuola", ability.school)
                    if (ability.castingTime.isNotBlank()) {
                        ReadOnlyProperty("Tempo di lancio", ability.castingTime)
                    }
                    if (ability.components.isNotBlank()) ReadOnlyProperty("Componenti", ability.components)
                    if (ability.duration.isNotBlank()) ReadOnlyProperty("Durata", ability.duration)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (ability.concentration) Chip("Concentrazione", Palette.Gold)
                        if (ability.ritual) Chip("Rituale", Palette.Party)
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
                    "Questa cura proviene dal pacchetto SRD, è protetta dalle modifiche " +
                        "e resta una capacità attiva automatizzata."
                } else {
                    "Questa voce proviene dal pacchetto SRD ed è protetta dalle modifiche. " +
                        "Puoi comunque decidere tu se giocarla come attiva o come passiva."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton(
                label = "Duplica come personalizzata",
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
    Text("NEL TURNO", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GameButton(
            label = "Attiva",
            accent = if (!passive) Palette.Gold else Palette.TextMuted,
            selected = !passive,
            dense = true,
            onClick = { onChange(false) },
        )
        GameButton(
            label = "Passiva",
            accent = if (passive) Palette.Crit else Palette.TextMuted,
            selected = passive,
            dense = true,
            onClick = { onChange(true) },
        )
        if (overridden) Chip("Scelta del tavolo", Palette.Crit)
    }
    Text(
        text = if (passive) {
            "Tratto permanente: vale sempre, resta fuori dai comandi e compare accanto a chi ha il turno."
        } else {
            "Capacità da spendere: compare fra i comandi di chi ha il turno."
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
                if (draft.healing == null) {
                    PassiveSelector(
                        passive = draft.passive,
                        overridden = false,
                        onChange = { onChange(draft.copy(passive = it)) },
                    )
                } else {
                    Text(
                        "Una cura è una capacità attiva e automatizzata.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

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
                if (draft.healing != null) {
                    Chip(ResolutionMethod.AUTOMATIC.label, Palette.Heal)
                } else {
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
                    SheetMetreField("Gittata", draft.rangeFeet, Modifier.width(150.dp)) {
                        onChange(draft.copy(rangeFeet = it.coerceAtLeast(0)))
                    }
                    if (!draft.isArea) {
                        SheetNumberField("Bersagli", draft.maxTargets, Modifier.width(120.dp)) {
                            onChange(draft.copy(maxTargets = it.coerceAtLeast(1)))
                        }
                    }
                }

                SheetCheck(
                    "Incantesimo o trucchetto",
                    draft.isSpellOrCantrip,
                ) { spell ->
                    onChange(draft.copy(spellOrCantrip = spell))
                }
                if (draft.resolutionMethod == ResolutionMethod.ATTACK_ROLL) {
                    Text(
                        "CARATTERISTICA DEL TIRO PER COLPIRE",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GameButton(
                            label = "Non specificata",
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
            }

            SheetBox("Cura") {
                SheetCheck("Recupera punti ferita", draft.healing != null) { enabled ->
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
                    Text("BERSAGLIO", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        HealingTarget.entries.forEach { target ->
                            GameButton(
                                label = target.label,
                                accent = if (healing.target == target) Palette.Heal else Palette.TextMuted,
                                selected = healing.target == target,
                                dense = true,
                                onClick = { onChange(draft.copy(healing = healing.copy(target = target))) },
                            )
                        }
                    }

                    Text("QUANTITÀ", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        GameButton(
                            label = "Dadi",
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
                            label = "Fissa",
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
                            SheetNumberField("Numero dadi", dice.count, Modifier.width(120.dp)) {
                                onChange(
                                    draft.copy(
                                        healing = healing.copy(
                                            dice = dice.copy(count = it.coerceAtLeast(1)),
                                        ),
                                    ),
                                )
                            }
                            SheetNumberField("Facce", dice.sides, Modifier.width(105.dp)) {
                                onChange(
                                    draft.copy(
                                        healing = healing.copy(
                                            dice = dice.copy(sides = it.coerceAtLeast(2)),
                                        ),
                                    ),
                                )
                            }
                            SheetNumberField("Modificatore", dice.modifier, Modifier.width(130.dp)) {
                                onChange(
                                    draft.copy(
                                        healing = healing.copy(dice = dice.copy(modifier = it)),
                                    ),
                                )
                            }
                        }
                        Text(
                            "BONUS DINAMICO",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            CatalogHealingBonusSource.entries.forEach { source ->
                                GameButton(
                                    label = source.label,
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
                                                        healing.bonusClassId ?: CharacterClassId.FIGHTER
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
                                "CLASSE DEL BONUS",
                                color = Palette.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                CharacterClassId.entries.forEach { classId ->
                                    GameButton(
                                        label = classId.italianLabel,
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
                        SheetNumberField("Punti ferita", amount, Modifier.width(150.dp)) {
                            onChange(
                                draft.copy(
                                    healing = CatalogHealing.fixed(healing.target, it.coerceAtLeast(1)),
                                ),
                            )
                        }
                    }
                    Text(
                        "La cura non infligge danno, non usa un'area e viene risolta automaticamente.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SheetBox("Danno") {
                if (draft.healing != null) {
                    Text(
                        "Non applicabile: questa capacità recupera punti ferita.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SheetCheck(
                        "Infligge danno",
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
                if (draft.healing != null) {
                    Text(
                        "Non applicabile: la cura sceglie un singolo bersaglio amico.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
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
                }
                if (draft.isArea && draft.healing == null) {
                    SheetMetreField("Raggio", draft.areaRadiusFeet, Modifier.width(150.dp)) {
                        onChange(draft.copy(areaRadiusFeet = it.coerceAtLeast(1)))
                    }
                }
                if (
                    draft.healing == null &&
                    (draft.resolutionMethod == ResolutionMethod.SAVING_THROW || draft.isArea)
                ) {
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
    attackAbility = Ability.STRENGTH,
)

private fun CatalogAbility.asCustomCopy(): CatalogAbility = copy(
    id = "abilita-${System.currentTimeMillis()}",
    name = "$name (copia)",
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

private val CatalogHealing.amountText: String
    get() = dice?.let { value ->
        buildString {
            append(value.count).append('d').append(value.sides)
            if (value.modifier > 0) append('+')
            if (value.modifier != 0) append(value.modifier)
            when (bonusSource) {
                CatalogHealingBonusSource.NONE -> Unit
                CatalogHealingBonusSource.SPELLCASTING_ABILITY -> append(" + mod. incantatore")
                CatalogHealingBonusSource.CLASS_LEVEL -> append(
                    " + livello ${bonusClassId?.italianLabel ?: "classe"}",
                )
            }
            slotScaling?.let { scaling ->
                append(" · +")
                    .append(scaling.additionalDicePerSlotLevel)
                    .append('d')
                    .append(value.sides)
                    .append("/livello oltre ")
                    .append(scaling.baseSlotLevel)
                    .append('°')
            }
        }
    } ?: fixedAmount.toString()

private val CatalogHealingBonusSource.label: String
    get() = when (this) {
        CatalogHealingBonusSource.NONE -> "Nessuno"
        CatalogHealingBonusSource.SPELLCASTING_ABILITY -> "Mod. incantatore"
        CatalogHealingBonusSource.CLASS_LEVEL -> "Livello di classe"
    }

private val HealingTarget.label: String
    get() = when (this) {
        HealingTarget.SELF -> "Solo sé"
        HealingTarget.ALLY -> "Solo alleato"
        HealingTarget.SELF_OR_ALLY -> "Sé o alleato"
    }

internal val ActivationCost.label: String
    get() = when (this) {
        ActivationCost.ACTION -> "Azione"
        ActivationCost.BONUS_ACTION -> "Azione bonus"
        ActivationCost.REACTION -> "Reazione"
        ActivationCost.LEGENDARY_ACTION -> "Azione leggendaria"
        ActivationCost.NONE -> "Nessun costo"
    }

internal val ResolutionMethod.label: String
    get() = when (this) {
        ResolutionMethod.ATTACK_ROLL -> "Tiro per colpire"
        ResolutionMethod.SAVING_THROW -> "Tiro salvezza"
        ResolutionMethod.ABILITY_CHECK -> "Prova"
        ResolutionMethod.AUTOMATIC -> "Automatica"
        ResolutionMethod.MANUAL -> "Manuale"
    }
