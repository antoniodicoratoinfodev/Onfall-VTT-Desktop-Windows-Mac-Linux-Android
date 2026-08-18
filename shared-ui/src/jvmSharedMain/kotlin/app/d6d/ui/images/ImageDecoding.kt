package app.d6d.ui.images

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Path

/**
 * Decodifica un'immagine dal disco senza superare un budget di pixel.
 *
 * Le due cose che fa sono entrambe necessarie e nessuna delle due e' un'ottimizzazione.
 *
 * Legge **dal file**, non da un array di byte gia' in memoria: il limite di
 * importazione e' un gigabyte, e leggere un gigabyte per poi decodificarlo vuol
 * dire tenerne in memoria due copie, la compressa e la decompressa.
 *
 * E **sottocampiona**: una battlemap da 12.000 × 12.000 occupa 576 MB una volta
 * decodificata, quattro byte per pixel, indipendentemente da quanto pesava il file.
 * Non c'e' schermo che ne mostri tanti, quindi si decodifica saltando pixel — un
 * lavoro che sia ImageIO sia BitmapFactory sanno fare durante la lettura, senza mai
 * materializzare l'immagine intera.
 *
 * C'era un tetto in pixel *all'importazione* che difendeva la stessa memoria, ma dal
 * lato sbagliato: rifiutava le mappe invece di leggerle con giudizio.
 */
internal expect fun decodeSampled(path: Path, maxPixels: Long): ImageBitmap?

/**
 * Di quanto sottocampionare perche' il risultato stia nel budget.
 *
 * Potenze di due: e' l'unica cosa che `BitmapFactory` accetta davvero — arrotonda da
 * se' qualunque altro valore — e tenere la stessa regola sulle due piattaforme fa si'
 * che la stessa mappa venga letta alla stessa risoluzione su entrambe.
 */
internal fun sampleStep(width: Int, height: Int, maxPixels: Long): Int {
    if (width <= 0 || height <= 0 || maxPixels <= 0) return 1
    var step = 1
    while ((width.toLong() / step) * (height.toLong() / step) > maxPixels) step *= 2
    return step
}

/**
 * Quanti pixel al massimo si tengono decodificati per immagine.
 *
 * Sedici megapixel sono quattro volte uno schermo 4K: bastano a ingrandire una mappa
 * senza vederla sgranare, e sono sessantaquattro megabyte, cioe' l'intero budget
 * della cache per una sola immagine. Oltre non si guadagna nulla che si veda.
 */
internal const val MAX_DECODED_PIXELS: Long = 16_000_000L
