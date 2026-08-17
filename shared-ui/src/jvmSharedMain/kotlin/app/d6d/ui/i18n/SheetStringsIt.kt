package app.d6d.ui.i18n

/** La scheda e lo stat block, in italiano. */
internal object SheetStringsIt : SheetStrings {

    // --- azioni comuni sulla scheda ------------------------------------------

    override val saveSheet = "Salva scheda"
    override val saveStatBlock = "Salva stat block"
    override val unsavedChanges = "Modifiche non salvate"
    override val deleteSheetTitle = "Eliminare la scheda?"
    override val deleteStatBlockTitle = "Eliminare lo stat block?"
    override fun deleteSheetBody(name: String) =
        "La scheda di «$name» verrà eliminata definitivamente."
    override fun deleteStatBlockBody(name: String) = "«$name» verrà eliminata definitivamente."

    // --- intestazione e valori principali ------------------------------------

    override val characterName = "Nome del personaggio"
    override val alignment = "Allineamento"
    override val failures = "Fallimenti"
    override val weapons = "Armi"
    override val tools = "Strumenti"
    override val monsters = "Mostri"
    override val score = "Punteggio"
    override val initiative = "Iniziativa"
    override val feats = "Talenti"
    override val spells = "Incantesimi"
    override val appearance = "Aspetto"
    override val equipment = "Equipaggiamento"
    override val header = "Intestazione"
    override val abilitiesLabel = "Abilita'"
    override val actions = "Azioni"
    override val reactions = "Reazioni"
    override val proficient = "Competente"
    override val expertise = "Maestria"
    override val fightingStyle = "Stile di combattimento"
    override val activeLabel = "Attivo"
    override val currentArmorClass = "CA attuale"
    override val hitPoints = "Punti ferita"
    override val hitDice = "Dadi vita"
    override val deathSaves = "TS contro morte"
    override val proficiencyBonus = "Bonus di competenza"
    override val heroicInspiration = "Ispirazione eroica"
    override val available = "Disponibile"
    override val cantripsHeading = "TRUCCHETTI"
    override val notAvailable = "Non disponibile"
    override val trainingAndProficiencies = "Addestramento e competenze"
    override val armorTraining = "Competenza nelle armature"
    override val savingThrow = "Tiro salvezza"
    override val disadvantageShort = " · svant."
    override val passivePerception = "Percezione passiva"
    override val modifier = "Modificatore"
    override val heal = "Cura"
    override val stabilize = "Stabilizza"
    override val conditions = "Condizioni"
    override fun effectiveSpeed(distance: String) = "Effettiva: $distance"

    // --- armi e capacita' -----------------------------------------------------

    override val weaponsAndCombatAbilities = "Armi e abilità da combattimento"
    override val attackBonusOrDc = "Bonus att. / CD"
    override val damageAndType = "Danno e tipo"
    override val addWeapon = "+ Aggiungi arma"
    override val addAbility = "+ Aggiungi abilità"
    override val fixedDamage = "Danno fisso"
    override val bonusAction = "Azione bonus"
    override val spellOrCantrip = "Incantesimo o trucchetto"
    override val attackAbilityCaps = "CARATTERISTICA DEL TIRO PER COLPIRE"
    override val spellcastingAbility = "Caratteristica da incantatore"
    override val toClassify = "Da classificare"
    override val unclassifiedEntryHint = "Voce importata da classificare: se è un'arma scegli " +
        "la caratteristica del tiro per colpire; altrimenti attiva “Incantesimo o trucchetto”. " +
        "Con un'armatura non competente resta esclusa dalla battaglia."
    override val areaDamageWithSave = "Danno ad area (incantesimo con TS)"
    override val savingThrowCaps = "TIRO SALVEZZA"
    override val halfDamageOnSave = "Metà danni con TS superato"
    override val fromAbilityCatalog = "Dal catalogo Abilità"
    override val addAbilityTitle = "Aggiungi abilità"
    override val emptyAbilityCatalog =
        "Il catalogo è vuoto. Crea prima un’abilità in Compendio → Abilità."
    override val legendaryAction = "Azione leggendaria"
    override val noCost = "Nessun costo"
    override fun areaOf(radius: String) = "Area $radius"
    override fun abilityMissingFromCatalog(abilityId: String) =
        "Abilità non più presente nel catalogo · $abilityId"

