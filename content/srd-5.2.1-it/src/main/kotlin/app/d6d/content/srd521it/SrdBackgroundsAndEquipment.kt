package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.label
import app.d6d.rules.character.Ability
import app.d6d.rules.character.BackgroundDefinition
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.EquipmentPackageDefinition
import app.d6d.rules.character.Skill
import app.d6d.rules.character.StartingArmor

private const val PREFIX = "srd521-it"

private fun weapon(slug: String) = "$PREFIX:weapon:$slug"
private fun tool(slug: String) = "$PREFIX:tool:$slug"
private fun equipment(owner: String, option: String) = "$PREFIX:equipment:$owner:$option"

private fun equipmentChoice(owner: String, title: String, vararg options: String) = ChoiceDefinition(
    id = "$PREFIX:choice:$owner:equipment",
    title = title,
    kind = ChoiceKind.STARTING_EQUIPMENT,
    count = 1,
    optionIds = options.toList(),
)

private fun fixedToolChoice(owner: String, name: String, slug: String) = ChoiceDefinition(
    id = "$PREFIX:choice:$owner:tool",
    title = "$name: competenza negli strumenti",  // tradotto in blocco piu' sotto
    kind = ChoiceKind.TOOL_PROFICIENCY,
    count = 1,
    optionIds = listOf(tool(slug)),
)

/** I quattro background completi distribuiti nello SRD 5.2.1 italiano. */
object SrdBackgrounds {
    fun all(language: AppLanguage): List<BackgroundDefinition> = when (language) {
        AppLanguage.ITALIAN -> italian
        AppLanguage.ENGLISH -> english
    }

    private val english: List<BackgroundDefinition> by lazy {
        italian.map { background ->
            val slug = background.id.substringAfterLast(':')
            val text = ENGLISH_BACKGROUNDS.getValue(slug)
            background.copy(
                name = text.name,
                description = text.description,
                toolChoice = background.toolChoice.copy(title = text.toolTitle),
                equipmentChoice = background.equipmentChoice.copy(title = text.equipmentTitle),
            )
        }
    }

