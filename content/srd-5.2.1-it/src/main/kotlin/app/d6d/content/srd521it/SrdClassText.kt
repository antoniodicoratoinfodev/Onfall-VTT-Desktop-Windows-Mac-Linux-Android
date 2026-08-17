package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.label
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ClassDefinition

/**
 * L'edizione inglese delle dodici classi, derivata da quella italiana.
 *
 * Le classi sono l'unico contenuto del pacchetto scritto a mano invece che
 * estratto dal PDF: duemilaquattrocento righe di progressione, scelte e risorse.
 * Tradurle vorrebbe dire duplicarle, e due copie di una tabella di progressione
 * divergono al primo errore corretto in una sola. Qui la struttura resta una,
 * e cambia solo il testo che si legge a schermo.
 *
 * Vengono tradotti anche i campi che oggi nessuna schermata mostra —
 * `startingEquipment`, `weaponTraining`, le descrizioni delle risorse — perche'
 * «oggi non si vede» non e' una proprieta' stabile: sono campi pubblici del
 * modello, e il giorno che una schermata li pesca comparirebbe una frase
 * italiana in mezzo a una scheda inglese, senza che nulla abbia segnalato
 * niente. La dotazione iniziale in prosa si *compone* da quella strutturata,
 * che e' gia' tradotta, invece di essere riscritta a mano: due elenchi delle
 * stesse cose divergono al primo ritocco a uno solo.
 */
internal fun ClassDefinition.translatedTo(language: AppLanguage): ClassDefinition {
    if (language == AppLanguage.ITALIAN) return this
    return copy(
        name = id.label(language),
        skillChoice = skillChoice.translated(language),
        toolChoice = toolChoice?.translated(language),
        startingWeaponChoice = startingWeaponChoice?.translated(language),
        startingEquipmentChoice = startingEquipmentChoice?.translated(language),
        multiclassSkillChoice = multiclassSkillChoice?.translated(language),
        multiclassToolChoice = multiclassToolChoice?.translated(language),
        levels = levels.map { level ->
            level.copy(
                choices = level.choices.map { it.translated(language) },
                languageProficiencyGrants = level.languageProficiencyGrants.map(::translateLanguage),
                effects = level.effects.map { effect ->
                    effect.copy(source = ENGLISH_EFFECT_SOURCES.getValue(effect.source))
                },
            )
        },
        resources = resources.map { resource ->
            resource.copy(
                name = translateResourceName(resource.name),
                description = resource.description
                    .takeIf { it.isNotBlank() }
                    ?.let { ENGLISH_RESOURCE_NOTES.getValue(it) }
                    .orEmpty(),
            )
        },
        weaponTraining = ENGLISH_WEAPON_TRAINING.getValue(weaponTraining),
        multiclassWeaponTraining = multiclassWeaponTraining
            .takeIf { it.isNotBlank() }
            ?.let { ENGLISH_WEAPON_TRAINING.getValue(it) }
            .orEmpty(),
        startingEquipment = englishStartingEquipment(id),
    )
}

/**
 * La dotazione iniziale in prosa, ricomposta dai pacchetti gia' tradotti.
 *
 * Il campo e' il residuo testuale che `startingEquipmentChoice` ha sostituito.
 * Comporlo invece di riscriverlo evita di mantenere due elenchi della stessa
 * dotazione, che e' esattamente il modo in cui i due finirebbero per non dire
 * piu' la stessa cosa.
 */
private fun englishStartingEquipment(classId: CharacterClassId): String {
    val packages = SrdStartingEquipment.all(AppLanguage.ENGLISH)
        .filter { ":equipment:class:${classId.contentId}:" in it.id }
    if (packages.isEmpty()) return ""
    val letters = packages.indices.map { ('A' + it) }
    return packages
        .mapIndexed { index, pack -> "(${letters[index]}) ${pack.description.trimEnd('.')}" }
        .joinToString(
            prefix = "Choose one of ${letters.joinToString(" or ")}: ",
            separator = "; ",
            postfix = ".",
        )
}

private val ENGLISH_WEAPON_TRAINING = mapOf(
    "Armi semplici" to "Simple weapons",
    "Armi da guerra" to "Martial weapons",
    "Armi semplici e da guerra" to "Simple and Martial weapons",
    "Armi semplici e armi da guerra con la proprietà accurata o leggera" to
        "Simple weapons and Martial weapons that have the Finesse or Light property",
    "Armi semplici e armi da guerra con la proprietà leggera" to
        "Simple weapons and Martial weapons that have the Light property",
)

private val ENGLISH_EFFECT_SOURCES = mapOf(
    "Movimento veloce" to "Fast Movement",
    "Movimento senza armatura" to "Unarmored Movement",
)

