#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/srd/extract_srd_class_features.py"
def _pdf(profile) -> Path:
    beside = ROOT / profile.pdf_name
    return beside if beside.is_file() else ROOT / "tmp/pdfs" / profile.pdf_name

SPEC = importlib.util.spec_from_file_location("extract_srd_class_features", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ActivationExtractionTest(unittest.TestCase):
    def test_action_surge_exception_is_not_an_activation(self) -> None:
        description = (
            "Nel suo turno, può effettuare un'azione aggiuntiva, "
            "fatta eccezione per l'azione di Magia."
        )
        self.assertIsNone(MODULE.activation_from(description))

    def test_explicit_activation_costs_are_preserved(self) -> None:
        self.assertEqual("azione", MODULE.activation_from("Usare un'azione per attivarlo."))
        self.assertEqual("azione bonus", MODULE.activation_from("Come azione bonus, si trasforma."))
        self.assertEqual("reazione", MODULE.activation_from("Può usare la sua reazione."))
        self.assertEqual("azione di Magia", MODULE.activation_from("Come azione di Magia, lancia."))

    def test_english_activation_costs_are_preserved(self) -> None:
        self.assertEqual("Action", MODULE.activation_from("As an Action, you regain Hit Points."))
        self.assertEqual("Bonus Action", MODULE.activation_from("You can enter it as a Bonus Action."))
        self.assertEqual("Reaction", MODULE.activation_from("You can use your Reaction to respond."))
        self.assertEqual("Magic action", MODULE.activation_from("Take the Magic action to cast it."))

    def test_english_action_surge_exception_is_not_an_activation(self) -> None:
        description = "On your turn, you can take one additional action, except the Magic action."
        self.assertIsNone(MODULE.activation_from(description))


@unittest.skipUnless(shutil.which("pdftohtml"), "Poppler non disponibile")
class ClassFeatureExtractorTest(unittest.TestCase):
    """Il capitolo delle classi, letto nella lingua del profilo.

    La sottoclasse inglese eredita tutto: i due SRD sono lo stesso libro, quindi
    ogni conteggio strutturale deve coincidere. Se una delle due estrazioni perde
    un privilegio per strada, e' qui che i numeri si separano.
    """

    language = "it"

    @classmethod
    def setUpClass(cls) -> None:
        cls.profile = MODULE.PROFILES[cls.language]
        pdf = _pdf(cls.profile)
        if not pdf.is_file():
            raise unittest.SkipTest(f"PDF SRD non trovato: {pdf}")
        cls.pdf = pdf
        cls.catalog = MODULE.build_catalog(pdf, cls.profile)

    def test_official_class_and_option_counts(self) -> None:
        self.assertEqual(12, self.catalog["counts"]["classes"])
        self.assertEqual(10, self.catalog["counts"]["by_kind"]["metamagia"])
        self.assertEqual(28, self.catalog["counts"]["by_kind"]["supplica-occulta"])
        self.assertEqual(
            {
                "barbaro",
                "bardo",
                "chierico",
                "druido",
                "guerriero",
                "ladro",
                "mago",
                "monaco",
                "paladino",
                "ranger",
                "stregone",
                "warlock",
            },
            {
                record["class"]
                for record in self.catalog["records"]
                if record["kind"] == "subclass-feature"
            },
        )

    def test_structural_counts_match_the_other_edition(self) -> None:
        # Numeri del libro, non della lingua: 408 voci e 232 titoli di livello.
        self.assertEqual(408, self.catalog["counts"]["records"])
        self.assertEqual(232, self.catalog["counts"]["level_headings"])

    def test_ids_and_descriptions_are_complete(self) -> None:
        records = self.catalog["records"]
        ids = [record["id"] for record in records]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue(all(record["description"].strip() for record in records))
        self.assertTrue(
            all(
                record["id"].startswith(
                    (
                        f"srd521-{self.profile.tag}:feature:",
                        f"srd521-{self.profile.tag}:subclass-feature:",
                    )
                )
                for record in records
            )
        )
        self.assertFalse(
            any("System Reference Document 5.2.1" in record["description"] for record in records)
        )

    def test_generation_is_byte_deterministic(self) -> None:
        first = MODULE.serialize(self.catalog)
        second = MODULE.serialize(MODULE.build_catalog(self.pdf, self.profile))
        self.assertEqual(first, second)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "features.json"
            output.write_bytes(first)
            self.assertEqual(self.catalog, json.loads(output.read_text(encoding="utf-8")))

    # Opzioni annidate rappresentative: se il riconoscimento dei sotto-titoli in
    # grassetto si rompe, spariscono queste prima di ogni altra cosa.
    nested_options = ("Colpo violento", "Devastatore dell'orda", "Incantesimo rapido", "Patto della lama")

    def test_representative_nested_options_are_searchable(self) -> None:
        names = {record["name"] for record in self.catalog["records"]}
        for option in self.nested_options:
            self.assertIn(option, names)


class EnglishClassFeatureExtractorTest(ClassFeatureExtractorTest):
    language = "en"
    nested_options = ("Brutal Strike", "Horde Breaker", "Quickened Spell", "Pact of the Blade")



class ChapterBoundaryTest(unittest.TestCase):
    """L'ultimo privilegio non deve inghiottire il capitolo che segue.

    Il capitolo delle classi non finisce a fine pagina: dopo l'ultimo privilegio
    del Mago comincia «Origini del Personaggio». Senza un confine esplicito
    l'estrattore continuava ad accodare, e «Overchannel» arrivava a
    quattromilacento caratteri con dentro background e specie.
    """

    FOREIGN = ("Character Origins", "Origini del Personaggio", "A background gives")

    def test_no_feature_swallows_the_next_chapter(self) -> None:
        for tag in ("it", "en"):
            path = (
                ROOT
                / f"content/srd-5.2.1-it/src/main/resources/srd/5.2.1-{tag}/class-features.json"
            )
            if not path.is_file():
                continue
            records = json.loads(path.read_text(encoding="utf-8"))["records"]
            for record in records:
                for stray in self.FOREIGN:
                    self.assertNotIn(stray, record["description"], f"{tag}: {record['id']}")

    def test_no_description_is_wildly_longer_than_the_rest(self) -> None:
        # Una descrizione fuori scala e' il sintomo con cui il difetto si e'
        # manifestato: e' un controllo grezzo, ma coglie l'accodamento infinito
        # anche quando il testo estraneo non contiene nessuna parola nota.
        for tag in ("it", "en"):
            path = (
                ROOT
                / f"content/srd-5.2.1-it/src/main/resources/srd/5.2.1-{tag}/class-features.json"
            )
            if not path.is_file():
                continue
            lengths = sorted(
                len(r["description"])
                for r in json.loads(path.read_text(encoding="utf-8"))["records"]
            )
            self.assertLess(lengths[-1], 4000, f"{tag}: una descrizione e' fuori scala")

if __name__ == "__main__":
    unittest.main()
