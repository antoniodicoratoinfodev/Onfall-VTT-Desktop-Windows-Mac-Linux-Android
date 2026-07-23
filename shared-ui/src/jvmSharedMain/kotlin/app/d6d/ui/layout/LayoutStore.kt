package app.d6d.ui.layout

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
    // Altezza fissa della fascia turni sopra la mappa: i riquadri turno vengono
    // scalati per riempirla, quindi allargandola crescono e restringendola calano.
    val topBarHeightDp: Float = 64f,
    // Altezza fissa della fascia comandi sotto la mappa, con scorrimento interno.
    val commandBarHeightDp: Float = 176f,
    val commandsCollapsed: Boolean = false,
    val mapCellSizeDp: Float = 46f,
    val mapShowGrid: Boolean = true,
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
        mapCellSizeDp = mapCellSizeDp.clampOr(14f, 140f, 46f),
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

    private val json = Json {
        prettyPrint = true
        // Un campo aggiunto in futuro non deve impedire di leggere un file vecchio.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): UiLayout {
        if (!Files.exists(file)) return UiLayout()
        // `Files.readAllBytes`/`write` invece di `readString`/`writeString`: questi
        // ultimi sono Java 11 e non esistono nell'SDK Android che compila lo stesso
        // sorgente condiviso.
        val text = runCatching { String(Files.readAllBytes(file)) }.getOrNull()
        if (text.isNullOrBlank()) return UiLayout()
        return runCatching { json.decodeFromString(UiLayout.serializer(), text) }
            .getOrDefault(UiLayout())
            .sanitized()
    }

    fun save(layout: UiLayout) {
        file.parent?.let { Files.createDirectories(it) }

        if (Files.exists(file)) {
            val backup = file.resolveSibling("${file.fileName}.bak")
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
        }

        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.write(temporary, json.encodeToString(UiLayout.serializer(), layout.sanitized()).toByteArray())
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
    }
}
