#!/usr/bin/env python3
"""Extract Wild Shape-eligible beasts from the Italian SRD 5.2.1 PDF.

The Animals appendix is a single-column sequence of complete stat blocks.  The
extractor keeps every creature whose type is exactly Beast and whose Challenge
Rating is at most 1, which is the complete range usable by the SRD Druid's Wild
Shape table.  Celestials, monstrosities and swarms printed in the same appendix
are deliberately excluded.

Only Python's standard library and Poppler's ``pdftotext`` executable are
required.  The generated JSON retains the Italian stat block so a selected form
is useful in the character sheet and compendium, not merely a name in a picker.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PDF = ROOT / "IT_SRD_CC_v5.2.1.pdf"
DEFAULT_OUTPUT = (
    ROOT
    / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-it/beasts.json"
)
FIRST_PAGE = 385
LAST_PAGE = 405
ALLOWED_CHALLENGE_RATINGS = {"0", "1/8", "1/4", "1/2", "1"}

PAGE_HEADER = re.compile(
    r"^(?:System Reference Document 5\.2\.1\s+(\d+)|(\d+)\s+System Reference Document 5\.2\.1)$"
)
CREATURE_TYPE = re.compile(
    r"^(?:Aberrazione|Bestia|Celestiale|Costrutto|Drago|Elementale|Folletto|Gigante|"
    r"Immondo|Melma|Mostruosità|Non morto|Pianta|Umanoide)\s+"
    r"(?:Minuscola|Piccola|Media|Grande|Enorme|Mastodontica)(?:\s+\([^)]*\))?,"
    r"|^Sciame\s+(?:Minuscolo|Piccolo|Medio|Grande|Enorme|Mastodontico)\s+di\s+"
)
CHALLENGE_RATING = re.compile(r"\bGS\s+([0-9]+(?:/[0-9]+)?)\s+\(")


@dataclass(frozen=True)
class SourceLine:
    page: int
    text: str


def content_slug(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.lower())
    ascii_text = "".join(character for character in decomposed if not unicodedata.combining(character))
    return re.sub(r"[^a-z0-9]+", "-", ascii_text).strip("-")


def source_lines(raw_text: str) -> list[SourceLine]:
    page = FIRST_PAGE
    result: list[SourceLine] = []
    for raw_line in raw_text.splitlines():
        text = (
            raw_line.replace("\f", "")
            .replace("\u00ad", "")
            .replace("\u00a0", " ")
            .strip()
        )
        if not text:
            continue
        header = PAGE_HEADER.fullmatch(text)
        if header:
            page = int(header.group(1) or header.group(2))
            continue
        result.append(SourceLine(page, re.sub(r"[ \t]+", " ", text)))
    return result


def join_wrapped_lines(lines: Sequence[str]) -> str:
    """Keep stat rows readable while repairing words hyphenated by page layout."""
    repaired: list[str] = []
    for line in lines:
        if repaired and repaired[-1].endswith("-") and line[:1].islower():
            repaired[-1] = repaired[-1][:-1] + line
        else:
            repaired.append(line)
    return "\n".join(repaired).strip()


def parse_records(raw_text: str) -> list[dict[str, object]]:
    lines = source_lines(raw_text)
    type_indexes = [index for index, line in enumerate(lines) if CREATURE_TYPE.match(line.text)]
    records: list[dict[str, object]] = []

    for position, type_index in enumerate(type_indexes):
        if type_index == 0:
            raise ValueError("Scheda animale senza nome prima del tipo di creatura")
        type_line = lines[type_index]
        next_type_index = type_indexes[position + 1] if position + 1 < len(type_indexes) else len(lines)
        # The line immediately before the next type is the next creature's name.
        block_end = next_type_index - 1 if next_type_index < len(lines) else next_type_index
        block_lines = [line.text for line in lines[type_index:block_end]]
        name = lines[type_index - 1].text

        challenge_match = next(
            (CHALLENGE_RATING.search(line) for line in block_lines if CHALLENGE_RATING.search(line)),
            None,
        )
        if challenge_match is None:
            raise ValueError(f"GS non trovato per {name} (pagina {type_line.page})")
        challenge_rating = challenge_match.group(1)
        if not type_line.text.startswith("Bestia ") or challenge_rating not in ALLOWED_CHALLENGE_RATINGS:
            continue

        speed = next((line.removeprefix("Velocità ") for line in block_lines if line.startswith("Velocità ")), "")
        if not speed:
            raise ValueError(f"Velocità non trovata per {name} (pagina {type_line.page})")
        stat_block = join_wrapped_lines(block_lines)
        records.append(
            {
                "id": f"srd521-it:beast:{content_slug(name)}",
                "name": name,
                "challenge_rating": challenge_rating,
                "has_fly_speed": "volo" in speed.lower(),
                "speed": speed,
                "page": type_line.page,
                "stat_block": stat_block,
            }
        )

    ids = [record["id"] for record in records]
    if len(ids) != len(set(ids)):
        raise ValueError("Gli ID delle forme bestiali estratte non sono univoci")
    return records


def extract_pdf_text(pdf: Path) -> str:
    if not pdf.is_file():
        raise FileNotFoundError(f"PDF SRD non trovato: {pdf}")
    executable = shutil.which("pdftotext")
    if not executable:
        raise RuntimeError("pdftotext (Poppler) non è disponibile nel PATH")
    command = [
        executable,
        "-f",
        str(FIRST_PAGE),
        "-l",
        str(LAST_PAGE),
        "-raw",
        str(pdf),
        "-",
    ]
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def build_document(records: list[dict[str, object]]) -> dict[str, object]:
    return {
        "schema_version": 1,
        "source": {
            "title": "System Reference Document 5.2.1 (italiano)",
            "pages": f"{FIRST_PAGE}-{LAST_PAGE}",
        },
        "counts": {"records": len(records)},
        "records": records,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    arguments = parser.parse_args()

    records = parse_records(extract_pdf_text(arguments.pdf))
    if len(records) != 64:
        raise RuntimeError(f"Attese 64 forme bestiali SRD con GS massimo 1, trovate {len(records)}")
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(
        json.dumps(build_document(records), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Scritte {len(records)} forme bestiali in {arguments.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
