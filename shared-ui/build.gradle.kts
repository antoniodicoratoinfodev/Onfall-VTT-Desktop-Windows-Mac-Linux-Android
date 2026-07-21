import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        namespace = "app.d6d.ui"
        compileSdk = 36
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

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
            }
        }
        named("androidMain") { dependsOn(jvmSharedMain) }
        named("desktopMain") { dependsOn(jvmSharedMain) }

        // I test dello strato di presentazione girano sul bersaglio desktop:
        // il codice sotto esame e' condiviso, quindi basta eseguirli una volta.
        named("desktopTest") {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
