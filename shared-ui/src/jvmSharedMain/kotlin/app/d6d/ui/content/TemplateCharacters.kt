package app.d6d.ui.content

import app.d6d.content.srd521it.SrdChoiceOption
import app.d6d.content.srd521it.SrdChoiceResolver
import app.d6d.rules.character.Ability
import app.d6d.rules.character.BackgroundDefinition
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.proficiencyBonusForLevel
import app.d6d.sheet.toWeaponEntry
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick

/**
 * Ricetta di un personaggio incluso nei template.
 *
 * Non contiene statistiche: contiene le **scelte**. Punti ferita, competenze,
 * privilegi, slot e risorse li calcola la creazione guidata dal content pack SRD,
 * cosi' un personaggio incluso e' identico a uno creato dall'utente e puo'
 * continuare a salire di livello dentro l'app.
 */
internal data class TemplateCharacterPlan(
    val id: String,
    val name: String,
    val classId: CharacterClassId,
    val level: Int,
    val species: String,
    val background: String,
    val alignment: String,
    val languages: String,
    /** Punteggi al 1º livello: gli Aumenti dei livelli successivi si sommano qui. */
    val scores: Map<Ability, Int>,
    /**
     * Frammenti di identificatore preferiti quando una scelta ha piu' opzioni
     * legali. Sono un desiderio, non un vincolo: cio' che non e' disponibile a
     * quel livello viene semplicemente ignorato e si ripiega sull'ordine del
     * content pack, quindi un personaggio si costruisce comunque.
     */
    val preferences: List<String> = emptyList(),
    /** Ordine con cui spendere gli Aumenti dei punteggi di caratteristica. */
    val abilityPriority: List<Ability> = emptyList(),
    /**
     * Armatura indossata dalla ricetta. La creazione guidata applica la dotazione
     * SRD, poi il template ripristina la configurazione narrativa già prevista;
     * "senza armatura" resta una formula, non un valore fisso.
     */
    val armorClassMethod: ArmorClassMethod = ArmorClassMethod.UNARMORED,
    val shieldEquipped: Boolean = false,
    val appearance: String = "",
    val backstory: String = "",
    val equipment: String = "",
)

/**
 * Costruisce i personaggi dei template salendo di livello un passo alla volta.
 *
 * E' la stessa strada della finestra di creazione guidata: per ogni livello si
 * chiedono le scelte richieste, si risolvono le opzioni legali e si applica
 * l'avanzamento. Nulla viene scritto a mano nella scheda, quindi il contenuto
 * incluso non puo' divergere dalle regole del content pack.
 */
internal object TemplateCharacters {

    /**
     * Le scelte si generano a cascata — una sottoclasse ne apre altre, che a loro
     * volta possono aprirne — quindi le passate si ripetono finche' l'elenco non
     * smette di cambiare. Alle catene dell'SRD ne bastano meno: il limite e' solo
     * il fermo che evita un ciclo infinito se un pack futuro ne annidasse di piu'.
     */
    private const val MAX_PASSES = 6