    private val italian: List<BackgroundDefinition> = listOf(
        BackgroundDefinition(
            id = "$PREFIX:background:accolito",
            name = "Accolito",
            abilityOptions = setOf(Ability.INTELLIGENCE, Ability.WISDOM, Ability.CHARISMA),
            featId = "$PREFIX:feat:origin:iniziato-alla-magia",
            skillProficiencies = setOf(Skill.INTUIZIONE, Skill.RELIGIONE),
            toolChoice = fixedToolChoice("background:accolito", "Accolito", "scorte-da-calligrafo"),
            equipmentChoice = equipmentChoice(
                "background:accolito",
                "Accolito: scegli la dotazione o 50 mo",
                equipment("background:accolito", "a"),
                equipment("background:accolito", "b"),
            ),
            magicInitiateListId = "$PREFIX:spell-list:chierico",
            description = "Intelligenza, Saggezza, Carisma · Iniziato alla magia (chierico) · " +
                "Intuizione, Religione · scorte da calligrafo.",
            sourcePage = 93,
        ),
        BackgroundDefinition(
            id = "$PREFIX:background:criminale",
            name = "Criminale",
            abilityOptions = setOf(Ability.DEXTERITY, Ability.CONSTITUTION, Ability.INTELLIGENCE),
            featId = "$PREFIX:feat:origin:allerta",
            skillProficiencies = setOf(Skill.RAPIDITA_DI_MANO, Skill.FURTIVITA),
            toolChoice = fixedToolChoice("background:criminale", "Criminale", "arnesi-da-scasso"),
            equipmentChoice = equipmentChoice(
                "background:criminale",
                "Criminale: scegli la dotazione o 50 mo",
                equipment("background:criminale", "a"),
                equipment("background:criminale", "b"),
            ),
            description = "Destrezza, Costituzione, Intelligenza · Allerta · " +
                "Rapidità di mano, Furtività · arnesi da scasso.",
            sourcePage = 93,
        ),
        BackgroundDefinition(
            id = "$PREFIX:background:sapiente",
            name = "Sapiente",
            abilityOptions = setOf(Ability.CONSTITUTION, Ability.INTELLIGENCE, Ability.WISDOM),
            featId = "$PREFIX:feat:origin:iniziato-alla-magia",
            skillProficiencies = setOf(Skill.ARCANO, Skill.STORIA),
            toolChoice = fixedToolChoice("background:sapiente", "Sapiente", "scorte-da-calligrafo"),
            equipmentChoice = equipmentChoice(
                "background:sapiente",
                "Sapiente: scegli la dotazione o 50 mo",
                equipment("background:sapiente", "a"),
                equipment("background:sapiente", "b"),
            ),
            magicInitiateListId = "$PREFIX:spell-list:mago",
            description = "Costituzione, Intelligenza, Saggezza · Iniziato alla magia (mago) · " +
                "Arcano, Storia · scorte da calligrafo.",
            sourcePage = 93,
        ),
        BackgroundDefinition(
            id = "$PREFIX:background:soldato",
            name = "Soldato",
            abilityOptions = setOf(Ability.STRENGTH, Ability.DEXTERITY, Ability.CONSTITUTION),
            featId = "$PREFIX:feat:origin:aggressore-selvaggio",
            skillProficiencies = setOf(Skill.ATLETICA, Skill.INTIMIDIRE),
            toolChoice = ChoiceDefinition(
                id = "$PREFIX:choice:background:soldato:tool",
                title = "Soldato: scegli un tipo di gioco",
                kind = ChoiceKind.TOOL_PROFICIENCY,
                count = 1,
                poolId = "$PREFIX:pool:tools:gaming-set",
            ),
            equipmentChoice = equipmentChoice(
                "background:soldato",
                "Soldato: scegli la dotazione o 50 mo",
                equipment("background:soldato", "a"),
                equipment("background:soldato", "b"),
            ),
            description = "Forza, Destrezza, Costituzione · Aggressore selvaggio · " +
                "Atletica, Intimidire · un tipo di gioco.",
            sourcePage = 93,
        ),
    )
}

/** Dotazioni A/B/C di background e classi, conservate come dati applicabili. */
object SrdStartingEquipment {
    private fun pack(
        owner: String,
        option: String,
        name: String,
        description: String,
        weapons: List<String> = emptyList(),
        items: List<String> = emptyList(),
        armor: StartingArmor? = null,
        shield: Boolean = false,
        gold: Int = 0,
    ) = EquipmentPackageDefinition(
        id = equipment(owner, option),
        name = name,
        description = description,
        weaponIds = weapons.map(::weapon),
        itemNames = items,
        armor = armor,
        shield = shield,
        goldPieces = gold,
    )

    fun all(language: AppLanguage): List<EquipmentPackageDefinition> = when (language) {
        AppLanguage.ITALIAN -> italian
        AppLanguage.ENGLISH -> english
    }

    private val english: List<EquipmentPackageDefinition> by lazy {
        italian.map { pack ->
            val text = ENGLISH_PACKS.getValue(pack.id.removePrefix("$PREFIX:equipment:"))
            pack.copy(
                name = text.name,
                description = text.description,
                itemNames = pack.itemNames.map { ENGLISH_ITEMS.getValue(it) },
            )
        }
    }

