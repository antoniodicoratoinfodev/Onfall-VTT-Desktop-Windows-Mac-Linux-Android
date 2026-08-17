package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.domain.combat.ActivationCost
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.CharacterProgressionEngine
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Il pacchetto inglese e quello italiano devono essere lo stesso contenuto.
 *
 * La prova che conta e' la prima: **gli identificativi coincidono, uno per uno**.
 * E' cio' che permette a un personaggio creato in italiano di conservare classe,
 * privilegi e incantesimi quando si passa all'inglese; se un solo identificativo
 * divergesse, quella scheda perderebbe in silenzio una scelta. Le altre prove
 * verificano che il testo sia davvero cambiato, perche' un pacchetto con gli
 * identificativi giusti e i nomi italiani passerebbe la prima e sarebbe inutile.
 */
class SrdEnglishPackTest {
    private val italian = Srd521ItContent.packFor(AppLanguage.ITALIAN)
    private val english = Srd521ItContent.packFor(AppLanguage.ENGLISH)
    private val italianService = GuidedCharacterService(italian) { id ->
        SrdChoiceResolver.labelsForId(id, AppLanguage.ITALIAN)
    }
    private val englishService = GuidedCharacterService(english) { id ->
        SrdChoiceResolver.labelsForId(id, AppLanguage.ENGLISH)
    }

    @Test
    fun `le due edizioni coniano gli stessi identificativi`() {
        assertEquals(
            italian.elements.map { it.id }.sorted(),
            english.elements.map { it.id }.sorted(),
            "un personaggio non sopravviverebbe al cambio di lingua",
        )
    }

    @Test
    fun `classi, armi, background e dotazioni conservano gli identificativi`() {
        assertEquals(italian.classes.map { it.id }, english.classes.map { it.id })
        assertEquals(italian.weapons.map { it.id }, english.weapons.map { it.id })
        assertEquals(italian.backgrounds.map { it.id }, english.backgrounds.map { it.id })
        assertEquals(
            italian.equipmentPackages.map { it.id },
            english.equipmentPackages.map { it.id },
        )
    }

    @Test
    fun `le scelte guidate conservano identificativi e opzioni`() {
        // Gli optionIds sono cio' che una scheda salva quando l'utente sceglie:
        // il titolo puo' cambiare lingua, l'elenco delle opzioni no.
        val italianChoices = italian.classes.flatMap { it.levels }.flatMap { it.choices }
        val englishChoices = english.classes.flatMap { it.levels }.flatMap { it.choices }
        assertEquals(italianChoices.map { it.id }, englishChoices.map { it.id })
        assertEquals(italianChoices.map { it.optionIds }, englishChoices.map { it.optionIds })
    }

    @Test
    fun `anche le scelte figlie dinamiche conservano gli identificativi`() {
        val cases = listOf(
            Triple(app.d6d.rules.character.CharacterClassId.CLERIC, ":ordine-taumaturgo", "Thaumaturge"),
            Triple(app.d6d.rules.character.CharacterClassId.DRUID, ":ordine-mago", "Primal Order: Magician"),
            Triple(app.d6d.rules.character.CharacterClassId.PALADIN, ":guerriero-benedetto", "Blessed Warrior"),
            Triple(app.d6d.rules.character.CharacterClassId.RANGER, ":guerriero-druidico", "Druidic Warrior"),
        )
        val italianEngine = CharacterProgressionEngine(italian)
        val englishEngine = CharacterProgressionEngine(english)

        cases.forEach { (classId, optionSuffix, englishTitle) ->
            val parent = italian.classes
                .first { it.id == classId }
                .levels
                .flatMap { it.choices }
                .first { choice -> choice.optionIds.any { it.endsWith(optionSuffix) } }
            val optionId = parent.optionIds.first { it.endsWith(optionSuffix) }
            val provisional = listOf(ChoiceSelection(parent.id, listOf(optionId)))

            val italianRequirements = italianEngine.requirementsFor(
                CharacterProgression(),
                classId,
                provisional,
            )
            val englishRequirements = englishEngine.requirementsFor(
                CharacterProgression(),
                classId,
                provisional,
            )

            assertEquals(
                italianRequirements.map { it.id }.sorted(),
                englishRequirements.map { it.id }.sorted(),
                classId.name,
            )
            assertTrue(englishRequirements.any { it.title.startsWith("$englishTitle: choose") })
        }
    }

