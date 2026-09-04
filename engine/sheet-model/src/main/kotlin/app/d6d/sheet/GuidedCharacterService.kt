package app.d6d.sheet

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick
import app.d6d.domain.combat.DamageType
import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgressionEngine
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceReplacementWindow
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.ProgressionIssue
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.rules.character.SpellcastingKind
import app.d6d.rules.character.StartingArmor
import app.d6d.rules.character.WeaponDefinition
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponReach
import app.d6d.sheet.i18n.distanceUnit
import app.d6d.sheet.i18n.distanceValue

data class RestChoiceReplacement(
    val classId: CharacterClassId,
    val choiceId: String,
    val oldOptionId: String,
    val newOptionId: String,
)

/**
 * Applica una creazione/avanzamento validato alla scheda senza duplicare le
 * tabelle del content pack nella UI.
 */
class GuidedCharacterService(
    val pack: RulesContentPack,
    private val optionLabelsForId: (String) -> List<String> = {
        listOf(defaultOptionLabel(it))
    },
) {
    private val progressionEngine = CharacterProgressionEngine(pack)
    private val language = if (pack.manifest.locale.startsWith("en", ignoreCase = true)) {
        AppLanguage.ENGLISH
    } else {
        AppLanguage.ITALIAN
    }
    private fun say(italian: String, english: String): String = language.pick(italian, english)

    /** Prima viene l'etichetta da salvare; le altre riconoscono una scheda creata nell'altra lingua. */
    private fun optionLabels(id: String): List<String> =
        optionLabelsForId(id).filter(String::isNotBlank).distinct().ifEmpty {
            listOf(defaultOptionLabel(id))
        }

    private fun optionLabel(id: String): String = optionLabels(id).first()

    fun requirements(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        provisionalSelections: List<app.d6d.rules.character.ChoiceSelection> = emptyList(),
    ) = progressionEngine.requirementsFor(sheet.progression, classId, provisionalSelections)

    fun validate(sheet: CharacterSheet, request: LevelUpRequest): app.d6d.rules.character.LevelUpValidation {
        val validation = progressionEngine.validateLevelUp(
            progression = sheet.progression,
            experiencePoints = sheet.experiencePoints,
            abilityScores = sheet.abilityScores,
            request = request,
        )
        val requirements = runCatching {
            progressionEngine.requirementsFor(sheet.progression, request.classId, request.selections)
        }.getOrDefault(emptyList())
        val byId = request.selections.associateBy { it.choiceId }
        val newlyProficientSkillIds = requirements
            .filter {
                it.kind == ChoiceKind.SKILL_PROFICIENCY ||
                    it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
            }
            .flatMapTo(mutableSetOf()) { byId[it.id]?.optionIds.orEmpty() }
        val invalidExpertise = requirements
            .filter { it.kind == ChoiceKind.EXPERTISE }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .filter { id ->
                val skill = id.toSkillOrNull(pack) ?: return@filter true
                sheet.skillProficiencies[skill] != Proficiency.PROFICIENT &&
                    id !in newlyProficientSkillIds
            }
        val duplicateSkillProficiencies = requirements
            .filter {
                it.kind == ChoiceKind.SKILL_PROFICIENCY ||
                    it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
            }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .mapNotNull { id -> id.toSkillOrNull(pack)?.let { id to it } }
            .filter { (_, skill) ->
                sheet.skillProficiencies[skill].let {
                    it != null && it != Proficiency.NONE
                }
            }
            .map { it.first }
        val duplicateToolProficiencies = requirements
            .filter {
                it.kind == ChoiceKind.TOOL_PROFICIENCY ||
                    it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
            }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .filter { it.toSkillOrNull(pack) == null }
            .filter { id -> optionLabels(id).any(sheet.toolProficiencies::containsListedEntry) }
        val duplicateLanguages = requirements
            .filter { it.kind == ChoiceKind.LANGUAGE_PROFICIENCY }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .filter { id -> optionLabels(id).any(sheet.languages::containsListedEntry) }
        val localIssues = buildList {
            if (invalidExpertise.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "EXPERTISE_REQUIRES_PROFICIENCY",
                        say(
                            "Maestria richiede una competenza nelle abilità già posseduta o appena scelta.",
                            "Expertise requires a Skill proficiency you already have or just selected.",
                        ),
                    ),
                )
            }
            if (duplicateSkillProficiencies.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "SKILL_ALREADY_PROFICIENT",
                        say(
                            "Non puoi scegliere di nuovo una competenza nelle abilità già posseduta.",
                            "You can't select a Skill proficiency you already have.",
                        ),
                    ),
                )
            }
            if (duplicateToolProficiencies.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "TOOL_ALREADY_PROFICIENT",
                        say(
                            "Non puoi scegliere di nuovo una competenza negli strumenti già posseduta.",
                            "You can't select a Tool proficiency you already have.",
                        ),
                    ),
                )
            }
            if (duplicateLanguages.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "LANGUAGE_ALREADY_KNOWN",
                        say(
                            "Non puoi scegliere di nuovo una lingua già conosciuta.",
                            "You can't select a language you already know.",
                        ),
                    ),
                )
            }
        }
        return if (localIssues.isEmpty()) {
            validation
        } else {
            validation.copy(
                issues = validation.issues + localIssues,
            )
        }
    }

    fun fixedHitPointIncrease(sheet: CharacterSheet, classId: CharacterClassId): Int {
        val definition = pack.classDefinition(classId)
        val constitution = sheet.modifier(Ability.CONSTITUTION)
        return if (!sheet.progression.configured) {
            (definition.hitDieSides + constitution).coerceAtLeast(1)
        } else {
            (definition.fixedHitPointsPerLevel + constitution).coerceAtLeast(1)
        }
    }

    /**
     * Ricalcola gli effetti dei privilegi ottenuti e delle personalizzazioni.
     *
     * La progressione resta la fonte storica, mentre [CharacterSheet.excludedTraitIds]
     * e i collegamenti manuali nel Compendio fanno da overlay. In questo modo
     * togliere uno stile dalla scheda toglie anche il suo bonus, senza cancellare
     * la scelta dal registro del livello in cui era stata fatta.
     */
    fun withRefreshedEffects(
        sheet: CharacterSheet,
        abilityCatalog: List<CatalogAbility> = emptyList(),
    ): CharacterSheet {
        val catalogById = abilityCatalog.associateBy { it.id }
        /*
         * Alcune scelte della progressione sono dati, non capacità: per esempio
         * l'arma di cui si usa la Padronanza, una forma bestiale conosciuta o il
         * tipo di danno di Resilienza immonda. Le vecchie versioni le copiavano
         * anche in abilityIds, dove l'editor le segnalava come voci di catalogo
         * scomparse. Il registro della progressione resta intatto; si elimina
         * soltanto il collegamento improprio alla sezione Abilità.
         */
        val nonCatalogProgressionIds = sheet.progression.selectedFeatureIds
            .filterTo(mutableSetOf()) { id ->
                pack.element(id) == null && id !in catalogById
            }
        val normalizedSheet = if (nonCatalogProgressionIds.isEmpty()) {
            sheet
        } else {
            sheet.copy(
                abilityIds = sheet.abilityIds.filterNot { it in nonCatalogProgressionIds },
            )
        }
        val baseline = if (normalizedSheet.progression.configured) {
            progressionEngine.deriveEffects(normalizedSheet.progression)
        } else {
            emptyList()
        }

        fun nameOf(id: String): String? =
            catalogById[id]?.name ?: pack.element(id)?.name

        fun effectsOf(id: String): List<RuleEffect> =
            catalogById[id]?.effects ?: pack.element(id)?.effects.orEmpty()

        val excludedEffects = normalizedSheet.excludedTraitIds.flatMap(::effectsOf)
        val excludedSources = buildSet {
            normalizedSheet.excludedTraitIds.mapNotNullTo(this) { nameOf(it)?.effectKey() }
            excludedEffects.mapTo(this) { it.source.effectKey() }
        }
        val excludedGroups = excludedEffects
            .mapNotNullTo(mutableSetOf()) { it.group.takeIf(String::isNotBlank) }
        val retainedBaseline = baseline.filterNot { effect ->
            effect.source.effectKey() in excludedSources ||
                effect.group.isNotBlank() && effect.group in excludedGroups
        }

        val activeTraitIds = (
            normalizedSheet.progression.selectedFeatureIds +
                normalizedSheet.progression.featIds +
                normalizedSheet.abilityIds
            )
            .distinct()
            .filterNot { it in normalizedSheet.excludedTraitIds }
            .filter { id ->
                val kind = catalogById[id]?.category ?: pack.element(id)?.kind
                kind in editableTraitKinds
            }
        val refreshed = mergeEffects(
            retainedBaseline + activeTraitIds.flatMap(::effectsOf),
        )
        if (refreshed == normalizedSheet.progression.effects) return normalizedSheet
        return normalizedSheet.copy(
            progression = normalizedSheet.progression.copy(effects = refreshed),
        )
    }

    /**
     * Toglie l'elenco di nomi che le versioni precedenti scrivevano nei campi di
     * testo di privilegi e talenti.
     *
     * Quei campi ora ospitano le note di chi gioca, mentre l'elenco arriva dalla
     * progressione: lasciarcelo mostrerebbe due volte le stesse voci. Si cancella
     * soltanto se il contenuto e' esattamente quello generato allora — una nota
     * scritta a mano, anche solo aggiunta in fondo, resta intatta.
     */
    fun withoutGeneratedFeatureText(sheet: CharacterSheet): CharacterSheet {
        if (!sheet.progression.configured) return sheet
        fun generated(ids: List<String>) = ids.joinToString("\n") {
            "• ${pack.element(it)?.name ?: optionLabel(it)}"
        }
        return sheet.copy(
            classFeatures = sheet.classFeatures
                .takeIf { it.trim() != generated(sheet.progression.selectedFeatureIds).trim() }
                .orEmpty(),
            feats = sheet.feats
                .takeIf { it.trim() != generated(sheet.progression.featIds).trim() }
                .orEmpty(),
        )
    }

    fun advance(sheet: CharacterSheet, request: LevelUpRequest): CharacterSheet {
        val wasConfigured = sheet.progression.configured
        // Una scheda vuota nasce nella lingua di chi la crea, e questo servizio la
        // scrive per intero: puo' marchiarla come vuole. Una scheda gia' scritta
        // no — il testo che c'e' e' nella *sua* lingua, e aggiungergli sopra
        // quello di un altro pacchetto la lascerebbe a meta' fra le due, per
        // giunta marchiata come se fosse tutta nell'ultima arrivata. Da li' la
        // ritraduzione non la recupera piu': crede di partire da una lingua in
        // cui meta' della scheda non e' mai stata scritta.
        //
        // Non e' una condizione che l'utente possa violare: chi chiama porta la
        // scheda nella lingua del servizio *prima* di applicarle un livello.
        require(!wasConfigured || sheet.contentLanguage == language) {
            "Scheda in ${sheet.contentLanguage} avanzata col pacchetto $language: " +
                "va tradotta prima di applicarle un livello."
        }
        val oldClassLevel = sheet.progression.levelIn(request.classId)
        val oldConstitutionModifier = sheet.modifier(Ability.CONSTITUTION)
        val scoresBeforeAdvancement = pack.stats.associate { it.id to it.defaultScore } + sheet.abilityScores
        val progressed = progressionEngine.applyLevelUp(
            progression = sheet.progression,
            experiencePoints = sheet.experiencePoints,
            abilityScores = scoresBeforeAdvancement,
            request = request,
        )
        val selectedBackground = progressed.backgroundId.takeIf { it.isNotBlank() }?.let(pack::background)
        val backgroundIncreases = if (wasConfigured) {
            emptyMap()
        } else {
            request.backgroundAbilityScoreIncreases
                .filterValues { it != 0 }
                .ifEmpty { selectedBackground?.abilityOptions?.associateWith { 1 }.orEmpty() }
        }
        val absoluteIncrease = if (
            request.selections
                .flatMap { it.optionIds }
                .mapNotNull(pack::element)
                .any { it.kind == RuleElementKind.EPIC_BOON_FEAT }
        ) {
            true
        } else {
            false
        }
        fun cap(ability: Ability, absolute: Boolean = false): Int = pack.stat(ability)?.let {
            if (absolute) it.maximumScore else it.advancementMaximum
        } ?: 20
        fun defaultScore(ability: Ability): Int = pack.stat(ability)?.defaultScore ?: 10
        val abilityScores = scoresBeforeAdvancement.toMutableMap().apply {
            backgroundIncreases.forEach { (ability, increase) ->
                this[ability] = (getOrDefault(ability, defaultScore(ability)) + increase)
                    .coerceAtMost(cap(ability))
            }
            request.abilityScoreIncreases.forEach { (ability, increase) ->
                this[ability] = (getOrDefault(ability, defaultScore(ability)) + increase)
                    .coerceAtMost(cap(ability, absoluteIncrease))
            }
        }
        val newConstitutionModifier = abilityModifier(abilityScores[Ability.CONSTITUTION] ?: 10)
        val constitutionHitPointAdjustment =
            (newConstitutionModifier - oldConstitutionModifier) * progressed.totalLevel
        val hitPointIncrease = request.hitPointIncrease + constitutionHitPointAdjustment
        val definition = pack.classDefinition(request.classId)
        val requirements = progressionEngine.requirementsFor(
            sheet.progression,
            request.classId,
            request.selections,
        )
        val choicesById = requirements.associateBy { it.id }

        val skills = sheet.skillProficiencies.toMutableMap().apply {
            selectedBackground?.skillProficiencies?.forEach {
                this[it] = Proficiency.PROFICIENT
            }
        }
        val selectedTools = mutableListOf<String>()
        val selectedLanguages = mutableListOf<String>()
        val selectedWeapons = mutableListOf<WeaponDefinition>()
        val selectedEquipment = mutableListOf<app.d6d.rules.character.EquipmentPackageDefinition>()
        request.selections.forEach { selection ->
            when (choicesById[selection.choiceId]?.kind) {
                ChoiceKind.STARTING_WEAPON ->
                    selectedWeapons += selection.optionIds.mapNotNull(pack::weapon)
                ChoiceKind.STARTING_EQUIPMENT ->
                    selectedEquipment += selection.optionIds.mapNotNull(pack::equipmentPackage)
                ChoiceKind.SKILL_PROFICIENCY -> selection.optionIds.forEach { id ->
                    id.toSkillOrNull(pack)?.let { skills[it] = Proficiency.PROFICIENT }
                }
                ChoiceKind.EXPERTISE -> selection.optionIds.forEach { id ->
                    id.toSkillOrNull(pack)?.let { skills[it] = Proficiency.EXPERTISE }
                }
                ChoiceKind.SKILL_OR_TOOL_PROFICIENCY -> selection.optionIds.forEach { id ->
                    val skill = id.toSkillOrNull(pack)
                    if (skill != null) {
                        skills[skill] = Proficiency.PROFICIENT
                    } else {
                        selectedTools += optionLabel(id)
                    }
                }
                ChoiceKind.TOOL_PROFICIENCY -> selectedTools += selection.optionIds.map(::optionLabel)
                ChoiceKind.LANGUAGE_PROFICIENCY ->
                    selectedLanguages += selection.optionIds.map(::optionLabel)
                else -> Unit
            }
        }
        selectedWeapons += selectedEquipment.flatMap { equipment ->
            equipment.weaponIds.mapNotNull(pack::weapon)
        }

        val isFirstCharacterLevel = !wasConfigured
        val cumulativeClassLevels = progressed.classLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId).levels.take(classLevel.level)
        }
        val baseSaves = if (isFirstCharacterLevel) {
            sheet.saveProficiencies + definition.savingThrowProficiencies.associateWith {
                Proficiency.PROFICIENT
            }
        } else {
            sheet.saveProficiencies
        }
        val saves = baseSaves +
            cumulativeClassLevels
                .flatMap { it.savingThrowProficiencyGrants }
                .associateWith {
                    Proficiency.PROFICIENT
                }
        val baseArmorGrant = when {
            isFirstCharacterLevel -> definition.armorTraining
            oldClassLevel == 0 -> definition.multiclassArmorTraining
            else -> null
        }
        val selectedRuleElements = request.selections
            .flatMap { it.optionIds }
            .mapNotNull(pack::element)
        val armor = (listOfNotNull(baseArmorGrant) +
            selectedRuleElements.mapNotNull { it.armorTrainingGrant })
            .fold(sheet.armorTraining) { current, grant ->
                current.copy(
                    light = current.light || grant.light,
                    medium = current.medium || grant.medium,
                    heavy = current.heavy || grant.heavy,
                    shields = current.shields || grant.shields,
                )
            }
        val weaponGrant = when {
            isFirstCharacterLevel -> definition.weaponTraining
            oldClassLevel == 0 -> definition.multiclassWeaponTraining
            else -> ""
        }
        val weaponTraining = appendDistinctText(
            sheet.weaponProficiencies,
            (
                listOf(weaponGrant) +
                    selectedRuleElements.map { it.weaponTrainingGrant }
                ).filter { it.isNotBlank() }.distinct().joinToString(", "),
        )
        val toolTraining = appendDistinctText(
            sheet.toolProficiencies,
            selectedTools.joinToString(", "),
        )
        val languages = appendDistinctText(
            sheet.languages,
            (
                // Soltanto il livello appena ottenuto concede nuove lingue. Ripassare
                // ogni volta tutte le righe precedenti duplicava una concessione
                // salvata in italiano quando il livello successivo veniva applicato
                // con il pacchetto inglese ("Druidico, Druidic").
                definition.level(progressed.levelIn(request.classId)).languageProficiencyGrants +
                    selectedLanguages
                ).distinct().joinToString(", "),
        )

        // Un'arma scelta alla creazione diventa subito una riga della scheda: da li'
        // la proiezione da combattimento la trasforma nella capacita' con cui si
        // attacca, senza che l'utente debba ricopiarne i numeri a mano.
        val weaponRows = selectedWeapons.map {
            it.toWeaponEntry(
                abilityScores = abilityScores,
                proficiencyBonus = pack.proficiencyProgression.bonus(progressed.totalLevel),
                language = language,
            )
        }
        val weapons = sheet.weapons +
            weaponRows.filterNot { row -> sheet.weapons.any { it.name == row.name } }

        val classNames = progressed.classLevels.joinToString(" / ") {
            "${pack.classDefinition(it.classId).name} ${it.level}"
        }
        val subclassNames = progressed.subclasses.joinToString(" / ") {
            pack.element(it.subclassId)?.name ?: optionLabel(it.subclassId)
        }
        val spellcasting = deriveSpellcasting(sheet.spellcasting, progressed)
        // L'azione di Magia dichiara che il personaggio sa lanciare incantesimi:
        // darla anche a chi non ne ha nemmeno uno non indicherebbe piu' nulla.
        val commonActions = pack.elements
            .filter { it.kind == RuleElementKind.COMMON_ACTION }
            .filterNot { it.id == MAGIC_ACTION_ID && spellcasting == null }
            .map { it.id }
        val previouslyGeneratedIds = buildSet {
            addAll(pack.elements.filter { it.kind == RuleElementKind.COMMON_ACTION }.map { it.id })
            addAll(sheet.progression.selectedFeatureIds)
            addAll(sheet.progression.featIds)
            addAll(sheet.progression.knownCantripIds)
            addAll(sheet.progression.preparedSpellIds)
            addAll(sheet.progression.alwaysPreparedSpellIds)
        }
        // Capacità e tratti aggiunti a mano dalla scheda non devono sparire al
        // passaggio di livello: si sostituisce soltanto la parte generata.
        val manuallyLinkedAbilityIds = sheet.abilityIds.filterNot { it in previouslyGeneratedIds }
        val progressedCatalogAbilityIds = (
            progressed.selectedFeatureIds +
                progressed.featIds +
                progressed.knownCantripIds +
                progressed.preparedSpellIds +
                progressed.alwaysPreparedSpellIds
            )
            .filter { pack.element(it) != null }
        val selectedAbilityIds = (
            commonActions +
                manuallyLinkedAbilityIds +
                progressedCatalogAbilityIds
            )
            .distinct()
            .filterNot { it in sheet.excludedTraitIds }
        val hitDicePools = deriveHitDicePools(sheet, progressed)
        val newClassLevel = progressed.levelIn(request.classId)
        val draconicResilienceActive =
            request.classId == CharacterClassId.SORCERER &&
                progressed.subclassFor(CharacterClassId.SORCERER)
                    ?.endsWith(":stregoneria-draconica") == true
        val draconicHitPointIncrease = when {
            !draconicResilienceActive -> 0
            newClassLevel == 3 -> 3
            newClassLevel > 3 -> 1
            else -> 0
        }
        val totalHitPointIncrease = hitPointIncrease + draconicHitPointIncrease
        val newMaximum = if (isFirstCharacterLevel) {
            totalHitPointIncrease.coerceAtLeast(1)
        } else {
            (sheet.maxHitPoints + totalHitPointIncrease).coerceAtLeast(1)
        }
        val maximumDelta = newMaximum - sheet.maxHitPoints
        val mayAdoptClassArmorFormula =
            sheet.armorClassMethod == ArmorClassMethod.UNARMORED ||
                (
                    sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL &&
                        sheet.armorClass == 10 &&
                        sheet.armorClassOverride == null &&
                        sheet.wornArmorCategory == null
                    )
        val armorClassMethod = if (mayAdoptClassArmorFormula) {
            when {
                isFirstCharacterLevel && request.classId == CharacterClassId.BARBARIAN ->
                    ArmorClassMethod.BARBARIAN_UNARMORED
                isFirstCharacterLevel && request.classId == CharacterClassId.MONK ->
                    ArmorClassMethod.MONK_UNARMORED
                draconicResilienceActive && newClassLevel >= 3 ->
                    ArmorClassMethod.DRACONIC_RESILIENCE
                else -> sheet.armorClassMethod
            }
        } else {
            sheet.armorClassMethod
        }
        val equippedArmorMethod = selectedEquipment.mapNotNull { equipment ->
            when (equipment.armor) {
                StartingArmor.LEATHER -> ArmorClassMethod.LEATHER
                StartingArmor.STUDDED_LEATHER -> ArmorClassMethod.STUDDED_LEATHER
                StartingArmor.CHAIN_SHIRT -> ArmorClassMethod.CHAIN_SHIRT
                StartingArmor.CHAIN_MAIL -> ArmorClassMethod.CHAIN_MAIL
                null -> null
            }
        }.lastOrNull()
        val fiendResistance = request.selections
            .firstOrNull { it.choiceId.endsWith(":resilienza-immonda") }
            ?.optionIds
            ?.singleOrNull()
            ?.substringAfterLast(':')
            ?.toDamageTypeOrNull()

        return withRefreshedEffects(
            sheet.copy(
                // Il testo che segue lo scrive questo servizio, nella lingua del
                // proprio pacchetto: la scheda lo registra, altrimenti nessuno
                // sapra' piu' da quale lingua tradurla. Per una scheda gia'
                // configurata e' il valore che aveva — lo pretende la guardia in
                // testa al metodo — quindi qui cambia solo alla prima creazione.
                contentLanguage = language,
                className = classNames,
                background = sheet.background.ifBlank { selectedBackground?.name.orEmpty() },
                subclass = subclassNames,
                level = progressed.totalLevel,
                progression = progressed,
                maxHitPoints = newMaximum,
                currentHitPoints = if (isFirstCharacterLevel) {
                    newMaximum
                } else {
                    (sheet.currentHitPoints + maximumDelta).coerceIn(0, newMaximum)
                },
                hitDiceMax = progressed.totalLevel,
                hitDiceSpent = hitDicePools.sumOf { it.spent },
                hitDieSides = definition.hitDieSides,
                hitDicePools = hitDicePools,
                weapons = weapons,
                abilityScores = abilityScores,
                saveProficiencies = saves,
                skillProficiencies = skills,
                armorTraining = armor,
                armorClassMethod = equippedArmorMethod ?: armorClassMethod,
                shieldEquipped = sheet.shieldEquipped || selectedEquipment.any { it.shield },
                fiendishResilienceDamageType =
                    fiendResistance ?: sheet.fiendishResilienceDamageType,
                weaponProficiencies = weaponTraining,
                toolProficiencies = toolTraining,
                languages = languages,
                equipment = appendEquipmentText(
                    sheet.equipment,
                    selectedEquipment.map { it.description },
                ),
                money = sheet.money.copy(
                    gold = sheet.money.gold + selectedEquipment.sumOf { it.goldPieces },
                ),
                abilityIds = selectedAbilityIds,
                // Privilegi e talenti non vengono piu' riscritti come elenco di nomi:
                // la progressione li conosce gia' e la scheda li mostra da li', con il
                // testo del documento. Quei campi restano all'utente per le sue note,
                // che un passaggio di livello non deve cancellare.
                spellcasting = spellcasting,
            ),
        )
    }

    /**
     * Sostituisce una scelta gia' acquisita senza simulare un nuovo livello.
     *
     * Il comando serve alle scelte che le regole consentono di cambiare durante
     * un riposo. Aggiorna tutte le proiezioni correnti della scelta, ma conserva
     * la cronologia dell'avanzamento: quella descrive cio' che fu scelto salendo
     * di livello e non viene riscritta retroattivamente.
     */
    fun replaceSelectedOption(
        sheet: CharacterSheet,
        oldOptionId: String,
        newOptionId: String,
        allowedOptionIds: Set<String>,
        choiceId: String? = null,
    ): CharacterSheet {
        require(sheet.progression.configured) {
            say("La progressione guidata non è configurata.", "Guided progression isn't configured.")
        }
        require(oldOptionId != newOptionId) {
            say(
                "La nuova opzione deve essere diversa da quella sostituita.",
                "The new option must differ from the option being replaced.",
            )
        }
        require(oldOptionId in sheet.progression.selectedFeatureIds) {
            say(
                "L'opzione da sostituire non è posseduta dal personaggio.",
                "The character doesn't have the option being replaced.",
            )
        }
        require(newOptionId in allowedOptionIds) {
            say("La nuova opzione non è disponibile.", "The new option isn't available.")
        }
        require(newOptionId !in sheet.progression.selectedFeatureIds) {
            say(
                "La nuova opzione è già posseduta dal personaggio.",
                "The character already has the new option.",
            )
        }

        val containingSelections = sheet.progression.selections.count { selection ->
            oldOptionId in selection.optionIds && (choiceId == null || selection.choiceId == choiceId)
        }
        require(containingSelections == 1) {
            say(
                "La scelta da sostituire non è rappresentata in modo univoco nella progressione.",
                "The selection being replaced isn't represented uniquely in the progression.",
            )
        }
        val updatedProgression = sheet.progression.copy(
            selections = sheet.progression.selections.map { selection ->
                if (oldOptionId in selection.optionIds && (choiceId == null || selection.choiceId == choiceId)) {
                    selection.copy(
                        optionIds = selection.optionIds.map { id ->
                            if (id == oldOptionId) newOptionId else id
                        },
                    )
                } else {
                    selection
                }
            },
            selectedFeatureIds = sheet.progression.selectedFeatureIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            },
            knownCantripIds = sheet.progression.knownCantripIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            }.distinct(),
            preparedSpellIds = sheet.progression.preparedSpellIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            }.distinct(),
            alwaysPreparedSpellIds = sheet.progression.alwaysPreparedSpellIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            }.distinct(),
            spellbookSpellIds = sheet.progression.spellbookSpellIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            }.distinct(),
        )
        val refreshedProgression = progressionEngine.refreshDerivedState(
            updatedProgression,
            pack.stats.associate { it.id to it.defaultScore } + sheet.abilityScores,
        )
        val fiendResistance = refreshedProgression.selections
            .firstOrNull { it.choiceId.endsWith(":resilienza-immonda") }
            ?.optionIds
            ?.singleOrNull()
            ?.substringAfterLast(':')
            ?.toDamageTypeOrNull()
        return sheet.copy(
            progression = refreshedProgression,
            abilityIds = sheet.abilityIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            }.distinct(),
            excludedTraitIds = sheet.excludedTraitIds.map { id ->
                if (id == oldOptionId) newOptionId else id
            }.toSet(),
            fiendishResilienceDamageType = fiendResistance,
            spellcasting = deriveSpellcasting(sheet.spellcasting, refreshedProgression),
        )
    }

    /** Scelte già acquisite che il riposo indicato permette di modificare. */
    fun replaceableChoicesAfterRest(
        sheet: CharacterSheet,
        period: RecoveryPeriod,
    ): List<Pair<CharacterClassId, ChoiceDefinition>> {
        if (!sheet.progression.configured) return emptyList()
        val selectedChoiceIds = sheet.progression.selections.mapTo(mutableSetOf()) { it.choiceId }
        val activeOptionIds = buildSet {
            sheet.progression.selections.forEach { addAll(it.optionIds) }
            sheet.progression.subclasses.forEach { add(it.subclassId) }
            addAll(sheet.progression.selectedFeatureIds)
        }
        return sheet.progression.classLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId)
                .levels
                .take(classLevel.level)
                .flatMap { it.choices }
                .filter { it.id in selectedChoiceIds }
                .filter { it.requiredOptionId == null || it.requiredOptionId in activeOptionIds }
                .filter { choice -> choice.replacementWindow.allows(period) }
                .map { classLevel.classId to it }
        }
    }

    /** Sostituisce tutte le scelte indicate e completa un solo riposo. */
    fun restAndReplaceSelectedOptions(
        sheet: CharacterSheet,
        period: RecoveryPeriod,
        replacements: List<RestChoiceReplacement>,
    ): CharacterSheet {
        require(replacements.isNotEmpty()) {
            say("Seleziona almeno una sostituzione.", "Select at least one replacement.")
        }
        require(replacements.map { it.choiceId }.distinct().size == replacements.size) {
            say(
                "Ogni scelta può essere modificata una sola volta nello stesso riposo.",
                "Each choice can be changed only once during the same rest.",
            )
        }
        val replaceableByKey = replaceableChoicesAfterRest(sheet, period)
            .associateBy { (classId, choice) -> classId to choice.id }
        val updated = replacements.fold(sheet) { current, replacement ->
            val choice = replaceableByKey[replacement.classId to replacement.choiceId]?.second
                ?: error(
                    say(
                        "Questa scelta non può essere cambiata con il riposo indicato.",
                        "This choice can't be changed with the specified rest.",
                    ),
                )
            require(replacement.newOptionId in choice.optionIds) {
                say(
                    "La nuova opzione non appartiene alla scelta.",
                    "The new option doesn't belong to the choice.",
                )
            }
            replaceSelectedOption(
                sheet = current,
                oldOptionId = replacement.oldOptionId,
                newOptionId = replacement.newOptionId,
                allowedOptionIds = choice.optionIds.toSet(),
                choiceId = replacement.choiceId,
            )
        }
        return updated.recoveredAfter(period)
    }

    private fun deriveSpellcasting(
        previous: Spellcasting?,
        progression: app.d6d.rules.character.CharacterProgression,
    ): Spellcasting? {
        val castingClasses = progression.classLevels.mapNotNull { classLevel ->
            pack.classDefinition(classLevel.classId)
                .takeIf { it.spellcastingKind != SpellcastingKind.NONE }
                ?.let { Triple(classLevel, it, it.level(classLevel.level)) }
        }
        val magicInitiateAbility = progression.selections
            .asReversed()
            .firstOrNull { it.choiceId.endsWith(":magic-initiate:ability") }
            ?.optionIds
            ?.singleOrNull()
            ?.substringAfterLast(':')
            ?.let { slug ->
                pack.stats.map { it.id }.firstOrNull {
                    it.name.lowercase() == slug ||
                        it.value.substringAfterLast(':').replace('-', '_').lowercase() == slug
                }
            }
        val spellIds = (
            progression.knownCantripIds +
                progression.preparedSpellIds +
                progression.alwaysPreparedSpellIds +
                progression.spellbookSpellIds
            ).distinct()
        if (castingClasses.isEmpty() && spellIds.isEmpty()) return null

        val abilities = castingClasses.associate { (state, definition) ->
            state.classId to checkNotNull(definition.spellcastingAbility)
        }
        val standardCasters = castingClasses.filter { (_, definition) ->
            definition.spellcastingKind != SpellcastingKind.PACT_MAGIC
        }
        val standardSlots = when {
            standardCasters.isEmpty() -> List(9) { 0 }
            standardCasters.size == 1 -> standardCasters.single().third.spellSlots.padSlots()
            else -> {
                val effectiveCasterLevel = standardCasters.sumOf { (state, definition) ->
                    when (definition.spellcastingKind) {
                        SpellcastingKind.HALF_CASTER -> (state.level + 1) / 2
                        else -> state.level
                    }
                }.coerceIn(1, 20)
                MULTICLASS_SLOTS[effectiveCasterLevel - 1]
            }
        }
        val existingSlots = previous?.slots.orEmpty().associateBy { it.level }
        val slots = standardSlots.mapIndexed { index, total ->
            val level = index + 1
            SpellSlot(level, total, existingSlots[level]?.spent?.coerceAtMost(total) ?: 0)
        }
        val pact = castingClasses.firstOrNull { (_, definition) ->
            definition.spellcastingKind == SpellcastingKind.PACT_MAGIC
        }?.third
        val pactSlots = pact?.let {
            SpellSlot(
                level = it.pactSlotLevel,
                total = it.pactSlotCount,
                spent = previous?.pactSlots?.spent?.coerceAtMost(it.pactSlotCount) ?: 0,
            )
        }
        val magicInitiateSpellIds = progression.selections
            .filter {
                it.choiceId.endsWith(":magic-initiate:cantrips") ||
                    it.choiceId.endsWith(":magic-initiate:spell")
            }
            .flatMapTo(mutableSetOf()) { it.optionIds }
        val spells = spellIds.mapNotNull { id ->
            pack.element(id)?.let { element ->
                val spell = element.spell ?: return@let null
                SpellEntry(
                    level = spell.level,
                    name = element.name,
                    castingTime = spell.castingTime,
                    range = spell.range,
                    concentration = spell.concentration,
                    ritual = spell.ritual,
                    materials = "M" in spell.components,
                    note = when {
                        id in magicInitiateSpellIds -> say("Iniziato alla magia", "Magic Initiate")
                        id in progression.alwaysPreparedSpellIds -> say("Sempre preparato", "Always prepared")
                        id in progression.spellbookSpellIds && id !in progression.preparedSpellIds ->
                            say("Nel libro degli incantesimi", "In the spellbook")
                        else -> ""
                    },
                )
            }
        }
        return Spellcasting(
            ability = abilities.values.firstOrNull() ?: magicInitiateAbility ?: Ability.INTELLIGENCE,
            abilitiesByClass = abilities,
            slots = slots,
            pactSlots = pactSlots,
            spells = spells,
        )
    }

    private fun deriveHitDicePools(
        previous: CharacterSheet,
        progression: app.d6d.rules.character.CharacterProgression,
    ): List<HitDicePool> {
        val oldPools = previous.hitDicePools.associateBy { it.dieSides }
        return progression.classLevels
            .groupingBy { pack.classDefinition(it.classId).hitDieSides }
            .fold(0) { total, state -> total + state.level }
            .map { (sides, total) ->
                HitDicePool(sides, total, oldPools[sides]?.spent?.coerceAtMost(total) ?: 0)
            }
            .sortedBy { it.dieSides }
    }
}

