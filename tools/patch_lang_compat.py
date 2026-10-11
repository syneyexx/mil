from pathlib import Path

p = Path("src/main/resources/assets/millenaire/lang/en_us.json")
text = p.read_text(encoding="utf-8")
if "placeVillageWaystones" in text:
    print("already present")
else:
    inserts = (
        '  "millenaire.config.placeVillageWaystones": "Place Village Waystones",\n'
        '  "millenaire.title.mediterranean": "Mediterranean Shores",\n'
        '  "millenaire.title.mediterranean.sub": "Byzantine and Seljuk lands",\n'
        '  "millenaire.title.sakura": "Cherry Blossom Groves",\n'
        '  "millenaire.title.sakura.sub": "Japanese frontier"\n'
    )
    body = text.rstrip()
    if body.endswith("}"):
        body = body[:-1].rstrip()
        if not body.endswith(","):
            body += ","
        text = body + "\n" + inserts + "}\n"
        p.write_text(text, encoding="utf-8")
        print("lang updated")
