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
    override val fogNeedsPlayerView = "La Nebbia si usa insieme all’anteprima giocatori."
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
    override val fogNeedsPlayerView = "Fog is available together with the player preview."
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
