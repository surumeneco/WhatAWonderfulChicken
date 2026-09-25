# What a Wonderful Chicken

Paper 26.2 向けの、育成・繁殖可能な騎乗用巨大Chickenプラグインです。

## 要件

- Paper 26.2
- Java 25

追加の前提プラグインや外部DBはありません。個体データはChicken EntityのPDCへ保存されるため、プラグインjarとワールドデータがあれば個体情報を引き継げます。

## ビルド

Windows (PowerShell / cmd):

```text
gradlew.bat build
```

Linux / macOS:

```text
./gradlew build
```

生成物:

```text
build/libs/WhatAWonderfulChicken-0.2.0-SNAPSHOT.jar
```

生成されたjarをPaperサーバーの `plugins/` に配置して起動してください。初回起動時に `plugins/WhatAWonderfulChicken/` 以下へconfigと言語ファイルを自動生成します。

## 主な機能

- 自然スポーンChicken / 新規チャンク初期配置Chickenを5%で `wonderful_chicken` 化
- 9種類の独立個体値と野生限定の規格外能力
- デカ鳥同士の繁殖・直接遺伝・ガウス遺伝・血統情報
- Carpetを騎乗装備として使用
- 地上走行、ジャンプ、羽ばたき飛行、スタミナ、緩降下
- 2ブロック下の全色羊毛を街道路盤として速度補正
- Shulker Boxを27スロット荷物として装備
- プレイヤーのHEADスロットへ装備可能なItemStackを頭防具として装備
- 放浪 / 待機 / 追従
- `/wwc` / `/whatawonderfulchicken` 管理コマンド
- PDCによるワールド内永続化

## コマンド

```text
/wwc summon [x y z] [<wwc-data>] [<vanilla-nbt>]
/wwc info [<entity selector>]
/wwc modify <entity selector> <set|add> <field> <value>
/wwc config get <path>
/wwc config set <path> <value>
/wwc config list [path]
/wwc config reset <path>
/wwc config reset numeric [path]
/wwc config reset other [path]
/wwc reload
```

`info` と `modify` はPaper 26.2のBrigadier Entity Selector引数を使用するため、`@e[...]`、UUID指定、クライアント標準のEntity候補補完を利用できます。

### summon のWWCデータ例

```text
/wwc summon ~ ~ ~ {stats:{size:2.5,ground_speed:18,stamina:15},equipment:{carpet:"red_carpet",shulker_box:"blue_shulker_box",head:"diamond_helmet"},behavior:{mode:"wait"}}
```

WWCデータを省略し、バニラNBTだけを指定したい場合は、WWC固有キーを含まないCompoundを1つ指定するとNBTとして扱います。

```text
/wwc summon ~ ~ ~ {CustomName:'"Bird"',Invulnerable:1b}
```

WWCデータとバニラNBTを両方指定した場合、能力Attribute等の競合値はWWC固有値が優先されます。

## 現時点の実機確認対象

0.2.0-SNAPSHOTではJava版の不可視ArmorStand表示を維持しつつ、Geyser 2.11系では統合版にCarpet / Shulker BoxをFallingBlockとして表示します。統合版の騎乗位置はGeyserのSEAT_OFFSETをChicken Scaleに合わせて補正します。BE側の表示位置・倍率は実サーバーで追加調整する前提です。