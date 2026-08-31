# Piano completo per regolamenti modulari e homebrew

Stato: piano vivo; runtime modulare esteso implementato e verificato sul codice il 31 agosto 2026

Ambito: desktop e Android, offline-first, compatibilità con salvataggi esistenti

Obiettivo di riferimento: rendere Onfall utilizzabile con SRD 5.2.1, varianti homebrew,
edizioni D20 differenti come una 3.5-like e regolamenti inventati, senza legare il motore a
nomi o formule specifiche dello SRD.

## 0. Stato verificato dell'implementazione

È ora operativo, non soltanto progettato, un percorso completo dal dato alla sessione:

- `rules-model` e `rules-persistence` conservano standard read-only, fork, patch, bozze e revisioni
  pubblicate con hash canonico e backup atomici;
- Regole è una destinazione principale con ricerca, filtri Standard/Homebrew, editor e
  pubblicazione validata;
- **Nuovo regolamento vuoto** crea un progetto da una fondazione generica read-only con zero entità:
  non eredita dadi, classi, caratteristiche, skill, danni, condizioni, armi, background o licenze SRD;
- la proiezione personaggio riconosce la genealogia SRD dalle entità e non dal nome: un fork SRD
  conserva il proprio pack, mentre un regolamento autonomo riceve soltanto stat, skill, classi ed
  elementi dichiarati; anche i pool di scelta usano l'ID del pack autonomo;
- ogni nuova sessione sceglie una revisione pubblicata e incorpora binding, entità eseguibili e
  stato generico; una sessione aperta può cambiarla con pausa, migrazione conservativa, Undo e audit;
- critico, 1 naturale, Sfinimento e bonus di competenza sono parametri runtime univoci: modificarli
  dai controlli o dagli attributi aggiorna la stessa fonte;
- ID di classe, caratteristica e skill sono aperti: una classe homebrew compare nella creazione
  guidata, avanza realmente, concede PF/competenze/privilegi ed è vincolata alla revisione usata;
  caratteristiche e skill aggiunte entrano nella scheda e usano le formule della revisione;
- il compilatore indipendente dall'edizione interpreta formule deterministiche, alias e riferimenti,
  tabelle tipizzate, curve PE, randomizzatori d20/dadi/pool/percentile/tabella, risorse, trigger,
  action economy, azioni atomiche, condizioni e tipi di danno con ID aperti;
- le dipendenze fra risorse e budget di turno sono ordinate dal grafo dei riferimenti e i cicli
  bloccano la pubblicazione; costi ripetuti o espressi tramite alias vengono sommati sullo stesso
  pool prima di eseguire l'azione, impedendo spese parziali o oltre il disponibile;
- `VALUE` dichiara valori `NUMBER`, `BOOLEAN`, `TEXT` o `REFERENCE`, con default, dominio ammesso e
  mutabilità; possono essere letti, corretti o impostati da effetti senza convertirli in testo libero;
- un `MODIFIER` generico collega obbligatoriamente regola proprietaria e bersaglio e può applicare
  modifiche statiche, cambiare numeri/risorse/condizioni oppure impostare un valore tipizzato; i
  proprietari possono essere attivi per default o attivati durante la sessione, con audit e Undo;
- caratteristiche/difese/skill numeriche, valori tipizzati, quantità e massimi delle risorse,
  accumuli di condizione e budget di turno hanno correzioni live validate, persistite, registrate e
  annullabili; un effetto che viene saturato allo stesso valore non produce falsi eventi di cambio;
- ogni stato generico è indirizzabile con `(tipo scope, ID scope, ID regola)`: Sessione, Attore,
  Oggetto, Scena e Campagna possono avere istanze indipendenti della stessa caratteristica, valore,
  risorsa, condizione, owner attivo e budget; Regole consente di scegliere lo scope prima di agire;
- gli scope sono inclusi nello snapshot esatto, migrati conservando lo speso, persistiti nello schema
  JSON 4, mostrati nell'audit e ripristinati insieme al resto del comando tramite Undo;
- un effetto dinamico dichiara `recipient=SELF|TARGET|SESSION`: azioni ed eventi possono spendere
  nello scope sorgente, modificare uno scope bersaglio distinto e aggiornare lo stato condiviso in
  un'unica transazione atomica; il registro conserva entrambi gli indirizzi;
- riferimenti mancanti (inclusi condizioni e budget citati nelle formule), tipi incompatibili,
  valori fuori dominio e cicli fra formule, valori, pool e modificatori statici bloccano la
  pubblicazione; il repository ripete la compilazione anche per pubblicazioni/import eseguiti senza UI;
- classi oltre il livello 20 estendono il limite di progressione; le tabelle PE possono essere
  complete o parziali: sono vincolanti nelle righe dichiarate e lasciano avanzamento manuale oltre
  l'ultima soglia, senza inventare valori; una curva eseguibile deve essere unica, cumulativa,
  intera, monotona e coerente con l'intervallo di livelli dichiarato;
- ciascuna scheda e ciascuna proiezione del roster usa il proprio regolamento, anche quando
  nell'archivio convivono personaggi di revisioni diverse;
- se la revisione esatta di una scheda non è installata, nomi e snapshot restano leggibili ma
  catalogo, progressione e ricalcolo degli effetti vengono bloccati: lo SRD non è mai usato come
  sostituto implicito;
- la sincronizzazione combattimento→scheda viene rifiutata quando gli hash di revisione non
  coincidono, evitando contaminazioni silenziose;
- schede e sessioni precedenti restano leggibili tramite i default legacy;
- una sessione distingue esplicitamente «snapshot configurato ma senza entità» da «salvataggio
  legacy senza snapshot» (`configured`, schema JSON 5): anche un regolamento interamente manuale e
  vuoto mantiene binding, tassonomie esatte e possibilità di ricevere regole in una revisione futura;
- selettori di danni e condizioni non reinseriscono più le enum D&D quando la revisione eseguibile
  dichiara una lista vuota; il fallback storico esiste soltanto per i salvataggi legacy;
- i controlli tattici legacy per prove d20, PF, morte e Sfinimento sono capability-aware: restano
  disponibili per SRD e salvataggi storici, ma non compaiono in un regolamento autonomo che non
  dichiara le corrispondenti entità; l'iniziativa iniziale usa il valore statico senza simulare un d20;
- test sintetici 3.5-like e non-D20 verificano formule, pool di d6, budget di turno personalizzati,
  tabelle PE, valori testuali, risorse, trigger concatenati, condizioni, tassonomie dinamiche e
  transazioni source→target→sessione senza codice specifico dell'edizione; test di migrazione
  verificano anche alias di risorsa, speso conservato, stack ricondotti ai nuovi massimi e valori
  incompatibili riportati al nuovo default.
- `StatePersistencePolicy` rende eseguibili durata, proprietario, evento di reset e politica di
  sincronizzazione; i confini azione/turno/scena/incontro/sessione/campagna scadono lo stato in modo
  atomico e il planner separa applicazioni automatiche, proposte, conflitti e modifiche locali;
- `HEALTH_MODEL`, `MOVEMENT`, `SHEET_SECTION` e `SCENE_PROCEDURE` sono compilati, validati e
  modificabili con editor strutturati; la scheda materializza sezioni e campi linkati, li persiste con
  l'hash esatto e li ritraduce senza perdere i valori;
- `GameSession` è un aggregate generale persistibile senza combattimento, PF, CA, iniziativa o d20
  obbligatori: gestisce scene, partecipanti, fasi, azioni, trigger, RNG, audit, Undo e cambio revisione;
- una singola azione può applicare effetti `TARGET` a più scope pagando i costi una sola volta; il
  pannello di gioco espone azioni, risorse, salute e condizioni della revisione attiva;
- `BoardGeometry` esegue distanze e ingombri quadrati, diagonale alternata 3.5-like, euclidea,
  esagonale, gridless e teatro della mente; la CPU usa un profilo conservativo esplicito e degrada a
  controllo manuale quando topologia o revisione non sono supportate;
- revisioni pubblicate homebrew si importano ed esportano dal catalogo Regole tramite il formato
  portabile con hash, limiti, compilazione e scrittura atomica già applicati dal repository.

Il precedente elenco di lacune — ID aperti, formule, PE, risorse/trigger, action economy, danni e
condizioni dinamici — è quindi superato. “Universale” continua però a non significare che qualunque
regola immaginabile venga automatizzata senza una primitiva. Restano confini espliciti:

- l'automazione tattica legacy di attacchi, PF, morte, concentrazione, slot, CPU e alcune procedure
  continua ad avere semantica D20/SRD; un regolamento diverso usa le primitive generiche o la
  risoluzione assistita/manuale per le parti non ancora estratte;
- la creazione personaggio guidata è ancora un adattatore con forma D&D (classe, livelli, dado vita,
  PF e competenze), scelto intenzionalmente come esperienza principale. Classi, stat e skill hanno ID
  aperti; i giochi classless usano le sezioni `SHEET_SECTION` generate e la `GameSession` generica.
  Restano SRD-specifici i riquadri storici della scheda 2024 e alcune procedure guidate avanzate;
- policy di stato e fan-out multi-target sono operative. Resta da coordinare in un unico journal una
  sincronizzazione che modifichi contemporaneamente più archivi fisici (regole, workspace, board e
  più schede); il planner attuale è deliberatamente read-only fuori dalla sessione;
- le geometrie alternative sono modellate, misurabili e capability-aware. Il renderer tattico e il
  pathfinding completi restano l'adattatore quadrato SRD: esagoni/gridless vengono mostrati come non
  automatizzati finché non esiste un renderer/pathfinder dedicato;
- formule ed effetti sono volutamente un linguaggio sicuro e finito: non viene eseguito codice
  homebrew arbitrario. Ciò che non ha ancora una primitiva resta sempre rappresentabile e giocabile
  in modalità assistita o manuale.

### Contratto eseguibile corrente

La pubblicazione interpreta e valida questi attributi, che quindi non sono semplice testo di
catalogo:

| Regola | Attributi eseguibili principali |
|---|---|
| Classe | `classId`, `hitDieSides`, `fixedHitPointsPerLevel`, `primaryAbilities`, `multiclassPrerequisiteGroups`, `savingThrowProficiencies`, `maximumLevel`, `skillChoiceCount`, `spellcastingKind`, `spellcastingAbility`, `subclassIds`, `levelFeatureIds`, addestramenti in armi/armature e dotazione testuale |
| Caratteristica/difesa/skill | ID aperto, caratteristica collegata, default/derivazione/minimo/massimo/modificatore, arrotondamento e bonus di addestramento |
| Valore | `valueType`, `defaultValue`, `allowedValues`, `mutable`, `dimension`, `canonicalUnit`; il tipo può essere numero, booleano, testo o riferimento a una regola |
| Modificatore personaggio | `ownerRef`, `target`, `amount`, `condition`, `minimumLevel`, `group` |
| Modificatore universale | `ownerRef`, `targetRef`, `application`, `recipient=SELF|TARGET|SESSION`, `operation`, `valueFormula` o `valueLiteral`, `conditionFormula`, `priority`, `minimumLevel`, `group` |
| Privilegio/talento/incantesimo/azione | tipo di elemento, attivazione, prerequisito, eleggibilità di classe, dettagli di incantesimo, costo/risorsa e incantesimi concessi |
| Tabella/progressione | tipo del valore, lookup esatto/floor/ceiling/nearest, righe e `experienceTableRef`, livello minimo/massimo e politica PE |
| Randomizzatore | modalità, formule per quantità/facce/soglia, aggregazione e tabella di esito opzionale |
| Risorsa/track | formule di massimo, iniziale e recupero più evento di recupero arbitrario |
| Action economy/azione | budget nominati e formula, costi su budget o risorse, condizione ed effetti collegati |
| Trigger | evento arbitrario, condizione, priorità, limite di esecuzione ed effetti collegati |
| Danno/condizione | ID aperto; per la condizione anche massimo, stacking, separazione per fonte ed evento di rimozione |
| Salute | risorsa primaria, buffer, condizioni a zero/morte, valori negativi e stato a zero |
| Movimento | topologia, diagonale, unità per cella, unità canonica, elevazione e occupazione |
| Sezione scheda | campi linkati, ordine, colonne, layout e formula di visibilità |
| Procedura scena | fasi, azioni/tracker collegati e requisiti di iniziativa/board |
| Policy di stato | `lifetime`, `owner`, `syncPolicy`, `resetEvent` per ogni entità stateful |

`ownerRef` accetta l'ID della regola che concede un effetto. `activeByDefault` oppure il controllo
live della sessione decide se quel proprietario è attivo; per una classe, `minimumLevel` indica la
riga di progressione dalla quale l'effetto personaggio resta attivo. `levelFeatureIds` usa righe
`livello:id1,id2;livello:id3`; ogni riferimento deve esistere ed essere abilitato. I gruppi di
prerequisiti multiclasse separano gli AND con `;` e le alternative OR con `,`. Ogni attributo
`...EntityRef`, `...EntityRefs` e la lista generica `links` attraversa la stessa validazione degli ID.
Al runtime l'indirizzo completo aggiunge un `RuleScope`: omettendolo si usa sempre `SESSION`, così
API e salvataggi precedenti mantengono esattamente la semantica storica.

## 1. Decisione di prodotto

La soluzione non deve essere una schermata di impostazioni con una serie di interruttori SRD.
Deve essere un sistema di regolamenti versionati, composto da moduli e interpretato dal motore.

Il modello consigliato è:

- lo **SRD 5.2.1 standard** resta incluso, verificato e sempre in sola lettura;
- ogni modifica allo standard crea una **derivazione homebrew**, senza alterare l'originale;
- un regolamento homebrew è un progetto modificabile con una cronologia di revisioni;
- una revisione pubblicata è immutabile e può essere selezionata da personaggi e sessioni;
- ogni sessione conserva la revisione esatta usata, non il generico nome del regolamento;
- cambiare le regole di una sessione già iniziata è consentito tramite una migrazione esplicita,
  verificabile e reversibile;
