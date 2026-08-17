#!/usr/bin/env python3
"""Extract either SRD 5.2.1 spell edition into deterministic JSON.

The spell chapter uses two independent text columns.  Poppler's ordinary text
output can interleave them, so this extractor consumes ``pdftotext
-bbox-layout`` XHTML and explicitly orders every text block by page, column,
and vertical position.

By default the script reads the repository copy selected by ``--language`` and
writes ``tmp/pdfs/srd_spells_it.json`` or ``tmp/pdfs/srd_spells_en.json``:

    python3 tools/srd/extract_srd_spells.py

Pass ``--output -`` to emit JSON on stdout instead.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


@dataclass(frozen=True)
class LanguageProfile:
    """Everything about the spell chapter that changes with the SRD edition.

    The two SRDs are the same book in two languages, so the layout logic — two
    columns, bbox ordering, discretionary hyphens — is shared.  What differs is
    where the chapter sits, how a spell announces its level and school, and what
    the four metadata labels are called.  Keeping those in one object means the
    extractor itself never has to know which language it is reading.
    """

    tag: str
    pdf_name: str
    first_page: int
    last_page: int
    expected_count: int
    expected_first: str
    expected_last: str
    schools: tuple[str, ...]
    known_classes: frozenset[str]
    spell_type_re: re.Pattern[str]
    metadata_re: re.Pattern[str]
    casting_time_label: str
    duration_label: str
    # Nell'italiano le sigle di caratteristica sono spezzate in due run di testo
    # dal PDF ("F" + "or"); in inglese non succede, quindi la mappa e' vuota.
    split_abbreviations: tuple[tuple[str, str], ...] = ()
    # Nei titoli in maiuscoletto la lettera iniziale di ogni parola e' un run di
    # testo a se': "A cid S plash". Ricucirla vale solo per i titoli — nel corpo
    # una maiuscola isolata seguita da minuscola e' quasi sempre legittima.
    repair_split_titles: bool = False


def _italian_profile() -> LanguageProfile:
    schools = (
        "Abiurazione",
        "Ammaliamento",
        "Divinazione",
        "Evocazione",
        "Illusione",
        "Invocazione",
        "Necromanzia",
        "Trasmutazione",
    )
    pattern = "|".join(schools)
    return LanguageProfile(
        tag="it",
        pdf_name="IT_SRD_CC_v5.2.1.pdf",
        first_page=121,
        last_page=201,
        expected_count=339,
        expected_first="Aculeo mentale",
        expected_last="Zona di verità",
        schools=schools,
        known_classes=frozenset(
            {
                "bardo",
                "chierico",
                "druido",
                "mago",
                "paladino",
                "ranger",
                "stregone",
                "warlock",
            }
        ),
        spell_type_re=re.compile(
            rf"^(?:"
            rf"Trucchetto di (?P<cantrip_school>{pattern})"
            rf"|"
            rf"(?P<level_school>{pattern}) di "
            rf"(?P<level>[1-9])º livello"
            rf")\s+\((?P<classes>[^)]+)\)$"
        ),
        metadata_re=re.compile(
            r"^Tempo di lancio:\s*(?P<casting_time>.*?)\s+"
            r"Gittata:\s*(?P<range>.*?)\s+"
            r"Component(?:e|i):\s*(?P<components>.*?)\s+"
            r"Durata:\s*(?P<duration>.+)$",
            re.DOTALL,
        ),
        casting_time_label="Tempo di lancio:",
        duration_label="Durata:",
        split_abbreviations=(
            ("F or", "For"),
            ("D es", "Des"),
            ("C os", "Cos"),
            ("I nt", "Int"),
            ("S ag", "Sag"),
            ("C ar", "Car"),
        ),
    )


def _english_profile() -> LanguageProfile:
    schools = (
        "Abjuration",
        "Conjuration",
        "Divination",
        "Enchantment",
        "Evocation",
        "Illusion",
        "Necromancy",
        "Transmutation",
    )
    pattern = "|".join(schools)
    return LanguageProfile(
        tag="en",
        pdf_name="SRD_CC_v5.2.1.pdf",
        first_page=107,
        last_page=175,
        expected_count=339,
        expected_first="Acid Arrow",
        expected_last="Zone of Truth",
        schools=schools,
        known_classes=frozenset(
            {
                "bard",
                "cleric",
                "druid",
                "paladin",
                "ranger",
                "sorcerer",
                "warlock",
                "wizard",
            }
        ),
        # L'inglese mette la scuola *prima* della parola: "Evocation Cantrip
        # (Sorcerer, Wizard)", mentre i livelli la mettono dopo il numero,
        # "Level 3 Illusion (Bard, Sorcerer, Warlock, Wizard)". Invertire i due
        # ordini costa ventisette trucchetti, che e' esattamente quanti sono.
        spell_type_re=re.compile(
            rf"^(?:"
            rf"(?P<cantrip_school>{pattern}) Cantrip"
            rf"|"
            rf"Level (?P<level>[1-9]) (?P<level_school>{pattern})"
            rf")\s+\((?P<classes>[^)]+)\)$"
        ),
        metadata_re=re.compile(
            r"^Casting Time:\s*(?P<casting_time>.*?)\s+"
            r"Range:\s*(?P<range>.*?)\s+"
            r"Components?:\s*(?P<components>.*?)\s+"
            r"Duration:\s*(?P<duration>.+)$",
            re.DOTALL,
        ),
        casting_time_label="Casting Time:",
        duration_label="Duration:",
        repair_split_titles=True,
    )


PROFILES = {"it": _italian_profile(), "en": _english_profile()}


@dataclass(frozen=True)
class TextBlock:
    page: int
    column: int
    y_min: float
    x_min: float
    source_order: int
    text: str


@dataclass(frozen=True)
class SpellMarker:
    title_index: int
    type_index: int
    school: str
    level: int
    classes: tuple[str, ...]


class ExtractionError(RuntimeError):
    """Raised when the source layout does not match the expected SRD format."""


def _repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _default_pdf(profile: LanguageProfile) -> Path:
    """Il PDF accanto al repository, o quello nella cartella temporanea."""
    root = _repository_root()
    beside = root / profile.pdf_name
    return beside if beside.is_file() else root / "tmp/pdfs" / profile.pdf_name


def _default_output(profile: LanguageProfile) -> Path:
    return _repository_root() / f"tmp/pdfs/srd_spells_{profile.tag}.json"


def _normalize_text(value: str, profile: LanguageProfile | None = None) -> str:
    """Normalize PDF text without losing meaningful punctuation."""

    value = unicodedata.normalize("NFC", value)
    value = value.replace("\u00ad", "").replace("\u00a0", " ")
    value = re.sub(r"[ \t]+", " ", value).strip()

    # In the compact ability tables, each italic abbreviation can be split into
    # two PDF text runs (for example ``F`` + ``or``).  Recombine only the fixed
    # SRD abbreviations the profile declares.
    for split_label, label in (profile.split_abbreviations if profile else ()):
        value = value.replace(split_label, label)
    return value


def _join_wrapped_lines(lines: Sequence[str], profile: LanguageProfile) -> str:
    """Join the visual lines of one PDF block and undo discretionary wraps."""

    result = ""
    for raw_line in lines:
        line = _normalize_text(raw_line, profile)
        if not line:
            continue
        if not result:
            result = line
            continue

        # InDesign represents most discretionary word breaks as a trailing
        # ASCII hyphen.  A lower-case continuation is a reliable discriminator
        # in this document; real compound hyphens remain inside a line.
        if result.endswith("-") and re.match(r"^[a-zà-öø-ÿ]", line):
            result = result[:-1] + line
        else:
            result += " " + line
    return _normalize_text(result, profile)


def _run_pdftotext(pdf_path: Path, first_page: int, last_page: int) -> bytes:
    executable = shutil.which("pdftotext")
    if executable is None:
        raise ExtractionError(
            "pdftotext non trovato: installare Poppler per eseguire l'estrazione"
        )
    if not pdf_path.is_file():
        raise ExtractionError(f"PDF SRD non trovato: {pdf_path}")

    command = [
        executable,
        "-f",
        str(first_page),
        "-l",
        str(last_page),
        "-enc",
        "UTF-8",
        "-bbox-layout",
        str(pdf_path),
        "-",
    ]
    completed = subprocess.run(command, capture_output=True, check=False)
    if completed.returncode != 0:
        stderr = completed.stderr.decode("utf-8", errors="replace").strip()
        raise ExtractionError(
            f"pdftotext è terminato con codice {completed.returncode}: {stderr}"
        )
    if not completed.stdout:
        raise ExtractionError("pdftotext non ha prodotto XHTML")
    return completed.stdout


def _xml_float(element: ET.Element, attribute: str) -> float:
    try:
        return float(element.attrib[attribute])
    except (KeyError, ValueError) as exc:
        raise ExtractionError(
            f"Attributo bbox non valido o mancante: {attribute}"
        ) from exc


def _extract_blocks(
    xhtml: bytes, first_page: int, last_page: int, profile: LanguageProfile
) -> list[TextBlock]:
    try:
        root = ET.fromstring(xhtml)
    except ET.ParseError as exc:
        raise ExtractionError(f"XHTML bbox non valido: {exc}") from exc

    pages = list(root.findall(".//{*}page"))
    expected_pages = last_page - first_page + 1
    if len(pages) != expected_pages:
        raise ExtractionError(
            f"Attese {expected_pages} pagine bbox, trovate {len(pages)}"
        )

    blocks: list[TextBlock] = []
    source_order = 0
    for page_offset, page_element in enumerate(pages):
        page_number = first_page + page_offset
        page_width = _xml_float(page_element, "width")
        page_height = _xml_float(page_element, "height")
        column_boundary = page_width / 2

        for block_element in page_element.findall(".//{*}block"):
            source_order += 1
            y_min = _xml_float(block_element, "yMin")
            x_min = _xml_float(block_element, "xMin")

            # Page numbers and the repeated SRD title live in the bottom
            # margin.  They are not spell content.
            if y_min >= page_height - 45:
                continue

            visual_lines: list[str] = []
            for line_element in block_element.findall("./{*}line"):
                words = [
                    word.text or ""
                    for word in line_element.findall("./{*}word")
                    if word.text
                ]
                if words:
                    visual_lines.append(" ".join(words))
            text = _join_wrapped_lines(visual_lines, profile)
            if not text:
                continue

            blocks.append(
                TextBlock(
                    page=page_number,
                    column=0 if x_min < column_boundary else 1,
                    y_min=y_min,
                    x_min=x_min,
                    source_order=source_order,
                    text=text,
                )
            )

    # Reading order is the whole left column followed by the whole right
    # column on each page.  x/source_order make overlapping stat-block
    # fragments stable without depending on ElementTree iteration details.
    blocks.sort(
        key=lambda block: (
            block.page,
            block.column,
            round(block.y_min, 3),
            round(block.x_min, 3),
            block.source_order,
        )
    )
    return blocks


def _spell_title(text: str, profile: LanguageProfile) -> str:
    """Il nome dell'incantesimo, ricucito se il PDF ne ha spezzato le iniziali."""
    if not profile.repair_split_titles:
        return text
    return re.sub(r"\b([A-Z]) (?=[a-z])", r"\1", text)


