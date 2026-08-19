package app.d6d.ui.content

import app.d6d.content.srd521it.SrdWeapons
import app.d6d.i18n.AppLanguage

/**
 * I nomi con cui una partita gia' avviata si mostra nella lingua corrente.
 *
 * Un combattente entra nella sessione come *copia*, e il nome ci arriva scritto
 * una volta per tutte. Il motore fa bene a non ridiscuterlo — audit,
 * annullamento e salvataggi si reggono su quel dato — ma cio' che il tavolo
 * *legge* non ha motivo di restare nella lingua in cui la partita e' nata.
 *
 * Vale la stessa regola che governa schede e bestiario: **si sostituisce
 * soltanto cio' di cui si conosce la provenienza.** Se il nome memorizzato
 * coincide con quello canonico di una delle due edizioni, se ne mostra la
 * versione richiesta; altrimenti quel nome se l'e' scritto chi gioca, e
 * attraversa il cambio di lingua intatto. Una traduzione mancata e' un
 * fastidio, un nome riscritto e' un danno.
 *
 * La lingua di partenza non serve saperla: si confronta con **tutte** le
 * edizioni e vince quella che combacia. Cosi' anche un salvataggio vecchio, che
 * non porta scritto in che lingua e' nato, si allinea senza migrazioni.
 */
internal object SessionNaming {

    /**
     * Le copie multiple si chiamano «Cane di palude 1», «Cane di palude 2»: il
     * numero lo aggiunge la procedura di nuova partita, non il contenuto, quindi
     * va tolto prima di cercare e rimesso dopo.
     */
    private val ORDINAL = Regex("^(.*?)( \\d+)$")

    /** Nome del combattente nella lingua richiesta, o quello che aveva. */
    fun combatantName(definitionId: String, storedName: String, language: AppLanguage): String =
        translate(storedName, language) { lang -> creatureNames(lang)[definitionId] }

    /**
     * Nome della capacita' nella lingua richiesta, o quello che aveva.
     *
     * Le armi non si trovano per identificativo: dentro una scheda diventano
     * capacita' con un id locale — `pg-aelis-arma-0` — che non dice nulla su
     * quale arma sia. Restano pero' il *nome* canonico della propria edizione,
     * e su quello si puo' decidere, che e' esattamente il criterio con cui
     * [SheetTranslation] tratta le armi di una scheda.
     */
    fun abilityName(abilityId: String, storedName: String, language: AppLanguage): String {
        val byId = translate(storedName, language) { lang -> abilityNames(lang)[abilityId] }
        if (byId != storedName) return byId
        return translate(storedName, language) { lang ->
            weaponSlug(storedName)?.let { slug -> weaponNames(lang)[slug] }
        }
    }

    /**
     * Il testo di regole nella lingua richiesta, o quello che aveva.
     *
     * Vale la stessa cautela dei nomi: si sostituisce solo quando quello
     * memorizzato e' ancora, parola per parola, il testo canonico di
     * un'edizione. Chi ha riscritto una regola a modo suo se la tiene.
     */
    fun abilityRulesText(abilityId: String, storedText: String, language: AppLanguage): String {
        if (storedText.isBlank()) return storedText
        val target = abilityRules(language)[abilityId] ?: return storedText
        if (storedText == target) return storedText
        val isCanonical = AppLanguage.entries.any { abilityRules(it)[abilityId] == storedText }
        return if (isCanonical) target else storedText
    }

    /**
     * Il cuore della regola, scritto una volta sola.
     *
     * Si stacca l'eventuale ordinale, si cerca il nome canonico in ogni lingua e
     * si sostituisce **solo** se quello memorizzato ne e' uno. Un nome che non
     * combacia con nessuna edizione e' dell'utente e torna indietro identico,
     * ordinale compreso.
     */
    private inline fun translate(
        storedName: String,
        language: AppLanguage,
        canonical: (AppLanguage) -> String?,
    ): String {
        val match = ORDINAL.find(storedName)
        val base = match?.groupValues?.get(1) ?: storedName
        val ordinal = match?.groupValues?.get(2).orEmpty()

        val target = canonical(language) ?: return storedName
        if (base == target) return storedName
        val isCanonical = AppLanguage.entries.any { canonical(it) == base }
        return if (isCanonical) target + ordinal else storedName
    }

    // Gli indici si costruiscono una volta per lingua: sono elenchi statici, ma
    // scorrerli a ogni combattente di ogni ridisegno sarebbe uno spreco.
    private val creaturesByLanguage = HashMap<AppLanguage, Map<String, String>>()
    private val abilitiesByLanguage = HashMap<AppLanguage, Map<String, String>>()
    private val abilityRulesByLanguage = HashMap<AppLanguage, Map<String, String>>()
    private val weaponsByLanguage = HashMap<AppLanguage, Map<String, String>>()
    private val weaponSlugsByName = HashMap<String, String>()

    private fun abilityRules(language: AppLanguage): Map<String, String> =
        synchronized(abilityRulesByLanguage) {
            abilityRulesByLanguage.getOrPut(language) {
                srdCatalogFor(language)
                    .filter { it.rulesText.isNotBlank() }
                    .associate { it.id to it.rulesText }
            }
        }

    /** Slug dell'arma -> nome, per lingua. */
    private fun weaponNames(language: AppLanguage): Map<String, String> =
        synchronized(weaponsByLanguage) {
            weaponsByLanguage.getOrPut(language) {
                SrdWeapons.all(language).associate { it.id.substringAfterLast(':') to it.name }
            }
        }

    /** Nome dell'arma in qualunque edizione -> slug, per riconoscerla. */
    private fun weaponSlug(name: String): String? = synchronized(weaponSlugsByName) {
        if (weaponSlugsByName.isEmpty()) {
            AppLanguage.entries.forEach { language ->
                SrdWeapons.all(language).forEach { weapon ->
                    weaponSlugsByName[weapon.name] = weapon.id.substringAfterLast(':')
                }
            }
        }
        weaponSlugsByName[name]
    }

    /**
     * Creature del bestiario e personaggi delle partite incluse stanno nello
     * stesso indice: dal punto di vista di un combattente sono la stessa cosa,
     * una definizione con un identificativo stabile e un nome per lingua.
     */
    private fun creatureNames(language: AppLanguage): Map<String, String> =
        synchronized(creaturesByLanguage) {
            creaturesByLanguage.getOrPut(language) {
                buildMap {
                    TemplateBestiary.of(language).all.forEach { put(it.id, it.name) }
                    SessionTemplates.of(language).all.forEach { putAll(it.partyNames) }
                }
            }
        }

    /**
     * Il catalogo non basta: le armi di una scheda entrano fra le capacita' del
     * combattente ma vivono in un elenco proprio, con lo stesso genere di
     * identificativo stabile. Senza di loro si giocava in inglese impugnando un
     * «Arco lungo».
     */
    private fun abilityNames(language: AppLanguage): Map<String, String> =
        synchronized(abilitiesByLanguage) {
            abilitiesByLanguage.getOrPut(language) {
                buildMap {
                    srdCatalogFor(language)
                        .filter { it.name.isNotBlank() }
                        .forEach { put(it.id, it.name) }
                    SrdWeapons.all(language).forEach { put(it.id, it.name) }
                }
            }
        }
}
