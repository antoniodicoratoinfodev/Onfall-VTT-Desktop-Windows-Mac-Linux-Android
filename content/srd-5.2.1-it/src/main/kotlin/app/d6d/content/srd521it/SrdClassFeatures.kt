package app.d6d.content.srd521it

import app.d6d.content.srd521it.SrdIdentity.Companion.CANONICAL_PREFIX
import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ArmorTrainingGrant
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ClassFeatureDocument(
    @SerialName("schema_version") val schemaVersion: Int,
    val counts: ClassFeatureCounts,
    val records: List<ClassFeatureRecord>,
)

@Serializable
private data class ClassFeatureCounts(
    val records: Int,
)

@Serializable
private data class ClassFeatureResource(
    val name: String,
    val cost: Int = 0,
)

@Serializable
private data class ClassFeatureRecord(
    val id: String,
    val kind: String,
    val name: String,
    @SerialName("class") val classSlug: String,
    @SerialName("minimum_level") val minimumLevel: Int,
    val subclass: String? = null,
    val description: String,
    val page: Int,
    val activation: String? = null,
    val resource: ClassFeatureResource? = null,
    val prerequisite: String? = null,
)

/** Privilegi, opzioni, Metamagie e Suppliche delle dodici classi SRD. */
object SrdClassFeatures {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<AppLanguage, List<RuleElementDefinition>>()

    fun all(language: AppLanguage = AppLanguage.ITALIAN): List<RuleElementDefinition> = synchronized(cache) {
        cache.getOrPut(language) { load(language) }
    }

    private fun load(language: AppLanguage): List<RuleElementDefinition> {
        val identity = SrdIdentity.of(language)
        val path = "/srd/${identity.resourceDirectory}/class-features.json"
        val stream = checkNotNull(SrdClassFeatures::class.java.getResourceAsStream(path)) {
            "Risorsa SRD dei privilegi di classe non trovata: $path."
        }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<ClassFeatureDocument>(it.readText())
        }
        check(document.schemaVersion == 1)
        check(document.records.size == document.counts.records)
        return document.records.map { it.toRuleElement(language, identity) }.also { elements ->
            check(elements.size == 408) {
                "Attesi 408 record di privilegi/opzioni SRD, trovati ${elements.size}."
            }
            check(elements.distinctBy { it.id }.size == elements.size) {
                "Gli ID dei privilegi di classe SRD non sono univoci."
            }
        }
    }
}

val srdClassFeatures: List<RuleElementDefinition> get() = SrdClassFeatures.all(AppLanguage.ITALIAN)

