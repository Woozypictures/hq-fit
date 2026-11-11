#!/usr/bin/env python3
"""Maps HQ Fit exercise names to free-exercise-db folders (Unlicense) so the app can load stills from GitHub.
Writes app/src/main/assets/exercise_images.tsv: normalised name <TAB> free-exercise-db id.
Usage: python3 tools/build_exercise_images.py [path/to/exercises.json]  (downloads it when no path is given)"""
import json, re, sys, difflib, urllib.request

URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
# Hand fixes where the names differ more than a fuzzy match can safely bridge.
OVERRIDES = {
    "cable triceps pushdown": "Triceps Pushdown",
    "cable rope overhead tricep extension": "Cable Rope Overhead Triceps Extension",
    "treadmill walk": "Walking, Treadmill",
    "romanian deadlift with dumbbells": "Romanian Deadlift",
    "machine lateral raise": "Side Lateral Raise",
    "decline chest press machine": "Leverage Decline Chest Press",
    "machine fly": "Butterfly",
}

def norm(s):
    s = re.sub(r"[^a-z0-9 ]", "", s.lower().replace("-", " "))
    return " ".join(w[:-1] if len(w) > 3 and w.endswith("s") and not w.endswith("ss") else w for w in s.split())

def main():
    data = json.load(open(sys.argv[1])) if len(sys.argv) > 1 else json.load(urllib.request.urlopen(URL))
    fed = {norm(e["name"]): e["id"] for e in data}
    byname = {e["name"]: e["id"] for e in data}
    ours = [l.split("\t")[1] for l in open("core/data/src/main/assets/exercises.tsv", encoding="utf-8") if "\t" in l]
    prog = open("core/model/src/main/kotlin/uk/woozy/fit/model/Programme.kt", encoding="utf-8").read()
    ours += re.findall(r'"([A-Z][A-Za-z0-9 ,()/\-]+)"', prog)
    out, exact, fuzzy = {}, 0, 0
    for name in dict.fromkeys(ours):
        n = norm(name)
        if n in out: continue
        key = norm(name)
        if key in OVERRIDES: out[n] = byname[OVERRIDES[key]]; exact += 1; continue
        if n in fed: out[n] = fed[n]; exact += 1; continue
        m = difflib.get_close_matches(n, list(fed), n=1, cutoff=0.93)
        if m: out[n] = fed[m[0]]; fuzzy += 1
    for k, v in OVERRIDES.items(): out[norm(k)] = byname[v]
    open("app/src/main/assets/exercise_images.tsv", "w", encoding="utf-8").write("".join(f"{k}\t{v}\n" for k, v in sorted(out.items())))
    print(len(ours), "names;", exact, "exact,", fuzzy, "fuzzy,", len(out), "mapped")

main()