    private val italian: List<EquipmentPackageDefinition> = listOf(
        pack("background:accolito", "a", "Dotazione A", "Scorte da calligrafo, libro (preghiere), simbolo sacro, pergamena (10 fogli), tunica e 8 mo.", items = listOf("Scorte da calligrafo", "Libro (preghiere)", "Simbolo sacro", "Pergamena (10 fogli)", "Tunica"), gold = 8),
        pack("background:accolito", "b", "50 mo", "50 mo.", gold = 50),
        pack("background:criminale", "a", "Dotazione A", "2 pugnali, arnesi da scasso, piede di porco, 2 borse, abiti da viaggiatore e 16 mo.", weapons = listOf("pugnale"), items = listOf("Arnesi da scasso", "Piede di porco", "2 borse", "Abiti da viaggiatore"), gold = 16),
        pack("background:criminale", "b", "50 mo", "50 mo.", gold = 50),
        pack("background:sapiente", "a", "Dotazione A", "Bastone ferrato, scorte da calligrafo, libro (storia), pergamena (8 fogli), tunica e 8 mo.", weapons = listOf("bastone-ferrato"), items = listOf("Scorte da calligrafo", "Libro (storia)", "Pergamena (8 fogli)", "Tunica"), gold = 8),
        pack("background:sapiente", "b", "50 mo", "50 mo.", gold = 50),
        pack("background:soldato", "a", "Dotazione A", "Lancia, arco corto, 20 frecce, gioco scelto, borsa del guaritore, faretra, abiti da viaggiatore e 14 mo.", weapons = listOf("lancia", "arco-corto"), items = listOf("20 frecce", "Gioco scelto", "Borsa del guaritore", "Faretra", "Abiti da viaggiatore"), gold = 14),
        pack("background:soldato", "b", "50 mo", "50 mo.", gold = 50),

        pack("class:barbaro", "a", "Dotazione A", "Ascia bipenne, 4 asce, dotazione da esploratore e 15 mo.", weapons = listOf("ascia-bipenne", "ascia"), items = listOf("Dotazione da esploratore"), gold = 15),
        pack("class:barbaro", "b", "75 mo", "75 mo.", gold = 75),
        pack("class:bardo", "a", "Dotazione A", "Armatura di cuoio, 2 pugnali, uno strumento musicale a scelta, dotazione da intrattenitore e 19 mo.", weapons = listOf("pugnale"), items = listOf("Strumento musicale a scelta", "Dotazione da intrattenitore"), armor = StartingArmor.LEATHER, gold = 19),
        pack("class:bardo", "b", "90 mo", "90 mo.", gold = 90),
        pack("class:chierico", "a", "Dotazione A", "Giaco di maglia, scudo, mazza, simbolo sacro, dotazione da sacerdote e 7 mo.", weapons = listOf("mazza"), items = listOf("Simbolo sacro", "Dotazione da sacerdote"), armor = StartingArmor.CHAIN_SHIRT, shield = true, gold = 7),
        pack("class:chierico", "b", "110 mo", "110 mo.", gold = 110),
        pack("class:druido", "a", "Dotazione A", "Armatura di cuoio, scudo, falcetto, focus druidico (bastone ferrato), dotazione da esploratore, borsa da erborista e 9 mo.", weapons = listOf("falcetto", "bastone-ferrato"), items = listOf("Focus druidico", "Dotazione da esploratore", "Borsa da erborista"), armor = StartingArmor.LEATHER, shield = true, gold = 9),
        pack("class:druido", "b", "50 mo", "50 mo.", gold = 50),
        pack("class:guerriero", "a", "Dotazione A", "Cotta di maglia, spadone, mazzafrusto, 8 giavellotti, dotazione da avventuriero e 4 mo.", weapons = listOf("spadone", "mazzafrusto", "giavellotto"), items = listOf("Dotazione da avventuriero"), armor = StartingArmor.CHAIN_MAIL, gold = 4),
        pack("class:guerriero", "b", "Dotazione B", "Cuoio borchiato, scimitarra, spada corta, arco lungo, 20 frecce, faretra, dotazione da avventuriero e 11 mo.", weapons = listOf("scimitarra", "spada-corta", "arco-lungo"), items = listOf("20 frecce", "Faretra", "Dotazione da avventuriero"), armor = StartingArmor.STUDDED_LEATHER, gold = 11),
        pack("class:guerriero", "c", "155 mo", "155 mo.", gold = 155),
        pack("class:ladro", "a", "Dotazione A", "Armatura di cuoio, 2 pugnali, spada corta, arco corto, 20 frecce, faretra, arnesi da scasso, dotazione da scassinatore e 8 mo.", weapons = listOf("pugnale", "spada-corta", "arco-corto"), items = listOf("20 frecce", "Faretra", "Arnesi da scasso", "Dotazione da scassinatore"), armor = StartingArmor.LEATHER, gold = 8),
        pack("class:ladro", "b", "100 mo", "100 mo.", gold = 100),
        pack("class:mago", "a", "Dotazione A", "2 pugnali, focus arcano (bastone ferrato), veste, libro degli incantesimi, dotazione da studioso e 5 mo.", weapons = listOf("pugnale", "bastone-ferrato"), items = listOf("Veste", "Libro degli incantesimi", "Dotazione da studioso"), gold = 5),
        pack("class:mago", "b", "55 mo", "55 mo.", gold = 55),
        pack("class:monaco", "a", "Dotazione A", "Lancia, 5 pugnali, strumento scelto, dotazione da esploratore e 11 mo.", weapons = listOf("lancia", "pugnale"), items = listOf("Strumento scelto", "Dotazione da esploratore"), gold = 11),
        pack("class:monaco", "b", "50 mo", "50 mo.", gold = 50),
        pack("class:paladino", "a", "Dotazione A", "Cotta di maglia, scudo, spada lunga, 6 giavellotti, simbolo sacro, dotazione da sacerdote e 9 mo.", weapons = listOf("spada-lunga", "giavellotto"), items = listOf("Simbolo sacro", "Dotazione da sacerdote"), armor = StartingArmor.CHAIN_MAIL, shield = true, gold = 9),
        pack("class:paladino", "b", "150 mo", "150 mo.", gold = 150),
        pack("class:ranger", "a", "Dotazione A", "Cuoio borchiato, scimitarra, spada corta, arco lungo, 20 frecce, faretra, focus druidico (rametto di vischio), dotazione da esploratore e 7 mo.", weapons = listOf("scimitarra", "spada-corta", "arco-lungo"), items = listOf("20 frecce", "Faretra", "Focus druidico", "Dotazione da esploratore"), armor = StartingArmor.STUDDED_LEATHER, gold = 7),
        pack("class:ranger", "b", "150 mo", "150 mo.", gold = 150),
        pack("class:stregone", "a", "Dotazione A", "Lancia, 2 pugnali, focus arcano (cristallo), dotazione da avventuriero e 28 mo.", weapons = listOf("lancia", "pugnale"), items = listOf("Focus arcano (cristallo)", "Dotazione da avventuriero"), gold = 28),
        pack("class:stregone", "b", "50 mo", "50 mo.", gold = 50),
        pack("class:warlock", "a", "Dotazione A", "Armatura di cuoio, falcetto, 2 pugnali, focus arcano (globo), libro (scienze occulte), dotazione da studioso e 15 mo.", weapons = listOf("falcetto", "pugnale"), items = listOf("Focus arcano (globo)", "Libro (scienze occulte)", "Dotazione da studioso"), armor = StartingArmor.LEATHER, gold = 15),
        pack("class:warlock", "b", "100 mo", "100 mo.", gold = 100),
    )

