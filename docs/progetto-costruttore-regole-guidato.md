# Contratto UX del Costruttore guidato delle regole

Stato: implementato e verificato il 3 settembre 2026
Ambito: desktop e Android, homebrew, regolamenti indipendenti e pack modulari

Questo documento è autorevole per l'esperienza di consultazione, creazione e prova delle regole.
Il modello eseguibile e i limiti del runtime sono descritti in
[`piano-regole-modulari.md`](piano-regole-modulari.md). La decisione architetturale che impedisce
perdite o un secondo motore è nell'[ADR 0001](adr/0001-authoring-regole-lossless.md).

## 1. Decisione di prodotto

Onfall usa due linguaggi collegati:

- il linguaggio dell'utente: intenzioni, domande, nomi, frasi, selettori, esempi e anteprime;
- il linguaggio del motore: `RuleEntity`, ID, formule, riferimenti, patch, moduli e revisioni.

Il primo compila deterministicamente nel secondo. Il testo naturale non viene eseguito, la UI non
possiede un interprete parallelo e una modalità semplice non riduce l'espressività dei dati.

```text
intento → bozza guidata → RuleEntity/RuleFormula → preflight → runtime
```

La complessità viene mostrata per gradi, non eliminata dal dominio.

## 2. Utenti

- **Autore occasionale:** cambia un numero o crea una statistica, risorsa, prova o bonus senza
  conoscere formule e ID.
- **Designer di sistema:** costruisce condizioni, azioni, progressioni e calcoli con controlli
  visuali e laboratorio.
- **Autore di pack:** usa dati del motore, import/export, moduli e diagnostica completa quando serve.

Le tre esperienze convivono per singola regola. Non sono editor o formati separati.

## 3. Principi non negoziabili

1. L'intento viene prima del tipo tecnico.
2. L'utente riconosce una regola dal nome, non deve ricordarne l'ID.
3. Le opzioni rare compaiono solo su richiesta.
4. Ogni cambiamento ha riepilogo, esempio o diagnostica prima della pubblicazione.
5. Navigare non può perdere modifiche locali.
6. Attributi e formule non rappresentabili restano protetti e modificabili in Esperto.
7. Manuale è una modalità legittima, non un errore.
8. Il laboratorio usa compilatore ed evaluator reali.
9. Ogni operazione deve funzionare senza drag e senza dipendere soltanto dal colore.
10. Desktop e Android producono gli stessi dati canonici.

## 4. Architettura dell'informazione

La destinazione Regole è divisa in cinque attività:

| Area | Responsabilità |
|---|---|
| **Panoramica** | regolamento selezionato, azioni comuni e binding della sessione |
| **Regole** | ricerca, filtri e lettura del catalogo effettivo |
| **Costruttore** | creazione e modifica della singola regola |
| **Prova** | preflight, esempi e scenari numerici |
| **Gestione** | versioni, moduli, composizione, import/export e pubblicazione |

Il flusso ordinario non mostra lock, hash, percorsi filesystem, bundle o cronologia delle revisioni.
Questi dettagli rimangono disponibili in Gestione.

## 5. Panoramica e azioni principali

La prima domanda è **Cosa vuoi fare?**:

- cambiare una regola;
- creare una regola;
- provare le modifiche;
- gestire e condividere il regolamento.

**Creare una regola** apre sempre un wizard nuovo. Se una regola era già selezionata, la selezione
viene azzerata. Se non esiste ancora un regolamento, viene creata una linea homebrew dalla fondazione
vuota. Una revisione pubblicata viene invece derivata in una variante modificabile.

## 6. Catalogo

Il catalogo mostra il risultato effettivo del regolamento selezionato. Offre:

- ricerca per nome, descrizione, ID, tipo, tag e attributi;
- filtri principali per cinque famiglie d'intento;
- filtri secondari per tipo preciso, automazione e stato abilitato;
- origine Standard/Homebrew;
- riepilogo in prosa e componenti collegati;
- ingresso esplicito nel Costruttore per una bozza modificabile.

Le primitive generate da una recipe composta sono nascoste per impostazione predefinita. Un filtro
avanzato permette comunque di ispezionarle.

## 7. Wizard di creazione

Il primo passo chiede il risultato desiderato, raggruppando i tipi del motore in cinque famiglie:

