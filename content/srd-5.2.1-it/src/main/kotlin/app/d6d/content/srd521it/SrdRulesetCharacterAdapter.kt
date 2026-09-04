package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.Ability
import app.d6d.rules.character.ArmorTrainingGrant
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterSkillDefinition
import app.d6d.rules.character.CharacterStatDefinition
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.ClassLevelDefinition
import app.d6d.rules.character.ProficiencyProgressionDefinition
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.rules.character.Skill
import app.d6d.rules.character.SpellDetails
import app.d6d.rules.character.SpellcastingKind
import app.d6d.rules.character.WeaponCategory
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponTrainingGrant
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.model.RulesetRevision

/**
 * Proietta una revisione pubblicata nel modello realmente consumato dalla
 * creazione e dalla progressione del personaggio.
 *
 * È l'adattatore di transizione fra il catalogo generico delle Regole e il
 * motore guidato attuale: classi, elementi e modificatori non restano più
 * semplici righe consultabili. Il formato stringa degli attributi è ancora il
 * formato V1, ma qui viene interpretato e validato in un unico punto.
 */
object SrdRulesetCharacterAdapter {

    fun project(revision: RulesetRevision, language: AppLanguage): RulesContentPack {
        validateExecutableLinks(revision, language)
        val srdBase = Srd521ItContent.packFor(language)
        val base = if (inheritsSrdContent(revision)) {
            srdBase
        } else {
            // Il modello guidato usa ancora lo stesso DTO del pack SRD, ma un
            // regolamento autonomo deve partire da collezioni realmente vuote:
            // nessuna classe, stat, skill, arma o provenienza entra per fallback.
            srdBase.copy(
                manifest = srdBase.manifest.copy(
                    id = revision.projectId(),
                    sourceUrl = "",
                    license = "",
                    attribution = "",
                ),
                classes = emptyList(),
                elements = emptyList(),
                weapons = emptyList(),
                backgrounds = emptyList(),
                equipmentPackages = emptyList(),
                maximumCharacterLevel = 1,
                enforceExperienceThresholds = false,
                stats = emptyList(),
                skills = emptyList(),
                experienceThresholds = emptyList(),
            )
        }
        val allEntities = revision.entities()
        val enabled = allEntities.filter(RuleEntity::enabled)
        val classEntities = enabled.filter { it.kind() == RuleKind.CLASS }
        val classIdsByEntity = classEntities.associate { entity -> entity.id() to classId(entity) }
        val stats = projectStats(base, allEntities, language)
        val skills = projectSkills(base, allEntities, stats, language)
        val modifiers = enabled
            .filter { it.kind() == RuleKind.MODIFIER && it.attributes().containsKey("target") }
            .map { modifier(it, language) }

        val elements = projectElements(base, allEntities, modifiers, language)
        val elementsById = elements.associateBy { it.id }
        val baseClasses = base.classes.associateBy { it.id }
        val classes = classEntities.map { entity ->
            val id = classIdsByEntity.getValue(entity.id())
            val definition = projectClass(
                entity = entity,
                id = id,
                base = baseClasses[id],
                modifiers = modifiers.filter { projected ->
                    projected.ownerRef == entity.id() ||
                        projected.ownerRef.equals(id.value, ignoreCase = true) ||
                        projected.ownerRef.equals(id.contentId, ignoreCase = true)
                },
                packId = base.manifest.id,
                language = language,
            )
            definition.levels.flatMap { it.featureIds }.forEach { featureId ->
                require(featureId in elementsById) {
                    "${entity.id()}.levelFeatureIds references missing rule $featureId"
                }
            }
            definition.subclassIds.forEach { subclassId ->
                require(subclassId in elementsById) {
                    "${entity.id()}.subclassIds references missing rule $subclassId"
                }
            }
            definition
        }.sortedWith(compareBy<ClassDefinition> { it.id.ordinal }.thenBy { it.id.value })

        val selectableOptionIds = buildSet {
            addAll(elementsById.keys)
            classes.forEach { definition ->
                addAll(definition.subclassIds)
                definition.levels.forEach { level ->
                    level.choices.forEach { addAll(it.optionIds) }
                }
            }
        }
        elements.forEach { element ->
            element.requiredOptionId?.let { requiredOptionId ->
                require(requiredOptionId in selectableOptionIds) {
                    "${element.id}.requiredOptionId references missing option $requiredOptionId"
                }
            }
        }
        classes.forEach { definition ->
            definition.levels.flatMap { it.choices }.forEach { choice ->
                choice.requiredOptionId?.let { requiredOptionId ->
                    require(requiredOptionId in selectableOptionIds) {
                        "${choice.id}.requiredOptionId references missing option $requiredOptionId"
                    }
                }
            }
        }

        val statIds = stats.mapTo(mutableSetOf()) { it.id }
        classes.forEach { definition ->
            val referenced = definition.primaryAbilities + definition.savingThrowProficiencies +
                definition.multiclassPrerequisiteGroups.flatten() + listOfNotNull(definition.spellcastingAbility)
            referenced.forEach { statId ->
                require(statId in statIds) {
                    "${definition.id.value} references missing enabled stat ${statId.value}"
                }
            }
        }

        val declaredMaximumRaw = enabled
            .asSequence()
            .filter { it.kind() == RuleKind.PROGRESSION }
            .mapNotNull { it.attributes()["maximumCharacterLevel"]?.takeIf(String::isNotBlank) }
            .firstOrNull()
        val declaredMaximum = declaredMaximumRaw?.toIntOrNull()
            ?: if (declaredMaximumRaw == null) null else error("maximumCharacterLevel must be an integer")
        declaredMaximum?.let { require(it >= 1) { "maximumCharacterLevel must be at least 1" } }
        // Un regolamento classless resta proiettabile: la scheda generata e la
        // GameSession non devono inventare una classe soltanto per attraversare
        // questo adattatore D&D. Il servizio guidato vedrà semplicemente zero classi.
        val largestClassLevel = classes.maxOfOrNull { it.maximumLevel } ?: 1
        // Una classe homebrew può estendere il limite ereditato dal regolamento
        // di base: i suoi livelli non devono diventare irraggiungibili perché
        // la progressione SRD fotografava ancora il livello 20.
        val maximumCharacterLevel = maxOf(declaredMaximum ?: largestClassLevel, largestClassLevel)
        val enforceExperienceRaw = enabled
            .asSequence()
            .filter { it.kind() == RuleKind.PROGRESSION }
            .mapNotNull { it.attributes()["enforceExperienceThresholds"]?.takeIf(String::isNotBlank) }
            .firstOrNull()
        val enforceExperience = enforceExperienceRaw?.toBooleanStrictOrNull()
            ?: if (enforceExperienceRaw == null) {
                // Oltre la tabella SRD il fallback sicuro è l'avanzamento deciso
                // dal tavolo, finché la revisione non dichiara una propria curva PE.
                base.experienceThresholds.isNotEmpty()
            } else {
                error("enforceExperienceThresholds must be true or false")
            }
        val compiled = revision.compile()
        val experienceThresholds = compiled.experienceProgression()?.experienceTableRef()
            ?.takeIf(String::isNotBlank)
            ?.let { tableRef -> compiled.tables()[compiled.resolveId(tableRef)] }
            ?.rows()
            ?.entries
            ?.mapNotNull { (threshold, level) ->
                runCatching { level.asNumber().intValueExact() to threshold.intValueExact() }.getOrNull()
            }
            ?.sortedBy { it.first }
            ?.takeIf { rows -> rows.map { it.first } == (1..rows.size).toList() }
            ?.map { it.second }
            .orEmpty()
        // Una curva può essere intenzionalmente parziale: le soglie sono
        // vincolanti finché esistono, poi il tavolo usa avanzamento manuale.

        return base.copy(
            manifest = base.manifest.copy(
                version = revision.version(),
                rulesetVersion = revision.version(),
                title = revision.name(),
                rulesetProjectId = revision.projectId(),
                rulesetRevisionId = revision.revisionId(),
                rulesetCanonicalHash = revision.canonicalHash(),
                rulesetRuntimeHash = revision.runtimeHash(),
                runtimeSemanticsVersion = revision.runtime().semanticsVersion(),
            ),
            classes = classes,
            elements = elements,
            proficiencyProgression = ProficiencyProgressionDefinition(
                base = revision.runtime().proficiencyBonusBase(),
                levelsPerIncrease = revision.runtime().proficiencyLevelsPerIncrease(),
                maximum = revision.runtime().proficiencyBonusMaximum(),
            ),
            maximumCharacterLevel = maximumCharacterLevel,
            enforceExperienceThresholds = enforceExperience,
            stats = stats,
            skills = skills,
            experienceThresholds = experienceThresholds,
        )
    }

