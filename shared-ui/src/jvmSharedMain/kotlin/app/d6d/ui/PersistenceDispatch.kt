package app.d6d.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Un solo writer evita che recovery, layout e autosave saturino insieme il disco.
// Su Windows le scansioni antivirus amplificano molto la contesa fra file atomici.
private val persistenceDispatcher = Dispatchers.IO.limitedParallelism(1)

/** Runs blocking local-disk work away from the Compose/UI dispatcher, in serial order. */
internal suspend fun <T> runDiskIo(block: () -> T): T =
    withContext(persistenceDispatcher) { block() }
