# What a Wonderful Chicken

## 2.1.0 リリース

- Paper 26.2 / Java 25 向けの次期リリース。`WonderfulGenomeLib 1.0.1` と新たに `WonderfulCoreLibrary 0.1.0` を**別々の必須プラグイン**として導入します。
- 共通化したポーション更新判断で、既存の無限時間効果（Paperの残時間 `-1`）を正しく処理します。同等以上の無限時間効果を上書きせず、より強い効果の優先と既存の更新閾値は維持します。
- WCLへ共通化した処理とWWC固有のゲーム処理を分離し、保存形式・Genome処理・特性パラメータを維持します。JE/BEと実サーバーの動作は自動テストと別に確認します。

### 以前のリリース：2.0.1

## 2.0.1 Config整備

- 新しい `founder-target.*` 設定で自然FounderとチキントラップFounderの能力Target分布を別々に指定できます。
- `genome-profile.decoder.*`、`genome-profile.synthesizer.*`、`genome-profile.breeding-policy.*` の種固有パラメータを外部設定化しました。reload後の新規生成・繁殖から反映し、既存Phenotype Snapshotは維持します。
- 旧1.xの無効な生成・繁殖設定を配布設定から廃止しました。現在使われている遺伝情報の移行用設定は維持しています。詳細は [設定の互換性](docs/config-compatibility.md) を参照してください。

## 2.0.1 パッチ修正

- 不変Genome・Phenotype・Backboneと保存済み外部参照が一致する装備・荷物・runtime保存では、WGL Marker再計算とGenome/Snapshot再エンコードを省略します。
- 新規個体、Genome更新、Profile変更、旧個体移行、不正/欠損参照は完全再計算し、外部ファイルのcopy-on-writeを維持します。
- WGL 1.0.1が実行時必須です。本番環境でのTPS/MSPT改善率は未測定です。

Paper 26.2 向けの、育成・繁殖可能な騎乗用巨大Chickenプラグインです。

## 要件

- Paper 26.2
- Java 25
- WonderfulGenomeLib 1.0.1
- WonderfulCoreLibrary 0.1.0

WonderfulGenomeLibは2.0.0から、WonderfulCoreLibraryは2.1.0から必須依存です。WWCへWGL/WCLクラスをshade / bundleせず、Paper上ではそれぞれ別Pluginとして読み込みます。個体データは引き続きChicken EntityのPDCへ保存します。

## ビルド

開発時はWonderfulGenomeLibとWonderfulCoreLibraryをWWCの隣へcheckoutします。

```text
workspace/
├─ WonderfulGenomeLib/
├─ WonderfulCoreLibrary/
└─ WhatAWonderfulChicken/
```

WWCはGradle Composite Buildで隣接WGL (`co.surumene:wgl-plugin:1.0.1`) とWCL (`co.surumene:wcl-plugin:0.1.0`) を参照します。

```text
gradle build
```

生成物:

```text
build/libs/WhatAWonderfulChicken-2.1.0.jar
```

生成されたjarをPaperサーバーの `plugins/` に配置して起動してください。初回起動時に `plugins/WhatAWonderfulChicken/` 以下へconfigと言語ファイルを自動生成します。

## 主な機能（2.0.0）

- 自然スポーンChicken / 新規チャンク初期配置Chickenを設定確率（既定5%）でWonderful Chicken化
- 9能力のGenome（通常能力Bと規格外寄与E）をWGLで生成・継承。自然Founderの能力Targetは0.0～0.75、チキントラップFounderは0.5～1.5
- 6性格因子の連続Scoreから性格を判定。性格は遺伝的な基礎能力を変えず、現在実効能力のみを補正
- Genomeから発現する特性（なし・弱発現1種類・弱発現2種類・強発現1種類）と固有効果
- WGL相同組換え・変異による繁殖、血統Snapshot、旧個体のGenome移行
- 成体化時を起点とした生物学的日齢、成熟・全盛・老化、発症時期を持つ潜在怪我
- 新月かつ晴天のゲーム内時刻18000で抽選するチキントラップ（発生後、騎射Skeleton 2騎）。夜明け23000に残存遭遇Entityを消去
- Carpet騎乗装備、Shulker Box荷物、頭装備
- 地上走行・ジャンプ・羽ばたき飛行・スタミナ・緩降下、羊毛路面速度補正
- 放浪・待機・追従、個体情報GUI、管理者向け詳細情報・Genomeコマンド
- PDCによるGenome・Phenotype Snapshot・血統・成体化時刻の保存

**注意:** 2.0.0では旧WWCの「性格・特性の2因子直接継承」「能力値を直接抽選するガウス繁殖」は2.0.0のGenome個体の仕組みではありません。

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

### 2.0.0 Genome管理

`/wwc genome get` はGenomeの染色体長・bit列と親ソースを表示します。ゲーム内の表示行をクリックすると全文をコピーできます。

`/wwc summon genome` は任意Genomeの直接注入です。Bを省略するとAを複製します。染色体境界はカンマで指定でき、省略箇所は `9:8:7:6:5:4` の比率で分割します。

`/wwc summon offspring` のsourceには、既存Wonderful Chickenを指す `chicken_<UUID>`、またはWGLPをBase64URL形式にした `wglp_<payload>` を指定します。後者では完成済み二倍体とgameteを区別できます。Backbone非互換・繁殖失敗では召喚しません。

`genome get` は `wwc.command.genome` 権限、召喚は `wwc.command.summon` 権限が必要です。

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
## 2.0.0 の後方互換性と設定

1.x個体は初回ロード時に旧能力・性格・特性をTarget PhenotypeとしてWGL Synthesizerへ渡し、GenomeとPhenotype Snapshotを追加します。既存個体の表現型・血統履歴を保持し、過去の親Genomeや繁殖過程を推測・再現しません。旧個体の成体日齢は移行時に0から開始します。

`config.yml` と言語ファイルの不足キーは起動時・再読み込み時に補完します。既存の明示設定・カスタム翻訳を優先し、不正な設定値は拒否します。1.x由来の自然スポーン能力分布・直接遺伝設定は、WGL Founderの分布やGenomeの組換え率を制御する設定ではありません。詳細は [config互換性資料](docs/config-compatibility.md) を参照してください。

設定・ワールドのバックアップを取った上で、旧JARを撤去し、WGLとWWCの対応JARを配置して再起動してください。YAMLの追記保存に伴い、空白や引用符等の書式が変わる場合があります。

2.1.0のインストール時は、対応するWonderful Genome Lib 1.0.1とWonderfulCoreLibrary 0.1.0を先に配置してください。Paper・Geyser/BE固有の挙動は導入環境で確認してください。
