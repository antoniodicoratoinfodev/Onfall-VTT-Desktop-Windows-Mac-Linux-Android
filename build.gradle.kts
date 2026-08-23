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
val onfallVersion: String = providers.gradleProperty("onfall.version").get()
require(Regex("""\d+\.\d+\.\d+""").matches(onfallVersion)) {
    "onfall.version deve essere nella forma «maggiore.minore.correzione», " +
        "per esempio 0.4.0; trovato «$onfallVersion»"
}

allprojects {
    version = onfallVersion
}
