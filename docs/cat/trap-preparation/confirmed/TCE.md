---
kind: tce
id: wwc.trap.tce.lifecycle
cat_version: '0.3'
status: confirmed
process: wwc.trap
source_refs:
  - https://drive.google.com/file/d/1A075R-gDEQW2-SO6DKEbrzNatPpuxVj8/view
  - user-chat:2026-11-14/trap-waiting-mob-request
refs: 
  - wwc.trap.pi.event
semantic_contract: markdown-v2
language: ja
decision_ref: user-chat:2026-11-13T21:18:12Z
---

# TCE: wwc.trap.tce.lifecycle

網羅要求: closed

## 振る舞い
| ID | 起点 | 条件 | 結果 | 効果 |
| --- | --- | --- | --- | --- |
| appear | spawn | `true` | 1 | `state.prepared = false; action.prepare_genomes = true` |
| damage.pending.entity | damage | `state.prepared == false and input.is_entity_attack == true` | 1 | `action.cancel_damage = true; action.totem_feedback = true; action.activate_trap = false` |
| damage.pending.other | damage | `state.prepared == false and input.is_entity_attack == false` | 1 | `action.cancel_damage = true; action.totem_feedback = false; action.activate_trap = false` |
| damage.ready.player | damage | `state.prepared == true and input.is_entity_attack == true and input.is_player_attack == true` | 1 | `action.cancel_damage = true; action.activate_trap = true` |
| damage.ready.other | damage | `state.prepared == true and (input.is_entity_attack == false or input.is_player_attack == false)` | 1 | `action.cancel_damage = false; action.activate_trap = false` |
| synthesis.success | synthesis | `input.genomes_ready == true` | 1 | `state.prepared = true` |
| synthesis.failed | synthesis | `input.genomes_ready == false` | 1 | `state.prepared = false; action.prepare_genomes = true` |
| proximity.trigger | proximity | `state.prepared == true and input.player_near == true and input.grace_passed == true` | 1 | `action.activate_trap = true` |
| proximity.no_trigger | proximity | `state.prepared == false or input.player_near == false or input.grace_passed == false` | 1 | `action.activate_trap = false` |
