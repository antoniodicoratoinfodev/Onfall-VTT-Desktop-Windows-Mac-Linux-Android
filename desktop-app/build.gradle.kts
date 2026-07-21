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

compose.desktop {
    application {
        mainClass = "app.d6d.desktop.MainKt"

        nativeDistributions {
            // Il documento chiede pacchetti desktop con runtime incluso, cosi'
            // l'utente non deve installare Java per conto proprio.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Nome tecnico del pacchetto: jpackage non accetta spazi. Va allineato
            // ad AppIdentity.displayName quando il nome commerciale sara' scelto.
            packageName = "InserireNome"
            packageVersion = "1.0.0"
            description = "Strumento di combattimento compatibile con 5.5e / SRD"
        }
    }
}
