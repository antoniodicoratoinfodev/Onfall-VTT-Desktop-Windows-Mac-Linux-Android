package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.sheet.CatalogAbility

/** Punto di ingresso unico del pacchetto italiano SRD 5.2.1. */
object Srd521ItContent {
    val pack: RulesContentPack by lazy {
        val sourceElements = SrdFeatsAndActions.all + SrdSpells.all +
            SrdClassFeatures.all.filterNot {
                it.id.startsWith("srd521-it:feature:warlock:ripetibile")
            }
        val elementsById = sourceElements.associateBy { it.id }.toMutableMap()
        val requiredIds = requiredElementIds()
        val redirectedSourceIds = mutableSetOf<String>()
        requiredIds.forEach { requiredId ->
            if (requiredId !in elementsById) {
                val resolution = resolveRequiredElement(requiredId, sourceElements)
                elementsById[requiredId] = resolution.element
                resolution.sourceId?.let(redirectedSourceIds::add)
            }
        }
        redirectedSourceIds
            .filterNot { it in requiredIds }
            .forEach(elementsById::remove)
        RulesContentPack(
            manifest = Srd521ItManifest.value,
            classes = SrdClasses.all,
            elements = elementsById.values.sortedBy { it.id },
            weapons = SrdWeapons.all,
        )
    }

    val catalog: List<CatalogAbility> by lazy {
        pack.elements.map { it.toCatalogAbility(pack.manifest) }
    }

    private fun requiredElementIds(): Set<String> = buildSet {
        SrdClasses.all.forEach { definition ->
            addAll(definition.subclassIds)
            definition.levels.forEach { level ->
                addAll(level.featureIds)
                level.choices.forEach { choice ->
                    addAll(choice.optionIds.filter(::isRuleElementId))
                }
            }
        }
    }

    private fun resolveRequiredElement(
        requiredId: String,
        sourceElements: List<RuleElementDefinition>,
    ): ElementResolution {
        if (requiredId.startsWith("srd521-it:subclass:")) {
            val slug = requiredId.substringAfterLast(':')
            val classDefinition = SrdClasses.all.first { slug in it.subclassIds.single() }
            val subclassFeatures = sourceElements.filter {
                it.kind == RuleElementKind.SUBCLASS_FEATURE &&
                    it.classEligibility.any { eligibility -> eligibility.classId == classDefinition.id }
            }
            return ElementResolution(
                RuleElementDefinition(
                    id = requiredId,
                    name = subclassDisplayNames[slug] ?: slug.toDisplayName(),
                    kind = RuleElementKind.CLASS_OPTION,
                    description = buildString {
                        append("Sottoclasse SRD del ${classDefinition.name}.")
                        if (subclassFeatures.isNotEmpty()) {
                            append(" Privilegi: ")
                            append(subclassFeatures.joinToString { it.name })
                            append('.')
                        }
                    },
                    classEligibility = listOf(ClassEligibility(classDefinition.id, 3)),
                    sourcePage = subclassFeatures.minOfOrNull { it.sourcePage } ?: 0,
                ),
            )
        }
        explicitChoiceElement(requiredId)?.let { return ElementResolution(it) }

        val segments = requiredId.split(':')
        val classSlug = segments.getOrNull(2)
        val requiredSlug = segments.last()
        val classId = CharacterClassId.entries.firstOrNull { it.contentId == classSlug }
        val explicitSourceId = canonicalSourceIds[requiredId]
        val candidate = explicitSourceId
            ?.let { sourceId -> sourceElements.firstOrNull { it.id == sourceId } }
            ?: sourceElements
            .asSequence()
            .filter { element ->
                element.spell == null &&
                    (classId == null || element.classEligibility.any { it.classId == classId })
            }
            .map { it to it.name.toContentSlug() }
            .filter { (_, nameSlug) ->
                requiredSlug.normalizedKey() == nameSlug.normalizedKey() ||
                    requiredSlug.normalizedKey().endsWith(nameSlug.normalizedKey())
            }
            .maxByOrNull { (_, nameSlug) -> nameSlug.length }
            ?.first
        checkNotNull(candidate) {
            "Riferimento SRD senza record canonico: $requiredId."
        }
        val minimumLevel = arcanumMinimumLevels[requiredId]
        val element = candidate.copy(
            id = requiredId,
            name = if (minimumLevel != null) {
                "Arcanum mistico (${requiredSlug.substringAfterLast('-')}º)"
            } else {
                candidate.name
            },
            classEligibility = if (minimumLevel != null && classId != null) {
                listOf(ClassEligibility(classId, minimumLevel))
            } else {
                candidate.classEligibility
            },
        )
        return ElementResolution(element, candidate.id)
    }
}

