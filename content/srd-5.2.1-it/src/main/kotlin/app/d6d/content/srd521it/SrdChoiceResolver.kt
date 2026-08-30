package app.d6d.content.srd521it

import app.d6d.domain.combat.DamageType
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.rules.character.Skill
import app.d6d.rules.character.WeaponDefinition
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.Proficiency
import app.d6d.sheet.reflowRulesText

data class SrdChoiceOption(
    val id: String,
    val label: String,
    val description: String = "",
    val secondaryLabel: String = "",
    /** Effetti numerici che scegliere questa opzione applica alle statistiche. */
    val effects: List<RuleEffect> = emptyList(),
)

object SrdChoiceResolver {
    /** Etichetta corrente seguita dagli alias delle altre edizioni, per dati salvati portabili. */
    fun labelsForId(id: String, language: AppLanguage): List<String> =
        (listOf(language) + AppLanguage.entries.filterNot { it == language })
            .map { targetLanguage ->
                val targetPack = Srd521ItContent.packFor(targetLanguage)
                optionForId(id, targetPack.element(id), targetLanguage, targetPack).label
            }
            .filter(String::isNotBlank)
            .distinct()

    fun options(
        choice: ChoiceDefinition,
        classId: CharacterClassId,
        classLevel: Int,
        sheet: CharacterSheet,
        provisionalSelections: List<ChoiceSelection> = emptyList(),
        language: AppLanguage = AppLanguage.ITALIAN,
        pack: RulesContentPack = Srd521ItContent.packFor(language),
    ): List<SrdChoiceOption> {
        val provisionalBackground = provisionalSelections
            .asSequence()
            .flatMap { it.optionIds.asSequence() }
            .mapNotNull(pack::background)
            .firstOrNull()
        val otherProvisionalIds = buildSet {
            provisionalSelections
                .filterNot { it.choiceId == choice.id }
                .forEach { addAll(it.optionIds) }
            provisionalBackground?.skillProficiencies?.forEach { add(skillId(it, pack)) }
            provisionalBackground?.toolChoice?.optionIds?.let(::addAll)
            provisionalBackground?.featId?.let(::add)
        }
        if (choice.optionIds.isNotEmpty()) {
            return choice.optionIds
                .filter { id ->
                    when (choice.kind) {
                        ChoiceKind.SKILL_PROFICIENCY ->
                            id.toSkillOrNull(pack)?.let {
                                sheet.skillProficiencies[it].let { proficiency ->
                                    proficiency == null || proficiency == Proficiency.NONE
                                }
                            } != false && id !in otherProvisionalIds
                        ChoiceKind.EXPERTISE ->
                            id.toSkillOrNull(pack)?.let {
                                (
                                    sheet.skillProficiencies[it] == Proficiency.PROFICIENT ||
                                        skillId(it, pack) in otherProvisionalIds
                                    ) &&
                                    sheet.skillProficiencies[it] != Proficiency.EXPERTISE
                            } != false
                        ChoiceKind.LANGUAGE_PROFICIENCY ->
                            labelsForId(id, language)
                                .none(sheet.languages::containsListedEntry) &&
                                id !in otherProvisionalIds
                        ChoiceKind.FEAT, ChoiceKind.EPIC_BOON ->
                            id !in otherProvisionalIds &&
                                (id !in sheet.progression.featIds || id in repeatableIds)
                        ChoiceKind.CLASS_OPTION,
                        ChoiceKind.FIGHTING_STYLE,
                        ChoiceKind.METAMAGIC,
                        ChoiceKind.ELDRITCH_INVOCATION,
                        ChoiceKind.WEAPON_MASTERY,
                        -> id !in sheet.progression.selectedFeatureIds
                        else -> true
                    }
                }
                .map { id -> optionForId(id, pack.element(id), language, pack) }
        }
        val semantic = choice.poolId.orEmpty().removePrefix("${pack.manifest.id}:pool:")
        val elements = pack.elements
        val resolved = when {
            semantic.startsWith("skills:") -> {
                val skills = if (semantic.endsWith(":proficient")) {
                    (
                        sheet.skillProficiencies
                            .filterValues { it == Proficiency.PROFICIENT }
                            .keys +
                            otherProvisionalIds.mapNotNull { it.toSkillOrNull(pack) }
                        )
                        .filter { sheet.skillProficiencies[it] != Proficiency.EXPERTISE }
                        .distinct()
                } else {
                    pack.skills.map { it.id }.filter {
                        sheet.skillProficiencies[it].let { proficiency ->
                            proficiency == null || proficiency == Proficiency.NONE
                        } && skillId(it, pack) !in otherProvisionalIds
                    }
                }
                return skills.map {
                    SrdChoiceOption(
                        id = skillId(it, pack),
                        label = pack.skill(it)?.name ?: it.label(language),
                        secondaryLabel = pack.stat(pack.skill(it)?.statId ?: it.ability)?.name
                            ?: it.ability.label(language),
                    )
                }
            }
            semantic.startsWith("skills-or-tools:") -> {
                val skillOptions = pack.skills.map { it.id }
                    .filter {
                        sheet.skillProficiencies[it].let { proficiency ->
                            proficiency == null || proficiency == Proficiency.NONE
                        } && skillId(it, pack) !in otherProvisionalIds
                    }
                    .map {
                        SrdChoiceOption(
                            id = skillId(it, pack),
                            label = pack.skill(it)?.name ?: it.label(language),
                            secondaryLabel = language.pick("Abilità", "Skill") +
                                " · ${pack.stat(pack.skill(it)?.statId ?: it.ability)?.name ?: it.ability.label(language)}",
                        )
                    }
                return skillOptions + toolOptions(
                    artisanTools + musicalInstruments + otherTools,
                    language,
                    otherProvisionalIds,
                    sheet.toolProficiencies,
                )
            }
            semantic == "tools:musical-instruments" -> return toolOptions(
                musicalInstruments,
                language,
                otherProvisionalIds,
                sheet.toolProficiencies,
            )
            semantic == "tools:artisan-or-musical" ->
                return toolOptions(
                    artisanTools + musicalInstruments,
                    language,
                    otherProvisionalIds,
                    sheet.toolProficiencies,
                )
            semantic == "tools:any" -> return toolOptions(
                artisanTools + musicalInstruments + otherTools,
                language,
                otherProvisionalIds,
                sheet.toolProficiencies,
            )
            semantic == "tools:gaming-set" -> return toolOptions(
                gamingSets,
                language,
                otherProvisionalIds,
                sheet.toolProficiencies,
            )
            semantic.startsWith("weapons:mastery:") -> return SrdWeapons.all(language)
                .map { weaponOption(it, language) }
                .filter { it.id !in sheet.progression.selectedFeatureIds }
            semantic == "feats:general" -> elements.filter {
                it.kind == RuleElementKind.GENERAL_FEAT || it.kind == RuleElementKind.ORIGIN_FEAT
            }
            semantic == "feats:origin" -> elements.filter {
                it.kind == RuleElementKind.ORIGIN_FEAT
            }
            semantic == "feats:epic-or-other" -> elements.filter {
                it.kind in featKinds
            }
            semantic == "eldritch-invocations:warlock" -> elements.filter {
                val availableFeatures = sheet.progression.selectedFeatureIds + otherProvisionalIds
                it.kind == RuleElementKind.ELDRITCH_INVOCATION &&
                    it.classEligibility.any { eligibility ->
                        eligibility.classId == classId && classLevel >= eligibility.minimumLevel
                    } &&
                    invocationPrerequisitesMet(it.id, availableFeatures)
            }
            semantic == "spells:bardo:magical-discoveries" -> elements.filter { element ->
                val spell = element.spell ?: return@filter false
                spell.level in 0..maximumSpellLevel(classId, classLevel, language) &&
                    element.classEligibility.any {
                        it.classId in magicalDiscoveryClasses
                    }
            }
            semantic == "spells:bardo:magical-secrets" -> elements.filter { element ->
                val spell = element.spell ?: return@filter false
                spell.level in 1..maximumSpellLevel(classId, classLevel, language) &&
                    element.classEligibility.any {
                        it.classId in magicalSecretsClasses
                    }
            }
            semantic == "spells:mago:evocation" -> elements.filter { element ->
                val spell = element.spell ?: return@filter false
                spell.level > 0 &&
                    spell.school.equals(language.pick("Invocazione", "Evocation"), ignoreCase = true) &&
                    element.classEligibility.any { eligibility ->
                        eligibility.classId == CharacterClassId.WIZARD
                    } &&
                    spell.level <= maximumSpellLevel(classId, classLevel, language)
            }
            semantic.startsWith("spells:magic-initiate:") -> {
                val requiredLevel = if (semantic.endsWith(":cantrip")) 0 else 1
                val chosenList = provisionalSelections
                    .firstOrNull { it.choiceId.endsWith(":magic-initiate:list") }
                    ?.optionIds?.singleOrNull()?.substringAfterLast(':')
                val chosenClass = when (chosenList) {
                    "chierico" -> CharacterClassId.CLERIC
                    "druido" -> CharacterClassId.DRUID
                    "mago" -> CharacterClassId.WIZARD
                    else -> null
                }
                elements.filter { element ->
                    element.spell?.level == requiredLevel &&
                        element.classEligibility.any {
                            (chosenClass == null && it.classId in magicalDiscoveryClasses) ||
                                it.classId == chosenClass
                        }
                }
            }
            semantic == "spells:any:cantrip" -> elements.filter {
                it.kind == RuleElementKind.CANTRIP
            }
            semantic == "spells:any:1:ritual" -> elements.filter {
                it.spell?.let { spell -> spell.level == 1 && spell.ritual } == true
            }
            semantic.startsWith("spells:") -> {
                val exactLevel = semantic.substringAfterLast(':').toIntOrNull()
                val spellListClass = semantic.substringAfter("spells:")
                    .substringBefore(':')
                    .let { slug ->
                        CharacterClassId.entries.firstOrNull { it.contentId == slug }
                    }
                elements.filter { element ->
                    val spell = element.spell ?: return@filter false
                    val eligible = element.classEligibility.any {
                        it.classId == (spellListClass ?: classId) &&
                            classLevel >= it.minimumLevel
                    }
                    eligible && when {
                        semantic.endsWith(":cantrip") -> spell.level == 0
                        exactLevel != null -> spell.level == exactLevel
                        else -> spell.level in 1..maximumSpellLevel(classId, classLevel, language)
                    }
                }
            }
            semantic.startsWith("beasts:") -> return SrdBeasts.availableAt(classLevel, language)
                .map { form ->
                    SrdChoiceOption(
                        id = form.id,
                        label = form.name,
                        description = form.summary,
                        secondaryLabel = "SRD p. ${form.sourcePage}",
                    )
                }
                .filter { it.id !in sheet.progression.selectedFeatureIds }
            semantic.startsWith("known-cantrips:warlock:") -> {
                val knownIds = sheet.progression.knownCantripIds +
                    provisionalSelections.flatMap { it.optionIds }
                val eligibleIds = if (semantic.endsWith(":range")) {
                    invocationRangeCantripIds
                } else {
                    invocationDamageCantripIds
                }
                elements.filter { element ->
                    element.id in knownIds &&
                        element.kind == RuleElementKind.CANTRIP &&
                        element.id in eligibleIds
                }
            }
            else -> emptyList()
        }
        val isWizardPreparedChoice =
            classId == CharacterClassId.WIZARD &&
                choice.kind == ChoiceKind.PREPARED_SPELL &&
                choice.id.startsWith("${CharacterClassId.WIZARD.contentId}:") &&
                choice.id.endsWith(":prepared-spells")
        val isWizardBookFeatureChoice =
            classId == CharacterClassId.WIZARD &&
                (
                    (
                        choice.kind == ChoiceKind.ALWAYS_PREPARED_SPELL &&
                            choice.id.contains(":maestria-incantesimo-")
                        ) ||
                        choice.id.endsWith(":incantesimi-personali")
                    )
        val wizardBookOptions = if (isWizardPreparedChoice || isWizardBookFeatureChoice) {
            val availableBookIds = sheet.progression.spellbookSpellIds +
                provisionalSelections
                    .filter { selection ->
                        selection.choiceId.endsWith(":spellbook")
                    }
                    .flatMap { it.optionIds }
            resolved.filter { it.id in availableBookIds }
        } else {
            resolved
        }
        val wizardCastingTimeOptions = if (
            isWizardBookFeatureChoice && choice.id.contains(":maestria-incantesimo-")
        ) {
            wizardBookOptions.filter {
                it.spell?.castingTime?.trim()?.lowercase() == language.pick("azione", "action")
            }
        } else {
            wizardBookOptions
        }
        val alreadySelected = when (choice.kind) {
            ChoiceKind.FEAT, ChoiceKind.EPIC_BOON -> sheet.progression.featIds
            ChoiceKind.CANTRIP -> sheet.progression.knownCantripIds
            ChoiceKind.MAGICAL_DISCOVERY ->
                sheet.progression.knownCantripIds + sheet.progression.alwaysPreparedSpellIds
            ChoiceKind.ALWAYS_PREPARED_SPELL -> sheet.progression.alwaysPreparedSpellIds
            ChoiceKind.PREPARED_SPELL ->
                sheet.progression.preparedSpellIds + sheet.progression.alwaysPreparedSpellIds
            ChoiceKind.SPELLBOOK_SPELL -> sheet.progression.spellbookSpellIds
            ChoiceKind.CLASS_OPTION,
            ChoiceKind.FIGHTING_STYLE,
            ChoiceKind.METAMAGIC,
            ChoiceKind.ELDRITCH_INVOCATION,
            ChoiceKind.WEAPON_MASTERY,
            -> sheet.progression.selectedFeatureIds
            else -> emptyList()
        }
        val provisionalAcquisitions = provisionalSelections
            .filterNot { it.choiceId == choice.id }
            .flatMap { selection ->
                selection.optionIds.mapNotNull { optionId ->
                    provisionalAcquisitionBucket(selection.choiceId, optionId, pack)
                        ?.let { bucket -> bucket to optionId }
                }
            }
            .toSet()
        val previouslyBoundTargets = if (choice.kind == ChoiceKind.FEATURE_TARGET) {
            val targetSuffix = choice.id.substringAfterLast(":suppliche-occulte:")
                .substringBeforeLast(":target")
            sheet.progression.selections
                .filter {
                    it.choiceId.endsWith(":$targetSuffix:target")
                }
                .flatMapTo(mutableSetOf()) { it.optionIds }
        } else {
            emptySet()
        }
        val previousAncientKnowledgeFeats =
            if (choice.id.contains(":conoscenze-degli-antichi:talento")) {
                sheet.progression.selections
                    .filter { it.choiceId.contains(":conoscenze-degli-antichi:talento") }
                    .flatMapTo(mutableSetOf()) { it.optionIds }
            } else {
                emptySet()
            }
        return wizardCastingTimeOptions
            .filter {
                isWizardBookFeatureChoice ||
                    it.id !in alreadySelected ||
                    it.id in repeatableIds
            }
            // I talenti ripetibili possono essere acquisiti di nuovo in un
            // avanzamento successivo. Nello stesso request, invece, i dati figli
            // di Abile, Iniziato alla magia e ASI non sono ancora namespaced per
            // acquisizione: offrire due volte lo stesso talento produrrebbe una
            // richiesta non rappresentabile senza ambiguita'.
            .filter {
                (
                    choice.kind != ChoiceKind.FEAT &&
                        choice.kind != ChoiceKind.EPIC_BOON
                ) ||
                    it.id !in otherProvisionalIds
            }
            .filter { element ->
                acquisitionBucket(choice.kind, element)?.let { bucket ->
                    bucket to element.id !in provisionalAcquisitions
                } ?: true
            }
            .filter { it.id !in previouslyBoundTargets }
            .filter { it.id !in previousAncientKnowledgeFeats }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.spell?.level ?: -1 }, { it.name.lowercase() }))
            .map { optionForId(it.id, it, language, pack) }
    }

    private fun maximumSpellLevel(
        classId: CharacterClassId,
        classLevel: Int,
        language: AppLanguage,
    ): Int {
        val level = SrdClasses.all(language).first { it.id == classId }.level(classLevel)
        return if (level.pactSlotLevel > 0) {
            level.pactSlotLevel
        } else {
            level.spellSlots.indexOfLast { it > 0 } + 1
        }
    }

    private fun optionForId(
        id: String,
        element: RuleElementDefinition?,
        language: AppLanguage,
        pack: RulesContentPack,
    ): SrdChoiceOption {
        pack.background(id)?.let { background ->
            return SrdChoiceOption(
                id = id,
                label = background.name,
                description = background.description,
                secondaryLabel = language.pick("Background SRD", "SRD background") +
                    " · p. ${background.sourcePage}",
            )
        }
        pack.equipmentPackage(id)?.let { equipment ->
            return SrdChoiceOption(
                id = id,
                label = equipment.name,
                description = equipment.description,
                secondaryLabel = if (equipment.goldPieces > 0) {
                    "${equipment.goldPieces} ${language.pick("mo", "gp")}"
                } else {
                    language.pick("Dotazione", "Equipment")
                },
            )
        }
        if (element != null) {
            return SrdChoiceOption(
                id = id,
                label = element.name,
                // Il testo del pacchetto e' spezzato a mano sulla larghezza del
                // sorgente: senza ricomporlo la finestra guidata mostrerebbe gli
                // a capo dove finisce la riga di codice, non dove finisce la frase.
                description = element.description.reflowRulesText(),
                secondaryLabel = element.spell?.let {
                    SrdWords.of(language).spellLevelTag(it.level, it.school)
                } ?: element.prerequisite,
                effects = element.effects,
            )
        }
        val skill = pack.skills.map { it.id }.firstOrNull { skillId(it, pack) == id }
        if (skill != null) {
            return SrdChoiceOption(
                id = id,
                label = pack.skill(skill)?.name ?: skill.label(language),
                secondaryLabel = pack.stat(pack.skill(skill)?.statId ?: skill.ability)?.name
                    ?: skill.ability.label(language),
            )
        }
        SrdWeapons.byId(id, language)?.let { weapon ->
            return SrdChoiceOption(
                id = id,
                label = weapon.name,
                description = weapon.summary(language),
                secondaryLabel = weapon.damageText(language),
            )
        }
        localizedToolsById[id]?.let { return toolOption(it, language) }
        localizedLanguagesById[id]?.let { knownLanguage ->
            return SrdChoiceOption(
                id = id,
                label = language.pick(knownLanguage.italian, knownLanguage.english),
                secondaryLabel = language.pick("Lingua standard", "Standard language"),
            )
        }
        damageTypesById[id]?.let { damageType ->
            return SrdChoiceOption(id = id, label = damageType.label(language))
        }
        if (id.startsWith("srd521-it:ability:")) {
            val ability = pack.stats.map { it.id }.firstOrNull {
                it.name.lowercase() == id.substringAfterLast(':') ||
                    it.value.substringAfterLast(':').replace('-', '_').lowercase() == id.substringAfterLast(':')
            }
            if (ability != null) {
                val definition = pack.stat(ability)
                return SrdChoiceOption(
                    id,
                    definition?.name ?: ability.label(language),
                    definition?.abbreviation ?: ability.abbreviation,
                )
            }
        }
        if (id.startsWith("srd521-it:spell-list:")) {
            val classId = CharacterClassId.entries.firstOrNull {
                it.contentId == id.substringAfterLast(':')
            }
            return SrdChoiceOption(
                id,
                classId?.label(language)
                    ?: id.substringAfterLast(':').replaceFirstChar { it.uppercase() },
                language.pick("Lista degli incantesimi", "Spell list"),
            )
        }
        return SrdChoiceOption(
            id = id,
            label = id.substringAfterLast(':').replace('-', ' ').replaceFirstChar { it.uppercase() },
        )
    }
}

