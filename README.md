# INSERIRE NOME

Strumento di combattimento e gestione incontri **compatibile con 5.5e / SRD**, offline-first,
con interfaccia da videogioco. Desktop e Android condividono motore, dati e schermate.

> Il nome evita volutamente il marchio "D&D", come raccomanda il paragrafo 1 del documento di
> progetto. L'applicazione si dichiara *compatibile con 5.5e*, non approvata
> ufficialmente.

## Cos'e'

Tre prodotti che convivono sullo stesso motore:

1. un **archivio** di personaggi, creature e capacita' (il Compendio);
2. un **motore di combattimento** indipendente dall'interfaccia, deterministico e annullabile;
3. una **interfaccia di gioco** che presenta lo scontro come una battaglia a turni.

## Interfaccia

La schermata di battaglia tiene tre aree visibili insieme sul desktop:

```
┌──────────────────────────────────────────────────────────────┐
│ ⚔ Campagna · Sessione · ROUND 3            [ROUND] [stato]   │
├───────────┬──────────────────────────────────┬───────────────┤
│  SQUADRA  │      SCENA DI BATTAGLIA          │    NEMICI     │
│           │   (impianto a turni da gioco,    │               │
│ ◈ Kaelen  │    con la densita' di 5.5)       │ ◈ Predone A   │
│ ▰▰▰▰▰▰▱ 28│                                  │ ▰▰▰▰▱▱ 12/20  │
│ A B R CA18│    bersaglio in alto             │ ☠ Avvelenato  │
│           │    attore di turno in basso      │               │
│ ◈ Mirethe │                                  │ ◈ Mastino     │
│ ▰▰▰▱▱▱ 11 │  ▶ATTACCA  Normale/Vant./Svant.  │ ▰▰▰▰▰▰ 16/16  │
├───────────┴──────────────────────────────────┴───────────────┤
│ REGISTRO ▸ Kaelen colpisce Predone A — 7 taglienti           │
└──────────────────────────────────────────────────────────────┘
```

Su telefono la stessa schermata diventa una superficie alla volta (Palco / Squadra / Nemici /
Registro) con i comandi sempre a portata di pollice. Il documento chiede esplicitamente di **non**
fare del desktop una UI mobile ingrandita, ne' viceversa: cambia il layout, non il motore.

Tutti i ritratti sono disegnati a vettori dal codice. Non esiste nessuna immagine importata,
quindi ogni creatura inserita ha subito una rappresentazione visiva e non ci sono vincoli di
licenza sulla grafica.

## Requisiti

- JDK 17 o superiore (qui verificato con JDK 26)
- Android SDK, solo per costruire l'APK — il percorso va in `local.properties`

## Avvio

```bash
# desktop
./gradlew :desktop-app:run

# tutti i test
./gradlew test :shared-ui:desktopTest

# APK Android di debug
./gradlew :android-app:assembleDebug
```

I dati risiedono in `~/.turnforge`. Si puo' cambiare percorso:

```bash
./gradlew :desktop-app:run -Dturnforge.dataDir=/percorso/scelto
```

## Moduli

| Modulo | Linguaggio | Ruolo |
|---|---|---|
| `engine/domain-model` | Java 17 | attori, capacita', condizioni, stato, campagne. Immutabile, zero dipendenze |
| `engine/core-engine` | Java 17 | dadi con seed, macchina a stati, audit append-only, budget XP |
| `engine/persistence-json` | Java 17 | salvataggi atomici, backup, import/export |
| `engine/sheet-model` | Kotlin | scheda personaggio 2024 e stat block mostri 2025 |
| `shared-ui` | Kotlin + Compose MP | tema, componenti, schermate, stato di presentazione |
| `desktop-app` | Kotlin | finestra JVM, shell densa |
| `android-app` | Kotlin | Activity, shell touch |

Il motore e' Java e resta consumabile da entrambe le piattaforme perche' Android e desktop girano
entrambi su bytecode JVM. La UI condivisa vive in `jvmSharedMain` anziche' in `commonMain`, il che
le permette di usare direttamente le classi Java del motore.

`core-engine` non conosce testi, classi o mostri: le regole stanno nel motore, i contenuti nei
pacchetti separati.

## Contenuti e licenza

Il materiale dimostrativo incluso (`SampleEncounter`) e' **interamente originale**: nessuno stat
block, testo, nome o illustrazione proviene dai manuali commerciali. Vedi `NOTICE-SRD.md`.

Le schede create nel Compendio sono per impostazione predefinita contenuto privato dell'utente e
non vengono condivise da sole.

## Stato

Vedi `docs/STATO.md` per la mappa onesta fra il documento di progetto e cio' che gira davvero.
