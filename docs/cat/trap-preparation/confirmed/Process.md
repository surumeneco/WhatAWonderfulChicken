---
kind: process
id: wwc.trap
cat_version: '0.3'
status: confirmed
process: wwc.trap
source_refs:
  - https://drive.google.com/file/d/1A075R-gDEQW2-SO6DKEbrzNatPpuxVj8/view
  - user-chat:2026-11-14/trap-waiting-mob-request
refs: []
semantic_contract: markdown-v2
language: ja
decision_ref: user-chat:2026-11-13T21:18:12Z
---

# Process: wwc.trap

## 契約

閉世界: 真

待機Mobに対して非同期ゲノム準備を先行させ、準備完了前に起動・被ダメージを許可しない。

## 境界
| PI |
| --- |
| wwc.trap.pi.event |

## 仕様状態
| ID | Domain |
| --- | --- |
| state.prepared | boolean |

## 観測能力
| ID | Source | Domain |
| --- | --- | --- |
| observation.bait_valid | Minecraft world | boolean |

## 作用能力
| ID | Target | Domain |
| --- | --- | --- |
| action.cancel_damage | Bukkit damage event | boolean |
| action.totem_feedback | Minecraft sound/particles | boolean |
| action.activate_trap | World spawn/pair | boolean |
| action.prepare_genomes | asynchronous founder | boolean |

## 起点
| ID | Source |
| --- | --- |
| spawn | wwc.trap.pi.event/spawn |
| damage | wwc.trap.pi.event/damage |
| synthesis | wwc.trap.pi.event/synthesis |
| proximity | wwc.trap.pi.event/proximity |
