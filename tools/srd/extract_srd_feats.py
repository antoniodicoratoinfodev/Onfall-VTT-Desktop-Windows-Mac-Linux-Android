#!/usr/bin/env python3
"""Estrae i talenti dell'SRD 5.2.1 italiano o inglese dal PDF ufficiale.

Il capitolo "Talenti" è su due colonne di sola prosa, senza le tabelle a tutta
pagina che complicano il capitolo delle classi: basta quindi ritagliare le due
colonne con ``pdftotext`` e leggerle nell'ordine di lettura, invece di
ricostruirle dall'XML come fa l'estrattore dei privilegi.

Un talento comincia dove una riga breve è seguita dalla riga di categoria della
lingua scelta e finisce dove ne comincia un altro o cambia sezione. Servono solo
la libreria standard e l'eseguibile ``pdftotext`` di Poppler.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import unicodedata
from pathlib import Path
from dataclasses import dataclass
from typing import Sequence

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PDF = ROOT / "IT_SRD_CC_v5.2.1.pdf"
DEFAULT_OUTPUT = ROOT / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-it/feats.json"

# Il capitolo occupa queste pagine del PDF italiano; la pagina 97 porta anche la
# spiegazione di come si legge un talento, che non è un talento e viene saltata.
@dataclass(frozen=True)
class LanguageProfile:
    """Cio' che del capitolo dei talenti cambia con la lingua dell'SRD.

    Il taglio in colonne e la ricomposizione dei paragrafi sono gli stessi; a
    cambiare sono le pagine, il nome delle quattro categorie, le intestazioni di
    sezione da saltare e la parola con cui il PDF introduce un prerequisito.
    """

    tag: str
    pdf_name: str
    first_page: int
    last_page: int
    expected_count: int
    categories: dict[str, str]
    section_headings: frozenset[str]
    prerequisite_re: "re.Pattern[str]"
    pdf_url: str


ITALIAN = LanguageProfile(
    tag="it",
    pdf_name="IT_SRD_CC_v5.2.1.pdf",
    first_page=97,
    last_page=100,
    expected_count=17,
    categories={
        "Talento Origini": "origin",
        "Talento Generale": "general",
        "Talento Stile di combattimento": "fighting-style",
        "Talento Dono epico": "epic-boon",
    },
    section_headings=frozenset(
        {
            "Talenti Origini",
            "Talenti Generali",
            "Talenti Stile di combattimento",
            "Talenti Dono epico",
            "Descrizioni dei talenti",
            "Elementi di un talento",
            "Talenti",
        }
    ),
    prerequisite_re=re.compile(r"\(prerequisito:\s*(.+?)\)", re.S | re.I),
    pdf_url="https://media.dndbeyond.com/compendium-images/srd/5.2/IT_SRD_CC_v5.2.1.pdf",
)

ENGLISH = LanguageProfile(
    tag="en",
    pdf_name="SRD_CC_v5.2.1.pdf",
    first_page=87,
    last_page=88,
    expected_count=17,
    categories={
        "Origin Feat": "origin",
        "General Feat": "general",
        "Fighting Style Feat": "fighting-style",
        "Epic Boon Feat": "epic-boon",
    },
    section_headings=frozenset(
        {
            "Origin Feats",
            "General Feats",
            "Fighting Style Feats",
            "Epic Boon Feats",
            "Feat Descriptions",
            "Parts of a Feat",
            "Feats",
        }
    ),
    prerequisite_re=re.compile(r"\(Prerequisite:\s*(.+?)\)", re.S | re.I),
    pdf_url="https://media.dndbeyond.com/compendium-images/srd/5.2/SRD_CC_v5.2.1.pdf",
)

PROFILES = {"it": ITALIAN, "en": ENGLISH}

COLUMN_WIDTH = 297
PAGE_HEIGHT = 783


def column_text(pdf: Path, page: int, left: int) -> str:
    executable = shutil.which("pdftotext")
    if not executable:
        raise RuntimeError("pdftotext (Poppler) non è disponibile nel PATH")
    result = subprocess.run(
        [
            executable,
            "-f", str(page), "-l", str(page),
            "-x", str(left), "-y", "0",
            "-W", str(COLUMN_WIDTH), "-H", str(PAGE_HEIGHT),
            str(pdf), "-",
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout


def reading_order(pdf: Path, profile: LanguageProfile) -> list[str]:
    lines: list[str] = []
    for page in range(profile.first_page, profile.last_page + 1):
        for left in (0, COLUMN_WIDTH):
            lines.extend(column_text(pdf, page, left).split("\n"))
    return lines


def is_footer(line: str) -> bool:
    """Piè di pagina: la dicitura del documento e il numero di pagina isolato.

    Ritagliando le colonne il numero resta da solo su una riga, e senza questo
    controllo finirebbe in mezzo al testo del talento che segue.
    """
    stripped = line.strip()
    return (
        bool(re.fullmatch(r"\d{1,3}", stripped))
        or bool(re.fullmatch(r"\d*\s*System Reference Document 5\.2\.1\s*\d*", stripped))
    )


def clean(text: str) -> str:
    """Toglie i segni della composizione tipografica, che non sono testo.

    Il PDF porta trattini morbidi e spazi unificatori inseriti per giustificare
    le colonne: restano invisibili a schermo ma fanno risultare diverse due
    stringhe identiche.
    """
    return text.replace("­", "").replace(" ", " ")


def join_paragraph(lines: Sequence[str]) -> str:
    """Ricompone il testo sciogliendo le sillabazioni di fine riga."""
    paragraphs: list[str] = []
    current: list[str] = []
    for raw in lines:
        line = clean(raw).strip()
        if not line:
            if current:
                paragraphs.append(" ".join(current))
                current = []
            continue
        if current and current[-1].endswith("-"):
            current[-1] = current[-1][:-1] + line
        else:
            current.append(line)
    if current:
        paragraphs.append(" ".join(current))
    return "\n".join(paragraphs).strip()


def slugify(name: str) -> str:
    text = unicodedata.normalize("NFD", name.lower())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return re.sub(r"-+", "-", text).strip("-")


def parse_feats(
    lines: Sequence[str], profile: LanguageProfile
) -> list[dict[str, object]]:
    cleaned = [line for line in lines if not is_footer(line)]
    feats: list[dict[str, object]] = []
    index = 0
    while index < len(cleaned):
        line = cleaned[index].strip()
        # L'intestazione di sezione va scartata *prima* di cercare la categoria:
        # in inglese "Origin Feats" comincia per "Origin Feat", e senza questo
        # controllo il titolo del paragrafo verrebbe letto come un talento con
        # per nome l'ultima riga di prosa che lo precede.
        if line in profile.section_headings:
            index += 1
            continue
        category_key = next(
            (key for key in profile.categories if line.startswith(key)),
            None,
        )
        if not category_key:
            index += 1
            continue
        # La riga di categoria può proseguire sulla successiva quando contiene un
        # prerequisito lungo: si accumula finché la parentesi non si chiude.
        category_line = line
        cursor = index + 1
        while category_line.count("(") > category_line.count(")") and cursor < len(cleaned):
            category_line = f"{category_line} {cleaned[cursor].strip()}"
            cursor += 1

        name_index = index - 1
        while name_index >= 0 and not cleaned[name_index].strip():
            name_index -= 1
        name = cleaned[name_index].strip() if name_index >= 0 else ""
        if not name or name in profile.section_headings:
            index = cursor
            continue

        body: list[str] = []
        while cursor < len(cleaned):
            candidate = cleaned[cursor].strip()
            if any(candidate.startswith(key) for key in profile.categories):
                # Il nome del prossimo talento è già finito nel corpo: si toglie.
                while body and not body[-1].strip():
                    body.pop()
                if body:
                    body.pop()
                break
            if candidate in profile.section_headings:
                break
            body.append(cleaned[cursor])
            cursor += 1

        prerequisite = ""
        match = profile.prerequisite_re.search(category_line)
        if match:
            prerequisite = re.sub(r"\s+", " ", match.group(1)).strip()

        feats.append(
            {
                "id": f"srd521-{profile.tag}:feat:{profile.categories[category_key]}:{slugify(name)}",
                "name": name,
                "category": profile.categories[category_key],
                "prerequisite": prerequisite,
                "description": join_paragraph(body),
            }
        )
        index = cursor
    return feats


def build_catalog(pdf: Path, profile: LanguageProfile) -> dict[str, object]:
    feats = parse_feats(reading_order(pdf, profile), profile)
    # Un nome che finisce col punto o comincia in minuscolo e' una riga di prosa
    # scambiata per un titolo: meglio fermarsi che scrivere un catalogo sbagliato.
    suspicious = [
        str(feat["name"])
        for feat in feats
        if str(feat["name"]).endswith(".") or str(feat["name"])[:1].islower()
    ]
    if suspicious:
        raise ValueError(f"nomi di talento sospetti: {suspicious}")
    if len(feats) != profile.expected_count:
        raise ValueError(
            f"attesi {profile.expected_count} talenti, trovati {len(feats)}"
        )
    by_category: dict[str, int] = {}
    for feat in feats:
        by_category[feat["category"]] = by_category.get(feat["category"], 0) + 1
    return {
        "schema_version": 1,
        "source": {
            "title": "System Reference Document 5.2.1",
            "language": profile.tag,
            "license": "CC-BY-4.0",
            "url": "https://www.dndbeyond.com/srd",
            "pdf": profile.pdf_url,
            "pages": f"{profile.first_page}-{profile.last_page}",
        },
        "counts": {"feats": len(feats), "by_category": by_category},
        "feats": feats,
    }


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--language", choices=sorted(PROFILES), default="it", help="lingua dell'SRD"
    )
    parser.add_argument("--pdf", type=Path, default=None, help="PDF ufficiale SRD 5.2.1")
    parser.add_argument("--output", type=Path, default=None, help="JSON da generare")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    profile = PROFILES[args.language]
    pdf = args.pdf or ROOT / profile.pdf_name
    output = args.output or (
        ROOT / f"content/srd-5.2.1-it/src/main/resources/srd/5.2.1-{profile.tag}/feats.json"
    )
    if not pdf.is_file():
        raise FileNotFoundError(f"PDF SRD non trovato: {pdf}")
    catalog = build_catalog(pdf.resolve(), profile)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{output}: {catalog['counts']['feats']} talenti · {catalog['counts']['by_category']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
