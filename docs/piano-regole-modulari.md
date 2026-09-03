# Contratto dei regolamenti modulari di Onfall

Stato: implementato e verificato il 3 settembre 2026
Ambito: motore, persistenza, sessioni, homebrew e pack modulari su desktop e Android

Questo è il documento autorevole per ciò che il sistema di regole rappresenta ed esegue. Descrive
lo stato corrente e i limiti ancora reali; non conserva cronologie di sessione o roadmap concluse.
L'esperienza di creazione è definita in
[`progetto-costruttore-regole-guidato.md`](progetto-costruttore-regole-guidato.md), mentre la scelta
lossless dell'authoring è registrata nell'[ADR 0001](adr/0001-authoring-regole-lossless.md).

## 1. Obiettivo e confine

Onfall deve poter usare:

- lo SRD 5.2.1 incluso e protetto;
- varianti homebrew derivate da uno standard;
- regolamenti indipendenti creati da una fondazione realmente vuota;
- composizioni riproducibili di moduli;
- meccaniche D20 differenti e sistemi non-D20;
- regole assistite o manuali quando non esiste ancora una primitiva automatica.

«Supportare un regolamento» non significa eseguire codice arbitrario del suo autore. Il motore
interpreta un linguaggio dati sicuro e finito. Una meccanica non automatizzata può comunque essere
descritta, mostrata, tracciata e risolta al tavolo.

## 2. Invarianti

1. Gli standard inclusi sono sempre in sola lettura.
2. Ogni modifica avviene in una bozza homebrew separata.
3. Una revisione pubblicata è immutabile e identificata dal proprio hash canonico.
4. Una sessione conserva binding e snapshot della revisione esatta usata.
5. Lo SRD non è mai un fallback implicito per un regolamento autonomo.
6. Formule, trigger ed effetti non eseguono bytecode o script importati.
7. Il testo naturale non determina la semantica: la sola verità eseguibile è il modello compilato.
8. Import, pubblicazione e cambio di revisione ripetono la validazione anche senza UI.
9. Modifiche di stato sono deterministiche, auditate e annullabili.
10. Dati e salvataggi precedenti restano leggibili tramite compatibilità esplicita, non euristiche sul
    nome del regolamento.

## 3. Ciclo di vita

```text
standard o fondazione vuota
            ↓ fork
bozza modificabile + metadati di authoring
            ↓ compilazione e preflight
revisione immutabile ─────→ binding/snapshot di sessione
            ↑
base + moduli ordinati + risoluzioni di conflitto
```

- `RulesetProject` identifica la linea di un regolamento.
- `RulesetDraft` contiene patch e aggiunte modificabili.
- `RulesetRevision` è la versione giocabile immutabile.
- `RulesetModule` modifica una base senza sostituirla implicitamente.
- `RulesetCompositionLock` registra base, moduli esatti, ordine e vincitori dei conflitti.
- `RulesetBinding` collega una scheda o sessione alla revisione esatta.

Il comando **Crea una variante** deriva dallo standard o da una revisione pubblicata. **Nuovo
regolamento vuoto** usa invece una fondazione generica con zero entità e non eredita classi, dadi,
statistiche, danni, condizioni o licenze SRD.

## 4. Modello eseguibile

Una `RuleEntity` possiede ID stabile, tipo, nome e descrizione localizzati, origine, licenza, livello
di automazione, stato abilitato, tag e attributi. Gli ID sono aperti: il runtime non richiede gli
identificatori dello SRD per classi, statistiche, skill, danni o condizioni.

Le principali famiglie compilate sono:

| Famiglia | Capacità correnti |
|---|---|
| Numeri | statistiche, skill, difese, salvezze, formule derivate, min/max e arrotondamento |
| Valori | `NUMBER`, `BOOLEAN`, `TEXT` e `REFERENCE`, dominio ammesso e mutabilità |
| Casualità | dadi, pool, percentile, tabelle e aggregazioni tramite `RANDOMIZER` |
| Tiri | totale, bersaglio, confronto, naturali, minaccia/conferma, critico, opposti ed esiti |
| Effetti | modificatori statici e dinamici, condizioni, priorità, fasi, cap/floor e stacking |
| Stato | risorse, track, condizioni, owner attivi e budget nominati |
| Azioni | costi atomici, action economy, destinatari, trigger ed eventi arbitrari |
| Progressione | curve PE multiple, livelli oltre 20 e track nominate come BAB o caster level |
| Personaggio | classi, feature, competenze, progressione e sezioni scheda generate dai dati |
| Sessione | salute, movimento, procedure di scena e azioni multi-target |
| Tassonomie | tipi di danno e condizioni con ID dichiarati dal regolamento |
| Manuale | testo e assistenza al tavolo senza falsa automazione |

