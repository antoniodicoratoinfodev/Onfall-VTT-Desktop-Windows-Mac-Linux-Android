package app.d6d.ui.images

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.initials
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette
import app.d6d.ui.i18n.strings
import app.d6d.sheet.PortraitFraming

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
@OptIn(ExperimentalLayoutApi::class)
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
    val savedFraming = repository.portraitFraming(definitionId)
    // Non usare il nome del file come chiave: cambia proprio al termine del picker
    // e azzererebbe l'apertura automatica dell'editor richiesta dal caricamento.
    var editing by remember(definitionId) { mutableStateOf(false) }
    var framing by remember(definitionId) { mutableStateOf(savedFraming) }

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
                FramedPortraitImage(
                    image = portrait,
                    framing = if (editing) framing else savedFraming,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initials(name),
                    color = Palette.Text,
                    fontSize = (diameter.value * 0.30f).sp,
                    style = OnfallTheme.typography.tokenInitials,
                )
            }
        }

        GameButton(
            label = if (portrait == null) words.uploadImage else words.changeImage,
            accent = Palette.Party,
            onClick = {
                repository.assignPortraitAsync(definitionId) { imported ->
                    if (imported) {
                        framing = PortraitFraming.DEFAULT
                        editing = true
                    }
                }
            },
        )
        if (portrait != null) {
            GameButton(
                label = words.frameImage,
                accent = Palette.Gold,
                selected = editing,
                onClick = {
                    framing = savedFraming
                    editing = !editing
                },
            )
            GameButton(
                label = strings.common.remove,
                accent = Palette.TextFaint,
                onClick = {
                    editing = false
                    repository.clearPortrait(definitionId)
                },
            )
        }

        if (portrait != null && editing) {
            PortraitFramingEditor(
                image = portrait,
                framing = framing,
                onFramingChange = { framing = it },
                previewSize = 126.dp,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GameButton(
                    label = strings.common.save,
                    accent = Palette.Heal,
                    dense = true,
                    onClick = {
                        repository.setPortraitFraming(definitionId, framing)
                        editing = false
                    },
                )
                GameButton(
                    label = strings.common.cancel,
                    accent = Palette.TextFaint,
                    dense = true,
                    onClick = {
                        framing = savedFraming
                        editing = false
                    },
                )
            }
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
