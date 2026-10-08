# Witaj w `millenaire-custom/`

Ten folder jest **TWÓJ**. Millénaire nigdy nie nadpisuje ani nie usuwa
niczego, co tu umieścisz, nawet podczas aktualizacji moda. Używaj go,
aby dodawać nowe budynki, mieszkańców, kultury, języki, zadania lub
dowolną inną zawartość.

Sąsiedni folder `millenaire/` należy do moda i jest ponownie wdrażany
przy każdej zmianie wersji. Czytaj go jako referencję; nie edytuj go
bezpośrednio.

## Model sub-modów

Niestandardowa zawartość jest dostarczana jako **sub-mody**: każdy
bezpośredni podkatalog `millenaire-custom/` jest jednym sub-modem,
z własnymi poddrzewami `cultures/`, `languages/`, `gathering_type/`
i/lub `quests/`. Wiele sub-modów może współistnieć; ich wkłady
nakładają się deterministycznie (dla plików REPLACE wygrywa pierwszy
sub-mod alfabetycznie, pozostałe wnoszą wkład addytywnie).

Nazwy katalogów sub-modów muszą pasować do `[A-Za-z0-9_-]+`. Nazwy
`cultures`, `languages`, `gathering_type`, `quests` i `exports` są
zarezerwowane na najwyższym poziomie — **nie** umieszczaj zawartości
bezpośrednio pod `millenaire-custom/cultures/...`. Mod odmówi
ładowania takich plików i zaloguje WARN podczas uruchamiania.

## Szybki start 1 — Dodaj budynek do kultury normańskiej

```
millenaire-custom/
└── my-norman-tweaks/             # dowolna nazwa [A-Za-z0-9_-]+ — twój sub-mod
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # metadane planu (skopiuj z _templates/)
                    └── my_house.nbt     # szablon struktury (eksportuj z gry)
```

`.nbt` jest tworzony w grze za pomocą bloku **Import Table**. `.json`
jest kopiowany z `millenaire/_templates/_template_building.json` i
edytowany.

## Szybki start 2 — Stwórz zupełnie nową kulturę

```
millenaire-custom/
└── viking-pack/                          # twój sub-mod
    ├── cultures/
    │   └── viking/                       # id kultury (musi pasować do [a-z0-9]+)
    │       ├── culture.json              # wymagane: name, language, biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # wymagane: co najmniej jedna lista imion
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # wymagane: co najmniej jeden typ mieszkańca
    │       ├── villages/
    │       │   └── basic_village.json    # wymagane: co najmniej jeden typ wioski
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # wymagane: co najmniej jeden ratusz
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # wymagane: sklep dla ratusza
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # obowiązkowe, co najmniej en_us
```

## Brak hot reload

Zawartość jest ładowana raz, przy starcie serwera. Po edycji
jakiegokolwiek pliku w `millenaire-custom/`, **uruchom ponownie
serwer**, aby zmiany odniosły skutek.

## Importowanie dodatku Millénaire 1.12

Umieść stary pakiet jako podfolder `millenaire-custom/` (np.
`millenaire-custom/MyOldPack/`). Przy następnym uruchomieniu
Millénaire automatycznie konwertuje go do `MyOldPack_converted/`.
Sąsiedni folder `_converted/` staje się sub-modem uruchomieniowym;
oryginał jest zachowany jako źródło.

## Wskazówki

- **Pełny przewodnik**: `millenaire/_docs/content-guide.en.md`
  (wdrażany przy każdym starcie moda)
- **Szablony** (szkielety do skopiowania): `millenaire/_templates/`
- **Katalogi referencyjne** (ID celów, punkty specjalne, ...):
  `millenaire/_reference/`
- **Repozytorium GitHub**: <https://github.com/Kinniken/Millenaire>