`ROLL` e `RANDOMIZER` sono distinti: il primo risolve una prova, il secondo produce soltanto il
valore casuale. Una ricetta UI può crearli insieme come gruppo atomico senza esporre il componente
tecnico all'utente.

## 5. Formule, riferimenti e validazione

`RuleFormula` espone un AST immutabile usato dallo stesso parser e valutatore del runtime. Supporta
numeri, valori referenziati, operatori e funzioni entro budget dichiarati. La compilazione:

- rifiuta sintassi, tipi e funzioni incompatibili;
- controlla riferimenti mancanti o del tipo sbagliato;
- ordina le dipendenze fra valori, pool, risorse e modificatori;
- rileva cicli prima della pubblicazione;
- impone limiti a profondità, dimensioni e lavoro eseguito;
- produce una sorgente canonica deterministica quando l'AST viene modificato.

La UI ricava i candidati numerici dalle capability del compilatore. Valori testuali, risorse e track
non vengono proposti come riferimenti numerici diretti se il runtime non li accetterebbe.

## 6. Modificatori ed effetti

I modificatori statici condividono una singola pipeline fra risultato e `valueTrace()`. Possono
dichiarare fase, priorità e strategia di stacking, inclusi stack, valore migliore/peggiore, fonte
unica ed esclusività.

Gli effetti dinamici possono:

- cambiare un numero o una risorsa;
- aggiungere o rimuovere condizioni;
- impostare valori tipizzati;
- scegliere `SELF`, `TARGET` o `SESSION` come destinatario;
- dipendere da condizioni e livello;
- essere attivati da azioni o eventi.

Costi ripetuti sullo stesso budget o risorsa vengono sommati prima dell'esecuzione. Se un costo o
effetto non è valido, l'azione non applica scritture parziali.

## 7. Stato, scope e durata

Lo stato generico è indirizzato da `(tipo scope, ID scope, ID regola)`. Gli scope disponibili sono
sessione, attore, oggetto, scena e campagna. `StatePersistencePolicy` dichiara durata, proprietario,
evento di reset e sincronizzazione.

I confini di azione, turno, scena, incontro, sessione o campagna scadono lo stato in modo atomico.
Snapshot, audit e Undo conservano gli indirizzi completi. Durante un cambio di revisione, quantità e
stack vengono ricondotti ai nuovi limiti e i valori incompatibili tornano al default dichiarato.

## 8. Sessioni e schede

Ogni nuova sessione sceglie una revisione pubblicata e ne incorpora binding, snapshot eseguibile e
stato. Una sessione aperta può cambiare revisione solo con pausa, preflight, migrazione
conservativa, audit e possibilità di Undo.

Ogni scheda usa il proprio regolamento. Se la revisione esatta non è installata, nome e snapshot
restano leggibili, ma catalogo, progressione e ricalcolo vengono bloccati. La sincronizzazione tra
combattimento e scheda è rifiutata quando gli hash non coincidono.

`GameSession` può esistere senza combattimento, griglia, PF, CA, iniziativa o d20. Le schermate
storiche di creazione personaggio rimangono invece intenzionalmente D&D-shaped; i regolamenti
classless usano sezioni scheda generate e la sessione generale.

## 9. Moduli e composizione

`RulesetComposer` applica a una base una lista ordinata di moduli content-addressed. Verifica:

- dipendenze esatte e incompatibilità;
- identità e hash dei moduli;
- conflitti per singolo campo;
- vincitore esplicito fra i soli candidati ammessi;
- validità della revisione risultante.

La pubblicazione conserva un lock riproducibile. La UI mostra anteprima e diff prima→dopo e limita
la quantità di righe visualizzata per non saturare la schermata.

## 10. Persistenza e portabilità

`RulesetLibraryJsonCodec` usa lo schema 3 e legge anche gli schemi 1 e 2. La libreria salva con
scrittura atomica e backup:

- progetti, bozze e revisioni;
- catalogo dei moduli;
- associazioni revisione→composition lock;
- metadati lossless dell'authoring.

