# Willkommen in `millenaire-custom/`

Dieser Ordner gehört **DIR**. Millénaire überschreibt oder löscht
niemals etwas, das du hier ablegst, selbst wenn der Mod aktualisiert
wird. Verwende ihn, um neue Gebäude, Dorfbewohner, Kulturen, Sprachen,
Quests oder andere Inhalte hinzuzufügen.

Der Nachbarordner `millenaire/` gehört dem Mod und wird bei jedem
Versionswechsel neu ausgespielt. Lies ihn als Referenz; bearbeite ihn
nicht direkt.

## Sub-Mod-Modell

Benutzerdefinierte Inhalte werden als **Sub-Mods** ausgeliefert: jedes
direkte Unterverzeichnis von `millenaire-custom/` ist ein Sub-Mod mit
eigenen `cultures/`-, `languages/`-, `gathering_type/`- und/oder
`quests/`-Teilbäumen. Mehrere Sub-Mods können nebeneinander existieren;
ihre Beiträge überlagern sich deterministisch (bei REPLACE-Dateien
gewinnt der alphabetisch erste Sub-Mod, andere tragen additiv bei).

Sub-Mod-Verzeichnisnamen müssen `[A-Za-z0-9_-]+` entsprechen. Die
Namen `cultures`, `languages`, `gathering_type`, `quests` und
`exports` sind auf oberster Ebene reserviert — lege Inhalte **nicht**
direkt unter `millenaire-custom/cultures/...` ab. Der Mod weigert sich,
solche Dateien zu laden, und protokolliert beim Start einen WARN.

## Schnellstart 1 — Ein Gebäude zur normannischen Kultur hinzufügen

```
millenaire-custom/
└── my-norman-tweaks/             # beliebiger Name [A-Za-z0-9_-]+ — dein Sub-Mod
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # Plan-Metadaten (aus _templates/ kopieren)
                    └── my_house.nbt     # Strukturvorlage (aus dem Spiel exportieren)
```

Die `.nbt`-Datei wird im Spiel mit dem **Import Table**-Block erzeugt.
Die `.json`-Datei wird aus
`millenaire/_templates/_template_building.json` kopiert und bearbeitet.

## Schnellstart 2 — Eine brandneue Kultur erstellen

```
millenaire-custom/
└── viking-pack/                          # dein Sub-Mod
    ├── cultures/
    │   └── viking/                       # die Kultur-id (muss [a-z0-9]+ entsprechen)
    │       ├── culture.json              # erforderlich: name, language, biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # erforderlich: mindestens eine namelist
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # erforderlich: mindestens ein Dorfbewohnertyp
    │       ├── villages/
    │       │   └── basic_village.json    # erforderlich: mindestens ein Dorftyp
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # erforderlich: mindestens ein Rathaus
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # erforderlich: Shop für das Rathaus
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # obligatorisch, mindestens en_us
```

## Kein Hot Reload

Inhalte werden einmalig beim Serverstart geladen. Nach dem Bearbeiten
einer Datei unter `millenaire-custom/` **starte den Server neu**, damit
die Änderungen wirksam werden.

## Ein Millénaire-1.12-Addon importieren

Lege das Legacy-Pack als Unterordner von `millenaire-custom/` ab
(z. B. `millenaire-custom/MyOldPack/`). Beim nächsten Boot wandelt
Millénaire es automatisch in `MyOldPack_converted/` um. Der
`_converted/`-Nachbarordner wird zum Laufzeit-Sub-Mod; das Original
bleibt als Quelle erhalten.

## Verweise

- **Vollständiger Leitfaden**: `millenaire/_docs/content-guide.en.md`
  (wird bei jedem Mod-Start ausgespielt)
- **Vorlagen** (zu kopierende Gerüste): `millenaire/_templates/`
- **Referenzkataloge** (Goal-IDs, spezielle Punkte, ...):
  `millenaire/_reference/`
- **GitHub-Repository**: <https://github.com/Kinniken/Millenaire>
