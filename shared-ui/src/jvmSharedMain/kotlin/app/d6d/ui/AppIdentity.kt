package app.d6d.ui

/**
 * Identita' visibile dell'applicazione, raccolta in un punto solo.
 *
 * Il nome commerciale non e' ancora deciso. Una ricerca in rete non ha trovato
 * software o strumenti da tavolo chiamati "Turnforge", ma una ricerca non e' una
 * verifica di marchio: il paragrafo 17 del documento richiede un controllo legale
 * specifico su branding e attribuzione prima di pubblicare. Finche' quel controllo
 * non c'e', resta il segnaposto.
 *
 * Per adottare un nome basta cambiare [displayName] qui: nessun'altra parte del
 * codice contiene il nome commerciale.
 */
object AppIdentity {

    /** Nome mostrato all'utente. Sostituire con il nome scelto. */
    const val displayName: String = "INSERIRE NOME"

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