- le parti non automatizzabili restano giocabili tramite una modalità assistita o manuale;
- formule ed effetti automatizzati usano un linguaggio dati sicuro e deterministico, non codice
  arbitrario importato.

Questa distinzione è essenziale: “poter giocare qualunque regolamento” è un obiettivo realistico
se significa che l'app può rappresentarne i dati, tenere traccia dello stato e lasciare al tavolo
la risoluzione manuale delle eccezioni. Nessun insieme finito di controlli può automatizzare ogni
regola immaginabile. L'automazione completa crescerà aggiungendo primitive generiche, mentre il
fallback manuale garantirà che una regola nuova non renda mai la sessione ingiocabile.

## 2. Risultato percepito dall'utente

La navigazione principale diventa, in quest'ordine:

1. Battaglia
2. Partita
3. Compendio
4. **Regole**
5. Impostazioni

La nuova sezione Regole permette di:

- consultare tutte le regole standard;
- filtrare per Standard, Homebrew, Modificate, Importate, Disabilitate e In uso;
- cercare per nome, descrizione, identificatore, tag, classe o categoria;
- creare un regolamento partendo dallo SRD oppure da una fondazione generica realmente vuota;
- duplicare una singola regola standard come variante homebrew;
- modificare caratteristiche, formule, modificatori, classi, progressione, azioni, condizioni,
  danni, magia, riposi, movimento, equipaggiamento e altri elementi descritti più avanti;
- vedere la differenza rispetto alla regola di origine;
- validare e provare una regola prima di pubblicarla;
- pubblicare una revisione immutabile;
- importare ed esportare pacchetti portabili;
- sapere quali personaggi e sessioni usano una revisione.

Ogni nuova sessione include un passaggio **Regolamento**. Le sessioni esistenti mostrano sempre il
regolamento attivo e offrono **Modifica regolamento della sessione**.

## 3. Requisiti non negoziabili

### 3.1 Immutabilità dello standard

- Le definizioni incluse non vengono mai sovrascritte.
- Il comando Modifica su una regola standard equivale a **Crea variante**.
- La variante conserva `derivedFrom`, revisione e hash dell'originale.
- È sempre possibile confrontare la variante con lo standard o rimuovere un singolo override.
- Un aggiornamento futuro dello SRD non modifica automaticamente alcuna sessione o homebrew.

### 3.2 Visibilità dell'homebrew

- Ogni elemento homebrew compare nella sezione Regole subito dopo il primo salvataggio della bozza.
- Anche una variante creata soltanto per una sessione compare, con il badge **Solo sessione**.
- Le voci archiviate restano trovabili attivando il filtro Archiviate.
- Una voce referenziata non può essere eliminata fisicamente: può soltanto essere archiviata o
  disabilitata in una nuova revisione.

### 3.3 Riproducibilità delle sessioni

- Una sessione è legata a `rulesetId + revisionId + canonicalHash`.
- Il caricamento non risolve mai “l'ultima versione disponibile”.
- Le modifiche globali a una bozza non cambiano una sessione aperta.
- Ogni passaggio a una nuova revisione produce un evento di audit con vecchio hash, nuovo hash e
  scelte di migrazione.
- Il passato del registro non viene ricalcolato usando regole nuove.

### 3.4 Portabilità e offline-first

- Tutto ciò che serve a continuare la partita deve essere disponibile senza rete.
- Le revisioni sono conservate in un archivio locale content-addressed e non vengono rimosse finché
  sono referenziate.
- L'esportazione di una sessione include la revisione completa necessaria; non presuppone che il
  destinatario possieda lo stesso homebrew.
- Scritture atomiche, backup e recupero bozze seguono il modello già usato dall'app.

### 3.5 Nessuna semantica dedotta dal testo

- Nome e descrizione sono presentazione.
- Formule, bersagli, costi, trigger ed effetti sono dati strutturati.
- Cambiare lingua non cambia mai la risoluzione.
- Una regola solo testuale è marcata Manuale; il motore non tenta di interpretarla.

### 3.6 Contratto realistico di supporto

“Supportato” deve avere un significato verificabile per evitare di promettere che ogni regolamento
immaginabile sarà automaticamente compreso dal motore. Onfall dichiara quattro livelli per ogni
meccanica e per ogni pack:

1. **Rappresentabile**: testo, riferimenti, campi, tracker e stato possono essere conservati;
2. **Giocabile manualmente**: la sessione offre strumenti generici e registra le decisioni del tavolo;
3. **Assistito**: il runtime prepara formule, scelte o conseguenze, ma richiede una conferma;
4. **Automatizzato**: il runtime valida e applica l'intero flusso in modo deterministico.

Un regolamento può quindi essere realmente giocabile anche se alcune regole sono Manuali, ma la UI
deve mostrare prima della sessione quali parti non sono automatizzate. Un pack non può dichiararsi
“completamente supportato” soltanto perché i suoi testi sono importabili.

### 3.7 Un solo significato per ogni sessione

- Ogni sessione risolve un solo snapshot immutabile, comprensivo degli override locali.
- Default dell'app, campagna e personaggio aiutano la selezione, ma non modificano mai in silenzio
  una sessione già creata.
- Anche la versione semantica dell'interprete fa parte del binding: lo stesso JSON non può cambiare
  significato dopo un aggiornamento dell'app.
- Stato della sessione, board, presentazione, recovery e sincronizzazioni verso le schede devono
  attraversare lo stesso confine transazionale quando cambia il regolamento.
- Le revisioni danneggiate, non verificabili o troppo nuove non vengono eseguite: si aprono in
  quarantena, in sola lettura o in modalità Manuale secondo il caso.

## 4. Audit iniziale dell'architettura (baseline storica)

Questa sezione fotografa la base precedente all'implementazione e viene mantenuta per spiegare le
decisioni di migrazione. Non è un elenco dello stato corrente: binding/snapshot di sessione, scelta
nel wizard, ID aperti principali, runtime generico, scope keyed-by-ID e controlli Regole sono stati
introdotti. La tabella seguente è volutamente storica: geometria, policy e `GameSession` indicate
come assenti nella baseline hanno ora un primo contratto operativo descritto nella sezione 0.

| Area attuale | Fondamenta riutilizzabile | Vincolo da rimuovere |
|---|---|---|
| `domain.rules` | `RulesetProfile`, manifest e versioni dei content pack | È usato quasi soltanto dal modello Campaign e non dalle sessioni aperte dalla UI |
| content pack | Lo SRD è già in un modulo separato e bilingue | `shared-ui` importa direttamente classi concrete `Srd...` |
| capacità | Le voci SRD sono read-only e duplicabili come personalizzate | Tipi, bersagli, costi ed effetti sono enum e campi SRD-specifici |
| creazione guidata | Classi e livelli sono dati del pack | Gli ID delle dodici classi, le sei caratteristiche e molte scelte sono enum chiusi |
| scheda | Conserva progressione e risorse strutturate | Livelli 1–20, nove slot, CA, morte, competenza e formule sono incorporati |
| motore | Stato immutabile, comandi, audit, RNG deterministico e Undo sono ottimi punti di estensione | d20, critico 1/20, vantaggio, PF, morte, Exhaustion, concentrazione e action economy sono rigidi |
| sessioni | Fotografano i combattenti e sono salvate atomicamente | Conservano stringhe di versione, ma non una revisione di regole eseguibile |
| workspace | Più sessioni aperte, autosave e crash recovery già esistono | Il recovery serializza combattimento, presentazione e board separatamente e non conosce una transazione di cambio ruleset |
| sincronizzazione | Le risorse e alcune modifiche del combattente possono tornare nella scheda | Il collegamento usa gli ID correnti senza verificare che scheda e sessione condividano la stessa revisione |
| mappa e board | Posizioni, fog, muri, pavimento, visione e Undo sono persistiti | Coordinate, ingombri e misure assumono una griglia quadrata in caselle |
| campagna | Esiste un aggregate con `RulesetProfile` e manifest bloccato | Non è ancora il contenitore usato dal workspace e non governa le sessioni create dalla UI |
| procedura Partita | Ha passaggi espliciti e stato persistibile | Non chiede quale regolamento usare |
| CPU | Ha comportamento deterministico e testato | Assume le capacità e l'economia di azioni correnti |

Legami concreti da eliminare progressivamente:

- `CharacterClassId`, `Ability`, `Skill`, `DamageType`, `ConditionType`, `SaveAbility`,
  `ActivationCost` e `TurnResource` sono insiemi chiusi;
- `ExperienceProgression` incorpora soglie, massimo livello e bonus di competenza;
- `TurnBudget` incorpora Azione, Azione bonus, Reazione, interazione con oggetto e regole speciali;
- `CombatSession` incorpora d20, 1/20 naturali, concentrazione, morte, Exhaustion e slot;
- `CharacterSheet` incorpora formule di CA, iniziativa, percezione, attacchi extra e regole di armatura;
- `SheetViewModel` sceglie sempre `srdPack` e `guidedCharacterService` globali;
- `EncounterBuilderViewModel.startedSession()` crea una sessione con i default SRD;
- il salvataggio sessione non contiene un binding completo al regolamento;
- `AppRoot` risolve direttamente forme SRD e livelli da Druido.
- `SessionWorkspace` recupera separatamente combattimento, board e presentazione: un cambio di
  revisione deve fotografarli e ripristinarli come un'unica unità logica;
- `RulesetProfile` include oggi `languageTag` e `measurementSystem`: lingua UI e preferenza di
  visualizzazione vanno separate dalle unità canoniche che hanno davvero semantica di gioco;
- `RosterViewModel.applyCombatEdit/applyCombatResources` può scrivere lo stato della battaglia nelle
  schede: con revisioni differenti servono compatibilità, mapping e ambito di persistenza espliciti;
- `MapGrid`, `GridPosition`, gli ingombri `squaresPerSide` e molte operazioni di visione/misura sono
  quadrati-specifici;
- template di sessione, progressione e creazione guidata importano ancora concetti concreti SRD,
  quindi rimuovere il solo singleton del pack non basta.

La migrazione non deve sostituire tutto in un solo passaggio. Ogni fase sotto conserva un adattatore
legacy e aggiunge test di equivalenza prima di spostare una regola dal codice ai dati.

## 5. Vocabolario del nuovo dominio

### Progetto di regolamento (`RulesetProject`)

Identità stabile e visibile all'utente: nome, descrizione, icona, autore, tag, base di derivazione,
bozza corrente e revisioni. È il contenitore modificabile.

### Bozza (`RulesetDraft`)

Stato modificabile non usato direttamente dal motore. Può essere incompleto, contenere errori e
avere modifiche non salvate. Le sessioni non cambiano mentre la bozza viene editata.

### Revisione (`RulesetRevision`)

Versione immutabile e validata del progetto. Contiene i moduli, gli override e i metadati necessari
a ricostruire esattamente il regolamento.

### Snapshot risolto (`ResolvedRulesetSnapshot`)

Risultato della composizione di base, moduli e override. Ha un hash canonico, non contiene conflitti
e può essere compilato dal runtime.

### Modulo (`RuleModule`)

Gruppo attivabile e versionato: per esempio “Combattimento base”, “Magia”, “Regole 3.5-like”,
“Ferite alternative” o “Classi della campagna”. Dichiara dipendenze, incompatibilità e priorità.

### Entità di regola (`RuleEntity`)

Una definizione indirizzabile: caratteristica, tiro, modificatore, condizione, classe, privilegio,
incantesimo, risorsa, tipo di danno, azione, tabella o regola testuale.

### Patch (`RulePatch`)

Operazione copy-on-write rispetto a una base: aggiunge, sovrascrive campi tipizzati, estende una
lista, sostituisce un riferimento o disabilita una voce. Conserva l'hash della base attesa, così un
rebase può rilevare conflitti veri.

### Binding (`RulesetBinding`)

Riferimento esatto conservato da scheda, incontro o sessione: ID progetto, ID revisione, hash e
informazioni di fallback.

### Sessione di gioco (`GameSession`)

Aggregate generale che possiede binding del regolamento, partecipanti, scene, tempo, tracker
condivisi, board, presentazione e audit. Un combattimento è un componente opzionale della sessione,
non il contenitore di esplorazione, interazione sociale e downtime.

### Ambito e proprietà dello stato (`StatePersistencePolicy`)

Non è un singolo enum che confonde durata e proprietario. Dichiara almeno:

- `lifetime`: `ACTION`, `TURN`, `SCENE`, `ENCOUNTER`, `SESSION`, `CAMPAIGN` o `PERMANENT`;
- `owner`: istanza attore, personaggio, gruppo, sessione, campagna o pool del GM;
- `syncPolicy`: solo locale, proposta, automatica se compatibile o mai sincronizzabile.

Serve sia al reset sia a stabilire quale archivio è autorevole e se una modifica possa uscire dalla
sessione.

Stato corrente: `RuleScope` implementa la parte di indirizzamento con tipi `SESSION`, `ACTOR`,
`OBJECT`, `SCENE` e `CAMPAIGN` e ID aperti; ogni scope conserva un `RuleRuntimeState` indipendente.
La durata automatica e la politica di sincronizzazione fra sessione, scheda e campagna restano la
parte non ancora implementata di `StatePersistencePolicy`.

### Versione semantica (`RuntimeSemanticsVersion`)

Identifica il significato degli operatori della DSL, delle primitive di effetto e delle politiche di
arrotondamento. È distinta dalla versione del formato JSON e dalla versione commerciale dell'app.

## 6. Modello dati proposto

Le classi riportate qui descrivono i concetti, non impongono già la sintassi Kotlin definitiva.

### 6.1 Intestazione comune

Ogni entità ha almeno:

