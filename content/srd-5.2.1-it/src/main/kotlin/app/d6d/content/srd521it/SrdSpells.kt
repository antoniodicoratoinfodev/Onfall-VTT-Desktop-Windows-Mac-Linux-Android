package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.SpellDetails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

@Serializable
private data class SpellDocument(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("spell_count") val spellCount: Int,
    val spells: List<SpellRecord>,
)

@Serializable
private data class SpellRecord(
    val name: String,
    val school: String,
    val level: Int,
    @SerialName("is_cantrip") val isCantrip: Boolean,
    val classes: List<String>,
    @SerialName("casting_time") val castingTime: String,
    val range: String,
    val components: String,
    val duration: String,
    val description: String,
    @SerialName("source_pages") val sourcePages: List<Int>,
)

/**
 * I 339 incantesimi del capitolo Incantesimi del PDF italiano SRD 5.2.1.
 *
 * Il JSON è generato da `tools/srd/extract_srd_spells.py`; caricarlo come
 * risorsa evita di trasformare centinaia di descrizioni in codice eseguibile.
 */
object SrdSpells {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<RuleElementDefinition> by lazy {
        val stream = checkNotNull(
            SrdSpells::class.java.getResourceAsStream("/srd/5.2.1-it/spells.json"),
        ) { "Risorsa SRD degli incantesimi non trovata." }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<SpellDocument>(it.readText())
        }
        check(document.schemaVersion == 1) {
            "Schema incantesimi SRD non supportato: ${document.schemaVersion}."
        }
        check(document.spellCount == document.spells.size) {
            "Catalogo incantesimi SRD incompleto: ${document.spells.size}/${document.spellCount}."
        }
        document.spells.map(SpellRecord::toRuleElement).also { elements ->
            check(elements.size == 339) { "Attesi 339 incantesimi SRD, trovati ${elements.size}." }
            check(elements.count { it.kind == RuleElementKind.CANTRIP } == 27) {
                "Il catalogo deve contenere 27 trucchetti."
            }
            check(elements.distinctBy { it.id }.size == elements.size) {
                "Gli ID degli incantesimi SRD non sono univoci."
            }
        }
    }
}

val srdSpells: List<RuleElementDefinition> get() = SrdSpells.all

private fun SpellRecord.toRuleElement(): RuleElementDefinition =
    RuleElementDefinition(
        id = "srd521-it:spell:${name.toContentSlug()}",
        name = name,
        kind = if (isCantrip) RuleElementKind.CANTRIP else RuleElementKind.SPELL,
        description = description,
        classEligibility = classes.map { className ->
            ClassEligibility(
                classId = requireNotNull(classByContentId[className]) {
                    "Classe incantesimo SRD sconosciuta: $className ($name)."
                },
            )
        },
        spell = SpellDetails(
            level = level,
            school = school,
            castingTime = castingTime,
            range = range,
            components = components,
            duration = duration,
            ritual = "rituale" in castingTime.lowercase(Locale.ROOT),
            concentration = "concentrazione" in duration.lowercase(Locale.ROOT),
        ),
        sourcePage = sourcePages.firstOrNull() ?: 0,
        activation = castingTime,
    )

internal fun String.toContentSlug(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace("’", "")
        .replace("'", "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private val classByContentId: Map<String, CharacterClassId> =
    CharacterClassId.entries.associateBy { it.contentId }
