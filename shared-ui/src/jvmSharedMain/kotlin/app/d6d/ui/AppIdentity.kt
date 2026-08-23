package app.d6d.ui

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick

/**
 * Identita' visibile dell'applicazione, raccolta in un punto solo.
 *
 * Il nome di prodotto usato dal progetto e' Onfall. Prima di una pubblicazione
 * commerciale resta comunque necessario il controllo legale su marchio e
 * attribuzioni richiesto dal documento di progetto.
 *
 * La shell condivisa legge il nome da qui; etichetta Android e nome dei pacchetti
 * nativi lo rispecchiano nelle rispettive configurazioni di piattaforma.
 */
object AppIdentity {

    /** Nome mostrato all'utente. */
    const val displayName: String = "Onfall"

    /**
     * Versione dell'applicazione.
     *
     * Il numero non e' scritto qui: arriva da `onfall.version` in
     * `gradle.properties` attraverso la costante generata [BuildInfo], la stessa
     * che marchia i pacchetti desktop e l'APK. Un solo posto da aggiornare, e
     * nessun modo di sbagliarne uno.
     */
    val version: String = BuildInfo.VERSION

    /**
     * Dicitura di compatibilita'.
     *
     * Il documento impone la formula prudente: si dichiara la compatibilita', non
     * l'approvazione ufficiale, e non si usano loghi o marchi.
     */
    fun compatibilityLine(language: AppLanguage): String =
        language.pick("Compatibile con 5.5e / SRD", "Compatible with 5.5e / SRD")

    /**
     * Titolo completo per finestre e intestazioni.
     *
     * La finestra nasce prima della composizione, quindi il titolo si fissa con la
     * lingua di allora. Cambiarla ridisegna tutto lo schermo ma non ribattezza la
     * finestra: e' l'unico testo dell'applicazione che aspetta il prossimo avvio, e
     * vale la pena saperlo invece di scoprirlo.
     */
    fun windowTitle(language: AppLanguage): String =
        "$displayName $version — ${compatibilityLine(language)}"
}