    private fun projectStats(
        base: RulesContentPack,
        entities: List<RuleEntity>,
        language: AppLanguage,
    ): List<CharacterStatDefinition> {
        val result = base.stats.associateByTo(linkedMapOf()) { it.id }
        entities.filter { it.kind() == RuleKind.STAT }.forEach { entity ->
            val id = Ability.of(entity.attributes()["statId"] ?: entity.id())
            if (!entity.enabled()) {
                result.remove(id)
                return@forEach
            }
            val inherited = result[id]
            fun simpleInteger(key: String, fallback: Int): Int {
                val raw = entity.attributes()[key]?.takeIf(String::isNotBlank) ?: return fallback
                return raw.toIntOrNull()
                    ?: error("${entity.id()}.$key must be an integer for the guided character sheet")
            }
            result[id] = CharacterStatDefinition(
                id = id,
                name = entity.name().text(language.tag),
                abbreviation = entity.attributes()["abbreviation"]
                    ?: inherited?.abbreviation
                    ?: id.abbreviation,
                defaultScore = simpleInteger("defaultFormula", inherited?.defaultScore ?: 10),
                minimumScore = simpleInteger("minimumFormula", inherited?.minimumScore ?: Int.MIN_VALUE),
                maximumScore = simpleInteger("maximumFormula", inherited?.maximumScore ?: Int.MAX_VALUE),
                advancementMaximum = simpleInteger(
                    "advancementMaximum",
                    inherited?.advancementMaximum ?: simpleInteger(
                        "maximumFormula",
                        inherited?.maximumScore ?: Int.MAX_VALUE,
                    ),
                ),
                modifierFormula = entity.attributes()["modifierFormula"]
                    ?: inherited?.modifierFormula
                    ?: "\${score}",
                ruleEntityId = entity.id(),
                rounding = entity.attributes()["rounding"]
                    ?.takeIf(String::isNotBlank)
                    ?.let { app.d6d.rules.character.CharacterStatRounding.valueOf(it.uppercase()) }
                    ?: inherited?.rounding
                    ?: app.d6d.rules.character.CharacterStatRounding.NONE,
            )
        }
        return result.values.sortedWith(compareBy<CharacterStatDefinition> { it.id.ordinal }.thenBy { it.name })
    }