private fun acquisitionBucket(
    kind: ChoiceKind,
    element: RuleElementDefinition,
): String? = when (kind) {
    ChoiceKind.CANTRIP -> "cantrip"
    ChoiceKind.PREPARED_SPELL,
    ChoiceKind.ALWAYS_PREPARED_SPELL,
    -> "prepared"
    ChoiceKind.MAGICAL_DISCOVERY ->
        if (element.spell?.level == 0) "cantrip" else "prepared"
    ChoiceKind.SPELLBOOK_SPELL -> "spellbook"
    else -> null
}

private fun provisionalAcquisitionBucket(
    choiceId: String,
    optionId: String,
    pack: app.d6d.rules.character.RulesContentPack,
): String? {
    val spellLevel = pack.element(optionId)?.spell?.level
    return when {
        choiceId.endsWith(":cantrips") -> "cantrip"
        choiceId.endsWith(":scoperte-magiche") ->
            if (spellLevel == 0) "cantrip" else "prepared"
        choiceId.endsWith(":spellbook") || choiceId.contains(":invocatore-sapiente") -> "spellbook"
        choiceId.endsWith(":prepared-spells") ||
            choiceId.endsWith(":rituals") ||
            choiceId.endsWith(":magic-initiate:spell") ||
            choiceId.endsWith(":incantesimi-personali") ||
            choiceId.contains(":maestria-incantesimo-") ||
            choiceId.contains(":arcanum-mistico-") -> "prepared"
        else -> null
    }
}

