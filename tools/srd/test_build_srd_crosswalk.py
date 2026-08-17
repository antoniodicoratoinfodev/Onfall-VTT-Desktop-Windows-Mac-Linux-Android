#!/usr/bin/env python3
"""La tavola di corrispondenza fra le due edizioni dell'SRD.

Un accoppiamento sbagliato qui non si vede: produce un identificativo valido che
punta alla cosa sbagliata, e il difetto salta fuori mesi dopo su una scheda che
non risolve piu' i propri privilegi. Per questo i controlli guardano le proprieta'
strutturali — totalita', biiettivita', invarianza dei numeri — invece di fidarsi
di qualche nome preso a campione.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

import build_srd_crosswalk as crosswalk


ROOT = Path(__file__).resolve().parents[2]
TABLE = ROOT / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-en/crosswalk.json"


class SignatureTest(unittest.TestCase):
    def test_components_ignore_the_material_description(self) -> None:
        # "M (una piuma bianca)" non deve contribuire con le lettere del testo.
        self.assertEqual("MSV", crosswalk.components("V, S, M (una piuma bianca)"))
        self.assertEqual("SV", crosswalk.components("V, S"))

    def test_italian_ranges_are_read_back_in_feet(self) -> None:
        # 18 m e' la resa italiana di 60 piedi: la firma deve vederli uguali.
        self.assertEqual(crosswalk.feet_in("18 metri", True), crosswalk.feet_in("60 feet", False))
        self.assertEqual(crosswalk.feet_in("9/36 m", True), crosswalk.feet_in("30/120 feet", False))

    def test_dice_survive_translation(self) -> None:
        self.assertEqual(
            crosswalk.dice_fingerprint("subisce 3d8 danni psichici"),
            crosswalk.dice_fingerprint("takes 3d8 Psychic damage"),
        )


@unittest.skipUnless(TABLE.is_file(), "tavola non ancora generata")
class TableTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.names = json.loads(TABLE.read_text(encoding="utf-8"))["names"]

    def test_every_entry_is_paired(self) -> None:
        for section, count in (
            ("spells", 339),
            ("beasts", 64),
            ("feats", 17),
            ("class_features", 408),
        ):
            self.assertEqual(count, len(self.names[section]), section)

    def test_class_features_are_keyed_by_identifier(self) -> None:
        # Non per nome: «Ripetibile» compare quattro volte nel solo warlock.
        for italian_id, english_id in self.names["class_features"].items():
            self.assertTrue(italian_id.startswith("srd521-it:"), italian_id)
            self.assertTrue(english_id.startswith("srd521-en:"), english_id)

    def test_paired_features_agree_on_level_and_class(self) -> None:
        # Le due edizioni sono lo stesso libro: una coppia che non concorda sul
        # livello minimo o sulla classe e' un accoppiamento sbagliato, non una
        # differenza di edizione.
        italian = {r["id"]: r for r in crosswalk.read_features(crosswalk.ITALIAN_DIR)}
        english = {r["id"]: r for r in crosswalk.read_features(crosswalk.ENGLISH_DIR)}
        for italian_id, english_id in self.names["class_features"].items():
            left, right = italian[italian_id], english[english_id]
            self.assertEqual(left["minimum_level"], right["minimum_level"], italian_id)
            self.assertEqual(left["class"], right["class"], italian_id)
            self.assertEqual(left["kind"], right["kind"], italian_id)

    def test_the_invocation_level_prerequisite_is_read_in_both_editions(self) -> None:
        # Il difetto che questo controllo sorveglia: «Conoscenze degli Antichi»
        # dichiara «warlock di 2º livello» e per un periodo e' stata registrata
        # come accessibile dal primo, perche' il livello si leggeva solo quando
        # riusciva anche il ritaglio della frase di prerequisito.
        for directory in (crosswalk.ITALIAN_DIR, crosswalk.ENGLISH_DIR):
            invocations = [
                r for r in crosswalk.read_features(directory) if r["kind"] == "supplica-occulta"
            ]
            self.assertEqual(28, len(invocations), directory.name)
            levels = sorted(r["minimum_level"] for r in invocations)
            self.assertEqual(
                [1] * 5 + [2] * 9 + [5] * 8 + [7] + [9] * 3 + [12] + [15],
                levels,
                directory.name,
            )

    def test_alphabetical_options_are_paired_by_meaning(self) -> None:
        """Le opzioni interne che l'SRD elenca in ordine alfabetico.

        Qui la posizione e' una coincidenza fra due alfabeti diversi, e
        accoppiare per posizione produceva coppie valide e sbagliate:
        «Sentinella sacra» diventava «Sunlight», «Inciampo» diventava «Poison».
        Sbagliate in silenzio, perche' entrambi gli identificativi esistono e
        nessun conteggio se ne accorge.
        """
        expected = {
            "inciampo-costo-1d6": "trip-cost-1d6",
            "ritirata-costo-1d6": "withdraw-cost-1d6",
            "veleno-costo-1d6": "poison-cost-1d6",
            "atterramento-costo-6d6": "knock-out-cost-6d6",
            "pergamene": "scrolls",
            "sintonia": "attunement",
            "danni-radiosi": "radiant-damage",
            "luce-del-sole": "sunlight",
            "sentinella-sacra": "holy-ward",
            "difesa-dal-multiattacco": "multiattack-defense",
            "sfuggire-allorda": "escape-the-horde",
            "devastatore-dellorda": "horde-breaker",
            "sterminatore-di-colossi": "colossus-slayer",
        }
        features = self.names["class_features"]
        for italian_slug, english_slug in expected.items():
            keys = [k for k in features if k.endswith(f":{italian_slug}")]
            self.assertEqual(1, len(keys), f"slug ambiguo o assente: {italian_slug}")
            self.assertEqual(
                english_slug,
                features[keys[0]].rsplit(":", 1)[-1],
                italian_slug,
            )

    def test_an_unmapped_alphabetical_option_stops_the_build(self) -> None:
        # Il ripiego posizionale non deve tornare di soppiatto: se qualcuno
        # aggiunge un'opzione alfabetica senza la sua resa, la generazione deve
        # fermarsi invece di indovinare.
        kids = [{"name": "Alfa", "description": ""}, {"name": "Beta", "description": ""}]
        siblings = [{"name": "Delta", "description": ""}, {"name": "Gamma", "description": ""}]
        with self.assertRaises(crosswalk.CrosswalkError):
            crosswalk.pair_children(kids, siblings)

    def test_the_pairing_is_bijective(self) -> None:
        # Due voci italiane che puntano allo stesso nome inglese vorrebbero dire
        # che una terza e' rimasta senza: la biiettivita' e' la rete piu' stretta.
        for section, mapping in self.names.items():
            values = list(mapping.values())
            self.assertEqual(len(values), len(set(values)), f"{section}: nomi ripetuti")

    def test_names_match_the_extracted_catalogues(self) -> None:
        sources = (
            ("spells", "spells", "spells"),
            ("beasts", "beasts", "records"),
            ("feats", "feats", "feats"),
        )
        for section, file_name, key in sources:
            italian = {i["name"] for i in crosswalk.load(crosswalk.ITALIAN_DIR, file_name, key)}
            english = {i["name"] for i in crosswalk.load(crosswalk.ENGLISH_DIR, file_name, key)}
            self.assertEqual(italian, set(self.names[section]), f"{section}: chiavi italiane")
            self.assertEqual(english, set(self.names[section].values()), f"{section}: valori inglesi")

    def test_regeneration_is_stable(self) -> None:
        rebuilt, problems = crosswalk.build()
        self.assertEqual([], problems)
        self.assertEqual(self.names, rebuilt)

    def test_known_pairs_are_right(self) -> None:
        # Poche voci scelte perche' sbagliarle sarebbe clamoroso — compresa la
        # coppia di scuole che si scambiano fra le due lingue.
        self.assertEqual("Fireball", self.names["spells"]["Palla di fuoco"])
        self.assertEqual("Lightning Bolt", self.names["spells"]["Fulmine"])
        self.assertEqual("Gaseous Form", self.names["spells"]["Forma gassosa"])
        self.assertEqual("Elk", self.names["beasts"]["Alce"])
        self.assertEqual("Magic Initiate", self.names["feats"]["Iniziato alla magia"])

    def test_magic_schools_are_not_false_friends(self) -> None:
        # «Evocazione» e' Conjuration, «Invocazione» e' Evocation. Scambiarle
        # accoppierebbe meta' del capitolo con l'altra meta'.
        self.assertEqual("Conjuration", crosswalk.SCHOOLS["Evocazione"])
        self.assertEqual("Evocation", crosswalk.SCHOOLS["Invocazione"])


if __name__ == "__main__":
    unittest.main()
