package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.ContentPackManifest

object Srd521ItManifest {
    /**
     * L'identificativo del pacchetto **non** cambia con la lingua.
     *
     * Finisce in `contentPackId` dentro ogni scheda salvata: due identificativi
     * vorrebbero dire che una scheda creata in italiano risulta appartenere a
     * un pacchetto che non c'e' piu' appena si passa all'inglese. E' lo stesso
     * libro in due edizioni, non due contenuti diversi.
     */
    private const val ID = "srd521-it"

    private const val LICENSE = "Creative Commons Attribution 4.0 International (CC BY 4.0)"

    // Con `get()`: in un object le proprieta' si inizializzano nell'ordine di
    // dichiarazione, e un valore assegnato qui leggerebbe ITALIAN prima che esista.
    val value: ContentPackManifest get() = ITALIAN

    fun forLanguage(language: AppLanguage): ContentPackManifest = when (language) {
        AppLanguage.ITALIAN -> ITALIAN
        AppLanguage.ENGLISH -> ENGLISH
    }

    private val ITALIAN = ContentPackManifest(
        id = ID,
        version = "5.2.1",
        rulesetVersion = "5.2.1",
        locale = "it-IT",
        title = "System Reference Document 5.2.1 — Italiano",
        sourceUrl = "https://media.dndbeyond.com/compendium-images/srd/5.2/IT_SRD_CC_v5.2.1.pdf",
        license = LICENSE,
        attribution = "Quest'opera include materiale tratto dal System Reference Document 5.2.1 " +
            "(\"SRD 5.2.1\") di Wizards of the Coast LLC, disponibile all'indirizzo " +
            "https://www.dndbeyond.com/srd. Il SRD 5.2.1 è concesso in licenza ai sensi " +
            "della licenza di attribuzione 4.0 Internazionale di Creative Commons, disponibile " +
            "all'indirizzo https://creativecommons.org/licenses/by/4.0/legalcode.",
    )

    // L'attribuzione inglese e' quella testuale richiesta dalla licenza: non e'
    // una traduzione della nostra resa italiana, ma la formula di Wizards.
    private val ENGLISH = ContentPackManifest(
        id = ID,
        version = "5.2.1",
        rulesetVersion = "5.2.1",
        locale = "en-US",
        title = "System Reference Document 5.2.1 — English",
        sourceUrl = "https://media.dndbeyond.com/compendium-images/srd/5.2/SRD_CC_v5.2.1.pdf",
        license = LICENSE,
        attribution = "This work includes material taken from the System Reference Document 5.2.1 " +
            "(\"SRD 5.2.1\") by Wizards of the Coast LLC, available at " +
            "https://www.dndbeyond.com/srd. The SRD 5.2.1 is licensed under the Creative Commons " +
            "Attribution 4.0 International License, available at " +
            "https://creativecommons.org/licenses/by/4.0/legalcode.",
    )
}

val srd521ItManifest: ContentPackManifest get() = Srd521ItManifest.value
