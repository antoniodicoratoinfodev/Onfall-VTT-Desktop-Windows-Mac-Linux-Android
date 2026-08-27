package app.d6d.sheet

import app.d6d.persistence.json.AtomicFiles
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

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
        const val SCHEMA_VERSION = 13
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

    private val backup: Path get() = file.resolveSibling("${file.fileName}.bak")

    var recoveredFromBackup: Boolean = false
        private set

    private val json = Json {
        prettyPrint = true
        // Un campo aggiunto in futuro non deve impedire di leggere un file vecchio.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exists(): Boolean = Files.exists(file) || Files.exists(backup)

    fun load(): SheetLibrary {
        recoveredFromBackup = false
        if (!Files.exists(file)) {
            if (!Files.isRegularFile(backup)) return SheetLibrary()
            return decode(Files.readString(backup)).also { recoveredFromBackup = true }
        }
        return try {
            decode(Files.readString(file))
        } catch (failure: Exception) {
            if (failure is UnsupportedSheetSchemaException || !Files.isRegularFile(backup)) throw failure
            try {
                decode(Files.readString(backup)).also { recoveredFromBackup = true }
            } catch (backupFailure: Exception) {
                failure.addSuppressed(backupFailure)
                throw failure
            }
        }
    }

    private fun decode(text: String): SheetLibrary {
        if (text.isBlank()) return SheetLibrary()
        val storedVersion = json.parseToJsonElement(text)
            .jsonObject["schemaVersion"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: 1
        if (storedVersion > SheetLibrary.SCHEMA_VERSION) {
            throw UnsupportedSheetSchemaException(
                "Il file usa lo schema $storedVersion, ma questa versione dell'app supporta " +
                    "fino allo schema ${SheetLibrary.SCHEMA_VERSION}. Aggiorna l'app prima di salvarlo.",
            )
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
        AtomicFiles.writeUtf8WithBackup(
            file,
            backup,
            json.encodeToString(SheetLibrary.serializer(), library),
        )
        recoveredFromBackup = false
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
        // Lo schema 8 aggiunge due metadati che non esistevano nei documenti
        // precedenti. Per gli ID incorporati possiamo recuperarli senza euristiche;
        // nome e numeri eventualmente personalizzati restano invece intatti.
        val migratedExisting = if (library.schemaVersion < 8) {
            library.abilities.map { stored ->
                val builtIn = defaults.firstOrNull { it.id == stored.id }
                if (builtIn == null) {
                    stored
                } else {
                    stored.copy(
                        attackAbility = stored.attackAbility ?: builtIn.attackAbility,
                        spellOrCantrip = stored.spellOrCantrip || builtIn.spellOrCantrip,
                    )
                }
            }
        } else {
            library.abilities
        }
        // Lo schema 10 aveva reso eseguibile il preset Recuperare Energie.
        val healedExisting = if (library.schemaVersion < 10) {
            val replacement = defaults.first { it.id == "abilita-recuperare-energie" }
            migratedExisting.map { stored ->
                if (stored == legacyRecoverEnergyAbility()) replacement else stored
            }
        } else {
            migratedExisting
        }
        // Quel preset non possiede pero' una riserva sulla scheda privata: lasciarlo
        // automatico lo renderebbe illimitato. Lo schema 11 riporta al tavolo solo
        // la copia incorporata esatta; le varianti personalizzate restano intatte.
        val resourceSafeExisting = if (library.schemaVersion < 11) {
            val replacement = defaults.first { it.id == "abilita-recuperare-energie" }
            healedExisting.map { stored ->
                if (stored == legacyUnlimitedRecoverEnergyAbility()) replacement else stored
            }
        } else {
            healedExisting
        }
        val abilities = resourceSafeExisting +
            defaults.filter { builtIn -> library.abilities.none { it.id == builtIn.id } }

        // Lo schema 9 porta la stessa classificazione anche sulle vecchie righe
        // "Armi e trucchetti". I preset riconoscibili ereditano i metadati del
        // catalogo; una riga realmente ambigua viene invece marcata per una scelta
        // esplicita, così non può aggirare le restrizioni dell'armatura.
        val classifiedCharacters = if (library.schemaVersion < 9) {
            library.characters.map { sheet ->
                sheet.copy(
                    weapons = sheet.weapons.map { weapon ->
                        weapon.withMigratedClassification(abilities)
                    },
                )
            }
        } else {
            library.characters
        }

        // Dalla versione 4 la CA puo' essere calcolata. I nuovi campi hanno come
        // predefinito MANUAL_TOTAL: una scheda precedente conserva quindi il suo
        // identico valore finale finche' l'utente non sceglie un metodo guidato.
        val characters = if (library.schemaVersion < 2) {
            val fireball = abilities.first { it.id == "inc-palla-di-fuoco" }
            classifiedCharacters.map { sheet ->
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
            classifiedCharacters
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

    private fun WeaponEntry.withMigratedClassification(
        abilities: List<CatalogAbility>,
    ): WeaponEntry {
        val matchingAbility = abilities.firstOrNull { ability ->
            matches(ability) &&
                (ability.attackAbility != null || ability.isSpellOrCantrip)
        }
        return when {
            name.isBlank() -> copy(legacyClassificationRequired = false)
            matchingAbility != null -> copy(
                attackAbility = attackAbility ?: matchingAbility.attackAbility,
                spellOrCantrip = spellOrCantrip || matchingAbility.isSpellOrCantrip,
                legacyClassificationRequired = false,
            )
            attackAbility != null || isSpellOrCantrip -> copy(
                legacyClassificationRequired = false,
            )
            else -> copy(legacyClassificationRequired = true)
        }
    }
}

private fun legacyRecoverEnergyAbility(): CatalogAbility = CatalogAbility(
    id = "abilita-recuperare-energie",
    name = "Recuperare Energie",
    activationCost = app.d6d.domain.combat.ActivationCost.BONUS_ACTION,
    resolutionMethod = app.d6d.domain.combat.ResolutionMethod.MANUAL,
    dealsDamage = false,
    automationStatus = app.d6d.domain.combat.AutomationStatus.MANUAL_REQUIRED,
    rulesText = "Recupera 1d10 + livello da guerriero punti ferita; si ricarica con un riposo.",
)

private fun legacyUnlimitedRecoverEnergyAbility(): CatalogAbility = CatalogAbility(
    id = "abilita-recuperare-energie",
    name = "Recuperare Energie",
    activationCost = app.d6d.domain.combat.ActivationCost.BONUS_ACTION,
    resolutionMethod = app.d6d.domain.combat.ResolutionMethod.AUTOMATIC,
    dealsDamage = false,
    automationStatus = app.d6d.domain.combat.AutomationStatus.AUTOMATED,
    rulesText = "Recupera 1d10 punti ferita.",
    healing = CatalogHealing.dice(app.d6d.domain.combat.HealingTarget.SELF, 1, 10),
)

private class UnsupportedSheetSchemaException(message: String) : IllegalArgumentException(message)
