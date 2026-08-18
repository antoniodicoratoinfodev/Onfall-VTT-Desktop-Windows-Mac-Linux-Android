package app.d6d.ui.content

import app.d6d.content.srd521it.Srd521ItContent
import app.d6d.content.srd521it.SrdBackgrounds
import app.d6d.content.srd521it.SrdStartingEquipment
import app.d6d.content.srd521it.SrdWeapons
import app.d6d.content.srd521it.srdToolAndLanguageNames
import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.WeaponDefinition
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.Spellcasting
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.weaponNote

/**
 * Riporta nella lingua richiesta il testo SRD gia' scritto dentro una scheda.
 *
 * La procedura guidata non lascia nella scheda solo identificativi: scrive anche
 * il *testo* di classe, sottoclasse, background, armi, strumenti, lingue,
 * equipaggiamento. Sono campi che l'utente vede e puo' modificare. Cambiare
 * lingua senza toccarli lascia un personaggio con le classi in inglese e tutto
 * il resto in italiano.
 *
 * Una regola sola governa ogni campo: **si sostituisce un valore soltanto quando
 * coincide esattamente con quello canonico nella lingua di partenza.** Se
 * l'utente ha scritto «Criminale in fuga» al posto di «Criminale», o ha chiamato
 * la propria spada «Mordilupo», quel testo e' suo e attraversa il cambio di
 * lingua intatto. Riderivare i campi dagli identificativi sarebbe piu' semplice
 * e cancellerebbe il lavoro di chi gioca: una traduzione mancata e' un fastidio,
 * una riga riscritta e' un danno.
 *
 * La lingua di partenza si legge da [CharacterSheet.contentLanguage], perche' e'
 * la scheda l'unica a saperlo con certezza. La funzione e' pura: chi la chiama
 * decide se e quando salvare.
 */
internal fun CharacterSheet.retranslatedTo(target: AppLanguage): CharacterSheet {
    if (target == contentLanguage) return this
    // La ricetta da cui la scheda e' nata, quando c'e', e' semplicemente
    // un'altra sorgente di corrispondenze: nome, specie, background, narrativa
    // e le singole voci di dotazione e lingue. Cosi' i campi del modello
    // seguono la stessa regola di tutto il resto — si sostituisce solo cio' che
    // combacia — senza un secondo meccanismo che si comporta in modo diverso.
    val terms = SrdTerms.between(contentLanguage, target)
        .plus(templatePairs(id, contentLanguage, target))

    return copy(
        contentLanguage = target,
        characterName = terms.translateWhole(characterName),
        className = terms.translateLevelled(className),
        subclass = terms.translateLevelled(subclass),
        background = terms.translateWhole(background),
        weaponProficiencies = terms.translateList(weaponProficiencies),
        weapons = weapons.map(terms::regenerate),
        toolProficiencies = terms.translateList(toolProficiencies),
        languages = terms.translateList(languages),
        equipment = terms.translateList(equipment),
        classFeatures = terms.translateList(classFeatures),
        feats = terms.translateList(feats),
        // La narrativa dei personaggi modello: specie, allineamento, aspetto e
        // storia. Non e' contenuto SRD e non ha identificativi, ma la ricetta da
        // cui la scheda e' nata esiste in entrambe le lingue: cio' che coincide
        // ancora con la ricetta di partenza e' nostro e segue la lingua, cio'
        // che non coincide piu' l'ha riscritto chi gioca e resta suo.
        species = terms.translateWhole(species),
        alignment = terms.translateWhole(alignment),
        appearance = terms.translateWhole(appearance),
        backstory = terms.translateWhole(backstory),
        // I campi generati annidati non si traducono: si **rigenerano** dagli
        // identificativi con il pacchetto di destinazione. E' possibile perche'
        // qui un identificativo c'e' — il pozzo di risorse ne porta uno, e un
        // incantesimo si ritrova nel pacchetto dal nome canonico — e da' anche
        // i dati che non sono nomi: tempo di lancio, gittata, concentrazione.
        spellcasting = spellcasting?.let { terms.regenerate(it) },
        progression = progression.copy(
            resourcePools = progression.resourcePools.map { pool ->
                pool.copy(name = terms.resourceName(pool.resourceId) ?: pool.name)
            },
            // La sorgente di un effetto e' il nome del privilegio che lo
            // concede, e la scheda la mostra: lasciarla indietro faceva leggere
            // «Movimento senza armatura» sotto una Classe Armatura inglese.
            effects = progression.effects.map { effect ->
                effect.copy(source = terms.translateWhole(effect.source))
            },
        ),
    )
}

