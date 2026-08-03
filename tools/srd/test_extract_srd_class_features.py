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
PDF = ROOT / "tmp/pdfs/IT_SRD_CC_v5.2.1.pdf"

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


@unittest.skipUnless(PDF.is_file() and shutil.which("pdftohtml"), "PDF SRD o Poppler non disponibile")
class ClassFeatureExtractorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = MODULE.build_catalog(PDF)

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

    def test_ids_and_descriptions_are_complete(self) -> None:
        records = self.catalog["records"]
        ids = [record["id"] for record in records]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue(all(record["description"].strip() for record in records))
        self.assertTrue(
            all(
                record["id"].startswith(("srd521-it:feature:", "srd521-it:subclass-feature:"))
                for record in records
            )
        )
        self.assertFalse(
            any("System Reference Document 5.2.1" in record["description"] for record in records)
        )

    def test_generation_is_byte_deterministic(self) -> None:
        first = MODULE.serialize(self.catalog)
        second = MODULE.serialize(MODULE.build_catalog(PDF))
        self.assertEqual(first, second)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "features.json"
            output.write_bytes(first)
            self.assertEqual(self.catalog, json.loads(output.read_text(encoding="utf-8")))

    def test_representative_nested_options_are_searchable(self) -> None:
        names = {record["name"] for record in self.catalog["records"]}
        self.assertIn("Colpo violento", names)
        self.assertIn("Devastatore dell'orda", names)
        self.assertIn("Incantesimo rapido", names)
        self.assertIn("Patto della lama", names)


if __name__ == "__main__":
    unittest.main()
