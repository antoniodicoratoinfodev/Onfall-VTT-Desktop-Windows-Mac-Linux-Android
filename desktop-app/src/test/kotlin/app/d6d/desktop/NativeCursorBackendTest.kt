package app.d6d.desktop

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Chi disegna il puntatore si decide dal sistema operativo.
 *
 * Il disegno in scena e' il ripiego per X11/Xwayland, dove il cursore nativo ad
 * alta risoluzione non arriva a destinazione. Finirci per sbaglio costa caro:
 * sul ramo del ripiego il cursore nativo viene reso trasparente, e su macOS,
 * dove il sistema ripristina la freccia rientrando nella finestra, si finiva per
 * vederne due.
 *
 * La regressione era proprio questa: il criterio era il numero di colori del
 * toolkit, e su macOS `maximumCursorColors` vale **0** — che non significa «non
 * so disegnare un cursore» ma «non lo dichiaro».
 */
class NativeCursorBackendTest {

    @Test
    fun `su macOS ci si fida del cursore nativo`() {
        assertTrue(nativeCursorIsTrustworthy("Mac OS X"))
    }

    @Test
    fun `su Windows ci si fida del cursore nativo`() {
        assertTrue(nativeCursorIsTrustworthy("Windows 11"))
    }

    @Test
    fun `su Linux si passa al disegno in scena`() {
        assertFalse(nativeCursorIsTrustworthy("Linux"))
    }

    /** Un sistema che non si riconosce prende la strada prudente, cioe' il ripiego. */
    @Test
    fun `un sistema sconosciuto usa il ripiego`() {
        assertFalse(nativeCursorIsTrustworthy(""))
        assertFalse(nativeCursorIsTrustworthy("FreeBSD"))
    }
}
