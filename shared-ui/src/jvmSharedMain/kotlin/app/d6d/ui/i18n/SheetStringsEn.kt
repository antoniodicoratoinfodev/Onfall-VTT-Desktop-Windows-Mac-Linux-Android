package app.d6d.ui.i18n

/** La scheda e lo stat block, in inglese. */
internal object SheetStringsEn : SheetStrings {

    // --- azioni comuni sulla scheda ------------------------------------------

    override val saveSheet = "Save sheet"
    override val saveStatBlock = "Save stat block"
    override val unsavedChanges = "Unsaved changes"
    override val deleteSheetTitle = "Delete the sheet?"
    override val deleteStatBlockTitle = "Delete the stat block?"
    override fun deleteSheetBody(name: String) = "“$name”'s sheet will be deleted for good."
    override fun deleteStatBlockBody(name: String) = "“$name” will be deleted for good."

    // --- intestazione e valori principali ------------------------------------

    override val characterName = "Character name"
    override val alignment = "Alignment"
    override val failures = "Failures"
    override val weapons = "Weapons"
    override val tools = "Tools"
    override val monsters = "Monsters"
    override val score = "Score"
    override val initiative = "Initiative"
    override val feats = "Feats"
    override val spells = "Spells"
    override val appearance = "Appearance"
    override val equipment = "Equipment"
    override val header = "Header"
    override val abilitiesLabel = "Abilities"
    override val actions = "Actions"
    override val reactions = "Reactions"
    override val proficient = "Proficient"
    override val expertise = "Expertise"
    override val fightingStyle = "Fighting style"
    override val activeLabel = "Active"
    override val currentArmorClass = "Current AC"
    override val hitPoints = "Hit points"
    override val hitDice = "Hit dice"
    override val deathSaves = "Death saves"
    override val proficiencyBonus = "Proficiency bonus"
    override val heroicInspiration = "Heroic Inspiration"
    override val available = "Available"
    override val cantripsHeading = "CANTRIPS"
    override val notAvailable = "Not available"
    override val trainingAndProficiencies = "Training and proficiencies"
    override val armorTraining = "Armor training"
    override val savingThrow = "Saving throw"
    override val disadvantageShort = " · disadv."
    override val passivePerception = "Passive Perception"
    override val modifier = "Modifier"
    override val heal = "Heal"
    override val stabilize = "Stabilize"
    override val conditions = "Conditions"
    override fun effectiveSpeed(distance: String) = "Effective: $distance"

    // --- armi e capacita' -----------------------------------------------------

    override val weaponsAndCombatAbilities = "Weapons and combat abilities"
    override val attackBonusOrDc = "Atk bonus / DC"
    override val damageAndType = "Damage and type"
    override val addWeapon = "+ Add weapon"
    override val addAbility = "+ Add ability"
    override val fixedDamage = "Fixed damage"
    override val bonusAction = "Bonus action"
    override val spellOrCantrip = "Spell or cantrip"
    override val attackAbilityCaps = "ATTACK ROLL ABILITY"
    override val spellcastingAbility = "Spellcasting ability"
    override val toClassify = "To classify"
    override val unclassifiedEntryHint = "Imported entry still to classify: if it is a weapon, " +
        "pick the attack roll ability; otherwise turn on “Spell or cantrip”. In armor you are " +
        "not proficient with, it stays out of the fight."
    override val areaDamageWithSave = "Area damage (spell with a save)"
    override val savingThrowCaps = "SAVING THROW"
    override val halfDamageOnSave = "Half damage on a successful save"
    override val fromAbilityCatalog = "From the Abilities catalog"
    override val addAbilityTitle = "Add ability"
    override val emptyAbilityCatalog =
        "The catalog is empty. Create an ability first, in Compendium → Abilities."
    override val legendaryAction = "Legendary action"
    override val noCost = "No cost"
    override fun areaOf(radius: String) = "Area $radius"
    override fun abilityMissingFromCatalog(abilityId: String) =
        "Ability no longer in the catalog · $abilityId"

    // --- privilegi, talenti e note --------------------------------------------

    override val classFeatures = "Class features"
    override val sectionFeatures = "features"
    override val sectionFeats = "feats"
    override val noFeaturesRecorded =
        "No features recorded: this sheet does not use guided progression."
    override val manageFeatures = "+ Manage features"
    override val yourNotes = "YOUR NOTES"
    override val speciesTraits = "Species traits"
    override val noFeatsRecorded = "No feats recorded."
    override val manageFeats = "+ Manage feats"
    override val manageFeaturesTitle = "Manage features"
    override val manageFeatsTitle = "Manage feats"
    override val searchByNameRuleOrPrerequisite = "Search by name, rule or prerequisite"
    override val allFeminine = "All"
    override val onlyCompatibleWithClassAndLevel = "Only ones matching class and level"
    override val noEntryMatchesFilters = "No entry matches these filters."
    override val outOfRequirements = "Requirements not met"
    override fun chooseFromCompendium(count: Int) = "Pick from the Compendium · $count results"
    override fun sourcePage(page: Int) = "p. $page"
    override fun classAndMinimumLevel(className: String, level: Int) = "$className $level+"
    override fun prerequisite(text: String) = "Prerequisite: $text"

