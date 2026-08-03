#!/usr/bin/env python3
"""Extract the Italian SRD 5.2.1 class features from the official PDF.

The class chapter mixes full-width tables and two independent text columns.
Reading the page as one plain-text stream interleaves those columns, so this
extractor uses Poppler's XML output, reconstructs each column independently,
and then follows the visual reading order (left column, then right column).

Only Python's standard library and Poppler's ``pdftohtml`` executable are
required. The generated JSON intentionally keeps the original Italian prose.
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
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Sequence


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PDF = ROOT / "tmp/pdfs/IT_SRD_CC_v5.2.1.pdf"
DEFAULT_OUTPUT = (
    ROOT
    / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-it/class-features.json"
)

RED = "#88191f"
BLACK = "#231f20"
COLUMN_SPLIT = 445
FOOTER_TOP = 1080

# Full-width class progression tables interrupt prose that begins before the
# table and resumes below it or on the following page. Their text is useful to
# the progression extractor, but it is not part of an individual feature's
# description, so exclude only these visually verified table rectangles.
PROGRESSION_TABLE_RANGES: dict[int, tuple[int, int]] = {
    32: (560, 1080),
    36: (50, 725),
    42: (50, 745),
    47: (50, 745),
    53: (580, 1080),
    56: (50, 575),
    59: (460, 1080),
    66: (545, 1080),
    70: (485, 1080),
    75: (510, 1080),
    80: (50, 900),
    86: (50, 750),
}


@dataclass(frozen=True)
class ClassSpec:
    class_id: str
    name: str
    subclass: str
    parts: tuple[tuple[int, str], ...]


def page_parts(
    first: int,
    last: int,
    *,
    first_column: str | None = None,
    last_column: str | None = None,
) -> tuple[tuple[int, str], ...]:
    parts: list[tuple[int, str]] = []
    for page in range(first, last + 1):
        columns = ("L", "R")
        if page == first and first_column:
            columns = (first_column,)
        if page == last and last_column:
            columns = tuple(column for column in columns if column == last_column)
        parts.extend((page, column) for column in columns)
    return tuple(parts)


CLASSES: tuple[ClassSpec, ...] = (
    ClassSpec("barbaro", "Barbaro", "Cammino del berserker", page_parts(32, 35, last_column="L")),
    ClassSpec("bardo", "Bardo", "Collegio della Sapienza", page_parts(35, 40, first_column="R")),
    ClassSpec("chierico", "Chierico", "Dominio della Vita", page_parts(41, 46, last_column="L")),
    ClassSpec("druido", "Druido", "Circolo della Terra", page_parts(46, 52, first_column="R")),
    ClassSpec("guerriero", "Guerriero", "Campione", page_parts(53, 55, last_column="L")),
    ClassSpec("ladro", "Ladro", "Furfante", page_parts(55, 58, first_column="R")),
    ClassSpec("mago", "Mago", "Invocatore", page_parts(59, 65)),
    ClassSpec("monaco", "Monaco", "Guerriero della Mano Aperta", page_parts(66, 69)),
    ClassSpec("paladino", "Paladino", "Giuramento di devozione", page_parts(70, 74)),
    ClassSpec("ranger", "Ranger", "Cacciatore", page_parts(75, 78)),
    ClassSpec("stregone", "Stregone", "Stregoneria draconica", page_parts(79, 85, last_column="L")),
    ClassSpec("warlock", "Warlock", "Patrono immondo", page_parts(85, 92, first_column="R")),
)


@dataclass(frozen=True)
class Fragment:
    text: str
    left: int
    width: int
    top: int
    color: str
    family: str
    size: int
    bold_prefix: str | None


@dataclass(frozen=True)
class Line:
    page: int
    column: str
    top: int
    left: int
    text: str
    color: str
    family: str
    size: int
    bold_prefix: str | None = None
    table_like: bool = False

    @property
    def is_red_heading(self) -> bool:
        return self.color == RED and "SemiBold" in self.family


@dataclass
class FeatureDraft:
    name: str
    class_spec: ClassSpec
    minimum_level: int
    page: int
    subclass: str | None
    kind: str
    lines: list[Line] = field(default_factory=list)
    prerequisite: str | None = None


def normalize_spaces(value: str) -> str:
    return re.sub(r"[ \t]+", " ", value.replace("\u00a0", " ").replace("\u00ad", "")).strip()


def bold_prefix(element: ET.Element, full_text: str) -> str | None:
    """Return an initial bold phrase, including bold nested inside italics."""
    for candidate in element.iter("b"):
        value = normalize_spaces("".join(candidate.itertext()))
        if value and normalize_spaces(full_text).startswith(value):
            return value
    return None


def parse_poppler_xml(pdf: Path) -> dict[tuple[int, str], list[Line]]:
    if not pdf.is_file():
        raise FileNotFoundError(f"PDF SRD non trovato: {pdf}")
    executable = shutil.which("pdftohtml")
    if not executable:
        raise RuntimeError("pdftohtml (Poppler) non è disponibile nel PATH")

    command = [
        executable,
        "-xml",
        "-hidden",
        "-i",
        "-f",
        "32",
        "-l",
        "92",
        "-stdout",
        str(pdf),
    ]
    xml_bytes = subprocess.run(command, check=True, capture_output=True).stdout
    root = ET.fromstring(xml_bytes)
    fonts = {element.attrib["id"]: element.attrib for element in root.iter("fontspec")}
    result: dict[tuple[int, str], list[Line]] = {}

    for page_element in root.findall("page"):
        page = int(page_element.attrib["number"])
        fragments_by_column: dict[str, list[Fragment]] = {"L": [], "R": []}
        for text_element in page_element.findall("text"):
            top = int(text_element.attrib["top"])
            left = int(text_element.attrib["left"])
            if top >= FOOTER_TOP:
                continue
            table_range = PROGRESSION_TABLE_RANGES.get(page)
            if table_range and table_range[0] <= top <= table_range[1]:
                continue
            raw_text = "".join(text_element.itertext()).replace("\u00ad", "")
            text = raw_text.strip()
            if not text:
                continue
            font = fonts[text_element.attrib["font"]]
            column = "L" if left < COLUMN_SPLIT else "R"
            fragments_by_column[column].append(
                Fragment(
                    text=text,
                    left=left,
                    width=int(text_element.attrib.get("width", "0")),
                    top=top,
                    color=font.get("color", BLACK).lower(),
                    family=font.get("family", ""),
                    size=int(font.get("size", "0")),
                    bold_prefix=bold_prefix(text_element, raw_text),
                )
            )

        for column, fragments in fragments_by_column.items():
            result[(page, column)] = group_fragments(page, column, fragments)
    return result


def group_fragments(page: int, column: str, fragments: Sequence[Fragment]) -> list[Line]:
    """Join XML fragments that share a visual baseline."""
    rows: list[list[Fragment]] = []
    for fragment in sorted(fragments, key=lambda item: (item.top, item.left)):
        if not rows or abs(rows[-1][0].top - fragment.top) > 3:
            rows.append([fragment])
        else:
            rows[-1].append(fragment)

    lines: list[Line] = []
    for row in rows:
        ordered = sorted(row, key=lambda item: item.left)
        pieces: list[str] = []
        table_like = False
        previous_right: int | None = None
        for fragment in ordered:
            if previous_right is not None:
                gap = fragment.left - previous_right
                if gap > 28:
                    pieces.append("   ")
                    table_like = True
                elif pieces and not pieces[-1].endswith((" ", "\n")):
                    pieces.append(" ")
            pieces.append(fragment.text)
            previous_right = fragment.left + fragment.width

        first = ordered[0]
        prefix = first.bold_prefix
        lines.append(
            Line(
                page=page,
                column=column,
                top=min(item.top for item in ordered),
                left=min(item.left for item in ordered),
                text="".join(pieces).strip(),
                color=first.color,
                family=first.family,
                size=max(item.size for item in ordered),
                bold_prefix=prefix,
                table_like=table_like,
            )
        )
    return lines


LEVEL_HEADING = re.compile(r"^Livello\s+(\d+):\s*(.+)$", re.IGNORECASE)
SUBCLASS_HEADING = re.compile(r"^Sottoclasse\s+(?:del|dello)\s+", re.IGNORECASE)


def merge_wrapped_level_headings(lines: Sequence[Line]) -> list[Line]:
    merged: list[Line] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if (
            LEVEL_HEADING.match(line.text)
            and index + 1 < len(lines)
            and lines[index + 1].is_red_heading
            and lines[index + 1].page == line.page
            and lines[index + 1].column == line.column
            and 0 < lines[index + 1].top - line.top <= 25
            and not LEVEL_HEADING.match(lines[index + 1].text)
        ):
            continuation = lines[index + 1]
            line = Line(
                page=line.page,
                column=line.column,
                top=line.top,
                left=line.left,
                text=f"{line.text} {continuation.text}",
                color=line.color,
                family=line.family,
                size=max(line.size, continuation.size),
                bold_prefix=None,
            )
            index += 1
        merged.append(line)
        index += 1
    return merged


def class_stream(spec: ClassSpec, page_lines: dict[tuple[int, str], list[Line]]) -> list[Line]:
    stream: list[Line] = []
    for part in spec.parts:
        stream.extend(page_lines.get(part, ()))
    return merge_wrapped_level_headings(stream)


def append_joined(output: list[str], text: str, separator: str) -> None:
    text = normalize_spaces(text)
    if not text:
        return
    if not output:
        output.append(text)
        return
    previous = output[-1]
    if previous.endswith("-") and text[:1].islower():
        output[-1] = previous[:-1] + text
    else:
        output.append(separator + text)


def description_from_lines(lines: Sequence[Line]) -> str:
    """Reflow body text without destroying bullets and simple tables."""
    output: list[str] = []
    previous: Line | None = None
    for line in lines:
        text = line.text.strip()
        if not text or text.startswith("System Reference Document"):
            continue
        base_left = 95 if line.column == "L" else 470
        starts_new_paragraph = (
            text.startswith(("•", "–", "—"))
            or line.table_like
            or (line.bold_prefix is not None and normalize_spaces(text).startswith(line.bold_prefix))
            or (line.left >= base_left + 8 and previous is not None and previous.page == line.page)
        )
        if previous and (line.page != previous.page or line.column != previous.column):
            # A column/page boundary commonly continues a split sentence.
            starts_new_paragraph = bool(line.left >= base_left + 8 or text.startswith(("•", "–", "—")))
        separator = "\n" if starts_new_paragraph else " "
        append_joined(output, text, separator)
        previous = line
    return "".join(output).strip()


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_value = normalized.encode("ascii", "ignore").decode("ascii").lower()
    ascii_value = ascii_value.replace("'", "")
    return re.sub(r"[^a-z0-9]+", "-", ascii_value).strip("-")


def activation_from(description: str) -> str | None:
    lowered = description.lower()
    # Le eccezioni descrivono ciò che l'azione concessa non può fare, non il
    # costo di attivazione del privilegio (Azione Impetuosa è il caso SRD).
    lowered = re.sub(
        r"(?:fatta eccezione per|tranne|eccetto)\s+l['’]azione di magia",
        "",
        lowered,
    )
    if re.search(r"\b(?:usa(?:re)?|usare la propria|come)\s+(?:la sua |una |un')?reazione\b", lowered):
        return "reazione"
    if "azione bonus" in lowered:
        return "azione bonus"
    if re.search(r"\bazione di magia\b", lowered):
        return "azione di Magia"
    if re.search(r"\bcome (?:un'|una )azione\b|\busare (?:un'|una )azione\b", lowered):
        return "azione"
    return None


RESOURCE_PATTERNS: tuple[tuple[str, str], ...] = (
    (r"punt[oi] stregoneria", "punti stregoneria"),
    (r"punt[oi] concentrazione", "punti concentrazione"),
    (r"ispirazione bardica", "Ispirazione bardica"),
    (r"forma selvatica", "Forma selvatica"),
    (r"incanalare divinità", "Incanalare divinità"),
    (r"slot incantesimo", "slot incantesimo"),
    (r"imposizione delle mani", "Imposizione delle mani"),
    (r"\bira\b", "Ira"),
)


def resource_from(description: str) -> dict[str, object] | None:
    lowered = description.lower()
    consumption = re.search(
        r"\b(?:spende(?:re)?|consuma(?:re)?|utilizz[ao](?:re)?|uso|utilizzo|utilizzi|riserva)\b",
        lowered,
    )
    recovery: list[str] = []
    if "riposo breve" in lowered:
        recovery.append("riposo breve")
    if "riposo lungo" in lowered:
        recovery.append("riposo lungo")

    for pattern, name in RESOURCE_PATTERNS:
        match = re.search(pattern, lowered)
        if not match or (not consumption and not recovery):
            continue
        nearby = lowered[max(0, match.start() - 45) : match.end() + 25]
        if re.search(r"senza\s+(?:spendere|consumare)\b", nearby):
            continue
        cost_match = re.search(r"(?:spende(?:re)?|consuma(?:re)?)\s+(\d+|un[oa]?)\s+", nearby)
        cost: int | None = None
        if cost_match:
            raw_cost = cost_match.group(1)
            cost = int(raw_cost) if raw_cost.isdigit() else 1
        result: dict[str, object] = {"name": name}
        if cost is not None:
            result["cost"] = cost
        if recovery:
            result["recovery"] = recovery
        return result
    if recovery and re.search(r"\b(?:riutilizz|utilizz|usare|uso|volte?)\b", lowered):
        return {"name": "utilizzo del privilegio", "recovery": recovery}
    return None


def invocation_prerequisite(description: str) -> tuple[str | None, int | None]:
    match = re.search(
        r"^Prerequisit[oi]:\s*(.+?)(?=\s+(?:Il |La |Lo |L'|Come |Quando |Se |Questo |Questa |Una volta |Mentre )|$)",
        description,
        re.IGNORECASE,
    )
    if not match:
        return None, None
    prerequisite = normalize_spaces(match.group(1))
    level_match = re.search(r"warlock di\s+(\d+)[º°]?\s+livello", description, re.IGNORECASE)
    return prerequisite, int(level_match.group(1)) if level_match else None


def extract_drafts(spec: ClassSpec, lines: Sequence[Line]) -> tuple[list[FeatureDraft], int]:
    drafts: list[FeatureDraft] = []
    current: FeatureDraft | None = None
    subclass_mode = False
    option_mode: str | None = None
    ignoring_spell_list = False
    level_heading_count = 0

    def finish() -> None:
        nonlocal current
        if current is not None:
            drafts.append(current)
            current = None

    for line in lines:
        text = normalize_spaces(line.text)
        lowered = text.lower()

        if line.is_red_heading and SUBCLASS_HEADING.match(text):
            finish()
            subclass_mode = True
            option_mode = None
            ignoring_spell_list = False
            continue

        if line.is_red_heading and lowered.startswith("lista degli incantesimi da "):
            finish()
            option_mode = None
            ignoring_spell_list = True
            continue

        if line.is_red_heading and lowered.startswith("opzioni di metamagia"):
            finish()
            option_mode = "metamagia"
            ignoring_spell_list = False
            continue

        if line.is_red_heading and lowered.startswith("opzioni di suppliche occulte"):
            finish()
            option_mode = "supplica-occulta"
            ignoring_spell_list = False
            continue

        level_match = LEVEL_HEADING.match(text) if line.is_red_heading else None
        if level_match:
            finish()
            level_heading_count += 1
            ignoring_spell_list = False
            current = FeatureDraft(
                name=normalize_spaces(level_match.group(2)),
                class_spec=spec,
                minimum_level=int(level_match.group(1)),
                page=line.page,
                subclass=spec.subclass if subclass_mode else None,
                kind="subclass-feature" if subclass_mode else "class-feature",
            )
            continue

        if (
            option_mode
            and line.is_red_heading
            and line.size <= 19
            and not lowered.startswith(("come personaggio", "privilegi di classe"))
        ):
            finish()
            current = FeatureDraft(
                name=text,
                class_spec=spec,
                minimum_level=2 if option_mode == "metamagia" else 1,
                page=line.page,
                subclass=None,
                kind=option_mode,
            )
            continue

        if current is not None and not ignoring_spell_list:
            current.lines.append(line)

    finish()
    return drafts, level_heading_count


def allocate_id(
    used: set[str],
    *,
    prefix: str,
    class_id: str,
    name: str,
    minimum_level: int,
    parent_slug: str | None = None,
) -> str:
    stem = f"srd521-it:{prefix}:{class_id}:{slugify(name)}"
    candidate = stem
    if candidate in used and parent_slug:
        candidate = f"{stem}-{parent_slug}"
    if candidate in used:
        candidate = f"{stem}-livello-{minimum_level}"
    suffix = 2
    unique = candidate
    while unique in used:
        unique = f"{candidate}-{suffix}"
        suffix += 1
    used.add(unique)
    return unique


def base_record(draft: FeatureDraft, used_ids: set[str], source_order: int) -> tuple[dict[str, object], list[Line]]:
    description = description_from_lines(draft.lines)
    if draft.kind == "supplica-occulta":
        prerequisite, inferred_level = invocation_prerequisite(description)
        draft.prerequisite = prerequisite
        if inferred_level is not None:
            draft.minimum_level = max(draft.minimum_level, inferred_level)

    prefix = "subclass-feature" if draft.subclass else "feature"
    record_id = allocate_id(
        used_ids,
        prefix=prefix,
        class_id=draft.class_spec.class_id,
        name=draft.name,
        minimum_level=draft.minimum_level,
    )
    record: dict[str, object] = {
        "id": record_id,
        "kind": draft.kind,
        "name": draft.name,
        "class": draft.class_spec.class_id,
        "class_name": draft.class_spec.name,
        "minimum_level": draft.minimum_level,
        "subclass": draft.subclass,
        "description": description,
        "page": draft.page,
        "activation": activation_from(description),
        "resource": resource_from(description),
        "source_order": source_order,
    }
    if draft.prerequisite:
        record["prerequisite"] = draft.prerequisite
    return record, draft.lines


def internal_option_records(
    parent: dict[str, object],
    lines: Sequence[Line],
    used_ids: set[str],
    source_order: int,
) -> list[dict[str, object]]:
    """Promote initial italic/bold body labels to searchable child records."""
    starts: list[tuple[int, str]] = []
    index = 0
    while index < len(lines):
        start_index = index
        line = lines[index]
        prefix = normalize_spaces(line.bold_prefix or "")
        wrapped_prefix = False
        if (
            prefix.endswith("-")
            and index + 1 < len(lines)
            and lines[index + 1].bold_prefix
            and lines[index + 1].page == line.page
            and lines[index + 1].column == line.column
        ):
            prefix = prefix[:-1] + normalize_spaces(lines[index + 1].bold_prefix or "")
            wrapped_prefix = True
            index += 1
        if (
            prefix
            and line.color == BLACK
            and "Cambria" in line.family
            and (wrapped_prefix or line.text.strip().startswith(prefix))
            and prefix.endswith((".", ":"))
            and 1 < len(prefix.rstrip(".:")) <= 90
        ):
            starts.append((start_index, prefix.rstrip(".:").strip()))
        index += 1

    records: list[dict[str, object]] = []
    parent_slug = slugify(str(parent["name"]))
    for option_index, (start, name) in enumerate(starts):
        end = starts[option_index + 1][0] if option_index + 1 < len(starts) else len(lines)
        option_lines = lines[start:end]
        description = description_from_lines(option_lines)
        if not description or slugify(name) == parent_slug:
            continue
        prefix = "subclass-feature" if parent["subclass"] else "feature"
        record_id = allocate_id(
            used_ids,
            prefix=prefix,
            class_id=str(parent["class"]),
            name=name,
            minimum_level=int(parent["minimum_level"]),
            parent_slug=parent_slug,
        )
        records.append(
            {
                "id": record_id,
                "kind": "internal-option",
                "name": name,
                "class": parent["class"],
                "class_name": parent["class_name"],
                "minimum_level": parent["minimum_level"],
                "subclass": parent["subclass"],
                "description": description,
                "page": option_lines[0].page,
                "activation": activation_from(description),
                "resource": resource_from(description),
                "parent_feature_id": parent["id"],
                "source_order": source_order + option_index + 1,
            }
        )
    return records


def build_catalog(pdf: Path) -> dict[str, object]:
    page_lines = parse_poppler_xml(pdf)
    all_records: list[dict[str, object]] = []
    used_ids: set[str] = set()
    level_counts: dict[str, int] = {}
    source_order = 0

    for spec in CLASSES:
        drafts, heading_count = extract_drafts(spec, class_stream(spec, page_lines))
        level_counts[spec.class_id] = heading_count
        for draft in drafts:
            record, body_lines = base_record(draft, used_ids, source_order)
            if not record["description"]:
                raise ValueError(f"Descrizione vuota per {record['id']} (pagina {record['page']})")
            all_records.append(record)
            options = internal_option_records(record, body_lines, used_ids, source_order * 100)
            all_records.extend(options)
            source_order += 1

    for index, record in enumerate(all_records):
        record["source_order"] = index
    validate_catalog(all_records, level_counts)
    kind_counts: dict[str, int] = {}
    for record in all_records:
        kind = str(record["kind"])
        kind_counts[kind] = kind_counts.get(kind, 0) + 1

    return {
        "schema_version": 1,
        "source": {
            "title": "System Reference Document 5.2.1",
            "language": "it",
            "license": "CC-BY-4.0",
            "url": "https://www.dndbeyond.com/srd",
            "pdf": "https://media.dndbeyond.com/compendium-images/srd/5.2/IT_SRD_CC_v5.2.1.pdf",
            "pages": "32-92",
        },
        "classes": [
            {
                "id": spec.class_id,
                "name": spec.name,
                "subclass": spec.subclass,
                "level_heading_count": level_counts[spec.class_id],
            }
            for spec in CLASSES
        ],
        "counts": {
            "classes": len(CLASSES),
            "records": len(all_records),
            "level_headings": sum(level_counts.values()),
            "by_kind": dict(sorted(kind_counts.items())),
        },
        "records": all_records,
    }


def validate_catalog(records: Sequence[dict[str, object]], level_counts: dict[str, int]) -> None:
    ids = [str(record["id"]) for record in records]
    if len(ids) != len(set(ids)):
        duplicates = sorted({record_id for record_id in ids if ids.count(record_id) > 1})
        raise ValueError(f"ID duplicati: {duplicates}")
    expected_level_counts = {
        "barbaro": 24,
        "bardo": 16,
        "chierico": 16,
        "druido": 18,
        "guerriero": 21,
        "ladro": 23,
        "mago": 15,
        "monaco": 26,
        "paladino": 22,
        "ranger": 22,
        "stregone": 15,
        "warlock": 14,
    }
    if level_counts != expected_level_counts:
        raise ValueError(
            "Conteggio delle intestazioni di livello inatteso: "
            f"atteso {expected_level_counts}, trovato {level_counts}"
        )
    level_records = [
        record
        for record in records
        if record["kind"] in {"class-feature", "subclass-feature"}
    ]
    if len(level_records) != sum(level_counts.values()):
        raise ValueError(
            f"Catturate {len(level_records)} intestazioni di livello su {sum(level_counts.values())}"
        )

    metamagia = [record for record in records if record["kind"] == "metamagia"]
    invocations = [record for record in records if record["kind"] == "supplica-occulta"]
    if len(metamagia) != 10:
        raise ValueError(f"Attese 10 opzioni di Metamagia, trovate {len(metamagia)}")
    if len(invocations) != 28:
        raise ValueError(f"Attese 28 Suppliche occulte, trovate {len(invocations)}")

    subclass_classes = {
        str(record["class"])
        for record in records
        if record["kind"] == "subclass-feature"
    }
    expected_classes = {spec.class_id for spec in CLASSES}
    if subclass_classes != expected_classes:
        raise ValueError(
            "Privilegi di sottoclasse incompleti: "
            f"attesi {sorted(expected_classes)}, trovati {sorted(subclass_classes)}"
        )

    for record in records:
        description = str(record["description"])
        if "System Reference Document 5.2.1" in description:
            raise ValueError(f"Footer PDF incluso nella descrizione di {record['id']}")
        if not (1 <= int(record["minimum_level"]) <= 20):
            raise ValueError(f"Livello non valido per {record['id']}")


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF, help="PDF ufficiale italiano SRD 5.2.1")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="JSON deterministico da generare")
    parser.add_argument("--check", action="store_true", help="Verifica che l'output esistente sia aggiornato")
    return parser.parse_args(argv)


def serialize(catalog: dict[str, object]) -> bytes:
    return (json.dumps(catalog, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    payload = serialize(build_catalog(args.pdf.resolve()))
    output = args.output.resolve()
    if args.check:
        if not output.is_file() or output.read_bytes() != payload:
            print(f"Output non aggiornato: {output}", file=sys.stderr)
            return 1
        print(f"OK: {output}")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
    catalog = json.loads(payload)
    print(
        f"Scritti {catalog['counts']['records']} record "
        f"({catalog['counts']['level_headings']} titoli di livello) in {output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
