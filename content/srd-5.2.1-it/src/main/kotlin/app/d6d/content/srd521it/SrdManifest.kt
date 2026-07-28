package app.d6d.content.srd521it

import app.d6d.rules.character.ContentPackManifest

object Srd521ItManifest {
    val value = ContentPackManifest(
        id = "srd521-it",
        version = "5.2.1",
        rulesetVersion = "5.2.1",
        locale = "it-IT",
        title = "System Reference Document 5.2.1 — Italiano",
        sourceUrl = "https://media.dndbeyond.com/compendium-images/srd/5.2/IT_SRD_CC_v5.2.1.pdf",
        license = "Creative Commons Attribution 4.0 International (CC BY 4.0)",
        attribution = "Quest'opera include materiale tratto dal System Reference Document 5.2.1 " +
            "(\"SRD 5.2.1\") di Wizards of the Coast LLC, disponibile all'indirizzo " +
            "https://www.dndbeyond.com/srd. Il SRD 5.2.1 è concesso in licenza ai sensi " +
            "della licenza di attribuzione 4.0 Internazionale di Creative Commons, disponibile " +
            "all'indirizzo https://creativecommons.org/licenses/by/4.0/legalcode.",
    )
}

val srd521ItManifest: ContentPackManifest get() = Srd521ItManifest.value
