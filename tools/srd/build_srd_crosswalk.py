#!/usr/bin/env python3
"""Accoppia i contenuti SRD italiani e inglesi, voce per voce.

I due SRD sono lo stesso libro in due lingue, ma ogni capitolo elenca le proprie
voci in ordine alfabetico *della propria lingua*: «Palla di fuoco» sta fra la P e
la Q, «Fireball» fra la F e la G. Accoppiarli per posizione funziona solo dove
l'ordine e' strutturale — i privilegi di classe, ordinati per livello — e altrove
produrrebbe corrispondenze plausibili e sbagliate.

Perche' serve accoppiarli: gli identificativi dei contenuti finiscono nelle schede
salvate (`selectedFeatureIds`, `abilityIds`). Se i due pacchetti coniassero id
diversi, cambiare lingua orfanerebbe ogni personaggio creato con la procedura
guidata. Con una tavola di corrispondenza l'edizione inglese puo' adottare gli
identificativi italiani, che sono gia' nei dati degli utenti e non si toccano.

Il metodo e' in tre passaggi, dal piu' affidabile al meno:

1. **Firma stabile.** Livello, scuola, classi e componenti non dipendono dalla
   lingua. Su questi quattro campi i due multiinsiemi coincidono *esattamente*,
   il che e' gia' una verifica: se non coincidessero, una delle due estrazioni
   starebbe perdendo qualcosa. La firma raggruppa; da sola risolve i tre quarti.
2. **Spareggio numerico.** Dentro un gruppo ambiguo si guardano i numeri, che la
   traduzione non tocca: i dadi nella descrizione, e la gittata riportata ai
   piedi (l'italiano la stampa in metri con la conversione del regolamento).
3. **Tavola a mano.** Cio' che resta si scrive qui sotto, esplicito e poco. Una
   voce sbagliata a mano e' un errore visibile; una dedotta male non lo e'.

Uso:
    python3 tools/srd/build_srd_crosswalk.py            # scrive la tavola
    python3 tools/srd/build_srd_crosswalk.py --check    # verifica che sia aggiornata
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[2]
ITALIAN_DIR = ROOT / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-it"
ENGLISH_DIR = ROOT / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-en"
DEFAULT_OUTPUT = ROOT / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-en/crosswalk.json"

# Le scuole di magia sono il classico tranello: «Evocazione» e' Conjuration e
# «Invocazione» e' Evocation, non il contrario.
SCHOOLS = {
    "Abiurazione": "Abjuration",
    "Ammaliamento": "Enchantment",
    "Divinazione": "Divination",
    "Evocazione": "Conjuration",
    "Illusione": "Illusion",
    "Invocazione": "Evocation",
    "Necromanzia": "Necromancy",
    "Trasmutazione": "Transmutation",
}

CLASSES = {
    "bardo": "bard",
    "chierico": "cleric",
    "druido": "druid",
    "mago": "wizard",
    "paladino": "paladin",
    "ranger": "ranger",
    "stregone": "sorcerer",
    "warlock": "warlock",
}

DICE = re.compile(r"\b(\d+)d(\d+)\b")
NUMBER = re.compile(r"\d+(?:[.,]\d+)?")

# Metri del regolamento italiano riportati ai piedi: 1,5 m = 5 piedi, cioe' tre
# decimi di metro per piede. E' la stessa conversione che usa l'applicazione.
METRE_TENTHS_PER_FOOT = 3


class CrosswalkError(RuntimeError):
    """Le due edizioni non si accoppiano come dovrebbero."""


def load(directory: Path, name: str, key: str) -> list[dict]:
    return json.loads((directory / f"{name}.json").read_text(encoding="utf-8"))[key]


def components(value: str) -> str:
    """Solo i marcatori V/S/M isolati: il resto e' la descrizione del materiale."""
    head = value.split("(")[0]
    found = {
        part.strip().upper()
        for part in re.split(r"[,\s]+", head)
        if part.strip().upper() in {"V", "S", "M"}
    }
    return "".join(sorted(found))


def dice_fingerprint(text: str) -> tuple:
    return tuple(sorted(Counter(DICE.findall(text)).items()))


def feet_in(value: str, italian: bool) -> tuple:
    """I numeri di una gittata, sempre in piedi."""
    numbers = [float(n.replace(",", ".")) for n in NUMBER.findall(value)]
    if italian:
        numbers = [round(n * 10 / METRE_TENTHS_PER_FOOT) for n in numbers]
    else:
        numbers = [round(n) for n in numbers]
    return tuple(sorted(numbers))


