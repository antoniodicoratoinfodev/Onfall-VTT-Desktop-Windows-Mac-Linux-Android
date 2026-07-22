# TurnForge

Strumento di combattimento e gestione incontri **compatibile con 5.5e / SRD**, offline-first,
con interfaccia da videogioco. Desktop e Android condividono motore, dati e schermate.

> Il nome evita volutamente il marchio "D&D", come raccomanda il paragrafo 1 del documento di
> progetto. L'applicazione si dichiara *compatibile con 5.5e*, non approvata ufficialmente.

## Cos'è

Tre prodotti che convivono sullo stesso motore:

1. un **archivio** di personaggi, creature e capacità (il Compendio);
2. un **motore di combattimento** indipendente dall'interfaccia, deterministico e annullabile;
3. una **interfaccia di gioco** che presenta lo scontro come una battaglia a turni.

## Interfaccia

Sul desktop la schermata di battaglia tiene tre aree visibili insieme: la **squadra** a
sinistra, la **scena di battaglia** al centro, i **nemici** a destra; in alto scorre l'ordine
dei turni, sotto i nemici resta il registro degli eventi. Le colonne laterali si
**ridimensionano trascinandone il bordo**, e quando si stringono le informazioni di ogni
combattente si ripiegano in verticale invece di essere troncate.

Su telefono la stessa schermata diventa una superficie alla volta (Palco / Squadra / Nemici /
Registro) con i comandi sempre a portata di pollice. Il documento chiede esplicitamente di
**non** fare del desktop una UI mobile ingrandita, né viceversa: cambia il layout, non il
motore.

La mappa tattica è a griglia: si **zooma con la rotellina**, si trascinano i segnaposti per
spostarli, e scala (piedi per casella) e dimensioni si cambiano a schermo. Con la **modalità
Modifica** attiva il tavolo compone la scena liberamente — corregge nome, CA, PF e iniziativa
direttamente sulle carte, riordina i turni e sceglie quello corrente, trascina i personaggi
dalle barre laterali sulla mappa e sposta i token ignorando i limiti di movimento. Fuori dalla
modifica quelle scorciatoie spariscono, così non si altera una partita per sbaglio.

Tutti i ritratti sono disegnati a vettori dal codice. Non esiste nessuna immagine importata,
quindi ogni creatura inserita ha subito una rappresentazione visiva e non ci sono vincoli di
licenza sulla grafica.

## Requisiti

- JDK 17 o superiore (qui verificato con JDK 26)
- Android SDK, solo per costruire l'APK — il percorso va in `local.properties`

## Avvio

```bash
# Desktop (macOS, Linux o Windows)
./gradlew :desktop-app:run

# tutti i test
./gradlew test :shared-ui:desktopTest

# APK Android di debug
./gradlew :android-app:assembleDebug
```

Su macOS, dopo aver creato il pacchetto con `./gradlew :desktop-app:packageDmg`, si può anche
aprire **TurnForge.app** dal Finder come una normale applicazione. Durante lo sviluppo il comando
`run` qui sopra è il modo più rapido e non richiede installazione.

I dati risiedono in `~/.turnforge`. Si può cambiare percorso:

```bash
./gradlew :desktop-app:run -Dturnforge.dataDir=/percorso/scelto
```

## Moduli

| Modulo | Linguaggio | Ruolo |
|---|---|---|
| `engine/domain-model` | Java 17 | attori, capacità, condizioni, stato, campagne. Immutabile, zero dipendenze |
| `engine/core-engine` | Java 17 | dadi con seed, macchina a stati, audit append-only, budget XP |
| `engine/persistence-json` | Java 17 | salvataggi atomici, backup, import/export |
| `engine/sheet-model` | Kotlin | scheda personaggio 2024 e stat block mostri 2025 |
| `shared-ui` | Kotlin + Compose MP | tema, componenti, schermate, stato di presentazione |
| `desktop-app` | Kotlin | finestra JVM, shell densa |
| `android-app` | Kotlin | Activity, shell touch |

Il motore è Java e resta consumabile da entrambe le piattaforme perché Android e desktop girano
entrambi su bytecode JVM. La UI condivisa vive in `jvmSharedMain` anziché in `commonMain`, il che
le permette di usare direttamente le classi Java del motore.

`core-engine` non conosce testi, classi o mostri: le regole stanno nel motore, i contenuti nei
pacchetti separati.

## Contenuti e licenza

Il materiale dimostrativo incluso (`SampleEncounter`) è **interamente originale**: nessuno stat
block, testo, nome o illustrazione proviene dai manuali commerciali. Vedi `NOTICE-SRD.md`.

Le schede create nel Compendio sono per impostazione predefinita contenuto privato dell'utente e
non vengono condivise da sole.

## Stato

Vedi `docs/STATO.md` per la mappa onesta fra il documento di progetto e ciò che gira davvero.
