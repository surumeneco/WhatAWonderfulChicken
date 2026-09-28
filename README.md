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
build/libs/WhatAWonderfulChicken-1.1.1.jar
```

生成されたjarをPaperサーバーの `plugins/` に配置して起動してください。初回起動時に `plugins/WhatAWonderfulChicken/` 以下へconfigと言語ファイルを自動生成します。

## 主な機能

- 自然スポーンChicken / 新規チャンク初期配置Chickenを5%で `wonderful_chicken` 化
- 9種類の独立個体値と野生限定の規格外能力
- 性格12種類・特性11種類と、それぞれ独立した遺伝因子2個の継承・突然変異
- 性格による移動・飛行・体格などへの実効値補正、固有特性による支援・防御・特殊産卵
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

Carpet / Shulker Boxの外観は、Geyser互換性を優先して不可視ArmorStandの頭装備として追従表示する初期実装です。位置・倍率、Chicken騎乗時のJE/BE入力差、乗騎ハート表示などは実サーバーで確認して調整する前提です。
## 1.1.0：性格・特性

個体にはそれぞれ性格1種類、特性1種類が発現します。性格用と特性用に独立した2因子の遺伝情報を持ち、繁殖で両親から因子を受け継ぎます。性格は基礎個体値を変えずに実効能力へ補正を加え、特性は騎乗支援、耐性、繁殖、産卵、警戒などの効果を持ちます。

旧バージョンの個体は、初回読込時に遺伝情報を追加して永続保存します。血統IDから両親の実個体を参照できる場合は、その遺伝因子を元に繁殖時と同じ方法で決定し、復元できない場合は自然スポーンと同様の一様ランダムで補完します。旧血統スナップショット自体には因子情報がないため、死亡済み・未ロードの親を推測して埋めることはありません。

性格は個体情報の先頭、特性は血統情報の先頭に表示され、GUIをクリックした場合や `/wwc info` にも反映されます。親の性格と特性も表示し、旧血統で復元できない情報は「不明」とします。能力補正率の数値は性格の説明文へ表示しません。

## 1.1.1：旧config.ymlとの互換性修正

1.0.0以前の設定ファイルに存在しない性格・特性・突然変異の追加項目を、JAR内のデフォルト値で補完して起動できるように修正しました。従来の `plugins/WhatAWonderfulChicken/config.yml` は削除せず、そのまま利用できます。明示的に設定した不正値は従来どおりエラーとなります。

1.1.0で起動時に `Invalid configuration` が発生した場合は、ワールドと設定ファイルをバックアップのうえ、**旧1.1.0 JARを撤去**してこの1.1.1 JARのみを `plugins/` に配置し、サーバーを完全再起動してください。
