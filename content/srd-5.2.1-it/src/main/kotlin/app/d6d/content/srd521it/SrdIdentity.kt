package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Da quale cartella di risorse legge una lingua, e con quali identificativi.
 *
 * Gli identificativi del pacchetto italiano sono **canonici per entrambe le
 * edizioni**. Non e' una preferenza estetica: finiscono nelle schede salvate
 * (`selectedFeatureIds`, `abilityIds`, `contentPackId`), quindi coniarne di
 * nuovi per l'inglese orfanerebbe ogni personaggio creato con la procedura
 * guidata appena qualcuno cambia lingua. Il suffisso `-it` nel prefisso e'
 * percio' il nome di uno spazio di identificativi, non piu' una lingua, e non
 * va «corretto».
 *
 * Il vantaggio di adottarli *prima* che il resto del modulo veda i dati e' che
 * tutta la logica che riconosce un privilegio dal suo identificativo — decine
 * di `id.endsWith(":feature:warlock:…")` in [SrdClassFeatures] — continua a
 * funzionare parola per parola sull'edizione inglese, senza un solo ramo in
 * piu'. Cambiano il nome e la descrizione; l'ossatura no.
 *
 * La tavola di corrispondenza e' generata e verificata da
 * `tools/srd/build_srd_crosswalk.py`.
 */
internal class SrdIdentity private constructor(
    /** Cartella delle risorse: `5.2.1-it` oppure `5.2.1-en`. */
    val resourceDirectory: String,
    private val spellNames: Map<String, String>,
    private val beastNames: Map<String, String>,
    private val featureIds: Map<String, String>,
    private val featNames: Map<String, String>,
) {
    /** L'identificativo canonico di un incantesimo, dal nome nella sua edizione. */
    fun spellId(name: String): String = "$CANONICAL_PREFIX:spell:${canonicalSpellName(name).toContentSlug()}"

    /** Il nome italiano di un incantesimo: serve anche a risolvere i rimandi per nome. */
    fun canonicalSpellName(name: String): String = spellNames[name] ?: name

    fun beastId(name: String, declaredId: String): String {
        val canonical = beastNames[name] ?: return declaredId
        return "$CANONICAL_PREFIX:beast:${canonical.toContentSlug()}"
    }

    fun featureId(declaredId: String): String = featureIds[declaredId] ?: declaredId

    /** Il nome italiano di un talento, che nel codice fa da chiave. */
    fun canonicalFeatName(name: String): String = featNames[name] ?: name

    companion object {
        const val CANONICAL_PREFIX = "srd521-it"

        private val json = Json { ignoreUnknownKeys = true }
        private val cache = HashMap<AppLanguage, SrdIdentity>()

        fun of(language: AppLanguage): SrdIdentity = synchronized(cache) {
            cache.getOrPut(language) { build(language) }
        }

        private fun build(language: AppLanguage): SrdIdentity = when (language) {
            // L'italiano e' gia' canonico: nessuna tavola da consultare.
            AppLanguage.ITALIAN ->
                SrdIdentity("5.2.1-it", emptyMap(), emptyMap(), emptyMap(), emptyMap())
            AppLanguage.ENGLISH -> {
                val table = readCrosswalk("5.2.1-en")
                SrdIdentity(
                    resourceDirectory = "5.2.1-en",
                    // La tavola va da italiano a inglese; qui serve il verso opposto.
                    spellNames = table.spells.inverted("incantesimi"),
                    beastNames = table.beasts.inverted("bestie"),
                    featureIds = table.classFeatures.inverted("privilegi"),
                    featNames = table.feats.inverted("talenti"),
                )
            }
        }

        private fun readCrosswalk(directory: String): CrosswalkNames {
            val stream = checkNotNull(
                SrdIdentity::class.java.getResourceAsStream("/srd/$directory/crosswalk.json"),
            ) { "Tavola di corrispondenza SRD non trovata per $directory." }
            val document = stream.bufferedReader(Charsets.UTF_8).use {
                json.decodeFromString<CrosswalkDocument>(it.readText())
            }
            return document.names
        }

        /**
         * Inverte la tavola verificando che resti una corrispondenza uno a uno.
         *
         * Due voci che puntassero allo stesso nome ne lascerebbero una terza
         * senza, e il difetto si vedrebbe solo mesi dopo su una scheda che non
         * risolve piu' i propri privilegi: meglio non partire affatto.
         */
        private fun Map<String, String>.inverted(what: String): Map<String, String> {
            val result = HashMap<String, String>(size)
            forEach { (canonical, translated) ->
                val clash = result.put(translated, canonical)
                check(clash == null) {
                    "Tavola SRD non biunivoca per $what: «$translated» vale sia " +
                        "«$clash» sia «$canonical»."
                }
            }
            return result
        }
    }
}

@Serializable
private data class CrosswalkDocument(val names: CrosswalkNames)

@Serializable
private data class CrosswalkNames(
    val spells: Map<String, String> = emptyMap(),
    val beasts: Map<String, String> = emptyMap(),
    val feats: Map<String, String> = emptyMap(),
    val class_features: Map<String, String> = emptyMap(),
) {
    val classFeatures: Map<String, String> get() = class_features
}
