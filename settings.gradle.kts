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
include(":engine:rules-model")
include(":engine:domain-model")
include(":engine:core-engine")
// Annotazioni della mappa: modello puro, separato dalle regole di combattimento.
include(":engine:board-model")
include(":engine:persistence-json")
include(":engine:rules-persistence")
// Schede di personaggio e stat block dei mostri: modello di redazione, piu'
// ricco della proiezione da combattimento usata dal motore.
include(":engine:sheet-model")
// Regole neutrali per creazione, classi e avanzamento. I dati SRD restano in un
// content pack separato, così motore e contenuto licenziato non si confondono.
include(":engine:character-rules")
include(":content:srd-5.2.1-it")

// Interfaccia condivisa Compose Multiplatform + le due shell.
include(":shared-ui")
include(":desktop-app")
include(":android-app")