    @Test
    fun `il pacchetto e' lo stesso, quindi le schede restano sue`() {
        assertEquals(italian.manifest.id, english.manifest.id)
        assertEquals("it-IT", italian.manifest.locale)
        assertEquals("en-US", english.manifest.locale)
    }

    @Test
    fun `il testo e' davvero inglese`() {
        val byId = english.elements.associateBy { it.id }
        assertEquals("Fireball", byId.getValue("srd521-it:spell:palla-di-fuoco").name)
        assertEquals("Wolf", byId.getValue("srd521-it:beast:lupo").name)
        assertEquals("Alert", byId.getValue("srd521-it:feat:origin:allerta").name)
        assertEquals("Rage", byId.getValue("srd521-it:feature:barbaro:ira").name)
        assertEquals("Subtle Spell", byId.getValue("srd521-it:metamagic:incantesimo-celato").name)
        assertEquals(
            "Agonizing Blast",
            byId.getValue("srd521-it:feature:warlock:deflagrazione-agonizzante").name,
        )
        assertEquals(
            "Passive; once per turn for Punch and Grab",
            byId.getValue("srd521-it:feat:general:lottatore").activation,
        )
    }

    @Test
    fun `nessun nome resta in italiano`() {
        // Rete larga ma efficace: queste sequenze non compaiono in inglese, e
        // pescano un pezzo dimenticato meglio di un elenco di casi noti.
        val italianisms = listOf("zione di ", "à", "è", "ù", " degli ", " della ", " dei ")
        val leftovers = english.elements
            .map { it.name }
            .filter { name -> italianisms.any { it in name } }
        assertTrue(leftovers.isEmpty(), "nomi rimasti in italiano: $leftovers")
    }

    @Test
    fun `classi, armi e risorse parlano inglese`() {
        val barbarian = english.classes.first { it.id.name == "BARBARIAN" }
        assertEquals("Barbarian", barbarian.name)
        assertTrue(barbarian.resources.any { it.name == "Rage" })
        assertFalse(barbarian.levels.flatMap { it.choices }.any { "Scegli" in it.title })
        assertEquals("Longsword", english.weapons.first { it.id.endsWith(":spada-lunga") }.name)
        assertEquals("Acolyte", english.backgrounds.first { it.id.endsWith(":accolito") }.name)
    }

    @Test
    fun `anche le descrizioni delle scelte di classe parlano inglese`() {
        val choices = english.classes.flatMap { it.levels }.flatMap { it.choices }
        val fiendResilience = choices.single { it.id.endsWith(":resilienza-immonda") }

        assertEquals(
            "You can change the type at the end of a Short or Long Rest.",
            fiendResilience.description,
        )
        assertFalse(
            choices.any { choice ->
                choice.description.contains("riposo", ignoreCase = true) ||
                    choice.description.contains("Puoi ", ignoreCase = true)
            },
        )
    }

