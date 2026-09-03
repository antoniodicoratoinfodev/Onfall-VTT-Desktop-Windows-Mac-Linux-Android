# ADR 0001 — Authoring visuale lossless delle regole

Stato: accettata e implementata nella fondazione, 2026-09-02.

## Contesto

Il runtime usa formule testuali sicure dentro `RuleEntity`. Un editor più semplice deve poterle
mostrare e modificare come blocchi senza creare un secondo motore, cambiare silenziosamente gli hash
o perdere attributi che una versione futura o un plugin conosce.

I dati utili soltanto a riaprire l'esperienza visuale devono inoltre restare sincronizzati con la
bozza dopo crash, backup e recovery.

## Decisione

1. `RuleFormula` espone lo stesso AST immutabile usato dal parser e dal valutatore. La compilazione
   da AST rientra nel parser testuale e nei suoi budget; non esiste un interprete visuale separato.
2. La sorgente originale resta autorevole finché l'utente non modifica davvero l'albero. Soltanto un
   albero dirty viene serializzato nella forma canonica.
3. Il dominio UI indipendente vive in `engine:rules-authoring`, che dipende da `rules-model` ma non da
   Compose né dalla persistence.
4. Gli attributi non riconosciuti e i nodi non modificabili visualmente sono conservati e marcati
   come protetti. `EXACT`, `PARTIAL` ed `EXPERT_ONLY` sono risultati espliciti della proiezione.
5. `RulesetLibrary` schema 3 contiene `RulesetAuthoringState` nella stessa scrittura atomica delle
   bozze. Gli schemi 1 e 2 vengono letti con stato vuoto. Revisioni e bundle giocabili non includono
   obbligatoriamente questi metadati.
6. I metadati non partecipano ad alcun hash runtime o documento di una revisione. Un hash della
   singola entità serve soltanto a capire se una proiezione UI è ancora valida.

## Conseguenze

- Aprire, cambiare modalità e salvare senza edit conserva sorgenti, attributi e semantica.
- Una vecchia regola avanzata resta eseguibile e modificabile in Esperto anche senza recipe.
- Le applicazioni precedenti non possono riscrivere una libreria schema 3: il controllo versione
  esistente la rifiuta prima di qualsiasi salvataggio.
- Ogni nuova famiglia guidata deve fornire proiezione forward/reverse e test lossless; non può
  concatenare formule nella UI.

## Rollback

Il runtime può ignorare completamente `rules-authoring`: revisioni pubblicate e snapshot rimangono
autosufficienti. Per tornare a un writer schema 2 occorre esportare o migrare intenzionalmente la
libreria eliminando `authoring`; non è consentito farlo implicitamente, perché perderebbe layout,
gruppi ed esempi dell'autore. I backup atomici restano la via di recovery per una scrittura fallita.
