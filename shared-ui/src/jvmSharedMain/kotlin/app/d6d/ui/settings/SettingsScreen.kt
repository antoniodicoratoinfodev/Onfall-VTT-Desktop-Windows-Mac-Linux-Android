package app.d6d.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.i18n.AppLanguage
import app.d6d.ui.AppIdentity
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.cursors.CursorArchive
import app.d6d.ui.cursors.CursorPreferences
import app.d6d.ui.i18n.SettingsStrings
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.strings
import app.d6d.ui.layout.TurnOrderDisplayMode
import app.d6d.ui.layout.UiLayoutState
import app.d6d.ui.state.EnemyCpuSpeed
import app.d6d.ui.state.label
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush
import java.nio.file.Path

/**
 * Impostazioni dell'applicazione.
 *
 * Raccoglie le scelte che valgono per tutta l'app e per tutte le partite: prima
 * erano sparse fra la fascia della CPU, la procedura di nuova partita e — nel caso
 * dei cursori — dentro il Compendio, che e' invece un archivio di contenuti di
 * gioco. Qui non entra nulla che riguardi il singolo scontro: la difficolta' resta
 * dov'e' chiesta, quando la partita si crea.
 */
@Composable
fun SettingsScreen(
    preferences: AppPreferencesState,
    layout: UiLayoutState,
    dataDirectory: Path,
    compact: Boolean,
    // Solo il desktop offre cursori personalizzati; null ne toglie la sezione.
    cursorPreferences: CursorPreferences? = null,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(SettingsSection.GENERAL) }
    // La shell puo' smettere di offrire i cursori (Android, o un avvio senza
    // immagini leggibili) mentre la sezione e' aperta: non si resta su una pagina
    // vuota. Stesso presidio che il Compendio aveva prima di cedere la sezione.
    LaunchedEffect(cursorPreferences != null) {
        if (cursorPreferences == null && section == SettingsSection.CURSORS) {
            section = SettingsSection.GENERAL
        }
    }

    Column(modifier.fillMaxSize()) {
        // Senza cursori resta una sezione sola: una barra con un unico pulsante
        // non offre alcuna scelta, e su Android sarebbe solo una striscia in piu'.
        if (cursorPreferences != null) {
            SettingsSectionBar(current = section, onSelect = { section = it })
        }
        when (section) {
            SettingsSection.GENERAL ->
                GeneralSettings(preferences, layout, dataDirectory, Modifier.weight(1f))

            SettingsSection.CURSORS -> cursorPreferences?.let {
                CursorArchive(it, compact, Modifier.weight(1f))
            }
        }
    }
}

private enum class SettingsSection {
    GENERAL,
    CURSORS,
}

private fun SettingsSection.label(strings: Strings): String = when (this) {
    SettingsSection.GENERAL -> strings.settings.sectionGeneral
    SettingsSection.CURSORS -> strings.settings.sectionCursors
}