    @Test
    fun `anche i campi che oggi nessuno mostra parlano inglese`() {
        // Sono campi pubblici del modello, sostituiti dagli equivalenti
        // strutturati ma ancora leggibili: il giorno che una schermata li pesca,
        // devono gia' essere nella lingua giusta. Questa prova e' il motivo per
        // cui non basta tradurre «cio' che si vede».
        english.classes.forEach { definition ->
            assertFalse(
                definition.weaponTraining.startsWith("Armi"),
                "addestramento nelle armi in italiano: ${definition.id}",
            )
            assertFalse(
                definition.startingEquipment.startsWith("A scelta"),
                "dotazione iniziale in italiano: ${definition.id}",
            )
            definition.resources.forEach { resource ->
                assertFalse(
                    resource.description.contains("riposo"),
                    "nota della risorsa in italiano: ${resource.id}",
                )
            }
        }
        val fighter = english.classes.first { it.id.name == "FIGHTER" }
        assertTrue(
            fighter.startingEquipment.startsWith("Choose one of A or B or C: "),
            fighter.startingEquipment,
        )
        val rangerTitles = english.classes
            .first { it.id.name == "RANGER" }
            .levels
            .flatMap { it.choices }
            .map { it.title }
        assertTrue("Deft Explorer: choose two standard languages" in rangerTitles)

        assertEquals(
            setOf("Fast Movement", "Unarmored Movement"),
            english.classes
                .flatMap { it.levels }
                .flatMap { it.effects }
                .mapTo(mutableSetOf()) { it.source },
        )
        assertEquals(
            setOf("Defense", "Archery"),
            english.elements
                .flatMap { it.effects }
                .mapTo(mutableSetOf()) { it.source },
        )
        val englishCatalog = Srd521ItContent.catalogFor(AppLanguage.ENGLISH).associateBy { it.id }
        assertTrue(
            englishCatalog.getValue("srd521-it:feature:barbaro:movimento-veloce")
                .effects.any { it.source == "Fast Movement" },
        )
        assertTrue(
            englishCatalog.getValue("srd521-it:feature:monaco:movimento-senza-armatura")
                .effects.any { it.source == "Unarmored Movement" },
        )
    }

    @Test
    fun `il costo dichiarato si legge in entrambe le edizioni`() {
        // «Costo: 2» contro «Cost: 2»: col solo modello italiano ogni privilegio
        // inglese risulterebbe gratuito, e il difetto sarebbe di regole, non di testo.
        val costly = italian.elements.filter { it.resourceCost > 0 }.associate { it.id to it.resourceCost }
        val englishCosts = english.elements.filter { it.resourceCost > 0 }.associate { it.id to it.resourceCost }
        assertEquals(costly, englishCosts, "i costi delle risorse divergono fra le edizioni")
        assertTrue(costly.isNotEmpty(), "la prova sarebbe vuota senza privilegi a costo")
    }

    @Test
    fun `azioni bonus e reazioni inglesi conservano il costo di turno`() {
        val catalog = Srd521ItContent.catalogFor(AppLanguage.ENGLISH).associateBy { it.id }

        assertEquals(
            ActivationCost.BONUS_ACTION,
            catalog.getValue("srd521-it:spell:parola-guaritrice").activationCost,
        )
        assertEquals(
            ActivationCost.REACTION,
            catalog.getValue("srd521-it:spell:scudo").activationCost,
        )
        assertEquals(
            ActivationCost.BONUS_ACTION,
            catalog.getValue("srd521-it:feature:barbaro:ira").activationCost,
        )
    }

