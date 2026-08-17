package app.d6d.ui.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.initials
import app.d6d.ui.theme.Palette
import app.d6d.ui.i18n.strings

/**
 * Ritratto di un attore, con caricamento dell'immagine.
 *
 * Senza immagine resta il medaglione con le iniziali: ogni creatura ha comunque
 * una rappresentazione, e caricare un file e' un'aggiunta, non un requisito.
 *
 * Le immagini restano nell'archivio locale. Il documento le tratta come contenuto
 * privato dell'utente, escluso di predefinito dagli export condivisibili: copiare
 * l'illustrazione di un manuale non la rende distribuibile.
 */
@Composable
fun PortraitPicker(
    repository: PortraitRepository,
    definitionId: String,
    name: String,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 92.dp,
) {
    val words = strings.maps
    // Leggere `revision` lega questa composizione agli import: un nuovo file
    // caricato ridisegna subito il ritratto.
    @Suppress("UNUSED_EXPRESSION")
    repository.revision

    val portrait = repository.rememberPortrait(definitionId)

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(Palette.SurfaceHigh)
                .border(2.dp, Palette.Gold.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    bitmap = portrait,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initials(name),
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    fontSize = (diameter.value * 0.30f).sp,
                )
            }
        }

        GameButton(
            label = if (portrait == null) words.uploadImage else words.changeImage,
            accent = Palette.Party,
            onClick = { repository.assignPortraitAsync(definitionId) },
        )
        if (portrait != null) {
            GameButton(
                label = strings.common.remove,
                accent = Palette.TextFaint,
                onClick = { repository.clearPortrait(definitionId) },
            )
        }

        repository.message?.let {
            Text(
                text = it,
                color = Palette.Gold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
