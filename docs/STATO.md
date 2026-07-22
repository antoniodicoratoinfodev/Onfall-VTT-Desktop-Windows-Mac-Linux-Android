# Stato dell'implementazione

Mappa fra il documento di progetto (`D&D fight tools.md`, 2461 righe, sei fasi) e cio' che gira
davvero. Il documento stima il solo MVP desktop in 4–6 mesi: qui c'e' la vertical slice giocabile,
le schede complete e una parte delle regole 2024, non la roadmap intera.

Le voci sono verificate contro il codice, non dichiarate a memoria.

## Operativo

### Motore (Java, 196 test)

| Meccanica | Stato |
|---|---|
| Iniziativa | individuale, condivisa, punteggio statico, ordine esplicito per le parita' |
| Test d20 | Normale / Vantaggio / Svantaggio, 1 e 20 naturali |
| Critici | raddoppio dei dadi di danno, modificatori applicati una volta sola |
| Danno | componenti tipizzate separate nel log |
| Resistenza | dimezza una sola volta, arrotondando per difetto |
| Vulnerabilita' / immunita' | raddoppio / annullamento della componente |
| PF temporanei | assorbono per primi, non si sommano, si sceglie il valore maggiore |
| Condizioni | istanze separate per fonte e scadenza; immunita' alle condizioni |
| Concentrazione | una sola per creatura; CD pari al maggiore fra 10 e meta' danno, massimo 30 |
| **Tiri salvezza contro morte** | 10+ successo, tre successi Stable, tre fallimenti morte |
| **20 e 1 naturali contro morte** | 20 fa recuperare 1 PF, 1 vale due fallimenti |
| **Danni a 0 PF** | un fallimento, due se il colpo e' critico; il danno elimina Stable |
| **Danno massiccio** | morte immediata se il residuo raggiunge i PF massimi |
| **Stabilizzazione e knockout** | Stable senza tiri; knockout a 1 PF senza tiri contro morte |
| **Exhaustion** | livelli 1–6, −2 ai D20 Test e −5 piedi per livello, morte al sesto |
| Budget del turno | Azione, Bonus, Reazione, movimento, interazione, attacchi, flag slot |
| **Turni simultanei** | i pareggi d'iniziativa possono giocare insieme; budget resta individuale |
| **Correzione in corsa** | nuova revisione `+tavolo.N`, evento con ogni statistica prima/dopo, annullabile |
| Annullamento | ripristina anche lo stato del generatore casuale e quello di morte |
| Registro | append-only, include l'evento di annullamento |
| Determinismo | seed esplicito, replay riproducibile |
| Persistenza | JSON versionato, scritture atomiche, backup; legge anche i salvataggi precedenti |
| Budget XP 2024 | tutti i 20 livelli, somma diretta, nessun moltiplicatore 2014 |

### Schede (Kotlin, `engine/sheet-model`)

- **Scheda del personaggio 2024** modellata sui campi della scheda ufficiale italiana:
  intestazione, CA e scudo, PF con temporanei, Dadi Vita, tiri contro morte, sei caratteristiche,
  diciotto abilita', Ispirazione Eroica, Armor Training, competenze in armi e strumenti, tabella
  armi, privilegi di classe, tratti della specie, talenti, blocco da incantatore con slot 1–9,
  aspetto, storia, allineamento, lingue, equipaggiamento, sintonia e denari;
- **stat block del mostro 2024/2025**, versione ridotta: taglia, tipo, tag, allineamento, CA,
  iniziativa con punteggio statico distinto, PF medi e formula, cinque velocita', caratteristiche
  con tiri salvezza, abilita', difese tipizzate, Gear, sensi, lingue, GS, XP base e in tana, bonus
  di competenza, e le cinque sezioni operative separate;
- valori derivati calcolati e non scrivibili: modificatori, tiri salvezza, bonus di abilita',
  iniziativa, Percezione passiva, CD e bonus d'attacco degli incantesimi;
- serializzazione `kotlinx.serialization`, come prescrive il documento.

### Interfaccia (Kotlin + Compose Multiplatform)

- schermata di battaglia: squadra a sinistra, **mappa tattica al centro**, nemici a destra,
  registro in basso; bersaglio e attore di turno in targhe compatte agli angoli della mappa;
- barre PF animate con segmento distinto per i PF temporanei, ritratti vettoriali con anello dei
  PF, numeri di danno fluttuanti, chip delle condizioni in italiano;
- ritratti caricabili dall’utente per personaggi e mostri, usati anche sui segnaposti della mappa;
- **Compendio unificato**: personaggi come schede complete, creature come stat block, in un solo
  posto; il catalogo da combattimento e' interamente derivato dalle schede;
