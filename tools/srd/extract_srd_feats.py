#!/usr/bin/env python3
"""Estrae i talenti dell'SRD 5.2.1 italiano dal PDF ufficiale.

Il capitolo "Talenti" è su due colonne di sola prosa, senza le tabelle a tutta
pagina che complicano il capitolo delle classi: basta quindi ritagliare le due
colonne con ``pdftotext`` e leggerle nell'ordine di lettura, invece di
ricostruirle dall'XML come fa l'estrattore dei privilegi.

Un talento comincia dove una riga breve è seguita dalla riga di categoria
("Talento Origini", "Talento Generale (prerequisito: ...)") e finisce dove ne
comincia un altro o cambia sezione. Servono solo la libreria standard e
l'eseguibile ``pdftotext`` di Poppler.
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
from typing import Sequence

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PDF = ROOT / "IT_SRD_CC_v5.2.1.pdf"
DEFAULT_OUTPUT = ROOT / "content/srd-5.2.1-it/src/main/resources/srd/5.2.1-it/feats.json"

# Il capitolo occupa queste pagine del PDF italiano; la pagina 97 porta anche la
# spiegazione di come si legge un talento, che non è un talento e viene saltata.
FIRST_PAGE = 97
LAST_PAGE = 100
COLUMN_WIDTH = 297
PAGE_HEIGHT = 783

CATEGORIES = {
    "Talento Origini": "origin",
    "Talento Generale": "general",
    "Talento Stile di combattimento": "fighting-style",
    "Talento Dono epico": "epic-boon",
}

SECTION_HEADINGS = {
    "Talenti Origini",
    "Talenti Generali",
    "Talenti Stile di combattimento",
    "Talenti Dono epico",
    "Descrizioni dei talenti",
    "Elementi di un talento",
    "Talenti",
}


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


def reading_order(pdf: Path) -> list[str]:
    lines: list[str] = []
    for page in range(FIRST_PAGE, LAST_PAGE + 1):
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


def parse_feats(lines: Sequence[str]) -> list[dict[str, object]]:
    cleaned = [line for line in lines if not is_footer(line)]
    feats: list[dict[str, object]] = []
    index = 0
    while index < len(cleaned):
        line = cleaned[index].strip()
        category_key = next(
            (key for key in CATEGORIES if line.startswith(key)),
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
        if not name or name in SECTION_HEADINGS:
            index = cursor
            continue

        body: list[str] = []
        while cursor < len(cleaned):
            candidate = cleaned[cursor].strip()
            if any(candidate.startswith(key) for key in CATEGORIES):
                # Il nome del prossimo talento è già finito nel corpo: si toglie.
                while body and not body[-1].strip():
                    body.pop()
                if body:
                    body.pop()
                break
            if candidate in SECTION_HEADINGS:
                break
            body.append(cleaned[cursor])
            cursor += 1

        prerequisite = ""
        match = re.search(r"\(prerequisito:\s*(.+?)\)", category_line, flags=re.S)
        if match:
            prerequisite = re.sub(r"\s+", " ", match.group(1)).strip()

        feats.append(
            {
                "id": f"srd521-it:feat:{CATEGORIES[category_key]}:{slugify(name)}",
                "name": name,
                "category": CATEGORIES[category_key],
                "prerequisite": prerequisite,
                "description": join_paragraph(body),
            }
        )
        index = cursor
    return feats


def build_catalog(pdf: Path) -> dict[str, object]:
    feats = parse_feats(reading_order(pdf))
    by_category: dict[str, int] = {}
    for feat in feats:
        by_category[feat["category"]] = by_category.get(feat["category"], 0) + 1
    return {
        "schema_version": 1,
        "source": {
            "title": "System Reference Document 5.2.1",
            "language": "it",
            "license": "CC-BY-4.0",
            "url": "https://www.dndbeyond.com/srd",
            "pdf": "https://media.dndbeyond.com/compendium-images/srd/5.2/IT_SRD_CC_v5.2.1.pdf",
            "pages": f"{FIRST_PAGE}-{LAST_PAGE}",
        },
        "counts": {"feats": len(feats), "by_category": by_category},
        "feats": feats,
    }


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF, help="PDF ufficiale italiano SRD 5.2.1")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="JSON da generare")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    if not args.pdf.is_file():
        raise FileNotFoundError(f"PDF SRD non trovato: {args.pdf}")
    catalog = build_catalog(args.pdf.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{args.output}: {catalog['counts']['feats']} talenti · {catalog['counts']['by_category']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
