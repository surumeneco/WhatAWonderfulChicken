# WWC 2.0.0 最終確認（チキントラップの消滅）

## 確定した運用契約

- チキントラップで戦闘中の1騎（特殊Skeleton 1体とその騎乗Wonderful Chicken 1体）は、夜明けの終了時に **1回** のWind Charge標準爆風を発生させ、両Entityを除去する。
- 待機中の特殊Skeletonなど単独で残っている遭遇Entityは、その単位で1回の標準爆風を使う。
- Wind Chargeの飛翔を可視化している`ItemDisplay`は演出用の追随表示に過ぎず、単体では爆風を発生させない。
- 騎手のみ死亡して解放されたWonderful Chickenはトラップの一員ではなく、夜明けに除去しない。
- 夜明け後にチャンクが読み込まれた場合は、期限切れEntityを除去する。既に終わった遭遇をオフスクリーンで後から爆発させる必要はない。
- 爆風はTNT等の通常の破壊爆発ではなく、`WindCharge.explode()` によるMinecraft標準の風の爆風を使用する。独自Particle、通常の`World.createExplosion`による代用は行わない。
- 起動時の標準Wind Charge爆風は従来どおり1回。直撃ダメージと風の爆風によるノックバックは区別する。

根拠: Minecraft Java Edition 1.21公式リリースノート（Wind Burstが周辺Entityをノックバックし、直接衝突時に小さなダメージを与える）、Paper API `AbstractWindCharge.explode()`。

## 最終検証で区別すること

- この仕様変更は生成方式・能力・戦闘AIの変更を伴わない。
- 夜明けで「全Entity」を除去することと、「1体ごと」に爆風を出すことは別。騎手と騎乗Chickenのグループを先に取り除き1回だけ風の爆風を出す。
- 性格のFounder分布は確定N(0.5,0.10)/しきい値を維持し、検証結果なしに新たなバランスパラメータ変更はしない。多世代WGL繁殖の確認は別途実施する。
