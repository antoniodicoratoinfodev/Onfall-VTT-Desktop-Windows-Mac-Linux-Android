package app.d6d.ui.content

import app.d6d.rules.character.Ability
import app.d6d.rules.character.Ability.Companion.CHARISMA
import app.d6d.rules.character.Ability.Companion.CONSTITUTION
import app.d6d.rules.character.Ability.Companion.DEXTERITY
import app.d6d.rules.character.Ability.Companion.INTELLIGENCE
import app.d6d.rules.character.Ability.Companion.STRENGTH
import app.d6d.rules.character.Ability.Companion.WISDOM
import app.d6d.rules.character.CharacterClassId
import app.d6d.sheet.ArmorClassMethod
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick

/**
 * Le tre squadre incluse con l'app, una per grado di esperienza.
 *
 * Dodici personaggi per dodici classi dello SRD: chi apre un template trova
 * sempre un esempio compilato della classe che gli interessa. I punteggi seguono
 * la serie consigliata con i due incrementi del background gia' applicati, cosi'
 * ogni scheda e' rifacibile a mano dall'utente senza sorprese.
 */
internal class TemplateParties private constructor(private val language: AppLanguage) {

    /** Sceglie fra la forma italiana e quella inglese. */
    private fun say(italian: String, english: String): String = language.pick(italian, english)

    /** Serie consigliata 15/14/13/12/10/8 con +2 e +1 del background. */
    private fun scores(
        strength: Int,
        dexterity: Int,
        constitution: Int,
        intelligence: Int,
        wisdom: Int,
        charisma: Int,
    ): Map<Ability, Int> = mapOf(
        STRENGTH to strength,
        DEXTERITY to dexterity,
        CONSTITUTION to constitution,
        INTELLIGENCE to intelligence,
        WISDOM to wisdom,
        CHARISMA to charisma,
    )

