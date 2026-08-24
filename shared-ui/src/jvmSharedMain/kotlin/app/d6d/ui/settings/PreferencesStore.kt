package app.d6d.ui.settings

import app.d6d.i18n.AppLanguage
import app.d6d.board.StampKind
import app.d6d.board.TemplateShape
import app.d6d.persistence.json.AtomicFiles
import app.d6d.ui.state.EnemyCpuSpeed
import app.d6d.ui.dice.DiceRollVisibility
import app.d6d.ui.dice.DiceSkinId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Preferenze dell'applicazione, distinte dalla disposizione dei pannelli.
 *
 * In `layout.json` sta dove sono le cose; qui sta come l'app si comporta. Sono due
 * domande diverse e vale la pena tenerle in due file: una disposizione buttata via
 * non deve portarsi dietro il ritmo con cui si guarda giocare la CPU.
 *
 * Gli enum si scrivono per nome e non come indice: rinumerare o riordinare le voci
 * non deve cambiare il significato di un file gia' salvato. Un nome che non esiste
 * piu' ricade sul predefinito in [sanitized], come fa il ripristino di una partita.
 */
@Serializable
data class AppPreferences(
    val schemaVersion: Int = SCHEMA_VERSION,
    // Il ritmo e' una preferenza di chi guarda, non una proprieta' dello scontro:
    // vale per ogni partita aperta e per quelle che verranno.
    val enemyCpuSpeed: String = EnemyCpuSpeed.NORMAL.name,
    // Il fondale animato costa qualche frame anche quando l'app e' ferma. Su una
    // macchina modesta poterlo spegnere vale piu' dell'atmosfera.
    val animatedBackdrop: Boolean = true,
    // Vuoto, e non «it», per un motivo: il campo assente e il campo vuoto devono
    // significare la stessa cosa, cioe' «non ho ancora scelto», e ricadere sulla
    // lingua del sistema. Scriverci dentro una lingua predefinita renderebbe una
    // scelta mai fatta indistinguibile da una fatta apposta.
    val language: String = "",
    val boardColorArgb: Int = DEFAULT_BOARD_COLOR,
    val boardStrokeWidth: Float = 0.12f,
    val boardTemplateShape: String = TemplateShape.SPHERE.name,
    val boardStampKind: String = StampKind.FLAME.name,
    // Predefinito conservativo: aggiornare l'app non inserisce attese nei
    // combattimenti esistenti finche' il tavolo non sceglie esplicitamente.
    val diceRollVisibility: String = DiceRollVisibility.HIDDEN.name,
    val diceSkin: String = DiceSkinId.RUNIC_OBSIDIAN.name,
    val reducedDiceEffects: Boolean = false,
) {
    /** Riporta ogni campo a un valore che l'interfaccia sa rappresentare. */
    fun sanitized(): AppPreferences = copy(
        enemyCpuSpeed = speedOrDefault().name,
        boardStrokeWidth = boardStrokeWidth.takeIf { it.isFinite() }?.coerceIn(0.03f, 2f) ?: 0.12f,
        boardTemplateShape = templateShapeOrDefault().name,
        boardStampKind = stampKindOrDefault().name,
        diceRollVisibility = diceRollVisibilityOrDefault().name,
        diceSkin = diceSkinOrDefault().name,
    )

    /** Il ritmo salvato, o quello predefinito se il nome non esiste piu'. */
    fun speedOrDefault(): EnemyCpuSpeed =
        runCatching { EnemyCpuSpeed.valueOf(enemyCpuSpeed) }.getOrDefault(EnemyCpuSpeed.NORMAL)

    /** La lingua salvata, o quella del sistema finche' nessuno ne ha scelta una. */
    fun languageOrSystemDefault(): AppLanguage = AppLanguage.parseOrSystemDefault(language)

    fun templateShapeOrDefault(): TemplateShape =
        runCatching { TemplateShape.valueOf(boardTemplateShape) }.getOrDefault(TemplateShape.SPHERE)

    fun stampKindOrDefault(): StampKind =
        runCatching { StampKind.valueOf(boardStampKind) }.getOrDefault(StampKind.FLAME)

    fun diceRollVisibilityOrDefault(): DiceRollVisibility =
        runCatching { DiceRollVisibility.valueOf(diceRollVisibility) }
            .getOrDefault(DiceRollVisibility.HIDDEN)

    fun diceSkinOrDefault(): DiceSkinId =
        runCatching { DiceSkinId.valueOf(diceSkin) }.getOrDefault(DiceSkinId.RUNIC_OBSIDIAN)

    companion object {
        const val SCHEMA_VERSION = 1
        const val DEFAULT_BOARD_COLOR: Int = 0xFFFFC857.toInt()
    }
}

/**
 * Archivio locale delle preferenze.
 *
 * Stesse garanzie di [app.d6d.ui.layout.LayoutStore]: scrittura atomica con copia
 * di riserva, e una lettura che non solleva mai. Una preferenza illeggibile deve
 * costare i valori predefiniti, non l'avvio dell'applicazione.
 */
class PreferencesStore(private val file: Path) {

    private val backup: Path get() = file.resolveSibling("${file.fileName}.bak")

    private val json = Json {
        prettyPrint = true
        // Un campo aggiunto in futuro non deve impedire di leggere un file vecchio.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AppPreferences = decode(file) ?: decode(backup) ?: AppPreferences()

    fun save(preferences: AppPreferences) {
        AtomicFiles.writeUtf8WithBackup(
            file,
            backup,
            json.encodeToString(AppPreferences.serializer(), preferences.sanitized()),
        )
    }

    private fun decode(candidate: Path): AppPreferences? {
        if (!Files.isRegularFile(candidate)) return null
        // `readAllBytes` invece di `readString`: quest'ultimo e' Java 11 e non
        // esiste nell'SDK Android che compila lo stesso sorgente condiviso.
        val text = runCatching {
            String(Files.readAllBytes(candidate), StandardCharsets.UTF_8)
        }.getOrNull()
        if (text.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(AppPreferences.serializer(), text) }
            .getOrNull()
            ?.sanitized()
    }
}