private fun skillId(skill: Skill, pack: RulesContentPack): String =
    skill.value.takeIf { ':' in it }
        ?: "${pack.manifest.id}:skill:${skill.name.lowercase().replace('_', '-')}"

private fun String.toSkillOrNull(pack: RulesContentPack): Skill? {
    val slug = substringAfterLast(':').replace('-', '_')
    return pack.skills.firstOrNull { definition ->
        definition.id.name.lowercase().substringAfterLast(':').replace('-', '_') == slug ||
            definition.name.toContentSlug().replace('-', '_') == slug
    }?.id
}

private fun invocationPrerequisitesMet(
    invocationId: String,
    availableFeatureIds: Collection<String>,
): Boolean {
    fun has(slug: String) = availableFeatureIds.any { it.endsWith(":$slug") }
    return when {
        invocationId.endsWith(":dono-del-protettore") -> has("patto-del-tomo")
        invocationId.endsWith(":investitura-del-signore-delle-catene") ->
            has("patto-della-catena")
        invocationId.endsWith(":lama-assetata") ||
            invocationId.endsWith(":punizione-occulta") ||
            invocationId.endsWith(":succhiavita") -> has("patto-della-lama")
        invocationId.endsWith(":lama-divoratrice") -> has("lama-assetata")
        else -> true
    }
}

