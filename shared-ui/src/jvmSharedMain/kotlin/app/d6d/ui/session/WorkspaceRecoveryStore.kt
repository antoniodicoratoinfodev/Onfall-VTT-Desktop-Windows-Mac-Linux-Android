package app.d6d.ui.session

import app.d6d.engine.CombatSession
import app.d6d.persistence.combat.CombatSessionJsonCodec
import app.d6d.persistence.json.AtomicJsonStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

/** Fotografia trasportabile delle schede aperte, usata soltanto per il crash recovery. */
internal data class WorkspaceRecovery(
    val activeIndex: Int,
    val sessions: List<RecoveredGameSession>,
)

internal data class RecoveredGameSession(
    val displayName: String,
    val currentSlug: String?,
    val session: CombatSession,
    val presentation: Map<String, String>,
)

/**
 * Bozza atomica dell'intero workspace.
 *
 * Non sostituisce i salvataggi scelti dall'utente: resta in un file dedicato e
 * viene cancellata dopo una chiusura pulita. Se il processo si interrompe, al
 * riavvio permette di recuperare anche le sessioni che non avevano ancora nome.
 */
internal class WorkspaceRecoveryStore(
    directory: Path,
    private val codec: CombatSessionJsonCodec = CombatSessionJsonCodec(),
) {
    private val directory = directory.toAbsolutePath().normalize()
    private val file = this.directory.resolve(FILE_NAME)
    private val backupDirectory = this.directory.resolve("backups")
    private val store = AtomicJsonStore(this.directory, FILE_NAME, MAX_BACKUPS)
    private var enabled = true

    @Synchronized
    fun save(recovery: WorkspaceRecovery) {
        if (!enabled) return
        require(recovery.sessions.isNotEmpty()) { "Il workspace da recuperare non può essere vuoto" }

        val encodedSessions = recovery.sessions.map { recovered ->
            LinkedHashMap<String, Any>().apply {
                put("displayName", recovered.displayName)
                recovered.currentSlug?.let { put("currentSlug", it) }
                put("presentation", LinkedHashMap<String, Any>().apply {
                    recovered.presentation.forEach { (key, value) -> put(key, value) }
                })
                put("combat", codec.encode(recovered.session))
            }
        }
        val document = LinkedHashMap<String, Any>().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("activeIndex", recovery.activeIndex)
            put("sessions", encodedSessions)
        }
        store.save(document)
    }

    @Synchronized
    fun load(): WorkspaceRecovery? {
        if (!store.exists()) return null
        val document = store.loadObject()
        val schemaVersion = integer(document["schemaVersion"], "$.schemaVersion")
        if (schemaVersion != SCHEMA_VERSION) {
            throw IOException("Versione della bozza workspace non supportata: $schemaVersion")
        }

        val values = document["sessions"] as? List<*>
            ?: throw IOException("Campo $.sessions mancante o non valido")
        if (values.isEmpty()) throw IOException("La bozza workspace non contiene sessioni")

        val sessions = values.mapIndexed { index, value ->
            val path = "$.sessions[$index]"
            val entry = objectValue(value, path)
            val displayName = string(entry["displayName"], "$path.displayName")
            val currentSlug = entry["currentSlug"]?.let { string(it, "$path.currentSlug") }
            val presentation = stringMap(entry["presentation"], "$path.presentation")
            val combat = codec.decode(objectValue(entry["combat"], "$path.combat"))
            RecoveredGameSession(displayName, currentSlug, combat, presentation)
        }
        val activeIndex = integer(document["activeIndex"], "$.activeIndex")
            .coerceIn(0, sessions.lastIndex)
        return WorkspaceRecovery(activeIndex, sessions)
    }

    @Synchronized
    fun clear() {
        // Impedisce a un autosave già accodato di ricreare la bozza mentre la
        // finestra sta terminando dopo una conferma esplicita.
        enabled = false
        Files.deleteIfExists(file)
        if (Files.isDirectory(backupDirectory)) {
            Files.newDirectoryStream(backupDirectory, "$FILE_STEM-*.json").use { backups ->
                backups.forEach { backup ->
                    if (Files.isRegularFile(backup)) Files.deleteIfExists(backup)
                }
            }
        }
    }

    private fun objectValue(value: Any?, path: String): Map<String, Any?> {
        val source = value as? Map<*, *> ?: throw IOException("Campo $path mancante o non valido")
        return LinkedHashMap<String, Any?>().apply {
            source.forEach { (key, child) ->
                val name = key as? String ?: throw IOException("Chiave non testuale in $path")
                put(name, child)
            }
        }
    }

    private fun stringMap(value: Any?, path: String): Map<String, String> =
        objectValue(value, path).mapValues { (key, child) -> string(child, "$path.$key") }

    private fun string(value: Any?, path: String): String =
        value as? String ?: throw IOException("Campo $path mancante o non valido")

    private fun integer(value: Any?, path: String): Int = when (value) {
        is Byte -> value.toInt()
        is Short -> value.toInt()
        is Int -> value
        is Long -> value.toInt().takeIf { it.toLong() == value }
            ?: throw IOException("Campo $path fuori intervallo")
        else -> throw IOException("Campo $path mancante o non valido")
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "workspace-recovery.json"
        const val FILE_STEM = "workspace-recovery"
        const val MAX_BACKUPS = 2
    }
}
