import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
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
            dependencies {
                api(project(":engine:domain-model"))
                api(project(":engine:core-engine"))
                api(project(":engine:persistence-json"))
                api(project(":engine:sheet-model"))
                implementation(project(":content:srd-5.2.1-it"))

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.components.resources)
            }
        }
        named("androidMain") { dependsOn(jvmSharedMain) }
        named("desktopMain") { dependsOn(jvmSharedMain) }

        // I test dello strato di presentazione girano sul bersaglio desktop:
        // il codice sotto esame e' condiviso, quindi basta eseguirli una volta.
        named("desktopTest") {
            dependencies {
                implementation(libs.junit.jupiter)
                // Il ritmo del turno CPU si prova a tempo virtuale: i test non
                // devono aspettare davvero le pause che rendono leggibile la partita.
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }
}

// I font del tema (Cinzel e Alegreya, licenza SIL OFL — vedi NOTICE-FONTS.md)
// sono risorse Compose incorporate: identita' tipografica identica su desktop e
// Android, senza dipendere dai font del sistema.
compose.resources {
    packageOfResClass = "app.d6d.ui.generated.resources"
    // La rilevazione automatica non scatta con il source set condiviso dichiarato
    // a mano: la classe `Res` va generata sempre.
    generateResClass = always
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
