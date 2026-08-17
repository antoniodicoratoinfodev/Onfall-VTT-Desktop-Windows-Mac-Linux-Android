package app.d6d.ui.content

import app.d6d.content.srd521it.Srd521ItContent
import app.d6d.content.srd521it.SrdBackgrounds
import app.d6d.content.srd521it.SrdStartingEquipment
import app.d6d.content.srd521it.SrdWeapons
import app.d6d.content.srd521it.srdToolAndLanguageNames
import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.Spellcasting

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
    val terms = SrdTerms.between(contentLanguage, target)
    val narrative = TemplateNarrative.between(id, contentLanguage, target)

    return copy(
        contentLanguage = target,
        className = terms.translateLevelled(className),
        subclass = terms.translateLevelled(subclass),
        background = terms.translateWhole(background),
        weaponProficiencies = terms.translateList(weaponProficiencies),
        weapons = weapons.map { entry ->
            entry.copy(
                name = terms.translateWhole(entry.name),
                note = terms.translateList(entry.note),
            )
        },
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
        species = narrative.pick(species) { it.species },
        alignment = narrative.pick(alignment) { it.alignment },
        appearance = narrative.pick(appearance) { it.appearance },
        backstory = narrative.pick(backstory) { it.backstory },
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
) {
    fun resourceName(resourceId: String): String? = targetResourceNames[resourceId]

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

            return SrdTerms(
                entries = entries,
                targetById = toPack.elements.associateBy { it.id },
                sourceIdByName = fromPack.elements.associate { it.name.lowercase() to it.id },
                targetResourceNames = toPack.classes
                    .flatMap { definition -> definition.resources }
                    .associate { it.id to it.name },
            )
        }

        private fun equipmentItems(language: AppLanguage): List<Pair<String, String>> =
            SrdStartingEquipment.all(language).flatMap { pack ->
                pack.itemNames.mapIndexed { index, item -> "${pack.id}#$index" to item }
            }
    }
}

/**
 * I campi narrativi di un personaggio modello, nelle due lingue.
 *
 * Un personaggio creato a mano non ha ricetta, e allora nessun campo si tocca.
 */
private class TemplateNarrative(
    private val source: TemplateCharacterPlan?,
    private val target: TemplateCharacterPlan?,
) {
    /** Sostituisce solo se il valore e' ancora quello della ricetta di partenza. */
    fun pick(current: String, field: (TemplateCharacterPlan) -> String): String {
        val canonical = source?.let(field) ?: return current
        val translated = target?.let(field) ?: return current
        return if (current.trim() == canonical.trim()) translated else current
    }

    companion object {
        fun between(id: String, source: AppLanguage, target: AppLanguage) = TemplateNarrative(
            source = TemplateParties.of(source).plansById[id],
            target = TemplateParties.of(target).plansById[id],
        )
    }
}