// Note operative delle risorse: non compaiono a schermo oggi, ma sono campi
// pubblici, e un campo pubblico prima o poi qualcuno lo mostra.
private val ENGLISH_RESOURCE_NOTES = mapOf(
    "Dopo l'uso gratuito può essere riattivata spendendo 3 punti stregoneria." to
        "After the free use, it can be activated again by spending 3 Sorcery Points.",
    "Dopo l'uso gratuito può essere riattivata spendendo uno slot del Patto." to
        "After the free use, it can be activated again by spending a Pact Magic slot.",
    "Dopo l'uso gratuito può essere riattivata spendendo uno slot di 5º." to
        "After the free use, it can be activated again by spending a level 5 spell slot.",
    "Dopo l'uso gratuito può essere riutilizzata spendendo un'Ira." to
        "After the free use, it can be used again by spending a Rage.",
    "Durante un riposo breve recupera punti stregoneria pari a metà del livello da Stregone, " +
        "arrotondata per difetto." to
        "On a Short Rest, you regain Sorcery Points equal to half your Sorcerer level, rounded down.",
    "Il dado associato è il dado di Arti marziali del livello corrente." to
        "The associated die is your Martial Arts die for your current level.",
    "Il pool deve filtrare prerequisiti, ripetibilità e suppliche già possedute." to
        "The pool must filter by prerequisites, repeatability, and invocations you already have.",
    "Incantesimi da chierico, druido o mago di un livello lanciabile." to
        "Cleric, Druid, or Wizard spells of a level you can cast.",
    "La scelta può essere cambiata al termine di un riposo lungo." to
        "You can change the choice at the end of a Long Rest.",
    "Lanci gratuiti di Marchio del cacciatore." to "Free castings of Hunter's Mark.",
    "Lancio gratuito separato per il primo Incantesimo personale." to
        "A separate free casting for the first Signature Spell.",
    "Lancio gratuito separato per il secondo Incantesimo personale." to
        "A separate free casting for the second Signature Spell.",
    "Le opzioni già possedute non possono essere scelte di nuovo." to
        "Options you already have can't be chosen again.",
    "Puoi cambiare il tipo al termine di un riposo breve o lungo." to
        "You can change the type at the end of a Short or Long Rest.",
    "Puoi sostituire l'opzione con l'altra al termine di un riposo breve o lungo." to
        "You can swap the option for the other one at the end of a Short or Long Rest.",
    "Recupera metà degli slot del Patto arrotondata per eccesso; al 20º livello li recupera tutti." to
        "You regain half your Pact Magic slots, rounded up; at level 20 you regain all of them.",
    "Recupera slot per livelli totali fino a metà del livello da Druido arrotondata per eccesso; " +
        "nessuno slot di 6º o superiore." to
        "You regain slots with a combined level up to half your Druid level, rounded up; " +
        "none of level 6 or higher.",
    "Recupera un utilizzo con un riposo breve e tutti con un riposo lungo." to
        "You regain one use on a Short Rest and all of them on a Long Rest.",
    "Un utilizzo per riposo lungo; al 20º Desiderio può imporre un recupero speciale di 2d4 riposi lunghi." to
        "One use per Long Rest; at level 20, Wish can impose a special recovery of 2d4 Long Rests.",
    "Una volta per riposo lungo, ripristina tutti gli utilizzi di Ira." to
        "Once per Long Rest, it restores all your Rage uses.",
    "Una volta per riposo lungo, spendi Forma selvatica per ottenere uno slot di 1º livello." to
        "Once per Long Rest, expend a Wild Shape use to gain a level 1 spell slot.",
    "Uno stile già posseduto non può essere scelto di nuovo." to
        "A Fighting Style you already have can't be chosen again.",
    "Utilizzi pari al modificatore di Carisma (minimo 1). Dal 5º livello si recuperano con un " +
        "riposo breve o lungo; prima del 5º, solo con un riposo lungo." to
        "Uses equal to your Charisma modifier (minimum 1). From level 5 they return on a Short or " +
        "Long Rest; before level 5, only on a Long Rest.",
)

private fun ChoiceDefinition.translated(language: AppLanguage): ChoiceDefinition =
    copy(
        title = translateChoiceTitle(title, language),
        description = description
            .takeIf { it.isNotBlank() }
            ?.let { ENGLISH_RESOURCE_NOTES.getValue(it) }
            .orEmpty(),
    )

/**
 * Il titolo di una scelta guidata.
 *
 * Quasi tutti sono fissi e stanno nella tavola. I pochi che interpolano un
 * numero — quante armi, quante Metamagie, quale livello di Arcanum — si
 * riconoscono per forma: mapparli come stringhe intere vorrebbe dire una voce
 * per ciascun valore, e una dimenticata al primo valore nuovo.
 */