    // --- privilegi, talenti e note --------------------------------------------

    override val classFeatures = "Privilegi di classe"
    override val sectionFeatures = "privilegi"
    override val sectionFeats = "talenti"
    override val noFeaturesRecorded =
        "Nessun privilegio registrato: questa scheda non usa la progressione guidata."
    override val manageFeatures = "+ Gestisci privilegi"
    override val yourNotes = "NOTE TUE"
    override val speciesTraits = "Tratti della specie"
    override val noFeatsRecorded = "Nessun talento registrato."
    override val manageFeats = "+ Gestisci talenti"
    override val manageFeaturesTitle = "Gestisci privilegi"
    override val manageFeatsTitle = "Gestisci talenti"
    override val searchByNameRuleOrPrerequisite = "Cerca per nome, regola o prerequisito"
    override val allFeminine = "Tutte"
    override val onlyCompatibleWithClassAndLevel = "Solo compatibili con classe e livello"
    override val noEntryMatchesFilters = "Nessuna voce corrisponde ai filtri."
    override val outOfRequirements = "Fuori requisiti"
    override fun chooseFromCompendium(count: Int) = "Scegli dal Compendio · $count risultati"
    override fun sourcePage(page: Int) = "pag. $page"
    override fun classAndMinimumLevel(className: String, level: Int) = "$className $level+"
    override fun prerequisite(text: String) = "Prerequisito: $text"

    // --- incantesimi -------------------------------------------------------------

    override val castsSpells = "Questo personaggio lancia incantesimi"
    override val castingBlockedByArmor = "Lancio bloccato: manca la competenza nell'armatura " +
        "indossata. Gli incantesimi non saranno disponibili in combattimento."
    override val saveDc = "CD tiro salvezza"
    override val attackBonus = "Bonus di attacco"
    override val spellSlots = "Slot incantesimo"
    override val selectedCantripsAndSpells = "Trucchetti e incantesimi selezionati"
    override val concentrationInitial = " · C"
    override val ritualInitial = " · R"
    override val shortOrLongRest = "Riposo breve o lungo"
    override val weaponMasteries = "PADRONANZE D'ARME"
    override val backgroundAndTraits = "Storia e tratti caratteriali"
    override val magicItemAttunement = "Sintonia con oggetti magici"
    override fun levelHeading(level: Int) = "LIVELLO $level"
    override fun slotLevel(level: Int) = "Livello $level"
    override fun slotTotal(total: Int) = "Totali $total"
    override fun pactSlotLevel(level: Int) = "PATTO · LIVELLO $level"
    override fun spellcastingAbilityOf(className: String, abbreviation: String) =
        "$className: $abbreviation"

    // --- classe armatura -----------------------------------------------------------

    override val armorClassCalculation = "Calcolo della classe armatura"
    override val baseMethodCaps = "METODO BASE"
    override val dexterityContributionCaps = "CONTRIBUTO DI DESTREZZA"
    override val wornArmorCaps = "ARMATURA REALMENTE INDOSSATA"
    override val armorVariantCaps = "VARIANTE DELL'ARMATURA"
    override val armorClassModifiersCaps = "MODIFICATORI ALLA CA"
    override val noArmor = "Nessuna armatura"
    override val manualFinalArmorClass = "CA finale manuale"
    override val manualFinalHint = "Il valore viene usato esattamente com'è: scudo e altri " +
        "modificatori non vengono sommati una seconda volta."
    override val elvenChainManualHint = " Deve quindi comprendere anche il +1 del giaco elfico."
    override val baseStartingValue = "Valore iniziale della base"
    override val armorStrengthRequirement = "Requisito di Forza dell'armatura (0 = nessuno)"
    override val stealthDisadvantage = "Svantaggio a Destrezza (Furtività)"
    override val mithralNote = "Mithral: nessun requisito di Forza e nessuno svantaggio a Furtività."
    override val elvenChainNote =
        "Giaco elfico: +1 alla CA e competenza garantita in questa armatura."
    override val shieldNotEquipped = "Scudo non equipaggiato"
    override val shieldAlreadyInManual = "Scudo equipaggiato · già compreso nella CA manuale"
    override val shieldEquipped = "Scudo equipaggiato · +2"
    override val shieldWithoutProficiency = "Scudo equipaggiato · +0 (manca competenza)"
    override val shieldActionNote = "Indossare o togliere lo scudo richiede un'azione di Utilizzo."
    override val shieldNeedsProficiency = "Il bonus dello scudo richiede competenza negli scudi."
    override val manualTotalNote = "La CA manuale è già il totale finale. Scegli un altro metodo " +
        "base per gestire separatamente scudo, bonus e penalità."
    override val noOtherModifier = "Nessun altro modificatore. Puoi aggiungere oggetti magici, " +
        "privilegi, incantesimi o penalità."
    override val addModifier = "+ Aggiungi modificatore"
    override val newModifier = "Nuovo modificatore"
    override val unnamedModifier = "Modificatore"
    override val bonusOrPenalty = "Bonus/penalità"
    override val stealthDisadvantageWarning =
        "Questa armatura impone svantaggio alle prove di Destrezza (Furtività)."

