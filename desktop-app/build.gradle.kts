import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

// Stessa versione dell'interfaccia e dell'APK: la decide il progetto radice.
val appVersion: String = version.toString()

val (appMajor, appMinor, appPatch) = appVersion.split('.').map(String::toInt)

// jpackage rifiuta --app-version con major zero, mentre Apple ammette 0.4.0 nel
// CFBundleShortVersionString mostrato all'utente. Al numero tecnico passato a
// jpackage e registrato come build number aggiungiamo quindi uno al major: resta
// positivo e cresce nello stesso ordine (0.99.99 -> 1.99.99, 1.0.0 -> 2.0.0).
// L'Info.plist qui sotto rimette la versione di prodotto esatta nel campo visibile.
val macBuildVersion = "${appMajor + 1}.$appMinor.$appPatch"
lateinit var macSigningEnabled: Provider<Boolean>
lateinit var macSigningIdentity: () -> String?
lateinit var macSigningKeychain: () -> String?
lateinit var macEntitlementsFile: RegularFileProperty
lateinit var macAppStoreEnabled: () -> Boolean

dependencies {
    implementation(project(":shared-ui"))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
            packageVersion = appVersion
            description = "Strumento di combattimento compatibile con 5.5e / SRD"

            // Ogni sistema vuole il proprio formato: macOS .icns, Windows .ico,
            // Linux un PNG. Sono tre incisioni della stessa immagine.
            macOS {
                packageVersion = macBuildVersion
                packageBuildVersion = macBuildVersion
                macSigningEnabled = signing.sign
                macSigningIdentity = { signing.identity.orNull }
                macSigningKeychain = { signing.keychain.orNull }
                macEntitlementsFile = entitlementsFile
                macAppStoreEnabled = { appStore }
                infoPlist {
                    // Compose accoda le chiavi extra alla sua risorsa Info.plist;
                    // macOS usa l'ultima occorrenza. Subito dopo jpackage il task
                    // normalizza il dizionario e ripristina la firma del bundle.
                    extraKeysRawXml = """
                        <key>CFBundleShortVersionString</key>
                        <string>$appVersion</string>
                        <key>OnfallProductVersion</key>
                        <string>$appVersion</string>
                    """.trimIndent()
                }
                iconFile.set(project.file("icons/arcano/Onfall.icns"))
            }
            windows { iconFile.set(project.file("icons/arcano/Onfall.ico")) }
            linux { iconFile.set(project.file("icons/arcano/onfall-512.png")) }
        }
    }
}

if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
    // L'estensione Info.plist qui sopra e' il solo aggancio offerto dal plugin e
    // genera temporaneamente due chiavi omonime. Dopo jpackage normalizziamo il
    // dizionario (resta l'ultima, cioe' 0.4.0) e ripristiniamo la stessa firma,
    // ad hoc oggi oppure Developer ID quando verra' configurata.
    tasks.configureEach {
        if (name == "createDistributable") doLast {
            val appBundle = layout.buildDirectory
                .dir("compose/binaries/main/app/Onfall.app")
                .get().asFile
            val infoPlist = appBundle.resolve("Contents/Info.plist")

            fun runTool(vararg arguments: String) {
                val result = ProcessBuilder(arguments.toList())
                    .inheritIO()
                    .start()
                    .waitFor()
                check(result == 0) {
                    "Comando fallito ($result): ${arguments.joinToString(" ")}"
                }
            }

            runTool("/usr/bin/plutil", "-convert", "binary1", infoPlist.absolutePath)
            val entitlements = macEntitlementsFile.orNull?.asFile
                ?: layout.buildDirectory.dir("compose/default-resources").get().asFile
                    .walkTopDown()
                    .firstOrNull { it.name == "default-entitlements.plist" }
                ?: error("Entitlements macOS predefiniti non trovati")
            val signArguments = mutableListOf(
                "/usr/bin/codesign",
                "--force",
                "--options", "runtime",
                "--entitlements", entitlements.absolutePath,
            )
            if (macSigningEnabled.get()) {
                val configuredIdentity = macSigningIdentity()
                    ?: error("Firma macOS abilitata senza identita'")
                val expectedPrefix = if (macAppStoreEnabled()) {
                    "3rd Party Mac Developer Application: "
                } else {
                    "Developer ID Application: "
                }
                val identity = if (
                    configuredIdentity.startsWith("Developer ID Application: ") ||
                    configuredIdentity.startsWith("3rd Party Mac Developer Application: ")
                ) configuredIdentity else expectedPrefix + configuredIdentity
                signArguments += listOf("--timestamp", "--sign", identity)
                macSigningKeychain()?.let { keychain ->
                    val keychainFile = listOf(project.file(keychain), rootProject.file(keychain))
                        .firstOrNull { it.exists() }
                        ?: error("Keychain macOS non trovato: $keychain")
                    signArguments += listOf("--keychain", keychainFile.absolutePath)
                }
            } else {
                signArguments += listOf("--sign", "-")
            }
            signArguments += appBundle.absolutePath
            runTool(*signArguments.toTypedArray())
        }

        if (name == "packageDmg") doLast {
            val dmgDirectory = layout.buildDirectory
                .dir("compose/binaries/main/dmg")
                .get().asFile
            val technicalName = dmgDirectory.resolve("Onfall-$macBuildVersion.dmg")
            val productName = dmgDirectory.resolve("Onfall-$appVersion.dmg")
            if (technicalName != productName && technicalName.exists()) {
                Files.move(
                    technicalName.toPath(),
                    productName.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                logger.lifecycle("Il DMG con la versione di prodotto e' in ${productName.canonicalPath}")
            }
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