/**
 * Le corrispondenze fra i termini SRD di due edizioni.
 *
 * Si ricavano dal pacchetto stesso — stessa voce, stesso identificativo, due
 * lingue — invece che da una tavola scritta a mano, che andrebbe tenuta
 * allineata al pacchetto e prima o poi non lo sarebbe piu'.
 */
private class SrdTerms(
    private val entries: Map<String, String>,
    /** Elementi del pacchetto di destinazione, per identificativo. */
    private val targetById: Map<String, RuleElementDefinition>,
    /** Identificativo dell'elemento a partire dal nome nella lingua di origine. */
    private val sourceIdByName: Map<String, String>,
    private val targetResourceNames: Map<String, String>,
    /** L'arma corrispondente nel pacchetto di destinazione, dal nome di origine. */
    private val targetWeaponBySourceName: Map<String, WeaponDefinition>,
    private val target: AppLanguage,
) {
    fun resourceName(resourceId: String): String? = targetResourceNames[resourceId]

    /** Le stesse corrispondenze piu' quelle di una ricetta. */
    fun plus(extra: Map<String, String>): SrdTerms = if (extra.isEmpty()) this else SrdTerms(
        entries = entries + extra.mapKeys { it.key.lowercase() },
        targetById = targetById,
        sourceIdByName = sourceIdByName,
        targetResourceNames = targetResourceNames,
        targetWeaponBySourceName = targetWeaponBySourceName,
        target = target,
    )

    /**
     * Riscrive un'arma della scheda con la voce corrispondente del pacchetto di
     * destinazione.
     *
     * La nota non si traduce: si **rigenera**. E' testo composto — «Padronanza:
     * Fiaccare · a due mani 1d8 · gittata 6/18 m» — dove cambia il nome della
     * padronanza, cambiano le parole che li stanno intorno e cambia perfino
     * l'unita' di misura, perche' l'italiano stampa i metri. Nessuna sostituzione
     * di voci canoniche puo' arrivarci: la riga intera non e' una voce canonica,
     * e restava in italiano dentro una scheda inglese.
     *
     * Cio' che fra le armi non e' un'arma — un incantesimo diventato capacita'
     * lanciabile, un privilegio che tira per colpire — non ha nota generata da
     * riscrivere e segue la regola di sempre: si sostituisce cio' che coincide
     * con una voce canonica. Come un'arma che l'utente ha rinominata, che resta
     * sua, nota compresa.
     */
    fun regenerate(entry: WeaponEntry): WeaponEntry {
        val definition = targetWeaponBySourceName[entry.name.trim().lowercase()]
            ?: return entry.copy(
                name = translateWhole(entry.name),
                note = translateList(entry.note),
            )
        return entry.copy(name = definition.name, note = definition.weaponNote(target))
    }

    /**
     * Riscrive gli incantesimi della scheda con la voce corrispondente del
     * pacchetto di destinazione.
     *
     * Un incantesimo che non si ritrova nel pacchetto — perche' l'utente lo ha
     * scritto a mano — resta esattamente com'e', nota compresa.
     */
    fun regenerate(spellcasting: Spellcasting): Spellcasting = spellcasting.copy(
        spells = spellcasting.spells.map { entry ->
            val element = sourceIdByName[entry.name.trim().lowercase()]?.let(targetById::get)
            val spell = element?.spell
            if (element == null || spell == null) {
                entry.copy(note = translateWhole(entry.note))
            } else {
                entry.copy(
                    name = element.name,
                    castingTime = spell.castingTime,
                    range = spell.range,
                    concentration = spell.concentration,
                    ritual = spell.ritual,
                    note = translateWhole(entry.note),
                )
            }
        },
    )


    /** Sostituisce il valore solo se e' per intero una voce canonica. */
    fun translateWhole(value: String): String =
        if (value.isBlank()) value else entries[value.trim().lowercase()] ?: value

    /**
     * Traduce le voci di un elenco separato da virgole, punti e virgola o a capo.
     *
     * Si sostituiscono le *voci*, non si spezza l'elenco: virgole, spaziatura e
     * a capo restano dove sono, perche' questo testo lo rilegge una persona.
     */
    fun translateList(value: String): String {
        if (value.isBlank() || entries.isEmpty()) return value
        return ENTRY.replace(value) { match ->
            val trimmed = match.value.trim()
            val translated = entries[trimmed.lowercase()]
            if (translated == null) match.value else match.value.replaceFirst(trimmed, translated)
        }
    }

    /**
     * Traduce «Ladro 3 / Mago 1», dove ogni segmento e' un nome col proprio livello.
     *
     * Vale la stessa regola: il segmento cambia solo se il nome combacia con una
     * voce canonica. «Ladro dei vicoli 3» resta com'e'.
     */
    fun translateLevelled(value: String): String {
        if (value.isBlank()) return value
        return value.split(" / ").joinToString(" / ") { segment ->
            val level = segment.substringAfterLast(' ', "")
            if (level.toIntOrNull() == null) {
                translateWhole(segment)
            } else {
                val name = segment.substringBeforeLast(' ')
                entries[name.trim().lowercase()]?.let { "$it $level" } ?: segment
            }
        }
    }

    companion object {
        private val ENTRY = Regex("[^,;\n]+")

        private val cache = HashMap<Pair<AppLanguage, AppLanguage>, SrdTerms>()

        fun between(source: AppLanguage, target: AppLanguage): SrdTerms =
            synchronized(cache) { cache.getOrPut(source to target) { build(source, target) } }

        private fun build(source: AppLanguage, target: AppLanguage): SrdTerms {
            val entries = HashMap<String, String>()

            fun pair(sourceName: String, targetName: String) {
                if (sourceName.isNotBlank() && targetName.isNotBlank() && sourceName != targetName) {
                    entries[sourceName.lowercase()] = targetName
                }
            }

            /** Accoppia per identificativo, che le due edizioni condividono. */
            fun pairById(from: List<Pair<String, String>>, to: List<Pair<String, String>>) {
                val byId = to.toMap()
                from.forEach { (id, name) -> byId[id]?.let { pair(name, it) } }
            }

            val fromPack = Srd521ItContent.packFor(source)
            val toPack = Srd521ItContent.packFor(target)

            // Incantesimi, talenti, privilegi e sottoclassi in un colpo solo:
            // tutto cio' che il pacchetto conosce per identificativo. Copre anche
            // i nomi che finiscono fra le armi della scheda quando un incantesimo
            // diventa una capacita' lanciabile.
            pairById(
                fromPack.elements.map { it.id to it.name },
                toPack.elements.map { it.id to it.name },
            )
            pairById(
                fromPack.classes.map { it.id.name to it.name },
                toPack.classes.map { it.id.name to it.name },
            )
            // Nomi delle risorse di classe: «Ira», «Punti stregoneria».
            pairById(
                fromPack.classes.flatMap { definition -> definition.resources.map { it.id to it.name } },
                toPack.classes.flatMap { definition -> definition.resources.map { it.id to it.name } },
            )
            // Addestramento nelle armi: «Armi semplici e da guerra».
            pairById(
                fromPack.classes.map { it.id.name to it.weaponTraining },
                toPack.classes.map { it.id.name to it.weaponTraining },
            )
            pairById(
                SrdWeapons.all(source).map { it.id to it.name },
                SrdWeapons.all(target).map { it.id to it.name },
            )
            pairById(
                SrdBackgrounds.all(source).map { it.id to it.name },
                SrdBackgrounds.all(target).map { it.id to it.name },
            )
            // La chiave e' la posizione nella dotazione, non il nome: un nome
            // nella chiave la renderebbe diversa fra le due lingue.
            pairById(equipmentItems(source), equipmentItems(target))

            srdToolAndLanguageNames(source, target).forEach { (from, to) -> pair(from, to) }

            // Le tre note che la procedura guidata scrive sotto un incantesimo.
            // Sono generate, non scritte dall'utente, e vivono nel motore: qui
            // servono le coppie, che sono poche e stabili.
            pair(
                if (source == AppLanguage.ITALIAN) "Iniziato alla magia" else "Magic Initiate",
                if (target == AppLanguage.ITALIAN) "Iniziato alla magia" else "Magic Initiate",
            )
            pair(
                if (source == AppLanguage.ITALIAN) "Sempre preparato" else "Always prepared",
                if (target == AppLanguage.ITALIAN) "Sempre preparato" else "Always prepared",
            )
            pair(
                if (source == AppLanguage.ITALIAN) "Nel libro degli incantesimi" else "In the spellbook",
                if (target == AppLanguage.ITALIAN) "Nel libro degli incantesimi" else "In the spellbook",
            )

            val targetWeaponsById = SrdWeapons.all(target).associateBy { it.id }
            return SrdTerms(
                entries = entries,
                targetById = toPack.elements.associateBy { it.id },
                sourceIdByName = fromPack.elements.associate { it.name.lowercase() to it.id },
                targetResourceNames = toPack.classes
                    .flatMap { definition -> definition.resources }
                    .associate { it.id to it.name },
                targetWeaponBySourceName = SrdWeapons.all(source).mapNotNull { weapon ->
                    targetWeaponsById[weapon.id]?.let { weapon.name.lowercase() to it }
                }.toMap(),
                target = target,
            )
        }

        private fun equipmentItems(language: AppLanguage): List<Pair<String, String>> =
            SrdStartingEquipment.all(language).flatMap { pack ->
                pack.itemNames.mapIndexed { index, item -> "${pack.id}#$index" to item }
            }
    }
}