    override fun overrideActive(value: Int) =
        "Override attivo: la CA $value sostituisce temporaneamente il calcolo senza cancellarne i dettagli."
    override fun restoreCalculatedArmorClass(value: Int) = "Ripristina CA calcolata ($value)"
    override fun baseArmorClass(value: Int) = "CA base $value"
    override fun armorClassModifiers(signed: String) = "Modificatori $signed"
    override fun calculatedArmorClass(value: Int) = "CA calcolata $value"
    override fun overrideValue(value: Int) = "Override $value"
    override fun missingArmorProficiency(category: String) = "Manca la competenza in $category. " +
        "Svantaggio a tutte le prove d20 relative a Forza o Destrezza; " +
        "il lancio degli incantesimi è bloccato."
    override fun strengthBelowRequirement(score: Int, required: Int, penalty: String) =
        "Forza $score inferiore al requisito $required: velocità ridotta di $penalty."
    override fun donDoffMinutes(don: Int, doff: Int) =
        "Equipaggiamento SRD: $don min per indossare, $doff min per togliere."
    override fun effectRow(source: String, text: String) = "◆ $source · $text"

    override fun effectOnArmorClass(amount: String, condition: String) =
        "$amount alla Classe Armatura$condition"
    override fun effectOnSpeed(distance: String, condition: String) =
        "$distance di velocità$condition"
    override fun effectOnAttack(amount: String, target: String, condition: String) =
        "$amount ai ${target.lowercase()}$condition"
    override fun manualFinalArmorClassIs(value: Int) = "CA attuale = CA finale manuale $value"
    override fun baseWithFullDexterity(base: Int, dexterity: String) = "$base + DES ($dexterity)"
    override fun baseWithCappedDexterity(base: Int, contribution: String) =
        "$base + DES ($contribution, massimo +2)"
    override fun baseWithoutDexterity(base: Int) = "$base, senza Destrezza"
    override fun plusSecondaryAbility(abbreviation: String, signed: String) =
        " + $abbreviation ($signed)"
    override fun equalsBaseArmorClass(detail: String, secondary: String, base: Int) =
        "$detail$secondary = CA base $base"
    override fun overrideSuffix(value: Int) = " · override $value"
    override fun shieldRow(signed: String) = "scudo $signed"
    override fun armorRuleRow(rule: String, signed: String) = "$rule $signed"
    override fun equalsCalculatedArmorClass(value: Int) = " = CA calcolata $value"
    override fun currentArmorClassOverride(value: Int) = " · CA attuale $value (override)"

    // --- progressione SRD ------------------------------------------------------------