    fun choiceFor(classId: CharacterClassId, language: AppLanguage): ChoiceDefinition {
        val slug = classId.contentId
        val optionIds = all(language)
            .filter { it.id.startsWith("$PREFIX:equipment:class:$slug:") }
            .map { it.id }
        return equipmentChoice(
            owner = "class:$slug",
            title = SrdWords.of(language).startingEquipmentFor(classId.label(language)),
            options = optionIds.toTypedArray(),
        )
    }
}

private class BackgroundText(
    val name: String,
    val description: String,
    val toolTitle: String,
    val equipmentTitle: String,
)

// Chiave: lo slug canonico, che resta italiano.
private val ENGLISH_BACKGROUNDS = mapOf(
    "accolito" to BackgroundText(
        name = "Acolyte",
        description = "Intelligence, Wisdom, Charisma · Magic Initiate (Cleric) · " +
            "Insight, Religion · Calligrapher's Supplies.",
        toolTitle = "Acolyte: tool proficiency",
        equipmentTitle = "Acolyte: choose the equipment or 50 GP",
    ),
    "criminale" to BackgroundText(
        name = "Criminal",
        description = "Dexterity, Constitution, Intelligence · Alert · " +
            "Sleight of Hand, Stealth · Thieves' Tools.",
        toolTitle = "Criminal: tool proficiency",
        equipmentTitle = "Criminal: choose the equipment or 50 GP",
    ),
    "sapiente" to BackgroundText(
        name = "Sage",
        description = "Constitution, Intelligence, Wisdom · Magic Initiate (Wizard) · " +
            "Arcana, History · Calligrapher's Supplies.",
        toolTitle = "Sage: tool proficiency",
        equipmentTitle = "Sage: choose the equipment or 50 GP",
    ),
    "soldato" to BackgroundText(
        name = "Soldier",
        description = "Strength, Dexterity, Constitution · Savage Attacker · " +
            "Athletics, Intimidation · a Gaming Set.",
        toolTitle = "Soldier: choose a Gaming Set",
        equipmentTitle = "Soldier: choose the equipment or 50 GP",
    ),
)