    /** Squadra del 1º livello: le quattro classi con cui si impara il gioco. */
    val novices: List<TemplateCharacterPlan> = listOf(
        TemplateCharacterPlan(
            id = "pg-tarvos",
            name = say("Tarvos di Pietrafredda", "Tarvos of Coldstone"),
            classId = CharacterClassId.FIGHTER,
            level = 1,
            species = say("Umano", "Human"),
            background = say("Sentinella di frontiera", "Frontier sentry"),
            alignment = say("Legale Neutrale", "Lawful Neutral"),
            languages = say("Comune, Nanico", "Common, Dwarvish"),
            scores = scores(17, 13, 15, 10, 12, 8),
            preferences = listOf(
                "feat:origin:aggressore-selvaggio",
                "skill:atletica",
                "skill:percezione",
                "weapon:spada-lunga",
                "weapon:giavellotto",
                "fighting-style:difesa",
                "weapon:ascia-da-battaglia",
            ),
            abilityPriority = listOf(STRENGTH, CONSTITUTION),
            armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
            shieldEquipped = true,
            appearance = say("Spalle larghe, barba corta, una cicatrice bianca sullo zigomo sinistro.",
                "Broad shoulders, short beard, a white scar on the left cheekbone."),
            backstory = say("Dodici anni sulle mura del Vallo, finché la guarnigione non è stata sciolta.",
                "Twelve years on the Wall, until the garrison was disbanded."),
            equipment = say("Cotta di maglia, scudo, zaino da avventuriero, corda di canapa, razioni per 5 giorni.",
                "Chain mail, shield, explorer's pack, hempen rope, 5 days of rations."),
        ),
        TemplateCharacterPlan(
            id = "pg-nerea",
            name = say("Nerea del Faro", "Nerea of the Lighthouse"),
            classId = CharacterClassId.CLERIC,
            level = 1,
            species = say("Umano", "Human"),
            background = say("Custode di un faro", "Lighthouse keeper"),
            alignment = say("Neutrale Buono", "Neutral Good"),
            languages = say("Comune, Celestiale", "Common, Celestial"),
            scores = scores(13, 10, 15, 8, 17, 12),
            preferences = listOf(
                "feat:origin:allerta",
                "skill:medicina",
                "skill:religione",
                "weapon:mazza",
                "weapon:balestra-leggera",
                "feature:chierico:ordine-protettore",
                "spell:fiamma-sacra",
                "spell:guida",
                "spell:luce",
                "spell:cura-ferite",
                "spell:benedizione",
                "spell:scudo-della-fede",
                "spell:parola-guaritrice",
            ),
            abilityPriority = listOf(WISDOM, CONSTITUTION),
            armorClassMethod = ArmorClassMethod.SCALE_MAIL,
            shieldEquipped = true,
            appearance = say("Capelli raccolti, mantello incerato, un lanternino sempre acceso alla cintura.",
                "Hair tied back, waxed cloak, a small lantern always lit at the belt."),
            backstory = say("Teneva accesa la luce sugli scogli del Passo; ora la porta dove il buio è peggiore.",
                "She kept the light burning on the rocks of the Pass; now she carries it where the dark is worse."),
            equipment = say("Corazza a scaglie, scudo, simbolo sacro, olio per lanterna, bende.",
                "Scale mail, shield, holy symbol, lamp oil, bandages."),
        ),
        TemplateCharacterPlan(
            id = "pg-ilvo",
            name = say("Ilvo Passocorto", "Ilvo Shortstep"),
            classId = CharacterClassId.ROGUE,
            level = 1,
            species = say("Halfling", "Halfling"),
            background = say("Corriere di città", "City courier"),
            alignment = say("Caotico Buono", "Chaotic Good"),
            languages = say("Comune, Halfling", "Common, Halfling"),
            scores = scores(8, 17, 14, 12, 13, 10),
            preferences = listOf(
                "feat:origin:abile",
                "skill:furtivita",
                "skill:percezione",
                "skill:acrobazia",
                "skill:rapidita-di-mano",
                "weapon:spada-corta",
                "weapon:arco-corto",
                "weapon:pugnale",
            ),
            abilityPriority = listOf(DEXTERITY, CONSTITUTION),
            armorClassMethod = ArmorClassMethod.LEATHER,
            appearance = say("Minuto, occhi svegli, sempre con due tasche in più del necessario.",
                "Small, sharp-eyed, always with two more pockets than he needs."),
            backstory = say("Conosceva ogni tetto del quartiere basso; ora conosce ogni cunicolo delle rovine.",
                "He knew every roof in the low quarter; now he knows every tunnel in the ruins."),
            equipment = say("Armatura di cuoio, arnesi da scasso, rampino, sacchetto di biglie.",
                "Leather armor, thieves' tools, grappling hook, bag of marbles."),
        ),
        TemplateCharacterPlan(
            id = "pg-sibilla",
            name = say("Sibilla d'Ardo", "Sibilla of Ardo"),
            classId = CharacterClassId.WIZARD,
            level = 1,
            species = say("Elfo", "Elf"),
            background = say("Copista di biblioteca", "Library copyist"),
            alignment = say("Neutrale", "Neutral"),
            languages = say("Comune, Elfico, Draconico", "Common, Elvish, Draconic"),
            scores = scores(8, 14, 14, 17, 12, 10),
            preferences = listOf(
                "feat:origin:iniziato-alla-magia",
                "skill:arcano",
                "skill:storia",
                // Un incantatore con Forza 8 col bastone farebbe meno danni di un
                // pugno: le armi accurate almeno usano la sua Destrezza.
                "weapon:pugnale",
                "weapon:dardo",
                "spell:dardo-di-fuoco",
                "spell:raggio-di-gelo",
                "spell:prestidigitazione",
                "spell:dardo-incantato",
                "spell:scudo",
                "spell:sonno",
                "spell:mani-brucianti",
                "spell:armatura-magica",
                "spell:individuazione-del-magico",
            ),
            abilityPriority = listOf(INTELLIGENCE, CONSTITUTION),
            appearance = say("Dita macchiate d'inchiostro, occhiali di cristallo appesi al collo.",
                "Ink-stained fingers, crystal spectacles hung round the neck."),
            backstory = say("Ha copiato per anni libri che non poteva leggere. Poi ne ha letto uno.",
                "She copied books she was not allowed to read for years. Then she read one."),
            equipment = say("Libro degli incantesimi, borsa delle componenti, calamaio, tre candele.",
                "Spellbook, component pouch, inkwell, three candles."),
        ),
    )