    fun build(plan: TemplateCharacterPlan, language: AppLanguage): CharacterSheet {
        val service = guidedCharacterServiceFor(language)
        val backgroundIncreases = plannedBackgroundIncreases(plan, language)
        var sheet = CharacterSheet(
            id = plan.id,
            // La lingua in cui il modello sta per essere scritto. Senza, un seme
            // generato in inglese finiva salvato come testo inglese marcato
            // italiano, e al riavvio la ritraduzione lo avrebbe peggiorato
            // invece di sistemarlo.
            contentLanguage = language,
            characterName = plan.name,
            species = plan.species,
            background = plan.background,
            alignment = plan.alignment,
            languages = plan.languages,
            // Le ricette storiche conservano i punteggi finali. Si sottraggono qui
            // i bonus del background, che la creazione guidata riapplica e registra.
            abilityScores = plan.scores.mapValues { (ability, score) ->
                score - (backgroundIncreases[ability] ?: 0)
            },
            armorClassMethod = plan.armorClassMethod,
            shieldEquipped = plan.shieldEquipped,
            appearance = plan.appearance,
            backstory = plan.backstory,
            equipment = plan.equipment,
        )
        repeat(plan.level) { index ->
            // I PE sono la soglia del livello che si sta prendendo: la progressione
            // esige che il personaggio se lo sia guadagnato prima di salire.
            sheet = sheet.copy(experiencePoints = ExperienceProgression.thresholdForLevel(index + 1))
            val current = sheet
            sheet = runCatching {
                service.advance(current, levelUpRequest(current, plan, language, service))
            }
                .getOrElse { failure ->
                    // Una ricetta sbagliata deve dire subito dove: senza il livello,
                    // il messaggio della progressione non basta a ritrovarne il punto.
                    throw IllegalStateException(
                        language.pick(
                            "«${plan.name}» non supera il ${index + 1}º livello: ${failure.message}",
                            "“${plan.name}” cannot pass level ${index + 1}: ${failure.message}",
                        ),
                        failure,
                    )
                }
        }
        return sheet.copy(
            weapons = sheet.weapons.map { it.atCurrentLevel(sheet, language) },
            armorClassMethod = plan.armorClassMethod,
            shieldEquipped = plan.shieldEquipped,
            equipment = plan.equipment,
        )
    }

    /**
     * Riallinea una riga delle armi al personaggio finito.
     *
     * L'arma entra nella scheda al livello in cui la si sceglie, con il bonus di
     * competenza e i punteggi di allora. Un personaggio incluso arriva pero' fino
     * al 20º in un colpo solo: senza questo ricalcolo si presenterebbe al tavolo
     * col tiro per colpire del 1º livello.
     */
    private fun WeaponEntry.atCurrentLevel(
        sheet: CharacterSheet,
        language: AppLanguage,
    ): WeaponEntry {
        val definition = srdPackFor(language).weapons.firstOrNull { it.name == name } ?: return this
        val refreshed = definition.toWeaponEntry(
            abilityScores = sheet.abilityScores,
            proficiencyBonus = proficiencyBonusForLevel(sheet.effectiveLevel),
            language = language,
        )
        return copy(attackBonus = refreshed.attackBonus, damageModifier = refreshed.damageModifier)
    }

    private fun levelUpRequest(
        sheet: CharacterSheet,
        plan: TemplateCharacterPlan,
        language: AppLanguage,
        service: GuidedCharacterService,
    ): LevelUpRequest {
        val classLevel = sheet.progression.levelIn(plan.classId) + 1
        var chosen = linkedMapOf<String, List<String>>()
        repeat(MAX_PASSES) {
            val requirements = service.requirements(sheet, plan.classId, chosen.selections())
            val live = requirements.mapTo(mutableSetOf()) { it.id }
            // Cambiare una scelta padre puo' revocare le figlie: quelle non piu'
            // richieste vanno tolte, altrimenti l'avanzamento le rifiuta.
            chosen = LinkedHashMap(chosen.filterKeys { it in live })
            requirements.forEach { choice ->
                if (chosen[choice.id]?.size == choice.count) return@forEach
                val options = SrdChoiceResolver.options(
                    choice,
                    plan.classId,
                    classLevel,
                    sheet,
                    chosen.selections(),
                    language,
                )
                chosen[choice.id] = pick(choice, options, plan, sheet, language)
            }
        }
        // L'elenco definitivo e' quello che vede la progressione con le scelte
        // convergute: e' su questo che l'avanzamento verifica di averle tutte.
        val requirements = service.requirements(sheet, plan.classId, chosen.selections())
        val selections = requirements.map { ChoiceSelection(it.id, chosen[it.id].orEmpty()) }
        return LevelUpRequest(
            classId = plan.classId,
            // I punti ferita fissi tengono i template riproducibili: un tiro casuale
            // darebbe schede diverse a ogni installazione.
            hitPointIncrease = service.fixedHitPointIncrease(sheet, plan.classId),
            usedFixedHitPoints = true,
            selections = selections,
            abilityScoreIncreases = abilityIncreases(requirements, selections, plan, sheet),
            backgroundAbilityScoreIncreases = if (sheet.progression.configured) {
                emptyMap()
            } else {
                plannedBackgroundIncreases(plan, language)
            },
        )
    }

