# WWC 2.0.1 — periodic work migration and verification (2026-10-17)

Status: partial changes merged into `develop`; live Paper 26.2 validation and CPU/MSPT comparison outstanding.

## Source of truth and release boundary

- Approved cross-plugin design: Google Drive `forGPT/XPlayServer/OriginalPlugins/共通基盤/WWC・WWW_必要時更新・相互軽量化計画.md`.
- This file tracks **WWC-only** implementation and experiments. WWW is developed independently and WGL is not changed.
- All modifications in `feature/*` → CI → `develop`. `main` and WWC 2.0.1 release PR #56 remain unchanged until real-server testing.
- No source-level count or CI pass is evidence of a production MSPT improvement.

## Implemented

| Change | Revision | Validation | Remaining |
| --- | --- | --- | --- |
| Founder synthesis work queue is no longer polled globally every server tick. Requests and completion callbacks drain a bounded queue. | PR #66, merge `bf59394f8133f0eda3d642064954c6da7c3f7fb4` | Java 25 CI passed; tests cover empty, capacity-limited, rejected, canceled, and invalidated work | Chunk reload, large natural spawn bursts, queue saturation, shutdown/reload and main-thread affinity on live Paper |
| Head equipment changes are also captured from Paper `EntityEquipmentChangedEvent`. The previous 200-tick integrity check **has not been removed**. Event captures vs periodic-only corrections are counted separately. | PR #67, merge `926a73fc9054ef4f51b02f21fe337b2201aa0495` | Java 25 CI passed; telemetry tests cover corrections and reset | Verify Paper delivery for own writes, external plugin writes, dispenser/pickup/durability, death, chunk lifecycle and ClickMobs, then benchmark before reducing fallback |

### Founder queue comparison

For an *idle* server, prior scheduling invoked `NaturalChickenGenerationCoordinator.tick()` once per tick (20 calls/s at nominal 20 TPS), even when the queue was empty. PR #66 removes that recurring task completely: no enqueue or completed worker task means zero dispatcher wakeups. A worker completion can now wake multiple pending submissions; total CPU time under saturated load **must** be measured before claiming superiority. Existing 16-slot executor queue and 128-pending job limit are unchanged. All Bukkit Entity/PDC changes stay on the server thread.

### Head equipment coverage diagnostics

After PR #67, the following *sparse* line is emitted by `IntegrityController` every 6000 server ticks **only if activity was observed**:

```text
WWC head equipment coverage: event HEAD notifications=N, event corrections=M, periodic-only corrections=K
```

- `event HEAD notifications` — HEAD slot change events for live, registered Wonderful Chickens.
- `event corrections` — such an event actually found a different saved head item and persisted it.
- `periodic-only corrections` — the retained 200-tick integrity audit found an equipment change that had not been captured by the event.
- A zero count of periodic-only corrections in a quiet test **does not prove** full event delivery. Explicitly exercise external-plugin edits and all relevant lifecycle cases, then compare under representative entity counts.

## Not yet changed; guarded work

| Component | Present logic | Competing approaches and adoption condition |
| --- | --- | --- |
| `IntegrityController` 200-tick loaded-chicken scan | Head/PDC and age/attribute rechecks; bounded batches | Event-driven head capture plus age deadlines vs existing check; never reduce fallback before tests show event coverage and acceptable external-edit correction latency |
| `IntegrityController` 200-tick late-PDC candidates and 6000-tick all-chunk audit | Detects PDC attached after spawn and eventless external entity insertion | Entity/Chunk lifecycle + delayed candidate checks; low-frequency fallback for edits that expose no event. Do not claim arbitrary PDC writes generate a Paper event |
| `DisplayService.tick` every tick | Follows moving Chicken with ArmorStand equipment visuals | `EntityMoveEvent` + teleport/born/died/shape updates vs active-display tick; compare *total* movement event cost with display count 0, 50, 200 and 1000 |
| `RidingController.run` every tick | Ride physics, active stamina recovery, eventless mount resync | Preserve per-tick active ride physics. Consider deadline recovery, mount events and active-only polling; verify BE/JE and contact/water transitions |
| `FollowController.run` every 5 ticks | Target movement, teleport, WAIT/FOLLOW, goals | MobGoal/target events vs active membership polling; consider interruptions and target teleport. Do not sacrifice AI behavior for lower scheduler count |
| `TraitController.run` every 10 ticks | Active trait effect refresh and nearby candidate detection | Potion expiration deadlines, movement events and active-only periodic proximity checks; compare event storm risk with bounded 10-tick approach |
| `ChickenTrapRuntime.run` every tick | Natural time 18000 spawn rolls, dawn expiry, active encounter/visual | Due-time scheduling + time-skip listeners and active trap state vs clock polling; validate world clock stop, rollback, sleep and multiple worlds |
| `IntegrityController` every 20 ticks legacy retry | Retry pending legacy WGL calculations | One-shot delayed retry on rejection/error and worker-capacity callbacks vs existing bounded scan; maintain 60-second wall-clock backoff |

## Mandatory A/B/C comparative protocol

A. **A — baseline**: existing selected subsystem with its current polling frequency.  
B. **B — alternative**: event listeners plus explicit dirty updates and one-shot deadlines.  
C. **C — restricted periodic**: active-only or known-candidate polling, with configurable interval.

1. **Correctness gate**: assertions for unmodified gameplay semantics, latency bounds, external writes, stale UUID/same-UUID entity replacements, death and unload, plugin shutdown, PDC/genome persistence, multiple worlds, ClickMobs, Geyser/Bedrock and WorldEdit/Multiverse interactions where relevant. A version failing this gate is rejected regardless of CPU score.
2. **Comparable workload**: identical seed, config, WGL version and loaded chunks; 0/50/200/1000 WWC chickens, activity fractions 0/5/25/100%, head-changes and no-head-changes, players 0/1/several. Repeat each measurement after warmup, and isolate chunk generation, network or BlueMap bursts.
3. **Metrics**: wall-clock interval, total CPU time, mean and p95/p99 MSPT, worst-tick spikes, total invocations, move/equipment events handled, candidate scans, PDC writes, allocations and queue backlog. Treat `spark` and Paper timings as complementary, not interchangeable.
4. **Adoption**: preserve existing method whenever B/C is not demonstrably equivalent; among equivalent candidates choose the lower **total CPU and tail-MSPT** cost rather than the method with fewer periodic tasks. If evidence is inconclusive, keep the established simpler implementation and record the open comparison.
5. **Workflow**: meaningful Red → minimal Green → Refactor; Java 25 CI; self-review; PR to `develop`. No `main` or release PR update before end-to-end live verification.

This is a work log, **not** a claim that PR #67's event strategy is already a production-validated replacement for periodic checks.