    private fun projectSkills(
        base: RulesContentPack,
        entities: List<RuleEntity>,
        stats: List<CharacterStatDefinition>,
        language: AppLanguage,
    ): List<CharacterSkillDefinition> {
        val result = base.skills.associateByTo(linkedMapOf()) { it.id }
        val statByEntity = entities.filter { it.kind() == RuleKind.STAT }.associate { entity ->
            entity.id() to Ability.of(entity.attributes()["statId"] ?: entity.id())
        }
        entities.filter { it.kind() == RuleKind.SKILL }.forEach { entity ->
            val id = Skill.of(entity.attributes()["skillId"] ?: entity.id())
            if (!entity.enabled()) {
                result.remove(id)
                return@forEach
            }
            val inherited = result[id]
            val rawStat = entity.attributes()["statRef"]
                ?: entity.attributes()["ability"]
                ?: inherited?.statId?.value
                ?: error("${entity.id()}.statRef is required")
            val statId = statByEntity[rawStat] ?: Ability.of(rawStat)
            result[id] = CharacterSkillDefinition(
                id = id,
                name = entity.name().text(language.tag),
                statId = statId,
                formula = entity.attributes()["formula"] ?: inherited?.formula.orEmpty(),
                trainedBonusFormula = entity.attributes()["trainedBonusFormula"]
                    ?: inherited?.trainedBonusFormula
                    ?: "\${proficiency}",
                ruleEntityId = entity.id(),
            )
        }
        val availableStats = stats.mapTo(mutableSetOf()) { it.id }
        result.values.forEach { skill ->
            require(skill.statId in availableStats) {
                "${skill.id.value} references missing enabled stat ${skill.statId.value}"
            }
        }
        return result.values.sortedWith(compareBy<CharacterSkillDefinition> { it.id.ordinal }.thenBy { it.name })
    }