def spell_signature(spell: dict, italian: bool) -> tuple:
    school = SCHOOLS[spell["school"]] if italian else spell["school"]
    classes = (
        sorted(CLASSES[c] for c in spell["classes"]) if italian else sorted(spell["classes"])
    )
    return (spell["level"], school, tuple(classes), components(spell["components"]))


def spell_tiebreak(spell: dict, italian: bool) -> tuple:
    return (dice_fingerprint(spell["description"]), feet_in(spell["range"], italian))


# I sei punteggi di caratteristica, nell'ordine in cui lo stat block li stampa.
ABILITY_LABELS = {
    True: ("For", "Des", "Cos", "Int", "Sag", "Car"),
    False: ("Str", "Dex", "Con", "Int", "Wis", "Cha"),
}


def beast_signature(beast: dict, italian: bool) -> tuple:
    """Classe Armatura, punti ferita e i sei punteggi: numeri, non parole.

    Presi per etichetta e non per posizione: i due stat block stampano le stesse
    grandezze ma non nello stesso ordine, e «i primi quattordici numeri» accoppia
    l'orso col cinghiale.
    """
    block = beast["stat_block"]
    armor = re.search(r"\b(?:CA|AC)\s+(\d+)", block)
    hit_points = re.search(r"\b(?:PF|HP)\s+(\d+)", block)
    scores = []
    for label in ABILITY_LABELS[italian]:
        # Lo spazio e' facoltativo: il PDF ogni tanto attacca l'etichetta al
        # numero ("Con10"), e senza questo otto bestie inglesi restano senza
        # punteggi e non si accoppiano.
        found = re.search(rf"\b{label}\s*(\d+)", block)
        scores.append(int(found.group(1)) if found else 0)
    return (
        beast["challenge_rating"],
        int(armor.group(1)) if armor else 0,
        int(hit_points.group(1)) if hit_points else 0,
        tuple(scores),
    )


def beast_tiebreak(beast: dict, italian: bool) -> tuple:
    return (feet_in(beast["speed"], italian), beast["has_fly_speed"])


def feat_signature(feat: dict, italian: bool) -> tuple:
    return (feat["category"], bool(feat["prerequisite"]))


def feat_tiebreak(feat: dict, italian: bool) -> tuple:
    """Nessuno spareggio automatico per i talenti.

    Sono diciassette: mapparli a mano costa poco ed e' sicuro. Il tentativo
    precedente spareggiava anche sulla lunghezza della descrizione, che non e'
    invariante — l'italiano e' piu' prolisso dell'inglese — e produceva
    accoppiamenti unici e sbagliati, cioe' il difetto peggiore possibile qui.
    """
    return ()


# Cio' che nessun numero distingue, scritto a mano. Chiave: nome italiano.
# Ogni voce qui e' una decisione presa da una persona, e sbagliarla si vede;
# una dedotta male, no. Per questo la tavola e' esplicita anche dove un
# euristica ci arriverebbe.
MANUAL_SPELLS: dict[str, str] = {
    # Stessa scuola, stesso livello, stesse classi, stessi componenti: solo il
    # senso li distingue.
    "Forma gassosa": "Gaseous Form",
    "Volare": "Fly",
    "Individuazione dei pensieri": "Detect Thoughts",
    "Vedere invisibilità": "See Invisibility",
}

MANUAL_BEASTS: dict[str, str] = {}

MANUAL_FEATS: dict[str, str] = {
    "Abile": "Skilled",
    "Aggressore selvaggio": "Savage Attacker",
    "Allerta": "Alert",
    "Iniziato alla magia": "Magic Initiate",
    "Aumento dei punteggi di caratteristica": "Ability Score Improvement",
    "Lottatore": "Grappler",
    "Combattere con armi possenti": "Great Weapon Fighting",
    "Combattere con due armi": "Two-Weapon Fighting",
    "Difesa": "Defense",
    "Tiro": "Archery",
    "Dono del fato": "Boon of Fate",
    "Dono della vista pura": "Boon of Truesight",
    "Dono delle abilità di combattimento": "Boon of Combat Prowess",
    "Dono dell'offensiva irresistibile": "Boon of Irresistible Offense",
    "Dono dello spirito notturno": "Boon of the Night Spirit",
    "Dono del richiamo degli incantesimi": "Boon of Spell Recall",
    "Dono del viaggio dimensionale": "Boon of Dimensional Travel",
}


