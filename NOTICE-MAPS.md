# Mappe incluse

> **Da risolvere prima di distribuire.** La provenienza qui sotto e' quella nota, ma
> il diritto di **ridistribuire** questi file dentro un'applicazione non e' stato
> verificato. Il nome dell'autore e' un'attribuzione, non una licenza. Finche' non
> c'e' una licenza verificabile per ciascuna voce, questi file non vanno pubblicati:
> vanno rimossi da `shared-ui/src/jvmSharedMain/resources/mappe/` e da
> `BundledMaps.kt`, oppure sostituiti con mappe di cui si abbiano i diritti.

L'applicazione include quattro sfondi da tavolo, installati nell'archivio delle mappe
al primo avvio (`shared-ui/src/jvmSharedMain/resources/mappe/`). Sono materiale di
**terze parti**: a differenza dei font e del contenuto SRD, non sono disegnati da noi
e non arrivano con una licenza di ridistribuzione accertata.

| File | Nome mostrato | Autore dichiarato | Fonte | Licenza |
|---|---|---|---|---|
| `anubis_tomb.jpg` | Anubis Tomb (DnDavid) | DnDavid | da accertare | **da accertare** |
| `abandoned_well.jpg` | Abandoned Well (DnDavid) | DnDavid | da accertare | **da accertare** |
| `cathedral_of_avacyn_basement.jpg` | Cathedral of Avacyn Basement [40x60] (DnDavid) | DnDavid | da accertare | **da accertare** |
| `volcano_temple.jpg` | VolcanoTempleHD | da accertare | da accertare | **da accertare** |

Il nome dell'autore compare nel nome della mappa dentro l'applicazione, ed e' l'unico
punto dell'interfaccia in cui chi l'ha disegnata e' visibile. Chi rinomina una mappa
nel proprio archivio lo fa per se': noi non la consegniamo gia' anonima.

## Cosa serve per chiudere questo punto

Per ogni voce: l'indirizzo da cui il file proviene, l'autore confermato e il testo
della licenza — o un permesso scritto — che consenta la ridistribuzione dentro un
programma. Le mappe della comunita' sono spesso concesse per uso personale al proprio
tavolo, che **non** e' la stessa cosa.

Il meccanismo che le installa non dipende da quali file siano: sostituirli, o
toglierli tutti, e' una modifica a `BundledMaps.all` e alla cartella delle risorse.
Con l'elenco vuoto l'archivio nasce vuoto e tutto il resto continua a funzionare.
