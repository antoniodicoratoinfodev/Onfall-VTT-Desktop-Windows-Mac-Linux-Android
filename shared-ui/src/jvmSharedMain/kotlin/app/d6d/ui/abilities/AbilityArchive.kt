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
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.RuleElementKind
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
import app.d6d.ui.sheet.readableText
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
        if (viewModel.setAbilityPassive(draft.id, passive)) {
            draft = draft.copy(passive = passive)
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
                        if (!draft.immutable && viewModel.upsertAbility(draft)) {
                            selectedId = draft.id
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
                        if (!draft.immutable && viewModel.upsertAbility(draft)) {
                            selectedId = draft.id
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
                        if (viewModel.deleteAbility(ability.id)) {
                            val remaining = viewModel.abilityCatalog.firstOrNull()
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
                text = "Regole SRD e capacità personalizzate riusabili nelle schede dei personaggi.",
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
    totalCount: Int,
    categories: List<RuleElementKind>,
    categoryFilter: RuleElementKind?,
    classFilter: CharacterClassId?,
    onCategoryFilter: (RuleElementKind?) -> Unit,
    onClassFilter: (CharacterClassId?) -> Unit,
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
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    AbilityFilters(
                        categories = categories,
                        categoryFilter = categoryFilter,
                        classFilter = classFilter,
                        onCategoryFilter = onCategoryFilter,
                        onClassFilter = onClassFilter,
                    )
                }
                item {
                    Eyebrow(
                        "Abilità (${abilities.size}${if (abilities.size == totalCount) "" else " di $totalCount"})",
                        color = Palette.Party,
                    )
                }
                if (abilities.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Nessuna abilità corrisponde ai filtri.",
                                color = Palette.TextFaint,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                items(abilities, key = { it.id }) { ability ->
                    AbilityListRow(ability, selected = ability.id == selectedId) { onSelect(ability) }
                }
            }
            PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityFilters(
    categories: List<RuleElementKind>,
    categoryFilter: RuleElementKind?,
    classFilter: CharacterClassId?,
    onCategoryFilter: (RuleElementKind?) -> Unit,
    onClassFilter: (CharacterClassId?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("CATEGORIA", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GameButton(
                label = "Tutte",
                accent = if (categoryFilter == null) Palette.Gold else Palette.TextMuted,
                selected = categoryFilter == null,
                dense = true,
                onClick = { onCategoryFilter(null) },
            )
            categories.forEach { category ->
                GameButton(
                    label = category.italianLabel,
                    accent = if (categoryFilter == category) Palette.Gold else Palette.TextMuted,
                    selected = categoryFilter == category,
                    dense = true,
                    onClick = { onCategoryFilter(category) },
                )
            }
        }

        Text("CLASSE", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GameButton(
                label = "Tutte",
                accent = if (classFilter == null) Palette.Party else Palette.TextMuted,
                selected = classFilter == null,
                dense = true,
                onClick = { onClassFilter(null) },
            )
            CharacterClassId.entries.forEach { classId ->
                GameButton(
                    label = classId.italianLabel,
                    accent = if (classFilter == classId) Palette.Party else Palette.TextMuted,
                    selected = classFilter == classId,
                    dense = true,
                    onClick = { onClassFilter(classId) },
                )
            }
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
            // Il primo tag dice subito da che parte sta: un tratto permanente non
            // si gioca nel turno, e vederlo prima del costo evita di cercarlo fra
            // i comandi.
            if (ability.passive) Chip("Passiva", Palette.Crit)
            Chip(ability.activationCost.label, Palette.Gold)
            Chip(ability.resolutionMethod.label, Palette.Party)
            if (ability.dealsDamage) Chip(ability.damageText, Palette.Enemy)
            if (ability.isArea) Chip("Area ${ability.areaRadiusFeet} ft", Palette.Crit)
        }
        AbilityMetadataChips(ability)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityMetadataChips(ability: CatalogAbility) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Chip(ability.category.italianLabel, Palette.Crit)
        if (ability.classEligibility.isEmpty()) {
            Chip("Tutte le classi", Palette.Party)
        } else {
            ability.classEligibility
                .map { it.classId }
                .distinct()
                .sortedBy { it.ordinal }
                .forEach { classId ->
                    Chip(classId.italianLabel, Palette.Party)
                }
            ability.classEligibility
                .map { it.minimumLevel }
                .distinct()
                .sorted()
                .forEach { minimumLevel ->
                    Chip("Dal ${minimumLevel}º livello", Palette.Gold)
                }
        }
        ability.spellLevel?.let { level ->
            Chip(if (level == 0) "Trucchetto" else "Incantesimo di ${level}º livello", Palette.Heal)
        }
        if (ability.sourcePage > 0) {
            Chip("Pagina ${ability.sourcePage}", Palette.TextMuted)
        }
        if (ability.immutable) {
            Chip("SRD · sola lettura", Palette.GoldBright)
        }
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
            onChange = onChange,
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
                    if (ability.isArea) Chip("Area ${ability.areaRadiusFeet} ft", Palette.Crit)
                }
                PassiveSelector(
                    passive = ability.passive,
                    overridden = overridden,
                    onChange = onPassiveChange,
                )
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
                text = "Questa voce proviene dal pacchetto SRD ed è protetta dalle modifiche. " +
                    "Puoi comunque decidere tu se giocarla come attiva o come passiva.",
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
                PassiveSelector(
                    passive = draft.passive,
                    overridden = false,
                    onChange = { onChange(draft.copy(passive = it)) },
                )

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

private fun CatalogAbility.asCustomCopy(): CatalogAbility = copy(
    id = "abilita-${System.currentTimeMillis()}",
    name = "$name (copia)",
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
