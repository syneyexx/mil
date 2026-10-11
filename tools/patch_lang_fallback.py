from pathlib import Path

p = Path("src/main/resources/assets/millenaire/lang/en_us.json")
t = p.read_text(encoding="utf-8")
key = "millenaire.config.worldgenUntaggedBiomeFallback"
if key in t:
    print("already present")
else:
    body = t.rstrip()
    if body.endswith("}"):
        body = body[:-1].rstrip()
        if not body.endswith(","):
            body += ","
        t = body + f'\n  "{key}": "Untagged Biome Climate Fallback"\n}}\n'
        p.write_text(t, encoding="utf-8")
        print("lang updated")
