package app.d6d.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs blocking local-disk work away from the Compose/UI dispatcher. */
internal suspend fun <T> runDiskIo(block: () -> T): T =
    withContext(Dispatchers.IO) { block() }
