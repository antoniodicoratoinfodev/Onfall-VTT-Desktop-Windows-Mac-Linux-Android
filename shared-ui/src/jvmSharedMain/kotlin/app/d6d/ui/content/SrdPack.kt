package app.d6d.ui.content

import app.d6d.content.srd521it.Srd521ItContent
import app.d6d.content.srd521it.SrdChoiceResolver
import app.d6d.content.srd521it.SrdRulesetCharacterAdapter
import app.d6d.content.srd521it.toCatalogAbility
import app.d6d.i18n.AppLanguage
import app.d6d.rules.model.RulesetRevision
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

/**
 * Adattatore registrabile fra una revisione universale e il modello guidato.
 * Il profilo SRD è il primo installato; altri profili D&D-like possono essere
 * aggiunti senza introdurre nuovi `when` nei ViewModel o nelle schede.
 */
internal interface RulesCharacterContentAdapter {
    fun supports(revision: RulesetRevision): Boolean
    fun project(revision: RulesetRevision, language: AppLanguage): RulesContentPack
    fun labelsForId(id: String, language: AppLanguage): List<String> = emptyList()
}

internal class RulesContentRegistry(
    adapters: List<RulesCharacterContentAdapter>,
) {
    private val adapters = adapters.toList().also {
        require(it.isNotEmpty()) { "At least one character content adapter is required" }
    }
    private val packs = HashMap<Pair<String, AppLanguage>, RulesContentPack>()
    private val catalogs = HashMap<Pair<String, AppLanguage>, List<CatalogAbility>>()
    private val guidedServices = HashMap<Pair<String, AppLanguage>, GuidedCharacterService>()

    fun pack(revision: RulesetRevision, language: AppLanguage): RulesContentPack = synchronized(this) {
        packs.getOrPut(revision.canonicalHash() to language) {
            adapter(revision).project(revision, language)
        }
    }

    fun catalog(revision: RulesetRevision, language: AppLanguage): List<CatalogAbility> = synchronized(this) {
        catalogs.getOrPut(revision.canonicalHash() to language) {
            val pack = pack(revision, language)
            pack.elements.map { it.toCatalogAbility(pack) }
        }
    }

    fun guided(revision: RulesetRevision, language: AppLanguage): GuidedCharacterService = synchronized(this) {
        guidedServices.getOrPut(revision.canonicalHash() to language) {
            val adapter = adapter(revision)
            GuidedCharacterService(pack(revision, language)) { id ->
                val localizedNames = AppLanguage.entries.mapNotNull { candidate ->
                    runCatching { pack(revision, candidate).element(id)?.name }.getOrNull()
                }
                (localizedNames + adapter.labelsForId(id, language)).distinct()
            }
        }
    }

    private fun adapter(revision: RulesetRevision): RulesCharacterContentAdapter =
        adapters.firstOrNull { it.supports(revision) }
            ?: error("No installed character content adapter supports ${revision.name()}")
}

private object DefaultDndCharacterAdapter : RulesCharacterContentAdapter {
    override fun supports(revision: RulesetRevision): Boolean = true

    override fun project(revision: RulesetRevision, language: AppLanguage): RulesContentPack =
        SrdRulesetCharacterAdapter.project(revision, language)

    override fun labelsForId(id: String, language: AppLanguage): List<String> =
        SrdChoiceResolver.labelsForId(id, language)
}

private val contentRegistry = RulesContentRegistry(listOf(DefaultDndCharacterAdapter))

/** Proiezione eseguibile di una revisione, indicizzata per hash e lingua. */
internal fun rulesPackFor(revision: RulesetRevision, language: AppLanguage): RulesContentPack =
    contentRegistry.pack(revision, language)

internal fun rulesCatalogFor(revision: RulesetRevision, language: AppLanguage): List<CatalogAbility> =
    contentRegistry.catalog(revision, language)

internal fun guidedCharacterServiceFor(
    revision: RulesetRevision,
    language: AppLanguage,
): GuidedCharacterService = contentRegistry.guided(revision, language)