private fun ClassFeatureRecord.toRuleElement(
    language: AppLanguage,
    identity: SrdIdentity,
): RuleElementDefinition {
    // L'identificativo canonico si adotta *subito*: da qui in giu' ogni
    // riconoscimento avviene sugli slug italiani, e vale percio' per entrambe
    // le edizioni senza un solo ramo dedicato alla lingua.
    val id = identity.featureId(this.id)
    val classId = requireNotNull(CharacterClassId.entries.firstOrNull { it.contentId == classSlug }) {
        "Classe SRD sconosciuta nel privilegio $id: $classSlug."
    }
    val ruleKind = when (kind) {
        "class-feature" -> RuleElementKind.CLASS_FEATURE
        "subclass-feature" -> RuleElementKind.SUBCLASS_FEATURE
        "metamagia" -> RuleElementKind.METAMAGIC
        "supplica-occulta" -> RuleElementKind.ELDRITCH_INVOCATION
        "internal-option" -> RuleElementKind.CLASS_OPTION
        else -> error("Tipo di privilegio SRD sconosciuto: $kind.")
    }
    val normalizedId = when (ruleKind) {
        // Dallo slug dell'identificativo canonico, non dal nome: in inglese il
        // nome e' «Subtle Spell» e coniarci sopra darebbe un secondo id.
        RuleElementKind.METAMAGIC -> "$CANONICAL_PREFIX:metamagic:${id.substringAfterLast(':')}"
        else -> id
    }
    val classDefinition = SrdClasses.all(language).first { it.id == classId }
    val explicitResourceSlug = when {
        id.endsWith(":feature:guerriero:recuperare-energie") -> "recuperare-energie"
        id.endsWith(":feature:guerriero:azione-impetuosa") -> "azione-impetuosa"
        id.endsWith(":feature:guerriero:indomabile") -> "indomabile"
        id.endsWith(":feature:ladro:colpo-di-fortuna") -> "colpo-di-fortuna"
        id.endsWith(":feature:mago:recupero-arcano") -> "recupero-arcano"
        id.endsWith(":feature:ranger:nemico-prescelto") -> "nemico-prescelto"
        id.endsWith(":feature:stregone:stregoneria-innata") -> "stregoneria-innata"
        id.endsWith(":feature:warlock:magia-del-patto") ||
            id.endsWith(":feature:warlock:punizione-occulta") -> "slot-magia-del-patto"
        else -> null
    }
    val resourceId = explicitResourceSlug
        ?.let { "$CANONICAL_PREFIX:resource:${classId.contentId}:$it" }
        ?: resource?.name?.let { resourceName ->
            classDefinition.resources.firstOrNull {
                it.name.toContentSlug() == resourceName.toContentSlug()
            }?.id
        }
    // Solo quando la descrizione dichiara *un* costo. Un privilegio che ne
    // elenca piu' d'uno e' un menu — «Colpi infidi» presenta tre opzioni da
    // 2d6, 3d6 e 6d6 — e il costo appartiene alle opzioni, non a lui. Prendere
    // il primo dava per giunta un numero diverso nelle due edizioni, perche'
    // le opzioni sono in ordine alfabetico e l'alfabeto cambia con la lingua.
    val declaredCosts = SrdWords.of(language).statedCost.findAll(description).toList()
    val statedCost = declaredCosts.singleOrNull()?.groupValues?.get(1)?.toIntOrNull()
    // Qui c'era una toppa che alzava a 2 il livello di «Conoscenze degli Antichi»
    // e ne riscriveva il prerequisito a mano. Serviva perche' l'estrattore
    // perdeva quel livello; ora lo legge, in entrambe le edizioni, e la toppa
    // non solo e' inutile ma avrebbe infilato una frase italiana nel pacchetto
    // inglese. Il dato corretto sta nel JSON: `tools/srd/extract_srd_class_features.py`.
    val effectiveMinimumLevel = minimumLevel
    val effectivePrerequisite = prerequisite.orEmpty()
    val armorGrant = when {
        id.endsWith(":feature:chierico:protettore") -> ArmorTrainingGrant(heavy = true)
        id.endsWith(":feature:druido:custode") -> ArmorTrainingGrant(medium = true)
        else -> null
    }
    val weaponGrant = when {
        id.endsWith(":feature:chierico:protettore") ||
            id.endsWith(":feature:druido:custode") -> SrdWords.of(language).martialWeapons
        else -> ""
    }
    val grantedSpellNames = when {
        id.endsWith(":feature:druido:compagno-selvatico") -> listOf("Trova famiglio")
        id.endsWith(":feature:warlock:patto-della-catena") -> listOf("Trova famiglio")
        id.endsWith(":feature:warlock:armatura-delle-ombre") -> listOf("Armatura magica")
        id.endsWith(":feature:warlock:balzo-ultraterreno") -> listOf("Salto")
        id.endsWith(":feature:warlock:dono-degli-abissi") -> listOf("Respirare sott'acqua")
        id.endsWith(":feature:warlock:maestro-di-mille-forme") -> listOf("Alterare se stesso")
        id.endsWith(":feature:warlock:maschera-dei-molti-volti") -> listOf("Camuffare se stesso")
        id.endsWith(":feature:warlock:passo-ascendente") -> listOf("Levitazione")
        id.endsWith(":feature:warlock:sussurri-dalla-tomba") -> listOf("Parlare con i morti")
        id.endsWith(":feature:warlock:tuttuno-con-le-ombre") -> listOf("Invisibilità")
        id.endsWith(":feature:warlock:vigore-immondo") -> listOf("Vita falsata")
        id.endsWith(":feature:warlock:visione-dei-reami-lontani") -> listOf("Occhio arcano")
        id.endsWith(":feature:warlock:visioni-velate") -> listOf("Immagine silenziosa")
        else -> emptyList()
    }
    return RuleElementDefinition(
        id = normalizedId,
        name = name,
        kind = ruleKind,
        description = description,
        classEligibility = listOf(ClassEligibility(classId, effectiveMinimumLevel.coerceIn(1, 20))),
        prerequisite = effectivePrerequisite,
        sourcePage = page,
        activation = if (id.endsWith(":feature:guerriero:azione-impetuosa")) "" else activation.orEmpty(),
        resourceId = resourceId,
        // Un costo senza risorsa non vuol dire nulla, e il motore lo rifiuta:
        // il testo puo' nominare una spesa la cui risorsa questa classe non
        // possiede, e in quel caso il numero va lasciato cadere.
        resourceCost = when {
            resourceId == null -> 0
            id.endsWith(":feature:paladino:tocco-rigenerante") -> 5
            id.endsWith(":feature:guerriero:azione-impetuosa") -> 1
            else -> resource?.cost?.takeIf { it > 0 } ?: statedCost ?: 0
        },
        armorTrainingGrant = armorGrant,
        weaponTrainingGrant = weaponGrant,
        // I nomi qui sopra sono italiani di proposito: sono chiavi, non testo da
        // mostrare, e producono l'identificativo canonico in entrambe le edizioni.
        grantedSpellIds = grantedSpellNames.map {
            "$CANONICAL_PREFIX:spell:${it.toContentSlug()}"
        },
    )
}
