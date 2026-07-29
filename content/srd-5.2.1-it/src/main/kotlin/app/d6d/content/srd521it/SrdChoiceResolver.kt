package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
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
    fun options(
        choice: ChoiceDefinition,
        classId: CharacterClassId,
        classLevel: Int,
        sheet: CharacterSheet,
        provisionalSelections: List<ChoiceSelection> = emptyList(),
    ): List<SrdChoiceOption> {
        val pack = Srd521ItContent.pack
        val otherProvisionalIds = provisionalSelections
            .filterNot { it.choiceId == choice.id }
            .flatMapTo(mutableSetOf()) { it.optionIds }
        if (choice.optionIds.isNotEmpty()) {
            return choice.optionIds
                .filter { id ->
                    when (choice.kind) {
                        ChoiceKind.SKILL_PROFICIENCY ->
                            id.toSkillOrNull()?.let {
                                sheet.skillProficiencies[it].let { proficiency ->
                                    proficiency == null || proficiency == Proficiency.NONE
                                }
                            } != false && id !in otherProvisionalIds
                        ChoiceKind.EXPERTISE ->
                            id.toSkillOrNull()?.let {
                                (
                                    sheet.skillProficiencies[it] == Proficiency.PROFICIENT ||
                                        skillId(it) in otherProvisionalIds
                                    ) &&
                                    sheet.skillProficiencies[it] != Proficiency.EXPERTISE
                            } != false
                        ChoiceKind.LANGUAGE_PROFICIENCY ->
                            id.substringAfterLast(':') !in sheet.languages
                                .split(',', ';', '\n')
                                .mapTo(mutableSetOf()) { it.toContentSlug() } &&
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
                .map { id -> optionForId(id, pack.element(id)) }
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
                            otherProvisionalIds.mapNotNull { it.toSkillOrNull() }
                        )
                        .filter { sheet.skillProficiencies[it] != Proficiency.EXPERTISE }
                        .distinct()
                } else {
                    Skill.entries.filter {
                        sheet.skillProficiencies[it].let { proficiency ->
                            proficiency == null || proficiency == Proficiency.NONE
                        } && skillId(it) !in otherProvisionalIds
                    }
                }
                return skills.map {
                    SrdChoiceOption(skillId(it), it.italianLabel, it.ability.italianLabel)
                }
            }
            semantic.startsWith("skills-or-tools:") -> return (
                Skill.entries
                    .filter {
                        sheet.skillProficiencies[it].let { proficiency ->
                            proficiency == null || proficiency == Proficiency.NONE
                        } && skillId(it) !in otherProvisionalIds
                    }
                    .map {
                        SrdChoiceOption(
                            skillId(it),
                            it.italianLabel,
                            "Abilità · ${it.ability.italianLabel}",
                        )
                    } + (artisanTools + musicalInstruments + otherTools)
                    .map(::toolOption)
                    .filter {
                        it.id !in otherProvisionalIds &&
                            !sheet.toolProficiencies.containsListedEntry(it.label)
                    }
                )
            semantic == "tools:musical-instruments" -> return musicalInstruments
                .map(::toolOption)
                .filter {
                    it.id !in otherProvisionalIds &&
                        !sheet.toolProficiencies.containsListedEntry(it.label)
                }
            semantic == "tools:artisan-or-musical" ->
                return (artisanTools + musicalInstruments)
                    .map(::toolOption)
                    .filter {
                        it.id !in otherProvisionalIds &&
                            !sheet.toolProficiencies.containsListedEntry(it.label)
                    }
            semantic.startsWith("weapons:mastery:") -> return SrdWeapons.all
                .map(::weaponOption)
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
                spell.level in 0..maximumSpellLevel(classId, classLevel) &&
                    element.classEligibility.any {
                        it.classId in magicalDiscoveryClasses
                    }
            }
            semantic == "spells:bardo:magical-secrets" -> elements.filter { element ->
                val spell = element.spell ?: return@filter false
                spell.level in 1..maximumSpellLevel(classId, classLevel) &&
                    element.classEligibility.any {
                        it.classId in magicalSecretsClasses
                    }
            }
            semantic == "spells:mago:evocation" -> elements.filter { element ->
                val spell = element.spell ?: return@filter false
                spell.level > 0 &&
                    spell.school == "Invocazione" &&
                    element.classEligibility.any { eligibility ->
                        eligibility.classId == CharacterClassId.WIZARD
                    } &&
                    spell.level <= maximumSpellLevel(classId, classLevel)
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
                        else -> spell.level in 1..maximumSpellLevel(classId, classLevel)
                    }
                }
            }
            semantic.startsWith("beasts:") -> return listOf(
                WildShapeOption("topo", "Topo", "GS 0 · senza volo", 2),
                WildShapeOption("cavallo-da-galoppo", "Cavallo da galoppo", "GS 1/4 · senza volo", 2),
                WildShapeOption("ragno", "Ragno", "GS 0 · senza volo", 2),
                WildShapeOption("lupo", "Lupo", "GS 1/4 · senza volo", 2),
                WildShapeOption("coccodrillo", "Coccodrillo", "GS 1/2 · senza volo", 4),
                WildShapeOption("cavallo-da-guerra", "Cavallo da guerra", "GS 1/2 · senza volo", 4),
                WildShapeOption("aquila-gigante", "Aquila gigante", "GS 1 · volo", 8),
                WildShapeOption("gufo-gigante", "Gufo gigante", "GS 1/4 · volo", 8),
            )
                .filter { classLevel >= it.minimumDruidLevel }
                .map { SrdChoiceOption(it.id, it.label, it.description) }
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
            wizardBookOptions.filter { it.spell?.castingTime?.trim()?.lowercase() == "azione" }
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
                    provisionalAcquisitionBucket(selection.choiceId, optionId)
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
            .map { optionForId(it.id, it) }
    }

    private fun maximumSpellLevel(classId: CharacterClassId, classLevel: Int): Int {
        val level = SrdClasses.all.first { it.id == classId }.level(classLevel)
        return if (level.pactSlotLevel > 0) {
            level.pactSlotLevel
        } else {
            level.spellSlots.indexOfLast { it > 0 } + 1
        }
    }

    private fun optionForId(id: String, element: RuleElementDefinition?): SrdChoiceOption {
        if (element != null) {
            return SrdChoiceOption(
                id = id,
                label = element.name,
                // Il testo del pacchetto e' spezzato a mano sulla larghezza del
                // sorgente: senza ricomporlo la finestra guidata mostrerebbe gli
                // a capo dove finisce la riga di codice, non dove finisce la frase.
                description = element.description.reflowRulesText(),
                secondaryLabel = element.spell?.let {
                    if (it.level == 0) "Trucchetto · ${it.school}" else "${it.level}º · ${it.school}"
                } ?: element.prerequisite,
                effects = element.effects,
            )
        }
        val skill = Skill.entries.firstOrNull { skillId(it) == id }
        if (skill != null) return SrdChoiceOption(id, skill.italianLabel, skill.ability.italianLabel)
        SrdWeapons.byId(id)?.let { weapon ->
            return SrdChoiceOption(
                id = id,
                label = weapon.name,
                description = weapon.summary,
                secondaryLabel = weapon.damageText,
            )
        }
        if (id.startsWith("srd521-it:ability:")) {
            val ability = app.d6d.rules.character.Ability.entries.firstOrNull {
                it.name.lowercase() == id.substringAfterLast(':')
            }
            if (ability != null) return SrdChoiceOption(id, ability.italianLabel, ability.abbreviation)
        }
        if (id.startsWith("srd521-it:spell-list:")) {
            return SrdChoiceOption(
                id,
                id.substringAfterLast(':').replaceFirstChar { it.uppercase() },
                "Lista degli incantesimi",
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

private fun provisionalAcquisitionBucket(choiceId: String, optionId: String): String? {
    val spellLevel = Srd521ItContent.pack.element(optionId)?.spell?.level
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

private data class WildShapeOption(
    val slug: String,
    val label: String,
    val description: String,
    val minimumDruidLevel: Int,
) {
    val id: String get() = "srd521-it:beast:$slug"
}

private fun skillId(skill: Skill): String =
    "srd521-it:skill:${skill.name.lowercase().replace('_', '-')}"

private fun String.toSkillOrNull(): Skill? {
    val slug = substringAfterLast(':').replace('-', '_')
    return Skill.entries.firstOrNull { it.name.lowercase() == slug }
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

private fun toolOption(name: String) =
    SrdChoiceOption("srd521-it:tool:${name.toContentSlug()}", name, "Competenza negli strumenti")

private fun String.containsListedEntry(label: String): Boolean =
    split(',', ';', '\n').any { it.trim().equals(label, ignoreCase = true) }

private fun weaponOption(weapon: WeaponDefinition) =
    SrdChoiceOption(weapon.id, weapon.name, weapon.summary, weapon.mastery)

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

private val artisanTools = listOf(
    "Strumenti da alchimista", "Scorte da birraio", "Strumenti da calligrafo",
    "Strumenti da carpentiere", "Strumenti da cartografo", "Strumenti da ciabattino",
    "Utensili da cuoco", "Strumenti da soffiatore di vetro", "Strumenti da gioielliere",
    "Strumenti da conciatore", "Strumenti da muratore", "Strumenti da pittore",
    "Strumenti da vasaio", "Strumenti da fabbro", "Strumenti da stagnino",
    "Strumenti da tessitore", "Strumenti da intagliatore",
)

private val musicalInstruments = listOf(
    "Cornamusa", "Tamburo", "Dulcimer", "Flauto", "Corno",
    "Liuto", "Lira", "Flauto di Pan", "Ciaramella", "Viola",
)

private val otherTools = listOf(
    "Arnesi da scasso", "Arnesi da falsario", "Borsa da erborista",
    "Sostanze da avvelenatore", "Strumenti da navigatore", "Trucchi per il camuffamento",
    "Gioco di dadi", "Scacchi dei draghi", "Carte da gioco", "Tre draghi al buio",
)
