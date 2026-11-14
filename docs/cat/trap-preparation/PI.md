---
kind: pi
id: wwc.trap.pi.event
cat_version: '0.3'
status: draft
process: wwc.trap
source_refs:
  - https://drive.google.com/file/d/1A075R-gDEQW2-SO6DKEbrzNatPpuxVj8/view
  - user-chat:2026-11-14/trap-waiting-mob-request
refs: []
semantic_contract: markdown-v2
language: ja
---

# PI: wwc.trap.pi.event

## 境界
| 相手Process | 方向 |
| --- | --- |
| minecraft.world | 双方向 |

## 項目
| ID | 方向 | Domain | 存在 |
| --- | --- | --- | --- |
| input.is_entity_attack | 受信 | boolean | 必須 |
| input.is_player_attack | 受信 | boolean | 必須 |
| input.genomes_ready | 受信 | boolean | 必須 |
| input.player_near | 受信 | boolean | 必須 |
| input.grace_passed | 受信 | boolean | 必須 |

## 操作
| ID | 方向 | 起点 |
| --- | --- | --- |
| spawn | 受付 | spawn |
| damage | 受付 | damage |
| synthesis | 受付 | synthesis |
| proximity | 受付 | proximity |

## 制約
| 式 |
| --- |
| `input.is_player_attack == false or input.is_entity_attack == true` |
