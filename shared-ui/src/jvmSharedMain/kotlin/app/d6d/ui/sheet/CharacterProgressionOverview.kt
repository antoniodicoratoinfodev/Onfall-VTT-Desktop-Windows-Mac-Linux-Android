package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.sheet.CharacterSheet
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgressionOverview(
    viewModel: SheetViewModel,
    sheet: CharacterSheet,
    compact: Boolean,
    onOpenProgression: () -> Unit,
) {
    if (!sheet.progression.configured) {
        SheetBox("Creazione e livelli SRD 5.2.1", Modifier.fillMaxWidth()) {
            Text(
                "La modalità guidata propone classe, competenze, privilegi, talenti, trucchetti, " +
                    "incantesimi e risorse nelle quantità previste dallo SRD. Le schede manuali " +
                    "esistenti restano invariate finché non la attivi.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton(
                "Avvia creazione guidata",
                accent = Palette.Gold,
                onClick = onOpenProgression,
            )
        }
        return
    }

    SheetBox("Progressione SRD 5.2.1", Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            sheet.progression.classLevels.forEach {
                Chip("${it.classId.italianLabel} ${it.level}", Palette.Party)
            }
            Chip("Bonus competenza ${signed(sheet.proficiencyBonus)}", Palette.Gold)
            Chip("${sheet.experiencePoints} PE", Palette.Temporary)
        }

        if (sheet.canLevelUp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
                    .border(1.dp, Palette.Gold.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Passaggio disponibile: i PE consentono il livello ${sheet.effectiveLevel + 1}.",
                    color = Palette.GoldBright,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                GameButton(
                    "Sali al livello ${sheet.effectiveLevel + 1}",
                    accent = Palette.Gold,
                    onClick = onOpenProgression,
                )
            }
        } else {
            sheet.nextLevelExperienceThreshold?.let { threshold ->
                Text(
                    "Prossimo livello a $threshold PE · ne mancano ${sheet.experienceToNextLevel}.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val visibleResourcePools = sheet.progression.resourcePools.filter { it.maximum > 0 }
        if (visibleResourcePools.isNotEmpty()) {
            Text("RISORSE DI CLASSE", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                visibleResourcePools.forEach { pool ->
                    Column(
                        Modifier
                            .width(if (compact) 150.dp else 180.dp)
                            .background(Palette.Night, RoundedCornerShape(6.dp))
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            buildString {
                                append(pool.name)
                                if (pool.dieSides > 0) append(" · d${pool.dieSides}")
                            },
                            color = Palette.Text,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${pool.remaining}/${pool.maximum} · ${pool.recovery.italianLabel}",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        PipRow(pool.maximum, pool.spent, color = Palette.Gold) {
                            viewModel.setCharacterResourceSpent(pool.resourceId, it)
                        }
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                GameButton(
                    "Riposo breve",
                    accent = Palette.Temporary,
                    dense = true,
                    onClick = { viewModel.recoverCharacterResources(RecoveryPeriod.SHORT_REST) },
                )
                GameButton(
                    "Riposo lungo",
                    accent = Palette.Heal,
                    dense = true,
                    onClick = { viewModel.recoverCharacterResources(RecoveryPeriod.LONG_REST) },
                )
            }
        }
    }
}

