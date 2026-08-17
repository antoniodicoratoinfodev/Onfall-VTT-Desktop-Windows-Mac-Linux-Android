package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.sheet.CatalogAbility

/**
 * Punto di ingresso unico del pacchetto SRD 5.2.1, in entrambe le edizioni.
 *
 * Un modulo solo per due lingue e non due moduli gemelli: le cinquemila righe
 * di regole qui dentro sono identiche: cambiano i nomi e le descrizioni, che
 * arrivano dai JSON estratti dai due PDF, e il poco testo che il modulo compone
 * da se', che sta in [SrdWords]. Gli identificativi non cambiano affatto — vedi
 * [SrdIdentity] per il perche'.
 */
object Srd521ItContent {
    private val packs = HashMap<AppLanguage, RulesContentPack>()
    private val catalogs = HashMap<AppLanguage, List<CatalogAbility>>()

    fun packFor(language: AppLanguage): RulesContentPack = synchronized(packs) {
        packs.getOrPut(language) { build(language) }
    }

    fun catalogFor(language: AppLanguage): List<CatalogAbility> = synchronized(catalogs) {
        catalogs.getOrPut(language) {
            val pack = packFor(language)
            pack.elements.map { it.toCatalogAbility(pack) }
        }
    }

    /** L'edizione italiana, che resta il riferimento per prove e regressioni. */
    val pack: RulesContentPack get() = packFor(AppLanguage.ITALIAN)

    val catalog: List<CatalogAbility> get() = catalogFor(AppLanguage.ITALIAN)

    private fun build(language: AppLanguage): RulesContentPack {
        val sourceElements = SrdFeatsAndActions.all(language) + SrdSpells.all(language) +
            SrdClassFeatures.all(language).filterNot {
                it.id.startsWith("srd521-it:feature:warlock:ripetibile")
            } + SrdBeasts.elements(language)
        val elementsById = sourceElements.associateBy { it.id }.toMutableMap()
        val requiredIds = requiredElementIds(language)
        val redirectedSourceIds = mutableSetOf<String>()
        requiredIds.forEach { requiredId ->
            if (requiredId !in elementsById) {
                val resolution = resolveRequiredElement(requiredId, sourceElements, language)
                elementsById[requiredId] = resolution.element
                resolution.sourceId?.let(redirectedSourceIds::add)
            }
        }
        redirectedSourceIds
            .filterNot { it in requiredIds }
            .forEach(elementsById::remove)
        return RulesContentPack(
            manifest = Srd521ItManifest.forLanguage(language),
            classes = SrdClasses.all(language).map { definition ->
                definition.copy(
                    startingWeaponChoice = null,
                    startingEquipmentChoice = SrdStartingEquipment.choiceFor(definition.id, language),
                )
            },
            elements = elementsById.values.sortedBy { it.id },
            weapons = SrdWeapons.all(language),
            backgrounds = SrdBackgrounds.all(language),
            equipmentPackages = SrdStartingEquipment.all(language),
        )
    }

    private fun requiredElementIds(language: AppLanguage): Set<String> = buildSet {
        SrdClasses.all(language).forEach { definition ->
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
        language: AppLanguage,
    ): ElementResolution {
        val words = SrdWords.of(language)
        if (requiredId.startsWith("srd521-it:subclass:")) {
            val slug = requiredId.substringAfterLast(':')
            val classDefinition = SrdClasses.all(language).first { slug in it.subclassIds.single() }
            val subclassFeatures = sourceElements.filter {
                it.kind == RuleElementKind.SUBCLASS_FEATURE &&
                    it.classEligibility.any { eligibility -> eligibility.classId == classDefinition.id }
            }
            return ElementResolution(
                RuleElementDefinition(
                    id = requiredId,
                    name = words.displayName(slug),
                    kind = RuleElementKind.CLASS_OPTION,
                    description = buildString {
                        append(words.subclassDescription(classDefinition.name))
                        if (subclassFeatures.isNotEmpty()) {
                            append(words.subclassFeaturesPrefix)
                            append(subclassFeatures.joinToString { it.name })
                            append('.')
                        }
                    },
                    classEligibility = listOf(ClassEligibility(classDefinition.id, 3)),
                    sourcePage = subclassFeatures.minOfOrNull { it.sourcePage } ?: 0,
                ),
            )
        }
        explicitChoiceElement(requiredId, words)?.let { return ElementResolution(it) }

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
            // Sullo slug dell'identificativo, non su quello del nome: gli
            // identificativi sono canonici in entrambe le edizioni, mentre il
            // nome inglese non somiglia allo slug italiano che stiamo cercando.
            .map { it to it.id.substringAfterLast(':') }
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
                words.mysticArcanum(requiredSlug.substringAfterLast('-').toInt())
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

private data class ElementResolution(
    val element: RuleElementDefinition,
    val sourceId: String? = null,
)

private fun explicitChoiceElement(id: String, words: SrdWords): RuleElementDefinition? {
    val parts = id.split(':')
    val classSlug = parts.getOrNull(2) ?: return null
    val slug = parts.last()
    val classId = CharacterClassId.entries.firstOrNull { it.contentId == classSlug } ?: return null
    val description = when {
        id.startsWith("srd521-it:feature:druido:terra-") -> {
            // Gli slug restano italiani perche' sono chiavi; la parola mostrata
            // no, e viene dal fascicolo.
            val resistance = when (slug) {
                "terra-arida" -> words.fire
                "terra-polare" -> words.cold
                "terra-temperata" -> words.lightning
                "terra-tropicale" -> words.poison
                else -> return null
            }
            words.landChoice(resistance)
        }
        id.startsWith("srd521-it:feature:stregone:affinita-") -> {
            val slugged = slug.removePrefix("affinita-")
            val damage = when (slugged) {
                "acido" -> words.acid
                "freddo" -> words.cold
                "fulmine" -> words.lightning
                "fuoco" -> words.fire
                "veleno" -> words.poison
                else -> return null
            }
            words.elementalAffinity(damage)
        }
        else -> return null
    }
    return RuleElementDefinition(
        id = id,
        name = words.displayName(slug),
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
