package app.d6d.ui.i18n

/**
 * I cursori personalizzati, che esistono solo sul desktop.
 *
 * I nomi delle coppie sono descrizioni di un'immagine — «Fredda», «Cuoio» — e non
 * termini di gioco: si traducono come si tradurrebbe la didascalia di una foto.
 */
interface CursorStrings {
    val eyebrow: String
    val title: String
    val subtitle: String
    val pairA: String
    val pairB: String
    val pairC: String
    val pairD: String
    val pairE: String
    val pairAHint: String
    val pairBHint: String
    val pairCHint: String
    val pairDHint: String
    val pairEHint: String
    val grabPose: String
    val pairSelected: String
    val usePair: String
    val inUse: String
    val applyAndRemember: String
    val sizeLabel: String
    val pointerPose: String
    val sizeSmallName: String
    val sizeMediumName: String
    val sizeOriginalName: String
    val sizeSmall: String
    val sizeMedium: String
    val sizeLarge: String
    fun normalPose(title: String): String
    fun dragPose(title: String): String
}

internal object CursorStringsIt : CursorStrings {
    override val eyebrow = "PERSONALIZZAZIONE DESKTOP"
    override val title = "Cursori"
    override val subtitle = "Scegli fra tutte le finiture disponibili. Ogni coppia include " +
        "la posa normale e quella che afferra la mappa."
    override val pairA = "Coppia A · Fredda"
    override val pairB = "Coppia B · Calda"
    override val pairC = "Coppia C · Cuoio"
    override val pairD = "Coppia D · Runica"
    override val pairE = "Coppia E · Acciaio"
    override val pairAHint = "Acciaio blu e riflessi lunari"
    override val pairBHint = "Bronzo, rame e riflessi d'ambra"
    override val pairCHint = "Cuoio scuro e piastre brunite"
    override val pairDHint = "Sigillo azzurro su metallo brunito"
    override val pairEHint = "Acciaio freddo e bagliori di zaffiro"
    override val grabPose = "Presa sulla mappa"
    override val pairSelected = "Coppia selezionata"
    override val usePair = "Usa questa coppia"
    override val inUse = "In uso nella finestra"
    override val applyAndRemember = "Applica subito e ricorda la scelta"
    override val sizeLabel = "Dimensione"
    override val pointerPose = "Puntatore"
    override val sizeSmallName = "Piccolo"
    override val sizeMediumName = "Medio"
    override val sizeOriginalName = "Originale"
    override val sizeSmall = "65% · più discreto"
    override val sizeMedium = "82% · compatto"
    override val sizeLarge = "100% · massima presenza"
    override fun normalPose(title: String) = "$title, posa normale"
    override fun dragPose(title: String) = "$title, posa di trascinamento"
}

internal object CursorStringsEn : CursorStrings {
    override val eyebrow = "DESKTOP PERSONALISATION"
    override val title = "Cursors"
    override val subtitle = "Pick from every finish available. Each pair covers the normal " +
        "pose and the one that grabs the map."
    override val pairA = "Pair A · Cold"
    override val pairB = "Pair B · Warm"
    override val pairC = "Pair C · Leather"
    override val pairD = "Pair D · Runic"
    override val pairE = "Pair E · Steel"
    override val pairAHint = "Blue steel and moonlit highlights"
    override val pairBHint = "Bronze, copper and amber highlights"
    override val pairCHint = "Dark leather and burnished plates"
    override val pairDHint = "An azure seal on burnished metal"
    override val pairEHint = "Cold steel and sapphire glints"
    override val grabPose = "Grabbing the map"
    override val pairSelected = "Pair selected"
    override val usePair = "Use this pair"
    override val inUse = "In use in this window"
    override val applyAndRemember = "Applies at once and remembers the choice"
    override val sizeLabel = "Size"
    override val pointerPose = "Pointer"
    override val sizeSmallName = "Small"
    override val sizeMediumName = "Medium"
    override val sizeOriginalName = "Original"
    override val sizeSmall = "65% · more discreet"
    override val sizeMedium = "82% · compact"
    override val sizeLarge = "100% · full presence"
    override fun normalPose(title: String) = "$title, normal pose"
    override fun dragPose(title: String) = "$title, dragging pose"
}