val srd521ItPack: RulesContentPack get() = Srd521ItContent.pack

private fun isRuleElementId(id: String): Boolean =
    id.startsWith("srd521-it:feature:") ||
        id.startsWith("srd521-it:subclass:") ||
        id.startsWith("srd521-it:metamagic:") ||
        id.startsWith("srd521-it:feat:")

private fun String.normalizedKey(): String =
    replace(Regex("[^a-z0-9]"), "")
        .removePrefix("berserker")
        .removePrefix("sapienza")
        .removePrefix("vita")
        .removePrefix("terra")
        .removePrefix("campione")
        .removePrefix("furfante")
        .removePrefix("invocatore")
        .removePrefix("manoaperta")
        .removePrefix("devozione")
        .removePrefix("cacciatore")
        .removePrefix("draconica")
        .removePrefix("immondo")

private fun String.toDisplayName(): String =
    replace('-', ' ').replaceFirstChar { it.uppercase() }

private data class ElementResolution(
    val element: RuleElementDefinition,
    val sourceId: String? = null,
)

private fun explicitChoiceElement(id: String): RuleElementDefinition? {
    val parts = id.split(':')
    val classSlug = parts.getOrNull(2) ?: return null
    val slug = parts.last()
    val classId = CharacterClassId.entries.firstOrNull { it.contentId == classSlug } ?: return null
    val description = when {
        id.startsWith("srd521-it:feature:druido:terra-") -> {
            val resistance = when (slug) {
                "terra-arida" -> "fuoco"
                "terra-polare" -> "freddo"
                "terra-temperata" -> "fulmine"
                "terra-tropicale" -> "veleno"
                else -> return null
            }
            "Tipo di terra del Circolo della Terra. Conferisce la relativa lista di " +
                "incantesimi del Circolo e, dall'Interdizione della Natura, resistenza ai danni da $resistance."
        }
        id.startsWith("srd521-it:feature:stregone:affinita-") -> {
            val damage = slug.removePrefix("affinita-")
            if (damage !in setOf("acido", "freddo", "fulmine", "fuoco", "veleno")) return null
            "Tipo di danno scelto per Affinità elementale: $damage."
        }
        else -> return null
    }
    return RuleElementDefinition(
        id = id,
        name = slug.toDisplayName(),
        kind = RuleElementKind.CLASS_OPTION,
        description = description,
        classEligibility = listOf(ClassEligibility(classId, if (classId == CharacterClassId.DRUID) 3 else 6)),
        sourcePage = if (classId == CharacterClassId.DRUID) 52 else 85,
    )
}

private val arcanumMinimumLevels = mapOf(
    "srd521-it:feature:warlock:arcanum-mistico-6" to 11,
    "srd521-it:feature:warlock:arcanum-mistico-7" to 13,
    "srd521-it:feature:warlock:arcanum-mistico-8" to 15,
    "srd521-it:feature:warlock:arcanum-mistico-9" to 17,
)

private val canonicalSourceIds = buildMap {
    CharacterClassId.entries.forEach { classId ->
        val connector = if (classId == CharacterClassId.SORCERER) "dello" else "del"
        put(
            "srd521-it:feature:${classId.contentId}:sottoclasse",
            "srd521-it:feature:${classId.contentId}:sottoclasse-$connector-${classId.contentId}",
        )
    }
    put(
        "srd521-it:feature:chierico:vita-incantesimi-del-dominio",
        "srd521-it:subclass-feature:chierico:incantesimi-del-dominio-della-vita",
    )
    put(
        "srd521-it:feature:druido:terra-incantesimi-del-circolo",
        "srd521-it:subclass-feature:druido:incantesimi-del-circolo-della-terra",
    )
    put(
        "srd521-it:feature:paladino:devozione-incantesimi-del-giuramento",
        "srd521-it:subclass-feature:paladino:incantesimi-del-giuramento-di-devozione",
    )
    arcanumMinimumLevels.keys.forEach {
        put(it, "srd521-it:feature:warlock:arcanum-mistico")
    }
}

private val subclassDisplayNames = mapOf(
    "cammino-del-berserker" to "Cammino del berserker",
    "collegio-della-sapienza" to "Collegio della Sapienza",
    "dominio-della-vita" to "Dominio della Vita",
    "circolo-della-terra" to "Circolo della Terra",
    "campione" to "Campione",
    "furfante" to "Furfante",
    "invocatore" to "Invocatore",
    "guerriero-della-mano-aperta" to "Guerriero della Mano Aperta",
    "giuramento-di-devozione" to "Giuramento di devozione",
    "cacciatore" to "Cacciatore",
    "stregoneria-draconica" to "Stregoneria draconica",
    "patrono-immondo" to "Patrono immondo",
)