    /** Prima le opzioni desiderate dalla ricetta, nell'ordine chiesto; poi le altre. */
    private fun pick(
        choice: ChoiceDefinition,
        options: List<SrdChoiceOption>,
        plan: TemplateCharacterPlan,
        sheet: CharacterSheet,
        language: AppLanguage,
    ): List<String> {
        if (choice.kind == ChoiceKind.BACKGROUND) {
            val id = plannedBackground(plan, language).id
            return options.firstOrNull { it.id == id }?.let { listOf(it.id) }.orEmpty()
        }
        val wanted = if (choice.kind == ChoiceKind.ABILITY_SCORE_INCREASE) {
            // Un +1 sprecato su una caratteristica gia' al massimo invaliderebbe il
            // livello: si offrono prima quelle che hanno ancora margine.
            plan.abilityPriority
                .filter { sheet.score(it) < MAX_ABILITY_SCORE }
                .map { ":ability:${it.name.lowercase()}" }
        } else {
            plan.preferences
        }
        val owned = ownedOptionIds(sheet)
        val taken = LinkedHashSet<String>()
        wanted.forEach { fragment ->
            if (taken.size == choice.count) return@forEach
            options
                .filter { it.id.contains(fragment) && it.id.isWorthTakingAgain(owned) }
                .forEach { option -> if (taken.size < choice.count) taken += option.id }
        }
        fallbackOrder(choice, options, sheet, language).forEach { option ->
            if (taken.size < choice.count) taken += option.id
        }
        return taken.toList()
    }

    /**
     * Una preferenza non si ripete su cio' che il personaggio ha gia'.
     *
     * Diverse opzioni dello SRD sono ripetibili, e una preferenza scritta una volta
     * verrebbe riletta a ogni livello: il talento Abile del 1º livello si
     * riprenderebbe a ogni Aumento regalando tre competenze per volta, e le
     * Conoscenze degli Antichi del warlock esaurirebbero i talenti Origini. Fa
     * eccezione l'Aumento dei punteggi di caratteristica, che esiste proprio per
     * essere ripreso ogni volta che la classe lo concede.
     */
    private fun String.isWorthTakingAgain(owned: Set<String>): Boolean =
        this !in owned || endsWith(ABILITY_SCORE_FEAT)

    private fun ownedOptionIds(sheet: CharacterSheet): Set<String> = with(sheet.progression) {
        (selectedFeatureIds + featIds + knownCantripIds + preparedSpellIds + spellbookSpellIds).toSet()
    }

    /**
     * Ordine di ripiego quando la ricetta non esprime una preferenza.
     *
     * Cio' che il personaggio ha gia' preso va in fondo: alcune opzioni sono
     * ripetibili e resterebbero in cima a ogni livello, prendendosi tutto lo
     * spazio — le Conoscenze degli Antichi del warlock, per dirne una, ogni volta
     * consumano un talento Origini diverso e dopo quattro giri non ne resta.
     *
     * Per gli incantesimi si parte invece dai livelli piu' alti, come farebbe chi
     * sceglie davvero: riempire il libro dal basso lascerebbe un mago di 20º senza
     * incantesimi di 3º fra cui pescare i propri Incantesimi personali.
     */
    private fun fallbackOrder(
        choice: ChoiceDefinition,
        options: List<SrdChoiceOption>,
        sheet: CharacterSheet,
        language: AppLanguage,
    ): List<SrdChoiceOption> {
        val owned = ownedOptionIds(sheet)
        val fresh = options.sortedBy { if (it.id in owned) 1 else 0 }
        return when (choice.kind) {
            ChoiceKind.PREPARED_SPELL,
            ChoiceKind.SPELLBOOK_SPELL,
            ChoiceKind.ALWAYS_PREPARED_SPELL,
            ChoiceKind.MAGICAL_DISCOVERY,
            -> fresh.sortedByDescending { spellLevel(it.id, language) }
            else -> fresh
        }
    }