    /**
     * Valida i collegamenti V1 anche in un regolamento senza classi giocabili.
     * I modificatori generici con `targetRef` sono invece verificati da
     * [app.d6d.rules.model.RulesetCompiler].
     */
    fun validateExecutableLinks(revision: RulesetRevision, language: AppLanguage) {
        val enabled = revision.entities().filter(RuleEntity::enabled)
        val classEntities = enabled.filter { it.kind() == RuleKind.CLASS }
        val elementIds = enabled.filter(::representsElement).mapTo(mutableSetOf()) { it.id() }
        enabled.asSequence()
            .filter { it.kind() == RuleKind.MODIFIER && it.attributes().containsKey("target") }
            .map { modifier(it, language) }
            .forEach { projected ->
                val classOwner = classEntities.firstOrNull { entity ->
                    val id = classId(entity)
                    projected.ownerRef == entity.id() ||
                        projected.ownerRef.equals(id.value, ignoreCase = true) ||
                        projected.ownerRef.equals(id.contentId, ignoreCase = true)
                }
                val ownsElement = projected.ownerRef in elementIds
                require(classOwner != null || ownsElement) {
                    "${projected.entityId}.ownerRef references missing class or rule ${projected.ownerRef}"
                }
                if (classOwner == null) {
                    require(projected.minimumLevel == 1) {
                        "${projected.entityId}.minimumLevel is only valid for a class owner"
                    }
                }
            }
    }

