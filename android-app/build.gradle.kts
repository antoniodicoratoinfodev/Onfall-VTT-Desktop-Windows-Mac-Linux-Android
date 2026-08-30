plugins {
    // AGP 9 integra il compilatore Kotlin, quindi non serve il plugin
    // `kotlin-android` separato: basta quello del compilatore Compose.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Stessa versione del desktop e dell'interfaccia: la decide il progetto radice.
val appVersion: String = version.toString()

// Il progetto radice calcola e valida anche il codice numerico usato da Android:
// 0.4.0 diventa 400, senza collisioni, overflow o valori rifiutati da Google Play.
val appVersionCode: Int = rootProject.extra["onfall.versionCode"] as Int

android {
    namespace = "app.d6d.android"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.d6d.onfall"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Il motore usa java.time e java.nio: il desugaring li rende disponibili
        // anche sotto le API piu' recenti senza alzare minSdk.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(project.dependencies.project(":shared-ui"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
