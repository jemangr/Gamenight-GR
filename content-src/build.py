#!/usr/bin/env python3
"""Builds app/src/main/assets/content/<lang>/*.json from the plain text files here.

Formats (one item per line, '#' comments, a leading '!' marks 18+):
  taboo*.txt   word|banned1,...,banned5|category
  quads*.txt   blocks of 4 lines "level|group name|w1,w2,w3,w4", blank line between puzzles
  bomb*.txt    text   (a trailing ' +' means: add a letter)
  find10*.txt / related*.txt / song*.txt   text
  movies*.txt  emoji|title|hint|f or s (film/series)|gr (optional)
"""
import glob, json, os, sys
lang = sys.argv[1] if len(sys.argv) > 1 else "el"
src = os.path.join(os.path.dirname(__file__), lang)
out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "content", lang)
os.makedirs(out, exist_ok=True)

def lines(prefix):
    for f in sorted(glob.glob(os.path.join(src, prefix + "*.txt"))):
        for raw in open(f, encoding="utf-8"):
            l = raw.rstrip("\n")
            if l.strip().startswith("#"): continue
            yield l
        yield ""

def adult(l):
    return (l[1:], True) if l.startswith("!") else (l, False)

def dump(name, items):
    with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{name}: {len(items)}")

taboo = []
for l in lines("taboo"):
    if not l.strip(): continue
    l, a = adult(l)
    w, no, c = l.split("|")
    item = {"w": w.strip(), "no": [x.strip() for x in no.split(",")], "c": c.strip()}
    if a: item["a"] = True
    taboo.append(item)
dump("taboo", taboo)

quads, cur, curA = [], [], False
for l in list(lines("quads")) + [""]:
    if not l.strip():
        if cur: quads.append({"groups": cur, **({"a": True} if curA else {})}); cur, curA = [], False
        continue
    l, a = adult(l)
    curA = curA or a
    lv, name, words = l.split("|")
    cur.append({"name": name.strip(), "words": [x.strip() for x in words.split(",")], "level": int(lv)})
dump("quads", quads)

def prompts(name, letter=False):
    items = []
    for l in lines(name):
        if not l.strip(): continue
        l, a = adult(l)
        it = {"t": l.strip()}
        if letter and l.rstrip().endswith(" +"): it = {"t": l.rstrip()[:-2].strip(), "l": True}
        if a: it["a"] = True
        items.append(it)
    dump(name, items)

prompts("bomb", letter=True)
prompts("find10"); prompts("related"); prompts("song")

movies = []
for l in lines("movies"):
    if not l.strip(): continue
    l, a = adult(l)
    p = [x.strip() for x in l.split("|")]
    it = {"e": p[0], "title": p[1], "hint": p[2]}
    if len(p) > 3 and p[3] == "s": it["series"] = True
    if len(p) > 4 and p[4] == "gr": it["gr"] = True
    if a: it["a"] = True
    movies.append(it)
dump("movies", movies)