/**
 * Le corrispondenze che una ricetta di personaggio modello porta con se'.
 *
 * Nome, specie, background, allineamento, aspetto e storia si accoppiano per
 * intero. Dotazione e lingue no: il servizio guidato ci *aggiunge* le voci
 * concesse dall'SRD, quindi il valore salvato non coincide piu' con quello
 * della ricetta. Si accoppiano allora le singole voci, che restano riconoscibili
 * dentro l'elenco accresciuto.
 *
 * Un personaggio creato a mano non ha ricetta e non riceve nessuna coppia.
 */
private fun templatePairs(
    id: String,
    source: AppLanguage,
    target: AppLanguage,
): Map<String, String> {
    val from = TemplateParties.of(source).plansById[id] ?: return emptyMap()
    val to = TemplateParties.of(target).plansById[id] ?: return emptyMap()
    return buildMap {
        fun whole(left: String, right: String) {
            if (left.isNotBlank() && right.isNotBlank() && left != right) put(left, right)
        }
        whole(from.name, to.name)
        whole(from.species, to.species)
        whole(from.background, to.background)
        whole(from.alignment, to.alignment)
        whole(from.appearance, to.appearance)
        whole(from.backstory, to.backstory)

        fun entries(left: String, right: String) {
            val a = left.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty)
            val b = right.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty)
            // Solo se le due liste hanno la stessa forma: se una ricetta cambia
            // il numero di voci fra le lingue, accoppiarle per posizione
            // inventerebbe corrispondenze.
            if (a.size == b.size) a.zip(b).forEach { (x, y) -> whole(x, y) }
        }
        entries(from.equipment, to.equipment)
        entries(from.languages, to.languages)
    }
}