/**
 * Riga "Armi e trucchetti da combattimento" per un'arma dello SRD.
 *
 * La caratteristica segue il regolamento: la Destrezza per le armi a distanza,
 * la migliore fra Forza e Destrezza per quelle accurate, la Forza altrimenti.
 * Il personaggio è competente in quello che la sua classe gli insegna a usare,
 * quindi il bonus di competenza rientra sempre nel tiro per colpire.
 */
fun WeaponDefinition.toWeaponEntry(
    abilityScores: Map<Ability, Int>,
    proficiencyBonus: Int,
    language: AppLanguage = AppLanguage.ITALIAN,
): WeaponEntry {
    fun modifierOf(ability: Ability) = abilityModifier(abilityScores[ability] ?: 10)
    val strength = modifierOf(Ability.STRENGTH)
    val dexterity = modifierOf(Ability.DEXTERITY)
    val attackAbility = when {
        reach == WeaponReach.RANGED -> Ability.DEXTERITY
        WeaponProperty.FINESSE in properties && dexterity > strength -> Ability.DEXTERITY
        else -> Ability.STRENGTH
    }
    val modifier = modifierOf(attackAbility)
    return WeaponEntry(
        name = name,
        attackBonus = proficiencyBonus + modifier,
        attackAbility = attackAbility,
        diceCount = diceCount,
        diceSides = diceSides,
        fixedDamage = fixedDamage,
        damageModifier = modifier,
        damageType = damageType,
        rangeFeet = attackRangeFeet,
        note = weaponNote(language),
    )
}