| Famiglia mostrata | Esempi |
|---|---|
| **Numeri e risorse** | statistica, valore, risorsa, salute, progressione |
| **Tiri e risultati** | dado, pool, prova, difficoltà, tabella |
| **Bonus, penalità e stati** | modificatore, effetto, condizione, tipo di danno |
| **Azioni ed eventi** | azione, costo, trigger, movimento, procedura di scena |
| **Scheda e regole manuali** | sezione scheda e testo affidato al tavolo |

Dopo la recipe, il wizard chiede:

1. nome;
2. descrizione in una frase;
3. soltanto i valori iniziali pertinenti.

Esempi di domande contestuali:

- statistica: «Da quale numero parte?»;
- risorsa: «Qual è la capienza massima?»;
- prova: dado, bonus fisso e difficoltà;
- modificatore: valore bersaglio, aggiunta/sottrazione e quantità.

Nome, descrizione e domande restano visibili anche quando il campo è precompilato. Un valore non
valido mostra un messaggio accanto al campo. **Crea e continua** produce subito la regola compilabile
e apre il suo editor.

I tipi tecnici sono un escape hatch disponibile soltanto all'ingresso del wizard. Non ricompaiono
dopo che l'utente ha scelto una famiglia guidata.

## 8. Tre livelli di modifica

### Guidata

È il default. Mostra nomi, frasi, domande e controlli specifici per la famiglia. ID, chiavi degli
attributi e sorgenti delle formule restano nascosti.

### Blocchi avanzati

Espone struttura dei calcoli, condizioni, limiti e opzioni che richiedono più controllo. Usa lo
stesso AST della formula eseguita dal motore.

### Dati del motore

Mostra attributi, ID e sorgenti originali. È sempre disponibile per preservare regole avanzate o
future che la UI non sa ancora proiettare.

Le ultime due modalità sono raccolte sotto **Altre opzioni**. Passare da Esperto a Guidata non
cancella contenuto sconosciuto: viene indicato come dettaglio avanzato protetto.

## 9. Famiglie guidate correnti

Hanno un percorso Guidato/Blocchi:

- `STAT`, `SKILL`, `SAVE`, `DEFENSE` e `VALUE`;
- `MODIFIER`, `CONDITION`, `DAMAGE_TYPE` e `TEXT_RULE`;
- `RESOURCE`, `TRACK`, `HEALTH_MODEL` e `MOVEMENT`;
- `RANDOMIZER`, `ROLL` e `TABLE`;
- `ACTION_ECONOMY`, `ACTION` e `TRIGGER`;
- `PROGRESSION`, `SHEET_SECTION` e `SCENE_PROCEDURE`.

Classi, sottoclassi, background, feature, talenti, incantesimi, oggetti, core mechanic e custom
restano in Dati del motore finché non possiedono una recipe lossless dedicata. Non vengono presentati
come supportati a metà.

## 10. Calcoli e condizioni

In Guidata un calcolo additivo è una frase a righe:

```text
Parte da 10
Aggiungi Agilità
Sottrai Penalità dell'armatura
```

Ogni termine può essere un numero o il valore di una regola compatibile. Gli ID di contesto vengono
localizzati, per esempio livello, bonus di competenza, massimo o risultato del tiro.

Moltiplicazioni, funzioni e strutture non appiattibili vengono conservate senza modifiche e inviano
l'utente a Blocchi avanzati. Le condizioni comuni usano sempre/mai oppure un confronto leggibile.

Sotto il calcolo, un esempio modificabile valuta input significativi con l'evaluator reale. Il
riepilogo non mostra chiavi come `targetRef` o `conditionFormula`.

## 11. Riferimenti

Il selettore dei riferimenti è ricercabile e mostra:

- nome localizzato;
- tipo e origine;
- descrizione;
- valore iniziale quando è numerico e direttamente disponibile;
- selezioni correnti e recenti prima degli altri risultati.

I risultati sono limitati e invitano a restringere la ricerca. Un riferimento non più disponibile
resta visibile come collegamento preservato invece di essere cancellato silenziosamente.

## 12. Regole composte

Una recipe può generare più entità tecniche in una sola scrittura atomica. La prova è il caso
principale: crea `ROLL` e il suo `RANDOMIZER`, collega gli ID e salva metadati e hash del gruppo.