    // --- incantesimi -------------------------------------------------------------

    override val castsSpells = "This character casts spells"
    override val castingBlockedByArmor = "Casting blocked: no proficiency with the armor worn. " +
        "Spells will not be available in combat."
    override val saveDc = "Save DC"
    override val attackBonus = "Attack bonus"
    override val spellSlots = "Spell slots"
    override val selectedCantripsAndSpells = "Selected cantrips and spells"
    override val concentrationInitial = " · C"
    override val ritualInitial = " · R"
    override val shortOrLongRest = "Short or long rest"
    override val weaponMasteries = "WEAPON MASTERIES"
    override val backgroundAndTraits = "Background and personality"
    override val magicItemAttunement = "Magic item attunement"
    override fun levelHeading(level: Int) = "LEVEL $level"
    override fun slotLevel(level: Int) = "Level $level"
    override fun slotTotal(total: Int) = "Total $total"
    override fun pactSlotLevel(level: Int) = "PACT · LEVEL $level"
    override fun spellcastingAbilityOf(className: String, abbreviation: String) =
        "$className: $abbreviation"

    // --- classe armatura -----------------------------------------------------------

    override val armorClassCalculation = "Armor Class calculation"
    override val baseMethodCaps = "BASE METHOD"
    override val dexterityContributionCaps = "DEXTERITY CONTRIBUTION"
    override val wornArmorCaps = "ARMOR ACTUALLY WORN"
    override val armorVariantCaps = "ARMOR VARIANT"
    override val armorClassModifiersCaps = "AC MODIFIERS"
    override val noArmor = "No armor"
    override val manualFinalArmorClass = "Manual final AC"
    override val manualFinalHint = "The value is used exactly as written: shield and other " +
        "modifiers are not added a second time."
    override val elvenChainManualHint = " So it has to include elven chain's +1 as well."
    override val baseStartingValue = "Starting base value"
    override val armorStrengthRequirement = "Armor Strength requirement (0 = none)"
    override val stealthDisadvantage = "Disadvantage on Dexterity (Stealth)"
    override val mithralNote =
        "Mithral: no Strength requirement and no disadvantage on Stealth."
    override val elvenChainNote =
        "Elven chain: +1 AC and proficiency with this armor is granted."
    override val shieldNotEquipped = "Shield not equipped"
    override val shieldAlreadyInManual = "Shield equipped · already in the manual AC"
    override val shieldEquipped = "Shield equipped · +2"
    override val shieldWithoutProficiency = "Shield equipped · +0 (no proficiency)"
    override val shieldActionNote = "Donning or doffing a shield takes a Utilize action."
    override val shieldNeedsProficiency = "The shield bonus needs shield proficiency."
    override val manualTotalNote = "The manual AC is already the final total. Pick another base " +
        "method to handle shield, bonuses and penalties separately."
    override val noOtherModifier = "No other modifier. You can add magic items, features, " +
        "spells or penalties."
    override val addModifier = "+ Add modifier"
    override val newModifier = "New modifier"
    override val unnamedModifier = "Modifier"
    override val bonusOrPenalty = "Bonus/penalty"
    override val stealthDisadvantageWarning =
        "This armor imposes disadvantage on Dexterity (Stealth) checks."

    override fun overrideActive(value: Int) = "Override on: AC $value temporarily replaces the " +
        "calculation without erasing its details."
    override fun restoreCalculatedArmorClass(value: Int) = "Restore calculated AC ($value)"
    override fun baseArmorClass(value: Int) = "Base AC $value"
    override fun armorClassModifiers(signed: String) = "Modifiers $signed"
    override fun calculatedArmorClass(value: Int) = "Calculated AC $value"
    override fun overrideValue(value: Int) = "Override $value"
    override fun missingArmorProficiency(category: String) = "No proficiency with $category. " +
        "Disadvantage on every d20 test using Strength or Dexterity; casting is blocked."
    override fun strengthBelowRequirement(score: Int, required: Int, penalty: String) =
        "Strength $score is below the $required requirement: speed reduced by $penalty."
    override fun donDoffMinutes(don: Int, doff: Int) =
        "SRD gear: $don min to don, $doff min to doff."
    override fun effectRow(source: String, text: String) = "◆ $source · $text"

