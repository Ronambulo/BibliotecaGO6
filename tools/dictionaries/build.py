"""
Genera los diccionarios offline del lector EPUB (SQLite, en los assets de reader-epub).

  es.db: Wiktionary en español (extracto de wiktextract publicado en kaikki.org), CC BY-SA 4.0.
  en.db: Princeton WordNet 3.1, licencia WordNet.

Uso:
  curl -LO https://kaikki.org/eswiktionary/raw-wiktextract-data.jsonl.gz
  curl -LO https://wordnetcode.princeton.edu/wn3.1.dict.tar.gz && tar xzf wn3.1.dict.tar.gz
  python tools/dictionaries/build.py raw-wiktextract-data.jsonl.gz dict reader-epub/src/main/assets/dict

Tablas: entry(word, pos, defs) con las acepciones separadas por saltos de línea, y
form(form, lemma) para llegar de una forma flexionada («comió», «geese») a su lema.
"""
import gzip
import json
import os
import re
import sqlite3
import sys

MAX_SENSES = 8
MAX_GLOSS = 400

# Categorías gramaticales que no aportan al leer: nombres propios, siglas, etc.
ES_SKIP_POS = {"Sustantivo propio", "Locución sustantiva propia", "Sigla", "Abreviatura", "Símbolo", "Letra"}


def create(path):
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.executescript(
        """
        PRAGMA page_size = 4096;
        CREATE TABLE entry (word TEXT NOT NULL, pos TEXT NOT NULL, defs TEXT NOT NULL);
        CREATE TABLE form (form TEXT NOT NULL, lemma TEXT NOT NULL, PRIMARY KEY (form, lemma)) WITHOUT ROWID;
        """
    )
    return db


def finish(db):
    db.execute("CREATE INDEX entry_word ON entry(word)")
    db.commit()
    db.execute("VACUUM")
    db.close()


def clean(gloss):
    gloss = re.sub(r"\s+", " ", gloss).strip()
    return gloss[:MAX_GLOSS]


def build_es(source, out):
    db = create(out)
    lemmas = set()
    forms = set()
    with gzip.open(source, "rt", encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            if d.get("lang_code") != "es" or not d.get("senses"):
                continue
            word = d["word"].lower()
            pos = d.get("pos_title") or ""
            if pos in ES_SKIP_POS:
                continue
            defs = []
            for sense in d["senses"]:
                if "form_of" in sense:
                    for target in sense["form_of"]:
                        forms.add((word, target["word"].lower()))
                    continue
                glosses = sense.get("glosses")
                if glosses:
                    defs.append(clean(glosses[-1]))
            if defs:
                lemmas.add(word)
                db.execute("INSERT INTO entry VALUES (?, ?, ?)", (word, pos, "\n".join(defs[:MAX_SENSES])))
                for form in d.get("forms", []):
                    text = form.get("form", "").lower()
                    if text and " " not in text and text != word:
                        forms.add((text, word))
    # Solo formas que llevan a un lema con definición.
    db.executemany("INSERT OR IGNORE INTO form VALUES (?, ?)", ((f, l) for f, l in forms if l in lemmas and f != l))
    finish(db)


WN_POS_EN = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb"}


def build_en(wordnet, out):
    db = create(out)
    for pos in ("noun", "verb", "adj", "adv"):
        glosses = {}
        with open(os.path.join(wordnet, f"data.{pos}"), encoding="utf-8") as f:
            for line in f:
                if line.startswith("  "):
                    continue
                offset, _, rest = line.partition(" ")
                gloss = rest.partition("| ")[2]
                # Sin los ejemplos entre comillas: solo la definición.
                glosses[offset] = clean(gloss.split('; "')[0].split(' "')[0])
        with open(os.path.join(wordnet, f"index.{pos}"), encoding="utf-8") as f:
            for line in f:
                if line.startswith("  "):
                    continue
                parts = line.split()
                lemma = parts[0].replace("_", " ")
                synsets = int(parts[2])
                offsets = parts[-synsets:]
                defs = [glosses[o] for o in offsets if glosses.get(o)]
                db.execute("INSERT INTO entry VALUES (?, ?, ?)", (lemma, WN_POS_EN[pos], "\n".join(defs[:MAX_SENSES])))
        with open(os.path.join(wordnet, f"{pos}.exc"), encoding="utf-8") as f:
            for line in f:
                parts = line.split()
                for lemma in parts[1:]:
                    db.execute("INSERT OR IGNORE INTO form VALUES (?, ?)", (parts[0].replace("_", " "), lemma.replace("_", " ")))
    finish(db)


if __name__ == "__main__":
    wiktionary, wordnet, out_dir = sys.argv[1:4]
    os.makedirs(out_dir, exist_ok=True)
    build_en(wordnet, os.path.join(out_dir, "en.db"))
    build_es(wiktionary, os.path.join(out_dir, "es.db"))
    for name in ("en.db", "es.db"):
        print(name, os.path.getsize(os.path.join(out_dir, name)) // 1024, "KB")