- striscia dell'ordine dei turni in cima al centro, con indicatore ⇄ sui turni condivisi;
- pulsante **Edit**: quando e' attivo, un doppio clic su un campo lo rende modificabile
  (Invio conferma, Esc annulla); fuori dalla modalita' il doppio clic non fa nulla, per non
  alterare una scheda per sbaglio a meta' combattimento;
- le correzioni fatte in battaglia rientrano nel catalogo del Compendio;
- registro eventi che risale sempre all'ultimo aggiornamento, con la riga "ORA" in evidenza;
- **menù Sessione**: salvataggio con nome, elenco delle partite salvate con round, combattenti e
  stato, riapertura ed eliminazione; un file danneggiato viene elencato come tale senza impedire
  di aprire gli altri;
- due shell distinte, densa su desktop e compatta su telefono, sullo stesso motore.

## Mappa tattica

La posizione vive **nel motore**, non nell'interfaccia. Il paragrafo 7 lo richiede: senza coordinate
il motore non puo' risolvere portata e gittata, e una mappa che mostra una cosa mentre il motore ne
valida un'altra sarebbe finta.

- griglia con dimensioni e **scala configurabili** (5, 10, 20, 50 piedi per casella): la stessa
  interfaccia regge la scaramuccia in una stanza e la battaglia campale;
- distanza con la metrica della griglia 2024 — la diagonale conta come una casella — misurata
  **fra i bordi**, cosi' una creatura Grande minaccia da tutto il suo spazio;
- segnaposti rotondi con ingombro proporzionale alla taglia — la taglia si imposta nel Compendio,
  non al tavolo — anello dei punti ferita, immagine caricata dall'utente e iniziali come ripiego;
- il movimento passa dal motore: consuma il budget vero, rifiuta le caselle occupate e i bordi;
- gli attacchi validano la portata **solo quando entrambi sono posizionati** — senza coordinate
  complete il motore non inventa una distanza, come impone il documento;
- zoom regolabile con cursore e pulsanti, da una casella minuscola a una molto grande;
- sfondo caricabile, ridimensionato sulla griglia: e' la griglia a definire la scala, non l'immagine.

L'ingombro e' memorizzato come **caselle per lato**, non come taglia di creatura: al motore serve
la geometria, il vocabolario di gioco resta nella scheda. Senza mappa configurata l'incontro si
comporta esattamente come prima che la mappa esistesse.

## Immagini dell'utente

Le immagini vengono **copiate** in `~/.onfall/images/` e referenziate per nome: spostare o
cancellare il file originale non rompe una scheda. La decodifica usa `decodeToImageBitmap`, che
Compose Multiplatform espone su desktop e Android, quindi non serve codice di piattaforma.

Restano deliberatamente **locali**. Il paragrafo 11 stabilisce che gli asset posseduti privatamente
dall'utente sono esclusi di predefinito dagli export condivisibili — copiare l'illustrazione di un
manuale non la rende distribuibile — quindi l'archivio immagini vive fuori dal pacchetto di
esportazione e va allegato solo con una scelta esplicita.

## Salvataggio e ripresa delle sessioni

Ogni sessione e' un file JSON in `~/.onfall/sessions/`, scritto in modo atomico con copie di
backup: un'interruzione durante il salvataggio non lascia mai una partita troncata.

Il file contiene **tutto il necessario a riprendere il tavolo**:

- stato del combattimento con mappa, griglia, scala, segnaposti e sfondo;
- registro eventi completo, quindi la cronologia non si perde;
- **seme e stato del generatore casuale**: riaprendo una sessione i tiri futuri sono gli stessi
  che si sarebbero ottenuti continuando, non una nuova sequenza;
- punti ferita, condizioni, concentrazione, Exhaustion e tiri contro morte;
- le scelte di presentazione che il motore non conosce — bersaglio inquadrato, modo di tiro,
  ingombro dei segnaposti non ancora collocati.

Il nome scelto dall'utente resta leggibile dentro il file, mentre il nome del file viene
sanificato: una sessione puo' chiamarsi "Cripta dei Predoni — sera 3" senza che finisca tale e
quale sul filesystem.

## Schede e compendio unificati

Un personaggio giocante viveva in due posti scollegati: un'entrata leggera nel compendio, con CA,
PF, iniziativa e capacita' modificabili a mano, e una scheda completa dove quegli stessi valori sono
**derivati** — l'iniziativa dalla Destrezza, il tiro salvezza su Costituzione da Costituzione piu'
competenza, le capacita' dalla tabella armi. Le due copie potevano contraddirsi.

Ora sono un solo sistema:

- la **libreria delle schede e' il roster**; il catalogo da combattimento (`catalog.json`, quello
  che la battaglia consuma) e' interamente **derivato** e viene rigenerato a ogni modifica;
- non esiste piu' un dato di personaggio indipendente dalla scheda: la scheda sovrascrive per intero
  la parte del compendio relativa ai personaggi giocanti, perche' il compendio non ne tiene piu' una
  copia modificabile a parte;