private fun toolOption(tool: LocalizedTool, language: AppLanguage) =
    SrdChoiceOption(
        tool.id,
        language.pick(tool.italian, tool.english),
        SrdWords.of(language).toolProficiency,
    )

private fun toolOptions(
    tools: List<LocalizedTool>,
    language: AppLanguage,
    unavailableIds: Set<String>,
    ownedLabels: String,
): List<SrdChoiceOption> = tools
    .filter { tool ->
        tool.id !in unavailableIds &&
            !ownedLabels.containsListedEntry(tool.italian) &&
            !ownedLabels.containsListedEntry(tool.english)
    }
    .map { toolOption(it, language) }

private fun String.containsListedEntry(label: String): Boolean =
    split(',', ';', '\n').any { it.trim().equals(label, ignoreCase = true) }

private fun weaponOption(weapon: WeaponDefinition, language: AppLanguage) =
    SrdChoiceOption(weapon.id, weapon.name, weapon.summary(language), weapon.mastery)

private val magicalDiscoveryClasses = setOf(
    CharacterClassId.CLERIC,
    CharacterClassId.DRUID,
    CharacterClassId.WIZARD,
)

private val magicalSecretsClasses = magicalDiscoveryClasses + CharacterClassId.BARD

private val invocationDamageCantripIds = setOf(
    "srd521-it:spell:colpo-accurato",
    "srd521-it:spell:deflagrazione-occulta",
    "srd521-it:spell:spruzzo-velenoso",
    "srd521-it:spell:tocco-gelido",
)

