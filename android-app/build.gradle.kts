plugins {
    // AGP 9 integra il compilatore Kotlin, quindi non serve il plugin
    // `kotlin-android` separato: basta quello del compilatore Compose.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Stessa versione del desktop e dell'interfaccia: la decide il progetto radice.
val appVersion: String = version.toString()

// Il Play Store confronta interi, non stringhe: 0.4.0 diventa 400, e ogni
// versione successiva resta piu' grande della precedente senza doverci pensare.
// I tre numeri esistono di sicuro: il progetto radice rifiuta ogni altra forma.
val appVersionCode: Int = appVersion.split('.')
    .let { (major, minor, patch) -> major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt() }

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
    implementation(project(":shared-ui"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
}
