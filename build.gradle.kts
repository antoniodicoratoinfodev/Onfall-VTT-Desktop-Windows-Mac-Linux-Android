// Tutti i plugin sono dichiarati qui con `apply false` per fissarne la versione
// una volta sola; i moduli li applicano senza ripetere il numero.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
}

// La versione del prodotto sta in `gradle.properties` e da li' scende a tutti i
// moduli come `project.version`: la leggono i pacchetti nativi, l'APK e la
// costante generata in shared-ui. Un rilascio puo' sovrascriverla dalla riga di
// comando con `-Ponfall.version=...` senza toccare il file.
//
// Il formato si controlla qui, subito, perche' un numero storto altrove si
// scoprirebbe solo a fine build: jpackage accetta da uno a tre interi separati da
// punti, e il codice di versione Android si calcola dagli stessi tre numeri.
val onfallVersion: String = providers.gradleProperty("onfall.version").orNull
    ?: error("Manca onfall.version: dichiararla in gradle.properties, per esempio 0.4.0")
val versionMatch = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d?)\.(0|[1-9]\d?)""")
    .matchEntire(onfallVersion)
    ?: error(
        "onfall.version deve essere nella forma «maggiore.minore.correzione», " +
            "senza zeri iniziali e con minore/correzione fra 0 e 99; " +
            "per esempio 0.4.0; trovato «$onfallVersion»",
    )

val (onfallMajor, onfallMinor, onfallPatch) = versionMatch.destructured
val onfallMajorNumber = onfallMajor.toIntOrNull()?.takeIf { it <= 255 }
    ?: error(
        "onfall.version «$onfallVersion» ha un numero maggiore non supportato: " +
            "deve essere compreso fra 0 e 255 per i pacchetti MSI",
    )
val onfallVersionCode =
    (onfallMajorNumber * 10_000L + onfallMinor.toLong() * 100L + onfallPatch.toLong())
    .takeIf { it in 1L..2_100_000_000L }
    ?: error(
        "onfall.version «$onfallVersion» non produce un versionCode Android valido " +
            "(deve essere compreso fra 1 e 2100000000)",
    )

// I moduli consumano valori gia' controllati: nessuno deve reinterpretare la
// versione per conto proprio e rischiare collisioni o overflow.
extra["onfall.versionCode"] = onfallVersionCode.toInt()

allprojects {
    version = onfallVersion
}
