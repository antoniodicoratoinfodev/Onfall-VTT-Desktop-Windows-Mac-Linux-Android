package app.d6d.ui.encounter

import app.d6d.domain.combat.CombatStatus
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.ui.state.EnemyCpuSpeed
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.SheetStore
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.sheet.SheetKind
import app.d6d.ui.state.BattleViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EncounterBuilderViewModelTest {

    @TempDir
    lateinit var directory: Path

    private fun roster() = RosterViewModel(
        ActorCatalogStore(directory),
        SheetStore(directory.resolve("schede.json")),
    )

    @Test
    fun `il preset propone i personaggi come alleati e lascia opzionali le creature`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 42L })

        val characters = builder.participants.filter { it.kind == RosterKind.PERSONAGGIO }
        val creatures = builder.participants.filter { it.kind == RosterKind.CREATURA }

        assertTrue(characters.isNotEmpty())
        assertTrue(characters.all { it.selected && it.faction == EncounterFaction.ALLEATI })
        assertTrue(creatures.isNotEmpty())
        assertTrue(creatures.all { !it.selected && it.faction == EncounterFaction.AVVERSARI })
        assertEquals(characters.size, builder.selectedCount)
    }

    @Test
    fun `crea una sessione attiva con fazioni quantita e iniziativa statica`() {
        val roster = roster()
        val builder = EncounterBuilderViewModel(roster, seedProvider = { 73L })
        val hero = builder.participants.first { it.kind == RosterKind.PERSONAGGIO }
        val creature = builder.participants.first { it.kind == RosterKind.CREATURA }
        builder.clearSelection()
        builder.encounterName = "Assalto al ponte"
        builder.setSelected(hero.id, true)
        builder.setFaction(hero.id, EncounterFaction.ALLEATI)
        builder.setSelected(creature.id, true)
        builder.setFaction(creature.id, EncounterFaction.AVVERSARI)
        builder.setQuantity(creature.id, 2)

        val state = builder.startedSession().currentState()

        assertEquals("Assalto al ponte", state.encounterId())
        assertEquals(CombatStatus.ACTIVE, state.status())
        assertEquals(1, state.round())
        assertEquals(3, state.combatants().size)
        assertEquals(setOf(hero.id), state.partyCombatantIds())
        assertEquals(73L, state.randomSeed())

        val creatureInstances = state.combatants().values
            .filter { it.snapshot().definitionId() == creature.id }
        assertEquals(2, creatureInstances.size)
        assertEquals(setOf("${creature.name} 1", "${creature.name} 2"), creatureInstances.map { it.snapshot().name() }.toSet())

        // Il suffisso distingue le pedine, ma non deve rinominare lo stat block
        // autorevole se una di esse viene corretta durante il combattimento.
        roster.applyCombatEdit(creature.id, creatureInstances.first().snapshot())
        assertEquals(creature.name, roster.definitionFor(creature.id)!!.name())

        state.combatants().forEach { (instanceId, combatant) ->
            assertEquals(combatant.snapshot().initiativeScore(), state.initiativeScores()[instanceId])
        }
    }

    @Test
    fun `la sessione usa le statistiche della scheda appena salvata`() {
        val roster = roster()
        val hero = roster.items.first { it.kind == RosterKind.PERSONAGGIO }
        roster.sheets.kind = SheetKind.PERSONAGGIO
        roster.sheets.selectCharacter(hero.id)
        roster.sheets.character = roster.sheets.character.copy(
            armorClass = 23,
            armorClassMethod = ArmorClassMethod.MANUAL_TOTAL,
            armorClassOverride = null,
            maxHitPoints = 47,
        )
        roster.sheets.save()

        val builder = EncounterBuilderViewModel(roster, seedProvider = { 1L })
        builder.clearSelection()
        builder.setSelected(hero.id, true)

        val snapshot = builder.startedSession().currentState().combatants()[hero.id]?.snapshot()

        assertNotNull(snapshot)
        assertEquals(23, snapshot!!.armorClass())
        assertEquals(47, snapshot.maxHitPoints())
    }

    @Test
    fun `lo stesso personaggio ha stato runtime indipendente in due sessioni`() {
        val roster = roster()
        val builder = EncounterBuilderViewModel(roster, seedProvider = { 7L })
        val hero = builder.participants.first { it.kind == RosterKind.PERSONAGGIO }
        val first = BattleViewModel(builder.startedSession())
        val second = BattleViewModel(builder.startedSession())
        val hitPoints = second.combatant(hero.id)!!.currentHitPoints()
        val armorClass = second.combatant(hero.id)!!.snapshot().armorClass()

        first.applyManualDamage(hero.id, 5)
        first.editCombatant(hero.id, armorClass = armorClass + 3)
        first.configureMap(31, 19, 10)

        assertTrue(first.combatant(hero.id)!!.currentHitPoints() < hitPoints)
        assertEquals(hitPoints, second.combatant(hero.id)!!.currentHitPoints())
        assertEquals(armorClass + 3, first.combatant(hero.id)!!.snapshot().armorClass())
        assertEquals(armorClass, second.combatant(hero.id)!!.snapshot().armorClass())
        assertEquals(20, second.battleMap.grid().columns())
        assertEquals(15, second.battleMap.grid().rows())
        assertEquals(5, second.battleMap.grid().feetPerSquare())
        assertEquals(armorClass, roster.definitionFor(hero.id)!!.armorClass())

        val third = BattleViewModel(builder.startedSession())
        assertEquals(armorClass, third.combatant(hero.id)!!.snapshot().armorClass())
    }

    @Test
    fun `quantita non scende mai sotto uno e una selezione vuota non parte`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })
        val first = builder.participants.first()

        builder.setQuantity(first.id, -20)
        assertEquals(1, builder.participants.first { it.id == first.id }.quantity)

        builder.clearSelection()
        assertFalse(builder.canStart)
        assertNull(builder.tryStart())
        assertEquals("Seleziona almeno un partecipante.", builder.status)
    }

    @Test
    fun `una sessione mono fazione resta valida ma dichiara la cpu inattiva`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })
        val hero = builder.participants.first { it.kind == RosterKind.PERSONAGGIO }
        val creature = builder.participants.first { it.kind == RosterKind.CREATURA }
        builder.clearSelection()

        builder.setSelected(hero.id, true)
        assertTrue(builder.canStart)
        val noEnemies = requireNotNull(builder.enemyCpuInactiveReason)
        assertTrue(noEnemies.contains("alcun avversario"))
        assertTrue(enemyCpuInactiveWarning(noEnemies).contains("sessione mono-fazione"))
        assertFalse(enemyCpuInactiveWarning(noEnemies).contains("esplorativa"))

        builder.setSelected(hero.id, false)
        builder.setSelected(creature.id, true)
        assertTrue(builder.canStart)
        assertTrue(builder.enemyCpuInactiveReason.orEmpty().contains("almeno un alleato"))

        builder.setSelected(hero.id, true)
        assertNull(builder.enemyCpuInactiveReason)
    }

    @Test
    fun `la sandbox avvia la partita senza alcuna cpu nemica`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })
        val creature = builder.participants.first { it.kind == RosterKind.CREATURA }

        builder.enemyCpuDifficulty = null
        val presentation = newEncounterPresentation(builder.mode, builder.enemyCpuDifficulty)
        assertFalse("enemyCpuDifficulty" in presentation)
        assertEquals(builder.mode.name, presentation["encounterMode"])

        // Senza CPU non ha senso avvertire che la CPU non potrebbe agire.
        builder.clearSelection()
        builder.setSelected(creature.id, true)
        assertNull(builder.enemyCpuInactiveReason)

        val battle = BattleViewModel(builder.startedSession())
        battle.adopt(battle.session, presentation)
        assertFalse(battle.enemyCpuEnabled)
        assertFalse(battle.shouldScheduleEnemyCpu)
        assertNull(battle.enemyCpuTurnKey)
    }

    @Test
    fun `scegliere una difficolta riaccende la cpu nella partita creata`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })

        builder.enemyCpuDifficulty = null
        builder.enemyCpuDifficulty = EnemyCpuDifficulty.SORRY_FOR_YOU
        val presentation = newEncounterPresentation(builder.mode, builder.enemyCpuDifficulty)

        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU.name, presentation["enemyCpuDifficulty"])

        val battle = BattleViewModel(builder.startedSession())
        battle.adopt(battle.session, presentation)
        assertTrue(battle.enemyCpuEnabled)
        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU, battle.enemyCpuDifficulty)
    }

    @Test
    fun `il ritmo scelto nella procedura arriva nella partita`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })
        assertEquals(EnemyCpuSpeed.NORMAL, builder.enemyCpuSpeed)

        builder.enemyCpuSpeed = EnemyCpuSpeed.SLOW
        val battle = BattleViewModel(builder.startedSession())
        battle.adopt(
            battle.session,
            newEncounterPresentation(builder.mode, builder.enemyCpuDifficulty, builder.enemyCpuSpeed),
        )
        assertEquals(EnemyCpuSpeed.SLOW, battle.enemyCpuSpeed)

        // Senza CPU non c'e' ritmo da salvare: la partita e' tutta del tavolo.
        val sandbox = newEncounterPresentation(builder.mode, null, builder.enemyCpuSpeed)
        assertFalse("enemyCpuSpeed" in sandbox)

        builder.restartWizard()
        assertEquals(EnemyCpuSpeed.NORMAL, builder.enemyCpuSpeed)
    }

    @Test
    fun `il copy difficolta confronta i livelli senza promettere piani tra turni`() {
        val easy = EnemyCpuDifficulty.EASY.italianComparison
        val medium = EnemyCpuDifficulty.MEDIUM.italianComparison
        val hard = EnemyCpuDifficulty.SORRY_FOR_YOU.italianComparison

        assertTrue(easy.contains("Rispetto a Medio"))
        assertTrue(easy.contains("scelte semplici"))
        assertTrue(easy.contains("slot minimo"))
        assertTrue(medium.contains("Rispetto a Facile"))
        assertTrue(medium.contains("Mi dispiace per te!"))
        assertTrue(medium.contains("potenzia una cura"))
        assertTrue(hard.contains("Rispetto al normale"))
        assertTrue(hard.contains("slot superiori"))
        assertFalse(listOf(easy, medium, hard).any { it.contains("oltre il turno", ignoreCase = true) })
        assertFalse(listOf(easy, medium, hard).any { it.contains("un turno alla volta", ignoreCase = true) })
    }

    @Test
    fun `nuova partita guida fino alla difficolta e il reset ripristina medio`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })

        assertEquals(NewGameStep.TEMPLATE, builder.step)
        assertEquals(EnemyCpuDifficulty.MEDIUM, builder.enemyCpuDifficulty)
        builder.useExistingTemplates()
        assertEquals(TemplateSource.ESISTENTI, builder.templateSource)
        assertEquals(NewGameStep.PARTECIPANTI, builder.step)

        builder.continueFromParticipants()
        assertEquals(NewGameStep.GRIGLIA, builder.step)
        builder.updateGridColumns(30)
        builder.updateGridRows(20)
        builder.updateFeetPerSquare(10)
        builder.continueFromGrid()

        assertEquals(NewGameStep.MODALITA, builder.step)
        assertEquals(30, builder.gridColumns)
        assertEquals(20, builder.gridRows)
        assertEquals(10, builder.feetPerSquare)

        builder.continueFromMode()
        assertEquals(NewGameStep.DIFFICOLTA, builder.step)
        builder.enemyCpuDifficulty = EnemyCpuDifficulty.SORRY_FOR_YOU
        builder.back()
        assertEquals(NewGameStep.MODALITA, builder.step)
        builder.continueFromMode()
        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU, builder.enemyCpuDifficulty)

        builder.restartWizard()
        assertEquals(NewGameStep.TEMPLATE, builder.step)
        assertNull(builder.templateSource)
        assertEquals(20, builder.gridColumns)
        assertEquals(15, builder.gridRows)
        assertEquals(5, builder.feetPerSquare)
        assertEquals(EnemyCpuDifficulty.MEDIUM, builder.enemyCpuDifficulty)
    }

    @Test
    fun `creare da zero non cancella i template e mostra solo le nuove schede`() {
        val roster = roster()
        val originalIds = roster.items.map { it.id }.toSet()
        val builder = EncounterBuilderViewModel(roster, seedProvider = { 1L })

        builder.createFromScratch()
        assertTrue(builder.participants.isEmpty())
        assertTrue(originalIds.all { id -> roster.items.any { it.id == id } })

        roster.newCharacter()
        roster.sheets.character = roster.sheets.character.copy(characterName = "Eroe nuovo")
        assertTrue(roster.sheets.save())
        roster.newCreature()
        roster.sheets.monster = roster.sheets.monster.copy(name = "Mob nuovo")
        assertTrue(roster.sheets.save())

        assertEquals(setOf("Eroe nuovo", "Mob nuovo"), builder.participants.map { it.name }.toSet())
        assertTrue(originalIds.all { id -> roster.items.any { it.id == id } })
    }

    @Test
    fun `fight configura la griglia e mette i due schieramenti vicini`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 9L })
        val hero = builder.participants.first { it.kind == RosterKind.PERSONAGGIO }
        val creature = builder.participants.first { it.kind == RosterKind.CREATURA }
        builder.clearSelection()
        builder.setSelected(hero.id, true)
        builder.setSelected(creature.id, true)
        builder.mode = EncounterMode.FIGHT

        val state = builder.startedSession().currentState()

        assertTrue(state.battleMap().configured())
        assertTrue(state.battleMap().isPlaced(hero.id))
        assertTrue(state.battleMap().isPlaced(creature.id))
        assertTrue(state.distanceFeet(hero.id, creature.id).orElseThrow() <= 20)
    }

    @Test
    fun `roleplay fight exploration prepara la griglia senza imporre i token`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 11L })
        builder.mode = EncounterMode.ROLEPLAY_FIGHT_EXPLORATION

        val state = builder.startedSession().currentState()

        assertTrue(state.battleMap().configured())
        assertTrue(state.battleMap().orderedPlacements().isEmpty())
    }

    @Test
    fun `una partita inclusa compila squadra, avversari, nome e griglia`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 13L })
        val template = builder.includedTemplates.first { it.partyLevel == 4 }

        builder.useIncludedTemplate(template)

        assertEquals(NewGameStep.PARTECIPANTI, builder.step)
        assertEquals(template.name, builder.encounterName)
        assertEquals(template.gridColumns, builder.gridColumns)
        assertEquals(template.gridRows, builder.gridRows)
        assertEquals(template.party.size, builder.allyCount)
        assertEquals(template.opponentCount, builder.opponentCount)

        val selected = builder.participants.filter { it.selected }.map { it.id }.toSet()
        assertEquals(
            (template.party.map { it.id } + template.opponents.map { it.statBlock.id }).toSet(),
            selected,
        )

        // Il template resta uno stampo: la partita che ne nasce e' modificabile
        // come le altre, e avviarla non lo consuma.
        val state = builder.startedSession().currentState()
        assertEquals(template.party.size + template.opponentCount, state.combatants().size)
        assertEquals(template.party.size, state.partyCombatantIds().size)
    }

    @Test
    fun `una partita inclusa rimette le schede cancellate dal compendio`() {
        val roster = roster()
        val builder = EncounterBuilderViewModel(roster, seedProvider = { 17L })
        val template = builder.includedTemplates.first { it.partyLevel == 1 }
        val victim = template.party.first()
        roster.sheets.kind = SheetKind.PERSONAGGIO
        roster.sheets.delete(victim.id)
        assertFalse(roster.items.any { it.id == victim.id })

        builder.useIncludedTemplate(template)

        assertTrue(roster.items.any { it.id == victim.id })
        assertTrue(builder.participants.any { it.id == victim.id && it.selected })
    }
}