private val invocationRangeCantripIds = setOf(
    "srd521-it:spell:deflagrazione-occulta",
    "srd521-it:spell:spruzzo-velenoso",
)

private val featKinds = setOf(
    RuleElementKind.ORIGIN_FEAT,
    RuleElementKind.GENERAL_FEAT,
    RuleElementKind.FIGHTING_STYLE_FEAT,
    RuleElementKind.EPIC_BOON_FEAT,
)

private val repeatableIds = setOf(
    "srd521-it:feat:origin:abile",
    "srd521-it:feat:origin:iniziato-alla-magia",
    "srd521-it:feat:general:aumento-punteggi-caratteristica",
    "srd521-it:feature:warlock:conoscenze-degli-antichi",
    "srd521-it:feature:warlock:deflagrazione-agonizzante",
    "srd521-it:feature:warlock:deflagrazione-respingente",
    "srd521-it:feature:warlock:lancia-occulta",
)

private data class LocalizedTool(val italian: String, val english: String) {
    val id: String get() = "srd521-it:tool:${italian.toContentSlug()}"

    fun name(language: AppLanguage): String = language.pick(italian, english)
}

private val artisanTools = listOf(
    LocalizedTool("Scorte da alchimista", "Alchemist's Supplies"),
    LocalizedTool("Scorte da birraio", "Brewer's Supplies"),
    LocalizedTool("Scorte da calligrafo", "Calligrapher's Supplies"),
    LocalizedTool("Strumenti da falegname", "Carpenter's Tools"),
    LocalizedTool("Strumenti da cartografo", "Cartographer's Tools"),
    LocalizedTool("Strumenti da calzolaio", "Cobbler's Tools"),
    LocalizedTool("Utensili da cuoco", "Cook's Utensils"),
    LocalizedTool("Strumenti da soffiatore", "Glassblower's Tools"),
    LocalizedTool("Strumenti da gioielliere", "Jeweler's Tools"),
    LocalizedTool("Strumenti da conciatore", "Leatherworker's Tools"),
    LocalizedTool("Strumenti da muratore", "Mason's Tools"),
    LocalizedTool("Strumenti da pittore", "Painter's Supplies"),
    LocalizedTool("Strumenti da vasaio", "Potter's Tools"),
    LocalizedTool("Strumenti da fabbro", "Smith's Tools"),
    LocalizedTool("Strumenti da inventore", "Tinker's Tools"),
    LocalizedTool("Strumenti da tessitore", "Weaver's Tools"),
    LocalizedTool("Strumenti da intagliatore", "Woodcarver's Tools"),
)