def pair_group(
    italian_items: Sequence[dict],
    english_items: Sequence[dict],
    signature,
    tiebreak,
    manual: dict[str, str],
    label: str,
) -> tuple[dict[str, str], list[tuple[list[str], list[str]]]]:
    """Accoppia due elenchi; restituisce la tavola e i gruppi rimasti ambigui."""
    by_signature: dict[tuple, tuple[list[dict], list[dict]]] = defaultdict(lambda: ([], []))
    for item in italian_items:
        by_signature[signature(item, True)][0].append(item)
    for item in english_items:
        by_signature[signature(item, False)][1].append(item)

    # Il controllo che conta: ogni firma deve avere lo stesso numero di voci da
    # entrambe le parti. Se no, una delle due estrazioni ha perso qualcosa.
    unbalanced = {
        key: (len(a), len(b)) for key, (a, b) in by_signature.items() if len(a) != len(b)
    }
    if unbalanced:
        raise CrosswalkError(
            f"{label}: firme sbilanciate fra le due edizioni: "
            + ", ".join(f"{k} → IT {a}, EN {b}" for k, (a, b) in list(unbalanced.items())[:5])
        )

    mapping: dict[str, str] = {}
    ambiguous: list[tuple[list[str], list[str]]] = []
    for italian_group, english_group in by_signature.values():
        if len(italian_group) == 1:
            mapping[italian_group[0]["name"]] = english_group[0]["name"]
            continue
        # Dentro il gruppo si prova con i numeri.
        english_by_tiebreak: dict[tuple, list[dict]] = defaultdict(list)
        for item in english_group:
            english_by_tiebreak[tiebreak(item, False)].append(item)
        leftover_italian: list[dict] = []
        for item in italian_group:
            candidates = english_by_tiebreak.get(tiebreak(item, True), [])
            if len(candidates) == 1:
                mapping[item["name"]] = candidates.pop()["name"]
            else:
                leftover_italian.append(item)
        leftover_english = [i for group in english_by_tiebreak.values() for i in group]
        for item in list(leftover_italian):
            chosen = manual.get(item["name"])
            if chosen and any(e["name"] == chosen for e in leftover_english):
                mapping[item["name"]] = chosen
                leftover_english = [e for e in leftover_english if e["name"] != chosen]
                leftover_italian.remove(item)
        if leftover_italian:
            ambiguous.append(
                ([i["name"] for i in leftover_italian], [e["name"] for e in leftover_english])
            )
    return mapping, ambiguous


# ---------------------------------------------------------------------------
# Privilegi di classe
#
# Qui i nomi non bastano come chiave — «Ripetibile» compare quattro volte nel
# solo warlock — quindi la tavola accoppia identificativo con identificativo.
#
# L'ordine di lettura e' quasi sempre strutturale, cioe' per livello crescente,
# e li' la posizione dentro (classe, tipo) e' una chiave affidabile: lo verifica
# il confronto fra le sequenze di livello minimo, che devono coincidere. Fanno
# eccezione due elenchi ordinati alfabeticamente nella propria lingua — le
# suppliche occulte e le opzioni di metamagia — dove la posizione accoppierebbe
# «Armatura delle ombre» con «Agonizing Blast». Quelli si scrivono a mano.
# ---------------------------------------------------------------------------

MANUAL_INVOCATIONS: dict[str, str] = {
    "Armatura delle ombre": "Armor of Shadows",
    "Balzo ultraterreno": "Otherworldly Leap",
    "Conoscenze degli Antichi": "Lessons of the First Ones",
    "Deflagrazione agonizzante": "Agonizing Blast",
    "Deflagrazione respingente": "Repelling Blast",
    "Dono degli abissi": "Gift of the Depths",
    "Dono del protettore": "Gift of the Protectors",
    "Investitura del Signore delle Catene": "Investment of the Chain Master",
    "Lama assetata": "Thirsting Blade",
    "Lama divoratrice": "Devouring Blade",
    "Lancia occulta": "Eldritch Spear",
    "Maestro di mille forme": "Master of Myriad Forms",
    "Maschera dei molti volti": "Mask of Many Faces",
    "Mente occulta": "Eldritch Mind",
    "Passo ascendente": "Ascendant Step",
    "Patto del tomo": "Pact of the Tome",
    "Patto della catena": "Pact of the Chain",
    "Patto della lama": "Pact of the Blade",
    "Punizione occulta": "Eldritch Smite",
    "Sguardo delle due menti": "Gaze of Two Minds",
    "Succhiavita": "Lifedrinker",
    "Sussurri dalla tomba": "Whispers of the Grave",
    "Tutt'uno con le ombre": "One with Shadows",
    "Vigore immondo": "Fiendish Vigor",
    "Visione dei Reami Lontani": "Visions of Distant Realms",
    "Visioni velate": "Misty Visions",
    "Vista del diavolo": "Devil\u2019s Sight",
    "Vista stregata": "Witch Sight",
}