    @Test
    fun `la creazione guidata inglese genera titoli e opzioni inglesi`() {
        val requirements = englishService.requirements(CharacterSheet(), CharacterClassId.FIGHTER)

        assertTrue(requirements.any { it.title.startsWith("Choose") })
        assertFalse(requirements.any { "Scegli" in it.title })

        val skillChoice = ChoiceDefinition(
            id = "test:skills",
            title = "Choose a skill",
            kind = ChoiceKind.SKILL_PROFICIENCY,
            count = 1,
            poolId = "${english.manifest.id}:pool:skills:guerriero:any",
        )
        val skillOptions = SrdChoiceResolver.options(
            choice = skillChoice,
            classId = app.d6d.rules.character.CharacterClassId.FIGHTER,
            classLevel = 1,
            sheet = CharacterSheet(),
            language = AppLanguage.ENGLISH,
        )
        assertTrue(skillOptions.any { it.label == "Athletics" && it.secondaryLabel == "Strength" })

        val toolChoice = skillChoice.copy(
            id = "test:tools",
            title = "Choose a tool",
            kind = ChoiceKind.TOOL_PROFICIENCY,
            poolId = "${english.manifest.id}:pool:tools:any",
        )
        val toolOptions = SrdChoiceResolver.options(
            choice = toolChoice,
            classId = app.d6d.rules.character.CharacterClassId.FIGHTER,
            classLevel = 1,
            sheet = CharacterSheet(),
            language = AppLanguage.ENGLISH,
        )
        assertTrue(toolOptions.any { it.label == "Thieves' Tools" })

        val italianOwnedTool = SrdChoiceResolver.options(
            choice = toolChoice,
            classId = app.d6d.rules.character.CharacterClassId.FIGHTER,
            classLevel = 1,
            sheet = CharacterSheet(toolProficiencies = "Arnesi da scasso"),
            language = AppLanguage.ENGLISH,
        )
        assertFalse(italianOwnedTool.any { it.label == "Thieves' Tools" })
    }

    @Test
    fun `gli id fissi espongono prima il nome inglese e poi quello italiano`() {
        assertEquals(
            listOf("Thieves' Tools", "Arnesi da scasso"),
            SrdChoiceResolver.labelsForId(
                "srd521-it:tool:arnesi-da-scasso",
                AppLanguage.ENGLISH,
            ),
        )
        assertEquals(
            listOf("Dwarvish", "Nanico"),
            SrdChoiceResolver.labelsForId("srd521-it:language:nanico", AppLanguage.ENGLISH),
        )
        assertEquals(
            listOf("Bludgeoning", "Contundente"),
            SrdChoiceResolver.labelsForId("srd521-it:damage:contundente", AppLanguage.ENGLISH),
        )
        assertEquals(
            listOf("Radioso", "Radiant"),
            SrdChoiceResolver.labelsForId("srd521-it:damage:radioso", AppLanguage.ITALIAN),
        )
    }

    @Test
    fun `la progressione inglese salva strumenti e lingue in inglese`() {
        val draft = CharacterSheet()
        val firstLevelSelections = englishSelectionsFor(
            sheet = draft,
            classId = CharacterClassId.RANGER,
            overrides = mapOf(
                "srd521-it:choice:origin:background" to
                    listOf("srd521-it:background:criminale"),
                "srd521-it:choice:background:criminale:tool" to
                    listOf("srd521-it:tool:arnesi-da-scasso"),
            ),
        )
        val levelOne = englishService.advance(
            draft,
            LevelUpRequest(
                classId = CharacterClassId.RANGER,
                hitPointIncrease = englishService.fixedHitPointIncrease(draft, CharacterClassId.RANGER),
                usedFixedHitPoints = true,
                selections = firstLevelSelections,
            ),
        )

        assertTrue("Thieves' Tools" in levelOne.toolProficiencies)
        assertFalse("Arnesi da scasso" in levelOne.toolProficiencies)
        assertTrue(levelOne.weapons.all { it.note.startsWith("Mastery: ") })
        assertTrue(levelOne.weapons.any { " · range " in it.note && it.note.endsWith(" ft") })
        assertFalse(levelOne.weapons.any { "Padronanza" in it.note || "gittata" in it.note })

        val readyForLevelTwo = levelOne.copy(
            experiencePoints = ExperienceProgression.thresholdForLevel(2),
        )
        val levelTwoSelections = englishSelectionsFor(readyForLevelTwo, CharacterClassId.RANGER)
        val levelTwo = englishService.advance(
            readyForLevelTwo,
            LevelUpRequest(
                classId = CharacterClassId.RANGER,
                hitPointIncrease = englishService.fixedHitPointIncrease(
                    readyForLevelTwo,
                    CharacterClassId.RANGER,
                ),
                usedFixedHitPoints = true,
                selections = levelTwoSelections,
            ),
        )

        assertEquals("Common, Common Sign Language", levelTwo.languages)
        assertFalse("Comune" in levelTwo.languages)
    }

