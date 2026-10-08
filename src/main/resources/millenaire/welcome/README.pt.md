# Bem-vindo a `millenaire-custom/`

Esta pasta é **SUA**. O Millénaire nunca sobrescreve nem apaga aquilo
que aqui colocares, mesmo quando o mod é actualizado. Usa-a para
adicionar novos edifícios, aldeões, culturas, idiomas, missões ou
qualquer outro conteúdo.

A pasta vizinha `millenaire/` pertence ao mod e é reimplantada a cada
mudança de versão. Lê-a como referência; não a edites directamente.

## Modelo de sub-mods

O conteúdo personalizado é distribuído como **sub-mods**: cada
subdirectório directo de `millenaire-custom/` é um sub-mod, com as suas
próprias subárvores `cultures/`, `languages/`, `gathering_type/` e/ou
`quests/`. Vários sub-mods podem coexistir; as suas contribuições
sobrepõem-se deterministicamente (para ficheiros REPLACE ganha o
primeiro sub-mod por ordem alfabética, os restantes contribuem de forma
aditiva).

Os nomes de directório de sub-mods devem corresponder a `[A-Za-z0-9_-]+`.
Os nomes `cultures`, `languages`, `gathering_type`, `quests` e `exports`
estão reservados no nível superior — **não** coloques conteúdo
directamente sob `millenaire-custom/cultures/...`. O mod recusar-se-á a
carregar tais ficheiros e registará um WARN no arranque.

## Início rápido 1 — Adicionar um edifício à cultura normanda

```
millenaire-custom/
└── my-norman-tweaks/             # qualquer nome [A-Za-z0-9_-]+ — o teu sub-mod
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # metadados do plano (copia de _templates/)
                    └── my_house.nbt     # modelo de estrutura (exporta a partir do jogo)
```

O `.nbt` é produzido dentro do jogo com o bloco **Import Table**. O
`.json` é copiado de `millenaire/_templates/_template_building.json` e
editado.

## Início rápido 2 — Criar uma cultura completamente nova

```
millenaire-custom/
└── viking-pack/                          # o teu sub-mod
    ├── cultures/
    │   └── viking/                       # o id da cultura (deve corresponder a [a-z0-9]+)
    │       ├── culture.json              # obrigatório: name, language, biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # obrigatório: pelo menos uma namelist
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # obrigatório: pelo menos um tipo de aldeão
    │       ├── villages/
    │       │   └── basic_village.json    # obrigatório: pelo menos um tipo de aldeia
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # obrigatório: pelo menos uma câmara municipal
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # obrigatório: loja para a câmara municipal
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # obrigatório, pelo menos en_us
```

## Sem hot reload

O conteúdo é carregado uma única vez, no arranque do servidor. Após
editares qualquer ficheiro em `millenaire-custom/`, **reinicia o
servidor** para que as alterações tenham efeito.

## Importar um addon de Millénaire 1.12

Coloca o pack antigo como subpasta de `millenaire-custom/` (p. ex.
`millenaire-custom/MyOldPack/`). No próximo arranque o Millénaire
converte-o automaticamente para `MyOldPack_converted/`. A pasta vizinha
`_converted/` torna-se o sub-mod em tempo de execução; o original é
preservado como fonte.

## Apontadores

- **Guia completo**: `millenaire/_docs/content-guide.en.md` (implantado
  a cada arranque do mod)
- **Modelos** (esqueletos para copiar): `millenaire/_templates/`
- **Catálogos de referência** (IDs de goals, pontos especiais, ...):
  `millenaire/_reference/`
- **Repositório GitHub**: <https://github.com/Kinniken/Millenaire>