- `entityId`: ID stabile, namespaced e indipendente dalla lingua;
- `kind`: tipo strutturale;
- `origin`: `BUNDLED_STANDARD`, `HOMEBREW`, `IMPORTED` o `SESSION_LOCAL`;
- `derivedFrom`: riferimento opzionale alla voce di origine;
- `name` e `description`: testo localizzato con fallback dichiarato;
- `source`: autore, URL facoltativo, pagina, licenza e attribuzione;
- `tags`: ricerca e organizzazione;
- `automationLevel`: `FULL`, `ASSISTED`, `MANUAL`;
- `enabled`: presenza effettiva nella revisione;
- `payload`: struttura tipizzata coerente con `kind`.

Gli ID non devono contenere nomi tradotti. Formato consigliato:

`<authority>:<pack>:<kind>:<stable-key>`

Esempi:

- `onfall:srd521:class:fighter`
- `user:7d3…:condition:bleeding`
- `campaign:4a1…:stat:sanity`

### 6.2 Progetto, revisione e layer

Un progetto contiene:

- ID, nome, descrizione e autore;
- `baseRevisionRef` opzionale;
- elenco ordinato di moduli;
- bozza corrente;
- revisioni pubblicate;
- revisione predefinita;
- metadati di compatibilità e capacità;
- stato attivo o archiviato.

Una revisione contiene:

- numero leggibile, per esempio `1.2.0`, e un ID interno immutabile;
- revisione genitore e base;
- versione dello schema Onfall e del runtime richiesta;
- patch ordinate;
- traduzioni e asset;
- changelog;
- hash canonico;
- data di pubblicazione;
- report di validazione firmato dall'app che l'ha prodotta.

Le bozze possono dichiarare intervalli di compatibilità, ma la pubblicazione produce un lockfile con
hash esatto di base, moduli, traduzioni eseguibili e schema. La sessione usa soltanto il grafo
bloccato; non risolve dipendenze dalla rete o dall'“ultima versione” durante apertura e gioco.

Ordine di composizione:

1. fondazione del sistema;
2. revisione base;
3. moduli richiesti dalla base;
4. moduli aggiunti dall'utente, nell'ordine dichiarato;
5. override del progetto;
6. override locale della sessione.

Due patch sullo stesso campo non si risolvono silenziosamente in base all'ordine se nessuna dichiara
precedenza: il compilatore segnala un conflitto da risolvere.

### 6.3 Strutture tipizzate, non un unico dizionario libero

Serve un modello ibrido:

- intestazione e riferimenti sono generici;
- ogni famiglia importante ha un payload tipizzato e validabile;
- campi aggiuntivi possono essere dichiarati tramite uno schema estensioni;
- lo stato runtime usa valori keyed by ID, non enum compilati.

Famiglie iniziali:

- `StatDefinition`: caratteristica, difesa, punteggio derivato o contatore;
- `SkillDefinition`: caratteristica associata, addestramento e formula;
- `RollDefinition`: dadi, selezione, modificatore, confronto ed esiti;
- `RandomizerDefinition`: dadi, mazzi, sacchetti/token, tabelle o input manuale;
- `ModifierDefinition`: destinazione, operazione, condizione, stacking e priorità;
- `TrackDefinition`: orologio, stress, conseguenza, progresso o contatore segmentato;
- `ResourceDefinition`: massimo, spesa, recupero e visibilità;
- `ActionDefinition`: costo, fase, bersagli, prerequisiti ed effetti;
- `ConditionDefinition`: durata, stacking, immunità, modificatori e azioni vietate;
- `DamageTypeDefinition` e `DamageResponseDefinition`;
- `HealthModelDefinition`: danno, cura, zero, morte, stabilità e tracce alternative;
- `TurnStructureDefinition`: fasi, budget, reset e interrupt;
- `SceneProcedureDefinition`: fasi e mosse di combattimento, esplorazione, sociale o downtime;
- `MovementDefinition`: modi, unità, diagonali e terreno;
- `ProgressionDefinition`: livelli, XP, milestone e avanzamenti;
- `ClassDefinition`, `SubclassDefinition`, `AncestryDefinition`, `BackgroundDefinition`;
- `FeatureDefinition`, `FeatDefinition`, `SpellDefinition`, `ItemDefinition`;
- `ChoiceDefinition` e `PrerequisiteDefinition`;
- `SheetSectionDefinition` e `VisibilityDefinition`: editor, layout e accesso GM/giocatore;
- `TableDefinition`: progressioni e lookup versionati;
- `TextRuleDefinition`: regola consultabile, assistita o manuale.

Schede e pannelli usano uno schema UI dichiarativo con un set finito di widget accessibili
(campo, selezione, tracker, lista, tabella, pulsante-azione, testo e gruppo). Un pack importato non
può fornire Kotlin/Java, Compose o HTML eseguibile. Se richiede un renderer non disponibile, i dati
restano accessibili nel form generico e il pack dichiara quella vista come capability opzionale.

### 6.4 Stato del personaggio e del combattente

Lo stato futuro non deve avere un campo Java/Kotlin per ogni statistica possibile. Deve distinguere:

- valori inseriti dall'utente;
- valori derivati dalla revisione;
- override temporanei;
- risorse correnti;
- condizioni e modificatori attivi;
- riferimenti a classi, privilegi, oggetti e scelte;
- dati di presentazione.

Struttura concettuale:

- `baseValues: Map<StatId, RuleValue>`;
- `derivedValues`: calcolati, non autorevoli;
- `resources: Map<ResourceId, ResourceState>`;
- `features: Set<EntityRef>`;
- `conditions: List<ConditionInstance>`;
- `progressionHistory: List<AdvancementRecord>`;
- `customFields: Map<FieldId, RuleValue>`;
- `rulesetBinding`.

I campi molto usati dal motore possono avere una vista compilata efficiente, ma la fonte resta keyed
by ID. Questo evita di trasformare il runtime in una lenta mappa non tipizzata senza ricadere negli
enum chiusi attuali.

### 6.5 Corpus completo delle regole standard

Il content pack attuale è soprattutto un catalogo strutturato di classi, privilegi, capacità,
incantesimi, equipaggiamento e creature. La nuova sezione non può presentarlo come “tutte le regole”
finché non include anche il corpus normativo del gioco base.

Servono due livelli collegati:

- **Manuale consultabile**: documenti, capitoli, sezioni, paragrafi, tabelle, glossario e riferimenti;
- **Meccaniche strutturate**: le entità che il runtime può applicare.

Una `RuleDocumentSection` conserva ID canonico bilingue, gerarchia, titolo, corpo, pagina, sorgente,
licenza, tag e collegamenti alle entità strutturate. Una `RuleEntity` può rimandare a più sezioni e una
sezione può spiegare più entità. Il testo non diventa la sorgente dei calcoli, ma resta completo e
navigabile.

Il lavoro sul pack SRD deve quindi:

1. estrarre la gerarchia completa dalle edizioni italiana e inglese già incluse nel progetto;
2. costruire un crosswalk stabile fra le due lingue;
3. assegnare ID indipendenti da titolo e pagina;
4. preservare tabelle, note, elenchi e riferimenti interni;
5. collegare ogni meccanica automatizzata alla sezione che la giustifica;
6. produrre un manifest di copertura con sezioni presenti, mancanti e ancora solo testuali;
7. verificare automaticamente link rotti, sezioni orfane e differenze di struttura fra lingue;
8. mostrare sempre attribuzione e fonte previste dal pack.

Categorie minime del manuale consultabile:

- regole fondamentali e d20 test;
- creazione e avanzamento;
- caratteristiche, abilità e competenza;
- esplorazione, interazione sociale e tempo;
- combattimento, movimento e condizioni;
- equipaggiamento, oggetti, servizi e valute;
- magia, lancio e descrittori;
- classi, background, talenti, incantesimi e creature;
- glossario e definizioni.

La pubblicazione della prima schermata Regole deve avere un criterio oggettivo: il manifest non può
segnalare buchi nel corpus che l'interfaccia descrive come completo. Le sezioni non ancora
automatizzate sono perfettamente valide come `TextRuleDefinition` Manuale.

### 6.6 Composizione di un regolamento

Per non confondere “regole” con “contenuti”, una revisione risolta compone quattro famiglie
indipendenti ma referenziabili:

- **kernel meccanico**: statistiche, tiri, turni, effetti, movimento, tempo e modelli di salute;
- **corpus normativo**: capitoli, glossario, esempi e collegamenti alle meccaniche;
- **contenuti**: classi, capacità, incantesimi, equipaggiamento, creature e template;
- **presentazione**: traduzioni, icone, schemi di scheda e asset consentiti dalla licenza.

Un regolamento inventato può usare il kernel generico senza alcun contenuto SRD. Un modulo di classi
può dipendere da un kernel compatibile senza ridefinirlo. La risoluzione verifica dipendenze e
capability fra famiglie, così un pack non può dichiarare una classe che usa “slot incantesimo” se
quel concetto non esiste e non è segnato Manuale.

### 6.7 Valori, unità e limiti numerici

`RuleValue` non deve ridursi a “numero o stringa”. Il sistema di tipi include almeno booleani, ID,
interi controllati, decimali esatti, dadi, durate, distanze, quantità, percentuali, tag, collezioni
limitate e valori opzionali. Le formule sono dimensionalmente validate: non si sommano metri a PF e
non si confronta una durata con un livello senza una conversione dichiarata.

Regole tecniche:

- niente `Float`/`Double` nella semantica persistita; usare interi controllati, decimali esatti o
  razionali con arrotondamento esplicito;
- overflow, divisione per zero e conversioni di unità ambigue sono errori, non risultati silenziosi;
- ogni definizione dichiara minimo, massimo e politica per uno stato legacy fuori intervallo;
- dadi, collezioni e tabelle hanno limiti quantitativi per evitare input patologici;
- unità di visualizzazione e unità canonica sono separate, quindi “1,5 m” e “5 ft” non alterano il
  calcolo cambiando lingua o preferenze.

### 6.8 Localizzazione dei contenuti creati dall'utente

Lingua dell'interfaccia e lingua di un regolamento sono impostazioni distinte. Il testo homebrew non
viene tradotto automaticamente né sovrascritto. Ogni campo localizzabile conserva varianti per
locale, lingua primaria e catena di fallback; quando viene mostrato un fallback, la UI espone il
badge della lingua effettiva. Gli ID e i payload meccanici rimangono identici in tutte le lingue.

Anche le misure hanno due piani: il ruleset definisce unità canoniche e conversioni che influenzano
le regole; le preferenze dell'utente decidono soltanto come mostrarle. Cambiare UI da piedi a metri
non crea una nuova revisione né arrotonda lo stato. Cambiare la scala meccanica o la formula di
movimento sì.

## 7. Inventario delle regole configurabili

Questa è la checklist che l'editor e il runtime devono considerare. Non tutte le voci devono avere
automazione completa nella prima release; tutte devono poter essere descritte e impostate a Manuale.

### 7.1 Dadi, prove ed esiti

- dado base o pool di dadi;
- mazzo di carte, pesca senza reinserimento, reshuffle e sacchetto di token;
- dadi percentuali, roll-over, roll-under e tiri contrapposti;
- vantaggio/svantaggio, keep highest/lowest, drop, reroll, exploding dice;
- modificatori statici, da statistica, da livello, da competenza o da tabella;
- difficoltà fissa, difesa del bersaglio o formula contestata;
- pareggi e chi li vince;
- successi automatici e fallimenti automatici;
- intervallo di minaccia, conferma del critico e moltiplicatore;
- gradi di successo/fallimento;
- take 10/take 20 o equivalenti;
- regole di stacking, cap, floor e arrotondamento;
- tiri visibili, segreti, manuali o digitali.

### 7.2 Caratteristiche, abilità e difese

- numero e nomi arbitrari delle caratteristiche;
- range, valore predefinito e formula del modificatore;
- abilità, caratteristica associata e competenze/ranghi;
- tiri salvezza basati su caratteristica o progressione autonoma;
- difese multiple, come CA normale, touch e flat-footed;
- iniziativa, percezione passiva e altre statistiche derivate;
- bonus di competenza, BAB o tabelle alternative;
- tipi di bonus con stacking diverso;
- taglia, portata, spazio occupato e modificatori di taglia;
- campi personalizzati visibili in scheda.

### 7.3 Creazione e avanzamento

- livello minimo/massimo o assenza di livelli;
- XP a soglie, spesa XP, milestone o progressione libera;
- classi, sottoclassi, classi di prestigio e multiclass;
- specie/razza/ascendenza, background, temi e archetipi;
- prerequisiti booleani e numerici;
- dadi vita, PF iniziali e PF per livello;
- incrementi di caratteristica, talenti e scelte ripetibili;
- skill points/ranks e limiti di classe/cross-class;
- progressioni di attacco e salvezza;
- privilegi per livello e sostituzioni;
- retraining/respec e regole per correggere la cronologia;
- progressione oltre il massimo ordinario;
- campi e passaggi di creazione interamente manuali.

### 7.4 Turno ed economia delle azioni

- iniziativa individuale, di gruppo, a carte o senza iniziativa;
- sorpresa e combattenti non consapevoli;
- fasi e ordine delle fasi;
- Azione/Azione bonus/Reazione;
- standard/move/full-round/swift/immediate/free;
- azioni multiple e attacchi iterativi;
- azioni preparate, ritardate e interrupt;
- attacchi di opportunità e area minacciata;
- azioni leggendarie, di tana o risorse condivise;
- azioni extra e restrizioni sulla loro spesa;
- reset a inizio turno, fine turno, round o altro trigger;
- turni simultanei e spareggi;
- costo zero, costo variabile o costo composto.

### 7.5 Attacchi, manovre e bersagli

- attacco contro una o più difese;
- tiro salvezza, prova contrapposta, successo automatico o risoluzione manuale;
- bersaglio sé, alleato, nemico, qualunque, oggetto o punto sulla mappa;
- bersagli multipli e selezione ripetuta;
- mischia, distanza, incrementi di gittata e penalità;
- portata naturale e armi con reach;
- copertura, concealment e occultamento totale;
- fiancheggiamento;
- grapple, trip, disarm, bull rush e manovre personalizzate;
- attacco completo e penalità progressive;
- critici, immunità ai critici e danni moltiplicati selettivamente;
- fuoco amico e line of effect;
- forme di area configurabili.