private val musicalInstruments = listOf(
    LocalizedTool("Cornamusa", "Bagpipes"), LocalizedTool("Tamburo", "Drum"),
    LocalizedTool("Dulcimer", "Dulcimer"), LocalizedTool("Flauto", "Flute"),
    LocalizedTool("Corno", "Horn"), LocalizedTool("Liuto", "Lute"),
    LocalizedTool("Lira", "Lyre"), LocalizedTool("Flauto di Pan", "Pan Flute"),
    LocalizedTool("Ciaramella", "Shawm"), LocalizedTool("Viola", "Viol"),
)

private val otherTools = listOf(
    LocalizedTool("Arnesi da scasso", "Thieves' Tools"),
    LocalizedTool("Arnesi da falsario", "Forgery Kit"),
    LocalizedTool("Borsa da erborista", "Herbalism Kit"),
    LocalizedTool("Sostanze da avvelenatore", "Poisoner's Kit"),
    LocalizedTool("Strumenti da navigatore", "Navigator's Tools"),
    LocalizedTool("Trucchi per il camuffamento", "Disguise Kit"),
    LocalizedTool("Dadi", "Dice Set"), LocalizedTool("Scacchi dei draghi", "Dragonchess Set"),
    LocalizedTool("Carte da gioco", "Playing Card Set"),
    LocalizedTool("Tre draghi al buio", "Three-Dragon Ante Set"),
)