    private fun projectElements(
        base: RulesContentPack,
        entities: List<RuleEntity>,
        modifiers: List<ProjectedModifier>,
        language: AppLanguage,
    ): List<RuleElementDefinition> {
        val result = base.elements.associateByTo(linkedMapOf()) { it.id }
        val representedIds = entities.filter(::representsElement).mapTo(mutableSetOf()) { it.id() }

        // Una voce standard disabilitata deve sparire anche dal pack eseguibile.
        entities.filterNot(RuleEntity::enabled).forEach { result.remove(it.id()) }

        entities.filter { it.enabled() && representsElement(it) }.forEach { entity ->
            val baseElement = result[entity.id()]
            val attributes = entity.attributes()
            val kind = attributes["elementKind"]
                ?.parseEnum<RuleElementKind>(entity, "elementKind")
                ?: baseElement?.kind
                ?: when (entity.kind()) {
                    RuleKind.ACTION -> RuleElementKind.COMMON_ACTION
                    RuleKind.FEATURE -> RuleElementKind.CLASS_FEATURE
                    RuleKind.SUBCLASS -> RuleElementKind.SUBCLASS_FEATURE
                    RuleKind.FEAT -> RuleElementKind.GENERAL_FEAT
                    RuleKind.SPELL -> if (attributes["spellLevel"] == "0") {
                        RuleElementKind.CANTRIP
                    } else {
                        RuleElementKind.SPELL
                    }
                    else -> RuleElementKind.CUSTOM
                }
            val spell = if (kind == RuleElementKind.SPELL || kind == RuleElementKind.CANTRIP) {
                SpellDetails(
                    level = attributes.int("spellLevel", entity, baseElement?.spell?.level ?: 0, minimum = 0),
                    school = attributes["school"] ?: baseElement?.spell?.school ?: "Custom",
                    castingTime = attributes["castingTime"] ?: baseElement?.spell?.castingTime.orEmpty(),
                    range = attributes["range"] ?: baseElement?.spell?.range.orEmpty(),
                    components = attributes["components"] ?: baseElement?.spell?.components.orEmpty(),
                    duration = attributes["duration"] ?: baseElement?.spell?.duration.orEmpty(),
                    ritual = attributes.boolean("ritual", entity, baseElement?.spell?.ritual ?: false),
                    concentration = attributes.boolean(
                        "concentration",
                        entity,
                        baseElement?.spell?.concentration ?: false,
                    ),
                )
            } else {
                null
            }
            val attachedEffects = modifiers
                .filter { it.ownerRef == entity.id() }
                .map { it.effect }
            result[entity.id()] = RuleElementDefinition(
                id = entity.id(),
                name = entity.name().text(language.tag),
                kind = kind,
                description = entity.description().text(language.tag),
                classEligibility = attributes["classEligibility"]
                    ?.splitCsv()
                    ?.map { encoded ->
                        val rawId = encoded.substringBeforeLast(':')
                        val level = encoded.substringAfterLast(':', "1").toIntOrNull()
                            ?: error("${entity.id()}.classEligibility has invalid level in $encoded")
                        require(level >= 1) {
                            "${entity.id()}.classEligibility minimum level must be at least 1"
                        }
                        ClassEligibility(CharacterClassId.of(rawId), level)
                    }
                    ?: baseElement?.classEligibility.orEmpty(),
                requiredOptionId = attributes["requiredOptionId"]
                    ?.takeIf(String::isNotBlank)
                    ?: baseElement?.requiredOptionId,
                spell = spell,
                prerequisite = attributes["prerequisite"] ?: baseElement?.prerequisite.orEmpty(),
                sourcePage = entity.sourcePage(),
                activation = attributes["activation"] ?: baseElement?.activation.orEmpty(),
                resourceId = attributes["resourceId"]?.takeIf(String::isNotBlank),
                resourceCost = attributes.int(
                    "resourceCost",
                    entity,
                    baseElement?.resourceCost ?: 0,
                    minimum = 0,
                ),
                armorTrainingGrant = baseElement?.armorTrainingGrant,
                weaponTrainingGrant = baseElement?.weaponTrainingGrant.orEmpty(),
                grantedSpellIds = attributes["grantedSpellIds"]?.splitCsv()
                    ?: baseElement?.grantedSpellIds.orEmpty(),
                effects = (baseElement?.effects.orEmpty() + attachedEffects).distinct(),
            )
        }

        // Se il documento rappresenta esplicitamente una voce del pack ma la
        // disabilita, non deve rientrare dalla base per effetto del fallback.
        base.elements.filter { it.id in representedIds }.forEach { baseElement ->
            if (entities.none { it.id() == baseElement.id && it.enabled() }) result.remove(baseElement.id)
        }
        return result.values.sortedBy { it.id }
    }

    private val elementKinds = setOf(
        RuleKind.ACTION,
        RuleKind.FEATURE,
        RuleKind.FEAT,
        RuleKind.SPELL,
        RuleKind.SUBCLASS,
    )

    private fun representsElement(entity: RuleEntity): Boolean =
        entity.kind() in elementKinds ||
            (entity.kind() == RuleKind.CUSTOM && entity.attributes().containsKey("elementKind"))