### 7.6 Danno, guarigione e morte

- tipi di danno creati dall'utente;
- resistenza, vulnerabilità, immunità e riduzione del danno;
- regole di stacking e bypass della riduzione;
- danno letale/non letale;
- punti ferita temporanei, scudi, barriere o tracce multiple;
- danno alle caratteristiche, drain, livelli negativi o ferite;
- soglia di danno massiccio;
- minimi e massimi dopo una modifica;
- zero PF, valori negativi, morte immediata, tiri morte o stabilizzazione;
- guarigione, overheal, resurrezione e limiti del bersaglio;
- concentrazione o test equivalenti causati dal danno;
- recupero naturale durante riposi o tempo di gioco.

### 7.7 Condizioni ed effetti persistenti

- condizioni standard, nuove condizioni e sottotipi;
- durata in round, turni, tempo reale di gioco, fino al riposo o manuale;
- scadenza a inizio/fine turno del bersaglio o della fonte;
- concentrazione, mantenimento o effetto permanente;
- stacking per fonte, intensità o esclusività;
- immunità e rimozione;
- modificatori concessi, azioni vietate e movimento alterato;
- tiro ripetuto per terminare l'effetto;
- aura ed effetti dipendenti dalla distanza;
- note private del GM e testo visibile ai giocatori.

### 7.8 Magia, poteri e risorse

- slot standard, preparazione Vanciana, spontanea, conosciuti/preparati;
- pact-like slots, punti magia, psionica o risorse inventate;
- livelli o gradi di potere arbitrari;
- attributo di lancio, DC, bonus d'attacco e caster level;
- componenti, focus, costo materiale e tempo di lancio;
- scuole, descrittori, rituali, concentrazione e controincantesimo;
- spell resistance e prove di superamento;
- scaling per livello del personaggio, classe, slot o dado;
- liste per classe, dominio o fonte;
- recupero per riposo, turno, incontro, giorno o condizione;
- risorse con dado, massimo derivato, ricarica casuale o parziale.

### 7.9 Movimento, spazio e mappa

- piedi, metri o unità personalizzate con conversione di sola presentazione;
- griglia quadrata, esagonale o teatro della mente;
- costo diagonale uniforme, 5-10-5 o formula personalizzata;
- modi di movimento: camminare, volare, nuotare, scalare, scavare, teletrasporto;
- terreno difficile e moltiplicatori;
- elevazione e distanza tridimensionale;
- spazio, reach e squeeze;
- collisioni, passaggio attraverso creature e occupazione;
- muri, linea di vista, line of effect, cover e concealment;
- luce, oscurità, sensi e raggi di percezione;
- portata delle aree e inclusione delle celle al bordo.

### 7.10 Equipaggiamento, inventario ed economia

- categorie e proprietà di armi configurabili;
- danno, critico, gittata, taglia e padronanze;
- armature, scudi, difese e limiti al modificatore;
- slot equipaggiamento e oggetti indossati;
- competenze/addestramenti;
- ingombro, carico e penalità;
- munizioni, cariche, consumo e rottura;
- attunement o limiti alternativi;
- valute e conversioni arbitrarie;
- oggetti magici, consumabili e crafting;
- loot collegato a una definizione del regolamento.

### 7.11 Riposi, tempo e ambiente

- tipi di riposo, durata e prerequisiti;
- recupero completo, parziale, con dadi o con spesa di risorse;
- unità di tempo e calendari opzionali;
- effetti a ogni round/minuto/ora/giorno;
- fame, sete, sonno, temperature e pericoli ambientali;
- viaggio, velocità di gruppo e incontri casuali;
- regole opzionali di affaticamento, sanità, onore o stress.

### 7.12 Incontri, ricompense e CPU

- CR/GS, livello incontro, budget XP o formule personalizzate;
- ricompense per sconfitta, obiettivo o milestone;
- numero e significato delle fazioni;
- condizioni di vittoria e sconfitta;
- regole di attivazione, morale e fuga;
- profilo di capacità dichiarato dal regolamento per la CPU;
- fallback automatico da CPU a suggerimenti o controllo manuale quando una meccanica non è
  supportata.

### 7.13 Scene, sfide e procedure narrative

- scene libere, conflitti sociali, inseguimenti, viaggi, downtime e combattimenti;
- prove di gruppo, challenge estese e obiettivi con più soglie;
- orologi/clock, tracce segmentate, countdown e progressi concorrenti;
- posizione ed effetto, rischio, costo, complicazioni e successo parziale;
- aspetti, tag, legami, reputazione, fazioni e relazioni;
- stress, conseguenze, ferite narrative e recuperi definiti dal tavolo;
- mosse attivate dalla fiction e prompt Manuali, senza interpretazione automatica del testo;
- spotlight, turni liberi o priorità negoziata;
- trasformazioni, forme, veicoli, cavalcature, gregari, evocazioni e gruppi/mob;
- ricette di scena che dichiarano pannelli, tracker, azioni disponibili e condizione di chiusura.

### 7.14 Metarisorse, informazioni e autorità

- punti fato/ispirazione/momentum e valute create dall'utente;
- pool personali, di gruppo o del GM con trasferimento e limiti;
- rilanci, modifica retroattiva di un tiro, flashback e spesa dopo l'esito;
- informazioni pubbliche, private al proprietario, segrete al GM o rivelate da un evento;
- tiri aperti, ciechi, impegnati/commit-reveal o inseriti manualmente;
- chi può creare, approvare, annullare o correggere una conseguenza;
- consenso o conferma del tavolo per regole Assistite;
- audit delle correzioni senza esporre note segrete a esportazioni/player view.

Questa checklist non può enumerare ogni meccanica futura. Il criterio di apertura è che un concetto
non presente possa almeno essere modellato con campi, tracker, procedure e azioni Manuali, e che una
nuova primitiva generica possa essere aggiunta senza introdurre un riferimento a un'edizione
specifica nel dominio.

## 8. Motore di regole

### 8.1 Pipeline a eventi

Ogni azione segue una pipeline stabile:

1. la UI crea un comando intenzionale, per esempio “usa capacità X su Y”;
2. il runtime costruisce il contesto dalla revisione e dallo stato;
3. i validatori stabiliscono se il comando è ammesso;
4. sostituzioni e modificatori costruiscono la formula effettiva;
5. eventuali dadi vengono consumati dal RNG deterministico della sessione;
6. gli effetti producono eventi di dominio;
7. lo stato applica gli eventi;
8. trigger successivi producono altri eventi entro un limite verificato;
9. audit e spiegazione registrano fonti, formule, risultati e hash del regolamento.

Fasi di hook consigliate:

- `COMMAND_VALIDATE`
- `BEFORE_COST`
- `AFTER_COST`
- `BEFORE_ROLL`
- `AFTER_ROLL`
- `BEFORE_EFFECT`
- `AFTER_EFFECT`
- `TURN_START`
- `TURN_END`
- `ROUND_START`
- `ROUND_END`
- `RESOURCE_CHANGED`
- `CONDITION_ADDED`
- `DAMAGE_TAKEN`
- `ZERO_REACHED`
- `REST_STARTED`
- `REST_COMPLETED`

### 8.2 Formule sicure

Le formule sono AST serializzabili e tipizzati. Operazioni iniziali:

- letterali numerici, booleani, stringhe e ID;
- riferimenti a statistica, risorsa, livello, distanza, tag e stato;
- `+`, `-`, `*`, divisione con politica di arrotondamento, modulo;
- `min`, `max`, `clamp`, valore assoluto e confronto;
- `if/then/else`;
- lookup in tabelle;
- conteggio, `any`, `all`, somma su collezioni limitate;
- dadi e selezione di dadi tramite il solo RNG della sessione;
- presenza di feature, condizione, equipaggiamento o tag;
- relazione fra fonte, attore e bersaglio.

Non sono ammessi rete, filesystem, orologio di sistema, riflessione, processi, loop senza limite o
chiamate a codice importato. Il compilatore impone limite di profondità, numero di nodi ed eventi
generati. Una formula non valida non viene pubblicata.

### 8.3 Modificatori e stacking

Un modificatore dichiara:

- sorgente;
- target tipizzato;
- operazione: `ADD`, `MULTIPLY`, `SET`, `MIN`, `MAX`, `REPLACE`, `GRANT`, `DENY`;
- valore o formula;
- predicato;
- gruppo di stacking;
- politica: somma, più alto, più basso, una volta per fonte, esclusivo;
- priorità;
- ambito e durata.

Ordine predefinito, sovrascrivibile soltanto in modo esplicito:

1. valore/base formula;
2. sostituzioni;
3. bonus e penalità selezionati dallo stacking;
4. moltiplicatori;
5. minimi e massimi;
6. override finali dichiarati;
7. arrotondamento.

Ogni valore derivato espone **Perché questo valore?**, con una traccia simile a:

`CA 18 = base 10 + armatura 6 + scudo 2; bonus schivare +1 escluso perché non applicabile`.

Questa spiegabilità è indispensabile quando l'utente può scrivere le regole.

### 8.4 Effetti e trigger

Primitive iniziali:

- spendi/recupera risorsa;
- infliggi danno o cura;
- aggiungi/rimuovi condizione;
- modifica un contatore o una statistica temporanea;
- concedi/consuma un'azione;
- muovi, teletrasporta o spingi;
- effettua una prova;
- crea una scelta assistita per il tavolo;
- emetti testo di log;
- termina concentrazione o collegamento equivalente;
- crea un effetto con scadenza.

Se una regola richiede un concetto assente:

- può essere Manuale immediatamente;
- può combinare primitive esistenti;
- può richiedere una nuova primitiva generica in una release futura;
- non deve mai essere implementata cercando parole nel nome o nella descrizione.

### 8.5 Cicli e conflitti

Il compilatore deve rifiutare o rendere manuali:

- formule derivate cicliche;
- trigger che si riattivano senza limite;
- riferimenti mancanti;
- patch contro una base diversa dall'hash atteso;
- due override finali senza precedenza;
- risorse con massimo negativo o stato fuori range;
- azioni con costi che il turno non possiede;
- entità disabilitate ancora richieste da una classe o da una scheda.

### 8.6 Semantica versionata e determinismo

L'hash del ruleset da solo non garantisce la riproducibilità: una futura versione dell'app potrebbe
interpretare diversamente lo stesso operatore. Ogni snapshot contiene quindi:

- `rulesSchemaVersion` per la forma dei dati;
- `runtimeSemanticsVersion` per il significato degli operatori;
- versioni delle primitive eventualmente evolute;
- algoritmo di canonicalizzazione e hash;
- capability minime richieste al lettore.

La serializzazione canonica ordina chiavi e insiemi non semantici, normalizza Unicode e numeri,
mantiene invece l'ordine dove influenza la precedenza e non include date o label nell'`runtimeHash`.
La `RuntimeSemanticsVersion` è invece sempre inclusa nell'`runtimeHash`. Il runtime conserva gli
interpreti ancora supportati oppure esegue una migrazione esplicita con
nuovo hash. Se non può garantire la vecchia semantica, apre la sessione in sola lettura/Manuale e
spiega quale componente manca; non la ricalcola con il comportamento nuovo.

### 8.7 Diagnostica e severità

Ogni validazione restituisce percorso preciso, entità, codice stabile, messaggio localizzato e
possibile correzione. Le severità sono:

- `ERROR`: impedisce pubblicazione o applicazione;
- `WARNING`: richiede presa visione prima della pubblicazione;
- `INFO`: segnala copertura, prestazioni o opportunità senza bloccare.

Compilazione, test e analisi d'impatto lavorano fuori dal thread UI, sono annullabili e riusano cache
indicizzate per `runtimeHash`. Le prestazioni vengono governate con budget misurati sui dispositivi
supportati — apertura, ricerca, compilazione, memoria ed eventi per comando — non con una promessa
astratta valida soltanto su desktop.

## 9. Livelli di automazione

Ogni regola e capacità mostra un livello chiaro:

### Completa

Onfall valida, tira, applica e registra tutto.

### Assistita

Onfall prepara calcoli e candidati, poi chiede una scelta o un risultato al tavolo. Esempio: il GM
sceglie fra più bonus situazionali o inserisce l'esito di una tabella esterna.

### Manuale

Onfall mostra testo, riferimenti, promemoria, contatori e un pannello per correggere lo stato. Non
impedisce la giocata e registra la decisione manuale.

Il regolamento espone anche una percentuale indicativa di copertura e le capacità richieste. La CPU
può essere abilitata soltanto se il profilo dichiara supportate le meccaniche necessarie; altrimenti
passa a **Suggerimenti** o **Sandbox** con una motivazione leggibile.

## 10. Sezione Regole: progettazione UX

### 10.1 Struttura principale

All'interno di Regole ci sono quattro viste:

1. **Regolamenti**: progetti, bozze e revisioni;
2. **Catalogo regole**: tutte le entità aggregate;
3. **Conflitti e test**: errori, warning e casi di prova;
4. **Importa/Esporta**: pacchetti e licenze.

Desktop usa lista/albero a sinistra, contenuto al centro e dettaglio/editor a destra. Android usa
lista → dettaglio → editor con un breadcrumb e conferma delle bozze non salvate.

### 10.2 Filtri sempre disponibili

- origine: Tutte, Standard, Homebrew, Importate, Solo sessione;
- stato: Attive, Modificate, Disabilitate, Bozze, Pubblicate, Archiviate;
- categoria;
- regolamento/revisione;
- automazione: Completa, Assistita, Manuale;
- compatibilità CPU;
- lingua disponibile;
- In uso da personaggi/sessioni;
- tag e testo libero.

Il filtro rapido principale è **Standard | Homebrew | Tutte**, esattamente aderente alla richiesta.

La lista distingue inoltre tre prospettive per evitare duplicati ingannevoli:

- **Sorgenti** mostra lo standard immutabile, i moduli e le patch homebrew come oggetti separati;
- **Vista effettiva** mostra una sola voce per ID, già risolta secondo la revisione selezionata, con
  badge Modificata/Disabilitata e link alla provenienza;