private val gamingSets = listOf(
    LocalizedTool("Dadi", "Dice Set"), LocalizedTool("Scacchi dei draghi", "Dragonchess Set"),
    LocalizedTool("Carte da gioco", "Playing Card Set"),
    LocalizedTool("Tre draghi al buio", "Three-Dragon Ante Set"),
)

private val localizedToolsById: Map<String, LocalizedTool> =
    (artisanTools + musicalInstruments + otherTools + gamingSets)
        .distinctBy { it.id }
        .associateBy { it.id }

private data class LocalizedLanguage(val italian: String, val english: String) {
    val id: String get() = "srd521-it:language:${italian.toContentSlug()}"

    fun name(language: AppLanguage): String = language.pick(italian, english)
}

private val localizedLanguagesById: Map<String, LocalizedLanguage> = listOf(
    LocalizedLanguage("Comune", "Common"),
    LocalizedLanguage("Lingua dei Segni Comune", "Common Sign Language"),
    LocalizedLanguage("Draconico", "Draconic"),
    LocalizedLanguage("Nanico", "Dwarvish"),
    LocalizedLanguage("Elfico", "Elvish"),
    LocalizedLanguage("Gigante", "Giant"),
    LocalizedLanguage("Gnomesco", "Gnomish"),
    LocalizedLanguage("Goblin", "Goblin"),
    LocalizedLanguage("Halfling", "Halfling"),
    LocalizedLanguage("Orchesco", "Orc"),
).associateBy { it.id }