/**
 * La nota che accompagna un'arma sulla scheda: padronanza, versatilità, gittata.
 *
 * E' testo generato per intero — nomi tradotti, unita' convertite — e nient'altro
 * che l'arma e la lingua lo determinano. Sta qui, e non dentro [toWeaponEntry],
 * perche' serve anche al cambio di lingua: una nota non si *traduce* parola per
 * parola, si riscrive dall'arma corrispondente nel pacchetto di destinazione.
 * Riscriverla altrove sarebbe una seconda formula da tenere allineata a questa,
 * e prima o poi non lo sarebbe.
 */
fun WeaponDefinition.weaponNote(language: AppLanguage): String = buildString {
    append(language.pick("Padronanza: ", "Mastery: ")).append(mastery)
    if (versatileDiceSides > 0) {
        append(language.pick(" · a due mani 1d", " · two-handed 1d"))
            .append(versatileDiceSides)
    }
    if (reach == WeaponReach.RANGED || WeaponProperty.THROWN in properties) {
        append(language.pick(" · gittata ", " · range "))
            .append(distanceValue(normalRangeFeet, language))
            .append('/')
            .append(distanceValue(longRangeFeet, language))
            .append(' ')
            .append(distanceUnit(language))
    }
}

/** Azione comune che segnala la capacità di lanciare incantesimi. */
private const val MAGIC_ACTION_ID = "srd521-it:action:magia"

