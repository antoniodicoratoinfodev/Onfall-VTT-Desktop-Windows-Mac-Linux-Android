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
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.sheet.i18n.damageText
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.CatalogAbility
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.theme.Palette

@Composable
internal fun AbilityArchiveHeader(compact: Boolean, onCreate: () -> Unit) {
    val words = strings.abilities
    val title = @Composable {
        Column {
            Text(
                text = words.title,
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = words.subtitle,
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
            GameButton(words.newAbilityPlus, accent = Palette.Party, onClick = onCreate)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { title() }
            GameButton(words.newAbilityPlus, accent = Palette.Party, onClick = onCreate)
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
    val words = strings.abilities
    val language = currentLanguage
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
                        words.abilitiesCount(abilities.size, totalCount),
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
                                words.noAbilityMatchesFilters,
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
    val words = strings.abilities
    val language = currentLanguage
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(language.pick("CATEGORIA", "CATEGORY"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GameButton(
                label = words.allFeminine,
                accent = if (categoryFilter == null) Palette.Gold else Palette.TextMuted,
                selected = categoryFilter == null,
                dense = true,
                onClick = { onCategoryFilter(null) },
            )
            categories.forEach { category ->
                GameButton(
                    label = category.label(language),
                    accent = if (categoryFilter == category) Palette.Gold else Palette.TextMuted,
                    selected = categoryFilter == category,
                    dense = true,
                    onClick = { onCategoryFilter(category) },
                )
            }
        }

        Text(strings.abilities.classCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GameButton(
                label = words.allFeminine,
                accent = if (classFilter == null) Palette.Party else Palette.TextMuted,
                selected = classFilter == null,
                dense = true,
                onClick = { onClassFilter(null) },
            )
            CharacterClassId.entries.forEach { classId ->
                GameButton(
                    label = classId.label(language),
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
    val strings = strings
    val words = strings.abilities
    val language = currentLanguage
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
            text = ability.name.ifBlank { strings.compendium.unnamed },
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
            if (ability.passive) Chip(language.pick("Passiva", "Passive"), Palette.Crit)
            Chip(ability.activationCost.labelIn(strings), Palette.Gold)
            Chip(ability.resolutionMethod.labelIn(strings), Palette.Party)
            if (ability.dealsDamage) Chip(ability.damageText(language), Palette.Enemy)
            if (ability.isArea) Chip(words.areaOf(distanceLabel(ability.areaRadiusFeet, language)), Palette.Crit)
        }
        AbilityMetadataChips(ability)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AbilityMetadataChips(ability: CatalogAbility) {
    val words = strings.abilities
    val language = currentLanguage
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Chip(ability.category.label(language), Palette.Crit)
        if (ability.classEligibility.isEmpty()) {
            Chip(words.allClasses, Palette.Party)
        } else {
            ability.classEligibility
                .map { it.classId }
                .distinct()
                .sortedBy { it.ordinal }
                .forEach { classId ->
                    Chip(classId.label(language), Palette.Party)
                }
            ability.classEligibility
                .map { it.minimumLevel }
                .distinct()
                .sorted()
                .forEach { minimumLevel ->
                    Chip(words.fromLevel(minimumLevel), Palette.Gold)
                }
        }
        ability.spellLevel?.let { level ->
            Chip(if (level == 0) strings.abilities.cantrip else words.spellOfLevel(level), Palette.Heal)
        }
        if (ability.sourcePage > 0) {
            Chip(words.page(ability.sourcePage), Palette.TextMuted)
        }
        if (ability.immutable) {
            Chip(words.srdReadOnly, Palette.GoldBright)
        }
    }
}
