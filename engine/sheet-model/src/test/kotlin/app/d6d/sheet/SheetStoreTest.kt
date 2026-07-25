package app.d6d.sheet

import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SheetStoreTest {

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
        val store = SheetStore(file)

        store.save(SheetLibrary(abilities = listOf(ability)))

        assertEquals(ability, store.load().abilities.single())
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
}
