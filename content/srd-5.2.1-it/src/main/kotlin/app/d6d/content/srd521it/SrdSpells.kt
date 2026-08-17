package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.label
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
 * I 339 incantesimi del capitolo corrispondente dei PDF SRD 5.2.1 italiano e inglese.
 *
 * Il JSON è generato da `tools/srd/extract_srd_spells.py`; caricarlo come
 * risorsa evita di trasformare centinaia di descrizioni in codice eseguibile.
 */
object SrdSpells {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<AppLanguage, List<RuleElementDefinition>>()

    fun all(language: AppLanguage = AppLanguage.ITALIAN): List<RuleElementDefinition> = synchronized(cache) {
        cache.getOrPut(language) { load(language) }
    }

    private fun load(language: AppLanguage): List<RuleElementDefinition> {
        val identity = SrdIdentity.of(language)
        val path = "/srd/${identity.resourceDirectory}/spells.json"
        val stream = checkNotNull(SrdSpells::class.java.getResourceAsStream(path)) {
            "Risorsa SRD degli incantesimi non trovata: $path."
        }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<SpellDocument>(it.readText())
        }
        check(document.schemaVersion == 1) {
            "Schema incantesimi SRD non supportato: ${document.schemaVersion}."
        }
        check(document.spellCount == document.spells.size) {
            "Catalogo incantesimi SRD incompleto: ${document.spells.size}/${document.spellCount}."
        }
        return document.spells.map { it.toRuleElement(identity) }.also { elements ->
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

val srdSpells: List<RuleElementDefinition> get() = SrdSpells.all(AppLanguage.ITALIAN)

private fun SpellRecord.toRuleElement(identity: SrdIdentity): RuleElementDefinition =
    RuleElementDefinition(
        id = identity.spellId(name),
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
            // Le due edizioni scrivono «Rituale»/«Ritual» e «Concentrazione»/
            // «Concentration»: cercarle entrambe costa meno di un ramo per lingua,
            // e nessuna delle due parole compare per caso nell'altra edizione.
            ritual = castingTime.lowercase(Locale.ROOT).let { "rituale" in it || "ritual" in it },
            concentration = duration.lowercase(Locale.ROOT)
                .let { "concentrazione" in it || "concentration" in it },
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

/**
 * Le classi come le nomina il JSON, in una lingua o nell'altra.
 *
 * Il `contentId` e' italiano ed e' la chiave di dominio; il PDF inglese pero'
 * elenca «wizard» dove quello italiano dice «mago». L'alias si ricava
 * dall'etichetta inglese della classe invece di essere scritto a mano: una
 * classe nuova lo ottiene da sola.
 */
private val classByContentId: Map<String, CharacterClassId> = buildMap {
    CharacterClassId.entries.forEach { classId ->
        put(classId.contentId, classId)
        put(classId.label(AppLanguage.ENGLISH).lowercase(Locale.ROOT), classId)
    }
}