    override val srdCreationTitle = "Creazione e livelli SRD 5.2.1"
    override val srdCreationBody = "La modalità guidata propone classe, competenze, privilegi, " +
        "talenti, trucchetti, incantesimi e risorse nelle quantità previste dallo SRD. " +
        "Le schede manuali esistenti restano invariate finché non la attivi."
    override val startGuidedCreation = "Avvia creazione guidata"
    override val srdProgressionTitle = "Progressione SRD 5.2.1"
    override val classResourcesCaps = "RISORSE DI CLASSE"
    override val shortRest = "Riposo breve"
    override val longRest = "Riposo lungo"
    override val longRestAndSwapForm = "Riposo lungo + sostituisci forma"
    override val swapKnownFormTitle = "Sostituisci una forma conosciuta"
    override val swapKnownFormBody = "Il comando completa un riposo lungo e sostituisce " +
        "esattamente una forma, come previsto da Forma Selvatica."
    override val formToForgetCaps = "FORMA DA DIMENTICARE"
    override val newFormCaps = "NUOVA FORMA"
    override val finishRestAndSwap = "Completa riposo e sostituisci"

    override fun classAndLevel(className: String, level: Int) = "$className $level"
    override fun proficiencyBonusIs(signed: String) = "Bonus competenza $signed"
    override fun experiencePoints(points: Int) = "$points PE"
    override fun levelUpAvailable(level: Int) =
        "Passaggio disponibile: i PE consentono il livello $level."
    override fun levelUpTo(level: Int) = "Sali al livello $level"
    override fun nextLevelAt(threshold: Int, missing: Int?) = buildString {
        append("Prossimo livello a ").append(threshold).append(" PE")
        if (missing != null) append(" · ne mancano ").append(missing)
        append('.')
    }
    override fun resourcePool(remaining: Int, maximum: Int, recovery: String) =
        "$remaining/$maximum · $recovery"
    override fun dieSuffix(sides: Int) = " · d$sides"
    override fun formSummary(name: String, summary: String) = "$name · $summary"

    // --- procedura guidata --------------------------------------------------------------

    override val guidedCreationTitle = "Creazione guidata SRD"
    override val srdLevelUpTitle = "Passaggio di livello SRD"
    override val chooseExactlyForFirstLevel =
        "Scegli esattamente le opzioni richieste per il 1º livello."
    override val multiclassNote = "Multiclasse: verranno verificati i punteggi minimi della " +
        "classe attuale e di quella nuova."
    override val newLevelHitPoints = "Punti ferita del nuovo livello"
    override val firstLevelUsesMaximum =
        "Al 1º livello si usa il massimo del Dado Vita + Costituzione."
    override val fixedValue = "Valore fisso"
    override val rollTheDie = "Tiro del dado"
    override val dieResultPlusConstitution = "Risultato dado + COS (minimo 1)"
    override val createCharacter = "Crea personaggio"
    override val applyLevel = "Applica livello"
    override val noOptionForClassAndLevel =
        "Nessuna opzione disponibile per classe e livello correnti."
    override val abilityScoreIncreaseTitle = "Aumento dei punteggi di caratteristica"
    override val abilityScoreIncreaseBody =
        "Assegna +2 a una caratteristica oppure +1 a due (massimo 20)."
    override fun levelAndExperience(level: Int, points: Int) = "Livello $level · $points PE"
    override fun choicesMade(selected: Int, required: Int) = "$selected/$required scelte"
    override fun searchAmongOptions(count: Int) = "Cerca tra $count opzioni"
    override fun fixedHitPoints(amount: Int) = "$amount PF"
    override fun applied(text: String) = "Applicato: $text"
    override fun backgroundAbilityScoresTitle(background: String) =
        "Punteggi di caratteristica · $background"
    override val backgroundAbilityScoresBody =
        "Assegna +2 e +1 a due caratteristiche, oppure +1 a tutte e tre (massimo 20)."
    override fun assignedOutOf(assigned: Int, total: Int) = "Assegnati $assigned/$total"

    // --- stat block delle creature ----------------------------------------------------

