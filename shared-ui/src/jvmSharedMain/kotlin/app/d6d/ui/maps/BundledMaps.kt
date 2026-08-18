package app.d6d.ui.maps

import app.d6d.ui.images.MapSeed

/**
 * Le mappe che arrivano insieme all'applicazione.
 *
 * Un archivio vuoto al primo avvio e' un vicolo cieco: «Scegli sfondo» non ha
 * niente da mostrare, e chi apre il programma per provarlo deve prima procurarsi
 * un'immagine altrove. Con queste, il tavolo funziona da subito.
 *
 * Restano mappe come tutte le altre — si rinominano, si eliminano, e una volta
 * eliminate non tornano. Non sono uno sfondo predefinito: nessuna partita ne
 * riceve una senza che qualcuno l'abbia scelta.
 */
internal object BundledMaps {

    /**
     * Una mappa inclusa.
     *
     * L'[id] e' stabile e non deriva dal nome del file: e' cio' che permette di
     * riconoscerla anche dopo che l'utente l'ha rinominata, e di ricordarsi che era
     * gia' stata installata.
     */
    data class Bundled(val id: String, val name: String, val fileName: String) {
        /** Percorso dentro le risorse dell'applicazione. */
        val resourcePath: String get() = "$DIRECTORY/$fileName"
    }

    /**
     * Risorse Java, non risorse Compose.
     *
     * Le risorse Compose di questo modulo non arrivano nell'APK — non ci arrivano
     * nemmeno i font del tema — mentre le risorse Java si', come dimostrano i JSON
     * del pacchetto SRD che sono li' dentro da sempre. Il caricatore di classi le
     * trova su entrambi i bersagli, e una mappa che esiste solo sul desktop non e'
     * una mappa inclusa: e' una sorpresa per chi apre l'app sul telefono.
     */
    private const val DIRECTORY = "mappe"

    /**
     * Il nome resta quello dell'autore, attribuzione compresa.
     *
     * Non e' cortesia: sono mappe di altri, e il nome e' l'unico posto dell'interfaccia
     * dove chi le ha disegnate compare. Chi rinomina una mappa lo fa per se'; noi non
     * la consegniamo gia' anonima.
     *
     * **Materiale di terze parti con licenza da accertare: vedi `NOTICE-MAPS.md`.**
     * Svuotare questo elenco e' tutto cio' che serve per non distribuirle — l'archivio
     * nasce vuoto e il resto continua a funzionare.
     */
    val all: List<Bundled> = listOf(
        Bundled(
            id = "map-bundled-anubis-tomb",
            name = "Anubis Tomb (DnDavid)",
            fileName = "anubis_tomb.jpg",
        ),
        Bundled(
            id = "map-bundled-abandoned-well",
            name = "Abandoned Well (DnDavid)",
            fileName = "abandoned_well.jpg",
        ),
        Bundled(
            id = "map-bundled-cathedral-of-avacyn-basement",
            name = "Cathedral of Avacyn Basement [40x60] (DnDavid)",
            fileName = "cathedral_of_avacyn_basement.jpg",
        ),
        Bundled(
            id = "map-bundled-volcano-temple",
            name = "VolcanoTempleHD",
            fileName = "volcano_temple.jpg",
        ),
    )

    /** I byte di una mappa inclusa, letti dal pacchetto dell'applicazione. */
    fun bytesOf(map: Bundled): ByteArray =
        checkNotNull(BundledMaps::class.java.getResourceAsStream("/${map.resourcePath}")) {
            "Mappa inclusa non impacchettata: ${map.resourcePath}"
        }.use { it.readBytes() }

    /** Le mappe incluse nella forma che l'archivio sa installare. */
    fun seeds(): List<MapSeed> = all.map { map ->
        MapSeed(map.id, map.name, map.fileName) { bytesOf(map) }
    }
}