private class PackText(val name: String, val description: String)

private fun gold(amount: Int) = PackText("$amount GP", "$amount GP.")

private val ENGLISH_PACKS = mapOf(
    "background:accolito:a" to PackText(
        "Equipment A",
        "Calligrapher's Supplies, Book (prayers), Holy Symbol, Parchment (10 sheets), Robe, and 8 GP.",
    ),
    "background:accolito:b" to gold(50),
    "background:criminale:a" to PackText(
        "Equipment A",
        "2 Daggers, Thieves' Tools, Crowbar, 2 Pouches, Traveler's Clothes, and 16 GP.",
    ),
    "background:criminale:b" to gold(50),
    "background:sapiente:a" to PackText(
        "Equipment A",
        "Quarterstaff, Calligrapher's Supplies, Book (history), Parchment (8 sheets), Robe, and 8 GP.",
    ),
    "background:sapiente:b" to gold(50),
    "background:soldato:a" to PackText(
        "Equipment A",
        "Spear, Shortbow, 20 Arrows, Gaming Set (chosen), Healer's Kit, Quiver, " +
            "Traveler's Clothes, and 14 GP.",
    ),
    "background:soldato:b" to gold(50),
    "class:barbaro:a" to PackText(
        "Equipment A",
        "Greataxe, 4 Handaxes, Explorer's Pack, and 15 GP.",
    ),
    "class:barbaro:b" to gold(75),
    "class:bardo:a" to PackText(
        "Equipment A",
        "Leather Armor, 2 Daggers, a Musical Instrument of your choice, Entertainer's Pack, and 19 GP.",
    ),
    "class:bardo:b" to gold(90),
    "class:chierico:a" to PackText(
        "Equipment A",
        "Chain Shirt, Shield, Mace, Holy Symbol, Priest's Pack, and 7 GP.",
    ),
    "class:chierico:b" to gold(110),
    "class:druido:a" to PackText(
        "Equipment A",
        "Leather Armor, Shield, Sickle, Druidic Focus (Quarterstaff), Explorer's Pack, " +
            "Herbalism Kit, and 9 GP.",
    ),
    "class:druido:b" to gold(50),
    "class:guerriero:a" to PackText(
        "Equipment A",
        "Chain Mail, Greatsword, Flail, 8 Javelins, Dungeoneer's Pack, and 4 GP.",
    ),
    "class:guerriero:b" to PackText(
        "Equipment B",
        "Studded Leather Armor, Scimitar, Shortsword, Longbow, 20 Arrows, Quiver, " +
            "Dungeoneer's Pack, and 11 GP.",
    ),
    "class:guerriero:c" to gold(155),
    "class:ladro:a" to PackText(
        "Equipment A",
        "Leather Armor, 2 Daggers, Shortsword, Shortbow, 20 Arrows, Quiver, Thieves' Tools, " +
            "Burglar's Pack, and 8 GP.",
    ),
    "class:ladro:b" to gold(100),
    "class:mago:a" to PackText(
        "Equipment A",
        "2 Daggers, Arcane Focus (Quarterstaff), Robe, Spellbook, Scholar's Pack, and 5 GP.",
    ),
    "class:mago:b" to gold(55),
    "class:monaco:a" to PackText(
        "Equipment A",
        "Spear, 5 Daggers, Artisan's Tools or Musical Instrument (chosen), Explorer's Pack, and 11 GP.",
    ),
    "class:monaco:b" to gold(50),
    "class:paladino:a" to PackText(
        "Equipment A",
        "Chain Mail, Shield, Longsword, 6 Javelins, Holy Symbol, Priest's Pack, and 9 GP.",
    ),
    "class:paladino:b" to gold(150),
    "class:ranger:a" to PackText(
        "Equipment A",
        "Studded Leather Armor, Scimitar, Shortsword, Longbow, 20 Arrows, Quiver, " +
            "Druidic Focus (sprig of mistletoe), Explorer's Pack, and 7 GP.",
    ),
    "class:ranger:b" to gold(150),
    "class:stregone:a" to PackText(
        "Equipment A",
        "Spear, 2 Daggers, Arcane Focus (crystal), Dungeoneer's Pack, and 28 GP.",
    ),
    "class:stregone:b" to gold(50),
    "class:warlock:a" to PackText(
        "Equipment A",
        "Leather Armor, Sickle, 2 Daggers, Arcane Focus (orb), Book (occult lore), " +
            "Scholar's Pack, and 15 GP.",
    ),
    "class:warlock:b" to gold(100),
)

