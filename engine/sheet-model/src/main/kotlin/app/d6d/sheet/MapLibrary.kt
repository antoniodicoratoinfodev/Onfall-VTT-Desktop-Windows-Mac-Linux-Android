package app.d6d.sheet

import kotlinx.serialization.Serializable

/**
 * Una mappa nell'archivio personale dell'utente.
 *
 * <p>L'identificatore e' stabile e non cambia rinominando: uno sfondo o una
 * sessione salvata si riferiscono al nome del file dell'immagine, mentre il nome
 * visualizzato resta libero di cambiare senza rompere quei riferimenti.</p>
 */
@Serializable
data class StoredMap(
    val id: String,
    val name: String,
    /** Nome del file nell'archivio locale delle immagini. */
    val image: String,
)

/**
 * Archivio delle mappe caricate dall'utente.
 *
 * Come i ritratti, le immagini vivono nella cartella dati locale e l'archivio ne
 * conserva solo il nome interno: e' un indice, non i byte. Resta deliberatamente
 * locale, escluso di predefinito dagli export condivisibili — copiare una mappa
 * acquistata non la rende distribuibile.
 */
@Serializable
data class MapLibrary(
    val schemaVersion: Int = 1,
    val maps: List<StoredMap> = emptyList(),
    /**
     * Le mappe incluse gia' installate, per identificativo.
     *
     * Serve a distinguere «non c'e' ancora» da «c'era e l'utente l'ha tolta». Senza
     * questa memoria una mappa inclusa eliminata tornerebbe al riavvio successivo,
     * e non ci sarebbe modo di liberarsene: l'unica cosa peggiore di un archivio
     * vuoto e' un archivio che si riempie da solo.
     */
    val installedDefaults: List<String> = emptyList(),
)
