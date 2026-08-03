package app.d6d.content.srd521it

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

    val all: List<RuleElementDefinition> by lazy {
        val stream = checkNotNull(
            SrdClassFeatures::class.java
                .getResourceAsStream("/srd/5.2.1-it/class-features.json"),
        ) { "Risorsa SRD dei privilegi di classe non trovata." }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<ClassFeatureDocument>(it.readText())
        }
        check(document.schemaVersion == 1)
        check(document.records.size == document.counts.records)
        document.records.map(ClassFeatureRecord::toRuleElement).also { elements ->
            check(elements.size == 408) {
                "Attesi 408 record di privilegi/opzioni SRD, trovati ${elements.size}."
            }
            check(elements.distinctBy { it.id }.size == elements.size) {
                "Gli ID dei privilegi di classe SRD non sono univoci."
            }
        }
    }
}

val srdClassFeatures: List<RuleElementDefinition> get() = SrdClassFeatures.all

private fun ClassFeatureRecord.toRuleElement(): RuleElementDefinition {
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
        RuleElementKind.METAMAGIC -> "srd521-it:metamagic:${name.toContentSlug()}"
        else -> id
    }
    val classDefinition = SrdClasses.all.first { it.id == classId }
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
        ?.let { "srd521-it:resource:${classId.contentId}:$it" }
        ?: resource?.name?.let { resourceName ->
            classDefinition.resources.firstOrNull {
                it.name.toContentSlug() == resourceName.toContentSlug()
            }?.id
        }
    val statedCost = Regex("""Costo:\s*(\d+)""", RegexOption.IGNORE_CASE)
        .find(description)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
    val effectiveMinimumLevel = if (id.endsWith(":conoscenze-degli-antichi")) {
        minimumLevel.coerceAtLeast(2)
    } else {
        minimumLevel
    }
    val effectivePrerequisite = if (id.endsWith(":conoscenze-degli-antichi")) {
        "Warlock di 2º livello o superiore"
    } else {
        prerequisite.orEmpty()
    }
    val armorGrant = when {
        id.endsWith(":feature:chierico:protettore") -> ArmorTrainingGrant(heavy = true)
        id.endsWith(":feature:druido:custode") -> ArmorTrainingGrant(medium = true)
        else -> null
    }
    val weaponGrant = when {
        id.endsWith(":feature:chierico:protettore") ||
            id.endsWith(":feature:druido:custode") -> "Armi da guerra"
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
        resourceCost = when {
            id.endsWith(":feature:paladino:tocco-rigenerante") -> 5
            id.endsWith(":feature:guerriero:azione-impetuosa") -> 1
            else -> resource?.cost?.takeIf { it > 0 } ?: statedCost ?: 0
        },
        armorTrainingGrant = armorGrant,
        weaponTrainingGrant = weaponGrant,
        grantedSpellIds = grantedSpellNames.map {
            "srd521-it:spell:${it.toContentSlug()}"
        },
    )
}
