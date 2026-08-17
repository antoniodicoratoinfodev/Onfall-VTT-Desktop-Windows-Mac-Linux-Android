package app.d6d.ui.i18n

import app.d6d.i18n.AppLanguage
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Fissa la lingua prima di ogni test.
 *
 * Senza questo, [AppLocale] partirebbe dalla lingua del sistema — cioe' da quella
 * della macchina che esegue la suite — e le asserzioni sul testo passerebbero sul
 * portatile di chi l'ha scritta e fallirebbero in CI. La lingua di un test e' un
 * dato del test, non dell'ambiente.
 *
 * L'italiano e' la scelta predefinita perche' e' quella che le asserzioni gia'
 * scritte danno per buona. Un test che voglia l'inglese chiama [AppLocale.use] nel
 * proprio corpo: questa estensione lo riporta all'italiano prima del successivo,
 * quindi non c'e' ordine di esecuzione che possa sporcare un altro caso.
 *
 * Viene registrata a livello di piattaforma (vedi `junit-platform.properties` e
 * `META-INF/services`) invece che con `@ExtendWith` classe per classe: cosi' vale
 * anche per i test che verranno, senza che nessuno debba ricordarsene.
 */
class PinnedLanguageExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        AppLocale.use(AppLanguage.ITALIAN)
    }
}
