#!/usr/bin/env python3
"""Extract Wild Shape-eligible beasts from either SRD 5.2.1 language PDF.

The Animals appendix is a single-column sequence of complete stat blocks.  The
extractor keeps every creature whose type is exactly Beast and whose Challenge
Rating is at most 1, which is the complete range usable by the SRD Druid's Wild
Shape table.  Celestials, monstrosities and swarms printed in the same appendix
are deliberately excluded.

Only Python's standard library and Poppler's ``pdftotext`` executable are
required. The generated JSON retains the selected edition's stat block so a
form is useful in the character sheet and compendium, not merely a picker name.
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
@dataclass(frozen=True)
class LanguageProfile:
    """Cio' che del capitolo dei mostri cambia con la lingua dell'SRD.

    Le due edizioni ordinano diversamente taglia e tipo — «Bestia Media» contro
    «Medium Beast» — e chiamano con parole diverse il Grado di Sfida, la velocita'
    e il volo. Il resto della lettura, righe e schede, e' identico.
    """

    tag: str
    pdf_name: str
    first_page: int
    last_page: int
    creature_type_re: "re.Pattern[str]"
    challenge_rating_re: "re.Pattern[str]"
    beast_line_re: "re.Pattern[str]"
    speed_prefix: str
    fly_word: str


ITALIAN = LanguageProfile(
    tag="it",
    pdf_name="IT_SRD_CC_v5.2.1.pdf",
    first_page=385,
    last_page=405,
    creature_type_re=re.compile(
        r"^(?:Aberrazione|Bestia|Celestiale|Costrutto|Drago|Elementale|Folletto|Gigante|"
        r"Immondo|Melma|Mostruosità|Non morto|Pianta|Umanoide)\s+"
        r"(?:Minuscola|Piccola|Media|Grande|Enorme|Mastodontica)(?:\s+\([^)]*\))?,"
        r"|^Sciame\s+(?:Minuscolo|Piccolo|Medio|Grande|Enorme|Mastodontico)\s+di\s+"
    ),
    challenge_rating_re=re.compile(r"\bGS\s+([0-9]+(?:/[0-9]+)?)\s+\("),
    beast_line_re=re.compile(r"^Bestia\s"),
    speed_prefix="Velocità ",
    fly_word="volo",
)

ENGLISH = LanguageProfile(
    tag="en",
    pdf_name="SRD_CC_v5.2.1.pdf",
    # Solo l'appendice "Animals": il resto del bestiario contiene creature senza
    # Grado di Sfida (l'Avatar of Death), che non sono forme di Forma Selvatica.
    first_page=344,
    last_page=364,
    # L'inglese mette la taglia prima del tipo: "Medium Beast, Unaligned".
    creature_type_re=re.compile(
        r"^(?:Tiny|Small|Medium|Large|Huge|Gargantuan)\s+"
        r"(?:Aberration|Beast|Celestial|Construct|Dragon|Elemental|Fey|Fiend|Giant|"
        r"Monstrosity|Ooze|Plant|Undead|Humanoid)(?:\s+\([^)]*\))?,"
        # Gli sciami: «Large Swarm of Tiny Beasts, Unaligned». La taglia viene
        # *prima* di «Swarm», e il modello che la voleva dopo non li riconosceva
        # come confine: sei schede di sciame finivano dentro quella del Ragno,
        # che si ritrovava sette attacchi, compreso quello di uno sciame GS 2.
        r"|^(?:Tiny|Small|Medium|Large|Huge|Gargantuan)\s+Swarm of\s+"
        r"(?:Tiny|Small|Medium|Large|Huge|Gargantuan)\s+\w+,"
    ),
    challenge_rating_re=re.compile(r"\bCR\s+([0-9]+(?:/[0-9]+)?)\s+\("),
    beast_line_re=re.compile(r"^(?:Tiny|Small|Medium|Large|Huge|Gargantuan)\s+Beast\b"),
    speed_prefix="Speed ",
    fly_word="fly",
)

PROFILES = {"it": ITALIAN, "en": ENGLISH}

ALLOWED_CHALLENGE_RATINGS = {"0", "1/8", "1/4", "1/2", "1"}

PAGE_HEADER = re.compile(
    r"^(?:System Reference Document 5\.2\.1\s+(\d+)|(\d+)\s+System Reference Document 5\.2\.1)$"
)


@dataclass(frozen=True)
class SourceLine:
    page: int
    text: str


def content_slug(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.lower())
    ascii_text = "".join(character for character in decomposed if not unicodedata.combining(character))
    return re.sub(r"[^a-z0-9]+", "-", ascii_text).strip("-")


def source_lines(raw_text: str, profile: LanguageProfile) -> list[SourceLine]:
    page = profile.first_page
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


def parse_records(
    raw_text: str, profile: LanguageProfile
) -> list[dict[str, object]]:
    lines = source_lines(raw_text, profile)
    type_indexes = [
        index
        for index, line in enumerate(lines)
        if profile.creature_type_re.match(line.text)
    ]
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
            (
                profile.challenge_rating_re.search(line)
                for line in block_lines
                if profile.challenge_rating_re.search(line)
            ),
            None,
        )
        if challenge_match is None:
            raise ValueError(
                f"Grado di Sfida non trovato per {name} (pagina {type_line.page})"
            )
        challenge_rating = challenge_match.group(1)
        if (
            not profile.beast_line_re.match(type_line.text)
            or challenge_rating not in ALLOWED_CHALLENGE_RATINGS
        ):
            continue

        speed = next(
            (
                line.removeprefix(profile.speed_prefix)
                for line in block_lines
                if line.startswith(profile.speed_prefix)
            ),
            "",
        )
        if not speed:
            raise ValueError(f"Velocità non trovata per {name} (pagina {type_line.page})")
        stat_block = join_wrapped_lines(block_lines)
        records.append(
            {
                "id": f"srd521-{profile.tag}:beast:{content_slug(name)}",
                "name": name,
                "challenge_rating": challenge_rating,
                "has_fly_speed": profile.fly_word in speed.lower(),
                "speed": speed,
                "page": type_line.page,
                "stat_block": stat_block,
            }
        )

    ids = [record["id"] for record in records]
    if len(ids) != len(set(ids)):
        raise ValueError("Gli ID delle forme bestiali estratte non sono univoci")
    return records


def extract_pdf_text(pdf: Path, profile: LanguageProfile) -> str:
    if not pdf.is_file():
        raise FileNotFoundError(f"PDF SRD non trovato: {pdf}")
    executable = shutil.which("pdftotext")
    if not executable:
        raise RuntimeError("pdftotext (Poppler) non è disponibile nel PATH")
    command = [
        executable,
        "-f",
        str(profile.first_page),
        "-l",
        str(profile.last_page),
        "-raw",
        str(pdf),
        "-",
    ]
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def build_document(
    records: list[dict[str, object]], profile: LanguageProfile
) -> dict[str, object]:
    return {
        "schema_version": 1,
        "source": {
            "title": "System Reference Document 5.2.1",
            "language": profile.tag,
            "pages": f"{profile.first_page}-{profile.last_page}",
        },
        "counts": {"records": len(records)},
        "records": records,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--language", choices=sorted(PROFILES), default="it")
    parser.add_argument("--pdf", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    arguments = parser.parse_args()

    profile = PROFILES[arguments.language]
    pdf = arguments.pdf or ROOT / profile.pdf_name
    output = arguments.output or (
        ROOT
        / f"content/srd-5.2.1-it/src/main/resources/srd/5.2.1-{profile.tag}/beasts.json"
    )

    records = parse_records(extract_pdf_text(pdf, profile), profile)
    if len(records) != 64:
        raise RuntimeError(
            f"Attese 64 forme bestiali SRD con Grado di Sfida massimo 1, trovate {len(records)}"
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(build_document(records, profile), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Scritte {len(records)} forme bestiali in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
