# Sistema tipografico di Onfall

## Esito della revisione

La direzione fantasy dell'app era già valida: Cinzel e Alegreya comunicano bene
un manuale di gioco senza imitare la grafica di un prodotto commerciale. Il
problema non era quindi sostituirle, ma assegnare a ogni famiglia un solo compito
e togliere al testo funzionale la dipendenza dai font del sistema operativo.

La nuova voce dell'interfaccia è una triade coordinata:

| Famiglia | Pesi incorporati | Quando si usa | Quando non si usa |
|---|---:|---|---|
| **Cinzel** | 700, 900 | titoli monumentali, step principali, micro-intestazioni in maiuscolo | paragrafi, pulsanti, hint, nomi lunghi |
| **Alegreya** | 500, 700 | titoli di schermata/pannello, nomi di attori, oggetti, mappe e abilità | testo operativo minuto, numeri, campi |
| **Alegreya Sans** | 400, 500, 700, 900 | corpo, regole, metadati, campi, controlli, chip, numeri e token | titoli narrativi o ornamentali |

Alegreya Sans è la famiglia sans sorella di Alegreya, disegnata dallo stesso
autore. Mantiene un ritmo calligrafico adatto al gioco, ma è più leggibile e
compatta nei pannelli densi. È incorporata nell'app: Android e desktop non
mostrano più Roboto, San Francisco o un altro fallback locale.

## Audit precedente

La ricognizione ha attraversato tutto `shared-ui` e ha trovato 42 file UI con
scelte tipografiche esplicite. Prima della revisione:

- erano dichiarati soltanto 9 dei 15 ruoli Material 3; i restanti potevano
  reintrodurre il font di sistema nei componenti della libreria;
- `bodySmall` compariva 173 volte e `labelSmall` 154 volte, ma quest'ultimo
  rappresentava sia intestazioni sia hint, stati, distanze e testi esplicativi;
- 96 usi aggiungevano `Bold` localmente e 55 aggiungevano `Black`; Alegreya non
  aveva un file Black, quindi molti titoli venivano ispessiti sinteticamente;
- nomi di abilità equivalenti usavano alternativamente `bodyMedium + Bold`,
  `titleLarge + Black` e `labelSmall`;
- campi di scheda, nome sessione, nome mappa e quantità risorsa costruivano
  `TextStyle` e dimensioni a mano, saltando completamente il tema;
- il corpo usava `FontFamily.Default`, con resa diversa per piattaforma.

## Scala Material completa

Questa scala alimenta anche i componenti Material non disegnati direttamente da
Onfall. Le misure sono `dimensione/interlinea` in sp.

| Ruolo Material | Famiglia | Peso | Misura | Uso autorizzato |
|---|---|---:|---:|---|
| `displayLarge` | Cinzel | 900 | 40/46 | display eccezionale, non per schermate ordinarie |
| `displayMedium` | Cinzel | 900 | 34/40 | display eccezionale |
| `displaySmall` | Cinzel | 900 | 28/34 | nome principale di uno stat block |
| `headlineLarge` | Cinzel | 700 | 24/30 | capitolo/step molto importante |
| `headlineMedium` | Cinzel | 700 | 22/28 | capitolo/step |
| `headlineSmall` | Cinzel | 700 | 20/25 | step della creazione guidata |
| `titleLarge` | Alegreya | 700 | 22/27 | titolo schermata, dialogo o dettaglio |
| `titleMedium` | Alegreya | 700 | 17/21 | titolo pannello e nome primario |
| `titleSmall` | Alegreya | 700 | 15/19 | titolo card, elemento e abilità |
| `bodyLarge` | Alegreya Sans | 400 | 16/22 | corpo ampio o accessibile |
| `bodyMedium` | Alegreya Sans | 400 | 14,5/20 | regole, descrizioni e testo ordinario |
| `bodySmall` | Alegreya Sans | 400 | 12,5/17 | metadati, hint e testo secondario |
| `labelLarge` | Alegreya Sans | 700 | 14/17 | pulsante e comando ordinario |
| `labelMedium` | Alegreya Sans | 700 | 12/15 | chip e comando compatto |
| `labelSmall` | Alegreya Sans | 700 | 10,5/14 | etichetta funzionale minuta, badge e metadato compatto |

La micro-intestazione decorativa non coincide più con il generico `labelSmall`:
usa il token `sectionLabel`, con Cinzel e 1,2 sp di spaziatura. In questo modo un
metadato Material o un tooltip non eredita il font display. I precedenti 2,2 sp
allungavano e spezzavano le parole sul telefono.

## Ruoli semantici Onfall

I ruoli in `OnfallTheme.typography` impediscono che lo stesso concetto scelga un
peso diverso in ogni schermata.

