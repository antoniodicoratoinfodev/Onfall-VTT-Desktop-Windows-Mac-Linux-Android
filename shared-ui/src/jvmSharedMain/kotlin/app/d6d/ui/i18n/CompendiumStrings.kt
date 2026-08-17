package app.d6d.ui.i18n

import app.d6d.i18n.AppLanguage
import app.d6d.sheet.i18n.unnamedActor
import app.d6d.sheet.i18n.unnamedCreature

/**
 * Il Compendio: l'archivio di personaggi, creature e capacita'.
 *
 * Contiene anche i nomi che l'applicazione da' a cio' che non ne ha ancora uno.
 * Sono segnaposto, non contenuto: una scheda appena creata si chiama «Senza nome»
 * finche' qualcuno non la battezza, e quel segnaposto va tradotto come tutto il
 * resto — resta pero' *scritto* nel documento appena viene salvato, quindi una
 * scheda salvata senza nome conserva la parola della lingua in cui e' nata.
 */
interface CompendiumStrings {
    val title: String
    val subtitle: String
    val backToCompendium: String
    val abilities: String
    val sheets: String
    val maps: String
    val characters: String
    val creatures: String
    val characterLabel: String
    val creatureLabel: String
    val classLabel: String
    val addCharacter: String
    val addCreature: String
    val characterSheet: String
    val statBlock: String
    val newElement: String
    val editElement: String
    val discardDraftTitle: String
    val discardDraftBody: String
    val discardAndContinue: String
    val unnamed: String
    val unnamedCreature: String
    val newAbility: String
    val newActor: String
    val editableField: String
    fun charactersCount(count: Int): String
    fun creaturesCount(count: Int): String
    fun classAndLevel(className: String, level: Int): String
    fun challengeRating(rating: String): String
    fun catalogError(detail: String): String
    fun invalidSheetForCatalog(detail: String): String
}

internal object CompendiumStringsIt : CompendiumStrings {
    override val title = "Compendio"
    override val subtitle = "Personaggi come schede complete, creature come stat block. " +
        "Il catalogo di combattimento discende da qui."
    override val backToCompendium = "← Compendio"
    override val abilities = "Abilità"
    override val sheets = "Schede"
    override val maps = "Mappe"
    override val characters = "Personaggi"
    override val creatures = "Creature"
    override val characterLabel = "Personaggio"
    override val creatureLabel = "Creatura"
    override val classLabel = "Classe"
    override val addCharacter = "+ Personaggio"
    override val addCreature = "+ Creatura"
    override val characterSheet = "Scheda personaggio"
    override val statBlock = "Stat block"
    override val newElement = "Nuovo elemento"
    override val editElement = "Modifica elemento"
    override val discardDraftTitle = "Scartare la bozza?"
    override val discardDraftBody =
        "La scheda contiene modifiche non salvate. Continuando verranno perse."
    override val discardAndContinue = "Scarta e continua"
    override val unnamed = unnamedActor(AppLanguage.ITALIAN)
    override val unnamedCreature = unnamedCreature(AppLanguage.ITALIAN)
    override val newAbility = "Nuova capacità"
    override val newActor = "Nuovo attore"
    override val editableField = "Campo modificabile"
    override fun charactersCount(count: Int) = "Personaggi ($count)"
    override fun creaturesCount(count: Int) = "Creature ($count)"
    override fun classAndLevel(className: String, level: Int) = "$className $level"
    override fun challengeRating(rating: String) = "GS $rating"
    override fun catalogError(detail: String) = "Errore nel catalogo: $detail"
    override fun invalidSheetForCatalog(detail: String) = "Scheda non valida per il catalogo: $detail"
}

internal object CompendiumStringsEn : CompendiumStrings {
    override val title = "Compendium"
    override val subtitle = "Characters as full sheets, creatures as stat blocks. " +
        "The combat catalog is derived from here."
    override val backToCompendium = "← Compendium"
    override val abilities = "Abilities"
    override val sheets = "Sheets"
    override val maps = "Maps"
    override val characters = "Characters"
    override val creatures = "Creatures"
    override val characterLabel = "Character"
    override val creatureLabel = "Creature"
    override val classLabel = "Class"
    override val addCharacter = "+ Character"
    override val addCreature = "+ Creature"
    override val characterSheet = "Character sheet"
    override val statBlock = "Stat block"
    override val newElement = "New entry"
    override val editElement = "Edit entry"
    override val discardDraftTitle = "Discard the draft?"
    override val discardDraftBody = "The sheet has unsaved changes. Carrying on will lose them."
    override val discardAndContinue = "Discard and carry on"
    override val unnamed = unnamedActor(AppLanguage.ENGLISH)
    override val unnamedCreature = unnamedCreature(AppLanguage.ENGLISH)
    override val newAbility = "New ability"
    override val newActor = "New actor"
    override val editableField = "Editable field"
    override fun charactersCount(count: Int) = "Characters ($count)"
    override fun creaturesCount(count: Int) = "Creatures ($count)"
    override fun classAndLevel(className: String, level: Int) = "$className $level"
    override fun challengeRating(rating: String) = "CR $rating"
    override fun catalogError(detail: String) = "Catalog error: $detail"
    override fun invalidSheetForCatalog(detail: String) = "Sheet not valid for the catalog: $detail"
}
