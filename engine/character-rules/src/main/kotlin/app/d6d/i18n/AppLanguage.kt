package app.d6d.i18n

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Lingue in cui l'applicazione sa parlare.
 *
 * Deliberatamente un enum chiuso e non un `Locale`: ogni lingua qui dentro ha una
 * traduzione completa, e il compilatore non lascia dimenticarne un pezzo. Un
 * `Locale` arbitrario prometterebbe invece qualcosa che non esiste.
 *
 * Vive nel modulo delle regole, non nell'interfaccia, perche' anche gli strati
 * sotto Compose compongono testo da mostrare — la riga dei danni di un'arma, il
 * sottotitolo di uno stat block — e devono poterlo fare nella lingua giusta senza
 * conoscere l'interfaccia. Nessuno strato tiene la lingua in una variabile
 * globale: viene passata dove serve, cosi' ricomporre in Compose resta corretto
 * e i test possono chiedere entrambe le lingue nello stesso processo.
 *
 * Il nome della lingua e' scritto *nella lingua stessa* (endonimo): chi apre le
 * Impostazioni con l'interfaccia in una lingua che non capisce deve comunque
 * riconoscere la propria. «Italiano» e «English», mai «Inglese» e «Italian».
 */
// Serializzabile perche' finisce dentro la scheda salvata: e' la lingua in
// cui ne e' scritto il testo SRD, non una preferenza dell'applicazione.
@Serializable
enum class AppLanguage(
    /** Codice ISO 639-1. E' quello che finisce in `preferences.json`. */
    val tag: String,
    /** Nome della lingua nella lingua stessa. */
    val endonym: String,
) {
    ITALIAN("it", "Italiano"),
    ENGLISH("en", "English"),
    ;

    companion object {

        /**
         * Lingua da usare quando non c'e' ancora una preferenza salvata.
         *
         * Non una costante ma la lingua del sistema: chi installa l'app su una
         * macchina inglese non deve passare dalle Impostazioni per leggere il
         * primo schermo. Tutto cio' che non e' italiano ricade sull'inglese, che
         * e' la lingua del motore e quella franca del gioco da tavolo.
         */
        fun systemDefault(): AppLanguage = fromLocale(Locale.getDefault())

        /** La lingua corrispondente a un `Locale`, con l'inglese come ripiego. */
        fun fromLocale(locale: Locale): AppLanguage =
            entries.firstOrNull { it.tag.equals(locale.language, ignoreCase = true) } ?: ENGLISH

        /**
         * La lingua salvata, o quella di sistema se il nome non significa piu' nulla.
         *
         * Accetta sia il nome dell'enum sia il codice ISO: il file delle preferenze
         * scrive il primo, ma un file corretto a mano con il secondo esprime una
         * richiesta chiara abbastanza da essere onorata.
         */
        fun parseOrSystemDefault(value: String?): AppLanguage {
            if (value.isNullOrBlank()) return systemDefault()
            val trimmed = value.trim()
            return entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                ?: entries.firstOrNull { it.tag.equals(trimmed, ignoreCase = true) }
                ?: systemDefault()
        }
    }
}

/**
 * Sceglie fra due varianti di una stringa.
 *
 * Zucchero minimo ma usatissimo: gli enum del motore portano entrambe le forme
 * come proprieta' costanti, e questo evita di ripetere un `when` a ogni voce.
 */
fun AppLanguage.pick(italian: String, english: String): String =
    when (this) {
        AppLanguage.ITALIAN -> italian
        AppLanguage.ENGLISH -> english
    }