| Token | Base | Significato |
|---|---|---|
| `screenTitle` | `titleLarge` | titolo della destinazione o del dialogo |
| `panelTitle` | `titleMedium` | titolo del pannello |
| `itemTitle` | `titleSmall` | nome di un elemento generico |
| `abilityNameLarge` | `titleLarge` | abilità aperta nel dettaglio |
| `abilityName` | `titleSmall` | abilità in archivio, scheda e comando |
| `abilityNameCompact` | Alegreya 700, 13/16 | abilità in chip o fascia molto densa |
| `sectionLabel` | Cinzel 700, 10,5/14 | sopracciglio/intestazione in maiuscolo |
| `body` | `bodyMedium` | testo ordinario |
| `bodyEmphasis` | Alegreya Sans 700, 14,5/20 | enfasi reale nel testo ordinario |
| `supporting` | `bodySmall` | hint e metadati |
| `supportingEmphasis` | Alegreya Sans 700, 12,5/17 | avviso o dato secondario marcato |
| `control` | `labelLarge` | pulsante ordinario |
| `compactControl` | `labelMedium` | chip o pulsante denso |
| `fieldValue` | Alegreya Sans 500, 14,5/20 | valore modificabile |
| `numberLarge` | Alegreya Sans 900, 22/26 | valore derivato in evidenza |
| `numberMedium` | Alegreya Sans 900, 17/21 | dado, totale o quantità |
| `numberCompact` | Alegreya Sans 900, 14,5/20 | distanza o contatore minuto |
| `numberSmall` | Alegreya Sans 900, 12,5/17 | modificatore e contatore in una riga densa |
| `tokenInitials` | Alegreya Sans 900, 14/14 | iniziali, glifi e badge circolari |

I quattro token numerici abilitano cifre tabulari (`tnum`) per non far oscillare
larghezze e allineamenti quando un valore cambia.

## Mappa per area dell'app

| Area | Titoli e nomi | Corpo e metadati | Eccezioni controllate |
|---|---|---|---|
| Shell e navigazione | Alegreya per destinazioni; Cinzel solo per etichette di sezione | Alegreya Sans per pulsanti e stato sessione | logo iniziale con `tokenInitials` |
| Sessioni e creazione partita | `titleLarge`/`titleMedium`; step in Cinzel | corpo e hint in Alegreya Sans | valori di griglia con token numerici |
| Compendio e roster | nomi attore in Alegreya | classe, livello e chip in Alegreya Sans | intestazioni lista in Cinzel |
| Archivio abilità | i tre token `abilityName*` in ogni contesto | regole in `bodyMedium`, metadati in `bodySmall`, chip sans | nessun Black locale |
| Scheda personaggio | titoli card/nome in Alegreya, box in Cinzel | campi 500, descrizioni 400 | valori derivati con `numberLarge` |
| Stat block creatura | nome principale in Cinzel 900; voci in Alegreya | statistiche e testo in Alegreya Sans | abbreviazioni brevi in Cinzel |
| Mappe | nomi mappa in Alegreya | comandi, cartelle e hint in Alegreya Sans | distanze overlay con `numberCompact` |
| Battaglia | nomi combattenti/abilità in Alegreya | comandi, log e stato in Alegreya Sans | token, PF, risorse e dadi con token numerici |
| Regole | entità e pannelli in Alegreya | testi lunghi e attributi in Alegreya Sans | sopraccigli strutturali in Cinzel |
| Impostazioni e dialoghi | titolo in Alegreya | opzioni, pulsanti e spiegazioni in Alegreya Sans | nessuna eccezione locale |

## Regole su peso, maiuscole e dimensioni

- **400 Regular**: descrizioni, regole, hint, stato secondario.
- **500 Medium**: contenuto digitato e valori di campo; non serve per enfasi.
- **700 Bold**: titoli, nomi, controlli e una vera enfasi semantica nel corpo.
- **900 Black**: soltanto display Cinzel, numeri e iniziali/glifi. Non va più
  richiesto direttamente dentro una schermata.
- Il maiuscolo appartiene solo a sezioni brevi e passa da `Eyebrow`, `SheetBox`
  o `sectionLabel`. Nomi, abilità, pulsanti e frasi mantengono la capitalizzazione
  della lingua.
- Una dimensione locale è ammessa solo quando dipende geometricamente dallo zoom
  o dal diametro (token sulla mappa, ritratto, numero fluttuante). Famiglia e peso
  devono comunque provenire da un token semantico.
- Un peso locale è ammesso solo quando cambia con lo stato, per esempio il membro
  del turno corrente o l'ultimo evento del log. Le enfasi statiche usano i token
  `bodyEmphasis`, `supportingEmphasis` o uno dei ruoli numerici.

## Punti di implementazione e guardrail

- La scala completa e le famiglie sono in
  `shared-ui/src/jvmSharedMain/kotlin/app/d6d/ui/theme/Theme.kt`.
- I ruoli di prodotto sono in
  `shared-ui/src/jvmSharedMain/kotlin/app/d6d/ui/theme/Typography.kt`.
- I font sono caricati allo stesso modo su Android e desktop da `ThemeFonts.kt`
  e dalle due implementazioni di piattaforma.
- `GameButton`, `Chip`, `Eyebrow`, campi scheda, numeri, dadi e token sono i punti
  condivisi da usare: una schermata non deve ricostruire il loro `TextStyle`.
- I titoli di `AlertDialog` passano da `DialogTitle`: Material usa normalmente
  `headlineSmall`, che in Onfall appartiene agli step Cinzel e non ai dialoghi.
- Nuove facce devono essere aggiunte a `NOTICE-FONTS.md` e a `ThemeFontsTest`.
- `TypographySystemTest` verifica che tutti i 15 ruoli Material appartengano a
  una delle tre famiglie intenzionali e che i pesi semantici restino stabili.

Questa mappa è il contratto per le schermate future: prima si sceglie il ruolo
del testo, poi il colore contestuale; famiglia, peso e misura non si ridefiniscono
nel singolo file.
