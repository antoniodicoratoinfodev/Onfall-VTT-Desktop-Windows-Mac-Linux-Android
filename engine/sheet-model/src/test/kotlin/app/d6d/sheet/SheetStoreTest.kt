package app.d6d.sheet

import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SheetStoreTest {
    @Test
    fun `un archivio senza versione viene migrato dallo schema uno`() {
        val file = directory.resolve("versionless.json")
        Files.writeString(
            file,
            """{"characters":[],"monsters":[]}""",
        )

        val loaded = SheetStore(file).load()

        assertEquals(SheetLibrary.SCHEMA_VERSION, loaded.schemaVersion)
        assertTrue(loaded.abilities.isNotEmpty())
    }

    @TempDir
    lateinit var directory: Path

    @Test
    fun `un archivio precedente riceve il catalogo abilita iniziale`() {
        val file = directory.resolve("schede.json")
        Files.writeString(
            file,
            """{"schemaVersion":1,"characters":[],"monsters":[]}""",
        )

        val loaded = SheetStore(file).load()

        assertEquals(SheetLibrary.SCHEMA_VERSION, loaded.schemaVersion)
        assertTrue(loaded.abilities.any { it.name == "Palla di Fuoco" })
    }

    @Test
    fun `il catalogo conserva tutti i dati meccanici`() {
        val file = directory.resolve("schede.json")
        val ability = CatalogAbility(
            id = "abilita-tempesta",
            name = "Tempesta",
            activationCost = ActivationCost.BONUS_ACTION,
            resolutionMethod = ResolutionMethod.SAVING_THROW,
            attackAbility = Ability.WISDOM,
            spellOrCantrip = true,
            rangeFeet = 90,
            diceCount = 4,
            diceSides = 8,
            damageModifier = 2,
            damageType = DamageType.LIGHTNING,
            areaRadiusFeet = 15,
            saveAbility = Ability.DEXTERITY,
            halfOnSave = true,
            rulesText = "Informazioni complete.",
        )
        val automaticEffect = CatalogAbility(
            id = "azione-automatica",
            name = "Azione automatica",
            activationCost = ActivationCost.NONE,
            resolutionMethod = ResolutionMethod.AUTOMATIC,
            dealsDamage = false,
            resourceId = "risorsa-automatica",
            resourceCost = 1,
            effect = AbilityEffect.GRANT_NON_MAGIC_ACTION,
        )
        val store = SheetStore(file)

        store.save(SheetLibrary(abilities = listOf(ability, automaticEffect)))

        assertEquals(listOf(ability, automaticEffect), store.load().abilities)
    }

    @Test
    fun `il vecchio preset palla di fuoco diventa un riferimento al catalogo`() {
        val fireball = defaultAbilityCatalog().first { it.id == "inc-palla-di-fuoco" }
        val legacyRow = WeaponEntry(
            name = fireball.name,
            attackBonus = fireball.attackBonus,
            diceCount = fireball.diceCount,
            diceSides = fireball.diceSides,
            damageModifier = fireball.damageModifier,
            damageType = fireball.damageType,
            rangeFeet = fireball.rangeFeet,
            note = fireball.rulesText,
            bonusAction = fireball.activationCost == ActivationCost.BONUS_ACTION,
            areaRadiusFeet = fireball.areaRadiusFeet,
            saveAbility = fireball.saveAbility,
            halfOnSave = fireball.halfOnSave,
        )
        val store = SheetStore(directory.resolve("schede.json"))
        store.save(
            SheetLibrary(
                schemaVersion = 1,
                characters = listOf(CharacterSheet(id = "pg-mago", weapons = listOf(legacyRow))),
            ),
        )

        val migrated = store.load().characters.single()

        assertTrue(migrated.weapons.isEmpty())
        assertEquals(listOf(fireball.id), migrated.abilityIds)
    }

    @Test
    fun `la migrazione aggiunge le nuove abilita senza sovrascrivere quelle esistenti`() {
        val defaults = defaultAbilityCatalog()
        val customizedFireball = defaults.first { it.id == "inc-palla-di-fuoco" }
            .copy(name = "Palla personale")
        val personal = CatalogAbility(id = "abilita-personale", name = "Tecnica personale")
        val store = SheetStore(directory.resolve("schede.json"))
        store.save(
            SheetLibrary(
                schemaVersion = 2,
                abilities = listOf(customizedFireball, personal),
            ),
        )

        val migrated = store.load()

        assertEquals("Palla personale", migrated.abilities.first { it.id == customizedFireball.id }.name)
        assertTrue(migrated.abilities.any { it.id == personal.id })
        assertTrue(migrated.abilities.any { it.id == "arma-arco" })
        assertTrue(migrated.abilities.any { it.id == "nem-morso" })
        assertEquals(SheetLibrary.SCHEMA_VERSION, migrated.schemaVersion)
    }

    @Test
    fun `schema sette recupera i metadati meccanici delle abilita incorporate`() {
        val defaults = defaultAbilityCatalog()
        val builtInSpell = defaults.first { it.id == "inc-dardo-runico" }
        val builtInWeapon = defaults.first { it.id == "arma-spadone" }
        val legacySpell = builtInSpell.copy(attackAbility = null, spellOrCantrip = false)
        val legacyWeapon = builtInWeapon.copy(attackAbility = null)
        val store = SheetStore(directory.resolve("abilita-schema-7.json"))
        store.save(
            SheetLibrary(
                schemaVersion = 7,
                abilities = listOf(legacySpell, legacyWeapon),
            ),
        )

        val migrated = store.load().abilities

        assertTrue(migrated.first { it.id == legacySpell.id }.isSpellOrCantrip)
        assertEquals(
            builtInWeapon.attackAbility,
            migrated.first { it.id == legacyWeapon.id }.attackAbility,
        )
        assertEquals(legacySpell.name, migrated.first { it.id == legacySpell.id }.name)
    }

    @Test
    fun `schema otto classifica i preset e segnala soltanto le vecchie righe ambigue`() {
        val knownSpell = defaultAbilityCatalog().first { it.id == "inc-dardo-runico" }
        val legacyKnownSpell = knownSpell.toLegacyWeaponEntry()
        val ambiguous = WeaponEntry(name = "Attacco personale")
        val explicitMentalAttack = WeaponEntry(
            name = "Lama mentale",
            attackAbility = Ability.INTELLIGENCE,
        )
        val store = SheetStore(directory.resolve("righe-schema-8.json"))
        store.save(
            SheetLibrary(
                schemaVersion = 8,
                characters = listOf(
                    CharacterSheet(
                        id = "pg-righe-legacy",
                        weapons = listOf(legacyKnownSpell, ambiguous, explicitMentalAttack),
                    ),
                ),
            ),
        )

        val migrated = store.load()
        val weapons = migrated.characters.single().weapons

        assertTrue(weapons[0].isSpellOrCantrip)
        assertEquals(false, weapons[0].legacyClassificationRequired)
        assertTrue(weapons[1].legacyClassificationRequired)
        assertEquals(false, weapons[2].legacyClassificationRequired)
        assertEquals(Ability.INTELLIGENCE, weapons[2].attackAbility)
        assertEquals(SheetLibrary.SCHEMA_VERSION, migrated.schemaVersion)

        store.save(migrated)
        assertTrue(store.load().characters.single().weapons[1].legacyClassificationRequired)
    }

    @Test
    fun `una scheda precedente conserva la CA finale manuale senza doppi conteggi`() {
        val file = directory.resolve("schede.json")
        Files.writeString(
            file,
            """
            {
              "schemaVersion": 3,
              "characters": [
                {
                  "id": "pg-legacy",
                  "armorClass": 18,
                  "shieldEquipped": true,
                  "abilityScores": { "DEXTERITY": 18 }
                }
              ],
              "monsters": [],
              "abilities": []
            }
            """.trimIndent(),
        )

        val loaded = SheetStore(file).load().characters.single()

        assertEquals(ArmorClassMethod.MANUAL_TOTAL, loaded.armorClassMethod)
        assertEquals(18, loaded.baseArmorClass)
        assertEquals(18, loaded.effectiveArmorClass)
        assertEquals(SheetLibrary.SCHEMA_VERSION, SheetStore(file).load().schemaVersion)
    }

    @Test
    fun `metodo modificatori e override della CA sopravvivono al salvataggio`() {
        val file = directory.resolve("schede.json")
        val expected = CharacterSheet(
            id = "pg-ca",
            armorClassMethod = ArmorClassMethod.HALF_PLATE,
            shieldEquipped = true,
            armorTraining = ArmorTraining(shields = true),
            armorClassAdjustments = listOf(
                ArmorClassAdjustment("Anello", 1, active = true, id = "anello"),
                ArmorClassAdjustment("Incantesimo", 2, active = false, id = "incantesimo"),
            ),
            armorClassOverride = 22,
            abilityScores = mapOf(Ability.DEXTERITY to 18),
        )
        val store = SheetStore(file)

        store.save(SheetLibrary(characters = listOf(expected)))
        val reloaded = store.load().characters.single()

        assertEquals(expected, reloaded)
        assertEquals(20, reloaded.calculatedArmorClass)
        assertEquals(22, reloaded.effectiveArmorClass)
    }

    @Test
    fun `schema sette senza dettagli armatura usa i default retrocompatibili`() {
        val file = directory.resolve("schema-7.json")
        Files.writeString(
            file,
            """
            {
              "schemaVersion": 7,
              "characters": [
                {
                  "id": "pg-schema-7",
                  "armorClass": 16,
                  "armorClassMethod": "MANUAL_TOTAL"
                }
              ],
              "monsters": [],
              "abilities": []
            }
            """.trimIndent(),
        )

        val loaded = SheetStore(file).load().characters.single()

        assertEquals(null, loaded.manualArmorCategory)
        assertEquals(0, loaded.manualArmorMinimumStrength)
        assertEquals(false, loaded.manualArmorStealthDisadvantage)
        assertEquals(ArmorSpecialRule.STANDARD, loaded.armorSpecialRule)
        assertEquals(16, loaded.effectiveArmorClass)
        assertEquals(SheetLibrary.SCHEMA_VERSION, SheetStore(file).load().schemaVersion)
    }

    @Test
    fun `armatura libera e variante sopravvivono al salvataggio dello schema corrente`() {
        val file = directory.resolve("armatura-schema-corrente.json")
        val expected = CharacterSheet(
            id = "pg-armatura-libera",
            armorClass = 16,
            armorClassMethod = ArmorClassMethod.CUSTOM_BASE,
            manualArmorCategory = ArmorCategory.HEAVY,
            manualArmorMinimumStrength = 15,
            manualArmorStealthDisadvantage = true,
            armorSpecialRule = ArmorSpecialRule.MITHRAL,
        )
        val store = SheetStore(file)

        store.save(SheetLibrary(characters = listOf(expected)))
        val reloaded = store.load().characters.single()

        assertEquals(expected, reloaded)
        assertEquals(ArmorCategory.HEAVY, reloaded.wornArmorCategory)
        assertEquals(0, reloaded.effectiveArmorMinimumStrength)
        assertEquals(false, reloaded.armorStealthDisadvantage)
    }

    @Test
    fun `un archivio principale corrotto viene recuperato dal backup`() {
        val file = directory.resolve("recupero.json")
        val store = SheetStore(file)
        val recoverable = SheetLibrary(characters = listOf(CharacterSheet(id = "pg-recuperabile")))
        store.save(recoverable)
        store.save(SheetLibrary(characters = listOf(CharacterSheet(id = "pg-piu-recente"))))
        Files.writeString(file, "{ archivio interrotto")

        val recoveringStore = SheetStore(file)
        val loaded = recoveringStore.load()

        assertEquals("pg-recuperabile", loaded.characters.single().id)
        assertEquals(true, recoveringStore.recoveredFromBackup)
    }

    @Test
    fun `un archivio principale mancante viene recuperato dal backup`() {
        val file = directory.resolve("recupero-mancante.json")
        val store = SheetStore(file)
        store.save(SheetLibrary(characters = listOf(CharacterSheet(id = "pg-recuperabile"))))
        store.save(SheetLibrary(characters = listOf(CharacterSheet(id = "pg-piu-recente"))))
        Files.delete(file)

        val recoveringStore = SheetStore(file)

        assertTrue(recoveringStore.exists())
        assertEquals("pg-recuperabile", recoveringStore.load().characters.single().id)
        assertTrue(recoveringStore.recoveredFromBackup)
    }

    private fun CatalogAbility.toLegacyWeaponEntry(): WeaponEntry = WeaponEntry(
        name = name,
        attackBonus = attackBonus,
        diceCount = diceCount,
        diceSides = diceSides,
        damageModifier = damageModifier,
        damageType = damageType,
        rangeFeet = rangeFeet,
        note = rulesText,
        bonusAction = activationCost == ActivationCost.BONUS_ACTION,
        areaRadiusFeet = areaRadiusFeet,
        saveAbility = saveAbility,
        halfOnSave = halfOnSave,
    )
}