def _find_spell_markers(
    blocks: Sequence[TextBlock], profile: LanguageProfile
) -> list[SpellMarker]:
    markers: list[SpellMarker] = []
    for type_index, block in enumerate(blocks):
        match = profile.spell_type_re.fullmatch(block.text)
        if match is None:
            continue
        if type_index == 0:
            raise ExtractionError("Metadati di incantesimo senza titolo precedente")

        title_index = type_index - 1
        title = _spell_title(blocks[title_index].text, profile)
        if ":" in title or len(title) > 100:
            raise ExtractionError(
                f"Titolo sospetto prima di '{block.text}': '{title}'"
            )

        school = match.group("cantrip_school") or match.group("level_school")
        level = 0 if match.group("cantrip_school") else int(match.group("level"))
        classes = tuple(
            item.strip().lower() for item in match.group("classes").split(",")
        )
        markers.append(
            SpellMarker(
                title_index=title_index,
                type_index=type_index,
                school=school,
                level=level,
                classes=classes,
            )
        )
    return markers


def _parse_metadata(
    blocks: Sequence[TextBlock],
    start: int,
    end: int,
    spell_name: str,
    profile: LanguageProfile,
) -> tuple[dict[str, str], int]:
    metadata_start: int | None = None
    for index in range(start, end):
        if blocks[index].text.startswith(profile.casting_time_label):
            metadata_start = index
            break
    if metadata_start is None:
        raise ExtractionError(
            f"'{spell_name}': {profile.casting_time_label} mancante"
        )

    metadata_parts: list[str] = []
    metadata_end: int | None = None
    for index in range(metadata_start, min(end, metadata_start + 5)):
        metadata_parts.append(blocks[index].text)
        if profile.duration_label in blocks[index].text:
            metadata_end = index + 1
            break
    if metadata_end is None:
        raise ExtractionError(f"'{spell_name}': {profile.duration_label} mancante")

    metadata_text = " ".join(metadata_parts)
    match = profile.metadata_re.fullmatch(metadata_text)
    if match is None:
        raise ExtractionError(
            f"'{spell_name}': blocco dei metadati non riconosciuto: "
            f"{metadata_text!r}"
        )
    return (
        {
            key: _normalize_text(value, profile)
            for key, value in match.groupdict().items()
        },
        metadata_end,
    )


