package app.d6d.ui.i18n

interface BoardStrings {
    val tools: String
    val table: String
    val edit: String
    val hand: String
    val measure: String
    val ink: String
    val template: String
    val label: String
    val ping: String
    val fog: String
    val floor: String
    val wall: String
    val layers: String
    val eraser: String
    val token: String
    val pin: String
    val unpin: String
    val undo: String
    val redo: String
    val lock: String
    val unlock: String
    val annotations: String
    val stamps: String
    val playerPreview: String
    val coverFog: String
    val revealFog: String
    val coverAllFog: String
    val revealAllFog: String
    val fogPaintHint: String
    val visionPainted: String
    val visionDynamic: String
    val visionDynamicHint: String
    val visionLens: String
    val visionLensTurn: String
    val visionLensParty: String
    val visionPlayerPresentation: String
    val visionMasterPresentation: String
    val visionPresentationAll: String
    val visionPresentationBlack: String
    val visionPresentationDim: String
    val visionRadius: String
    val visionBlind: String
    val forgetExplored: String
    val forgetExploredHint: String
    val visionUseMapRadius: String
    val visionPickCombatantHint: String
    fun visionOf(name: String): String
    val brushSize: String
    val paintFloors: String
    val eraseFloors: String
    val fillFloors: String
    val clearFloors: String
    val floorHint: String
    val addWalls: String
    val eraseWalls: String
    val clearWalls: String
    val wallHint: String
    val pinMeasurement: String
    val clearMeasurement: String
    val strokeWidth: String
    val colour: String
    val writeLabel: String
    val labelHint: String
    val add: String
    val cone: String
    val cube: String
    val cylinder: String
    val emanation: String
    val line: String
    val sphere: String
    val stampMode: String
    val templateMode: String
    val flame: String
    val light: String
    val danger: String
    val door: String
    val treasure: String
    val boardLockedHint: String
    val fogNeedsPlayerView: String
    val illustrativeTemplate: String
    val deleteObject: String
    val smaller: String
    val bigger: String
    val rotateLeft: String
    val rotateRight: String
    val editText: String
    val background: String
    val grid: String
    val tokens: String
    val protectedLayer: String
    val combatants: String
    val sceneTokens: String
    val createToken: String
    val editToken: String
    val tokenName: String
    val tokenNameHint: String
    val tokenCategory: String
    val tokenSize: String
    val showTokenName: String
    val visibleToPlayers: String
    val masterNotes: String
    val masterNotesHint: String
    val createAndPlace: String
    val cancelPlacement: String
    val tokenPlacementHint: String
    val tokenVisualOnlyHint: String
    val tokenImageHint: String
    val lootable: String
    val lootSettingsHint: String
    val lootInventoryCategory: String
    val lootQuantity: String
    val lootDescription: String
    val lootDescriptionHint: String
    val collectLoot: String
    val collectLootHint: String
    val noLootCollectors: String
    val lootTransferTokenMissing: String
    val lootTransferNotLootable: String
    val lootTransferCollectorMissing: String
    val lootTransferSaveFailed: String
    val lootTransferBoardChanged: String
    fun sceneTokenAccessibility(
        name: String,
        category: String,
        collectible: Boolean,
        quantity: Int,
    ): String
    val categoryCharacter: String
    val categoryAlly: String
    val categoryNpc: String
    val categoryMonster: String
    val categoryObject: String
    val categoryTrap: String
    val categoryHazard: String
    val categoryTerrain: String
    val categoryLoot: String
    val categoryVehicle: String
    val categoryMarker: String
    val categoryOther: String
}

