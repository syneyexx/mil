# 欢迎来到 `millenaire-custom/`

这个文件夹是 **你的**。Millénaire 永远不会覆盖或删除你放在这里的
任何内容，即使在模组更新时也是如此。用它来添加新的建筑、村民、
文化、语言、任务或任何其他内容。

同级文件夹 `millenaire/` 归模组所有，并在每次版本变更时重新部署。
请阅读它作为参考；不要直接编辑它。

## 子模组模型

自定义内容以 **子模组** 形式发布：`millenaire-custom/` 的每一个直接
子目录就是一个子模组，拥有自己的 `cultures/`、`languages/`、
`gathering_type/` 和/或 `quests/` 子树。多个子模组可以共存；
它们的贡献会确定性地叠加（对于 REPLACE 文件，按字母顺序排在第一的
子模组获胜，其他子模组进行累加贡献）。

子模组目录名称必须匹配 `[A-Za-z0-9_-]+`。名称 `cultures`、
`languages`、`gathering_type`、`quests` 和 `exports` 在顶层是保留的
—— 请 **不要** 直接将内容放在
`millenaire-custom/cultures/...` 下。模组将拒绝加载此类文件
并在启动时记录 WARN 日志。

## 快速入门 1 —— 给诺曼文化添加一个建筑

```
millenaire-custom/
└── my-norman-tweaks/             # 任意名称 [A-Za-z0-9_-]+ —— 你的子模组
    └── cultures/
        └── norman/
            └── buildings/
                └── houses/
                    ├── my_house.json    # 计划元数据（从 _templates/ 复制）
                    └── my_house.nbt     # 结构模板（从游戏内导出）
```

`.nbt` 文件由游戏内的 **导入台（Import Table）** 方块生成。`.json` 文件
从 `millenaire/_templates/_template_building.json` 复制并编辑。

## 快速入门 2 —— 创建一个全新的文化

```
millenaire-custom/
└── viking-pack/                          # 你的子模组
    ├── cultures/
    │   └── viking/                       # 文化 id（必须匹配 [a-z0-9]+）
    │       ├── culture.json              # 必需：name、language、biomes
    │       ├── namelists/
    │       │   ├── men_names.txt         # 必需：至少一个名字列表
    │       │   ├── women_names.txt
    │       │   └── family_names.txt
    │       ├── villagers/
    │       │   └── leaders/
    │       │       └── leader.json       # 必需：至少一个村民类型
    │       ├── villages/
    │       │   └── basic_village.json    # 必需：至少一个村庄类型
    │       ├── buildings/
    │       │   └── townhalls/
    │       │       ├── townhall.json     # 必需：至少一个城镇厅
    │       │       └── townhall_a_0.nbt
    │       └── shops/
    │           └── townhall.json         # 必需：城镇厅的商店
    └── languages/
        └── en_us/
            └── viking_sentences.txt      # 强制：至少需要 en_us
```

## 不支持热重载

内容只在服务器启动时加载一次。在编辑 `millenaire-custom/` 下的
任何文件后，**重启服务器** 才能使更改生效。

## 导入 Millénaire 1.12 插件包

将旧版插件包作为 `millenaire-custom/` 的子文件夹放入（例如
`millenaire-custom/MyOldPack/`）。下次启动时 Millénaire 会自动将
它转换为 `MyOldPack_converted/`。`_converted/` 同级文件夹将成为
运行时子模组；原始文件夹则作为源被保留。

## 参考资料

- **完整指南**：`millenaire/_docs/content-guide.en.md`（每次模组启动时
  部署）
- **模板**（可复制的骨架）：`millenaire/_templates/`
- **参考目录**（目标 ID、特殊点等）：`millenaire/_reference/`
- **GitHub 仓库**：<https://github.com/Kinniken/Millenaire>