- **Differenze** mostra soltanto aggiunte, sostituzioni e disabilitazioni rispetto alla base.

Il filtro Standard opera sulle sorgenti incluse; Homebrew sulle aggiunte e patch dell'utente. Nella
Vista effettiva l'origine non si perde: una regola standard sovrascritta appare “Standard · modificata
da Homebrew”, non due regole apparentemente attive insieme.

### 10.3 Dettaglio di una regola standard

Mostra:

- badge Standard · sola lettura;
- nome e testo;
- comportamento strutturato;
- dipendenze e riferimenti inversi;
- sorgente, licenza, attribuzione e pagina;
- revisione e hash;
- test inclusi;
- pulsante **Crea variante homebrew**.

Il pulsante richiede una destinazione. Se non esiste ancora un progetto modificabile, apre un wizard
breve per crearlo; se ne esistono più di uno, fa scegliere progetto, modulo e ambito. Non esiste una
bozza homebrew “senza proprietario” che possa poi essere applicata alla sessione sbagliata.

### 10.4 Editor homebrew

Tab consigliate:

- Panoramica;
- Testo e traduzioni;
- Meccanica;
- Modificatori;
- Prerequisiti e riferimenti;
- Test;
- Differenze dall'origine;
- Cronologia.

L'editor offre due livelli:

- **Semplice**: moduli guidati, campi e formule comuni;
- **Avanzato**: albero di espressioni, trigger e ordine di stacking.

La rappresentazione JSON non è l'interfaccia primaria, ma può essere mostrata in sola lettura o
editata in una modalità esperto con validazione immediata.

### 10.5 Creare un regolamento

Scelte iniziali:

- **Da SRD 5.2.1**: scelta consigliata e richiesta principale;
- **Da zero/manuale**: fondazione generica con zero entità; nessun contenuto o default D20/SRD;
- **Fondazione D20 vuota**: preset futuro costruibile sopra la stessa base generica, non requisito
  tecnico del runtime;
- **Importa pacchetto**.

Flusso da SRD:

1. nome e descrizione;
2. lingua primaria e fallback;
3. moduli SRD da ereditare;
4. opzioni iniziali comuni;
5. creazione della bozza;
6. pagina Differenze inizialmente vuota.

### 10.6 Pubblicare

La pubblicazione richiede:

- nessun errore bloccante;
- accettazione dei warning non bloccanti;
- nome revisione e changelog;
- esito dei test inclusi;
- riepilogo di incompatibilità e migrazioni;
- conferma che la revisione diventerà immutabile.

Per correggerla si crea una revisione successiva.

### 10.7 Analisi d'impatto e laboratorio

La pagina **Dove è usata** costruisce riferimenti inversi verso regole, classi, personaggi, creature,
template, campagne e sessioni. Prima di pubblicare una modifica condivisa mostra gli oggetti che
potrebbero richiedere migrazione e permette di preparare un piano collettivo senza applicarlo.

Il laboratorio consente di eseguire esempi isolati con stato e seed scelti dall'utente, confrontare
base e bozza, vedere la trace “Perché?” e salvare il caso come test della revisione. Le prove non
mutano personaggi o sessioni reali.

## 11. Confine fra Regole e Compendio

Il confine deve essere comprensibile e senza doppie fonti di verità.

### Regole contiene

- meccaniche globali;
- caratteristiche, abilità, condizioni e tipi di danno;
- classi, sottoclassi, ascendenze, background e progressioni;
- privilegi, talenti, incantesimi e azioni riusabili;
- definizioni di armi, armature, oggetti e risorse;
- moduli, formule, tabelle e testi di regola.

### Compendio contiene

- personaggi e loro stato;
- PNG e creature come template;
- inventari e istanze di oggetto possedute;
- mappe e asset del tavolo;
- gruppi e template di incontro.

L'attuale Archivio capacità non deve restare una seconda copia. Durante la migrazione può essere una
vista compatibile del Catalogo regole; a regime apre direttamente Regole filtrata su
“Azioni/Privilegi/Incantesimi”.

Ogni scheda e stat block dichiara il proprio `RulesetBinding`. Creare una nuova scheda chiede quale
regolamento usare, con il default configurabile nelle Impostazioni. Le liste di classi, statistiche,
abilità e campi sono generate da quella revisione.

### Sessione generale e incontri opzionali

Oggi il workspace conserva una `CombatSession` anche quando la procedura parla di ruolo o
esplorazione. Per supportare davvero regolamenti diversi serve un aggregate `GameSession` che
contenga:

- identità, titolo, partecipanti e `RulesetBinding`;
- scene e modalità correnti: libera, esplorazione, sociale, downtime, combattimento o custom;
- tempo di gioco, orologi, obiettivi e risorse condivise dichiarate dal ruleset;
- eventuali `EncounterState`, dei quali il combattimento è una specializzazione;
- board, presentazione, note strutturate e allegati;
- audit, seed/RNG dove richiesto e confini di revisione.

`CombatSession` può inizialmente rimanere intatto dietro un adattatore come
`CombatEncounterState`. Il punto è evitare che un regolamento senza iniziativa, CA o turni debba
inventarli soltanto per esistere in Onfall. La UI della sessione mostra pannelli e tracker dichiarati
dal modulo attivo; un regolamento puramente manuale conserva comunque partecipanti, scene, note,
dadi, contatori e registro.

### Gerarchia dei default e binding

La risoluzione segue una gerarchia esplicita:

1. **default dell'app**: suggerimento per nuovi oggetti, mai vincolo retroattivo;
2. **default della campagna**: revisione proposta a nuove schede, scene e sessioni;
3. **binding del personaggio/template**: revisione con cui è stato costruito;
4. **binding fissato della sessione**: unica revisione eseguita durante il gioco;
5. **override locale della sessione**: layer finale incluso nello snapshot e nel suo hash.

Il `Campaign` già presente, che conserva profilo e manifest bloccato, va collegato al workspace
invece di creare un secondo concetto parallelo. Cambiare il default di campagna propone migrazioni
per gli oggetti esistenti, ma non le applica. Se non c'è una campagna, la sessione rimane un aggregate
autonomo e portabile.

### Proprietà e durata dello stato

Ogni statistica o risorsa compilata dichiara `StatePersistencePolicy`:

- Azione/Turno: effimera, mai scritta nella scheda;
- Incontro: sopravvive ai round ma si chiude con l'incontro secondo una regola dichiarata;
- Sessione: resta nel file di sessione e può attraversare più scene;
- Campagna: appartiene al gruppo/mondo e richiede un archivio campagna autorevole;
- Permanente del personaggio: appartiene alla scheda e può essere sincronizzata solo in modo
  compatibile.

Reset e recuperi sono eventi del ruleset, non convenzioni dedotte dal nome “slot”, “PF” o “stress”.
Questo consente a un regolamento inventato di usare, per esempio, Stress di sessione e Ferite del
personaggio senza aggiungere campi speciali al codice.

## 12. Nuova sessione

La procedura diventa:

1. Origine/template;
2. **Regolamento**;
3. Partecipanti;
4. Griglia/spazio;
5. Modalità;
6. Controllo avversari/CPU;
7. Riepilogo e avvio.

### Passaggio Regolamento

Mostra:

- Standard SRD 5.2.1;
- regolamenti homebrew pubblicati;
- bozze valide con comando “pubblica e usa”;
- regolamenti importati;
- nome revisione, origine, data, copertura automazione e compatibilità CPU;
- eventuali aggiornamenti disponibili, mai selezionati automaticamente.

Il regolamento standard può essere preselezionato, ma il passaggio resta visibile e modificabile.
La scelta viene fotografata prima di creare i combattenti.

### Compatibilità partecipanti

Ogni partecipante mostra uno stato:

- **Esatta**: stessa revisione;
- **Compatibile**: tutti gli ID usati hanno tipo, unità e semantica compatibili secondo un contratto
  validato o un adapter dichiarato; appartenere alla stessa famiglia non basta;
- **Migrazione disponibile**;
- **Manuale**: dati base importabili, automazioni non garantite;
- **Incompatibile**: impossibile produrre lo stato minimo richiesto.

Gli elementi incompatibili non vengono nascosti. L'utente può:

- migrare una copia della scheda;
- usare un adattatore manuale;
- tornare a scegliere il regolamento;
- escludere il partecipante.

Una sessione usa un solo snapshot risolto. Supportare contemporaneamente due action economy o due
modelli di morte nello stesso motore produrrebbe risultati ambigui. Un personaggio di un'altra
edizione entra tramite migrazione o adattatore, non portando un secondo motore dentro la sessione.

### Template inclusi

Ogni template dichiara la revisione con cui è stato progettato. Se si sceglie un'altra revisione,
Onfall mostra un diff di compatibilità e non promette più il bilanciamento originale.

## 13. Modificare le regole di una sessione già iniziata

Comando disponibile da:

- menu della sessione;
- chip del regolamento nell'intestazione Battaglia;
- dettaglio della sessione in Partita.

### 13.1 Flusso sicuro

1. la sessione viene messa in pausa;
2. si attende la fine di un eventuale comando o turno CPU;
3. viene creato un checkpoint persistente di sessione, incontri, board, presentazione, binding e
   coda di sincronizzazione;
4. l'utente sceglie una revisione esistente oppure **Crea modifica per questa sessione**;
5. l'app calcola differenze e impatto sullo stato;
6. l'utente risolve le decisioni necessarie;
7. una simulazione valida la migrazione senza mutare l'originale;
8. il nuovo snapshot e tutto lo stato riconciliato vengono applicati atomicamente;
9. audit e salvataggio registrano il confine di revisione;
10. la sessione può riprendere.

Le modifiche non entrano in vigore a ogni battuta nell'editor. La bozza viene applicata soltanto con
**Pubblica e applica**.

### 13.2 Classificazione dell'impatto

- `PRESENTATION_ONLY`: testo o traduzione, applicazione diretta;
- `DERIVED_VALUE`: ricalcolo di statistiche;
- `STATE_SHAPE`: aggiunta/rimozione di risorse, campi o fasi;
- `BEHAVIOR`: stessa forma, risoluzione futura diversa;
- `REFERENCE`: elemento rinominato, sostituito o disabilitato;
- `UNSUPPORTED`: richiede intervento manuale.

### 13.3 Decisioni di riconciliazione

Esempi che l'interfaccia deve gestire esplicitamente:

- cambia il massimo PF: conserva PF correnti, danno subito, percentuale o imposta al nuovo massimo;
- cambia una risorsa: conserva speso, rimanente o proporzione;
- cambia action economy nel mezzo del turno: applica dal prossimo turno, dal prossimo round o rimappa
  subito il budget;
- cambia iniziativa: conserva ordine, ricalcola al round successivo o ritira ora;
- cambia una condizione già attiva: conserva la vecchia istanza, migra o rimuovi;
- disabilita un'azione posseduta: mantieni come manuale/tombstone o sostituisci;
- cambia classe/progressione: conserva la cronologia e apre un wizard di riconciliazione, senza
  inventare retroattivamente le scelte;
- cambia modello di morte: mappa stato corrente tramite una scelta dichiarata;
- cambia unità o diagonali: le posizioni restano, cambiano soltanto i costi futuri salvo scelta
  contraria.

### 13.4 Audit e Undo

Le azioni precedenti restano valide sotto il vecchio hash. Il cambio di revisione è un confine forte:

- il normale Undo non attraversa il confine;
- il checkpoint precedente resta disponibile come **Ripristina regolamento e stato precedente**;
- il ripristino è a sua volta un evento auditato;
- nessun tiro passato viene ripetuto;
- il nuovo hash viene associato a tutti gli eventi successivi.

Questa politica è più comprensibile di un Undo che riporta silenziosamente anche un intero motore a
una versione precedente.

### 13.5 Ambito della variante

Una modifica può essere:

- solo per questa sessione;
- riutilizzabile in nuove sessioni;
- proposta come nuova revisione del regolamento d'origine.

In ogni caso compare in Regole. La promozione da Solo sessione a Riutilizzabile non cambia l'ID della
revisione già in uso.

Visibilità della variante e momento di attivazione sono scelte diverse. L'utente può applicare la
nuova revisione subito al confine sicuro, dal prossimo turno/round, dalla prossima scena o soltanto ai
nuovi incontri. L'attivazione pianificata è salvata e auditata; quando scatta produce comunque un
unico confine di revisione. Per un effetto ambientale temporaneo già esprimibile dalle regole si usa
invece uno stato/condizione di scena, non si pubblica una revisione a ogni entrata in una stanza.

### 13.6 Sincronizzazione con schede e Compendio

Il codice attuale inoltra modifiche e risorse dalla battaglia al roster. Questo comportamento non è
sicuro quando sessione e scheda usano revisioni diverse. Il nuovo `StateSyncPlan` applica una
scrittura verso la scheda soltanto se:

1. la policy indica proprietario `CHARACTER` e consente la sincronizzazione;
2. l'attore mantiene un riferimento certo alla scheda originale;
3. ID, tipo, unità e semantica coincidono fra le due revisioni, oppure esiste un mapping di
   migrazione esplicito;
4. il valore supera la validazione della scheda di destinazione;
5. la transazione che salva la sessione può salvare o compensare anche la scheda.

Le risorse nuove create soltanto nella sessione restano di proprietà della sessione finché l'utente
non le mappa o le promuove. In caso di dubbio, Onfall conserva la scheda e mostra **Modifiche in
attesa**; non associa campi per somiglianza del nome. L'anteprima del cambio ruleset elenca anche le
scritture previste verso il Compendio e offre: applica, conserva solo in sessione, migra una copia o
annulla.

Un ripristino del checkpoint non deve lasciare la scheda in uno stato successivo alla sessione:
eventuali scritture già pubblicate sono incluse nel journal e vengono compensate o presentate come
decisioni esplicite.

### 13.7 Cambio di topologia e board

