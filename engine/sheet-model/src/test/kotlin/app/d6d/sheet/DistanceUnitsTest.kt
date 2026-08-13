package app.d6d.sheet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DistanceUnitsTest {

    @Test
    fun `converte i piedi con i valori del regolamento`() {
        // 5 piedi = 1,5 metri: la conversione ufficiale, non quella esatta del
        // piede internazionale, che darebbe 1,524 e non compare in nessuna tabella.
        assertEquals("1,5", metresFromFeet(5))
        assertEquals("3", metresFromFeet(10))
        assertEquals("4,5", metresFromFeet(15))
        assertEquals("9", metresFromFeet(30))
        assertEquals("18", metresFromFeet(60))
        assertEquals("45", metresFromFeet(150))
        assertEquals("−1,5", metresFromFeet(-5))
        assertEquals("0", metresFromFeet(0))
    }

    @Test
    fun `i valori interi non portano decimali inutili`() {
        assertEquals("9", metresFromFeet(30))
        assertEquals("9 m", metresLabel(30))
        assertEquals("1,5 m", metresLabel(5))
    }

    @Test
    fun `il giro di andata e ritorno e' esatto per ogni misura del regolamento`() {
        listOf(5, 10, 15, 20, 25, 30, 40, 50, 60, 90, 100, 120, 150, 300).forEach { feet ->
            val metres = parseMetres(metresFromFeet(feet))
            assertEquals(feet, metres?.let(::feetFromMetres), "$feet piedi non tornano al valore iniziale")
        }
    }

    @Test
    fun `legge i metri con la virgola o con il punto`() {
        assertEquals(5, feetFromMetres(1.5))
        assertEquals(30, feetFromMetres(9.0))
        assertEquals(1.5, parseMetres("1,5"))
        assertEquals(1.5, parseMetres("1.5"))
        assertEquals(9.0, parseMetres(" 9 "))
        // Una misura appena cominciata resta valida e vale la sua parte intera:
        // mentre si digita «1,5» il campo passa da 1 m a 1,5 m senza mai svuotarsi.
        assertEquals(1.0, parseMetres("1,"))
        assertNull(parseMetres(""))
        assertNull(parseMetres(","))
        assertNull(parseMetres("abc"))
    }

    @Test
    fun `sostituisce i piedi con i metri nei testi regolamentari`() {
        assertEquals(
            "Gittata 9/36 m.",
            "Gittata 30/120 piedi.".withMetricDistances(),
        )
        assertEquals("Portata 1,5 m", "Portata 5 ft.".withMetricDistances())
        assertEquals("Raggio 6 m", "Raggio 20 ft".withMetricDistances())
    }

    @Test
    fun `un testo gia' metrico resta com'e'`() {
        val metrico = "Sfera di 6 m di raggio; tiro salvezza su Destrezza."

        assertEquals(metrico, metrico.withMetricDistances())
    }
}