    private fun projectClass(
        entity: RuleEntity,
        id: CharacterClassId,
        base: ClassDefinition?,
        modifiers: List<ProjectedModifier>,
        packId: String,
        language: AppLanguage,
    ): ClassDefinition {
        val attributes = entity.attributes()
        val primaryAbilities = attributes["primaryAbilities"]
            ?.parseAbilities(entity, "primaryAbilities")
            ?: base?.primaryAbilities
            ?: setOf(Ability.STRENGTH)
        val saves = attributes["savingThrowProficiencies"]
            ?.parseAbilities(entity, "savingThrowProficiencies")
            ?: base?.savingThrowProficiencies
            ?: primaryAbilities.take(1).toSet()
        val multiclassPrerequisites = attributes["multiclassPrerequisiteGroups"]
            ?.parseAbilityGroups(entity, "multiclassPrerequisiteGroups")
            ?: base?.multiclassPrerequisiteGroups
            ?: primaryAbilities.map { setOf(it) }
        val maximumLevel = attributes.int(
            "maximumLevel",
            entity,
            base?.maximumLevel ?: 20,
            minimum = 1,
        )
        val subclassIds = attributes["subclassIds"]?.splitCsv() ?: base?.subclassIds.orEmpty()
        val subclassLevel = attributes.int(
            "subclassLevel",
            entity,
            base?.subclassLevel ?: 3,
            minimum = 1,
        )
        require(subclassIds.isEmpty() || subclassLevel <= maximumLevel) {
            "${entity.id()}.subclassLevel exceeds maximumLevel $maximumLevel"
        }
        val inheritedSubclassChoice = base
            ?.levels
            ?.asSequence()
            ?.flatMap { it.choices.asSequence() }
            ?.firstOrNull { it.kind == ChoiceKind.SUBCLASS }
        val featureIdsByLevel = attributes["levelFeatureIds"]
            ?.parseLevelFeatures(entity, maximumLevel)
        val levels = (1..maximumLevel).map { level ->
            val inherited = base?.levels?.getOrNull(level - 1) ?: ClassLevelDefinition(level)
            val hadSubclassChoice = inherited.choices.any { it.kind == ChoiceKind.SUBCLASS }
            val choices = inherited.choices.mapNotNullTo(mutableListOf()) { choice ->
                if (choice.kind != ChoiceKind.SUBCLASS) {
                    choice
                } else if (level == subclassLevel && subclassIds.isNotEmpty()) {
                    choice.copy(optionIds = subclassIds)
                } else {
                    null
                }
            }
            if (level == subclassLevel && subclassIds.isNotEmpty() && !hadSubclassChoice) {
                choices += inheritedSubclassChoice?.copy(optionIds = subclassIds)
                    ?: ChoiceDefinition(
                        id = "${id.value}:choice:$level:subclass",
                        title = if (language == AppLanguage.ITALIAN) {
                            "Scegli la sottoclasse"
                        } else {
                            "Choose the subclass"
                        },
                        kind = ChoiceKind.SUBCLASS,
                        count = 1,
                        optionIds = subclassIds,
                    )
            }
            inherited.copy(
                level = level,
                featureIds = featureIdsByLevel?.get(level) ?: inherited.featureIds,
                choices = choices,
                effects = (
                    inherited.effects +
                        modifiers.filter { it.minimumLevel == level }.map { it.effect }
                    ).distinct(),
            )
        }
        modifiers.forEach { projected ->
            require(projected.minimumLevel <= maximumLevel) {
                "${projected.entityId}.minimumLevel exceeds ${entity.id()} maximumLevel $maximumLevel"
            }
        }
        val spellcastingKind = attributes["spellcastingKind"]
            ?.parseEnum<SpellcastingKind>(entity, "spellcastingKind")
            ?: base?.spellcastingKind
            ?: SpellcastingKind.NONE
        val spellcastingAbility = attributes["spellcastingAbility"]
            ?.takeIf(String::isNotBlank)
            ?.parseAbility(entity, "spellcastingAbility")
            ?: base?.spellcastingAbility
        val skillCount = attributes.int(
            "skillChoiceCount",
            entity,
            base?.skillChoice?.count ?: 0,
            minimum = 0,
        )
        val skillChoice = if (base != null) {
            base.skillChoice.copy(count = skillCount, minimumCount = skillCount)
        } else {
            ChoiceDefinition(
                id = "${id.value}:choice:skills",
                title = if (language == AppLanguage.ITALIAN) {
                    "Scegli $skillCount competenze nelle abilità"
                } else {
                    "Choose $skillCount Skill proficiencies"
                },
                kind = ChoiceKind.SKILL_PROFICIENCY,
                count = skillCount,
                poolId = if (skillCount == 0) null else "$packId:pool:skills:any",
            )
        }
        val weaponTrainingGrant = WeaponTrainingGrant(
            categories = attributes["weaponCategories"]
                ?.parseEnums<WeaponCategory>(entity, "weaponCategories")
                ?: base?.weaponTrainingGrant?.categories.orEmpty(),
            martialPropertyFilter = attributes["martialWeaponProperties"]
                ?.parseEnums<WeaponProperty>(entity, "martialWeaponProperties")
                ?: base?.weaponTrainingGrant?.martialPropertyFilter.orEmpty(),
        )
        fun armor(prefix: String, fallback: ArmorTrainingGrant): ArmorTrainingGrant = ArmorTrainingGrant(
            light = attributes.boolean("${prefix}Light", entity, fallback.light),
            medium = attributes.boolean("${prefix}Medium", entity, fallback.medium),
            heavy = attributes.boolean("${prefix}Heavy", entity, fallback.heavy),
            shields = attributes.boolean("${prefix}Shields", entity, fallback.shields),
        )
        return ClassDefinition(
            id = id,
            name = entity.name().text(language.tag),
            primaryAbilities = primaryAbilities,
            multiclassPrerequisiteGroups = multiclassPrerequisites,
            hitDieSides = attributes.int("hitDieSides", entity, base?.hitDieSides ?: 8, minimum = 2),
            fixedHitPointsPerLevel = attributes.int(
                "fixedHitPointsPerLevel",
                entity,
                base?.fixedHitPointsPerLevel ?: 5,
                minimum = 1,
            ),
            savingThrowProficiencies = saves,
            skillChoice = skillChoice,
            weaponTraining = attributes["weaponTraining"] ?: base?.weaponTraining.orEmpty(),
            weaponTrainingGrant = weaponTrainingGrant,
            armorTraining = armor("armorTraining", base?.armorTraining ?: ArmorTrainingGrant()),
            toolChoice = base?.toolChoice,
            startingWeaponChoice = base?.startingWeaponChoice,
            startingEquipmentChoice = base?.startingEquipmentChoice,
            multiclassSkillChoice = base?.multiclassSkillChoice,
            multiclassToolChoice = base?.multiclassToolChoice,
            startingEquipment = attributes["startingEquipment"] ?: base?.startingEquipment.orEmpty(),
            multiclassWeaponTraining = base?.multiclassWeaponTraining.orEmpty(),
            multiclassArmorTraining = armor(
                "multiclassArmorTraining",
                base?.multiclassArmorTraining ?: ArmorTrainingGrant(),
            ),
            subclassIds = subclassIds,
            subclassLevel = subclassLevel,
            spellcastingAbility = spellcastingAbility,
            spellcastingKind = spellcastingKind,
            levels = levels,
            resources = base?.resources.orEmpty(),
        )
    }