/** Barra in cima alle Impostazioni, mostrata solo quando c'e' piu' di una sezione. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSectionBar(
    current: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
) {
    val strings = strings
    FlowRow(
        Modifier
            .fillMaxWidth()
            .background(Palette.Abyss)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsSection.entries.forEach { entry ->
            GameButton(
                label = entry.label(strings),
                accent = if (current == entry) Palette.Gold else Palette.TextMuted,
                selected = current == entry,
                dense = true,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun GeneralSettings(
    preferences: AppPreferencesState,
    layout: UiLayoutState,
    dataDirectory: Path,
    modifier: Modifier = Modifier,
) {
    val strings = strings
    val text = strings.settings
    var confirmLayoutReset by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Eyebrow(text.eyebrow)
            Text(
                text = text.title,
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = text.subtitle,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        GoldenRule()

        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.Night.copy(alpha = 0.72f))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // La lingua sta in cima, prima di ogni altra cosa: e' l'unica
            // impostazione che chi non capisce la lingua corrente deve poter
            // trovare senza leggere nulla, e in cima si trova guardando.
            SettingsGroup(
                title = text.groupLanguage,
                description = text.groupLanguageDescription,
            ) {
                SettingsChoice(label = text.languageChoice, hint = text.languageHint) {
                    AppLanguage.entries.forEach { language ->
                        val selected = preferences.language == language
                        GameButton(
                            // Il nome della lingua sempre nella lingua stessa:
                            // «Italiano» resta «Italiano» anche a interfaccia
                            // inglese, altrimenti chi la cerca non la riconosce.
                            label = language.endonym,
                            accent = if (selected) Palette.Gold else Palette.TextMuted,
                            selected = selected,
                            dense = true,
                            onClick = { preferences.chooseLanguage(language) },
                        )
                    }
                }
            }

            SettingsGroup(
                title = text.groupGame,
                description = text.groupGameDescription,
            ) {
                SettingsChoice(
                    label = text.cpuSpeed,
                    // Il ritmo si sente solo guardando: la riga sotto dice cosa
                    // aspettarsi prima di tornare al tavolo per scoprirlo.
                    hint = preferences.enemyCpuSpeed.pace(strings),
                ) {
                    EnemyCpuSpeed.entries.forEach { speed ->
                        val selected = preferences.enemyCpuSpeed == speed
                        GameButton(
                            label = speed.label(strings),
                            accent = if (selected) Palette.Gold else Palette.TextMuted,
                            selected = selected,
                            dense = true,
                            onClick = { preferences.enemyCpuSpeed = speed },
                        )
                    }
                }
            }

            SettingsGroup(
                title = text.groupTable,
                description = text.groupTableDescription,
            ) {
                SettingsChoice(
                    label = text.turnOrder,
                    hint = layout.turnOrderDisplayMode.hint(text),
                ) {
                    TurnOrderDisplayMode.entries.forEach { mode ->
                        val selected = layout.turnOrderDisplayMode == mode
                        GameButton(
                            label = mode.label(text),
                            accent = if (selected) Palette.Gold else Palette.TextMuted,
                            selected = selected,
                            dense = true,
                            onClick = { layout.applyTurnOrderDisplayMode(mode) },
                        )
                    }
                }
            }

            SettingsGroup(
                title = text.groupLook,
                description = text.groupLookDescription,
            ) {
                SettingsChoice(
                    label = text.backdrop,
                    hint = if (preferences.animatedBackdrop) {
                        text.backdropAnimatedHint
                    } else {
                        text.backdropStillHint
                    },
                ) {
                    listOf(true, false).forEach { animated ->
                        val selected = preferences.animatedBackdrop == animated
                        GameButton(
                            label = if (animated) text.backdropAnimated else text.backdropStill,
                            accent = if (selected) Palette.Gold else Palette.TextMuted,
                            selected = selected,
                            dense = true,
                            onClick = { preferences.animatedBackdrop = animated },
                        )
                    }
                }
            }

            SettingsGroup(
                title = text.groupData,
                description = text.groupDataDescription,
            ) {
                SettingsChoice(label = text.panelLayout, hint = text.panelLayoutHint) {
                    GameButton(
                        label = text.resetLayout,
                        accent = Palette.Enemy,
                        dense = true,
                        onClick = { confirmLayoutReset = true },
                    )
                }
                InfoRow(text.dataFolder, dataDirectory.toString())
                InfoRow(AppIdentity.displayName, AppIdentity.compatibilityLine(strings.language))
            }
        }
    }

    if (confirmLayoutReset) {
        AlertDialog(
            onDismissRequest = { confirmLayoutReset = false },
            containerColor = Palette.Surface,
            title = { Text(text.resetLayoutTitle, color = Palette.Text) },
            text = { Text(text.resetLayoutBody, color = Palette.TextMuted) },
            confirmButton = {
                GameButton(strings.common.reset, accent = Palette.Enemy, onClick = {
                    layout.resetToDefaults()
                    confirmLayoutReset = false
                })
            },
            dismissButton = {
                GameButton(
                    strings.common.cancel,
                    accent = Palette.TextMuted,
                    onClick = { confirmLayoutReset = false },
                )
            },
        )
    }
}

/** Riquadro di un gruppo di impostazioni: stesso pannello inciso del resto. */
@Composable
private fun SettingsGroup(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            // L'ordine conta: `widthIn` restringe il vincolo in arrivo e `fillMaxWidth`
            // riempie quello ristretto. Al contrario il riempimento vincerebbe e su una
            // finestra larga i pannelli si stirerebbero lasciando il testo tutto a sinistra.
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .panelBrush(shape)
            .border(1.dp, Palette.Line, shape)
            .ornateFrame(accent = Palette.Gold, alpha = 0.4f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(title)
            Text(
                text = description,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OrnateDivider(color = Palette.GoldDim)
        content()
    }
}

/** Una singola scelta: nome, comandi in fila e una riga che ne dice l'effetto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChoice(
    label: String,
    hint: String,
    options: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        // In fila finche' ci stanno, a capo quando la finestra si stringe: gli
        // stessi comandi valgono su desktop largo e su shell compatta.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options()
        }
        Text(
            text = hint,
            color = Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Riga di sola lettura: un'etichetta e il valore che l'app usa. */
@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Ritmo con cui la CPU mostra il proprio turno, detto a parole.
 *
 * Viveva accanto alla procedura di nuova partita, che non lo chiede piu': segue
 * il comando dove il comando e' andato.
 */
internal fun EnemyCpuSpeed.pace(strings: Strings): String = when (this) {
    EnemyCpuSpeed.SLOW -> strings.settings.cpuPaceSlow
    EnemyCpuSpeed.NORMAL -> strings.settings.cpuPaceNormal
    EnemyCpuSpeed.FAST -> strings.settings.cpuPaceFast
    EnemyCpuSpeed.INSTANT -> strings.settings.cpuPaceInstant
}

private fun TurnOrderDisplayMode.label(text: SettingsStrings): String = when (this) {
    TurnOrderDisplayMode.HIDDEN -> text.turnOrderHidden
    TurnOrderDisplayMode.ORDER_ONLY -> text.turnOrderOnly
    TurnOrderDisplayMode.WITH_INITIATIVE -> text.turnOrderWithInitiative
}

private fun TurnOrderDisplayMode.hint(text: SettingsStrings): String = when (this) {
    TurnOrderDisplayMode.HIDDEN -> text.turnOrderHiddenHint
    TurnOrderDisplayMode.ORDER_ONLY -> text.turnOrderOnlyHint
    TurnOrderDisplayMode.WITH_INITIATIVE -> text.turnOrderWithInitiativeHint
}
