#!/usr/bin/env python3
"""Builds app/src/main/assets/cofid.tsv from the CoFID 2021 integrated dataset xlsx.
Usage: python3 tools/build_cofid.py path/to/McCance_Widdowsons_..._2021.xlsx
Columns: code, name, per-100 g nutrients as key=value;key=value (HQ Fit keys).
"Tr" (trace) becomes 0; "N" (present, not measured) and blanks are left out. Brackets (estimates) are stripped.
Carbohydrate: CoFID's figure excludes fibre, so carbs = CHO + fibre and net carbs (carbs - fibre) comes back to CHO."""
import sys, re, openpyxl

MAP = {  # sheet -> {CoFID column code: (HQ Fit key, scale)}
    "1.3 Proximates": {"WATER": ("water", 1), "PROT": ("protein", 1), "FAT": ("fat", 1), "KCALS": ("energy", 1),
                       "TOTSUG": ("sugars", 1), "ALCO": ("alcohol", 1), "SATFOD": ("sat_fat", 1), "MONOFOD": ("mono_fat", 1),
                       "POLYFOD": ("poly_fat", 1), "FODTRANS": ("trans_fat", 1), "CHOL": ("cholesterol", 1),
                       "CHO": ("_cho", 1), "AOACFIB": ("_aoac", 1), "ENGFIB": ("_nsp", 1)},
    "1.4 Inorganics": {"NA": ("sodium", 1), "K": ("potassium", 1), "CA": ("calcium", 1), "MG": ("magnesium", 1),
                       "P": ("phosphorus", 1), "FE": ("iron", 1), "CU": ("copper", 1), "ZN": ("zinc", 1),
                       "MN": ("manganese", 1), "SE": ("selenium", 1), "I": ("iodine", 1)},
    "1.5 Vitamins": {"RETEQU": ("vit_a", 1), "VITD": ("vit_d", 1), "VITE": ("vit_e", 1), "VITK1": ("vit_k", 1),
                     "THIA": ("thiamin", 1), "RIBO": ("riboflavin", 1), "NIAC": ("niacin", 1), "VITB6": ("vit_b6", 1),
                     "VITB12": ("vit_b12", 1), "FOLT": ("folate", 1), "PANTO": ("pantothenic", 1), "BIOT": ("biotin", 1),
                     "VITC": ("vit_c", 1)},
}

def num(v):
    if v is None: return None
    if isinstance(v, (int, float)): return float(v)
    t = str(v).strip().strip("()[]").strip()
    if t == "Tr": return 0.0
    try: return float(t)
    except ValueError: return None  # N, blank, text

def main(path, out):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    foods = {}
    for sheet, cols in MAP.items():
        rows = wb[sheet].iter_rows(values_only=True)
        head = next(rows); codes = next(rows); next(rows)
        idx = {c: i for i, c in enumerate(codes) if c in cols}
        for r in rows:
            code = r[0]
            if not code or not str(code).strip(): continue
            code = str(code).strip()
            f = foods.setdefault(code, {"name": str(r[1]).strip() if r[1] else "", "n": {}})
            for c, i in idx.items():
                v = num(r[i]) if i < len(r) else None
                if v is not None: f["n"][cols[c][0]] = v * cols[c][1]
    lines = []
    for code, f in foods.items():
        n = f["n"]
        if not f["name"] or "energy" not in n: continue
        fibre = n.pop("_aoac", None)
        if fibre is None: fibre = n.pop("_nsp", 0.0)
        else: n.pop("_nsp", None)
        cho = n.pop("_cho", None)
        if cho is not None:
            n["carbs"] = round(cho + fibre, 2); n["fibre"] = fibre
        elif fibre: n["fibre"] = fibre
        body = ";".join(f"{k}={round(v, 3):g}" for k, v in sorted(n.items()) if v != 0)
        lines.append(f"{code}\t{f['name'].replace(chr(9), ' ')}\t{body}")
    open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(len(lines), "foods")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/cofid.tsv")
