package app.d6d.ui

/**
 * Identita' visibile dell'applicazione, raccolta in un punto solo.
 *
 * Il nome di prodotto usato dal progetto e' TurnForge. Prima di una pubblicazione
 * commerciale resta comunque necessario il controllo legale su marchio e
 * attribuzioni richiesto dal documento di progetto.
 *
 * La shell condivisa legge il nome da qui; etichetta Android e nome dei pacchetti
 * nativi lo rispecchiano nelle rispettive configurazioni di piattaforma.
 */
object AppIdentity {

    /** Nome mostrato all'utente. */
    const val displayName: String = "TurnForge"

    /**
     * Dicitura di compatibilita'.
     *
     * Il documento impone la formula prudente: si dichiara la compatibilita', non
     * l'approvazione ufficiale, e non si usano loghi o marchi.
     */
    const val compatibilityLine: String = "Compatibile con 5.5e / SRD"

    /** Titolo completo per finestre e intestazioni. */
    val windowTitle: String get() = "$displayName — $compatibilityLine"
}
