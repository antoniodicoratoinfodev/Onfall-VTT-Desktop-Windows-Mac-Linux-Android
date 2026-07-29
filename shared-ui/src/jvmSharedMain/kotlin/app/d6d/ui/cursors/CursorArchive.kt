package app.d6d.ui.cursors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette

/**
 * Sezione desktop del Compendio dedicata al guanto-cursore.
 *
 * Ogni scheda mostra entrambe le pose della coppia. Le bitmap sono quelle
 * decodificate direttamente dai file del cursore, quindi l'anteprima non può
 * divergere dall'immagine applicata alla finestra.
 */
@Composable
fun CursorArchive(
    preferences: CursorPreferences,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val previews = CursorPair.entries.mapNotNull { pair ->
        preferences.previews.firstOrNull { it.pair == pair }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Night.copy(alpha = 0.72f)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Palette.Surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Eyebrow("PERSONALIZZAZIONE DESKTOP", Palette.Gold)
            Text(
                text = "Cursori",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Scegli fra tutte le finiture disponibili. Ogni coppia include " +
                    "la posa normale e quella che afferra la mappa.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            CursorSizeSelector(preferences)
        }
        GoldenRule()

        val scroll = rememberScrollState()
        if (compact) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                previews.forEach { preview ->
                    CursorPairCard(
                        preview = preview,
                        selected = preferences.selected == preview.pair,
                        compact = true,
                        onSelect = { preferences.onSelect(preview.pair) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                previews.chunked(2).forEach { rowPreviews ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowPreviews.forEach { preview ->
                            CursorPairCard(
                                preview = preview,
                                selected = preferences.selected == preview.pair,
                                compact = false,
                                onSelect = { preferences.onSelect(preview.pair) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowPreviews.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CursorSizeSelector(preferences: CursorPreferences) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "DIMENSIONE",
            color = Palette.TextMuted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CursorSize.entries.forEach { size ->
                GameButton(
                    label = size.label,
                    subtitle = size.description,
                    accent = Palette.Gold,
                    selected = preferences.size == size,
                    onClick = { preferences.onSizeChange(size) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CursorPairCard(
    preview: CursorPairPreview,
    selected: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (preview.pair) {
        CursorPair.COLD -> Palette.Party
        CursorPair.WARM -> Palette.RangePreview
        CursorPair.CLASSIC -> Palette.Bloodied
        CursorPair.RUNIC -> Palette.Crit
        CursorPair.STEEL -> Palette.GoldBright
    }
    val title = when (preview.pair) {
        CursorPair.COLD -> "Coppia A · Fredda"
        CursorPair.WARM -> "Coppia B · Calda"
        CursorPair.CLASSIC -> "Coppia C · Cuoio"
        CursorPair.RUNIC -> "Coppia D · Runica"
        CursorPair.STEEL -> "Coppia E · Acciaio"
    }
    val subtitle = when (preview.pair) {
        CursorPair.COLD -> "Acciaio blu e riflessi lunari"
        CursorPair.WARM -> "Bronzo, rame e riflessi d'ambra"
        CursorPair.CLASSIC -> "Cuoio scuro e piastre brunite"
        CursorPair.RUNIC -> "Sigillo azzurro su metallo brunito"
        CursorPair.STEEL -> "Acciaio freddo e bagliori di zaffiro"
    }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier
            .background(
                if (selected) accent.copy(alpha = 0.10f) else Palette.Surface.copy(alpha = 0.92f),
                shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Palette.Line,
                shape = shape,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = if (selected) accent else Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CursorPosePreview(
                image = preview.pointer,
                label = "Puntatore",
                description = "$title, posa normale",
                compact = compact,
                accent = accent,
                modifier = Modifier.weight(1f),
            )
            CursorPosePreview(
                image = preview.grab,
                label = "Presa sulla mappa",
                description = "$title, posa di trascinamento",
                compact = compact,
                accent = accent,
                modifier = Modifier.weight(1f),
            )
        }

        GameButton(
            label = if (selected) "Coppia selezionata" else "Usa questa coppia",
            subtitle = if (selected) "In uso nella finestra" else "Applica subito e ricorda la scelta",
            accent = accent,
            selected = selected,
            onClick = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CursorPosePreview(
    image: ImageBitmap,
    label: String,
    description: String,
    compact: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val gridLine = Palette.Line.copy(alpha = 0.72f)
        Box(
            Modifier.fillMaxWidth()
                .height(if (compact) 132.dp else 176.dp)
                .background(Palette.Abyss, RoundedCornerShape(7.dp))
                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(7.dp))
                .drawBehind {
                    val step = 24.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), 1f)
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(gridLine, Offset(0f, y), Offset(size.width, y), 1f)
                        y += step
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = image,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 102.dp else 136.dp),
            )
        }
        Text(
            text = label.uppercase(),
            color = Palette.TextMuted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