    @Test
    fun `cambiare lingua tra due livelli non duplica le lingue di classe`() {
        val draft = CharacterSheet()
        val italianLevelOne = italianService.advance(
            draft,
            LevelUpRequest(
                classId = CharacterClassId.DRUID,
                hitPointIncrease = italianService.fixedHitPointIncrease(draft, CharacterClassId.DRUID),
                usedFixedHitPoints = true,
                selections = selectionsFor(
                    sheet = draft,
                    classId = CharacterClassId.DRUID,
                    service = italianService,
                    language = AppLanguage.ITALIAN,
                ),
            ),
        )
        assertEquals("Druidico", italianLevelOne.languages)

        val readyForLevelTwo = italianLevelOne.copy(
            experiencePoints = ExperienceProgression.thresholdForLevel(2),
        )
        val switched = englishService.advance(
            readyForLevelTwo,
            LevelUpRequest(
                classId = CharacterClassId.DRUID,
                hitPointIncrease = englishService.fixedHitPointIncrease(
                    readyForLevelTwo,
                    CharacterClassId.DRUID,
                ),
                usedFixedHitPoints = true,
                selections = englishSelectionsFor(readyForLevelTwo, CharacterClassId.DRUID),
            ),
        )

        // Il testo libero gia' salvato non viene riscritto, ma neppure affiancato
        // dalla traduzione della stessa concessione al livello successivo.
        assertEquals("Druidico", switched.languages)
    }

    @Test
    fun `le risorse derivate dalla progressione usano i nomi inglesi`() {
        val draft = CharacterSheet()
        val created = englishService.advance(
            draft,
            LevelUpRequest(
                classId = CharacterClassId.FIGHTER,
                hitPointIncrease = englishService.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER),
                usedFixedHitPoints = true,
                // La prima opzione di background e' Acolyte, che concede Magic Initiate (Cleric).
                selections = englishSelectionsFor(draft, CharacterClassId.FIGHTER),
            ),
        )