MANUAL_METAMAGIC: dict[str, str] = {
    "Incantesimo celato": "Subtle Spell",
    "Incantesimo distante": "Distant Spell",
    "Incantesimo esteso": "Extended Spell",
    "Incantesimo intensificato": "Heightened Spell",
    "Incantesimo mirato": "Seeking Spell",
    "Incantesimo potenziato": "Empowered Spell",
    "Incantesimo preciso": "Careful Spell",
    "Incantesimo raddoppiato": "Twinned Spell",
    "Incantesimo rapido": "Quickened Spell",
    "Incantesimo trasmutato": "Transmuted Spell",
}

BY_NAME = {"supplica-occulta": MANUAL_INVOCATIONS, "metamagia": MANUAL_METAMAGIC}

# Le opzioni interne che l'SRD elenca in ordine alfabetico dentro il proprio
# privilegio. Sono poche e vanno scritte: la posizione, in questi gruppi, e' una
# coincidenza fra due alfabeti diversi.
MANUAL_OPTIONS: dict[str, str] = {
    # Ordine divino del chierico
    "Protettore": "Protector",
    "Taumaturgo": "Thaumaturge",
    # Privilegi di dominio, sia al 7º sia al 17º livello
    "Colpo divino": "Divine Strike",
    "Incantesimi potenti": "Potent Spellcasting",
    # Colpo astuto del ladro
    "Inciampo (costo: 1d6)": "Trip (Cost: 1d6)",
    "Ritirata (costo: 1d6)": "Withdraw (Cost: 1d6)",
    "Veleno (costo: 1d6)": "Poison (Cost: 1d6)",
    # Maestria del furfante
    "Rapidità di mano": "Sleight of Hand",
    "Usare un oggetto": "Use an Object",
    # Uso di oggetti magici del ladro
    "Cariche": "Charges",
    "Pergamene": "Scrolls",
    "Sintonia": "Attunement",
    # Nube sacra del paladino
    "Danni radiosi": "Radiant Damage",
    "Luce del sole": "Sunlight",
    "Sentinella sacra": "Holy Ward",
    # Preda del cacciatore e Tattiche difensive del ranger
    "Devastatore dell'orda": "Horde Breaker",
    "Sterminatore di colossi": "Colossus Slayer",
    "Difesa dal multiattacco": "Multiattack Defense",
    "Sfuggire all'orda": "Escape the Horde",
    # Stregoneria innata
    "Convertire gli slot incantesimo in punti stregoneria":
        "Converting Spell Slots to Sorcery Points",
    "Creare slot incantesimo": "Creating Spell Slots",
    # Suppliche occulte del warlock
    "Prerequisiti": "Prerequisites",
    "Sostituire e ottenere suppliche": "Replacing and Gaining Invocations",
}


def read_features(directory: Path) -> list[dict]:
    raw = json.loads((directory / "class-features.json").read_text(encoding="utf-8"))
    records = raw.get("features") or raw.get("records")
    return sorted(records, key=lambda r: r["source_order"])


def parent_of(records: Sequence[dict]) -> dict[int, int | None]:
    """Per ogni opzione interna, l'ordine della voce che la ospita.

    Un'opzione interna e' un paragrafo dentro un privilegio — «Ripetibile» sotto
    una supplica — e non ha un ordine proprio: segue il padre. Accoppiarle per
    posizione dentro la classe fallisce, perche' i padri stessi si riordinano.
    """
    owner: dict[int, int | None] = {}
    last: int | None = None
    for record in records:
        if record["kind"] == "internal-option":
            owner[record["source_order"]] = last
        else:
            last = record["source_order"]
    return owner