    override val unnamedCreature = "Creatura senza nome"
    override val defenceInitiativeHitPoints = "Difesa, iniziativa e punti ferita"
    override val armorClass = "Classe Armatura"
    override val initiativeModifier = "Mod. iniziativa"
    override val initiativeLabel = "Iniziativa"
    override val perceptionLabel = "Percezione"
    override val challengeRatingShort = "GS"
    override val selected = "Selezionato"
    override val marked = "Segnato"
    override val staticScore = "Punteggio statico"
    override val averageHitPoints = "PF medi"
    override val diceCount = "Numero dadi"
    override val onFoot = "A piedi"
    override val canHover = "Puo' fluttuare"
    override val typedDefences = "Difese tipizzate"
    override val damageImmunities = "Immunità ai danni"
    override val conditionImmunities = "Immunità alle condizioni"
    override val sensesLanguagesGear = "Sensi, lingue ed equipaggiamento"
    override val gear = "Gear (oggetti recuperabili)"
    override val challengeRating = "Grado di Sfida"
    override val baseXp = "PE base"
    override val lairXp = "PE in tana"
    override val treasureTheme = "Tema del tesoro"
    override val bonusActions = "Azioni Bonus"
    override val legendaryActions = "Azioni Leggendarie"
    override val makeAttackExecutable = "Rendi attacco eseguibile"
    override val attackBonusShort = "Bonus att."
    override val addEntry = "+ Aggiungi voce"
    override val vulnerabilities = "Vulnerabilità"
    override fun passive(value: Int) = "passiva $value"
    override fun sectionCount(title: String, count: Int) = "$title ($count)"
    override fun challengeRatingSummary(
        rating: String,
        xp: Long,
        lairXp: Long?,
        proficiency: String,
    ) = buildString {
        append(rating).append(" (PE ").append(xp)
        if (lairXp != null) append("; in tana ").append(lairXp)
        append("; BC ").append(proficiency).append(')')
    }
    override fun initiativeSummary(signed: String, score: Int) = "$signed ($score)"

    // --- campi e stati dei componenti ---------------------------------------------------

    override val notProficient = "Non competente"
    override val notSelected = "Non selezionato"
    override val notMarked = "Non segnato"
    override fun labelWithUnit(label: String, unit: String) = "$label ($unit)"

    // --- messaggi del view model -----------------------------------------------------------

    override val archiveLoaded = "Archivio caricato."
    override val newSheetFillAndSave = "Nuova scheda: compila e salva."
    override val sheetSaved = "Scheda salvata."
    override val sheetDeleted = "Scheda eliminata."
    override val sheetUpdatedFromBattle = "Scheda aggiornata dalla battaglia."
    override val statBlockUpdatedFromBattle = "Stat block aggiornato dalla battaglia."
    override val templateSheetsRestored = "Schede del template ripristinate."
    override val longRestResourcesRecovered = "Risorse da riposo lungo recuperate."
    override val shortRestResourcesRecovered = "Risorse da riposo breve recuperate."
    override val cannotSwapKnownForm = "Impossibile sostituire la forma conosciuta."
    override val srdCharacterCreated =
        "Personaggio SRD creato: completa i dettagli narrativi e salva."
    override val abilitySaved = "Abilità salvata."
    override val automaticAbilityMustStayActive =
        "Una capacità con effetto automatico deve restare attiva."
    override val abilityNotInCompendium = "Abilità non trovata nel Compendio."
    override val bundledSrdReadOnly =
        "Il contenuto SRD è in sola lettura. Duplicalo per creare una variante personale."
    override val bundledSrdCannotBeDeleted = "Il contenuto SRD incluso non può essere eliminato."
    override val abilityDeleted = "Abilità eliminata."
    override val unsavedChangesPrompt =
        "Ci sono modifiche non salvate: salva oppure conferma di volerle scartare."
    override val sheetNotFound = "Scheda non trovata."
    override fun diskError(detail: String) = "Errore su disco: $detail"
    override fun invalidSheet(detail: String) = "Scheda non valida: $detail"
    override fun longRestFormSwapped(oldName: String, newName: String) =
        "Riposo lungo completato: $oldName sostituita con $newName."
    override fun levelApplied(level: Int) = "Livello $level applicato. Controlla e salva la scheda."
    override fun entryNotInSection(section: String) =
        "La voce scelta non appartiene alla lista dei $section."
    override fun abilityAddedToSheet(name: String) = "«$name» aggiunto alla scheda."
    override fun abilityRemovedFromSheet(name: String) = "«$name» rimosso dalla scheda."
    override fun abilityBecamePassive(name: String) = "«$name» ora vale come tratto permanente."
    override fun abilityBecameActive(name: String) =
        "«$name» torna fra le capacità da spendere nel turno."
    override fun cannotDeleteAbilityInUse(usedBy: String) =
        "Impossibile eliminare: l'abilità è usata da $usedBy."
}
