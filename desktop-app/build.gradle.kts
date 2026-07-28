import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared-ui"))
    implementation(compose.desktop.currentOs)
}

// L'icona ha una sola copia, in `icons/arcano`. Qui ne entra nel classpath la
// versione PNG, che serve a runtime per Dock e barra delle applicazioni; i
// pacchetti nativi leggono invece direttamente .icns e .ico da quella cartella.
tasks.processResources {
    from(layout.projectDirectory.dir("icons/arcano")) {
        include("onfall-512.png")
        into("icons")
        rename { "onfall-icon.png" }
    }
}

compose.desktop {
    application {
        mainClass = "app.d6d.desktop.MainKt"
        // Skiko carica una libreria nativa; sui JDK recenti l'accesso va dichiarato
        // esplicitamente per evitare l'avviso e il futuro blocco di System.load.
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Dapple.awt.application.name=Onfall",
        )

        nativeDistributions {
            // Il documento chiede pacchetti desktop con runtime incluso, cosi'
            // l'utente non deve installare Java per conto proprio.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Nome tecnico del pacchetto: jpackage non accetta spazi.
            packageName = "Onfall"
            packageVersion = "1.0.0"
            description = "Strumento di combattimento compatibile con 5.5e / SRD"

            // Ogni sistema vuole il proprio formato: macOS .icns, Windows .ico,
            // Linux un PNG. Sono tre incisioni della stessa immagine.
            macOS { iconFile.set(project.file("icons/arcano/Onfall.icns")) }
            windows { iconFile.set(project.file("icons/arcano/Onfall.ico")) }
            linux { iconFile.set(project.file("icons/arcano/onfall-512.png")) }
        }
    }
}

// Le proprieta' -D passate a Gradle non vengono inoltrate automaticamente al
// processo JavaExec creato dal plugin Compose. Questa configurazione rende
// effettivo il comando documentato `-Donfall.dataDir=...`.
tasks.withType<JavaExec>().configureEach {
    providers.systemProperty("onfall.dataDir").orNull?.let { directory ->
        systemProperty("onfall.dataDir", directory)
    }
}
