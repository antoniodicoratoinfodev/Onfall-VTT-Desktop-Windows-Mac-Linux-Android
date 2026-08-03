package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BeastDocument(
    @SerialName("schema_version") val schemaVersion: Int,
    val counts: BeastCounts,
    val records: List<BeastRecord>,
)

@Serializable
private data class BeastCounts(val records: Int)

@Serializable
private data class BeastRecord(
    val id: String,
    val name: String,
    @SerialName("challenge_rating") val challengeRating: String,
    @SerialName("has_fly_speed") val hasFlySpeed: Boolean,
    val speed: String,
    val page: Int,
    @SerialName("stat_block") val statBlock: String,
)

/** Scheda completa di una forma bestiale selezionabile con Forma selvatica. */
data class SrdBeastForm(
    val id: String,
    val name: String,
    val challengeRating: String,
    val hasFlySpeed: Boolean,
    val speed: String,
    val sourcePage: Int,
    val statBlock: String,
) {
    private val challengeRank: Int
        get() = when (challengeRating) {
            "0" -> 0
            "1/8" -> 1
            "1/4" -> 2
            "1/2" -> 4
            "1" -> 8
            else -> error("GS non supportato per Forma selvatica: $challengeRating")
        }

    val minimumDruidLevel: Int
        get() = when {
            hasFlySpeed -> 8
            challengeRank <= 2 -> 2
            challengeRank <= 4 -> 4
            else -> 8
        }

    val summary: String
        get() = "GS $challengeRating · Velocità $speed"

    fun isAvailableAt(druidLevel: Int): Boolean {
        val maximumRank = when {
            druidLevel < 4 -> 2
            druidLevel < 8 -> 4
            else -> 8
        }
        return druidLevel >= 2 && challengeRank <= maximumRank && (druidLevel >= 8 || !hasFlySpeed)
    }

    fun toRuleElement(): RuleElementDefinition = RuleElementDefinition(
        id = id,
        name = name,
        kind = RuleElementKind.CLASS_OPTION,
        description = statBlock,
        classEligibility = listOf(ClassEligibility(CharacterClassId.DRUID, minimumDruidLevel)),
        prerequisite = "Forma selvatica · Druido di ${minimumDruidLevel}º livello",
        sourcePage = sourcePage,
    )
}

/** Tutte le Bestie SRD con GS massimo 1, complete di scheda delle statistiche. */
object SrdBeasts {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<SrdBeastForm> by lazy {
        val stream = checkNotNull(
            SrdBeasts::class.java.getResourceAsStream("/srd/5.2.1-it/beasts.json"),
        ) { "Risorsa SRD delle forme bestiali non trovata." }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<BeastDocument>(it.readText())
        }
        check(document.schemaVersion == 1)
        check(document.records.size == document.counts.records)
        document.records.map { record ->
            SrdBeastForm(
                id = record.id,
                name = record.name,
                challengeRating = record.challengeRating,
                hasFlySpeed = record.hasFlySpeed,
                speed = record.speed,
                sourcePage = record.page,
                statBlock = record.statBlock,
            )
        }.also { forms ->
            check(forms.size == 64) {
                "Attese 64 forme bestiali SRD con GS massimo 1, trovate ${forms.size}."
            }
            check(forms.distinctBy { it.id }.size == forms.size) {
                "Gli ID delle forme bestiali SRD non sono univoci."
            }
        }
    }

    val elements: List<RuleElementDefinition> by lazy { all.map(SrdBeastForm::toRuleElement) }

    fun byId(id: String): SrdBeastForm? = all.firstOrNull { it.id == id }

    fun availableAt(druidLevel: Int): List<SrdBeastForm> =
        all.filter { it.isAvailableAt(druidLevel) }
}