def _join_description_blocks(blocks: Sequence[TextBlock]) -> str:
    paragraphs: list[str] = []
    for block in blocks:
        text = block.text.strip()
        if not text:
            continue
        if not paragraphs:
            paragraphs.append(text)
            continue

        previous = paragraphs[-1]
        if previous.endswith("-") and re.match(r"^[a-zà-öø-ÿ]", text):
            paragraphs[-1] = previous[:-1] + text
        elif re.match(r"^[a-zà-öø-ÿ]", text) and not re.search(
            r"[.!?…][\"”')\]]?$", previous
        ):
            # Some flows are split solely because a sentence crosses a page
            # or column boundary.  Do not turn those visual breaks into false
            # paragraph boundaries in the extracted prose.
            paragraphs[-1] = previous + " " + text
        else:
            paragraphs.append(text)
    return "\n\n".join(paragraphs).strip()


def _extract_spells(
    blocks: Sequence[TextBlock], profile: LanguageProfile
) -> list[dict[str, object]]:
    markers = _find_spell_markers(blocks, profile)
    if not markers:
        raise ExtractionError("Nessuna descrizione di incantesimo individuata")

    spells: list[dict[str, object]] = []
    for marker_index, marker in enumerate(markers):
        end = (
            markers[marker_index + 1].title_index
            if marker_index + 1 < len(markers)
            else len(blocks)
        )
        name = _spell_title(blocks[marker.title_index].text, profile)
        metadata, description_start = _parse_metadata(
            blocks, marker.type_index + 1, end, name, profile
        )
        description_blocks = blocks[description_start:end]
        description = _join_description_blocks(description_blocks)
        if not description:
            raise ExtractionError(f"'{name}': descrizione vuota")

        spells.append(
            {
                "name": name,
                "school": marker.school,
                "level": marker.level,
                "is_cantrip": marker.level == 0,
                "classes": list(marker.classes),
                "casting_time": metadata["casting_time"],
                "range": metadata["range"],
                "components": metadata["components"],
                "duration": metadata["duration"],
                "description": description,
                "source_pages": [
                    blocks[marker.title_index].page,
                    description_blocks[-1].page,
                ],
            }
        )
    return spells


