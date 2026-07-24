package app.d6d.ui.roster

import app.d6d.domain.campaign.ActorKind
import app.d6d.domain.catalog.ActorCatalogEntry
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.Proficiency
import app.d6d.sheet.SheetStore
import app.d6d.ui.sheet.SheetKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Unificazione di schede e compendio.
 *
 * Il punto verificato non e' che i file si scrivano, ma che il catalogo da
 * combattimento non possa piu' divergere dalle schede: le schede lo generano.
 */
class RosterViewModelTest {

    @TempDir
    lateinit var directory: Path

    private fun catalogStore() = ActorCatalogStore(directory)
    private fun sheetStore() = SheetStore(directory.resolve("schede.json"))

    private fun roster() = RosterViewModel(catalogStore(), sheetStore())

    private fun catalogEntry(id: String): ActorCatalogEntry? =
        catalogStore().load().firstOrNull { it.combatDefinition().id() == id }

    @Test
    fun `il roster contiene sia personaggi sia creature`() {
        val roster = roster()

        assertTrue(roster.items.any { it.kind == RosterKind.PERSONAGGIO })
        assertTrue(roster.items.any { it.kind == RosterKind.CREATURA })
    }

    @Test
    fun `il catalogo viene generato dalle schede all'avvio`() {
        val roster = roster()

        // Ogni voce del roster ha una corrispondente entrata nel catalogo.
        roster.items.forEach { item ->
            assertNotNull(catalogEntry(item.id), "manca l'entrata di catalogo per ${item.id}")
        }
    }

    @Test
    fun `le statistiche di combattimento di un personaggio vengono dalla scheda`() {
        val roster = roster()
        val kaelen = roster.sheets.library.characters.first { it.id == "pg-kaelen" }

        val entry = catalogEntry("pg-kaelen")!!
        val definition = entry.combatDefinition()

        // Non sono valori scritti a mano nel catalogo: sono derivati dalla scheda.
        assertEquals(kaelen.armorClass, definition.armorClass())
        assertEquals(kaelen.maxHitPoints, definition.maxHitPoints())
        assertEquals(kaelen.initiativeModifier, definition.initiativeModifier())
        assertEquals(kaelen.saveBonus(Ability.CONSTITUTION), definition.constitutionSaveBonus())
        // Un personaggio in catalogo e' sempre un membro della squadra senza Grado di Sfida.
        assertEquals(ActorKind.PLAYER_CHARACTER, entry.template().kind())
        assertTrue(entry.activePartyMember())
        assertEquals(0, entry.challengeRating().signum())
    }

    @Test
    fun `modificare la scheda sovrascrive l'entrata di catalogo del personaggio`() {
        val roster = roster()
        roster.sheets.kind = SheetKind.PERSONAGGIO
        roster.sheets.selectCharacter("pg-kaelen")

        // Cambio la Destrezza: l'iniziativa derivata deve cambiare di conseguenza.
        val updated = roster.sheets.character.copy(
            armorClass = 21,
            abilityScores = roster.sheets.character.abilityScores + (Ability.DEXTERITY to 20),
        )
        roster.sheets.character = updated
        roster.sheets.save()

        val definition = catalogEntry("pg-kaelen")!!.combatDefinition()
        assertEquals(21, definition.armorClass())
        // Destrezza 20 → modificatore +5 → iniziativa +5.
        assertEquals(5, definition.initiativeModifier())
    }

    @Test
    fun `un nuovo personaggio salvato entra nel catalogo`() {
        val roster = roster()
        roster.newCharacter()
        roster.sheets.character = CharacterSheet(
            id = "pg-nuovo",
            characterName = "Nuovo Eroe",
            armorClass = 15,
            maxHitPoints = 24,
            abilityScores = mapOf(Ability.DEXTERITY to 16, Ability.CONSTITUTION to 14),
            saveProficiencies = mapOf(Ability.CONSTITUTION to Proficiency.PROFICIENT),
        )
        roster.sheets.save()

        val entry = catalogEntry("pg-nuovo")
        assertNotNull(entry)
        assertEquals("Nuovo Eroe", entry!!.combatDefinition().name())
        assertEquals(3, entry.combatDefinition().initiativeModifier())
        assertTrue(roster.items.any { it.id == "pg-nuovo" && it.kind == RosterKind.PERSONAGGIO })
    }

    @Test
    fun `eliminare una scheda la toglie dal catalogo`() {
        val roster = roster()
        roster.sheets.kind = SheetKind.PERSONAGGIO
        roster.sheets.selectCharacter("pg-kaelen")

        roster.sheets.delete("pg-kaelen")

        assertNull(catalogEntry("pg-kaelen"))
        assertFalse(roster.items.any { it.id == "pg-kaelen" })
    }

