package app.d6d.ui.content

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.ResourcePoolState
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.SpellEntry
import app.d6d.sheet.Spellcasting
import app.d6d.sheet.WeaponEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Il testo SRD gia' scritto in una scheda deve seguire il cambio di lingua.
 *
 * La procedura guidata materializza nella scheda molto piu' degli identificativi:
 * classe, sottoclasse, background, nomi delle armi, strumenti, equipaggiamento.
 * Senza ritraduzione, un personaggio creato in italiano e riletto in inglese
 * mostrava le classi tradotte e tutto il resto no.
 *
 * L'altra meta' della prova conta quanto la prima: cio' che l'utente ha scritto
 * di suo non deve essere toccato.
 */
class SheetTranslationTest {
    private fun italianCharacter() = CharacterSheet(
        id = "pg",
        characterName = "Aria",
        className = "Ladro 3",
        subclass = "Furfante",
        background = "Criminale",
        weapons = listOf(
            WeaponEntry(name = "Spada corta"),
            WeaponEntry(name = "Pugnale"),
            WeaponEntry(name = "Bastone del nonno"),
        ),
        toolProficiencies = "Arnesi da scasso, Scorte da calligrafo, Cetra di famiglia",
        equipment = "Piede di porco, Faretra, La lettera del mandante",
        progression = CharacterProgression(
            backgroundId = "srd521-it:background:criminale",
            classLevels = listOf(ClassLevelState(CharacterClassId.ROGUE, 3)),
        ),
    )

    @Test
    fun `classe background e armi passano all'inglese`() {
        val english = italianCharacter()
            .retranslatedTo(AppLanguage.ENGLISH)

        assertEquals("Rogue 3", english.className)
        assertEquals("Criminal", english.background)
        assertEquals("Shortsword", english.weapons[0].name)
        assertEquals("Dagger", english.weapons[1].name)
    }

    @Test
    fun `strumenti ed equipaggiamento si traducono voce per voce`() {
        val english = italianCharacter()
            .retranslatedTo(AppLanguage.ENGLISH)

        assertTrue("Thieves' Tools" in english.toolProficiencies, english.toolProficiencies)
        assertTrue("Calligrapher's Supplies" in english.toolProficiencies, english.toolProficiencies)
        assertTrue("Crowbar" in english.equipment, english.equipment)
        assertTrue("Quiver" in english.equipment, english.equipment)
    }

    @Test
    fun `cio' che ha scritto l'utente resta intatto`() {
        // La regola che rende sicura questa funzione: si traduce solo cio' di cui
        // si conosce la provenienza. Un'arma inventata, uno strumento di famiglia
        // e una nota di trama non combaciano con nessuna voce del pacchetto, e
        // devono attraversare il cambio di lingua senza un graffio.
        val english = italianCharacter()
            .retranslatedTo(AppLanguage.ENGLISH)

        assertEquals("Bastone del nonno", english.weapons[2].name)
        assertTrue("Cetra di famiglia" in english.toolProficiencies, english.toolProficiencies)
        assertTrue("La lettera del mandante" in english.equipment, english.equipment)
        assertEquals("Aria", english.characterName)
    }

    @Test
    fun `la punteggiatura dell'elenco sopravvive`() {
        val english = italianCharacter()
            .retranslatedTo(AppLanguage.ENGLISH)
        assertEquals(3, english.equipment.split(',').size, english.equipment)
        assertEquals(3, english.toolProficiencies.split(',').size, english.toolProficiencies)
    }

    @Test
    fun `il viaggio di andata e ritorno riporta il testo di partenza`() {
        // Se la corrispondenza non fosse biunivoca, un giro IT→EN→IT lascerebbe
        // detriti: e' il modo piu' economico per accorgersene.
        val original = italianCharacter()
        val roundTrip = original
            .retranslatedTo(AppLanguage.ENGLISH)
            .retranslatedTo(AppLanguage.ITALIAN)

        assertEquals(original.className, roundTrip.className)
        assertEquals(original.background, roundTrip.background)
        assertEquals(original.weapons.map { it.name }, roundTrip.weapons.map { it.name })
        assertEquals(original.toolProficiencies, roundTrip.toolProficiencies)
        assertEquals(original.equipment, roundTrip.equipment)
    }

    @Test
    fun `un background personalizzato non viene riscritto`() {
        // Il difetto che questo controllo sorveglia: riderivare il background
        // dall'identificativo lo riportava al valore canonico, cancellando la
        // personalizzazione di chi gioca. Ora si sostituisce solo cio' che
        // coincide *esattamente* col valore canonico di partenza.
        val personalizzato = italianCharacter().copy(background = "Criminale in fuga")
        val english = personalizzato.retranslatedTo(AppLanguage.ENGLISH)
        assertEquals("Criminale in fuga", english.background)
    }

