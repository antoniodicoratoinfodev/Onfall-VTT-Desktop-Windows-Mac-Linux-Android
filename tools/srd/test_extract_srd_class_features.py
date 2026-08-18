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

    def test_a_granted_action_is_not_a_cost(self) -> None:
        # Il privilegio che *regala* un'azione non se ne paga una: senza questo
        # taglio Azione impetuosa risultava costare un'azione per concederne una.
        self.assertIsNone(MODULE.activation_from("Nel suo turno può effettuare un'azione aggiuntiva."))
        self.assertIsNone(MODULE.activation_from("On your turn, you can take one additional action."))
        self.assertIsNone(MODULE.activation_from("On your turn, you can take an extra action."))

    def test_take_a_reaction_is_the_english_ordinary_formula(self) -> None:
        # «take a Reaction», non «use your reaction»: e' la formula con cui l'SRD
        # inglese scrive otto privilegi difensivi, e senza di essa risultavano
        # tutti passivi mentre gli omologhi italiani costavano una reazione.
        self.assertEqual(
            "Reaction",
            MODULE.activation_from(
                "When an attacker that you can see hits you with an attack roll, you can "
                "take a Reaction to halve the attack’s damage against you."
            ),
        )
        self.assertEqual("Reaction", MODULE.activation_from("It takes a Reaction to deflect the blow."))
        self.assertEqual("Reaction", MODULE.activation_from("As a Reaction, you interpose your shield."))

    def test_italian_alternates_the_verb_before_reazione(self) -> None:
        # L'SRD italiano non ne usa uno solo, e prevederne uno solo lasciava
        # senza costo privilegi che una reazione la spendono davvero.
        for description in (
            "Il ladro può usare la sua reazione per dimezzare i danni.",
            "Il bardo può utilizzare una reazione per sottrarre il dado.",
            "Il monaco effettua una reazione per deviare l'attacco.",
            "Come reazione, il paladino impone le mani.",
        ):
            self.assertEqual("reazione", MODULE.activation_from(description), description)

    def test_the_action_an_object_demands_is_not_the_feature_cost(self) -> None:
        # «Utilizzare un oggetto» descrive cosa chiede l'oggetto magico, non cosa
        # costa il privilegio. Le due edizioni lo nominano con parole diverse, e
        # senza il taglio leggevano cose diverse dalla stessa frase.
        self.assertIsNone(
            MODULE.activation_from(
                "Use an Object. Take the Utilize action, or take the Magic action to use "
                "a magic item that requires that action."
            )
        )
        self.assertIsNone(
            MODULE.activation_from(
                "Usare un oggetto. Puoi effettuare l'azione di Utilizzo o di Magia per "
                "utilizzare un oggetto magico che richiede una di quelle azioni."
            )
        )


class ContainerActivationTest(unittest.TestCase):
    """Un privilegio che elenca opzioni annidate non paga il costo delle opzioni.

    Il difetto: l'attivazione si leggeva sull'intera descrizione, figli compresi.
    «Investitura del Signore delle Catene» contiene un'azione bonus (Attacco
    rapido) e una reazione (Resistenza), e ne usciva dichiarato reazione — non
    perche' lo sia, ma perche' quel ramo del riconoscimento viene provato prima.
    """

    def test_own_text_wins_over_the_options(self) -> None:
        self.assertEqual(
            "azione bonus",
            MODULE.container_activation(
                "Come azione bonus, il druido assume le sembianze di una bestia.",
                [{"activation": "reazione"}, {"activation": None}],
            ),
        )

    def test_a_single_option_cost_becomes_the_container_cost(self) -> None:
        # «Ispirazione bardica» e' un'azione bonus perche' l'unica delle sue voci
        # a costare qualcosa lo e'.
        self.assertEqual(
            "azione bonus",
            MODULE.container_activation(
                "Questa ispirazione è rappresentata dal dado di Ispirazione bardica, un d6.",
                [{"activation": "azione bonus"}, {"activation": None}, {"activation": None}],
            ),
        )

    def test_options_that_disagree_leave_the_container_passive(self) -> None:
        self.assertIsNone(
            MODULE.container_activation(
                "Il famiglio riceve i seguenti benefici.",
                [
                    {"activation": None},
                    {"activation": "azione bonus"},
                    {"activation": "reazione"},
                ],
            )
        )

    def test_a_container_without_costly_options_is_passive(self) -> None:
        self.assertIsNone(
            MODULE.container_activation(
                "La natura fiorisce nel druido, concedendogli i seguenti benefici.",
                [{"activation": None}, {"activation": None}],
            )
        )