object BoardStringsIt : BoardStrings {
    override val tools = "Strumenti"
    override val table = "Tavolo"
    override val edit = "Modifica Lucido"
    override val hand = "Mano"
    override val measure = "Misura"
    override val ink = "Traccia"
    override val template = "Sagoma"
    override val label = "Cartiglio"
    override val ping = "Richiamo"
    override val fog = "Nebbia"
    override val floor = "Pavimento"
    override val wall = "Muri"
    override val layers = "Strati"
    override val eraser = "Gomma"
    override val token = "Pedina"
    override val pin = "Fissa Cassetta"
    override val unpin = "Sgancia Cassetta"
    override val undo = "Annulla Lucido"
    override val redo = "Ripeti Lucido"
    override val lock = "Blocca Lucido"
    override val unlock = "Sblocca Lucido"
    override val annotations = "Annotazioni"
    override val stamps = "Timbri"
    override val playerPreview = "Anteprima giocatori"
    override val coverFog = "Copri"
    override val revealFog = "Scopri"
    override val coverAllFog = "Copri tutto"
    override val revealAllFog = "Scopri tutto"
    override val fogPaintHint = "Trascina sulla mappa per coprire o scoprire le caselle. L’anteprima giocatori mostra il risultato finale."
    override val visionPainted = "A mano"
    override val visionDynamic = "Vista dinamica"
    override val visionDynamicHint =
        "I muri fermano lo sguardo e l'esplorato resta in memoria. Scegli separatamente " +
            "la resa di master e giocatori; i giocatori guardano sempre con gli occhi del gruppo."
    override val visionLens = "Occhi master"
    override val visionLensTurn = "Turno"
    override val visionLensParty = "Gruppo"
    override val visionPlayerPresentation = "Vista giocatori"
    override val visionMasterPresentation = "Vista master"
    override val visionPresentationAll = "Tutto"
    override val visionPresentationBlack = "Memoria + nero"
    override val visionPresentationDim = "Memoria + penombra"
    override val visionRadius = "Raggio"
    override val visionBlind = "Cieco"
    override val forgetExplored = "Dimentica esplorato"
    override val forgetExploredHint = "Riporta al nero tutto cio' che il gruppo ricorda."
    override val visionUseMapRadius = "Raggio di mappa"
    override val visionPickCombatantHint = "Scegli un combattente sulla mappa per dargli un raggio suo."
    override fun visionOf(name: String) = "Vista di $name"
    override val brushSize = "Pennello"
    override val paintFloors = "Disegna pavimento"
    override val eraseFloors = "Cancella pavimento"
    override val fillFloors = "Riempi mappa"
    override val clearFloors = "Rimuovi pavimento"
    override val floorHint = "Ogni casella è calpestabile di base. Il Pavimento evidenzia le aree percorribili e, quando lo disegni, rimuove i muri sotto il pennello; Riempi mappa conserva i muri."
    override val addWalls = "Aggiungi muri"
    override val eraseWalls = "Cancella muri"
    override val clearWalls = "Rimuovi tutti i muri"
    override val wallHint = "Trascina sulle caselle: i muri bloccano movimento, attacchi e linea d’effetto anche quando lo strato è nascosto."
    override val pinMeasurement = "Appunta"
    override val clearMeasurement = "Pulisci misura"
    override val strokeWidth = "Spessore"
    override val colour = "Colore"
    override val writeLabel = "Scrivi cartiglio"
    override val labelHint = "Testo visibile sulla mappa"
    override val add = "Aggiungi"
    override val cone = "Cono"
    override val cube = "Cubo"
    override val cylinder = "Cilindro"
    override val emanation = "Emanazione"
    override val line = "Linea"
    override val sphere = "Sfera"
    override val stampMode = "Timbro"
    override val templateMode = "Area"
    override val flame = "Fiamma"
    override val light = "Luce"
    override val danger = "Pericolo"
    override val door = "Porta"
    override val treasure = "Tesoro"
    override val boardLockedHint = "Il Lucido è bloccato: sbloccalo da Strati per modificarlo."
    override val fogNeedsPlayerView = "Modifica la Nebbia qui; usa l’anteprima giocatori solo per controllare il risultato."
    override val illustrativeTemplate = "Sagoma illustrativa: non applica automaticamente le regole."
    override val deleteObject = "Elimina"
    override val smaller = "Riduci"
    override val bigger = "Ingrandisci"
    override val rotateLeft = "Ruota a sinistra"
    override val rotateRight = "Ruota a destra"
    override val editText = "Modifica testo"
    override val background = "Sfondo"
    override val grid = "Griglia"
    override val tokens = "Segnaposti"
    override val protectedLayer = "protetto"
    override val combatants = "Combattenti"
    override val sceneTokens = "Pedine di scena"
    override val createToken = "Crea pedina"
    override val editToken = "Modifica dettagli"
    override val tokenName = "Nome"
    override val tokenNameHint = "Es. Mimic, porta segreta, carro"
    override val tokenCategory = "Categoria"
    override val tokenSize = "Ingombro in caselle"
    override val showTokenName = "Mostra nome"
    override val visibleToPlayers = "Visibile ai giocatori"
    override val masterNotes = "Note master"
    override val masterNotesHint = "Informazioni private, mai mostrate sulla mappa"
    override val createAndPlace = "Crea e posiziona"
    override val cancelPlacement = "Annulla posa"
    override val tokenPlacementHint = "Tocca una casella della mappa per posizionare la pedina."
    override val tokenVisualOnlyHint = "Pedina di scena: non possiede PF, turno o regole. Per un vero combattente usa il roster."
    override val tokenImageHint = "L’immagine è facoltativa; senza file viene usato il medaglione Onfall."
    override val lootable = "Raccoglibile"
    override val lootSettingsHint = "Quando viene raccolta, la pedina entra nell’inventario del PG scelto e lascia la mappa."
    override val lootInventoryCategory = "Categoria inventario"
    override val lootQuantity = "Quantità"
    override val lootDescription = "Descrizione dell’oggetto"
    override val lootDescriptionHint = "Informazione visibile nell’inventario; le note master restano private"
    override val collectLoot = "Raccogli"
    override val collectLootHint = "Scegli il personaggio che riceve l’oggetto."
    override val noLootCollectors = "Nessun PG della squadra possiede una scheda locale."
    override val lootTransferTokenMissing = "La pedina non esiste più."
    override val lootTransferNotLootable = "Questa pedina non è raccoglibile."
    override val lootTransferCollectorMissing = "Il personaggio scelto non è più disponibile."
    override val lootTransferSaveFailed = "Impossibile salvare l’inventario: la pedina è rimasta sulla mappa."
    override val lootTransferBoardChanged = "Il tavolo è cambiato durante il trasferimento; riprova."
    override fun sceneTokenAccessibility(
        name: String,
        category: String,
        collectible: Boolean,
        quantity: Int,
    ) = buildString {
        append(name).append(", ").append(category)
        if (collectible) append(", raccoglibile, quantità ").append(quantity)
    }
    override val categoryCharacter = "Personaggio"
    override val categoryAlly = "Alleato"
    override val categoryNpc = "PNG"
    override val categoryMonster = "Mostro"
    override val categoryObject = "Oggetto"
    override val categoryTrap = "Trappola"
    override val categoryHazard = "Pericolo"
    override val categoryTerrain = "Terreno"
    override val categoryLoot = "Bottino"
    override val categoryVehicle = "Veicolo"
    override val categoryMarker = "Marcatore"
    override val categoryOther = "Altro"
}

