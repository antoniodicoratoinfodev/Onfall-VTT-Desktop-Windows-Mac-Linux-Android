#!/usr/bin/env python3
"""Regression tests for the SRD spell extractor, in both languages.

The two SRDs share every layout rule and differ only in the profile, so each
integration test runs once per language: a change that fixes one edition and
breaks the other cannot pass.
"""

from __future__ import annotations

import json
import shutil
import unittest
from pathlib import Path

import extract_srd_spells as extractor


ITALIAN = extractor.PROFILES["it"]
ENGLISH = extractor.PROFILES["en"]


class TextNormalizationTests(unittest.TestCase):
    def test_discretionary_hyphenation_is_removed(self) -> None:
        self.assertEqual(
            extractor._join_wrapped_lines(["incante-", "simo completo"], ITALIAN),
            "incantesimo completo",
        )

    def test_real_inline_hyphen_is_preserved(self) -> None:
        self.assertEqual(
            extractor._join_wrapped_lines(["Anti-individuazione"], ITALIAN),
            "Anti-individuazione",
        )

    def test_stat_block_ability_labels_are_recombined(self) -> None:
        self.assertEqual(
            extractor._normalize_text("F or 18 D es 12 C os 14", ITALIAN),
            "For 18 Des 12 Cos 14",
        )

    def test_english_leaves_isolated_capitals_alone(self) -> None:
        # La ricucitura delle sigle e' italiana: in inglese "A cold" e' prosa.
        self.assertEqual(
            extractor._normalize_text("A cold wind", ENGLISH),
            "A cold wind",
        )

    def test_english_repairs_small_caps_titles(self) -> None:
        # Nei titoli in maiuscoletto il PDF stacca l'iniziale di ogni parola.
        self.assertEqual(
            extractor._spell_title("A cid S plash", ENGLISH),
            "Acid Splash",
        )

    def test_italian_titles_are_left_untouched(self) -> None:
        self.assertEqual(
            extractor._spell_title("Aculeo mentale", ITALIAN),
            "Aculeo mentale",
        )


@unittest.skipUnless(shutil.which("pdftotext"), "pdftotext/Poppler non disponibile")
class PdfIntegrationTests(unittest.TestCase):
    profile = ITALIAN

    @classmethod
    def setUpClass(cls) -> None:
        if cls is PdfIntegrationTests and cls.profile is not ITALIAN:
            raise unittest.SkipTest("classe base")
        cls.pdf_path = extractor._default_pdf(cls.profile)
        if not cls.pdf_path.is_file():
            raise unittest.SkipTest(f"PDF SRD non trovato: {cls.pdf_path}")
        cls.document, cls.anomalies = extractor._build_document(
            cls.pdf_path, cls.profile
        )

    def test_expected_srd_catalog_is_complete(self) -> None:
        spells = self.document["spells"]
        self.assertEqual(self.document["spell_count"], self.profile.expected_count)
        self.assertEqual(len(spells), self.profile.expected_count)
        self.assertEqual(spells[0]["name"], self.profile.expected_first)
        self.assertEqual(spells[-1]["name"], self.profile.expected_last)
        self.assertEqual(self.anomalies, [])

    def test_cantrips_are_recognised(self) -> None:
        # I trucchetti dello SRD sono ventisette in entrambe le edizioni: se
        # l'ordine di scuola e livello cambia, e' qui che si vede.
        cantrips = [s for s in self.document["spells"] if s["is_cantrip"]]
        self.assertEqual(len(cantrips), 27)

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
        second_document, second_anomalies = extractor._build_document(
            self.pdf_path, self.profile
        )
        self.assertEqual(second_anomalies, [])
        options = {"ensure_ascii": False, "indent": 2, "sort_keys": False}
        self.assertEqual(
            json.dumps(self.document, **options),
            json.dumps(second_document, **options),
        )


class EnglishPdfIntegrationTests(PdfIntegrationTests):
    profile = ENGLISH


if __name__ == "__main__":
    unittest.main()