def pair_children(kids: Sequence[dict], siblings: Sequence[dict]) -> list[tuple[dict, dict]]:
    """Accoppia le opzioni interne di uno stesso privilegio.

    Tre criteri, dal piu' affidabile al meno:

    1. **I dadi**, che la traduzione non tocca e che spesso stanno nel nome
       stesso: «Atterramento (costo: 6d6)» con «Knock Out (Cost: 6d6)».
    2. **La tavola a mano**, per i gruppi che l'SRD elenca in ordine alfabetico
       — e l'alfabeto delle due lingue non e' lo stesso. Qui non c'e' ripiego:
       una voce mancante ferma la generazione, perche' indovinare produrrebbe
       coppie plausibili e sbagliate, e sbagliate in silenzio.
    3. **La posizione**, che resta valida solo dove l'ordine e' strutturale:
       «Trucchetti, Slot incantesimo, Incantesimi preparati…» sta nello stesso
       ordine in entrambe le edizioni perche' e' l'ordine del libro, non
       dell'alfabeto.
    """
    fingerprints_it = [dice_fingerprint(k["name"] + " " + k["description"]) for k in kids]
    fingerprints_en = [dice_fingerprint(s["name"] + " " + s["description"]) for s in siblings]
    paired: list[tuple[dict, dict]] = []
    used: set[int] = set()
    leftover_it: list[dict] = []
    for kid, fingerprint in zip(kids, fingerprints_it):
        matches = [
            index
            for index, other in enumerate(fingerprints_en)
            if index not in used and other == fingerprint and fingerprint
        ]
        if len(matches) == 1 and fingerprints_it.count(fingerprint) == 1:
            used.add(matches[0])
            paired.append((kid, siblings[matches[0]]))
        else:
            leftover_it.append(kid)
    leftover_en = [s for index, s in enumerate(siblings) if index not in used]

    if len(leftover_it) > 1:
        names_it = [k["name"] for k in leftover_it]
        names_en = [s["name"] for s in leftover_en]
        # Se cio' che resta e' ordinato alfabeticamente in *entrambe* le lingue,
        # la posizione e' una coincidenza fra due alfabeti: e' il caso in cui
        # «Inciampo, Ritirata, Veleno» finiva su «Poison, Trip, Withdraw».
        if (
            names_it == sorted(names_it, key=str.casefold)
            and names_en == sorted(names_en, key=str.casefold)
        ):
            return paired + pair_by_hand(leftover_it, leftover_en)

    return paired + list(zip(leftover_it, leftover_en))


def pair_by_hand(kids: Sequence[dict], siblings: Sequence[dict]) -> list[tuple[dict, dict]]:
    """Accoppia opzioni interne ordinate alfabeticamente, e solo a mano.

    Non c'e' ripiego: se una voce non e' nella tavola, la generazione fallisce.
    Un ripiego posizionale qui produrrebbe coppie *plausibili* e sbagliate —
    «Sentinella sacra» che diventa «Sunlight» — e sbagliate in silenzio, perche'
    entrambi gli identificativi esistono e nessun conteggio se ne accorge.
    """
    by_name = {s["name"]: s for s in siblings}
    paired: list[tuple[dict, dict]] = []
    for kid in kids:
        wanted = MANUAL_OPTIONS.get(kid["name"])
        target = by_name.get(wanted) if wanted else None
        if target is None:
            raise CrosswalkError(
                f"opzione interna senza resa a mano: «{kid['name']}» "
                f"(possibili: {sorted(by_name)}). Aggiungila a MANUAL_OPTIONS."
            )
        paired.append((kid, target))
    return paired