Passare da quadrati a esagoni, distanze libere o teatro della mente è una migrazione strutturale, non
un semplice cambio di unità. Il planner tratta separatamente:

- immagine di sfondo e scala;
- coordinate e ingombri dei token;
- muri, porte, pavimento, nebbia e visibilità;
- misure, aree, etichette e annotazioni;
- regole di adiacenza, diagonale, portata e linea di effetto.

Per default non inventa una conversione square→hex. L'utente può mantenere soltanto lo sfondo,
rimappare manualmente, usare un convertitore dichiarato dal modulo, azzerare gli strati geometrici o
duplicare la sessione prima della prova. La board viene astratta dietro `BoardGeometry`; l'adattatore
quadrato attuale è la prima implementazione e il supporto hex è una capability separata, non una
promessa implicita del primo runtime generico.

### 13.8 Autorità e concorrenza

Nella versione offline il proprietario locale/GM è l'unica autorità che pubblica o applica regole.
Il modello evento conserva comunque `actorId`, autorità, timestamp logico e hash, così una futura
sessione condivisa non richiederà di reinterpretare l'audit. Draft, sessione e schede usano revisioni
ottimistiche: se un'altra finestra ha salvato una versione più nuova, l'app ricalcola il diff e non
sovrascrive. In un futuro multiplayer, tutti i client devono confermare lo stesso `runtimeHash`
prima di riprendere l'esecuzione automatica.

## 14. Versionamento, aggiornamenti e merge

### 14.1 Regole di versione

- Le bozze sono mutabili e hanno un numero di salvataggio interno.
- Le revisioni sono immutabili.
- Il numero semantico è leggibile, ma l'identità tecnica è l'hash canonico.
- Due revisioni con gli stessi dati risolti hanno lo stesso hash, anche se importate due volte.
- Cambiare soltanto metadati non eseguibili può avere un hash documento distinto e lo stesso
  `runtimeHash`; entrambi vengono conservati.

Una revisione può includere `MigrationDefinition` dichiarative da specifici hash precedenti:
alias/tombstone di ID, conversioni di valore tipizzate, valori iniziali e domande da porre
all'utente. Usano la stessa AST limitata del runtime, non script. Se manca un percorso diretto, il
planner può concatenare soltanto migrazioni compatibili e mostra l'intero piano prima di applicarlo.

### 14.2 Aggiornare la base

Un homebrew derivato non segue automaticamente una nuova base. Il comando **Aggiorna base** esegue
un merge a tre vie:

- vecchia base;
- nuova base;
- patch homebrew.

Risultati:

- patch ancora applicabile;
- patch già assorbita dalla nuova base;
- conflitto sullo stesso campo;
- riferimento rimosso o trasformato.

L'utente risolve i conflitti e pubblica una nuova revisione. Le sessioni sulla revisione precedente
non cambiano.

### 14.3 Eliminazione

- Bozza mai usata: eliminabile con conferma.
- Revisione non usata: archiviabile, non mutabile.
- Revisione usata: conservata obbligatoriamente.
- Progetto archiviato: nascosto dai selettori predefiniti ma visibile nelle sessioni che lo usano.
- Garbage collection fisica ammessa soltanto per oggetti non referenziati e dopo backup.

## 15. Importazione, esportazione e licenze

Formato consigliato: `.onfall-ruleset`, archivio con:

- `manifest.json`;
- revisioni e patch canoniche;
- traduzioni;
- asset opzionali con limiti;
- casi di test;
- licenza e attribuzione;
- hash di ogni file.

Sicurezza import:

- nessun bytecode o script eseguibile;
- validazione schema prima dell'installazione;
- protezione da path traversal e archivi compressi eccessivi;
- limiti su dimensioni, numero di entità, profondità delle formule ed eventi;
- anteprima di autore, licenza, dipendenze e permessi;
- namespace collision risolta con import separato o remapping dichiarato;
- quarantena delle revisioni non compilabili;
- test eseguiti in sandbox deterministica.

L'import non deduplica usando soltanto `projectId` o numero di versione. Se lo stesso ID arriva con
hash differente, mostra un conflitto di provenienza e permette import affiancato, rifiuto o remapping;
non sostituisce il progetto locale. Un hash dichiarato diverso da quello ricalcolato, una dipendenza
mancante o una semantica runtime più nuova porta il pacchetto in quarantena. Il contenuto resta
ispezionabile/esportabile senza essere eseguito.

Metadati di licenza vanno mantenuti per pack e, quando necessario, per singola entità. La possibilità
tecnica di rappresentare una certa edizione non implica il diritto di distribuirne il testo. Onfall
può fornire l'infrastruttura, contenuti inclusi soltanto quando licenza e attribuzione lo consentono,
e strumenti per contenuti privati dell'utente. Prima di pubblicare pack di terzi serve una verifica
specifica dei diritti.

## 16. Persistenza proposta

Archivio locale concettuale:

```text
rulesets/
  index.json
  projects/<project-id>.json
  drafts/<project-id>.json
  revisions/<canonical-hash>.json
  runtime/<runtime-hash>.json
  assets/<content-hash>
  transactions/<transaction-id>.json
  backups/
```

Ogni file usa schema versionato, scrittura atomica e backup.

### Transazioni fra archivi

La sostituzione atomica di un singolo JSON non basta: applicare un ruleset può coinvolgere
repository regole, archivio sessione, workspace recovery, board e una o più schede. Un
`RulesTransactionCoordinator` usa un journal recuperabile:

1. acquisisce le versioni attese degli oggetti e rifiuta conflitti ottimistici;
2. prepara in un'area di staging revisione, runtime compilato, stato migrato e scritture scheda;
3. valida e rilegge tutti i documenti staged, inclusi hash e riferimenti;
4. salva un record `PREPARED` con hash prima/dopo e operazioni di compensazione;
5. sostituisce i file e aggiorna per ultimi i puntatori visibili;
6. marca `COMMITTED`, aggiorna il recovery e poi rimuove lo staging quando sicuro.

All'avvio, un journal incompleto viene completato o compensato in modo deterministico. Il checkpoint
precedente resta finché sessione e schede nuove sono state rilette con successo. I test devono
interrompere artificialmente il processo dopo ogni passaggio per provare che non esista uno stato
mezzo vecchio e mezzo nuovo.

### Binding nella sessione

Il nuovo archivio sessione contiene:

- `rulesetBinding` completo;
- `runtimeHash`;
- snapshot runtime minimo per recupero di emergenza;
- riferimenti alle entità dei combattenti;
- lista dei confini di revisione nel registro;
- decisioni di migrazione.

Il repository locale conserva la revisione completa. Un export portabile include revisione e asset
necessari. Se la revisione completa manca ma è presente lo snapshot runtime, la sessione resta
giocabile e consultabile in sola lettura; per modificarne il regolamento occorre ricostruire o
importare il progetto completo.

### Binding nelle schede

- `rulesetBinding` della scheda;
- revisione usata per l'ultima derivazione;
- scelte con ID stabili;
- snapshot dei dati necessari a mostrare scelte non più disponibili;
- stato di compatibilità e migrazione pendente.

### Workspace e recupero

Il recovery attuale fotografa `CombatSession`, presentazione e `BoardDocument` con uno schema proprio.
La nuova versione fotografa il `GameSession` completo, binding/hash, checkpoint attivo, decisioni di
migrazione e coda delle sincronizzazioni. Anche una variante Solo sessione non ancora promossa deve
essere incorporata o referenziata con una copia recuperabile. Il normale salvataggio di sessione e
il crash recovery usano lo stesso codec di dominio per evitare due significati diversi dello stato.

Revisioni e runtime vengono riletti e verificati contro il content hash prima dell'uso. In caso di
corruzione si prova una copia content-addressed/backup; se nessuna è valida, la sessione si apre in
sola lettura con esportazione diagnostica e non esegue il payload.

## 17. Migrazione dei dati esistenti

Baseline verificata da conservare nelle fixture: archivio sessioni schema 2, payload
`CombatSessionJsonCodec` schema 1, libreria schede schema 13 e workspace recovery schema 2 (con
lettura del precedente schema 1). Sono formati distinti e vanno migrati separatamente, anche quando
finiscono nello stesso file di recovery.

### 17.1 Sessioni

Le sessioni attuali contengono varianti di stringhe come `srd-5.2.1` e `5.2.1`. Il migratore deve:

1. riconoscere tutte le stringhe legacy supportate;
2. associarle alla revisione built-in che riproduce esattamente il comportamento attuale;
3. creare `RulesetBinding` e snapshot runtime;
4. non ricalcolare combattenti, iniziativa, PF, condizioni o registro;
5. mantenere leggibili gli archivi schema 1 e 2;
6. scrivere il nuovo schema soltanto al successivo salvataggio esplicito/autosave sicuro;
7. conservare backup e messaggio di migrazione.

Il ripristino corrente ricostruisce lo stato e l'audit, ma non una cronologia Undo persistente: la
migrazione non deve promettere di recuperare comandi precedenti al caricamento. Deve invece
preservare checkpoint espliciti e confini di revisione, che sono dati persistenti.

Una sessione legacy deve produrre gli stessi risultati con lo stesso seed prima e dopo la migrazione.

### 17.2 Schede

- Progressione con `contentPackId = srd521-it`: binding allo standard SRD.
- Scheda manuale senza progressione: binding “SRD compatibile/manuale” per preservare i campi attuali.
- Capacità private: modulo homebrew locale creato automaticamente, senza cambiare gli ID.
- `passiveOverrides`: patch homebrew sul relativo elemento SRD.
- Classi enum e caratteristiche: adattate a ID dinamici tramite una tabella canonica.
- Testo scritto dall'utente: mai tradotto o sostituito.
- Voci SRD rigenerate: restano riferimenti allo standard, non vengono duplicate nel file utente.

### 17.3 Catalogo e Compendio

- Il catalogo combattimento resta inizialmente una proiezione rigenerabile.
- La fonte diventa `scheda + revisione`, non `scheda + singleton SRD globale`.
- Le capacità private migrano nel Catalogo regole ma l'API legacy continua a esporle finché gli
  editor non sono stati convertiti.
- Mappe e ritratti non cambiano quando resta la stessa geometria; board, fog, muri e posizioni
  partecipano invece al piano se il nuovo ruleset cambia topologia o unità canonica.

### 17.4 Workspace, template e campagne

- Il recovery migra tutte le sessioni aperte come gruppo e mantiene l'indice della sessione attiva.
- I template di sessione/incontro ricevono un binding esplicito; quelli legacy puntano alla revisione
  built-in equivalente allo SRD attuale.
- Il `Campaign.lockedRulesetManifest` viene tradotto nel nuovo binding senza perdere l'intento di
  blocco; campagne e workspace non devono creare due revisioni concorrenti dello stesso concetto.
- Una bozza Solo sessione referenziata dal recovery viene incorporata prima di rimuovere file legacy.
- Il migratore verifica anche i riferimenti da board e presentazione agli attori, non soltanto lo
  stato di combattimento.

### 17.5 Politica di errore

- Migrazione transazionale su copia.
- Validazione completa prima della sostituzione.
- Ripristino automatico del backup al fallimento.
- Report leggibile con entità non mappate.
- Possibilità di aprire in sola lettura e di esportare i dati grezzi.
- Nessuna sostituzione per somiglianza del nome quando manca un ID certo.

## 18. Architettura dei moduli

Struttura raccomandata:

```text
engine:rules-model
  ID, entità tipizzate, AST formule, patch, revisioni, validazione statica

engine:rules-runtime
  compilatore, evaluator, modifier pipeline, trigger, trace, capability profile

engine:rules-persistence
  repository, schema/migrazioni, hash canonico, import/export

engine:domain-model
  stato e comandi generici; nessun contenuto SRD

engine:core-engine
  orchestrazione degli incontri, audit, RNG, Undo; usa CompiledRuleset

engine:session-model
  GameSession, scene, tracker condivisi, binding, scope e checkpoint; il combattimento è opzionale

engine:sheet-model
  schede keyed by ID e schema della revisione

engine:board-model
  BoardGeometry e documenti indipendenti dalla topologia; adattatore square legacy

content:srd-5.2.1-it
  solo dati/adattatori verso rules-model

shared-ui
  RulesRepository e servizi iniettati; nessun import concreto Srd...
```

È possibile iniziare espandendo `engine:character-rules`, ma a regime il nome è troppo stretto:
azioni, mappa, morte e condizioni non sono soltanto regole del personaggio. Separare `rules-model` e
`rules-runtime` mantiene il dominio portabile e rende testabile la compilazione senza UI.

### Interfacce principali

- `RulesetRepository`: progetti, bozze, revisioni e lookup per hash;
- `RulesetResolver`: compone base, moduli e patch;
- `RulesetCompiler`: produce `CompiledRuleset` e capability profile;
- `RulesRuntime`: valida e risolve comandi;
- `RulesMigrationPlanner`: diff semantico e piano di riconciliazione;
- `RulesMigrationExecutor`: simulazione e applicazione atomica;
- `RulesTransactionCoordinator`: journal e commit coerente fra regole, sessioni, board e schede;
- `StateSyncPlanner`: proprietà dello stato, mapping e scritture sessione↔scheda;
- `SessionRulesContext`: snapshot compilato unico esposto a scene e incontri;
- `BoardGeometry`: coordinate, adiacenza, misura, ingombro e conversioni dichiarate;
- `RuleTraceFormatter`: spiegazioni localizzate senza cambiare i calcoli;
- `RulesetExportService`: bundle portabili;
- `RulesetCompatibilityService`: compatibilità di schede, template e CPU.

## 19. Cambiamenti nei componenti esistenti

### `AppRoot` e navigazione

- aggiungere `Destination.REGOLE` fra Compendio e Impostazioni;
- inizializzare `RulesetRepository` prima di roster e workspace;
- iniettare un `ContentRegistry` invece di usare direttamente `SrdBeasts`;
- propagare lingua al repository per le sole label;
- gestire bozze Regole non salvate all'uscita.