private fun translateChoiceTitle(title: String, language: AppLanguage): String {
    if (language == AppLanguage.ITALIAN) return title
    STARTING_WEAPONS.matchEntire(title)?.let {
        return "Choose ${it.groupValues[1]} starting weapons from your class list"
    }
    METAMAGIC.matchEntire(title)?.let {
        return "Choose ${it.groupValues[1]} Metamagic options"
    }
    MYSTIC_ARCANUM.matchEntire(title)?.let {
        return "Choose your level ${it.groupValues[1]} Mystic Arcanum"
    }
    GAIN_PROFICIENCY.matchEntire(title)?.let {
        // La coda e' il nome di uno strumento, non un altro titolo: «Ottieni
        // competenza: Borsa da erborista». Ricorrere qui la cercava fra i
        // titoli e non la trovava.
        val tool = it.groupValues[1]
        return "Gain proficiency: ${ENGLISH_ITEMS.getValue(tool)}"
    }
    SKILLS.matchEntire(title)?.let {
        return "Choose ${it.count()} skill proficiencies"
    }
    EXPERTISE.matchEntire(title)?.let {
        return "Choose ${it.count()} proficiencies to gain Expertise in"
    }
    WEAPON_MASTERY.matchEntire(title)?.let {
        return "Choose ${it.count()} weapon Masteries"
    }
    INVOCATIONS.matchEntire(title)?.let {
        return "Choose ${it.count()} Eldritch Invocations"
    }
    EVOKER_SPELLS.matchEntire(title)?.let {
        val spells = if (it.count() == "1") "one Evocation spell" else "${it.count()} Evocation spells"
        return "Scholar of Evocation: add $spells (up to level ${it.groupValues[3]})"
    }
    return ENGLISH_CHOICE_TITLES.getValue(title)
}

/**
 * Il numero della scelta, con «una» che vale uno.
 *
 * L'italiano alterna la parola e la cifra («una competenza», «due competenze»);
 * l'inglese usa sempre la cifra, quindi qui basta ricondurre l'articolo a 1.
 * La cifra sta nel secondo gruppo: il primo e' il ramo vuoto che segna «una».
 */
private fun MatchResult.count(): String = groupValues[2].ifBlank { "1" }

private val STARTING_WEAPONS = Regex("""Scegli (\d+) armi iniziali fra quelle della classe""")
private val METAMAGIC = Regex("""Scegli (\d+) opzioni di Metamagia""")
private val MYSTIC_ARCANUM = Regex("""Scegli l'Arcanum mistico di (\d+)º livello""")
private val GAIN_PROFICIENCY = Regex("""Ottieni competenza: (.+)""")
private val SKILLS = Regex("""Scegli (?:una()|(\d+)) competenz[ae] in abilità""")
private val EXPERTISE = Regex("""Scegli (?:una()|(\d+)) competenz[ae] in cui ottenere Maestria""")
private val WEAPON_MASTERY = Regex("""Scegli (?:una()|(\d+)) Padronanz[ae] d'arma""")
private val INVOCATIONS = Regex("""Scegli (?:una()|(\d+)) Supplic[ah](?:e)? occult[ae]""")
private val EVOKER_SPELLS = Regex(
    """Invocatore sapiente: aggiungi (?:un()|(\d+)) incantesim[oi] di Invocazione \(fino al (\d+)º\)""",
)