def _validate_spells(
    spells: Sequence[dict[str, object]], profile: LanguageProfile
) -> list[str]:
    anomalies: list[str] = []
    if len(spells) != profile.expected_count:
        anomalies.append(
            f"conteggio: attesi {profile.expected_count}, trovati {len(spells)}"
        )
    if spells and spells[0]["name"] != profile.expected_first:
        anomalies.append(
            f"primo incantesimo: atteso '{profile.expected_first}', "
            f"trovato '{spells[0]['name']}'"
        )
    if spells and spells[-1]["name"] != profile.expected_last:
        anomalies.append(
            f"ultimo incantesimo: atteso '{profile.expected_last}', "
            f"trovato '{spells[-1]['name']}'"
        )

    seen_names: set[str] = set()
    for spell in spells:
        name = str(spell["name"])
        if name in seen_names:
            anomalies.append(f"nome duplicato: '{name}'")
        seen_names.add(name)

        classes = set(spell["classes"])
        unknown_classes = sorted(classes - profile.known_classes)
        if unknown_classes:
            anomalies.append(
                f"'{name}': classi non riconosciute: {', '.join(unknown_classes)}"
            )
        if not classes:
            anomalies.append(f"'{name}': nessuna classe")

        pages = spell["source_pages"]
        if not (
            isinstance(pages, list)
            and len(pages) == 2
            and profile.first_page <= pages[0] <= pages[1] <= profile.last_page
        ):
            anomalies.append(f"'{name}': pagine sorgente non valide: {pages!r}")
    return anomalies