def pair_class_features() -> tuple[dict[str, str], list[str]]:
    italian = read_features(ITALIAN_DIR)
    english = read_features(ENGLISH_DIR)
    if len(italian) != len(english):
        raise CrosswalkError(
            f"privilegi: {len(italian)} voci italiane contro {len(english)} inglesi"
        )

    problems: list[str] = []
    mapping: dict[str, str] = {}
    matched_order: dict[int, int] = {}

    groups_it = defaultdict(list)
    groups_en = defaultdict(list)
    for record in italian:
        groups_it[(record["class"], record["kind"])].append(record)
    for record in english:
        groups_en[(record["class"], record["kind"])].append(record)
    if set(groups_it) != set(groups_en):
        raise CrosswalkError("privilegi: i gruppi (classe, tipo) non coincidono")

    for key in sorted(groups_it):
        left, right = groups_it[key], groups_en[key]
        if len(left) != len(right):
            problems.append(f"privilegi {key}: {len(left)} contro {len(right)}")
            continue
        table = BY_NAME.get(key[1])
        if table is not None:
            by_name = {record["name"]: record for record in right}
            for record in left:
                wanted = table.get(record["name"])
                target = by_name.get(wanted) if wanted else None
                if target is None:
                    problems.append(f"privilegi {key}: manca la resa di «{record['name']}»")
                    continue
                mapping[record["id"]] = target["id"]
                matched_order[record["source_order"]] = target["source_order"]
            continue
        if key[1] == "internal-option":
            continue  # trattate dopo, quando i padri sono noti
        levels_it = [record["minimum_level"] for record in left]
        levels_en = [record["minimum_level"] for record in right]
        if levels_it != levels_en:
            problems.append(f"privilegi {key}: livelli discordi, la posizione non e' affidabile")
            continue
        for record, target in zip(left, right):
            mapping[record["id"]] = target["id"]
            matched_order[record["source_order"]] = target["source_order"]

    # Le opzioni interne seguono il padre: stesso padre, stessa posizione sotto
    # di lui. Se il padre non e' stato accoppiato, non lo sono nemmeno loro.
    owner_it, owner_en = parent_of(italian), parent_of(english)
    children_en: dict[int, list[dict]] = defaultdict(list)
    for record in english:
        if record["kind"] == "internal-option":
            children_en[owner_en[record["source_order"]]].append(record)
    children_it: dict[int, list[dict]] = defaultdict(list)
    for record in italian:
        if record["kind"] == "internal-option":
            children_it[owner_it[record["source_order"]]].append(record)

    for parent, kids in sorted(children_it.items(), key=lambda item: (item[0] is None, item[0])):
        target_parent = matched_order.get(parent)
        siblings = children_en.get(target_parent, [])
        if target_parent is None or len(siblings) != len(kids):
            problems.append(
                f"privilegi: {len(kids)} opzioni interne senza padre accoppiato "
                f"({kids[0]['id']})"
            )
            continue
        for record, target in pair_children(kids, siblings):
            mapping[record["id"]] = target["id"]

    missing = len(italian) - len(mapping)
    if missing:
        problems.append(f"privilegi: {missing} voci non accoppiate")
    return dict(sorted(mapping.items())), problems


def build(report: bool = False) -> tuple[dict[str, dict[str, str]], list[str]]:
    problems: list[str] = []
    result: dict[str, dict[str, str]] = {}

    for label, name, key, signature, tiebreak, manual in (
        ("incantesimi", "spells", "spells", spell_signature, spell_tiebreak, MANUAL_SPELLS),
        ("bestie", "beasts", "records", beast_signature, beast_tiebreak, MANUAL_BEASTS),
        ("talenti", "feats", "feats", feat_signature, feat_tiebreak, MANUAL_FEATS),
    ):
        italian_items = load(ITALIAN_DIR, name, key)
        english_items = load(ENGLISH_DIR, name, key)
        if len(italian_items) != len(english_items):
            raise CrosswalkError(
                f"{label}: {len(italian_items)} voci italiane contro {len(english_items)} inglesi"
            )
        mapping, ambiguous = pair_group(
            italian_items, english_items, signature, tiebreak, manual, label
        )
        result[name] = dict(sorted(mapping.items()))
        for italian_names, english_names in ambiguous:
            problems.append(f"{label}: {sorted(italian_names)} ←→ {sorted(english_names)}")
        missing = len(italian_items) - len(mapping)
        if report:
            print(
                f"{label}: {len(mapping)}/{len(italian_items)} accoppiati"
                + (f" · {missing} da mappare a mano" if missing else ""),
                file=sys.stderr,
            )
    features, feature_problems = pair_class_features()
    result["class_features"] = features
    problems.extend(feature_problems)
    if report:
        print(f"privilegi: {len(features)}/408 accoppiati", file=sys.stderr)

    return result, problems


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true", help="verifica senza scrivere")
    args = parser.parse_args(argv)

    try:
        mapping, problems = build(report=True)
    except CrosswalkError as failure:
        print(f"errore: {failure}", file=sys.stderr)
        return 1

    if problems:
        print("\nvoci ancora ambigue, da aggiungere alle tavole a mano:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 2

    payload = json.dumps(
        {"schema_version": 1, "from": "it", "to": "en", "names": mapping},
        ensure_ascii=False,
        indent=2,
    ) + "\n"
    if args.check:
        current = args.output.read_text(encoding="utf-8") if args.output.is_file() else ""
        if current != payload:
            print(f"tavola non aggiornata: {args.output}", file=sys.stderr)
            return 1
        print(f"OK: {args.output}")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(payload, encoding="utf-8")
    print(f"scritta in {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
