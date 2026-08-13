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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.sheet.metresLabel
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.italianLabel
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.theme.Palette

@Composable
internal fun AbilityArchiveHeader(compact: Boolean, onCreate: () -> Unit) {
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
internal fun AbilityList(
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
            if (ability.isArea) Chip("Area ${metresLabel(ability.areaRadiusFeet)}", Palette.Crit)
        }
        AbilityMetadataChips(ability)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AbilityMetadataChips(ability: CatalogAbility) {
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