    override fun effectOnArmorClass(amount: String, condition: String) =
        "$amount to Armor Class$condition"
    override fun effectOnSpeed(distance: String, condition: String) =
        "$distance of Speed$condition"
    override fun effectOnAttack(amount: String, target: String, condition: String) =
        "$amount to $target$condition"
    override fun manualFinalArmorClassIs(value: Int) = "Current AC = manual final AC $value"
    override fun baseWithFullDexterity(base: Int, dexterity: String) = "$base + DEX ($dexterity)"
    override fun baseWithCappedDexterity(base: Int, contribution: String) =
        "$base + DEX ($contribution, max +2)"
    override fun baseWithoutDexterity(base: Int) = "$base, no Dexterity"
    override fun plusSecondaryAbility(abbreviation: String, signed: String) =
        " + $abbreviation ($signed)"
    override fun equalsBaseArmorClass(detail: String, secondary: String, base: Int) =
        "$detail$secondary = base AC $base"
    override fun overrideSuffix(value: Int) = " · override $value"
    override fun shieldRow(signed: String) = "shield $signed"
    override fun armorRuleRow(rule: String, signed: String) = "$rule $signed"
    override fun equalsCalculatedArmorClass(value: Int) = " = calculated AC $value"
    override fun currentArmorClassOverride(value: Int) = " · current AC $value (override)"

    // --- progressione SRD ------------------------------------------------------------

    override val srdCreationTitle = "Guided creation and levels"
    override val srdCreationBody = "Guided mode offers class, proficiencies, features, feats, " +
        "cantrips, spells and resources supplied by the selected ruleset. Existing manual " +
        "sheets stay as they are until you turn it on."
    override val startGuidedCreation = "Start guided creation"
    override val srdProgressionTitle = "Character progression"
    override val classResourcesCaps = "CLASS RESOURCES"
    override val shortRest = "Short rest"
    override val longRest = "Long rest"
    override val longRestAndSwapForm = "Long rest + swap form"
    override val swapKnownFormTitle = "Swap a known form"
    override val swapKnownFormBody = "This finishes a long rest and swaps exactly one form, " +
        "the way Wild Shape calls for."
    override val formToForgetCaps = "FORM TO FORGET"
    override val newFormCaps = "NEW FORM"
    override val finishRestAndSwap = "Finish rest and swap"

    override fun classAndLevel(className: String, level: Int) = "$className $level"
    override fun proficiencyBonusIs(signed: String) = "Proficiency bonus $signed"
    override fun experiencePoints(points: Int) = "$points XP"
    override fun levelUpAvailable(level: Int) = "Level up available: XP allows level $level."
    override fun levelUpTo(level: Int) = "Level up to $level"
    override fun nextLevelAt(threshold: Int, missing: Int?) = buildString {
        append("Next level at ").append(threshold).append(" XP")
        if (missing != null) append(" · ").append(missing).append(" to go")
        append('.')
    }
    override fun resourcePool(remaining: Int, maximum: Int, recovery: String) =
        "$remaining/$maximum · $recovery"
    override fun dieSuffix(sides: Int) = " · d$sides"
    override fun formSummary(name: String, summary: String) = "$name · $summary"

    // --- procedura guidata --------------------------------------------------------------

    override val guidedCreationTitle = "Guided creation"
    override val srdLevelUpTitle = "Level up"
    override val chooseExactlyForFirstLevel = "Pick exactly the options 1st level calls for."
    override val multiclassNote = "Multiclass: the minimum scores of both the current and the " +
        "new class will be checked."
    override val newLevelHitPoints = "Hit points for the new level"
    override val firstLevelUsesMaximum =
        "At 1st level you take the maximum Hit Die + Constitution."
    override val fixedValue = "Fixed value"
    override val rollTheDie = "Roll the die"
    override val dieResultPlusConstitution = "Die result + CON (minimum 1)"
    override val createCharacter = "Create character"
    override val applyLevel = "Apply level"
    override val noOptionForClassAndLevel = "No option available for the current class and level."
    override val abilityScoreIncreaseTitle = "Ability score increase"
    override val abilityScoreIncreaseBody =
        "Assign +2 to one ability, or +1 to two of them (maximum 20)."
    override fun levelAndExperience(level: Int, points: Int) = "Level $level · $points XP"
    override fun choicesMade(selected: Int, required: Int) = "$selected/$required chosen"
    override fun searchAmongOptions(count: Int) = "Search among $count options"
    override fun fixedHitPoints(amount: Int) = "$amount HP"
    override fun applied(text: String) = "Applied: $text"
    override fun backgroundAbilityScoresTitle(background: String) = "Ability scores · $background"
    override val backgroundAbilityScoresBody =
        "Assign +2 and +1 to two abilities, or +1 to all three (maximum 20)."
    override fun assignedOutOf(assigned: Int, total: Int) = "Assigned $assigned/$total"

