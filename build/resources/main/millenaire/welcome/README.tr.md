# `millenaire-custom/` klasörüne hoş geldin

Bu klasör **SENİN**. Millénaire, mod güncellense bile buraya
koyduğun hiçbir şeyi asla üzerine yazmaz veya silmez. Yeni binalar,
köylüler, kültürler, diller, görevler veya başka herhangi bir içerik
eklemek için kullan.

Kardeş klasör `millenaire/` moda aittir ve her sürüm değişikliğinde
yeniden dağıtılır. Referans için oku; doğrudan düzenleme.

## Alt-mod modeli

Özel içerik **alt-mod** olarak dağıtılır: `millenaire-custom/` içindeki
her doğrudan alt dizin, kendi `cultures/`, `languages/`,
`gathering_type/` ve/veya `quests/` alt ağaçlarına sahip bir
alt-moddur. Birden fazla alt-mod birlikte var olabilir; katkıları
deterministik olarak üst üste bindirilir (REPLACE dosyaları için
alfabetik olarak ilk gelen alt-mod kazanır, diğerleri toplamalı
olarak katkıda bulunur).

Alt-mod dizin adları `[A-Za-z0-9_-]+` ile eşleşmelidir. `cultures`,
`languages`, `gathering_type`, `quests` ve `exports` adları en üst
seviyede ayrılmıştır — içeriği doğrudan
`millenaire-custom/cultures/...` altına koyma. Mod, böyle dosyaları
yüklemeyi reddedecek ve başlangıçta WARN günlüğü yazacaktır.

## Hızlı başlangıç 1 — Norman kültürüne bir bina ekle

```
millenaire-custom/
└── my-norman-tweaks/             # herhangi bir ad [A-Za-z0-9_-]+ — senin alt-modun
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # plan meta verileri (_templates/'den kopyala)
                    └── my_house.nbt     # yapı şablonu (oyun içinden dışa aktar)
```

`.nbt`, oyun içinde **Import Table** bloğu ile üretilir. `.json`,
`millenaire/_templates/_template_building.json` dosyasından
kopyalanır ve düzenlenir.

## Hızlı başlangıç 2 — Yepyeni bir kültür oluştur

```
millenaire-custom/
└── viking-pack/                          # senin alt-modun
    ├── cultures/
    │   └── viking/                       # kültür id'si ([a-z0-9]+ ile eşleşmeli)
    │       ├── culture.json              # gerekli: name, language, biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # gerekli: en az bir isim listesi
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # gerekli: en az bir köylü türü
    │       ├── villages/
    │       │   └── basic_village.json    # gerekli: en az bir köy türü
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # gerekli: en az bir belediye binası
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # gerekli: belediye binası için dükkan
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # zorunlu, en az en_us
```

## Hot reload yok

İçerik, sunucu başlangıcında bir kez yüklenir. `millenaire-custom/`
altındaki herhangi bir dosyayı düzenledikten sonra, değişikliklerin
geçerli olması için **sunucuyu yeniden başlat**.

## Millénaire 1.12 eklentisini içe aktarma

Eski paketi `millenaire-custom/` altına bir alt klasör olarak bırak
(örn. `millenaire-custom/MyOldPack/`). Bir sonraki başlatmada
Millénaire bunu otomatik olarak `MyOldPack_converted/` haline
dönüştürür. `_converted/` kardeş klasör çalışma zamanı alt-modu olur;
orijinali kaynak olarak korunur.

## İşaretçiler

- **Tam kılavuz**: `millenaire/_docs/content-guide.en.md` (her mod
  başlangıcında dağıtılır)
- **Şablonlar** (kopyalanacak iskeletler): `millenaire/_templates/`
- **Referans katalogları** (goal ID'leri, özel noktalar, ...):
  `millenaire/_reference/`
- **GitHub deposu**: <https://github.com/Kinniken/Millenaire>