def _build_document(
    pdf_path: Path,
    profile: LanguageProfile,
) -> tuple[dict[str, object], list[str]]:
    first_page = profile.first_page
    last_page = profile.last_page
    xhtml = _run_pdftotext(pdf_path, first_page, last_page)
    blocks = _extract_blocks(xhtml, first_page, last_page, profile)
    spells = _extract_spells(blocks, profile)
    anomalies = _validate_spells(spells, profile)
    document: dict[str, object] = {
        "schema_version": 1,
        "source": {
            "document": pdf_path.name,
            "srd_version": "5.2.1",
            "language": profile.tag,
            "pdf_pages": {"first": first_page, "last": last_page},
        },
        "spell_count": len(spells),
        "spells": spells,
    }
    return document, anomalies


def _write_json(document: dict[str, object], output: str) -> None:
    serialized = json.dumps(
        document,
        ensure_ascii=False,
        indent=2,
        sort_keys=False,
    ) + "\n"
    if output == "-":
        sys.stdout.write(serialized)
        return

    output_path = Path(output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="\n") as output_file:
        output_file.write(serialized)


def _parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Estrae gli incantesimi dal capitolo corrispondente dell'SRD 5.2.1, "
            "italiano o inglese, usando l'ordine bbox delle due colonne."
        )
    )
    parser.add_argument(
        "--language",
        choices=sorted(PROFILES),
        default="it",
        help="lingua dell'SRD da leggere (default: %(default)s)",
    )
    parser.add_argument(
        "pdf",
        nargs="?",
        type=Path,
        default=None,
        help="PDF SRD sorgente (default: quello della lingua scelta)",
    )
    parser.add_argument(
        "-o",
        "--output",
        default=None,
        help="JSON di destinazione, oppure '-' per stdout",
    )
    parser.add_argument(
        "--allow-anomalies",
        action="store_true",
        help="scrive il JSON e termina con successo anche se la validazione segnala anomalie",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = _parse_args(argv)
    profile = PROFILES[args.language]
    pdf_path = args.pdf or _default_pdf(profile)
    output = args.output or str(_default_output(profile))
    try:
        document, anomalies = _build_document(pdf_path, profile)
        if anomalies and not args.allow_anomalies:
            for anomaly in anomalies:
                print(f"anomalia: {anomaly}", file=sys.stderr)
            print(
                "estrazione interrotta; usare --allow-anomalies per scrivere comunque",
                file=sys.stderr,
            )
            return 2
        _write_json(document, output)
    except (ExtractionError, OSError) as exc:
        print(f"errore: {exc}", file=sys.stderr)
        return 1

    print(
        f"estratti {document['spell_count']} incantesimi; "
        f"anomalie: {len(anomalies)}; output: {output}",
        file=sys.stderr,
    )
    for anomaly in anomalies:
        print(f"anomalia: {anomaly}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
