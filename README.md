# What a Wonderful Chicken

Paper 26.2 向けの、育成・繁殖可能な騎乗用巨大Chickenプラグインです。

## 要件

- Paper 26.2
- Java 25
- WonderfulGenomeLib

WonderfulGenomeLibは2.0.0から必須依存です。WWCへWGLクラスをshade / bundleせず、Paper上では別Pluginとして読み込みます。個体データは引き続きChicken EntityのPDCへ保存します。

## ビルド

開発時はWonderfulGenomeLibをWWCの隣へcheckoutします。

```text
workspace/
├─ WonderfulGenomeLib/
└─ WhatAWonderfulChicken/
```

WWCはGradle Composite Buildで隣接WGLを `co.surumene:wgl-plugin:0.1.0-SNAPSHOT` へ置換します。

```text
gradle build
```

生成物:

```text
build/libs/WhatAWonderfulChicken-2.0.0.jar
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
/wwc genome get <entity selector>
/wwc summon genome <text|bits|hex|dna> <haplotypeA> [haplotypeB]
/wwc summon offspring parent <sourceA> parent <sourceB>
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

### 2.0.0 Genome管理コマンド

`/wwc genome get <entity selector>` は保存済み二倍体Genomeの染色体ごとのビット長、
A/B各半数体のraw bits、WGL型付きParent Source tokenを表示します。閲覧は `wwc.command.genome` 権限（初期設定はOP）で制御します。

`/wwc summon genome <text|bits|hex|dna> <haplotypeA> [haplotypeB]` は
Genomeを**そのまま注入**します。6染色体はカンマ区切りで明示するか、
未指定境界を `9:8:7:6:5:4` で配分します。B省略時はAを複製します。
この直接召喚にはChicken Backbone互換判定や繁殖処理を要求しません。

`/wwc summon offspring parent <sourceA> parent <sourceB>` は
WGLの型付きParent Sourceを2つ受け取って繁殖させます。
sourceは `diploid:<base64url>` または `gamete:<base64url>` です。
このバイト列はWGL `encodeParentSource(...)` の結果をBase64 URL-safe（paddingなし）
で符号化したものです。`genome get` が出力する `Source:` は
`diploid:`形式であり、そのままコピーできます。
`gamete:` は既に確定した半数体をWGLの `Gamete` として符号化します。
二倍体・gamete混在も可。双方のChicken Backbone適合判定後にWGL繁殖を実行し、
失敗なら召喚しません。直接召喚・管理者繁殖の新規個体は祖先不明の管理個体として生成します。
長いGenome文字列はゲーム内チャットの入力上限に収まらない場合があります。

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
## 1.1.0（リリース予定）：性格・特性と設定互換性

個体には性格1種類と特性1種類が発現します。性格用・特性用にそれぞれ独立した2因子の遺伝情報を持ち、繁殖で両親から因子を受け継ぎます。性格は基礎個体値を変えずに実効能力へ補正を加え、特性は騎乗支援、耐性、繁殖、産卵、警戒などの効果を持ちます。

旧バージョンの個体には初回読込時に遺伝情報を追加・永続化します。血統IDで両親の実個体を参照できる場合はその遺伝因子を用い、復元できない場合は一様ランダムで補完します。性格は個体情報、特性は血統情報に表示され、GUIや `/wwc info` でも確認できます。

### 設定・翻訳ファイルの後方互換性

旧 `config.yml` にない設定は同梱デフォルトで検証・利用できます。さらに起動時と `/wwc reload` 時に、`config.yml`、`lang/ja_jp.yml`、`lang/en_us.yml` の不足キーを追記保存します。既存の設定値・独自キー・カスタム翻訳は変更せず、不正な明示値は従来どおりエラーとします。不足がない場合は再保存しません。

設定ファイルを削除する必要はありませんが、YAMLの再保存によって引用符や空白などの書式が変わる場合があります。更新前にファイルとワールドをバックアップし、旧JARを撤去してから新しいJARのみを配置して完全再起動してください。

### バージョン採番

`main`への反映・リリースが完了するまでは、`develop`上の修正や機能追加もすべて同じ次期リリース番号を使用します。今回のGenome移行を含む未リリース変更はすべて`2.0.0`に含め、次の番号は2.0.0リリース後の変更で採番します。