        assertTrue(
            created.progression.resourcePools.any {
                it.name == "Magic Initiate (Cleric): free casting"
            },
            created.progression.resourcePools.joinToString { it.name },
        )
    }

    @Test
    fun `maestria negli incantesimi riconosce il tempo di lancio inglese`() {
        val magicMissileId = "srd521-it:spell:dardo-incantato"
        val sheet = CharacterSheet(
            progression = CharacterProgression(
                classLevels = listOf(
                    ClassLevelState(app.d6d.rules.character.CharacterClassId.WIZARD, 17),
                ),
                spellbookSpellIds = listOf(magicMissileId),
            ),
        )
        val choice = ChoiceDefinition(
            id = "mago:18:maestria-incantesimo-1",
            title = "Spell Mastery: choose a level 1 spell",
            kind = ChoiceKind.ALWAYS_PREPARED_SPELL,
            count = 1,
            poolId = "${english.manifest.id}:pool:spells:mago:1",
        )

        val options = SrdChoiceResolver.options(
            choice = choice,
            classId = app.d6d.rules.character.CharacterClassId.WIZARD,
            classLevel = 18,
            sheet = sheet,
            language = AppLanguage.ENGLISH,
        )

        assertTrue(options.any { it.id == magicMissileId && it.label == "Magic Missile" })
    }

    @Test
    fun `tutte le forme bestiali hanno gli stessi attacchi nelle due edizioni`() {
        // Il difetto che questo controllo sorveglia e' doppio, e nessuno dei due
        // si vedeva contando le bestie: la scheda del Ragno aveva inghiottito sei
        // sciami e portava sette attacchi (compreso quello di uno sciame GS 2),
        // mentre Aquila e Ratto gigante non ne avevano nessuno perche' l'SRD li
        // scrive con «5 feet» e il parser accettava solo «ft».
        val italian = SrdBeasts.all(AppLanguage.ITALIAN).associateBy { it.id }
        val english = SrdBeasts.all(AppLanguage.ENGLISH).associateBy { it.id }
        assertEquals(italian.keys, english.keys)

        val divergent = italian.keys.mapNotNull { id ->
            val left = italian.getValue(id).toActorDefinition()
            val right = english.getValue(id).toActorDefinition()
            val mismatch = left.abilities().size != right.abilities().size ||
                left.armorClass() != right.armorClass() ||
                left.speedFeet() != right.speedFeet()
            if (mismatch) {
                "$id: IT ${left.abilities().size} attacchi/CA ${left.armorClass()}/" +
                    "${left.speedFeet()} piedi · EN ${right.abilities().size}/" +
                    "${right.armorClass()}/${right.speedFeet()}"
            } else {
                null
            }
        }
        assertTrue(divergent.isEmpty(), divergent.joinToString("\n"))
    }

    @Test
    fun `le due edizioni concordano su quali forme non attaccano`() {
        // Non «tutte attaccano»: il Cavalluccio marino ha una sola azione, ed e'
        // uno scatto in acqua. Cio' che deve valere e' che le due edizioni siano
        // d'accordo su chi non attacca — un'edizione muta dove l'altra colpisce
        // e' un difetto di lettura, non una differenza del libro.
        fun silent(language: AppLanguage) = SrdBeasts.all(language)
            .filter { it.toActorDefinition().abilities().isEmpty() }
            .map { it.id }
            .toSet()
        assertEquals(silent(AppLanguage.ITALIAN), silent(AppLanguage.ENGLISH))
    }

    @Test
    fun `le distanze passano dai metri ai piedi`() {
        val wolf = SrdBeasts.byId("srd521-it:beast:lupo", AppLanguage.ENGLISH)
        val actor = requireNotNull(wolf).toActorDefinition()
        val italianWolf = requireNotNull(SrdBeasts.byId("srd521-it:beast:lupo"))
        // Stessa bestia, due schede, stessa velocita' una volta riportata a piedi.
        assertEquals(italianWolf.toActorDefinition().speedFeet, actor.speedFeet)
        assertEquals(italianWolf.toActorDefinition().armorClass, actor.armorClass)
    }

    private fun englishSelectionsFor(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        overrides: Map<String, List<String>> = emptyMap(),
    ): List<ChoiceSelection> = selectionsFor(
        sheet = sheet,
        classId = classId,
        service = englishService,
        language = AppLanguage.ENGLISH,
        overrides = overrides,
    )

    private fun selectionsFor(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        service: GuidedCharacterService,
        language: AppLanguage,
        overrides: Map<String, List<String>> = emptyMap(),
    ): List<ChoiceSelection> {
        val classLevel = sheet.progression.levelIn(classId) + 1
        var selected = linkedMapOf<String, List<String>>()
        repeat(8) {
            val provisional = selected.map { ChoiceSelection(it.key, it.value) }
            val requirements = service.requirements(sheet, classId, provisional)
            val currentIds = requirements.mapTo(mutableSetOf()) { it.id }
            selected = selected.filterKeys { it in currentIds }.toMap(LinkedHashMap())
            requirements.forEach { choice ->
                val options = SrdChoiceResolver.options(
                    choice = choice,
                    classId = classId,
                    classLevel = classLevel,
                    sheet = sheet,
                    provisionalSelections = selected.map { ChoiceSelection(it.key, it.value) },
                    language = language,
                )
                selected[choice.id] = overrides[choice.id]
                    ?: options.take(choice.count).map { it.id }
            }
        }
        return service.requirements(
            sheet,
            classId,
            selected.map { ChoiceSelection(it.key, it.value) },
        ).map { ChoiceSelection(it.id, selected[it.id].orEmpty()) }
    }
}