    /** Squadra del 4º livello: gia' rodata, con la prima sottoclasse alle spalle. */
    val veterans: List<TemplateCharacterPlan> = listOf(
        TemplateCharacterPlan(
            id = "pg-gudrun",
            name = say("Gudrun Spaccascudi", "Gudrun Shieldbreaker"),
            classId = CharacterClassId.BARBARIAN,
            level = 4,
            species = say("Mezzorco", "Half-Orc"),
            background = say("Guida delle terre alte", "Highland guide"),
            alignment = say("Caotico Neutrale", "Chaotic Neutral"),
            languages = say("Comune, Orchesco", "Common, Orc"),
            scores = scores(17, 14, 16, 8, 12, 10),
            preferences = listOf(
                "feat:origin:aggressore-selvaggio",
                "skill:atletica",
                "skill:sopravvivenza",
                "weapon:ascia-bipenne",
                "weapon:giavellotto",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(STRENGTH, CONSTITUTION),
            appearance = say("Alta una spanna più di chiunque altro, treccia laterale, zanne corte.",
                "A head taller than anyone else, side braid, short tusks."),
            backstory = say("Portava le carovane oltre i passi. Una di quelle carovane non è mai arrivata.",
                "She took caravans over the passes. One of those caravans never arrived."),
            equipment = say("Ascia bipenne, giavellotti, pelli da viaggio, corno da segnale.",
                "Greataxe, javelins, travelling furs, signal horn."),
        ),
        TemplateCharacterPlan(
            id = "pg-lyra",
            name = say("Lyra Voceargento", "Lyra Silvervoice"),
            classId = CharacterClassId.BARD,
            level = 4,
            species = say("Umano", "Human"),
            background = say("Cantastorie da fiera", "Fairground storyteller"),
            alignment = say("Caotico Buono", "Chaotic Good"),
            languages = say("Comune, Silvano", "Common, Sylvan"),
            scores = scores(8, 14, 14, 10, 12, 17),
            preferences = listOf(
                "feat:origin:abile",
                "skill:persuasione",
                "skill:inganno",
                "skill:storia",
                "weapon:pugnale",
                "weapon:balestra-leggera",
                "spell:beffa-crudele",
                "spell:luci-danzanti",
                "spell:cura-ferite",
                "spell:charme-su-persone",
                "spell:sussurri-dissonanti",
                "spell:eroismo",
                "spell:invisibilita",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(CHARISMA, DEXTERITY),
            armorClassMethod = ArmorClassMethod.LEATHER,
            appearance = say("Giacca rattoppata di velluto, liuto con una crepa riparata d'argento.",
                "A patched velvet coat, a lute with a crack mended in silver."),
            backstory = say("Canta le imprese altrui da tanto tempo da volerne finalmente una propria.",
                "She has sung of other people's deeds long enough to want one of her own."),
            equipment = say("Liuto, armatura di cuoio, stocco, quaderno di ballate.",
                "Lute, leather armor, rapier, book of ballads."),
        ),
        TemplateCharacterPlan(
            id = "pg-aelis",
            name = say("Aelis Corvorosso", "Aelis Redcrow"),
            classId = CharacterClassId.RANGER,
            level = 4,
            species = say("Mezzelfo", "Half-Elf"),
            background = say("Battitrice di confine", "Border scout"),
            alignment = say("Neutrale Buono", "Neutral Good"),
            languages = say("Comune, Elfico", "Common, Elvish"),
            scores = scores(12, 17, 14, 10, 15, 8),
            preferences = listOf(
                "feat:origin:allerta",
                "skill:sopravvivenza",
                "skill:percezione",
                "skill:natura",
                "weapon:arco-lungo",
                "weapon:spada-corta",
                "fighting-style:tiro",
                "spell:marchio-del-cacciatore",
                "spell:cura-ferite",
                "spell:passo-veloce",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(DEXTERITY, WISDOM),
            armorClassMethod = ArmorClassMethod.STUDDED_LEATHER,
            appearance = say("Mantello grigioverde, una piuma nera legata alla faretra.",
                "Grey-green cloak, a black feather tied to the quiver."),
            backstory = say("Segue le tracce di ciò che ha bruciato il suo villaggio, e sa che l'ha quasi raggiunto.",
                "She follows the trail of whatever burned her village, and knows she has nearly caught it."),
            equipment = say("Arco lungo, quaranta frecce, cuoio borchiato, trappole a laccio.",
                "Longbow, forty arrows, studded leather, snare traps."),
        ),
        TemplateCharacterPlan(
            id = "pg-ysolde",
            name = say("Ysolde Fiammadraco", "Ysolde Dragonflame"),
            classId = CharacterClassId.SORCERER,
            level = 4,
            species = say("Draconide", "Dragonborn"),
            background = say("Erede di una casata caduta", "Heir of a fallen house"),
            alignment = say("Neutrale", "Neutral"),
            languages = say("Comune, Draconico", "Common, Draconic"),
            scores = scores(8, 14, 15, 12, 10, 17),
            preferences = listOf(
                "feat:origin:allerta",
                "skill:arcano",
                "skill:intimidire",
                "weapon:pugnale",
                "weapon:balestra-leggera",
                "spell:dardo-di-fuoco",
                "spell:prestidigitazione",
                "spell:luce",
                "spell:mani-brucianti",
                "spell:globo-cromatico",
                "spell:frantumare",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(CHARISMA, CONSTITUTION),
            appearance = say("Scaglie ramate lungo gli zigomi, occhi che riflettono la luce del fuoco.",
                "Copper scales along the cheekbones, eyes that catch firelight."),
            backstory = say("L'ultima della sua casata: il sangue che l'ha rovinata è anche ciò che la rende pericolosa.",
                "The last of her house: the blood that ruined it is also what makes her dangerous."),
            equipment = say("Pugnale cerimoniale, anello con sigillo, mantello foderato di rosso.",
                "Ceremonial dagger, signet ring, cloak lined in red."),
        ),
    )

    /** Squadra del 20º livello: il tetto dello SRD, per provare lo scontro finale. */
    /**
     * Tutte le ricette per identificativo.
     *
     * Serve a riconoscere, dentro una scheda gia' installata, quali campi sono
     * ancora quelli della ricetta e quali li ha riscritti chi gioca: i primi
     * seguono il cambio di lingua, i secondi no.
     */
    val plansById: Map<String, TemplateCharacterPlan> by lazy {
        (novices + veterans + legends).associateBy { it.id }
    }

    val legends: List<TemplateCharacterPlan> = listOf(
        TemplateCharacterPlan(
            id = "pg-aldemar",
            name = say("Aldemar della Fiamma Ferma", "Aldemar of the Steady Flame"),
            classId = CharacterClassId.PALADIN,
            level = 20,
            species = say("Umano", "Human"),
            background = say("Cavaliere di un ordine disciolto", "Knight of a disbanded order"),
            alignment = say("Legale Buono", "Lawful Good"),
            languages = say("Comune, Celestiale", "Common, Celestial"),
            scores = scores(17, 10, 14, 8, 12, 15),
            preferences = listOf(
                "feat:origin:allerta",
                "skill:persuasione",
                "skill:religione",
                "weapon:spada-lunga",
                "weapon:giavellotto",
                "fighting-style:difesa",
                "spell:punizione-divina",
                "spell:favore-divino",
                "spell:cura-ferite",
                // Il Dono epico va nominato prima dell'Aumento: al 19º livello sono
                // offerti insieme, e l'Aumento — che vale a ogni livello pari —
                // altrimenti se lo prenderebbe sempre lui.
                "feat:epic-boon:dono-abilita-combattimento",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(STRENGTH, CHARISMA, CONSTITUTION),
            armorClassMethod = ArmorClassMethod.PLATE,
            shieldEquipped = true,
            appearance = say("Armatura lucidata fino a specchiarsi, sopravveste bruciacchiata sul fianco.",
                "Armor polished to a mirror, surcoat scorched along one side."),
            backstory = say("L'ordine non esiste più. Il giuramento sì, e non ha bisogno di un ordine per valere.",
                "The order is gone. The oath is not, and it needs no order to hold."),
            equipment = say("Armatura a piastre, scudo con stemma raschiato, spada lunga, olio santo.",
                "Plate armor, shield with its blazon scraped off, longsword, holy oil."),
        ),
        TemplateCharacterPlan(
            id = "pg-maelis",
            name = say("Maelis del Bosco Alto", "Maelis of the High Wood"),
            classId = CharacterClassId.DRUID,
            level = 20,
            species = say("Elfo", "Elf"),
            background = say("Custode di un bosco antico", "Warden of an old wood"),
            alignment = say("Neutrale", "Neutral"),
            languages = say("Comune, Elfico, Silvano", "Common, Elvish, Sylvan"),
            scores = scores(8, 14, 16, 12, 17, 10),
            preferences = listOf(
                "feat:origin:abile",
                "skill:natura",
                "skill:percezione",
                "weapon:pugnale",
                "weapon:dardo",
                "spell:artificio-druidico",
                "spell:produrre-fiamma",
                "spell:randello-incantato",
                "spell:cura-ferite",
                "spell:intralciare",
                "spell:invocare-il-fulmine",
                "feat:epic-boon:dono-richiamo-incantesimi",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(WISDOM, CONSTITUTION),
            armorClassMethod = ArmorClassMethod.HIDE,
            appearance = say("Capelli grigio-verdi intrecciati con rametti vivi, piedi spesso scalzi.",
                "Grey-green hair braided with living twigs, feet more often bare than not."),
            backstory = say("Ha visto crescere tre volte lo stesso bosco. Non ne vedrà bruciare un quarto.",
                "She has watched the same wood grow three times. She will not watch a fourth burn."),
            equipment = say("Armatura di pelle, falcetto, focus druidico di vischio, sacchetto di semi.",
                "Hide armor, sickle, mistletoe druidic focus, pouch of seeds."),
        ),
        TemplateCharacterPlan(
            id = "pg-shen",
            name = say("Shen dei Passi Silenziosi", "Shen of the Silent Steps"),
            classId = CharacterClassId.MONK,
            level = 20,
            species = say("Umano", "Human"),
            background = say("Novizio di un monastero di montagna", "Novice of a mountain monastery"),
            alignment = say("Legale Neutrale", "Lawful Neutral"),
            languages = say("Comune, Nanico", "Common, Dwarvish"),
            scores = scores(12, 17, 14, 8, 16, 10),
            preferences = listOf(
                "feat:origin:allerta",
                "skill:acrobazia",
                "skill:furtivita",
                "weapon:spada-corta",
                "weapon:dardo",
                "feat:epic-boon:dono-spirito-notturno",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(DEXTERITY, WISDOM),
            appearance = say("Fascia annodata sull'avambraccio, cammina senza fare rumore neanche sulla ghiaia.",
                "A band knotted round the forearm; he walks silently even on gravel."),
            backstory = say("Il monastero gli ha insegnato la pazienza. Il mondo gli ha insegnato la fretta.",
                "The monastery taught him patience. The world taught him hurry."),
            equipment = say("Veste da viaggio, spada corta, ciotola di legno, corda di seta.",
                "Travelling robes, shortsword, wooden bowl, silk rope."),
        ),
        TemplateCharacterPlan(
            id = "pg-nyx",
            name = say("Nyx del Patto Cinereo", "Nyx of the Ashen Pact"),
            classId = CharacterClassId.WARLOCK,
            level = 20,
            species = say("Tiefling", "Tiefling"),
            background = say("Studiosa di rovine proibite", "Scholar of forbidden ruins"),
            alignment = say("Caotico Neutrale", "Chaotic Neutral"),
            languages = say("Comune, Infernale", "Common, Infernal"),
            scores = scores(8, 14, 15, 12, 10, 17),
            preferences = listOf(
                "feat:origin:abile",
                "skill:arcano",
                "skill:indagare",
                "weapon:pugnale",
                "weapon:balestra-leggera",
                "spell:deflagrazione-occulta",
                "spell:beffa-crudele",
                "feature:warlock:deflagrazione-agonizzante",
                "spell:intimorire-infernale",
                "spell:paura",
                "feat:epic-boon:dono-fato",
                "feat:general:aumento-punteggi-caratteristica",
            ),
            abilityPriority = listOf(CHARISMA, CONSTITUTION),
            armorClassMethod = ArmorClassMethod.STUDDED_LEATHER,
            appearance = say("Corna corte limate, occhi senza pupilla, cenere che non si toglie dalle maniche.",
                "Short filed horns, pupilless eyes, ash that will not come out of her sleeves."),
            backstory = say("Ha firmato per sapere. Sa. E adesso deve conviverci.",
                "She signed to know. She knows. Now she has to live with it."),
            equipment = say("Cuoio borchiato, tomo rilegato in pelle grigia, sigillo del patto, sale nero.",
                "Studded leather, a tome bound in grey hide, the pact's seal, black salt."),
        ),
    )

    companion object {
        private val byLanguage = mutableMapOf<AppLanguage, TemplateParties>()

        /** Le squadre in una lingua. Costruite al primo uso e poi riusate. */
        fun of(language: AppLanguage): TemplateParties = synchronized(byLanguage) {
            byLanguage.getOrPut(language) { TemplateParties(language) }
        }
    }
}