    // --- stat block delle creature ----------------------------------------------------

    override val unnamedCreature = "Unnamed creature"
    override val defenceInitiativeHitPoints = "Defence, initiative and hit points"
    override val armorClass = "Armor Class"
    override val initiativeModifier = "Initiative mod."
    override val initiativeLabel = "Initiative"
    override val perceptionLabel = "Perception"
    override val challengeRatingShort = "CR"
    override val selected = "Selected"
    override val marked = "Marked"
    override val staticScore = "Static score"
    override val averageHitPoints = "Average HP"
    override val diceCount = "Number of dice"
    override val onFoot = "On foot"
    override val canHover = "Can hover"
    override val typedDefences = "Typed defences"
    override val damageImmunities = "Damage immunities"
    override val conditionImmunities = "Condition immunities"
    override val sensesLanguagesGear = "Senses, languages and gear"
    override val gear = "Gear (recoverable items)"
    override val challengeRating = "Challenge Rating"
    override val baseXp = "Base XP"
    override val lairXp = "Lair XP"
    override val treasureTheme = "Treasure theme"
    override val bonusActions = "Bonus Actions"
    override val legendaryActions = "Legendary Actions"
    override val makeAttackExecutable = "Make the attack executable"
    override val attackBonusShort = "Atk bonus"
    override val addEntry = "+ Add entry"
    override val vulnerabilities = "Vulnerabilities"
    override fun passive(value: Int) = "passive $value"
    override fun sectionCount(title: String, count: Int) = "$title ($count)"
    override fun challengeRatingSummary(
        rating: String,
        xp: Long,
        lairXp: Long?,
        proficiency: String,
    ) = buildString {
        append(rating).append(" (XP ").append(xp)
        if (lairXp != null) append("; in lair ").append(lairXp)
        append("; PB ").append(proficiency).append(')')
    }
    override fun initiativeSummary(signed: String, score: Int) = "$signed ($score)"

    // --- campi e stati dei componenti ---------------------------------------------------

    override val notProficient = "Not proficient"
    override val notSelected = "Not selected"
    override val notMarked = "Not marked"
    override fun labelWithUnit(label: String, unit: String) = "$label ($unit)"

    // --- messaggi del view model -----------------------------------------------------------

    override val archiveLoaded = "Archive loaded."
    override val newSheetFillAndSave = "New sheet: fill it in and save."
    override val sheetSaved = "Sheet saved."
    override val sheetDeleted = "Sheet deleted."
    override val sheetUpdatedFromBattle = "Sheet updated from the battle."
    override val statBlockUpdatedFromBattle = "Stat block updated from the battle."
    override val templateSheetsRestored = "Template sheets restored."
    override val longRestResourcesRecovered = "Long rest resources recovered."
    override val shortRestResourcesRecovered = "Short rest resources recovered."
    override val cannotSwapKnownForm = "Could not swap the known form."
    override val srdCharacterCreated = "Character created: fill in the story details and save."
    override val abilitySaved = "Ability saved."
    override val automaticAbilityMustStayActive =
        "An ability with an automatic effect has to stay active."
    override val abilityNotInCompendium = "Ability not found in the Compendium."
    override val bundledSrdReadOnly =
        "SRD content is read-only. Duplicate it to make your own variant."
    override val bundledSrdCannotBeDeleted = "Bundled SRD content cannot be deleted."
    override val abilityDeleted = "Ability deleted."
    override val unsavedChangesPrompt =
        "There are unsaved changes: save, or confirm you want to discard them."
    override val sheetNotFound = "Sheet not found."
    override fun diskError(detail: String) = "Disk error: $detail"
    override fun invalidSheet(detail: String) = "Invalid sheet: $detail"
    override fun longRestFormSwapped(oldName: String, newName: String) =
        "Long rest finished: $oldName swapped for $newName."
    override fun levelApplied(level: Int) = "Level $level applied. Check it over and save."
    override fun entryNotInSection(section: String) =
        "The chosen entry does not belong to the $section list."
    override fun abilityAddedToSheet(name: String) = "“$name” added to the sheet."
    override fun abilityRemovedFromSheet(name: String) = "“$name” removed from the sheet."
    override fun abilityBecamePassive(name: String) = "“$name” now counts as a passive trait."
    override fun abilityBecameActive(name: String) =
        "“$name” goes back to abilities you spend on your turn."
    override fun cannotDeleteAbilityInUse(usedBy: String) =
        "Cannot delete: the ability is used by $usedBy."
}
