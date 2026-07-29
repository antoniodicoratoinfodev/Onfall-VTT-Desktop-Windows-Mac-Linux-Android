package app.d6d.content.srd521it

import app.d6d.rules.character.RuleElementKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * I talenti del pacchetto devono coincidere con quelli estratti dal PDF.
 *
 * Le voci sono scritte in Kotlin perché portano anche dati meccanici che la
 * prosa non contiene — effetti, risorse, attivazione — ma il testo e l'elenco
 * restano quelli del documento: `tools/srd/extract_srd_feats.py` rilegge il PDF
 * ufficiale e questo test confronta le due cose, così una svista o un
 * aggiornamento della fonte non passano inosservati.
 */
class SrdFeatSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FeatRecord(
        val id: String,
        val name: String,
        val category: String,
        val prerequisite: String = "",
        val description: String,
    )

    @Serializable
    private data class FeatCatalog(val feats: List<FeatRecord>)

    private val source: List<FeatRecord> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/srd/5.2.1-it/feats.json")) {
            "feats.json non è fra le risorse: rigenerarlo con tools/srd/extract_srd_feats.py"
        }
        json.decodeFromString(FeatCatalog.serializer(), stream.bufferedReader().use { it.readText() })
            .feats
    }

    private val packFeats = Srd521ItContent.pack.elements.filter { it.kind in featKindsUnderTest }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()

    @Test
    fun `il pacchetto contiene tutti i talenti del documento e nessuno in piu'`() {
        assertEquals(
            source.map { it.name }.sorted(),
            packFeats.map { it.name }.sorted(),
            "l'elenco dei talenti non coincide con quello estratto dal PDF",
        )
        assertEquals(17, source.size, "il capitolo Talenti dell'SRD 5.2.1 ne elenca diciassette")
    }

    @Test
    fun `ogni categoria ha la consistenza del documento`() {
        val expected = mapOf(
            "origin" to RuleElementKind.ORIGIN_FEAT,
            "general" to RuleElementKind.GENERAL_FEAT,
            "fighting-style" to RuleElementKind.FIGHTING_STYLE_FEAT,
            "epic-boon" to RuleElementKind.EPIC_BOON_FEAT,
        )
        val byName = packFeats.associateBy { it.name }
        source.forEach { record ->
            val element = requireNotNull(byName[record.name]) { "manca «${record.name}»" }
            assertEquals(
                expected.getValue(record.category),
                element.kind,
                "«${record.name}» è nella categoria sbagliata",
            )
        }
    }

    @Test
    fun `il testo dei talenti e' quello del documento`() {
        val byName = packFeats.associateBy { it.name }
        source.forEach { record ->
            val element = requireNotNull(byName[record.name])
            assertEquals(
                normalize(record.description),
                normalize(element.description),
                "il testo di «${record.name}» non corrisponde al PDF",
            )
        }
    }

    @Test
    fun `i prerequisiti dichiarati sono quelli del documento`() {
        val byName = packFeats.associateBy { it.name }
        source.filter { it.prerequisite.isNotBlank() }.forEach { record ->
            val element = requireNotNull(byName[record.name])
            assertTrue(
                element.prerequisite.isNotBlank(),
                "«${record.name}» ha un prerequisito nel PDF ma non nel pacchetto",
            )
        }
    }

    private companion object {
        val featKindsUnderTest = setOf(
            RuleElementKind.ORIGIN_FEAT,
            RuleElementKind.GENERAL_FEAT,
            RuleElementKind.FIGHTING_STYLE_FEAT,
            RuleElementKind.EPIC_BOON_FEAT,
        )
    }
}