private fun List<Int>.padSlots(): List<Int> = (this + List(9) { 0 }).take(9)

private fun String.toSkillOrNull(pack: RulesContentPack): Skill? {
    val slug = substringAfterLast(':').replace('-', '_')
    return pack.skills.firstOrNull { definition ->
        definition.id.name.lowercase().substringAfterLast(':').replace('-', '_') == slug ||
            definition.name.toRulesSlug().replace('-', '_') == slug
    }?.id
}

private fun defaultOptionLabel(id: String): String =
    id.substringAfterLast(':')
        .replace('-', ' ')
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }

private fun String.toRulesSlug(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private fun String.toDamageTypeOrNull(): DamageType? = when (this) {
    "acido" -> DamageType.ACID
    "contundente" -> DamageType.BLUDGEONING
    "freddo" -> DamageType.COLD
    "fuoco" -> DamageType.FIRE
    "fulmine" -> DamageType.LIGHTNING
    "necrotico" -> DamageType.NECROTIC
    "perforante" -> DamageType.PIERCING
    "veleno" -> DamageType.POISON
    "psichico" -> DamageType.PSYCHIC
    "radioso" -> DamageType.RADIANT
    "tagliente" -> DamageType.SLASHING
    "tuono" -> DamageType.THUNDER
    else -> null
}

private fun ChoiceReplacementWindow.allows(period: RecoveryPeriod): Boolean = when (this) {
    ChoiceReplacementWindow.SHORT_OR_LONG_REST ->
        period == RecoveryPeriod.SHORT_REST || period == RecoveryPeriod.LONG_REST
    ChoiceReplacementWindow.LONG_REST -> period == RecoveryPeriod.LONG_REST
    ChoiceReplacementWindow.NEVER,
    ChoiceReplacementWindow.CLASS_LEVEL_UP,
    -> false
}

private fun appendDistinctText(existing: String, addition: String): String =
    listOf(existing, addition)
        .flatMap { it.split(',', ';', '\n') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

private fun appendEquipmentText(existing: String, additions: List<String>): String =
    (listOf(existing) + additions)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString("\n")

private fun String.containsListedEntry(label: String): Boolean =
    split(',', ';', '\n').any { it.trim().equals(label, ignoreCase = true) }

private val editableTraitKinds = setOf(
    RuleElementKind.CLASS_FEATURE,
    RuleElementKind.SUBCLASS_FEATURE,
    RuleElementKind.CLASS_OPTION,
    RuleElementKind.METAMAGIC,
    RuleElementKind.ELDRITCH_INVOCATION,
    RuleElementKind.ORIGIN_FEAT,
    RuleElementKind.GENERAL_FEAT,
    RuleElementKind.FIGHTING_STYLE_FEAT,
    RuleElementKind.EPIC_BOON_FEAT,
)

private fun String.effectKey(): String = trim().lowercase()

/** Applica la stessa regola degli effetti progressivi usata dal motore. */
private fun mergeEffects(effects: List<RuleEffect>): List<RuleEffect> {
    val distinct = effects.distinct()
    val (grouped, single) = distinct.partition { it.group.isNotBlank() }
    return single + grouped
        .groupBy { it.group }
        .map { (_, sameGroup) -> sameGroup.maxBy { it.amount } }
}

/** Tabella Incantatore multiclasse SRD 5.2.1, livelli effettivi 1–20. */
private val MULTICLASS_SLOTS = listOf(
    listOf(2, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(3, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(4, 2, 0, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 0, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 2, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 1, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 2, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 3, 1, 0, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 0, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 1, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 2, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 2, 2, 1, 1),
)