- un personaggio si redige con la scheda completa, una creatura con lo stat block; l'editor leggero
  ridondante e' stato ritirato;
- le correzioni fatte in battaglia confluiscono nella scheda autorevole, non solo nel catalogo.

Il roster iniziale rispecchia l'incontro dimostrativo con gli stessi identificatori, quindi le
statistiche derivate dalle schede coincidono con quelle della battaglia (verificato dai test).

## Fotografie e correzioni al tavolo

Il paragrafo 3 del documento vuole che un combattimento usi una fotografia degli attori e che
modificare in seguito la scheda originale non alteri retroattivamente uno scontro gia' giocato.
La correzione durante il gioco va nel verso opposto e consentito — e' lo scontro a correggere la
scheda — ma resta tracciabile su tre livelli:

1. **Revisione derivata.** Una fotografia corretta al tavolo passa da `1.0.0` a `1.0.0+tavolo.1`,
   poi `+tavolo.2` e cosi' via. La revisione originale non viene mai sovrascritta, e
   `CombatantSnapshot.tableEdited()` distingue sempre una fotografia corretta da quella di catalogo.
2. **Evento completo.** `COMBATANT_EDITED` registra nome, CA, PF massimi, velocita', modificatore e
   punteggio d'iniziativa e tiro salvezza su Costituzione, sia prima sia dopo, piu' entrambe le
   revisioni. Il registro basta da solo a sapere quale fotografia era in uso in ogni punto della
   partita, invece di mostrare soltanto quella finale.
3. **Annullamento.** La correzione si annulla come ogni altro comando e ripristina anche la
   revisione; l'evento resta comunque scritto, perche' il registro e' append-only.

L'invariante sta nel dominio, non nell'interfaccia: `CombatantSnapshot.withStats(...)` riporta da
se' i punti ferita iniziali entro un massimo abbassato e incrementa la revisione, quindi vale per
qualunque chiamante e non solo per la schermata di battaglia.

## Presente nel motore, non ancora esposto dall'interfaccia

- **Encounter Builder** — `engine/core-engine/.../encounter/`: budget, categorie, avvisi;
- **grafo di sessione** — `domain/campaign/`: `Campaign`, `SessionPlan`, `Scene`, `SceneTransition`;
- **import / export** del catalogo.

## Non implementato

Il motore copre il combattimento base 2024 piu' morte ed Exhaustion. Restano fuori:

- Weapon Mastery: Cleave, Graze, Nick, Push, Sap, Slow, Topple, Vex, e la proprieta' Light;
- Reazioni reali: esiste la casella di budget, non la coda di trigger interrompibile;
- attacchi di opportunita' e azione Ready;
- Azioni Leggendarie: esiste il costo, non il pool ne' la finestra; nessun `LairContext`;
- Grapple, Shove, Unarmed Strike;
- Riposi Breve e Lungo, spesa dei Dadi Vita, recupero delle risorse;
- azioni generali 2024 oltre all'attacco (Dash, Disengage, Dodge, Help, Hide, Influence, Magic,
  Ready, Search, Study, Utilize);
- lancio degli incantesimi e consumo degli slot dentro il combattimento (la scheda li registra,
  il motore non li applica);
- copertura, linea di vista, terreno difficile e aree di effetto sulla mappa
  (la griglia e le distanze ci sono, questi strati no);
- character builder 2024: classi, sottoclassi, background, specie, talenti, validazione di
  Armor Training;
- pacchetti contenuto SRD italiano e inglese;
- `RulesetProfile` e versionamento dei contenuti esposti all'utente;
- simulazione Monte Carlo, controller IA, modalita' headless;
- sincronizzazione, multiplayer, account.

## Verifiche eseguite

- `./gradlew test` — 196 test di motore e dominio, 0 falliti;
- `./gradlew :shared-ui:desktopTest` — 35 test dello strato di presentazione, 0 falliti;
- `./gradlew :engine:sheet-model:test` — 25 test su valori derivati e archivio immagini, 0 falliti;
- `./gradlew :desktop-app:run` — avvio reale verificato, nessuna eccezione;
- `./gradlew :android-app:assembleDebug` — APK prodotto;
- catalogo e archivio schede scritti e riletti da `~/.onfall/`.

## Nome commerciale

Il nome scelto e' **Onfall** (parola inglese arcaica per "assalto, onset"). Le ricerche in rete
non hanno trovato software, giochi o strumenti da tavolo con questo nome, ma **una ricerca non e'
una verifica di marchio** e il paragrafo 17 del documento richiede un controllo legale specifico
prima di pubblicare. Il nome visibile vive in `AppIdentity.displayName`; lo slug tecnico (cartella
dati `~/.onfall`, `applicationId`, nome del progetto) lo rispecchia.