    @Test
    fun `una correzione in combattimento confluisce nella scheda del personaggio`() {
        val roster = roster()
        val before = roster.sheets.library.characters.first { it.id == "pg-kaelen" }
        val snapshot = snapshotFor(before.id, "Kaelen il Segnato", armorClass = 20, maxHitPoints = 40)

        roster.applyCombatEdit("pg-kaelen", snapshot)

        val after = roster.sheets.library.characters.first { it.id == "pg-kaelen" }
        assertEquals("Kaelen il Segnato", after.characterName)
        assertEquals(20, after.armorClass)
        assertEquals(40, after.maxHitPoints)
        // E il catalogo riflette la scheda aggiornata.
        assertEquals(20, catalogEntry("pg-kaelen")!!.combatDefinition().armorClass())
    }

    @Test
    fun `una correzione a un attore fuori dal roster non fa nulla`() {
        val roster = roster()
        val before = roster.items.size
        val snapshot = snapshotFor("attore-sconosciuto", "Ignoto", armorClass = 12, maxHitPoints = 10)

        roster.applyCombatEdit("attore-sconosciuto", snapshot)

        assertEquals(before, roster.items.size)
        assertNull(catalogEntry("attore-sconosciuto"))
    }

    @Test
    fun `le creature restano creature nel catalogo, non personaggi`() {
        val roster = roster()
        val creature = roster.items.first { it.kind == RosterKind.CREATURA }

        val entry = catalogEntry(creature.id)!!
        assertEquals(ActorKind.CREATURE, entry.template().kind())
        assertFalse(entry.activePartyMember())
    }

    @Test
    fun `riaprendo il roster il catalogo resta coerente con le schede`() {
        // Prima apertura: crea schede e catalogo.
        roster().sheets.library.characters.size

        // Seconda apertura sugli stessi file.
        val reopened = roster()
        reopened.items.forEach { item ->
            assertNotNull(catalogEntry(item.id))
        }
    }

    @Test
    fun `l'ingombro del segnaposto viene dalla taglia impostata nel compendio`() {
        val roster = roster()
        // Una creatura Media occupa una casella.
        val creature = roster.sheets.library.monsters.first()
        assertEquals(1, roster.footprintFor(creature.id))

        // Portandola a Grande nel compendio, il segnaposto passa a 2x2.
        roster.sheets.kind = SheetKind.MOSTRO
        roster.sheets.selectMonster(creature.id)
        roster.sheets.monster = roster.sheets.monster.copy(size = app.d6d.sheet.CreatureSize.LARGE)
        roster.sheets.save()

        assertEquals(2, roster.footprintFor(creature.id))
    }

    @Test
    fun `le taglie mappano il numero di caselle atteso`() {
        assertEquals(1, app.d6d.sheet.CreatureSize.TINY.squaresPerSide)
        assertEquals(1, app.d6d.sheet.CreatureSize.MEDIUM.squaresPerSide)
        assertEquals(2, app.d6d.sheet.CreatureSize.LARGE.squaresPerSide)
        assertEquals(3, app.d6d.sheet.CreatureSize.HUGE.squaresPerSide)
        assertEquals(4, app.d6d.sheet.CreatureSize.GARGANTUAN.squaresPerSide)
    }

    @Test
    fun `un attore fuori dal roster ricade su una casella`() {
        assertEquals(1, roster().footprintFor("attore-sconosciuto"))
    }

    @Test
    fun `le abilita del catalogo scelte nella scheda arrivano al combattimento e restano aggiornate`() {
        val roster = roster()
        val ability = CatalogAbility(
            id = "abilita-saetta",
            name = "Saetta del catalogo",
            diceCount = 2,
            diceSides = 6,
        )
        assertTrue(roster.sheets.upsertAbility(ability))

        roster.sheets.kind = SheetKind.PERSONAGGIO
        roster.sheets.selectCharacter("pg-kaelen")
        roster.sheets.character = roster.sheets.character.copy(abilityIds = listOf(ability.id))
        assertTrue(roster.sheets.save())

        assertEquals(
            2,
            roster.definitionFor("pg-kaelen")!!.ability(ability.id).damage().single().dice().count(),
        )

        assertTrue(roster.sheets.upsertAbility(ability.copy(diceCount = 4)))
        assertEquals(
            4,
            catalogEntry("pg-kaelen")!!.combatDefinition()
                .ability(ability.id).damage().single().dice().count(),
        )
    }

    private fun snapshotFor(id: String, name: String, armorClass: Int, maxHitPoints: Int): CombatantSnapshot =
        CombatantSnapshot(
            id, id, "1.0.0", "5.2.1", name,
            armorClass, maxHitPoints, maxHitPoints, 0, 30, 0, 10, 0,
            emptySet(), emptySet(), emptySet(), emptySet(), emptyList(),
        )
}