Per l'utente il gruppo appare come una sola regola. Il dettaglio indica quanti componenti vengono
gestiti automaticamente; Gestione e i filtri avanzati permettono di ispezionarli.

## 13. Salvataggio e navigazione

L'editor mantiene uno snapshot iniziale e calcola lo stato dirty. La barra persistente mostra:

- stato salvato o modifiche pendenti;
- **Annulla modifiche**;
- **Prova**;
- **Salva**.

Prima di cambiare regola, regolamento o area, il navigation gate salva il form corrente. Se nome o
descrizione obbligatori mancano, la navigazione si ferma, l'errore compare inline e il primo campo
invalido riceve il focus.

La creazione di un effetto o modificatore collegato segue lo stesso gate. Nessun cambio di contesto
può scartare implicitamente la modifica locale.

## 14. Prova e pubblicazione

L'area Prova salva la modifica corrente e usa lo stesso preflight della pubblicazione. Per le
formule mostra uno scenario numerico modificabile; gli errori restano in linguaggio comune finché
non si aprono i dettagli tecnici.

La pubblicazione vive in Gestione e crea una versione giocabile immutabile. Moduli, conflitti,
bundle, import ed export non competono con il salvataggio della singola regola.

## 15. Desktop e layout compatto

Desktop usa al massimo due pannelli: elenco/creazione e area di lavoro. Non esiste più una griglia
permanente a tre colonne.

Il layout compatto usa pagine elenco → dettaglio. Le azioni dell'editor restano fuori dallo
scorrimento e le tab principali scorrono orizzontalmente quando necessario. I comandi densi nella
destinazione Regole mantengono un target interattivo minimo di 48 dp.

## 16. Accessibilità e localizzazione

- Le etichette non dipendono dai placeholder.
- Errori dei campi sono esposti alla semantica accessibile.
- Pulsanti e filtri dichiarano ruolo e stato selezionato.
- I comandi di rimozione hanno un nome, non il solo simbolo `×`.
- Riordino e selezione funzionano con controlli espliciti, non soltanto via drag.
- Informazioni importanti non dipendono esclusivamente dal colore.
- Testi di contesto e relazioni delle formule sono localizzati in italiano e inglese.

## 17. Contratto lossless

`engine:rules-authoring` dipende dal modello, non da Compose o dalla persistence. Proietta una
`RuleEntity` in un draft tipizzato e la ricompila nel formato esistente.

Gli esiti di proiezione sono:

- `EXACT`: tutto è rappresentabile;
- `PARTIAL`: le parti guidate sono modificabili e il resto resta protetto;
- `EXPERT_ONLY`: la sorgente originale rimane l'unico editor sicuro.

I metadati di authoring vengono salvati atomicamente con la bozza ma non partecipano agli hash
runtime. Prima di riusarli, gli hash di tutte le entità del gruppo devono corrispondere; altrimenti la
UI riproietta dal contenuto effettivo.

## 18. Mappa dell'implementazione

| Responsabilità | Percorso |
|---|---|
| Schermata, wizard ed editor | `shared-ui/.../rules/RulesScreen.kt` |
| Stato, gruppi e comandi | `shared-ui/.../rules/RulesViewModel.kt` |
| AST e compiler runtime | `engine/rules-model` |
| Draft e proiezione lossless | `engine/rules-authoring` |
| Metadati e repository | `engine/rules-persistence` |
| Test puri e Compose | `shared-ui/src/desktopTest/.../rules` |

## 19. Criteri di accettazione

Il percorso è accettabile quando:

1. una regola comune si crea senza digitare ID o formule;
2. le opzioni tecniche non invadono il flusso guidato;
3. un round-trip senza modifiche conserva sorgente, attributi e hash semantico;
4. una parte non proiettabile non viene cancellata;
5. navigazione e azioni collegate salvano o si fermano su un errore;
6. una recipe composta usa una singola revisione atomica;
7. esempi e preflight usano il runtime reale;
8. desktop e Android compilano lo stesso documento;
9. layout compatto e semantica accessibile sono coperti da test;
10. import/export e versionamento rimangono separati dall'editor quotidiano.

Verifica corrente:

```text
./gradlew :engine:rules-model:test :engine:rules-authoring:test \
  :engine:rules-persistence:test :engine:core-engine:test :shared-ui:desktopTest --no-daemon

./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon
```

Entrambi i gate risultano verdi sulle modifiche del 3 settembre 2026.
