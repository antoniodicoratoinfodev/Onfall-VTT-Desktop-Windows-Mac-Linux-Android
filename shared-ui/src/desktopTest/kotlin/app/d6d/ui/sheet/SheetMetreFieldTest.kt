package app.d6d.ui.sheet

import app.d6d.i18n.AppLanguage
import app.d6d.sheet.i18n.distanceValue
import app.d6d.sheet.i18n.feetFromDistance
import app.d6d.sheet.i18n.parseDistance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regressione della battitura nel campo metrico.
 *
 * Il campo non puo' rigenerare il testo a ogni cambio del valore: scrivere «1,5»
 * passa per «1», che vale 3 piedi, e riscrivere la forma canonica lo
 * trasformerebbe in «0,9» al secondo carattere. Qui si riproduce la sequenza di
 * tasti e si verifica che il testo resti quello digitato.
 */
class SheetMetreFieldTest {

    /** Un campo simulato: stessa logica del composable, senza Compose. */
    private class Field(initialFeet: Int, private val language: AppLanguage = AppLanguage.ITALIAN) {
        var feet = initialFeet
            private set
        var draft = distanceValue(initialFeet, language)
            private set

        fun type(text: String) {
            val cleaned = text.trim()
            if (!cleaned.matches(Regex("""-?\d*(?:[,.]\d*)?"""))) return
            draft = cleaned
            parseDistance(cleaned)?.let { distance ->
                val updated = feetFromDistance(distance, language)
                if (updated != feet) {
                    feet = updated
                    // È l'effetto agganciato a `feet`: riallinea solo se necessario.
                    if (distanceDraftIsStale(draft, feet, language)) {
                        draft = distanceValue(feet, language)
                    }
                }
            }
        }

        /** Cambio che arriva da fuori: Undo, caricamento di un'altra scheda. */
        fun receiveExternal(newFeet: Int) {
            feet = newFeet
            if (distanceDraftIsStale(draft, feet, language)) draft = distanceValue(feet, language)
        }

        fun blur() {
            draft = distanceValue(feet, language)
        }
    }

    @Test
    fun `digitare una misura decimale non riscrive il campo sotto le dita`() {
        val field = Field(initialFeet = 30)

        field.type("")
        field.type("1")
        assertEquals("1", field.draft, "Il primo carattere è stato sostituito")
        field.type("1,")
        assertEquals("1,", field.draft, "La virgola appena digitata è stata persa")
        field.type("1,5")

        assertEquals("1,5", field.draft)
        assertEquals(5, field.feet)
    }

    @Test
    fun `una misura intera resta come scritta`() {
        val field = Field(initialFeet = 5)

        field.type("9")

        assertEquals("9", field.draft)
        assertEquals(30, field.feet)
    }

    @Test
    fun `il punto e la virgola valgono la stessa misura`() {
        val conPunto = Field(initialFeet = 30).apply { type("4.5") }
        val conVirgola = Field(initialFeet = 30).apply { type("4,5") }

        assertEquals(15, conPunto.feet)
        assertEquals(15, conVirgola.feet)
        // Nessuna delle due scritture viene corretta mentre il campo ha il fuoco.
        assertEquals("4.5", conPunto.draft)
        assertEquals("4,5", conVirgola.draft)
    }

    @Test
    fun `uscendo dal campo la misura torna in forma canonica`() {
        val field = Field(initialFeet = 30)
        field.type("4.5")

        field.blur()

        assertEquals("4,5", field.draft)
        assertEquals(15, field.feet)
    }

    @Test
    fun `una misura lasciata a meta' non resta a schermo`() {
        val field = Field(initialFeet = 30)
        field.type("1,")

        field.blur()

        assertEquals("0,9", field.draft, "«1,» vale 3 piedi: uscendo deve mostrarlo")
        assertEquals(3, field.feet)
    }

    @Test
    fun `un valore che arriva da fuori riallinea il campo`() {
        val field = Field(initialFeet = 5)
        field.type("1,5")

        field.receiveExternal(30)

        assertEquals("9", field.draft)
        assertEquals(30, field.feet)
    }

    @Test
    fun `la stessa misura scritta in modo diverso non e' obsoleta`() {
        assertFalse(distanceDraftIsStale("1,5", 5, AppLanguage.ITALIAN))
        assertFalse(distanceDraftIsStale("1.5", 5, AppLanguage.ITALIAN))
        assertFalse(distanceDraftIsStale("1,50", 5, AppLanguage.ITALIAN))
        assertFalse(distanceDraftIsStale("9", 30, AppLanguage.ITALIAN))
        assertTrue(distanceDraftIsStale("1,5", 30, AppLanguage.ITALIAN))
        assertTrue(distanceDraftIsStale("", 30, AppLanguage.ITALIAN))
    }

    @Test
    fun `il campo inglese legge e scrive direttamente i piedi`() {
        val field = Field(initialFeet = 30, language = AppLanguage.ENGLISH)

        assertEquals("30", field.draft)
        field.type("45")

        assertEquals("45", field.draft)
        assertEquals(45, field.feet)
        assertFalse(distanceDraftIsStale("45", 45, AppLanguage.ENGLISH))
    }
}