private val ENGLISH_CHOICE_TITLES = mapOf(
    "Conoscenza primordiale: scegli un'altra abilità da barbaro" to
        "Primal Knowledge: choose another Barbarian skill",
    "Esploratore esperto: scegli due lingue standard" to
        "Deft Explorer: choose two standard languages",
    "Maestria negli incantesimi: scegli un incantesimo di 1º livello" to
        "Spell Mastery: choose a level 1 spell",
    "Maestria negli incantesimi: scegli un incantesimo di 2º livello" to
        "Spell Mastery: choose a level 2 spell",
    "Preda del Cacciatore: scegli un'opzione" to "Hunter's Prey: choose an option",
    "Resilienza immonda: scegli un tipo di danno" to
        "Fiendish Resilience: choose a damage type",
    "Scegli Aumento dei punteggi di caratteristica o un talento Generale" to
        "Choose Ability Score Improvement or a General feat",
    "Scegli due Incantesimi personali di 3º livello" to
        "Choose two level 3 Signature Spells",
    "Scegli due forme bestiali aggiuntive (GS massimo 1, volo consentito)" to
        "Choose two more beast forms (CR 1 or lower, flying allowed)",
    "Scegli due forme bestiali aggiuntive (GS massimo 1/2, senza volo)" to
        "Choose two more beast forms (CR 1/2 or lower, no flying)",
    "Scegli il tipo di danno dell'Affinità elementale" to
        "Choose your Elemental Affinity damage type",
    "Scegli il tipo di terra del Circolo" to "Choose your Circle's land type",
    "Scegli la sottoclasse" to "Choose your subclass",
    "Scegli quattro forme bestiali (GS massimo 1/4, senza volo)" to
        "Choose four beast forms (CR 1/4 or lower, no flying)",
    "Scegli tre strumenti musicali" to "Choose three Musical Instruments",
    "Scegli un Dono epico o un altro talento di cui possiedi i prerequisiti" to
        "Choose an Epic Boon or another feat whose prerequisites you meet",
    "Scegli un Ordine divino" to "Choose a Divine Order",
    "Scegli un Ordine primordiale" to "Choose a Primal Order",
    "Scegli un'opzione di Colpi benedetti" to "Choose a Blessed Strikes option",
    "Scegli un'opzione di Furia elementale" to "Choose an Elemental Fury option",
    "Scegli uno Stile di combattimento" to "Choose a Fighting Style",
    "Scegli uno strumento da artigiano o musicale" to
        "Choose an Artisan's Tool or a Musical Instrument",
    "Scegli uno strumento musicale" to "Choose a Musical Instrument",
    "Scoperte magiche: scegli due incantesimi" to "Magical Discoveries: choose two spells",
    "Studioso: scegli una competenza in cui ottenere Maestria" to
        "Scholar: choose a proficiency to gain Expertise in",
    "Tattiche difensive: scegli un'opzione" to "Defensive Tactics: choose an option",
)

private fun translateResourceName(name: String): String {
    ARCANUM_RESOURCE.matchEntire(name)?.let {
        return "Mystic Arcanum (level ${it.groupValues[1]})"
    }
    return ENGLISH_RESOURCES.getValue(name)
}

private val ARCANUM_RESOURCE = Regex("""Arcanum mistico \((\d+)º\)""")

// Chiave: il nome italiano della risorsa, che e' univoco fra le dodici classi.
private val ENGLISH_RESOURCES = mapOf(
    "Ali di drago" to "Dragon Wings",
    "Azione impetuosa" to "Action Surge",
    "Colpo di fortuna" to "Stroke of Luck",
    "Contatta patrono: lancio gratuito" to "Contact Patron: free casting",
    "Dono degli abissi: lancio gratuito" to "Gift of the Depths: free casting",
    "Dono del protettore" to "Gift of the Protectors",
    "Fido destriero: lancio gratuito" to "Find Steed: free casting",
    "Forma selvatica" to "Wild Shape",
    "Fortuna dell'Oscuro" to "Dark One's Own Luck",
    "Imposizione delle mani" to "Lay On Hands",
    "Incanalare divinità" to "Channel Divinity",
    "Incantesimo personale I" to "Signature Spell I",
    "Incantesimo personale II" to "Signature Spell II",
    "Indomabile" to "Indomitable",
    "Instancabile: punti ferita temporanei" to "Tireless: Temporary Hit Points",
    "Integrità del corpo" to "Wholeness of Body",
    "Intervento divino" to "Divine Intervention",
    "Ira" to "Rage",
    "Ira persistente: ripristino" to "Persistent Rage: restore",
    "Ispirazione bardica" to "Bardic Inspiration",
    "Mago della natura" to "Nature Magician",
    "Metabolismo straordinario" to "Uncanny Metabolism",
    "Nemico prescelto" to "Favored Enemy",
    "Nube sacra" to "Holy Nimbus",
    "Presenza intimidatoria" to "Intimidating Presence",
    "Punizione del paladino: lancio gratuito" to "Divine Smite: free casting",
    "Punti concentrazione" to "Focus Points",
    "Punti stregoneria" to "Sorcery Points",
    "Recuperare energie" to "Second Wind",
    "Recupero arcano" to "Arcane Recovery",
    "Recupero naturale: lancio gratuito" to "Natural Recovery: free casting",
    "Recupero naturale: recupero slot" to "Natural Recovery: slot recovery",
    "Rinascita selvatica: slot gratuito" to "Wild Resurgence: free slot",
    "Ripristino stregonesco" to "Sorcerous Restoration",
    "Scagliare all'Inferno" to "Hurl Through Hell",
    "Scaltrezza magica" to "Magical Cunning",
    "Seguace draconico: lancio gratuito" to "Dragon Companion: free casting",
    "Slot di Magia del patto" to "Pact Magic slots",
    "Stregoneria innata" to "Innate Sorcery",
    "Velo della natura" to "Nature's Veil",
)

private fun translateLanguage(name: String): String = ENGLISH_LANGUAGES.getValue(name)

private val ENGLISH_LANGUAGES = mapOf(
    "Druidico" to "Druidic",
    "Gergo ladresco" to "Thieves' Cant",
)
