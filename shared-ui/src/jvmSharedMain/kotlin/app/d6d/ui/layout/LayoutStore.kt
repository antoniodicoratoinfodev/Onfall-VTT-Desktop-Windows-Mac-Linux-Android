package app.d6d.ui.layout

import app.d6d.persistence.json.AtomicFiles
import app.d6d.ui.battle.MAX_CELL_DP
import app.d6d.ui.battle.MIN_CELL_DP
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

/**
 * Posizione di una targa flottante, espressa come frazione dello spazio libero.
 *
 * Non salviamo pixel: `0` e' l'angolo iniziale, `1` il bordo opposto. Cosi' la
 * targa resta nello stesso punto relativo anche su uno schermo di dimensioni
 * diverse, e il file delle impostazioni resta trasportabile fra dispositivi.
 */
@Serializable
data class PlateFraction(val x: Float, val y: Float) {
    fun sanitized(): PlateFraction = PlateFraction(x.clampFraction(), y.clampFraction())

    private fun Float.clampFraction(): Float = if (isNaN() || isInfinite()) 0f else coerceIn(0f, 1f)
}

/**
 * Disposizione dell'interfaccia da conservare fra un avvio e l'altro.
 *
 * Contiene solo scelte indipendenti dal dispositivo: larghezze e altezze dei
 * pannelli in dp, stato aperto/chiuso o collassato, zoom della mappa e posizione
 * relativa delle targhe. Non registra la dimensione della finestra ne' la
 * risoluzione del monitor, che cambiano da un dispositivo all'altro.
 */
@Serializable
data class UiLayout(
    val schemaVersion: Int = SCHEMA_VERSION,
    val railWidthDp: Float = 54f,
    val railOpen: Boolean = true,
    val squadWidthDp: Float = 230f,
    val enemyWidthDp: Float = 310f,
    val logHeightDp: Float = 230f,
    val logCollapsed: Boolean = false,
    val turnsCollapsed: Boolean = false,
    // Separato dal collasso per restare compatibili con le preferenze salvate
    // quando l'ordine dei turni aveva soltanto due stati.
    val turnsShowInitiative: Boolean = true,
    // Altezza della fascia turni sopra la mappa: i riquadri ne seguono la misura,
    // mentre il testo conserva la densita' nativa per restare nitido.
    val topBarHeightDp: Float = 64f,
    // Altezza fissa della fascia comandi sotto la mappa, con scorrimento interno.
    val commandBarHeightDp: Float = 176f,
    val commandsCollapsed: Boolean = false,
    val mapCellSizeDp: Float = 46f,
    val mapShowGrid: Boolean = true,
    // Luminosita' delle linee della griglia, scelta dall'utente: 0 quasi
    // invisibili, 1 ben marcate. Predefinito a meta' scala, come i vecchi grigi.
    val mapGridBrightness: Float = 0.5f,
    // La Cassetta fissata resta sul bordo del palco e si muove soltanto in verticale.
    val toolboxPinned: Boolean = false,
    val toolboxVerticalFraction: Float = 0.5f,
    val targetPlate: PlateFraction? = null,
    val activePlate: PlateFraction? = null,
    // Scala delle due targhe flottanti: la maniglia d'angolo le ingrandisce o
    // rimpicciolisce, e con loro tutto il contenuto (nome, barra, chip, cornice).
    val targetPlateScale: Float = 1f,
    val activePlateScale: Float = 1f,
) {
    /**
     * Riporta ogni valore entro limiti ragionevoli.
     *
     * Un file vecchio o danneggiato non deve piazzare un pannello fuori scala o
     * con un numero non valido: i margini sono ampi, perche' il ritaglio fine lo
     * fanno gia' i cursori di trascinamento quando l'utente li tocca.
     */
    fun sanitized(): UiLayout = copy(
        railWidthDp = railWidthDp.clampOr(40f, 400f, 54f),
        squadWidthDp = squadWidthDp.clampOr(100f, 640f, 230f),
        enemyWidthDp = enemyWidthDp.clampOr(120f, 720f, 310f),
        logHeightDp = logHeightDp.clampOr(40f, 640f, 230f),
        topBarHeightDp = topBarHeightDp.clampOr(48f, 320f, 64f),
        commandBarHeightDp = commandBarHeightDp.clampOr(48f, 640f, 176f),
        // Deve usare gli stessi estremi di slider, pulsanti e rotellina: in caso
        // contrario uno zoom molto ampio verrebbe perso al riavvio dell'app.
        mapCellSizeDp = mapCellSizeDp.clampOr(MIN_CELL_DP, MAX_CELL_DP, 46f),
        mapGridBrightness = mapGridBrightness.clampOr(0.05f, 1f, 0.5f),
        toolboxVerticalFraction = toolboxVerticalFraction.clampOr(0f, 1f, 0.5f),
        targetPlate = targetPlate?.sanitized(),
        activePlate = activePlate?.sanitized(),
        targetPlateScale = targetPlateScale.clampOr(0.6f, 2f, 1f),
        activePlateScale = activePlateScale.clampOr(0.6f, 2f, 1f),
    )

    private fun Float.clampOr(min: Float, max: Float, fallback: Float): Float =
        if (isNaN() || isInfinite()) fallback else coerceIn(min, max)

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Archivio locale della disposizione dell'interfaccia.
 *
 * Scrive in modo atomico — file temporaneo e sostituzione, con una copia di
 * riserva — cosi' un'interruzione non lascia un file troncato. La lettura non
 * solleva mai eccezioni: un file assente o illeggibile ricade sui valori
 * predefiniti, perche' una preferenza corrotta non deve impedire l'avvio.
 */
class LayoutStore(private val file: Path) {

    private val backup: Path get() = file.resolveSibling("${file.fileName}.bak")

    private val json = Json {
        prettyPrint = true
        // Un campo aggiunto in futuro non deve impedire di leggere un file vecchio.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): UiLayout {
        // `Files.readAllBytes`/`write` invece di `readString`/`writeString`: questi
        // ultimi sono Java 11 e non esistono nell'SDK Android che compila lo stesso
        // sorgente condiviso.
        return decode(file)
            ?: decode(backup)
            ?: UiLayout()
    }

    fun save(layout: UiLayout) {
        AtomicFiles.writeUtf8WithBackup(
            file,
            backup,
            json.encodeToString(UiLayout.serializer(), layout.sanitized()),
        )
    }

    private fun decode(candidate: Path): UiLayout? {
        if (!Files.isRegularFile(candidate)) return null
        val text = runCatching {
            String(Files.readAllBytes(candidate), StandardCharsets.UTF_8)
        }.getOrNull()
        if (text.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(UiLayout.serializer(), text) }
            .getOrNull()
            ?.sanitized()
    }
}
