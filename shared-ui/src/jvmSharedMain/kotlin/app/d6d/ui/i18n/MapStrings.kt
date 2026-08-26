package app.d6d.ui.i18n

/**
 * L'archivio delle mappe, il selettore dello sfondo e il caricamento immagini.
 *
 * Le immagini vivono nel Compendio ma servono al tavolo: il fascicolo sta a se'
 * perche' le stesse frasi compaiono da entrambe le parti.
 */
interface MapStrings {
    val archiveSubtitle: String
    val archiveEmpty: String
    val uploadMap: String
    val upload: String
    val uploadImage: String
    val chooseImageDialogTitle: String
    val changeImage: String
    val deleteMapTitle: String
    val previewUnavailable: String
    val chooseBackground: String
    val chooseBackgroundSubtitle: String
    val pickerEmpty: String
    val currentBackground: String
    val removeBackground: String
    val unnamedMap: String

    /** La cartella dove stanno i file delle mappe, e cosa farci. */
    val mapsFolder: String
    val mapsFolderHint: String

    val imageSelectionCancelled: String
    val backgroundLoaded: String
    val portraitAssigned: String
    val invalidImage: String
    val imagePickerAlreadyOpen: String
    val imageProviderReturnedNoData: String

    fun mapsCount(count: Int): String
    fun deleteMapBody(name: String): String
    fun formatsAndLimit(formats: String, maxSize: String): String
    fun imageTooLarge(maxSize: String): String
    fun mapAdded(name: String): String
    fun mapDeleted(name: String): String
    fun cannotOpenImage(detail: String): String
    fun diskError(detail: String): String

    /** Il segnaposto non entra nella griglia scelta. */
    fun gridTooSmall(columns: Int, rows: Int): String
}

internal object MapStringsIt : MapStrings {
    override val archiveSubtitle = "Il tuo archivio di sfondi. Caricali una volta e riusali " +
        "in ogni partita da «Scegli sfondo»."
    override val archiveEmpty =
        "Nessuna mappa nell'archivio.\nCarica un'immagine per iniziare la tua collezione."
    override val uploadMap = "＋ Carica mappa"
    override val upload = "＋ Carica"
    override val uploadImage = "Carica immagine"
    override val chooseImageDialogTitle = "Scegli un'immagine"
    override val changeImage = "Cambia"
    override val deleteMapTitle = "Eliminare la mappa?"
    override val previewUnavailable = "Anteprima non disponibile"
    override val chooseBackground = "Scegli sfondo"
    override val chooseBackgroundSubtitle = "Dall'archivio delle mappe del Compendio."
    override val pickerEmpty = "L'archivio è vuoto. Carica una mappa per usarla come sfondo, " +
        "poi la ritroverai qui in ogni partita."
    override val currentBackground = "Sfondo attuale"
    override val removeBackground = "Togli sfondo"
    override val unnamedMap = "Mappa senza nome"

    override val mapsFolder = "Cartella delle mappe"
    override val mapsFolderHint =
        "Puoi copiare le immagini direttamente qui: le trovi nell'archivio al prossimo avvio."

    override val imageSelectionCancelled = "Selezione immagine annullata."
    override val backgroundLoaded = "Sfondo caricato."
    override val portraitAssigned = "Ritratto assegnato."
    override val invalidImage = "Immagine non valida."
    override val imagePickerAlreadyOpen = "Un selettore di immagini è già aperto."
    override val imageProviderReturnedNoData =
        "Il provider non ha restituito dati per l'immagine selezionata."

    override fun mapsCount(count: Int) = "Mappe ($count)"
    override fun deleteMapBody(name: String) = "«$name» verrà rimossa dall'archivio. " +
        "Le partite che la usano come sfondo resteranno senza immagine."
    override fun formatsAndLimit(formats: String, maxSize: String) = "$formats · max $maxSize"
    override fun imageTooLarge(maxSize: String) =
        "L'immagine selezionata supera il limite di $maxSize."
    override fun mapAdded(name: String) = "Mappa «$name» aggiunta all'archivio."
    override fun mapDeleted(name: String) = "Mappa «$name» eliminata."
    override fun cannotOpenImage(detail: String) = "Impossibile aprire l'immagine: $detail"
    override fun diskError(detail: String) = "Errore su disco: $detail"

    override fun gridTooSmall(columns: Int, rows: Int) =
        "La griglia $columns×$rows è troppo piccola per tutti i token selezionati."
}

internal object MapStringsEn : MapStrings {
    override val archiveSubtitle = "Your library of backdrops. Upload one once and reuse it " +
        "in every game from “Choose background”."
    override val archiveEmpty =
        "No maps in the archive.\nUpload an image to start your collection."
    override val uploadMap = "＋ Upload map"
    override val upload = "＋ Upload"
    override val uploadImage = "Upload image"
    override val chooseImageDialogTitle = "Choose an image"
    override val changeImage = "Change"
    override val deleteMapTitle = "Delete the map?"
    override val previewUnavailable = "Preview unavailable"
    override val chooseBackground = "Choose background"
    override val chooseBackgroundSubtitle = "From the Compendium's map archive."
    override val pickerEmpty = "The archive is empty. Upload a map to use it as a backdrop, " +
        "and you will find it here in every game."
    override val currentBackground = "Current background"
    override val removeBackground = "Remove background"
    override val unnamedMap = "Unnamed map"

    override val mapsFolder = "Maps folder"
    override val mapsFolderHint =
        "You can copy images straight into it: they show up in the archive on the next start."

    override val imageSelectionCancelled = "Image selection cancelled."
    override val backgroundLoaded = "Background loaded."
    override val portraitAssigned = "Portrait assigned."
    override val invalidImage = "Invalid image."
    override val imagePickerAlreadyOpen = "An image picker is already open."
    override val imageProviderReturnedNoData =
        "The provider returned no data for the selected image."

    override fun mapsCount(count: Int) = "Maps ($count)"
    override fun deleteMapBody(name: String) = "“$name” will be removed from the archive. " +
        "Games using it as a backdrop will be left without an image."
    override fun formatsAndLimit(formats: String, maxSize: String) = "$formats · max $maxSize"
    override fun imageTooLarge(maxSize: String) =
        "The selected image exceeds the $maxSize limit."
    override fun mapAdded(name: String) = "Map “$name” added to the archive."
    override fun mapDeleted(name: String) = "Map “$name” deleted."
    override fun cannotOpenImage(detail: String) = "Could not open the image: $detail"
    override fun diskError(detail: String) = "Disk error: $detail"

    override fun gridTooSmall(columns: Int, rows: Int) =
        "The $columns×$rows grid is too small for every selected token."
}