object BoardStringsEn : BoardStrings {
    override val tools = "Tools"
    override val table = "Table"
    override val edit = "Edit Board"
    override val hand = "Hand"
    override val measure = "Measure"
    override val ink = "Ink"
    override val template = "Template"
    override val label = "Label"
    override val ping = "Ping"
    override val fog = "Fog"
    override val floor = "Floor"
    override val wall = "Walls"
    override val layers = "Layers"
    override val eraser = "Eraser"
    override val token = "Token"
    override val pin = "Pin toolbox"
    override val unpin = "Unpin toolbox"
    override val undo = "Undo Board"
    override val redo = "Redo Board"
    override val lock = "Lock Board"
    override val unlock = "Unlock Board"
    override val annotations = "Annotations"
    override val stamps = "Stamps"
    override val playerPreview = "Player preview"
    override val coverFog = "Cover"
    override val revealFog = "Reveal"
    override val coverAllFog = "Cover all"
    override val revealAllFog = "Reveal all"
    override val fogPaintHint = "Drag across the map to cover or reveal cells. Player preview shows the final result."
    override val visionPainted = "Painted"
    override val visionDynamic = "Dynamic sight"
    override val visionDynamicHint =
        "Walls stop sight and explored stays in memory. Pick the GM and player rendering " +
            "separately; players always look through the party's eyes."
    override val visionLens = "GM eyes"
    override val visionLensTurn = "Turn"
    override val visionLensParty = "Party"
    override val visionPlayerPresentation = "Player view"
    override val visionMasterPresentation = "GM view"
    override val visionPresentationAll = "All"
    override val visionPresentationBlack = "Memory + black"
    override val visionPresentationDim = "Memory + half-light"
    override val visionRadius = "Radius"
    override val visionBlind = "Blind"
    override val forgetExplored = "Forget explored"
    override val forgetExploredHint = "Turns everything the party remembers back to black."
    override val visionUseMapRadius = "Map radius"
    override val visionPickCombatantHint = "Pick a combatant on the map to give them their own radius."
    override fun visionOf(name: String) = "$name's sight"
    override val brushSize = "Brush"
    override val paintFloors = "Paint floor"
    override val eraseFloors = "Erase floor"
    override val fillFloors = "Fill map"
    override val clearFloors = "Remove floor"
    override val floorHint = "Every cell is walkable by default. Floor highlights walkable areas and clears walls beneath the brush; Fill map preserves walls."
    override val addWalls = "Add walls"
    override val eraseWalls = "Erase walls"
    override val clearWalls = "Remove all walls"
    override val wallHint = "Drag across cells: walls block movement, attacks, and line of effect even when their layer is hidden."
    override val pinMeasurement = "Pin"
    override val clearMeasurement = "Clear measure"
    override val strokeWidth = "Width"
    override val colour = "Colour"
    override val writeLabel = "Write label"
    override val labelHint = "Text shown on the map"
    override val add = "Add"
    override val cone = "Cone"
    override val cube = "Cube"
    override val cylinder = "Cylinder"
    override val emanation = "Emanation"
    override val line = "Line"
    override val sphere = "Sphere"
    override val stampMode = "Stamp"
    override val templateMode = "Area"
    override val flame = "Flame"
    override val light = "Light"
    override val danger = "Danger"
    override val door = "Door"
    override val treasure = "Treasure"
    override val boardLockedHint = "The Board is locked. Unlock it in Layers to edit it."
    override val fogNeedsPlayerView = "Edit Fog here; use player preview only to check the final result."
    override val illustrativeTemplate = "Illustrative template: it does not apply rules automatically."
    override val deleteObject = "Delete"
    override val smaller = "Smaller"
    override val bigger = "Larger"
    override val rotateLeft = "Rotate left"
    override val rotateRight = "Rotate right"
    override val editText = "Edit text"
    override val background = "Background"
    override val grid = "Grid"
    override val tokens = "Tokens"
    override val protectedLayer = "protected"
    override val combatants = "Combatants"
    override val sceneTokens = "Scene tokens"
    override val createToken = "Create token"
    override val editToken = "Edit details"
    override val tokenName = "Name"
    override val tokenNameHint = "E.g. Mimic, secret door, wagon"
    override val tokenCategory = "Category"
    override val tokenSize = "Footprint in squares"
    override val showTokenName = "Show name"
    override val visibleToPlayers = "Visible to players"
    override val masterNotes = "GM notes"
    override val masterNotesHint = "Private information, never drawn on the map"
    override val createAndPlace = "Create and place"
    override val cancelPlacement = "Cancel placement"
    override val tokenPlacementHint = "Tap a map square to place the token."
    override val tokenVisualOnlyHint = "Scene token: it has no HP, turn, or rules. Use the roster for a real combatant."
    override val tokenImageHint = "The image is optional; without a file Onfall uses its token medallion."
    override val lootable = "Collectible"
    override val lootSettingsHint = "When collected, the token enters the chosen character’s inventory and leaves the map."
    override val lootInventoryCategory = "Inventory category"
    override val lootQuantity = "Quantity"
    override val lootDescription = "Item description"
    override val lootDescriptionHint = "Shown in the inventory; GM notes always remain private"
    override val collectLoot = "Collect"
    override val collectLootHint = "Choose the character who receives the item."
    override val noLootCollectors = "No party character has a local character sheet."
    override val lootTransferTokenMissing = "The token no longer exists."
    override val lootTransferNotLootable = "This token is not collectible."
    override val lootTransferCollectorMissing = "The selected character is no longer available."
    override val lootTransferSaveFailed = "The inventory could not be saved; the token stayed on the map."
    override val lootTransferBoardChanged = "The table changed during the transfer; try again."
    override fun sceneTokenAccessibility(
        name: String,
        category: String,
        collectible: Boolean,
        quantity: Int,
    ) = buildString {
        append(name).append(", ").append(category)
        if (collectible) append(", collectible, quantity ").append(quantity)
    }
    override val categoryCharacter = "Character"
    override val categoryAlly = "Ally"
    override val categoryNpc = "NPC"
    override val categoryMonster = "Monster"
    override val categoryObject = "Object"
    override val categoryTrap = "Trap"
    override val categoryHazard = "Hazard"
    override val categoryTerrain = "Terrain"
    override val categoryLoot = "Loot"
    override val categoryVehicle = "Vehicle"
    override val categoryMarker = "Marker"
    override val categoryOther = "Other"
}