    private fun spellLevel(optionId: String, language: AppLanguage): Int =
        srdPackFor(language).element(optionId)?.spell?.level ?: 0

    /**
     * Gli aumenti di caratteristica hanno due sorgenti che non convivono mai.
     *
     * Alcuni talenti chiedono a quale caratteristica applicare il proprio +1, e
     * allora la richiesta deve rispecchiare esattamente quella scelta. Il talento
     * Aumento dei punteggi di caratteristica non chiede nulla: distribuisce due
     * punti, che qui vanno alla caratteristica piu' importante del personaggio.
     */
    private fun abilityIncreases(
        requirements: List<ChoiceDefinition>,
        selections: List<ChoiceSelection>,
        plan: TemplateCharacterPlan,
        sheet: CharacterSheet,
    ): Map<Ability, Int> {
        val byChoice = selections.associate { it.choiceId to it.optionIds }
        val fromFeatChoices = requirements
            .filter { it.kind == ChoiceKind.ABILITY_SCORE_INCREASE }
            .flatMap { byChoice[it.id].orEmpty() }
            .mapNotNull(::abilityOf)
            .groupingBy { it }
            .eachCount()
        if (fromFeatChoices.isNotEmpty()) return fromFeatChoices
        val takesIncrease = selections
            .flatMap { it.optionIds }
            .any { it.endsWith(ABILITY_SCORE_FEAT) }
        if (!takesIncrease) return emptyMap()
        val order = (plan.abilityPriority + Ability.entries).distinct()
        order.firstOrNull { sheet.score(it) <= MAX_ABILITY_SCORE - 2 }?.let { return mapOf(it to 2) }
        val split = order.filter { sheet.score(it) < MAX_ABILITY_SCORE }.take(2)
        return if (split.size == 2) split.associateWith { 1 } else emptyMap()
    }

    private fun abilityOf(optionId: String): Ability? {
        val slug = optionId.substringAfterLast(':')
        return Ability.entries.firstOrNull { it.name.lowercase() == slug }
    }

    private fun plannedBackground(
        plan: TemplateCharacterPlan,
        language: AppLanguage,
    ): BackgroundDefinition {
        val backgrounds = srdPackFor(language).backgrounds
        val preferredFeat = plan.preferences.firstOrNull { ":feat:origin:" in it }
        val matchingFeat = backgrounds.filter { background ->
            preferredFeat != null && background.featId.contains(preferredFeat)
        }
        if (matchingFeat.size == 1) return matchingFeat.single()
        if (matchingFeat.size > 1) {
            val preferredList = when (plan.classId) {
                CharacterClassId.CLERIC -> ":chierico"
                CharacterClassId.WIZARD -> ":mago"
                else -> null
            }
            matchingFeat.firstOrNull { preferredList != null && it.magicInitiateListId?.endsWith(preferredList) == true }
                ?.let { return it }
            return matchingFeat.maxWith(
                compareBy<BackgroundDefinition> {
                    plan.abilityPriority.count { ability -> ability in it.abilityOptions }
                }.thenBy { it.name },
            )
        }
        return backgrounds.maxWith(
            compareBy<BackgroundDefinition> {
                plan.abilityPriority.count { ability -> ability in it.abilityOptions }
            }.thenBy { it.name },
        )
    }

    private fun plannedBackgroundIncreases(
        plan: TemplateCharacterPlan,
        language: AppLanguage,
    ): Map<Ability, Int> {
        val background = plannedBackground(plan, language)
        val order = (plan.abilityPriority + background.abilityOptions.sortedBy { it.ordinal })
            .distinct()
            .filter { it in background.abilityOptions }
        return mapOf(order[0] to 2, order[1] to 1)
    }

    private fun Map<String, List<String>>.selections(): List<ChoiceSelection> =
        map { ChoiceSelection(it.key, it.value) }

    private const val MAX_ABILITY_SCORE = 20
    private const val ABILITY_SCORE_FEAT = ":aumento-punteggi-caratteristica"
}
