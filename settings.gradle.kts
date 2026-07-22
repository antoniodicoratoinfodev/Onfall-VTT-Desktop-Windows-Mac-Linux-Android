pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "onfall"

// Motore condiviso: Java puro, senza dipendenze esterne, consumato
// sia dal desktop JVM sia da Android.
include(":engine:domain-model")
include(":engine:core-engine")
include(":engine:persistence-json")
// Schede di personaggio e stat block dei mostri: modello di redazione, piu'
// ricco della proiezione da combattimento usata dal motore.
include(":engine:sheet-model")

// Interfaccia condivisa Compose Multiplatform + le due shell.
include(":shared-ui")
include(":desktop-app")
include(":android-app")
