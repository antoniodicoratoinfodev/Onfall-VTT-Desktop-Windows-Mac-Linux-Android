package app.d6d.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * I caratteri del tema, letti dalle risorse Java.
 *
 * Prima erano risorse Compose, e su Android non c'erano affatto: il plugin KMP di
 * AGP non espone gli asset alla variante — `variant.sources.assets` e' `null` — e
 * il task che Compose prepara per copiarceli non ha dove scrivere. Nessun aggancio
 * poteva funzionare, e il difetto non si vedeva perche' Android ripiega in silenzio
 * sui caratteri di sistema: l'applicazione restava leggibile e perdeva la propria
 * voce, senza dirlo.
 *
 * Le risorse Java invece arrivano su entrambi i bersagli — lo dimostrano da sempre
 * i JSON del pacchetto SRD, che stanno dentro l'APK. Il prezzo e' che il *modo* di
 * costruire un font dai byte non e' comune: Skia ne accetta un array, Android
 * pretende un file. Sono due righe di piattaforma, dichiarate qui una volta.
 */
internal expect fun themeFont(identity: String, data: ByteArray, weight: FontWeight): Font

/**
 * Una famiglia del tema, costruita una volta sola.
 *
 * Fuori dalla composizione di proposito: leggere i byte e — su Android — scriverli
 * in un file non sono cose da rifare a ogni ricomposizione. Prima ci si affidava
 * alla cache interna di Compose Resources; qui la cache e' semplicemente il fatto
 * che questa funzione la si chiama una volta.
 */
internal fun themeFontFamily(vararg faces: Pair<String, FontWeight>): FontFamily =
    FontFamily(faces.map { (fileName, weight) -> themeFont(fileName, fontBytes(fileName), weight) })

private fun fontBytes(fileName: String): ByteArray =
    checkNotNull(object {}.javaClass.getResourceAsStream("/font/$fileName")) {
        "Carattere del tema non impacchettato: font/$fileName"
    }.use { it.readBytes() }