Revisione, modulo e bundle hanno documenti JSON portabili distinti. Gli import hanno limiti,
ricalcolano hash, compilano il contenuto, sono idempotenti sugli oggetti identici e non fanno merge
impliciti in caso di collisione.

Un bundle modulare contiene snapshot appiattito, lock e grafo chiuso dei moduli. Senza la base esatta
lo snapshot resta giocabile offline, ma diff, ricomposizione e rebase non sono disponibili.

Un eventuale archivio `.onfall-ruleset` con asset e quarantena completa resta futuro: non va
confuso con i formati JSON già implementati.

## 11. Sicurezza e licenze

- Nessun import può contenere codice eseguibile.
- Hash, schema, dimensioni, riferimenti e compilazione vengono verificati prima dell'installazione.
- Formule, eventi e trigger hanno budget finiti.
- Una semantica runtime più nuova o una dipendenza mancante impedisce l'esecuzione.
- La capacità tecnica di rappresentare un'edizione non autorizza a distribuirne il testo.

Attribuzioni e termini dei contenuti distribuiti sono in [`../NOTICE-SRD.md`](../NOTICE-SRD.md),
[`../NOTICE-FONTS.md`](../NOTICE-FONTS.md) e [`../NOTICE-MAPS.md`](../NOTICE-MAPS.md). Nessun pack
3.5 o corpus 2014 aggiuntivo è incluso dal lavoro sul motore modulare.

## 12. Limiti ancora aperti

Questi punti non sono regressioni né funzionalità già promesse come concluse:

- alcuni handler tattici storici per attacchi, PF, morte, concentrazione, slot e CPU restano
  adattatori D20/SRD;
- creazione personaggio e riquadri storici della scheda restano principalmente D&D-shaped;
- la sincronizzazione atomica fra più archivi fisici non usa ancora un journal distribuito unico;
- topologie esagonali e gridless sono modellate e misurabili, ma renderer e pathfinding completi
  restano orientati alla griglia quadrata;
- authoring visuale dei moduli, aggiornamento della base con rebase a tre vie e download automatico
  delle dipendenze non sono implementati;
- classi, sottoclassi, background, feature, talenti, incantesimi, oggetti e core mechanic conservano
  l'escape hatch Esperto finché non hanno una recipe guidata lossless dedicata;
- produzione di nuovi pack richiede corpus verificato e revisione separata di licenze e attribuzioni.

## 13. Contratto della UI

La destinazione Regole espone Panoramica, catalogo, Costruttore, Prova e Gestione. Versioni, moduli,
bundle, import/export e hash rimangono in Gestione. Il flusso ordinario parte dall'intento e compila
nel modello descritto qui senza introdurre una seconda semantica.

Dettagli, famiglie supportate e comportamento desktop/compatto sono nel
[`progetto-costruttore-regole-guidato.md`](progetto-costruttore-regole-guidato.md).

## 14. Mappa del codice

| Area | Percorso principale |
|---|---|
| Modello e formule | `engine/rules-model` |
| Proiezione e compiler di authoring | `engine/rules-authoring` |
| Repository e codec | `engine/rules-persistence` |
| Runtime di sessione | `engine/core-engine` |
| Adattatore personaggio | `engine/character-rules` |
| Pack SRD | `content/srd-5.2.1-it` |
| UI e ViewModel | `shared-ui/.../ui/rules` |
| Test della UI | `shared-ui/src/desktopTest/.../rules` |

## 15. Gate di accettazione

Una modifica al sistema di regole deve dimostrare:

- stesso hash per contenuto semanticamente identico;
- nessuna perdita nel round-trip di attributi conosciuti o protetti;
- rifiuto di riferimenti, tipi, cicli e composizioni invalidi;
- pubblicazione e import senza scritture parziali;
- sessioni e schede legacy ancora leggibili;
- comportamento deterministico a parità di revisione, stato e seed;
- test desktop e compilazione/lint Android verdi;
- `git diff --check` pulito.

Comandi usati per l'ultima verifica:

```text
./gradlew :engine:rules-model:test :engine:rules-authoring:test \
  :engine:rules-persistence:test :engine:core-engine:test :shared-ui:desktopTest --no-daemon

./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon
```

Entrambi i gate risultano verdi sulle modifiche del 3 settembre 2026.
