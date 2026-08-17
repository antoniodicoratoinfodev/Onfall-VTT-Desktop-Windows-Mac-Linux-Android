package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Il capitolo Talenti come estratto dal PDF, usato per il testo e non per le regole.
 *
 * Le regole dei diciassette talenti stanno scritte in Kotlin, perche' sono
 * effetti e scelte, non prosa. Il *testo*, invece, e' gia' nei due PDF: prenderlo
 * da li' evita di tradurre a mano diciassette descrizioni e, soprattutto, evita
 * che la resa inglese si allontani dalla lettera dell'SRD.
 */
internal object SrdFeatSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<AppLanguage, Map<String, FeatRecord>>()

    /**
     * I record della lingua richiesta, indicizzati per **nome italiano**.
     *
     * L'indice e' italiano anche per l'edizione inglese: e' la chiave che il
     * codice possiede, dato che le definizioni in Kotlin portano il nome
     * italiano. Il passaggio da un nome all'altro lo fa il crosswalk.
     */
    fun byItalianName(language: AppLanguage): Map<String, FeatRecord> = synchronized(cache) {
        cache.getOrPut(language) { load(language) }
    }

    private fun load(language: AppLanguage): Map<String, FeatRecord> {
        val identity = SrdIdentity.of(language)
        val path = "/srd/${identity.resourceDirectory}/feats.json"
        val stream = checkNotNull(SrdFeatSource::class.java.getResourceAsStream(path)) {
            "Risorsa SRD dei talenti non trovata: $path."
        }
        val catalog = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<FeatCatalog>(it.readText())
        }
        check(catalog.feats.size == 17) {
            "Il capitolo Talenti dell'SRD 5.2.1 ne elenca diciassette, non ${catalog.feats.size}."
        }
        return catalog.feats.associateBy { record ->
            // In italiano il nome e' gia' la chiave; in inglese si torna indietro.
            if (language == AppLanguage.ITALIAN) record.name
            else identity.canonicalFeatName(record.name)
        }.also { indexed ->
            check(indexed.size == catalog.feats.size) {
                "Due talenti risolvono allo stesso nome canonico."
            }
        }
    }
}

@Serializable
internal data class FeatRecord(
    val name: String,
    val category: String,
    val prerequisite: String = "",
    val description: String,
)

@Serializable
private data class FeatCatalog(val feats: List<FeatRecord>)
