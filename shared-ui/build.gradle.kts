import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// La versione decisa dal progetto radice entra nel codice come costante generata,
// invece di essere ricopiata a mano in un file Kotlin: cosi' pacchetti nativi,
// APK e schermata Impostazioni non possono raccontare numeri diversi.
val appVersion: String = version.toString()

val generateBuildInfo = tasks.register("generateBuildInfo") {
    // Copia locale: il task non deve leggere il progetto mentre gira.
    val declaredVersion = appVersion
    val outputDirectory = layout.buildDirectory.dir("generated/onfall/kotlin")
    inputs.property("version", declaredVersion)
    outputs.dir(outputDirectory)
    doLast {
        val packageDirectory = outputDirectory.get().asFile.resolve("app/d6d/ui")
        packageDirectory.mkdirs()
        packageDirectory.resolve("BuildInfo.kt").writeText(
            """
            package app.d6d.ui

            /** Generato da Gradle a partire da `onfall.version`. Non modificare a mano. */
            internal object BuildInfo {
                const val VERSION: String = "$declaredVersion"
            }

            """.trimIndent(),
        )
    }
}

kotlin {
    android {
        namespace = "app.d6d.ui"
        compileSdk = 37
        minSdk = 26
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // Entrambi i bersagli sono JVM, quindi il codice realmente condiviso vive in
    // `jvmSharedMain` invece che in `commonMain`: cosi' puo' usare direttamente il
    // motore Java, cosa che `commonMain` (compilato a metadata) non permetterebbe.
    // Il source set e' dichiarato a mano, senza template gerarchico, per non
    // dipendere da accessor che cambiano fra le versioni di Kotlin e AGP.
    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(getByName("commonMain"))
            kotlin.srcDir(generateBuildInfo)
            dependencies {
                api(project(":engine:domain-model"))
                api(project(":engine:core-engine"))
                api(project(":engine:board-model"))
                api(project(":engine:persistence-json"))
                api(project(":engine:rules-persistence"))
                api(project(":engine:rules-authoring"))
                api(project(":engine:sheet-model"))
                implementation(project(":content:srd-5.2.1-it"))

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)
            }
        }
        named("androidMain") { dependsOn(jvmSharedMain) }
        named("desktopMain") { dependsOn(jvmSharedMain) }

        // I test dello strato di presentazione girano sul bersaglio desktop:
        // il codice sotto esame e' condiviso, quindi basta eseguirli una volta.
        named("desktopTest") {
            dependencies {
                implementation(libs.junit.jupiter)
                // La riflessione serve a un test solo, quello che percorre tutto
                // il vocabolario voce per voce: elencarle a mano vorrebbe dire
                // riscrivere proprio il difetto che deve trovare.
                implementation(kotlin("reflect"))
                // Il ritmo del turno CPU si prova a tempo virtuale: i test non
                // devono aspettare davvero le pause che rendono leggibile la partita.
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.jetbrains.compose.ui.test)
                implementation(compose.desktop.currentOs)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

// I font del tema (Cinzel, Alegreya e Alegreya Sans, licenza SIL OFL — vedi
// NOTICE-FONTS.md) sono
// risorse Java, non risorse Compose: identita' tipografica identica su desktop e
// Android, senza dipendere dai caratteri di sistema.
//
// Erano risorse Compose, e su Android non arrivavano affatto: il plugin KMP di AGP
// non espone gli asset alla variante (`variant.sources.assets` e' `null`), quindi
// il task che Compose prepara per copiarceli non ha dove scrivere e nessun aggancio
// dallo script puo' dargliene uno. Le risorse Java invece entrano nell'APK — lo
// fanno da sempre i JSON del pacchetto SRD. Il caricamento sta in `ThemeFonts.kt`.

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Chiude il giro: il numero che il build ha scritto nella costante generata
    // arriva anche al test, che verifica sia proprio quello a comparire nell'app.
    systemProperty("onfall.version", appVersion)
}
