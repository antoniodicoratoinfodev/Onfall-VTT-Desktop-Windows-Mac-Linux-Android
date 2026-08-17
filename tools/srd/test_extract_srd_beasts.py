import unittest

from extract_srd_beasts import PROFILES, content_slug, parse_records

ITALIAN = PROFILES["it"]


class ExtractSrdBeastsTest(unittest.TestCase):
    def test_extracts_only_beasts_in_the_wild_shape_cr_range(self):
        fixture = """
385 System Reference Document 5.2.1
Topo
Bestia Minuscola, senza allineamento
CA 10 Iniziativa +0 (10)
PF 1 (1d4 − 1)
Velocità 6 m, scalata 6 m
GS 0 (PE 10; BC +2)
Azioni
Mor-
so. Colpito: 1 danno perforante.
Aquila gigante
Celestiale Grande, neutrale buono
CA 13 Iniziativa +3 (13)
PF 26 (4d10 + 4)
Velocità 3 m, volo 24 m
GS 1 (PE 200; BC +2)
Lupo
Bestia Media, senza allineamento
CA 12 Iniziativa +2 (12)
PF 11 (2d8 + 2)
Velocità 12 m
GS 1/4 (PE 50; BC +2)
Allosauro
Bestia Grande (dinosauro), senza allineamento
CA 13 Iniziativa +1 (11)
PF 51 (6d10 + 18)
Velocità 18 m
GS 2 (PE 450; BC +2)
"""

        records = parse_records(fixture, ITALIAN)

        self.assertEqual(["Topo", "Lupo"], [record["name"] for record in records])
        self.assertIn("Morso.", records[0]["stat_block"])
        self.assertFalse(records[0]["has_fly_speed"])

    def test_marks_a_real_beast_fly_speed_and_normalizes_its_id(self):
        fixture = """
System Reference Document 5.2.1 405
Vespa gigante
Bestia Media, senza allineamento
CA 13 Iniziativa +2 (12)
PF 22 (5d8)
Velocità 3 m, volo 15 m
GS 1/2 (PE 100; BC +2)
Azioni
Pungiglione. Colpito: 5 danni perforanti.
"""

        record = parse_records(fixture, ITALIAN)[0]

        self.assertEqual("srd521-it:beast:vespa-gigante", record["id"])
        self.assertTrue(record["has_fly_speed"])
        self.assertEqual("vespa-gigante", content_slug("Vespa gigante"))


if __name__ == "__main__":
    unittest.main()
