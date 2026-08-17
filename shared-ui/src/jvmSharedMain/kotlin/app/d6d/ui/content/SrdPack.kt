package app.d6d.ui.content

import app.d6d.content.srd521it.Srd521ItContent
import app.d6d.content.srd521it.SrdChoiceResolver
import app.d6d.i18n.AppLanguage
import app.d6d.sheet.GuidedCharacterService
import app.d6d.rules.character.RulesContentPack
import app.d6d.sheet.CatalogAbility
import app.d6d.ui.i18n.AppLocale

/**
 * Il pacchetto SRD nella lingua corrente.
 *
 * Sono accessori con `get()` e non valori: il pacchetto deve seguire la lingua,
 * e una proprieta' inizializzata una volta resterebbe quella scelta all'avvio.
 * La lettura di [AppLocale] e' uno stato snapshot, quindi chi legge dentro una
 * composizione viene ridisegnato quando la lingua cambia.
 *
 * Cambiare lingua **non** tocca i personaggi salvati: le due edizioni coniano
 * gli stessi identificativi, e le schede si riagganciano a nomi tradotti senza
 * perdere una sola scelta. Lo sorveglia `SrdEnglishPackTest`.
 */
internal fun srdPackFor(language: AppLanguage): RulesContentPack =
    Srd521ItContent.packFor(language)

internal fun srdCatalogFor(language: AppLanguage): List<CatalogAbility> =
    Srd521ItContent.catalogFor(language)

internal val srdPack: RulesContentPack get() = srdPackFor(AppLocale.language)

internal val srdCatalog: List<CatalogAbility> get() = srdCatalogFor(AppLocale.language)

/**
 * Il servizio di creazione guidata, uno per lingua.
 *
 * Costruirlo e' caro — indicizza l'intero pacchetto — quindi si conserva; ma si
 * conserva *per lingua*, perche' un servizio costruito sull'edizione italiana
 * proporrebbe scelte italiane dopo il passaggio all'inglese.
 */
internal fun guidedCharacterServiceFor(language: AppLanguage): GuidedCharacterService =
    synchronized(services) {
        services.getOrPut(language) {
            GuidedCharacterService(srdPackFor(language)) { id ->
                SrdChoiceResolver.labelsForId(id, language)
            }
        }
    }

internal val guidedCharacterService: GuidedCharacterService
    get() = guidedCharacterServiceFor(AppLocale.language)

private val services = HashMap<AppLanguage, GuidedCharacterService>()
