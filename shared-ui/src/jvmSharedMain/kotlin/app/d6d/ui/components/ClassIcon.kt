package app.d6d.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.d6d.i18n.label
import app.d6d.rules.character.CharacterClassId
import app.d6d.ui.i18n.currentLanguage

/**
 * I dodici simboli di classe inclusi nell'app.
 *
 * I PNG di anteprima arrivano dal generatore con una scacchiera chiara impressa
 * nello sfondo. Il decoder di piattaforma la elimina mentre riduce l'immagine:
 * nel pacchetto restano gli originali, mentre in memoria vive soltanto il piccolo
 * bitmap trasparente effettivamente disegnato dalla UI.
 */
internal object ClassIconAssets {
    private const val DIRECTORY = "class-icons"

    fun bytesOf(classId: CharacterClassId): ByteArray =
        checkNotNull(ClassIconAssets::class.java.getResourceAsStream("/$DIRECTORY/${classId.contentId}.png")) {
            "Simbolo di classe non impacchettato: ${classId.contentId}"
        }.use { it.readBytes() }
}

private object ClassIconCache {
    private val images = mutableMapOf<CharacterClassId, ImageBitmap?>()

    fun image(classId: CharacterClassId): ImageBitmap? = synchronized(images) {
        if (images.containsKey(classId)) return@synchronized images[classId]
        val decoded = runCatching {
            decodeClassIcon(ClassIconAssets.bytesOf(classId), maximumSide = 192)
        }.getOrNull()
        images[classId] = decoded
        decoded
    }
}

/** Decodifica, riduce e rende trasparente la scacchiera chiara dell'anteprima. */
internal expect fun decodeClassIcon(bytes: ByteArray, maximumSide: Int): ImageBitmap?

/**
 * Rimuove le due tinte quasi bianche della scacchiera impressa nei PNG sorgente.
 *
 * Alcune risorse, in particolare quella del mago, usano per i quadrati grigi
 * valori attorno a 239 anziche' 242: la vecchia soglia cancellava soltanto la
 * meta' bianca e lasciava quindi un riquadro a scacchi visibile nella scheda.
 * La bassa escursione fra i canali evita di confondere i bagliori colorati del
 * simbolo con lo sfondo neutro.
 */
internal fun withoutClassIconPreviewGrid(argb: Int): Int {
    val red = argb ushr 16 and 0xff
    val green = argb ushr 8 and 0xff
    val blue = argb and 0xff
    val lightest = maxOf(red, green, blue)
    val darkest = minOf(red, green, blue)
    return if (darkest >= 232 && lightest - darkest <= 8) argb and 0x00ffffff else argb
}

/** Simbolo della classe, con nome accessibile nella lingua corrente. */
@Composable
fun ClassIcon(
    classId: CharacterClassId,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val image = remember(classId) { ClassIconCache.image(classId) } ?: return
    Image(
        bitmap = image,
        contentDescription = classId.label(currentLanguage),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
