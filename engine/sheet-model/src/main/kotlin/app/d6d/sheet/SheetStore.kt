package app.d6d.sheet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Contenuto salvato: schede, stat block e catalogo delle capacità riusabili. */
@Serializable
data class SheetLibrary(
    val schemaVersion: Int = SCHEMA_VERSION,
    val characters: List<CharacterSheet> = emptyList(),
    val monsters: List<MonsterStatBlock> = emptyList(),
    val abilities: List<CatalogAbility> = defaultAbilityCatalog(),
    /**
     * Capacità del pacchetto SRD che questo tavolo ha riclassificato.
     *
     * Il pacchetto resta intatto e in sola lettura: qui si annota soltanto se una
     * sua voce, in questa installazione, valga come tratto permanente o come
     * capacità da spendere nel turno. Assente significa "come dice il pacchetto".
     */
    val passiveOverrides: Map<String, Boolean> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 6
    }
}

/**
 * Archivio locale delle schede.
 *
 * Scrive in modo atomico — file temporaneo e sostituzione — cosi' un'interruzione
 * non lascia mai un archivio troncato, e tiene una copia della versione precedente.
 * Stesso principio dello store del catalogo gia' presente nel motore.
 */
class SheetStore(private val file: Path) {

    private val json = Json {
        prettyPrint = true
        // Un campo aggiunto in futuro non deve impedire di leggere un file vecchio.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exists(): Boolean = Files.exists(file)

    fun load(): SheetLibrary {
        if (!Files.exists(file)) return SheetLibrary()
        val text = Files.readString(file)
        if (text.isBlank()) return SheetLibrary()
        val storedVersion = json.parseToJsonElement(text)
            .jsonObject["schemaVersion"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: 1
        require(storedVersion <= SheetLibrary.SCHEMA_VERSION) {
            "Il file usa lo schema $storedVersion, ma questa versione dell'app supporta " +
                "fino allo schema ${SheetLibrary.SCHEMA_VERSION}. Aggiorna l'app prima di salvarlo."
        }
        val decoded = json.decodeFromString(SheetLibrary.serializer(), text)
            .copy(schemaVersion = storedVersion)
        return if (storedVersion < SheetLibrary.SCHEMA_VERSION) {
            migrate(decoded)
        } else {
            decoded
        }
    }

    fun save(library: SheetLibrary) {
        file.parent?.let { Files.createDirectories(it) }

        if (Files.exists(file)) {
            val backup = file.resolveSibling("${file.fileName}.bak")
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
        }

        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(SheetLibrary.serializer(), library))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Il vecchio pulsante rapido inseriva Palla di Fuoco come una normale arma.
     * La migrazione riconosce soltanto quel preset completo e lo sostituisce col
     * riferimento alla nuova voce di catalogo, senza toccare capacità personalizzate
     * che condividano solo il nome.
     */
    private fun migrate(library: SheetLibrary): SheetLibrary {
        val defaults = defaultAbilityCatalog()
        // Migrazione additiva: le voci con lo stesso ID restano quelle dell'utente;
        // vengono inserite soltanto le nuove capacità iniziali che ancora mancano.
        val abilities = library.abilities +
            defaults.filter { builtIn -> library.abilities.none { it.id == builtIn.id } }

        // Dalla versione 4 la CA puo' essere calcolata. I nuovi campi hanno come
        // predefinito MANUAL_TOTAL: una scheda precedente conserva quindi il suo
        // identico valore finale finche' l'utente non sceglie un metodo guidato.
        val characters = if (library.schemaVersion < 2) {
            val fireball = abilities.first { it.id == "inc-palla-di-fuoco" }
            library.characters.map { sheet ->
                val presetRows = sheet.weapons.filter { it.matches(fireball) }
                if (presetRows.isEmpty()) {
                    sheet
                } else {
                    sheet.copy(
                        weapons = sheet.weapons.filterNot { it.matches(fireball) },
                        abilityIds = (sheet.abilityIds + fireball.id).distinct(),
                    )
                }
            }
        } else {
            library.characters
        }
        return library.copy(
            schemaVersion = SheetLibrary.SCHEMA_VERSION,
            characters = characters,
            abilities = abilities,
        )
    }

    private fun WeaponEntry.matches(ability: CatalogAbility): Boolean =
        name == ability.name &&
            attackBonus == ability.attackBonus &&
            diceCount == ability.diceCount &&
            diceSides == ability.diceSides &&
            damageModifier == ability.damageModifier &&
            damageType == ability.damageType &&
            rangeFeet == ability.rangeFeet &&
            note == ability.rulesText &&
            bonusAction == (ability.activationCost == app.d6d.domain.combat.ActivationCost.BONUS_ACTION) &&
            areaRadiusFeet == ability.areaRadiusFeet &&
            saveAbility == ability.saveAbility &&
            halfOnSave == ability.halfOnSave
}