class ResourceSpendTest(unittest.TestCase):
    """Nominare una risorsa non e' spenderla.

    Il verbo di consumo si cercava nell'intero testo: bastava che il privilegio
    spendesse qualcosa da qualche parte perche' la prima risorsa nominata
    risultasse la spesa.
    """

    ITALIAN = MODULE.PROFILES["it"]
    ENGLISH = MODULE.PROFILES["en"]

    def test_a_slot_named_as_a_trigger_is_not_spent(self) -> None:
        # Supercanalizzazione: lo slot lo spende l'incantesimo, che lanci
        # comunque; il privilegio ci si limita sopra. L'italiano ne usciva pulito
        # per caso — scrive «uno slot di livello», e il nome non combaciava.
        overchannel = (
            "When you cast a Wizard spell with a spell slot of levels 1–5 that deals damage, "
            "you can deal maximum damage with that spell on the turn you cast it. If you use "
            "this feature again before you finish a Long Rest, you take 2d12 Necrotic damage."
        )
        self.assertIsNone(MODULE.resource_from(overchannel, self.ENGLISH))

    def test_a_stated_cost_line_is_a_spend(self) -> None:
        # Le opzioni di metamagia non hanno verbo: dichiarano il costo e basta.
        # Il numero non lo legge l'estrattore ma `SrdWords.statedCost` dal lato
        # Kotlin, che deve saperlo fare comunque per «Costo:» contro «Cost:».
        # Qui conta che la riga basti a riconoscere la spesa: senza, la richiesta
        # di un verbo vicino avrebbe lasciato tutta la metamagia senza risorsa.
        self.assertEqual(
            {"name": "punti stregoneria"},
            MODULE.resource_from(
                "Incantesimo rapido\nCosto: 2 punti stregoneria\nQuando lancia un incantesimo…",
                self.ITALIAN,
            ),
        )
        self.assertEqual(
            {"name": "Sorcery Points"},
            MODULE.resource_from(
                "Quickened Spell\nCost: 2 Sorcery Points\nWhen you cast a spell…",
                self.ENGLISH,
            ),
        )

    def test_the_gerund_counts_as_a_spend(self) -> None:
        # Ali di drago: «ignorare questo limite spendendo 3 punti stregoneria».
        self.assertEqual(
            {"name": "punti stregoneria", "cost": 3, "recovery": ["riposo lungo"]},
            MODULE.resource_from(
                "Dopo aver usato questo privilegio non può riutilizzarlo prima di aver "
                "completato un riposo lungo; può ignorare questo limite spendendo 3 punti "
                "stregoneria.",
                self.ITALIAN,
            ),
        )

    def test_what_costs_nothing_declares_nothing(self) -> None:
        self.assertIsNone(
            MODULE.resource_from(
                "Il druido può lanciare questo incantesimo senza spendere uno slot incantesimo.",
                self.ITALIAN,
            )
        )
        self.assertIsNone(
            MODULE.resource_from("You can cast it without expending a spell slot.", self.ENGLISH)
        )

    def test_a_record_declares_at_most_the_resource_it_names_first(self) -> None:
        # Arcidruido converte utilizzi di Forma selvatica in slot incantesimo:
        # non spende ne' l'una ne' gli altri. Scartata la prima, si prendeva i
        # secondi — una risorsa sbagliata al posto di nessuna.
        self.assertIsNone(
            MODULE.resource_from(
                "La vitalità della natura fiorisce costantemente all'interno del druido, "
                "concedendogli i seguenti benefici.\n"
                "Forma selvatica sempreverde. Ogni volta che il druido tira per "
                "l'iniziativa e non ha utilizzi disponibili di Forma Selvatica, ne "
                "recupera uno.\n"
                "Mago della natura. Il druido può convertire gli utilizzi di Forma "
                "selvatica in slot incantesimo (nessuna azione richiesta).",
                self.ITALIAN,
            )
        )


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