    @Test
    fun `strumenti musicali e lingue seguono la traduzione`() {
        val sheet = italianCharacter().copy(
            toolProficiencies = "Liuto, Cornamusa",
            languages = "Comune, Elfico, Draconico",
        )
        val english = sheet.retranslatedTo(AppLanguage.ENGLISH)

        assertEquals("Lute, Bagpipes", english.toolProficiencies)
        assertEquals("Common, Elvish, Draconic", english.languages)
    }

    @Test
    fun `incantesimi e privilegi fra le armi seguono la traduzione`() {
        // Un incantesimo lanciabile finisce fra le "armi" della scheda: e' li'
        // che la battaglia lo trova. Deve tradursi come tutto il resto.
        val sheet = italianCharacter().copy(
            weapons = listOf(
                WeaponEntry(name = "Palla di fuoco"),
                WeaponEntry(name = "Dardo incantato"),
                WeaponEntry(name = "Il mio incantesimo di casa"),
            ),
            classFeatures = "Ira, Attacco furtivo",
        )
        val english = sheet.retranslatedTo(AppLanguage.ENGLISH)

        assertEquals("Fireball", english.weapons[0].name)
        assertEquals("Magic Missile", english.weapons[1].name)
        assertEquals("Il mio incantesimo di casa", english.weapons[2].name)
        assertTrue("Rage" in english.classFeatures, english.classFeatures)
    }

    @Test
    fun `una dotazione reale non resta mista`() {
        // La dotazione A del ladro, parola per parola come la scrive il pacchetto.
        val sheet = italianCharacter().copy(
            equipment = "Arnesi da scasso, Dotazione da scassinatore, 20 frecce, Faretra",
        )
        val english = sheet.retranslatedTo(AppLanguage.ENGLISH)

        assertEquals("Thieves' Tools, Burglar's Pack, 20 Arrows, Quiver", english.equipment)
    }

    @Test
    fun `la lingua di partenza si legge dalla scheda`() {
        val english = italianCharacter().retranslatedTo(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, english.contentLanguage)
        // E una scheda gia' inglese non viene ritradotta una seconda volta.
        assertEquals(english, english.retranslatedTo(AppLanguage.ENGLISH))
    }

    @Test
    fun `gli incantesimi si rigenerano dal pacchetto, dati compresi`() {
        // Non basta tradurre il nome: tempo di lancio, gittata e nota restavano
        // nella lingua di prima. Rigenerare dal pacchetto li porta tutti.
        val sheet = italianCharacter().copy(
            spellcasting = Spellcasting(
                spells = listOf(
                    SpellEntry(
                        level = 3,
                        name = "Palla di fuoco",
                        castingTime = "Azione",
                        range = "45 metri",
                        note = "Sempre preparato",
                    ),
                    SpellEntry(level = 1, name = "Il mio incantesimo di casa", note = "appunto mio"),
                ),
            ),
        )
        val english = sheet.retranslatedTo(AppLanguage.ENGLISH)
        val spells = requireNotNull(english.spellcasting).spells

        assertEquals("Fireball", spells[0].name)
        assertEquals("Always prepared", spells[0].note)
        assertTrue("feet" in spells[0].range || "ft" in spells[0].range, spells[0].range)
        assertFalse("metri" in spells[0].castingTime, spells[0].castingTime)
        // L'incantesimo di casa non e' nel pacchetto: resta intatto, nota compresa.
        assertEquals("Il mio incantesimo di casa", spells[1].name)
        assertEquals("appunto mio", spells[1].note)
    }

    @Test
    fun `i nomi delle risorse si rigenerano dall'identificativo`() {
        // Le risorse finiscono in combattimento tramite la proiezione da attore:
        // un nome rimasto in italiano si vedeva nella barra dei combattenti.
        val sheet = italianCharacter().copy(
            progression = italianCharacter().progression.copy(
                resourcePools = listOf(
                    ResourcePoolState(
                        resourceId = "srd521-it:resource:barbaro:ira",
                        name = "Ira",
                        maximum = 2,
                        recovery = RecoveryPeriod.LONG_REST,
                    ),
                ),
            ),
        )
        val english = sheet.retranslatedTo(AppLanguage.ENGLISH)
        assertEquals("Rage", english.progression.resourcePools.single().name)
    }

    @Test
    fun `restare nella stessa lingua non cambia nulla`() {
        val sheet = italianCharacter()
        assertEquals(
            sheet,
            sheet.retranslatedTo(AppLanguage.ITALIAN),
        )
    }
}
