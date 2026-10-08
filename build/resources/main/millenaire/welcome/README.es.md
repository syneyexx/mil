# Bienvenido a `millenaire-custom/`

Esta carpeta es **TUYA**. Millénaire nunca sobrescribe ni elimina nada
de lo que pongas aquí, incluso cuando se actualiza el mod. Úsala para
añadir nuevos edificios, aldeanos, culturas, idiomas, misiones o
cualquier otro contenido.

La carpeta hermana `millenaire/` pertenece al mod y se vuelve a desplegar
en cada cambio de versión. Léela como referencia; no la edites
directamente.

## Modelo de sub-mods

El contenido personalizado se distribuye como **sub-mods**: cada
subdirectorio directo de `millenaire-custom/` es un sub-mod, con sus
propios subárboles `cultures/`, `languages/`, `gathering_type/` y/o
`quests/`. Varios sub-mods pueden coexistir; sus contribuciones se
superponen de forma determinista (para los archivos REPLACE gana el
primer sub-mod por orden alfabético, los demás contribuyen de forma
aditiva).

Los nombres de directorios de sub-mods deben coincidir con
`[A-Za-z0-9_-]+`. Los nombres `cultures`, `languages`, `gathering_type`,
`quests` y `exports` están reservados en el nivel superior — **no**
pongas contenido directamente bajo `millenaire-custom/cultures/...`.
El mod rechazará cargar dichos archivos y registrará un WARN al arrancar.

## Inicio rápido 1 — Añadir un edificio a la cultura normanda

```
millenaire-custom/
└── my-norman-tweaks/             # cualquier nombre [A-Za-z0-9_-]+ — tu sub-mod
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # metadatos del plan (copia de _templates/)
                    └── my_house.nbt     # plantilla de estructura (exporta desde el juego)
```

El `.nbt` se produce en el juego con el bloque **Import Table**. El
`.json` se copia de `millenaire/_templates/_template_building.json` y
se edita.

## Inicio rápido 2 — Crear una cultura nueva desde cero

```
millenaire-custom/
└── viking-pack/                          # tu sub-mod
    ├── cultures/
    │   └── viking/                       # el id de la cultura (debe coincidir con [a-z0-9]+)
    │       ├── culture.json              # obligatorio: name, language, biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # obligatorio: al menos una lista de nombres
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # obligatorio: al menos un tipo de aldeano
    │       ├── villages/
    │       │   └── basic_village.json    # obligatorio: al menos un tipo de aldea
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # obligatorio: al menos un ayuntamiento
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # obligatorio: tienda para el ayuntamiento
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # obligatorio, al menos en_us
```

## Sin recarga en caliente

El contenido se carga una sola vez, al arrancar el servidor. Tras
editar cualquier archivo bajo `millenaire-custom/`, **reinicia el
servidor** para que los cambios surtan efecto.

## Importar un complemento de Millénaire 1.12

Coloca el pack heredado como subcarpeta de `millenaire-custom/` (p. ej.
`millenaire-custom/MyOldPack/`). En el siguiente arranque Millénaire lo
convierte automáticamente en `MyOldPack_converted/`. La carpeta hermana
`_converted/` pasa a ser el sub-mod en tiempo de ejecución; el original
se conserva como fuente.

## Referencias

- **Guía completa**: `millenaire/_docs/content-guide.en.md` (desplegada
  en cada arranque del mod)
- **Plantillas** (esqueletos para copiar): `millenaire/_templates/`
- **Catálogos de referencia** (IDs de goals, puntos especiales, ...):
  `millenaire/_reference/`
- **Repositorio GitHub**: <https://github.com/Kinniken/Millenaire>
