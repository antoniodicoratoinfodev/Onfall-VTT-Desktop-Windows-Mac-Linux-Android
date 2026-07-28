#!/usr/bin/env python3
"""Regression tests for the Italian SRD spell extractor."""

from __future__ import annotations

import json
import shutil
import unittest
from pathlib import Path

import extract_srd_spells as extractor


class TextNormalizationTests(unittest.TestCase):
    def test_discretionary_hyphenation_is_removed(self) -> None:
        self.assertEqual(
            extractor._join_wrapped_lines(["incante-", "simo completo"]),
            "incantesimo completo",
        )

    def test_real_inline_hyphen_is_preserved(self) -> None:
        self.assertEqual(
            extractor._join_wrapped_lines(["Anti-individuazione"]),
            "Anti-individuazione",
        )

    def test_stat_block_ability_labels_are_recombined(self) -> None:
        self.assertEqual(
            extractor._normalize_text("F or 18 D es 12 C os 14"),
            "For 18 Des 12 Cos 14",
        )


@unittest.skipUnless(shutil.which("pdftotext"), "pdftotext/Poppler non disponibile")
class PdfIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.pdf_path = extractor._default_pdf()
        if not cls.pdf_path.is_file():
            raise unittest.SkipTest(f"PDF SRD non trovato: {cls.pdf_path}")
        cls.document, cls.anomalies = extractor._build_document(cls.pdf_path)

    def test_expected_srd_catalog_is_complete(self) -> None:
        spells = self.document["spells"]
        self.assertEqual(self.document["spell_count"], 339)
        self.assertEqual(len(spells), 339)
        self.assertEqual(spells[0]["name"], "Aculeo mentale")
        self.assertEqual(spells[-1]["name"], "Zona di verità")
        self.assertEqual(self.anomalies, [])

    def test_every_spell_has_required_fields(self) -> None:
        required_fields = {
            "name",
            "school",
            "level",
            "is_cantrip",
            "classes",
            "casting_time",
            "range",
            "components",
            "duration",
            "description",
            "source_pages",
        }
        spells = self.document["spells"]
        self.assertEqual(len({spell["name"] for spell in spells}), len(spells))
        for spell in spells:
            self.assertEqual(set(spell), required_fields)
            for field in (
                "name",
                "school",
                "classes",
                "casting_time",
                "range",
                "components",
                "duration",
                "description",
            ):
                self.assertTrue(spell[field], f"{spell['name']}: {field} vuoto")
            self.assertEqual(spell["is_cantrip"], spell["level"] == 0)
            self.assertNotIn("\u00ad", spell["description"])
            self.assertNotIn("System Reference Document", spell["description"])

    def test_serialization_is_deterministic(self) -> None:
        second_document, second_anomalies = extractor._build_document(self.pdf_path)
        self.assertEqual(second_anomalies, [])
        options = {"ensure_ascii": False, "indent": 2, "sort_keys": False}
        self.assertEqual(
            json.dumps(self.document, **options),
            json.dumps(second_document, **options),
        )


if __name__ == "__main__":
    unittest.main()
