package app.d6d.ui.sheet

import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.SheetStore
import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.RuleElementKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SheetViewModelTest {

    @TempDir
    lateinit var directory: Path

    private fun model(file: Path = directory.resolve("schede.json")) = SheetViewModel(SheetStore(file))

    @Test
    fun `una modifica rende dirty la bozza e la selezione non la scarta implicitamente`() {
        val model = model()
        val originalId = model.selectedId!!
        val anotherId = model.library.characters.first { it.id != originalId }.id

        assertFalse(model.isDirty)
        model.character = model.character.copy(characterName = "Bozza non salvata")
        assertTrue(model.isDirty)

        assertEquals(SheetNavigationResult.UNSAVED_CHANGES, model.selectCharacter(anotherId))
        assertEquals(originalId, model.selectedId)
        assertEquals("Bozza non salvata", model.character.characterName)

        assertEquals(
            SheetNavigationResult.APPLIED,
            model.selectCharacter(anotherId, discardUnsavedChanges = true),
        )
        assertFalse(model.isDirty)
    }

    @Test
    fun `cambiare tipo non scarta una bozza tramite il setter compatibile`() {
        val model = model()
        model.character = model.character.copy(characterName = "Da conservare")

        model.kind = SheetKind.MOSTRO

        assertEquals(SheetKind.PERSONAGGIO, model.kind)
        assertEquals("Da conservare", model.character.characterName)
        assertTrue(model.isDirty)
    }

    @Test
    fun `un modulo nuovo e ancora intatto non viene trattato come lavoro perso`() {
        val model = model()

        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        assertFalse(model.isDirty)
        assertEquals(ArmorClassMethod.UNARMORED, model.character.armorClassMethod)
        assertEquals(10, model.character.effectiveArmorClass)

        model.character = model.character.copy(characterName = "Nuovo eroe")
        assertTrue(model.isDirty)
    }

    @Test
    fun `un salvataggio fallito non anticipa il commit della libreria in memoria`() {
        val parent = directory.resolve("archivio")
        val file = parent.resolve("schede.json")
        val model = model(file)
        val libraryBefore = model.library
        model.character = model.character.copy(characterName = "Non persistito")

        // Trasforma la cartella in un file: la successiva scrittura deve fallire.
        Files.delete(file)
        Files.delete(parent)
        Files.writeString(parent, "bloccato")

        assertFalse(model.save())
        assertEquals(libraryBefore, model.library)
        assertTrue(model.isDirty)
    }

    @Test
    fun `un upsert silenzioso fallito lascia intatta la copia in memoria`() {
        val parent = directory.resolve("archivio")
        val file = parent.resolve("schede.json")
        val model = model(file)
        val libraryBefore = model.library
        val sheet = libraryBefore.characters.first().copy(characterName = "Correzione")

        Files.delete(file)
        Files.delete(parent)
        Files.writeString(parent, "bloccato")

        assertFalse(model.upsertCharacterSilently(sheet))
        assertEquals(libraryBefore, model.library)
    }

    @Test
    fun `il catalogo distribuito arriva dal content pack e le abilita private si salvano`() {
        val model = model()

        // Le voci SRD non stanno nel file dell'utente: le porta il content pack.
        assertTrue(model.library.abilities.isEmpty())
        assertTrue(model.abilityCatalog.any { it.name == "Palla di fuoco" })

        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))

        val reopened = model()
        assertEquals(ability, reopened.library.abilities.first { it.id == ability.id })
    }

    @Test
    fun `i personaggi inclusi usano le voci del catalogo SRD`() {
        val model = model()
        val sibilla = model.library.characters.first { it.id == "pg-sibilla" }
        val nerea = model.library.characters.first { it.id == "pg-nerea" }

        assertTrue(sibilla.abilityIds.any { it.startsWith("srd521-it:spell:") })
        assertTrue(nerea.abilityIds.any { it.startsWith("srd521-it:spell:") })
        assertTrue(model.abilityCatalog.map { it.id }.containsAll(nerea.abilityIds))
    }

    @Test
    fun `i selettori separano privilegi e talenti dalle altre voci del catalogo`() {
        val model = model()
        val featureKinds = setOf(
            RuleElementKind.CLASS_FEATURE,
            RuleElementKind.SUBCLASS_FEATURE,
            RuleElementKind.METAMAGIC,
            RuleElementKind.ELDRITCH_INVOCATION,
            RuleElementKind.CLASS_OPTION,
        )
        val featKinds = setOf(
            RuleElementKind.ORIGIN_FEAT,
            RuleElementKind.GENERAL_FEAT,
            RuleElementKind.FIGHTING_STYLE_FEAT,
            RuleElementKind.EPIC_BOON_FEAT,
        )

        val features = model.characterTraitCandidates(CharacterTraitSection.FEATURE)
        val feats = model.characterTraitCandidates(CharacterTraitSection.FEAT)

        assertTrue(features.isNotEmpty())
        assertTrue(feats.isNotEmpty())
        assertTrue(features.all { it.category in featureKinds })
        assertTrue(feats.all { it.category in featKinds })
        assertTrue(features.any { it.category == RuleElementKind.CLASS_FEATURE })
        assertTrue(features.any { it.category == RuleElementKind.SUBCLASS_FEATURE })
        assertTrue(feats.any { it.category == RuleElementKind.FIGHTING_STYLE_FEAT })
        assertTrue(features.none { it.category in featKinds })
        assertTrue(feats.none { it.category in featureKinds })
    }

    @Test
    fun `i talenti Generali e i Doni epici rispettano il livello totale`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        val general = model.abilityCatalog.first {
            it.id == "srd521-it:feat:general:aumento-punteggi-caratteristica"
        }
        val epic = model.abilityCatalog.first {
            it.id == "srd521-it:feat:epic-boon:dono-fato"
        }

        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 3)),
            ),
        )
        assertFalse(model.characterTraitIsCompatible(general))
        assertFalse(model.characterTraitIsCompatible(epic))

        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 4)),
            ),
        )
        assertTrue(model.characterTraitIsCompatible(general))
        assertFalse(model.characterTraitIsCompatible(epic))

        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(
                    ClassLevelState(CharacterClassId.FIGHTER, 10),
                    ClassLevelState(CharacterClassId.ROGUE, 9),
                ),
            ),
        )
        assertTrue(model.characterTraitIsCompatible(epic))
    }

    @Test
    fun `gli stili richiedono il privilegio Stile di combattimento attivo`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        val style = model.abilityCatalog.first {
            it.id == "srd521-it:feat:fighting-style:difesa"
        }
        val featureId = "srd521-it:feature:guerriero:stile-di-combattimento"
        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 1)),
            ),
        )

        assertFalse(model.characterTraitIsCompatible(style))

        model.character = model.character.copy(
            progression = model.character.progression.copy(
                selectedFeatureIds = listOf(featureId),
            ),
        )
        assertTrue(model.characterTraitIsCompatible(style))

        model.character = model.character.copy(excludedTraitIds = setOf(featureId))
        assertFalse(model.characterTraitIsCompatible(style))
    }

    @Test
    fun `Lottatore richiede Forza o Destrezza 13 oltre al quarto livello`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        val grappler = model.abilityCatalog.first {
            it.id == "srd521-it:feat:general:lottatore"
        }
        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 4)),
            ),
            abilityScores = Ability.entries.associateWith { 12 },
        )

        assertFalse(model.characterTraitIsCompatible(grappler))

        model.character = model.character.copy(
            abilityScores = model.character.abilityScores + (Ability.DEXTERITY to 13),
        )
        assertTrue(model.characterTraitIsCompatible(grappler))

        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 3)),
            ),
        )
        assertFalse(model.characterTraitIsCompatible(grappler))
    }

    @Test
    fun `il Dono del richiamo degli incantesimi richiede una classe incantatrice`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        val boon = model.abilityCatalog.first {
            it.id == "srd521-it:feat:epic-boon:dono-richiamo-incantesimi"
        }
        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 19)),
            ),
        )

        assertFalse(model.characterTraitIsCompatible(boon))

        model.character = model.character.copy(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.WIZARD, 19)),
            ),
        )
        assertTrue(model.characterTraitIsCompatible(boon))
    }

    @Test
    fun `una scheda manuale aggiunge e rimuove privilegi e talenti dal catalogo`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        model.character = model.character.copy(
            classFeatures = "Nota personale sul privilegio.",
            feats = "Nota personale sul talento.",
        )
        val feature = model.characterTraitCandidates(CharacterTraitSection.FEATURE)
            .first { it.effects.isEmpty() }
        val feat = model.characterTraitCandidates(CharacterTraitSection.FEAT)
            .first { it.effects.isEmpty() }
        val originalFeatureIds = model.character.progression.selectedFeatureIds
        val originalFeatIds = model.character.progression.featIds
        val originalSelections = model.character.progression.selections
        val originalHistory = model.character.progression.advancementHistory

        model.setCharacterTraitSelected(CharacterTraitSection.FEATURE, feature.id, true)
        model.setCharacterTraitSelected(CharacterTraitSection.FEAT, feat.id, true)

        assertTrue(feature.id in model.characterTraitIds(CharacterTraitSection.FEATURE))
        assertTrue(feat.id in model.characterTraitIds(CharacterTraitSection.FEAT))
        assertTrue(feature.id in model.character.abilityIds)
        assertTrue(feat.id in model.character.abilityIds)
        assertFalse(feature.id in model.character.excludedTraitIds)
        assertFalse(feat.id in model.character.excludedTraitIds)
        assertTrue(model.isDirty)
        assertEquals("Nota personale sul privilegio.", model.character.classFeatures)
        assertEquals("Nota personale sul talento.", model.character.feats)
        assertEquals(originalFeatureIds, model.character.progression.selectedFeatureIds)
        assertEquals(originalFeatIds, model.character.progression.featIds)
        assertEquals(originalSelections, model.character.progression.selections)
        assertEquals(originalHistory, model.character.progression.advancementHistory)

        model.setCharacterTraitSelected(CharacterTraitSection.FEATURE, feature.id, false)
        model.setCharacterTraitSelected(CharacterTraitSection.FEAT, feat.id, false)

        assertFalse(feature.id in model.characterTraitIds(CharacterTraitSection.FEATURE))
        assertFalse(feat.id in model.characterTraitIds(CharacterTraitSection.FEAT))
        assertFalse(feature.id in model.character.abilityIds)
        assertFalse(feat.id in model.character.abilityIds)
        assertFalse(feature.id in model.character.excludedTraitIds)
        assertFalse(feat.id in model.character.excludedTraitIds)
        assertEquals("Nota personale sul privilegio.", model.character.classFeatures)
        assertEquals("Nota personale sul talento.", model.character.feats)
        assertEquals(originalFeatureIds, model.character.progression.selectedFeatureIds)
        assertEquals(originalFeatIds, model.character.progression.featIds)
        assertEquals(originalSelections, model.character.progression.selections)
        assertEquals(originalHistory, model.character.progression.advancementHistory)
    }

    @Test
    fun `rimuovere e ripristinare tratti guidati usa un overlay senza riscrivere la progressione`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.selectCharacter("pg-tarvos"))
        val originalFeatureIds = model.character.progression.selectedFeatureIds
        val originalFeatIds = model.character.progression.featIds
        val originalSelections = model.character.progression.selections
        val originalHistory = model.character.progression.advancementHistory
        val guidedFeature = model.characterTraitIds(CharacterTraitSection.FEATURE)
            .first { id ->
                model.characterTraitCandidates(CharacterTraitSection.FEATURE).any { it.id == id }
            }
        val guidedFeat = model.characterTraitIds(CharacterTraitSection.FEAT)
            .first { id ->
                model.characterTraitCandidates(CharacterTraitSection.FEAT).any { it.id == id }
            }

        listOf(
            CharacterTraitSection.FEATURE to guidedFeature,
            CharacterTraitSection.FEAT to guidedFeat,
        ).forEach { (section, id) ->
            assertTrue(id in model.character.abilityIds)

            model.setCharacterTraitSelected(section, id, false)

            assertFalse(id in model.characterTraitIds(section))
            assertFalse(id in model.character.abilityIds)
            assertTrue(id in model.character.excludedTraitIds)
            assertEquals(originalFeatureIds, model.character.progression.selectedFeatureIds)
            assertEquals(originalFeatIds, model.character.progression.featIds)
            assertEquals(originalSelections, model.character.progression.selections)
            assertEquals(originalHistory, model.character.progression.advancementHistory)

            model.setCharacterTraitSelected(section, id, true)

            assertTrue(id in model.characterTraitIds(section))
            assertTrue(id in model.character.abilityIds)
            assertFalse(id in model.character.excludedTraitIds)
            assertEquals(originalFeatureIds, model.character.progression.selectedFeatureIds)
            assertEquals(originalFeatIds, model.character.progression.featIds)
            assertEquals(originalSelections, model.character.progression.selections)
            assertEquals(originalHistory, model.character.progression.advancementHistory)
        }
    }

    @Test
    fun `aggiungere e rimuovere lo stile Difesa aggiorna la classe armatura`() {
        val model = model()
        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        model.character = model.character.copy(armorClassMethod = ArmorClassMethod.CHAIN_MAIL)
        val defenseId = "srd521-it:feat:fighting-style:difesa"

        assertTrue(
            model.characterTraitCandidates(CharacterTraitSection.FEAT).any { it.id == defenseId },
        )
        assertEquals(16, model.character.effectiveArmorClass)
        assertEquals(0, model.character.armorClassEffectBonus)

        model.setCharacterTraitSelected(CharacterTraitSection.FEAT, defenseId, true)

        assertEquals(17, model.character.effectiveArmorClass)
        assertEquals(1, model.character.armorClassEffectBonus)
        assertTrue(defenseId in model.characterTraitIds(CharacterTraitSection.FEAT))
        assertTrue(defenseId in model.character.abilityIds)

        model.setCharacterTraitSelected(CharacterTraitSection.FEAT, defenseId, false)

        assertEquals(16, model.character.effectiveArmorClass)
        assertEquals(0, model.character.armorClassEffectBonus)
        assertFalse(defenseId in model.characterTraitIds(CharacterTraitSection.FEAT))
        assertFalse(defenseId in model.character.abilityIds)
    }

    @Test
    fun `personalizzazioni di privilegi e talenti persistono insieme alle note`() {
        val file = directory.resolve("schede.json")
        val model = model(file)
        assertEquals(SheetNavigationResult.APPLIED, model.selectCharacter("pg-tarvos"))
        val characterId = model.character.id
        val originalFeatureIds = model.character.progression.selectedFeatureIds
        val originalFeatIds = model.character.progression.featIds
        val originalSelections = model.character.progression.selections
        val originalHistory = model.character.progression.advancementHistory
        val removedFeat = model.characterTraitIds(CharacterTraitSection.FEAT).first()
        val addedFeature = model.characterTraitCandidates(CharacterTraitSection.FEATURE)
            .first { it.id !in model.characterTraitIds(CharacterTraitSection.FEATURE) }
        model.character = model.character.copy(
            classFeatures = "Il privilegio segue una regola della casa.",
            feats = "Il talento proviene da una ricompensa narrativa.",
        )

        model.setCharacterTraitSelected(CharacterTraitSection.FEAT, removedFeat, false)
        model.setCharacterTraitSelected(CharacterTraitSection.FEATURE, addedFeature.id, true)
        assertTrue(model.save())

        val reopened = model(file)
        assertEquals(SheetNavigationResult.APPLIED, reopened.selectCharacter(characterId))

        assertFalse(removedFeat in reopened.characterTraitIds(CharacterTraitSection.FEAT))
        assertTrue(removedFeat in reopened.character.excludedTraitIds)
        assertFalse(removedFeat in reopened.character.abilityIds)
        assertTrue(addedFeature.id in reopened.characterTraitIds(CharacterTraitSection.FEATURE))
        assertTrue(addedFeature.id in reopened.character.abilityIds)
        assertEquals("Il privilegio segue una regola della casa.", reopened.character.classFeatures)
        assertEquals("Il talento proviene da una ricompensa narrativa.", reopened.character.feats)
        assertEquals(originalFeatureIds, reopened.character.progression.selectedFeatureIds)
        assertEquals(originalFeatIds, reopened.character.progression.featIds)
        assertEquals(originalSelections, reopened.character.progression.selections)
        assertEquals(originalHistory, reopened.character.progression.advancementHistory)
    }

    @Test
    fun `una scheda salvata prima degli effetti li riceve alla riapertura`() {
        val file = directory.resolve("schede.json")
        val model = model(file)
        val tarvos = model.library.characters.first { it.id == "pg-tarvos" }
        assertEquals(19, tarvos.effectiveArmorClass)

        // Archivio come quello di chi usava l'app prima: progressione completa,
        // ma nessun effetto registrato.
        SheetStore(file).save(
            model.library.copy(
                characters = model.library.characters.map {
                    it.copy(progression = it.progression.copy(effects = emptyList()))
                },
            ),
        )

        val reopened = model(file)
        val restored = reopened.library.characters.first { it.id == "pg-tarvos" }

        assertTrue(restored.progression.effects.any { it.source == "Difesa" })
        assertEquals(19, restored.effectiveArmorClass)
        // E la correzione e' finita su disco, non solo in memoria.
        assertTrue(
            SheetStore(file).load().characters
                .first { it.id == "pg-tarvos" }
                .progression.effects.isNotEmpty(),
        )
    }

    @Test
    fun `privilegi e talenti non vanno piu' ricopiati a mano nei campi di testo`() {
        val file = directory.resolve("schede.json")
        val model = model(file)
        val tarvos = model.library.characters.first { it.id == "pg-tarvos" }

        // La progressione li conosce: la scheda non deve chiedere di riscriverli.
        assertTrue(tarvos.progression.selectedFeatureIds.isNotEmpty())
        assertTrue(tarvos.progression.featIds.isNotEmpty())
        assertTrue(tarvos.classFeatures.isBlank(), "i privilegi sono di nuovo testo da ricopiare")
        assertTrue(tarvos.feats.isBlank())

        // Un archivio vecchio porta l'elenco di nomi generato allora: va tolto,
        // perche' ora la scheda mostra le stesse voci dalla progressione.
        val vecchio = tarvos.copy(
            classFeatures = "• Padronanza d'armi\n• Recuperare energie\n• Stile di combattimento\n" +
                "• Difesa\n• Spada lunga\n• Giavellotto\n• Ascia da battaglia",
            feats = "• Aggressore selvaggio",
        )
        SheetStore(file).save(
            model.library.copy(characters = model.library.characters.map { if (it.id == vecchio.id) vecchio else it }),
        )

        val ripulito = model(file).library.characters.first { it.id == "pg-tarvos" }
        assertTrue(ripulito.classFeatures.isBlank())
        assertTrue(ripulito.feats.isBlank())
    }

    @Test
    fun `un passaggio di livello non cancella le note scritte a mano`() {
        val model = model()
        val nota = "Al nostro tavolo Recuperare energie si usa anche fuori dal turno."
        val tarvos = model.library.characters.first { it.id == "pg-tarvos" }.copy(classFeatures = nota)

        assertTrue(model.upsertCharacterSilently(tarvos))
        val riletto = model.library.characters.first { it.id == "pg-tarvos" }
        assertEquals(nota, riletto.classFeatures)

        // E la nota sopravvive anche alla ripulitura dell'elenco generato.
        val reopened = model(directory.resolve("schede.json"))
        assertEquals(nota, reopened.library.characters.first { it.id == "pg-tarvos" }.classFeatures)
    }

    @Test
    fun `una voce SRD si puo riclassificare senza modificare il pacchetto`() {
        val file = directory.resolve("schede.json")
        val model = model(file)
        val srd = model.abilityCatalog.first { it.id.startsWith("srd521-it:") && !it.passive }

        assertTrue(model.setAbilityPassive(srd.id, true))
        assertTrue(model.abilityCatalog.first { it.id == srd.id }.passive)
        assertTrue(model.abilityPassiveIsOverridden(srd.id))
        // Il pacchetto resta in sola lettura: la scelta vive nell'archivio utente,
        // non in una copia modificata della voce.
        assertTrue(model.library.abilities.none { it.id == srd.id })
        assertFalse(model.upsertAbility(srd.copy(name = "Modificata")))

        val reopened = model(file)
        assertTrue(reopened.abilityCatalog.first { it.id == srd.id }.passive)

        // Tornare al valore del pacchetto cancella l'annotazione.
        assertTrue(reopened.setAbilityPassive(srd.id, false))
        assertFalse(reopened.abilityPassiveIsOverridden(srd.id))
        assertFalse(model(file).abilityCatalog.first { it.id == srd.id }.passive)
    }

    @Test
    fun `una abilita personale porta la classificazione in se stessa`() {
        val model = model()
        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))

        assertTrue(model.setAbilityPassive(ability.id, true))

        assertTrue(model.library.abilities.first { it.id == ability.id }.passive)
        assertFalse(model.abilityPassiveIsOverridden(ability.id))
    }

    @Test
    fun `una abilita usata da una scheda non puo essere eliminata`() {
        val model = model()
        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))
        model.character = model.character.copy(abilityIds = listOf(ability.id))

        assertFalse(model.deleteAbility(ability.id))
        assertTrue(model.library.abilities.any { it.id == ability.id })
    }
}
