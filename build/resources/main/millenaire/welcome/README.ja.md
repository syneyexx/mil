# `millenaire-custom/` へようこそ

このフォルダは **あなたのもの** です。Millénaire はここに置かれたものを
上書きしたり削除したりすることは決してありません。mod が更新されても
同じです。新しい建物、村人、文化、言語、クエスト、その他のコンテンツを
追加するために使用してください。

兄弟フォルダ `millenaire/` は mod が所有しており、バージョンが変わる
たびに再配置されます。参考として読んでください。直接編集しないで
ください。

## サブ mod モデル

カスタムコンテンツは **サブ mod** として配布されます：
`millenaire-custom/` の各直下サブディレクトリが 1 つのサブ mod であり、
独自の `cultures/`、`languages/`、`gathering_type/` および/または
`quests/` サブツリーを持ちます。複数のサブ mod が共存可能であり、
その貢献は決定論的に重ね合わされます（REPLACE ファイルではアルファベット
順で最初のサブ mod が優先され、他のサブ mod は加算的に貢献します）。

サブ mod ディレクトリ名は `[A-Za-z0-9_-]+` に一致しなければなりません。
`cultures`、`languages`、`gathering_type`、`quests`、`exports` という
名前はトップレベルで予約されています — `millenaire-custom/cultures/...`
の直下にコンテンツを置か **ない** でください。mod はそのようなファイルを
ロードすることを拒否し、起動時に WARN ログを記録します。

## クイックスタート 1 — ノルマン文化に建物を追加する

```
millenaire-custom/
└── my-norman-tweaks/             # 任意の名前 [A-Za-z0-9_-]+ — あなたのサブ mod
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # プランのメタデータ（_templates/ からコピー）
                    └── my_house.nbt     # 構造テンプレート（ゲーム内からエクスポート）
```

`.nbt` はゲーム内で **Import Table** ブロックを使って生成されます。
`.json` は `millenaire/_templates/_template_building.json` から
コピーして編集します。

## クイックスタート 2 — まったく新しい文化を作成する

```
millenaire-custom/
└── viking-pack/                          # あなたのサブ mod
    ├── cultures/
    │   └── viking/                       # 文化 id（[a-z0-9]+ に一致する必要あり）
    │       ├── culture.json              # 必須: name、language、biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # 必須: 少なくとも 1 つの namelist
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # 必須: 少なくとも 1 つの村人タイプ
    │       ├── villages/
    │       │   └── basic_village.json    # 必須: 少なくとも 1 つの村タイプ
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # 必須: 少なくとも 1 つの町役場
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # 必須: 町役場のショップ
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # 必須、最低でも en_us
```

## ホットリロードなし

コンテンツはサーバー起動時に一度だけロードされます。
`millenaire-custom/` 配下のファイルを編集した後は、変更を反映させる
ために **サーバーを再起動してください**。

## Millénaire 1.12 のアドオンをインポートする

レガシーパックを `millenaire-custom/` のサブフォルダとして配置します
（例：`millenaire-custom/MyOldPack/`）。次回起動時に Millénaire はそれを
自動的に `MyOldPack_converted/` に変換します。`_converted/` 兄弟フォルダ
がランタイムのサブ mod になります。元のフォルダはソースとして保持
されます。

## 参照先

- **完全ガイド**: `millenaire/_docs/content-guide.en.md`（mod 起動の
  たびに配置されます）
- **テンプレート**（コピー用スケルトン）: `millenaire/_templates/`
- **リファレンスカタログ**（ゴール ID、特殊ポイントなど）:
  `millenaire/_reference/`
- **GitHub リポジトリ**: <https://github.com/Kinniken/Millenaire>