    private fun classId(entity: RuleEntity): CharacterClassId =
        entity.attributes()["classId"]
            ?.takeIf(String::isNotBlank)
            ?.let(CharacterClassId::of)
            ?: CharacterClassId.of(entity.id())

    /**
     * La provenienza viene riconosciuta dalle entità, non dal solo hash della
     * base: una revisione importata resta proiettabile anche senza avere tutta
     * la propria genealogia installata. Una collisione di ID in un pack
     * autonomo non basta invece a trascinare dentro lo SRD.
     */
    fun inheritsSrdContent(revision: RulesetRevision): Boolean = revision.entities().any { entity ->
        entity.id() in srdEntityIds &&
            (entity.origin() == RulesetOrigin.BUNDLED_STANDARD || entity.derivedFrom() in srdEntityIds)
    }

    private val srdEntityIds: Set<String> by lazy {
        Srd521Ruleset.revision.entities().mapTo(linkedSetOf(), RuleEntity::id)
    }

    private fun modifier(entity: RuleEntity, language: AppLanguage): ProjectedModifier {
        val attributes = entity.attributes()
        val owner = attributes["ownerRef"]?.trim().orEmpty()
        require(owner.isNotEmpty()) { "${entity.id()}.ownerRef is required" }
        return ProjectedModifier(
            entityId = entity.id(),
            ownerRef = owner,
            minimumLevel = attributes.int("minimumLevel", entity, 1, minimum = 1),
            effect = RuleEffect(
                target = attributes.required("target", entity).parseEnum(entity, "target"),
                amount = attributes.required("amount", entity).toIntOrNull()
                    ?: error("${entity.id()}.amount must be an integer"),
                condition = attributes["condition"]
                    ?.parseEnum<EffectCondition>(entity, "condition")
                    ?: EffectCondition.ALWAYS,
                source = entity.name().text(language.tag),
                group = attributes["group"].orEmpty(),
            ),
        )
    }