// Le voci non-arma delle dotazioni. Le armi hanno gia' la loro tavola in
// [SrdWeapons]; qui sta solo cio' che arma non e'.
internal val ENGLISH_ITEMS = mapOf(
    "Scorte da calligrafo" to "Calligrapher's Supplies",
    "Libro (preghiere)" to "Book (prayers)",
    "Libro (storia)" to "Book (history)",
    "Libro (scienze occulte)" to "Book (occult lore)",
    "Simbolo sacro" to "Holy Symbol",
    "Pergamena (10 fogli)" to "Parchment (10 sheets)",
    "Pergamena (8 fogli)" to "Parchment (8 sheets)",
    "Tunica" to "Robe",
    "Veste" to "Robe",
    "Arnesi da scasso" to "Thieves' Tools",
    "Piede di porco" to "Crowbar",
    "2 borse" to "2 Pouches",
    "Abiti da viaggiatore" to "Traveler's Clothes",
    "20 frecce" to "20 Arrows",
    "Gioco scelto" to "Gaming Set (chosen)",
    "Borsa del guaritore" to "Healer's Kit",
    "Faretra" to "Quiver",
    "Dotazione da esploratore" to "Explorer's Pack",
    "Dotazione da intrattenitore" to "Entertainer's Pack",
    "Dotazione da sacerdote" to "Priest's Pack",
    "Dotazione da avventuriero" to "Dungeoneer's Pack",
    "Dotazione da scassinatore" to "Burglar's Pack",
    "Dotazione da studioso" to "Scholar's Pack",
    "Strumento musicale a scelta" to "Musical Instrument (chosen)",
    "Strumento scelto" to "Tool (chosen)",
    "Focus druidico" to "Druidic Focus",
    "Focus arcano (cristallo)" to "Arcane Focus (crystal)",
    "Focus arcano (globo)" to "Arcane Focus (orb)",
    "Borsa da erborista" to "Herbalism Kit",
    "Libro degli incantesimi" to "Spellbook",
)