private val damageTypesById = mapOf(
    "srd521-it:damage:acido" to DamageType.ACID,
    "srd521-it:damage:contundente" to DamageType.BLUDGEONING,
    "srd521-it:damage:freddo" to DamageType.COLD,
    "srd521-it:damage:fuoco" to DamageType.FIRE,
    "srd521-it:damage:fulmine" to DamageType.LIGHTNING,
    "srd521-it:damage:necrotico" to DamageType.NECROTIC,
    "srd521-it:damage:perforante" to DamageType.PIERCING,
    "srd521-it:damage:veleno" to DamageType.POISON,
    "srd521-it:damage:psichico" to DamageType.PSYCHIC,
    "srd521-it:damage:radioso" to DamageType.RADIANT,
    "srd521-it:damage:tagliente" to DamageType.SLASHING,
    "srd521-it:damage:tuono" to DamageType.THUNDER,
)

/**
 * Strumenti e lingue, dal nome in una lingua a quello nell'altra.
 *
 * Il vocabolario e' gia' bilingue e con identificativi stabili; questa e' la
 * porta con cui lo raggiunge chi deve riportare nella lingua corrente una scheda
 * scritta in quella precedente. Espone le corrispondenze, non le strutture:
 * cosi' l'elenco resta libero di cambiare forma.
 */
fun srdToolAndLanguageNames(source: AppLanguage, target: AppLanguage): Map<String, String> =
    buildMap {
        localizedToolsById.values.forEach { put(it.name(source), it.name(target)) }
        localizedLanguagesById.values.forEach { put(it.name(source), it.name(target)) }
    }.filterKeys { it.isNotBlank() }
