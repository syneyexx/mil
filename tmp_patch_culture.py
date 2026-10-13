from pathlib import Path

p = Path(r"c:\Users\xxx\Desktop\mil\mil\src\main\java\org\millenaire\culture\CultureLoader.java")
t = p.read_text(encoding="utf-8")
old = "ContentStatsReporter.capture(activeCultures, new HashSet<>(BUILTIN_CULTURES));\n   }"
new = (
    "ContentStatsReporter.capture(activeCultures, new HashSet<>(BUILTIN_CULTURES));\n"
    "      org.millenaire.village.PlayerBuildingPlans.registerAll();\n"
    "   }"
)
if old not in t:
    # try CRLF
    old = old.replace("\n", "\r\n")
    new = new.replace("\n", "\r\n")
if old not in t:
    raise SystemExit("pattern missing")
p.write_text(t.replace(old, new, 1), encoding="utf-8")
print("ok")