### `SheetViewModel` e creazione guidata

- sostituire `srdPack` globale con revisione della scheda;
- indicizzare servizi guidati per `runtimeHash + lingua`;
- generare classi, caratteristiche e sezioni dal modello;
- mostrare campi manuali per payload non supportati;
- aggiungere migrazione/duplicazione verso un altro regolamento.

### `EncounterBuilderViewModel`

- aggiungere lo step Regolamento e `selectedRulesetBinding`;
- filtrare/annotare compatibilità dei partecipanti;
- compilare lo snapshot prima di `fromCombatants`;
- passare `CompiledRuleset` o binding alla sessione;
- far dipendere griglia, unità, iniziativa e CPU dalla revisione.

### `GameSession`, `Campaign` e workspace

- introdurre `GameSession` come contenitore persistito e adattare gradualmente le sessioni correnti;
- collegare il `Campaign` esistente al workspace e usare il suo binding come default esplicito;
- mantenere `CombatSession` come stato di un incontro, non come radice di ogni modalità;
- salvare scene, tracker di sessione e override locali anche quando non esiste un combattimento;
- includere binding e draft locali nel crash recovery di tutte le schede aperte.

### `CombatSession`

- ricevere un runtime compilato invece di default di versione;
- spostare gradualmente le formule hard-coded dietro handler di regola;
- includere hash di revisione negli eventi;
- introdurre il comando transazionale di cambio ruleset;
- mantenere gli handler SRD legacy fino a equivalenza completa.

### Persistenza sessioni

- incrementare schema archivio e codec;
- serializzare binding, snapshot runtime e confini di revisione;
- leggere i vecchi schemi tramite migratori espliciti;
- aggiungere nome regolamento/revisione a `SessionSummary`.
- coordinare archivio nominato e workspace recovery tramite lo stesso journal di cambio revisione.

### `RosterViewModel` e sincronizzazione

- sostituire i sink diretti con un `StateSyncPlanner` consapevole di binding e scope;
- non salvare automaticamente una risorsa sconosciuta o semanticamente diversa nella scheda;
- presentare modifiche pendenti e mapping prima di aggiornare il Compendio;
- includere scritture e compensazioni nel checkpoint del cambio ruleset.

### Board e mappe

- estrarre coordinate, misura, adiacenza e ingombro dietro `BoardGeometry`;
- incapsulare `squaresPerSide`, maschere rettangolari, fog e visione nell'adattatore square;
- rendere esplicita la capability richiesta da una topologia;
- migrare board e Board Undo insieme allo stato di incontro quando cambia geometria.

### Template e contenuto guidato

- eliminare import concreti `Srd...` da template di sessione, progressione e dialoghi;
- risolvere esempi, scelte e forme attraverso `ContentRegistry + RulesetBinding`;
- marcare ogni template con revisione e capability richieste;
- mantenere adapter SRD finché i test di caratterizzazione sono equivalenti.

### CPU

- leggere un `RulesCapabilityProfile`;
- pianificare usando azioni e costi esposti dal runtime;
- rifiutare soltanto le capacità non comprese, non l'intera sessione se può suggerire mosse;
- mostrare chiaramente modalità Completa, Suggerimenti o Manuale.

## 20. Prove di generalità

Stato: sono presenti test sintetici eseguibili delle primitive 3.5-like e non-D20, inclusi pool d6,
budget nominati, formule, PE, risorse, trigger e valori testuali. I gate end-to-end descritti qui
restano volutamente più ampi: pack importabile completo, scene non-combat e UI priva di qualunque
campo D20 obbligatorio non sono ancora dichiarati conclusi.

### 20.1 Regolamento 3.5-like sintetico

Prima di dichiarare l'architettura aperta, un pack di test originale e privo di testo editoriale di
terzi deve dimostrare almeno:

- salvezze Fortitude/Reflex/Will invece dei sei saving throw correnti;
- BAB con attacchi iterativi;
- CA normale, touch e flat-footed;
- bonus tipizzati con regole di stacking;
- azioni standard, move, full-round, swift, immediate e free;
- attacchi di opportunità e area minacciata;
- critico con intervallo di minaccia, conferma e moltiplicatore;
- diagonali 5-10-5;
- skill ranks e prerequisiti;
- preparazione degli incantesimi per singolo slot;
- spell resistance e damage reduction;
- PF negativi e stabilizzazione diversi dai death save correnti;
- classi oltre l'elenco delle dodici SRD 5.2.1.

Il test non deve chiamarsi o presentarsi come un prodotto ufficiale: serve a verificare le primitive.
Superarlo dimostra una separazione reale, non soltanto la possibilità di cambiare alcuni numeri.

### 20.2 Regolamento non-D20 sintetico

La prova 3.5-like è necessaria ma insufficiente: potrebbe lasciare nascoste assunzioni comuni a tutte
le edizioni D20. Un secondo pack minimale, originale e classless, deve usare:

- pool di soli d6 con successi per dado invece di `d20 + modificatore`;
- quattro tratti creati nel pack e nessuna lista di classi o livelli;
- Stress e Conseguenze al posto di PF, CA e death save;
- iniziativa per fazione o scelta narrativa, senza ordine numerico obbligatorio;
- costi in token Momentum che persistono per l'intera sessione;
- esiti multipli: fallimento, successo con costo, successo e successo critico per soglia di successi;
- una scena sociale e una di esplorazione risolte senza creare un `CombatSession`;
- board opzionale senza griglia e almeno una regola Manuale con tracker strutturato.

Gate: il pack si crea/importa senza ricompilazione, gioca dall'inizio alla fine, salva e riapre con
lo stesso risultato deterministico, e nessuna UI obbliga a valorizzare classe, livello, CA, PF,
iniziativa numerica o slot. Solo l'insieme delle due prove autorizza a descrivere l'architettura come
aperta a regolamenti inventati e non soltanto ad altre versioni di D&D.

## 21. Roadmap implementativa

Ogni fase termina con app compilabile, dati migrabili e regressione SRD verde. Gli stati seguenti
sono verificati al 30 agosto 2026; “parziale” significa che i deliverable già presenti sono in uso,
mentre quelli non citati nella sezione 0 restano pianificati.

### Fase 0 — Baseline e contratti — sostanzialmente completata

Deliverable:

- inventario completo delle assunzioni hard-coded;
- scenari golden del comportamento SRD attuale;
- fixture di ogni schema sessione e scheda supportato;
- nomenclatura di ID canonici;
- decision record su hashing, patch, semantica runtime e fallback manuale;
- contratto di proprietà/scope dello stato e matrice sessione↔scheda;
- caratterizzazione separata di session archive, codec combattimento, workspace recovery e board.

Gate:

- stesso seed e stessi comandi producono stato/eventi attuali;
- nessun cambiamento di formato ancora obbligatorio.

### Fase 1 — Repository e Regole in sola lettura — completata per il corpus disponibile

Deliverable:

- `rules-model` e repository locale;
- adattatore dello SRD corrente a una revisione built-in;
- pipeline e manifest di copertura del corpus SRD completo, con gerarchia e crosswalk bilingue;
- nuova destinazione Regole;
- browser con ricerca, categorie, origine, sorgente e dipendenze;
- viste Sorgenti, Effettiva e Differenze senza duplicati ambigui;
- Standard marcato read-only;
- indice e backup atomici.

Gate:

- tutto il contenuto SRD consultabile in Regole su desktop e Android;
- nessun calcolo di battaglia cambiato.

### Fase 2 — Fork, patch e pubblicazione — completata

Deliverable:

- crea regolamento da SRD;
- duplica regola come homebrew;
- editor di testo, numeri, tabelle e modificatori semplici;
- diff con la base;
- validazione, bozza, pubblicazione e cronologia;
- filtri Standard/Homebrew;
- homebrew solo-sessione visibile globalmente.

Gate:

- lo standard non è mutabile neppure manipolando il file della bozza;
- una patch sopravvive a chiusura, cambio lingua e riavvio.

### Fase 3 — Binding delle sessioni — completata per sessioni di combattimento

Deliverable:

- passaggio Regolamento nella nuova sessione;
- binding esatto e snapshot runtime nei salvataggi;
- primo contenitore `GameSession` attorno al combattimento legacy;
- badge nella battaglia e riepilogo sessioni;
- migrazione automatica conservativa delle sessioni legacy;
- recovery del workspace con binding e snapshot esatto;
- export portabile con revisione.

Gate:

- una sessione continua a usare la propria revisione dopo che ne viene pubblicata una nuova;
- una sessione esportata si apre offline su un'installazione pulita.

### Fase 4 — Modifica delle sessioni avviate — parziale

Deliverable:

- diff semantico;
- planner di riconciliazione;
- simulazione, checkpoint e applicazione atomica;
- journal transazionale fra rules repository, sessione, board, recovery e schede;
- planner di sincronizzazione con scope e modifiche pendenti;
- evento di cambio revisione e confine Undo;
- ripristino del checkpoint;
- regole solo-sessione.

Gate:

- tutti i casi di PF, risorse, turno, iniziativa e condizioni hanno una scelta esplicita;
- un fallimento in qualunque punto recupera una combinazione coerente di tutti gli archivi;
- nessuna scheda con binding incompatibile viene modificata silenziosamente.

### Fase 5 — ID dinamici e schede modulari — implementazione operativa, rifiniture residue

Deliverable:

- wrapper ID al posto di enum per classi, statistiche, skill, danni e condizioni;
- adattatori legacy;
- schema scheda generato dal ruleset;
- `StatePersistencePolicy` con durata, proprietario autorevole e politica di sync;
- creazione/avanzamento guidati dalla revisione;
- migrazione o copia di scheda fra regolamenti;
- archivio capacità reindirizzato al Catalogo regole.

Gate:

- è possibile creare una settima caratteristica, una nuova classe e un nuovo tipo di danno senza
  ricompilare l'app;
- le schede SRD esistenti restano identiche.

### Fase 6 — Runtime formule, modificatori e action economy — runtime generico avanzato, adapter legacy e multi-scope parziali

Deliverable:

- compilatore AST;
- valori dimensionali, numeri esatti e `runtimeSemanticsVersion`;
- modifier pipeline e trace;
- risorse e turni definiti dai dati;
- tiri, critici, difese, danno e condizioni modulari;
- primi trigger generici;
- fallback assistito/manuale completo.

Gate:

- le golden SRD passano attraverso il nuovo runtime;
- nessuna regola critica dipende da nome localizzato o ID SRD speciale.

### Fase 7 — Movimento, magia e progressioni avanzate — geometria/GameSession operative; tattica avanzata parziale

Deliverable:

- topologia/costo movimento modulari;
- `BoardGeometry` con adattatore square e migrazione esplicita della board;
- cover, concealment, reach e opportunità;
- modelli di magia e risorse avanzati;
- livelli, XP, skill ranks e progressioni configurabili;
- classi/sottoclassi interamente dati;
- capability profile della CPU;
- scene/tracker non-combat nel `GameSession`.

Gate:

- il pack sintetico 3.5-like completa uno scontro e un avanzamento;
- il pack d6 sintetico completa scene sociali/esplorative senza campi D20 obbligatori;
- una regola non supportata degrada a Manuale senza corrompere stato o UI.

### Fase 8 — Ecosistema e hardening — import/export operativo; hardening avanzato pianificato

Deliverable:

- import/export `.onfall-ruleset`;
- merge a tre vie e aggiornamento base;
- limiti e fuzzing degli import;
- strumenti di test per autori;
- diagnostica prestazioni;
- documentazione per creare pack;
- gestione quarantena, conflitti di provenienza e revisioni con semantica futura.

Gate:

- pacchetti malformati non eseguono codice, non escono dalla directory e non bloccano l'app;
- migliaia di entità restano ricercabili e compilabili su un telefono supportato.

## 22. Strategia di test

### Unit test del modello

- ID, namespace e riferimenti;
- hash canonico indipendente dall'ordine non semantico;
- patch e override tipizzati;
- merge e conflitti;
- dipendenze mancanti e cicli;
- tipi dimensionali, unità e scope dello stato;
- versioni immutabili;
- regole di eliminazione/retention.

### Unit test runtime

- ogni operatore della DSL;
- limiti, overflow e arrotondamento;
- stacking;
- priorità e fasi;
- trigger e protezione dai loop;
- RNG deterministico;
- compatibilità fra versioni semantiche dell'interprete;
- trace esplicativa;
- Full/Assisted/Manual.

### Golden test SRD

- iniziativa e turni;
- attacchi, critici, salvezze e aree;
- danno, resistenze, cura, zero PF e morte;
- condizioni, concentrazione ed Exhaustion;
- slot, Pact slot e risorse;
- Action Surge, Wild Shape e attacchi extra;
- movimento, muri, linea di effetto e visione;
- creazione e avanzamento delle dodici classi.

Ogni golden esegue vecchio handler e nuovo runtime sugli stessi input finché il vecchio non viene
rimosso.

### Test di migrazione

- fixture distinte per session archive 1/2, combat codec 1, workspace recovery 1/2 e ogni schema
  schede ancora supportato fino al 13;
- custom ability, passive override, personaggio manuale e progressione SRD;
- migrazione interrotta e recupero backup;
- revisione mancante;
- homebrew archiviato ma ancora in uso;
- scheda e sessione su revisioni differenti, con e senza mapping;
- square→square con unità diversa e square→hex/no-grid senza convertitore;
- crash simulato dopo ogni stato del journal, con recupero o compensazione completa;
- round-trip desktop ↔ Android.

### Test end-to-end

1. crea homebrew da SRD;
2. modifica una formula e una classe;
3. pubblica;
4. crea personaggio con quella revisione;
5. avvia sessione selezionandola;
6. salva e riapre offline;
7. modifica il regolamento in combattimento;
8. riconcilia PF e turno;
9. ripristina il checkpoint;
10. verifica che scheda e board siano tornate allo stato coerente;
11. esporta e importa su archivio vuoto;
12. crea una sessione non-combat con il pack d6 e la riapre offline.

### Property e fuzz test