    private data class ProjectedModifier(
        val entityId: String,
        val ownerRef: String,
        val minimumLevel: Int,
        val effect: RuleEffect,
    )
}

private fun Map<String, String>.required(key: String, entity: RuleEntity): String =
    this[key]?.takeIf(String::isNotBlank)
        ?: error("${entity.id()}.$key is required")

private fun Map<String, String>.int(
    key: String,
    entity: RuleEntity,
    default: Int,
    minimum: Int,
): Int {
    val raw = this[key]?.takeIf(String::isNotBlank) ?: return default
    val value = raw.toIntOrNull()
        ?: error("${entity.id()}.$key must be an integer")
    require(value >= minimum) { "${entity.id()}.$key must be at least $minimum" }
    return value
}

private fun Map<String, String>.boolean(
    key: String,
    entity: RuleEntity,
    default: Boolean,
): Boolean = this[key]?.takeIf(String::isNotBlank)?.toBooleanStrictOrNull()
    ?: if (this[key].isNullOrBlank()) default else error("${entity.id()}.$key must be true or false")

private inline fun <reified T : Enum<T>> String.parseEnum(entity: RuleEntity, field: String): T =
    enumValues<T>().firstOrNull { it.name.equals(trim(), ignoreCase = true) }
        ?: error("${entity.id()}.$field has unsupported value '$this'")

private fun String.parseAbility(entity: RuleEntity, field: String): Ability =
    runCatching { Ability.of(this) }
        .getOrElse { error("${entity.id()}.$field has invalid ability id '$this'") }

private fun String.parseAbilities(entity: RuleEntity, field: String): Set<Ability> =
    splitCsv().mapTo(linkedSetOf()) { it.parseAbility(entity, field) }.also {
        require(it.isNotEmpty()) { "${entity.id()}.$field cannot be empty" }
    }

private fun String.parseAbilityGroups(entity: RuleEntity, field: String): List<Set<Ability>> =
    split(';').filter(String::isNotBlank).map { group ->
        group.parseAbilities(entity, field)
    }.also {
        require(it.isNotEmpty()) { "${entity.id()}.$field cannot be empty" }
    }

private inline fun <reified T : Enum<T>> String.parseEnums(
    entity: RuleEntity,
    field: String,
): Set<T> = splitCsv().mapTo(linkedSetOf()) { it.parseEnum(entity, field) }

private fun String.parseLevelFeatures(entity: RuleEntity, maximumLevel: Int): Map<Int, List<String>> =
    if (isBlank()) {
        emptyMap()
    } else {
        split(';').filter(String::isNotBlank).associate { row ->
            val level = row.substringBefore(':').trim().toIntOrNull()
                ?: error("${entity.id()}.levelFeatureIds has invalid level in '$row'")
            require(level in 1..maximumLevel) {
                "${entity.id()}.levelFeatureIds level $level is outside 1..$maximumLevel"
            }
            level to row.substringAfter(':', "").splitCsv()
        }
    }

private fun String.splitCsv(): List<String> =
    split(',').map(String::trim).filter(String::isNotBlank).distinct()
