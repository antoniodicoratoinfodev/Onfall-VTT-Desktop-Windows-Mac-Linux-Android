package app.d6d.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.i18n.AppLanguage

/**
 * La lingua viva dell'applicazione.
 *
 * Esiste perche' non tutto il testo nasce dentro una composable. I view model
 * rifiutano un comando, il turno della CPU si racconta, un salvataggio fallisce:
 * sono frasi prodotte fuori da Compose, dove [LocalStrings] non arriva.
 *
 * Il valore e' uno stato di snapshot, non un semplice `var`, e la differenza e'
 * tutto: leggerlo dentro una composizione la iscrive al cambio di lingua, quindi
 * cambiare lingua ridisegna lo schermo senza che nessuno se ne debba occupare.
 * Fuori dalla composizione si legge e basta, e si ottiene il valore corrente.
 *
 * Chi scrive qui e' uno solo: la radice dell'applicazione, che segue le
 * preferenze. Un test puo' fissare la lingua per il proprio caso, e deve farlo
 * esplicitamente invece di ereditare quella della macchina che lo esegue.
 */
object AppLocale {

    /**
     * Il vocabolario in uso.
     *
     * Parte dalla lingua del sistema perche' e' la risposta migliore prima che le
     * preferenze siano state lette da disco: cosi' il primo fotogramma, quello
     * disegnato mentre il file arriva, e' gia' nella lingua probabilmente giusta.
     */
    var current: Strings by mutableStateOf(stringsFor(AppLanguage.systemDefault()))

    /** Scorciatoia per chi deve solo passare la lingua a una funzione del motore. */
    val language: AppLanguage get() = current.language

    /** Cambia lingua. Idempotente: riassegnare la stessa non invalida nulla. */
    fun use(language: AppLanguage) {
        if (current.language != language) current = stringsFor(language)
    }
}

/**
 * Il vocabolario disponibile a tutta la composizione.
 *
 * `compositionLocalOf` e non `staticCompositionLocalOf`: la versione statica
 * ridisegnerebbe l'intero albero a ogni cambio, mentre questa invalida soltanto
 * chi il testo lo legge davvero. Il valore predefinito segue [AppLocale], cosi'
 * un'anteprima o un test che non fornisce nulla non si trova senza parole.
 */
val LocalStrings: ProvidableCompositionLocal<Strings> = compositionLocalOf { AppLocale.current }

/**
 * Il vocabolario, dentro una composable.
 *
 * Si usa come una proprieta': `strings.common.save`. E' il modo normale di
 * raggiungere il testo in questa applicazione.
 */
val strings: Strings
    @Composable
    @ReadOnlyComposable
    get() = LocalStrings.current

/** La lingua in uso, per le funzioni del motore che la vogliono come parametro. */
val currentLanguage: AppLanguage
    @Composable
    @ReadOnlyComposable
    get() = LocalStrings.current.language

/**
 * Un testo che sa dirsi in entrambe le lingue.
 *
 * I view model non conservano frasi gia' fatte ma il modo di ricavarle: un
 * messaggio scritto in italiano resterebbe italiano anche dopo che l'utente ha
 * cambiato lingua, e resterebbe li' finche' qualcosa non lo sostituisce. Cosi'
 * invece segue la lingua come tutto il resto dello schermo.
 */
fun interface LocalizedText {
    fun resolve(strings: Strings): String
}

/** Testo che non si traduce: un nome proprio, un percorso, un errore del sistema. */
fun literalText(value: String): LocalizedText = LocalizedText { value }