- nessuna risorsa fuori dai limiti;
- nessun valore derivato non finito;
- stessa revisione + stesso stato + stesso seed = stessi eventi;
- una patch non tocca campi non indirizzati;
- serializza/deserializza senza perdita;
- import casuali non causano esecuzione arbitraria o path traversal;
- un trigger non supera il budget massimo di eventi;
- numeri, dadi, collezioni e formule ostili rispettano tutti i limiti;
- canonicalizzazione equivalente produce lo stesso hash su JVM/desktop e Android;
- due salvataggi concorrenti non perdono silenziosamente una revisione.

### UI e accessibilità

- desktop ampio/stretto e Android;
- filtri combinati e ricerca;
- editor con tastiera e screen reader;
- bozze non salvate;
- diff leggibile senza affidarsi soltanto al colore;
- badge Standard/Homebrew e livelli di automazione;
- distinzione fra Sorgenti, Vista effettiva e Differenze;
- analisi “Dove è usata” e modifiche pendenti verso le schede;
- cambio lingua senza modificare dati dell'utente;
- liste grandi e memoria limitata.

## 23. Criteri di accettazione finali

La funzionalità può dirsi completa quando:

1. Regole è una destinazione principale accanto al Compendio.
2. Tutte le regole SRD distribuite sono consultabili e non modificabili direttamente.
3. Modificare lo standard crea sempre un homebrew derivato con diff e origine.
4. Le regole homebrew sono sempre trovabili tramite il filtro dedicato.
5. L'utente può creare senza ricompilazione almeno una caratteristica, skill, difesa, tipo di danno,
   condizione, risorsa, azione, classe, progressione e regola di morte.
6. Ogni nuova sessione permette di scegliere Standard o una revisione homebrew.
7. Il salvataggio conserva ID, revisione, hash e snapshot necessario.
8. Una sessione non cambia quando il suo homebrew viene ulteriormente modificato.
9. Una sessione già iniziata può passare a un'altra revisione con anteprima, riconciliazione,
   checkpoint e audit.
10. Le sessioni e schede legacy si aprono senza variazioni di stato o risultati.
11. Le regole manuali restano visibili e giocabili.
12. La CPU dichiara limiti e degrada in modo sicuro.
13. Un pack sintetico 3.5-like dimostra action economy, difese, critici, stacking e progressione
    differenti.
14. Nessun modulo generico o UI importa direttamente classi concrete del pack SRD.
15. Import ed export funzionano offline e non eseguono codice arbitrario.
16. Una sessione sociale/esplorativa può esistere e salvarsi senza creare dati fittizi di
    combattimento.
17. Una risorsa di sessione non modifica una scheda; una risorsa del personaggio viene sincronizzata
    soltanto con binding compatibile o mapping approvato.
18. Un cambio di revisione è transazionale anche per board, recovery e schede collegate, inclusa la
    ripresa dopo un crash simulato.
19. La semantica dell'interprete è versionata e lo stesso snapshot/seed produce gli stessi eventi
    dopo un aggiornamento compatibile dell'app.
20. Il pack d6 classless dimostra tiri, salute, iniziativa e scene non-D20 senza campi SRD obbligatori.
21. Un cambio di topologia non distrugge né converte silenziosamente posizioni, muri, fog o misure.
22. La Vista effettiva mostra una sola regola vincente e rende sempre tracciabile quale standard o
    homebrew l'ha prodotta.

## 24. Rischi e contromisure

| Rischio | Contromisura |
|---|---|
| Scope enorme | Fasi verticali, fallback Manuale e golden test a ogni estrazione |
| Motore trasformato in mappe non tipizzate | Payload tipizzati, compilazione in viste efficienti e ID dinamici soltanto ai confini |
| Homebrew rompe salvataggi | Revisioni immutabili, snapshot, migratore transazionale e checkpoint |
| Cambio ruleset lascia archivi incoerenti | Journal multi-file, staging, versioni ottimistiche e recovery testato a ogni passaggio |
| Sessione aggiorna la scheda sbagliata | Scope/proprietà dello stato, binding compatibile, mapping esplicito e coda pendente |
| Conflitti fra moduli | Patch tipizzate, precedenza esplicita e validatore |
| Formule lente o infinite | AST limitato, compilazione/cache, budget di nodi ed eventi |
| Stesso JSON cambia significato dopo un update | `RuntimeSemanticsVersion`, interpreti compatibili o migrazione esplicita |
| Overflow o errori di unità | Numeri esatti controllati, tipi dimensionali e arrotondamento dichiarato |
| CPU prende decisioni sbagliate | Capability profile e fallback Suggerimenti/Sandbox |
| Aggiornamento base cambia il gioco | Rebase solo esplicito e merge a tre vie |
| Contenuto senza diritti | Metadati licenza obbligatori e separazione fra supporto tecnico e contenuto distribuito |
| UI troppo complessa | Modalità Semplice/Avanzata, template e spiegazioni “Perché?” |
| Mobile sovraccarico | Master/detail, editor per sezioni, lazy lists e compilazione fuori dal thread UI |
| Perdita di testo utente al cambio lingua | Separazione testo pack/testo utente e fallback localizzato dichiarato |
| “Qualunque regolamento” resta in realtà D20 | Secondo pack classless d6 e scene senza `CombatSession` come gate |
| Cambio griglia perde la board | Migrazione topologica separata, nessuna conversione implicita e duplicazione/checkpoint |
| Pack corrotto o con identità ambigua | Verifica hash, quarantena e conflitto di provenienza senza overwrite |

## 25. Priorità di prodotto

### P0 — indispensabile

- repository versionato;
- Regole read-only;
- fork homebrew e filtri;
- scelta regolamento per nuova sessione;
- binding e snapshot;
- versione semantica del runtime e hashing canonico;
- modifica sicura di sessioni avviate;
- journal multi-file e recovery coerente;
- scope dello stato e sincronizzazione scheda sicura;
- contenitore `GameSession` compatibile con il combattimento legacy;
- ID dinamici per i concetti principali;
- fallback Manuale;
- migrazione senza perdita.

### P1 — apertura reale ad altre edizioni

- DSL, stacking e trace;
- action economy, critici e difese modulari;
- schede e progressioni generate;
- movimento e magia modulari;
- compatibilità/migrazione personaggi;
- pack sintetico 3.5-like;
- pack sintetico classless d6 e scene non-combat;
- geometria board astratta con adattatore square;
- import/export.

### P2 — ecosistema avanzato

- editor visuale completo dei trigger;
- marketplace o condivisione, se autorizzati in futuro;
- firme degli autori;
- collaborazione e merge multiutente;
- renderer di scheda personalizzati;
- SDK per nuove primitive fidate, separato dai normali pack dati.

## 26. Primo incremento — completato e superato

La fetta verticale originariamente consigliata è ora operativa: repository, canonicalizzazione,
revisione SRD immutabile, sezione Regole, fork, binding, snapshot, scelta nel wizard, modifica live,
Undo e protezione delle sincronizzazioni incompatibili sono coperti. L'implementazione è già andata
oltre quel traguardo con classi/ID aperti, formule, valori tipizzati, tabelle, risorse, trigger e
action economy generici. Anche le istanze di stato keyed-by-scope, la policy di
durata/sincronizzazione, il fan-out atomico multi-target e il contenitore `GameSession` non-combat
sono operativi e retrocompatibili. Il confine residuo importante è il journal realmente multi-file
fra repository, workspace, board e più schede: non va confuso con le transazioni già atomiche
all'interno di una singola sessione.

## 27. Tracciabilità della richiesta

| Richiesta | Risposta progettuale |
|---|---|
| Non giocabile soltanto con SRD | Runtime versionato, moduli, ID dinamici e fallback manuale |
| Qualunque edizione/regolamento simile | Primitive generiche più pack sintetici 3.5-like e classless d6 come gate |
| Regole e modificatori modificabili | RuleEntity, formule, ModifierDefinition, patch e editor |
| Classi modulari | ID aperti, proiezione guidata senza eredità SRD implicita e sezioni scheda data-driven persistite |
| Sezione Regole accanto al Compendio | Nuova destinazione principale e confine Regole/Compendio |
| Standard read-only ma modificabile | Crea variante/fork copy-on-write, mai overwrite |
| Homebrew sempre visibile | Archivio globale, badge e filtro Standard/Homebrew |
| Scelta a ogni nuova sessione | Passaggio Regolamento nella procedura |
| Sessioni avviate modificabili | Migrazione live con pausa, diff, checkpoint, riconciliazione e audit |
| Gioco non limitato al combattimento | `GameSession` con scene, tracker e incontri opzionali |
| Dati persistenti coerenti | Scope/proprietà, sync plan e journal fra sessione, board, recovery e schede |
| Mappe di sistemi diversi | `BoardGeometry` e migrazione topologica mai implicita |
| Accuratezza e apertura futura | Versioni immutabili, DSL sicura, manual fallback, capability profile e test |

## 28. Mappa iniziale dei file coinvolti

Questa mappa rende il piano collegabile al repository attuale; nuovi file e nomi definitivi verranno
decisi nei singoli incrementi.

| Area | File o modulo attuale | Intervento principale |
|---|---|---|
| build | `settings.gradle.kts` | registrare rules-model, rules-runtime e rules-persistence |
| profili | `engine/domain-model/.../rules/RulesetProfile.java` | separare progetto selezionabile e revisione immutabile |
| manifest | `engine/domain-model/.../rules/RulesetVersionManifest.java` | aggiungere hash, layer, dipendenze e schema runtime |
| campagna | `engine/domain-model/.../campaign/Campaign.java` | collegare binding/default al workspace senza aggiornamenti retroattivi |
| sessione generale | nuovo `engine/session-model` | introdurre GameSession, scene, tracker, scope e incontri opzionali |
| combattimento | `engine/core-engine/.../CombatSession.java` | ricevere CompiledRuleset ed estrarre gli handler SRD |
| stato | `engine/domain-model/.../combat/CombatState.java` | binding, runtime hash e confini di revisione |
| turni | `engine/domain-model/.../combat/TurnBudget.java` | sostituire campi fissi con budget compilato e adapter legacy |
| tipi chiusi | `engine/domain-model/.../combat/DamageType.java`, `ConditionType.java`, `SaveAbility.java` | migrare a ID dinamici mantenendo codec legacy |
| classi | `engine/character-rules/.../CharacterRules.kt` | migrare enum e payload SRD-specifici verso rules-model |
| progressione | `engine/character-rules/.../Progression.kt` | soglie, livelli, prerequisiti e scelte guidati dalla revisione |
| scheda | `engine/sheet-model/.../CharacterSheet.kt` | binding, valori keyed by ID e schema dinamico |
| sync scheda | `shared-ui/.../roster/RosterViewModel.kt`, `shared-ui/.../App.kt` | sostituire i sink diretti con mapping/scope e transazione |
| creazione guidata | `engine/sheet-model/.../GuidedCharacterService.kt` | rimuovere rami per classe/feature SRD e usare lo schema compilato |
| catalogo | `engine/sheet-model/.../AbilityCatalog.kt` | diventare vista compatibile del Catalogo regole |
| archivio schede | `engine/sheet-model/.../SheetStore.kt` | nuovo schema e migrazione transazionale |
| sessioni JSON | `engine/persistence-json/.../CombatSessionJsonCodec.java` | codec revisionato per binding e stato generico |
| archivio sessioni | `engine/persistence-json/.../SessionArchiveStore.java` | snapshot, summary del ruleset e bundle portabile |
| workspace recovery | `shared-ui/.../session/WorkspaceRecoveryStore.kt` | serializzare GameSession, binding, checkpoint e sync pendenti |
| board | `engine/board-model/.../BoardDocument.java`, `engine/domain-model/.../space/MapGrid.java` | introdurre BoardGeometry e migrazioni topologiche |
| codec board | `engine/persistence-json/.../BoardDocumentJsonCodec.java` | versione schema e partecipazione al journal |
| pack SRD | `content/srd-5.2.1-it` | adattatore generico e corpus completo bilingue |
| accesso pack | `shared-ui/.../content/SrdPack.kt` | sostituire singleton concreto con repository/registry iniettato |
| navigazione | `shared-ui/.../App.kt` | aggiungere Regole e inizializzare i servizi |
| nuova partita | `shared-ui/.../encounter/EncounterBuilderViewModel.kt` | step Regolamento e compatibilità partecipanti |
| schermata partita | `shared-ui/.../encounter/EncounterBuilderScreen.kt` | selettore, riepilogo e warning |
| template sessione | `shared-ui/.../content/SessionTemplates.kt` | binding/capability invece di assunzioni SRD e square |
| progressione UI | `shared-ui/.../sheet/SrdProgressionDialog.kt`, `CharacterProgressionOverview.kt` | editor generato dal ruleset e adapter legacy |
| Compendio | `shared-ui/.../roster/RosterScreen.kt` | binding schede e collegamento al Catalogo regole |
| capacità | `shared-ui/.../abilities/AbilityArchive.kt` | migrare editor e filtri nella nuova sezione |
| workspace | `shared-ui/.../session/SessionWorkspace.kt` | cambio revisione, checkpoint e recovery |
| salvataggio | `shared-ui/.../session/SessionManager.kt` | dirty state e autosave delle migrazioni |
| battaglia | `shared-ui/.../state/BattleViewModel.kt` | comandi generici, trace e livello di automazione |
| CPU | `engine/core-engine/.../ai` | capability profile e fallback dichiarato |

Prima di modificare un file ad alto accoppiamento come `CombatSession.java` o `CharacterSheet.kt`, la
fase relativa deve aggiungere un test di caratterizzazione per ogni ramo che verrà spostato. I vecchi
handler vengono eliminati soltanto quando il nuovo runtime supera gli stessi scenari golden.

La direzione architetturale fondamentale è quindi: **Onfall non conosce più “le regole SRD”; conosce
una revisione compilata di regole. Lo SRD è una revisione inclusa, protetta e perfettamente testata fra
le altre possibili.**
