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

## 2026-10-17 implementation freeze for live A/B/C validation

All comparison branches below originate from the *same executable baseline* commit
`11d7abc6ff0dd2aa57721db22b9aeec302fa6024` (WWC 2.0.1), which includes
PR #66 founder on-demand dispatch, PR #67 equipment-event coverage telemetry,
and PR #68 this verification plan. Branch contents intentionally differ **only**
in their named experiment. Each candidate is a Draft PR, not accepted into
`develop`. Do not combine multiple B branches before evaluating them separately.

| Experiment | A (unchanged comparator) | B branch / draft PR | Contract still requiring live evidence |
| --- | --- | --- | --- |
| Display move and yaw synchronization | `feature/wwc-compare-a-periodic-20261017` | `feature/wwc-compare-b-display-event-20261017`, #69 | Validate movement/teleport/cross-world/rider/scale and compare event CPU against 1-tick baseline; B has 20-tick fallback |
| Integrity membership enumeration | same A | `feature/wwc-compare-b-integrity-deadline-20261017`, #70 | Validate initial scan, delayed PDC, same-UUID replacement, unload and 200-tick audit cost; despite branch name, age/helm checks retain their periodic deadlines |
| Head item equipment notification | same A | `feature/wwc-compare-b-head-events-20261017`, #71 | Compare event-only against A with event + periodic fallback; do not accept if third-party changes are missed |
| Late-PDC and eventless entity insertion | same A | `feature/wwc-compare-b-pdc-candidates-20261017`, #72 | B intentionally omits global 6000-tick loaded-chunk sweep; ClickMobs eventless replacement must be shown not to regress |
| Legacy Genome failure and queue retry | same A | `feature/wwc-compare-b-legacy-retry-wake-20261017`, #73 | 60-second wall-clock cooldown, saturation, queue drain, shutdown, profile change; compare callback/timer scheduling against 20-tick checking |
| Idle trait runtime | same A | `feature/wwc-compare-b-trait-active-scheduler-20261017`, #74 | B schedules trait updates only while active sources exist. Validate delayed first application, Aura, rider buffs, watch, reload, lifecycle, and scheduler cost |

**Individually testing a candidate**: always check out branch A and the selected
B branch independently, with exactly the same WGL 1.0.1, Java 25, Paper 26.2
build, WWC 2.0.1 config and immutable copy of the test world. Never overwrite
the currently deployed JAR or run on the production world; take a snapshot and
use a distinct isolated test server. Each JAR's plugin.yml must say version 2.0.1;
record Git SHA and SHA-256 separately because **all six B JARs share a version**.

Build each branch in the matching project workspace, using
`./gradlew clean build --no-daemon` (on Windows PowerShell:
`./gradlew.bat clean build --no-daemon`). The CI build also checks the
matching WGL 1.0.1 dependency. Keep the generated test JARs in distinctly
named local directories labelled by branch SHA, **never alongside duplicate
WWC jars in one plugins/ directory**.

For comparable scenarios use the existing read-only
`MineCraftXPlayServer/deploy/capture-performance.py` plus
`deploy/performance-validation.md`; record CPU and Spark-derived main-thread
MSPT separately. `summary.json`'s p95/p99 describe **sampled CPU**, not
MSPT. Run A/B/A/B or another alternating repeated sequence after warm-up for
each isolated scenario. If effects are statistically inconclusive, the
current A implementation remains selected.

### Functional checklist specific to branches

1. **Display #69**: static equipped Chicken, rapid walking/flying, rotation
   without translation, rotation of body yaw, rider and passenger, teleport to
   another chunk/world, scale update after growth and reload, display orphan
   cleanup, Geyser BE vs Java rendering. Check smoothness/frame delay.
2. **Integrity #70**: 0/50/200/1000 loaded Chickens, old instance unload after
   same-UUID new instance registration, death, chunk reload, config reload,
   legacy genome change and age growth/reversal, missing-PDC replacement.
3. **Helmet #71**: all WWC GUI routes, external Paper equipment setters,
   dispensers/pickups, armor removal, chunk reload, equipment event timing.
   Compare A diagnostics `periodic-only corrections`; every missed external
   mutation disqualifies event-only until solved.
4. **PDC #72**: initial known PDC, PDC added one tick and 10 seconds after
   EntityAdd, late PDC after 6000 ticks, ClickMobs restore/replace, eventless
   third-party entity injection. A guarantees broad eventual discovery; B
   may violate this and must remain **unadopted** if the requirement remains.
5. **Migration #73**: many old Genome-free individuals, failed WGL migration,
   >16 queued calculations, retry saturation, 60s backoff, profile hot swap,
   death/unload before callback and JVM/plugin shutdown.
6. **Traits #74**: 0 eligible traits, one or many active traits, rapid trait
   mutation, brief absence of all eligible sources, full unload, reload,
   external strong Potion effects, watch warning intervals and riding effects.

### Intentional non-experiment cases (current A retained)

- `RidingController` 1-tick active steering/physics and grounded recovery
  retain the active membership optimization already in `develop`. They
  depend on continuous environment state; a purely event-driven substitute
  is not established as equivalent. Mount compatibility 200-tick check remains
  during real-world plug-in compatibility evaluation.
- `FollowController` 5-tick mode-specific reconciliation and internal
  MobGoal ticks remain to catch missing pathfinder goals/interruptions;
  eliminating an outer check based solely on player events may regress
  third-party goal changes. Validate with many FOLLOW/WAIT entities before
  introducing a separate contender.
- `ChickenTrapRuntime` full-time observation remains each tick because
  exact natural `previous + 1` boundary, time skip and clock rollback
  semantics are not established as event-complete for arbitrary external
  `World#setFullTime` or gamerule changes. Loaded active trap visuals also
  require per-tick movement. Do not replace with a deadline-based guess.
- `TraitController` active near-player effects and lookouts retain their
  current 10-tick cadence in both A and #74. An unrestricted event listener
  for *all* nearby living-entity moves could itself be more costly; there is
  no demonstrated better candidate yet.

### Final gate

For each candidate record: CI link and commit, feature equivalence results,
per-scenario total CPU and MSPT tail statistics, number of missed events or
late corrections, and an explicit adopt/reject decision. After separately
adopting compatible candidates, prepare a *new* integrated candidate branch
from `develop`, rerun full automated CI and gameplay regressions, then
compare that integrated build with A. No adopted behavior or optimization
claim is inferred merely from passing Java 25 CI.

The last outstanding steps after these code candidates are tested are
**external-server behavior validation, controlled comparison, adoption
decisions, and release integration**. These cannot be marked completed
without real-server observations.

## 2026-10-17: user-approved implementation decision (supersedes prior A/B candidates)

The decisions below were explicitly made by the server owner. The earlier comparison matrix and candidate-only descriptions above remain as **historical** evidence, not open design choices.

| Item | Decision | WWC 2.0.1 implementation |
| --- | --- | --- |
| #1 Display movement #69 | **REJECT** | Preserve per-tick `DisplayService.tick()` exactly; no Paper move listener is adopted |
| #2 Integrity roster #70 | **ACCEPT** | Maintain UUID-to-live-entity membership by load/unload/registration callbacks; keep bounded 200-tick processing and age projection. Same-UUID stale unload does not clear replacement caches |
| #3 Head equipment #71 | **ACCEPT** | Use `EntityEquipmentChangedEvent`, existing on-demand internal writes; no periodic `captureHeadEquipment()` read in the integrity sweep |
| #4 PDC/unknown entity #72 | **MODIFY** | Keep 200-tick known candidate checks and entity/chunk lifecycle; every 6000 server ticks audit only loaded 3×3 chunk neighborhoods around **each online player** (max 9 positions/player, deduplicated per world), not all loaded chunks. Never load chunks for this audit. Inspect at most 2 scheduled chunks/tick |
| #5 Legacy migration #73 | **ACCEPT** | On-demand worker-completion and single retry deadline, preserving 60s wall-clock failure backoff and worker backpressure; no 20-tick global empty retry check |
| #6 Trait ticker #74 | **ACCEPT** | Schedule every 10 ticks only when the active trait membership is nonempty; stop task when empty, restart when a source appears; do not alter effect strength, expiry or alert rules |

Implementation PR: [#76](https://github.com/surumeneco/WhatAWonderfulChicken/pull/76).
Google Drive design decision: https://docs.google.com/document/d/1Ivkek-Mo5x6hnEI9nEo0DtFFBYOQQMbGQ2CEEshaNjs .

### Live acceptance gate (still pending)

- Verify ClickMobs restoration and later PDC writes both inside and outside player-nearby chunks, across multiple worlds and two overlapping player neighborhoods. Unloaded chunks **must not** be loaded; unknown chickens outside the neighborhoods have no longer a global discovery guarantee.
- Verify same UUID entity replacement followed by old entity unload, equipment edits by other plugins, attribute growth, stale cache invalidation, and plugin reload/shutdown.
- Verify failed/saturated migration retries after 60 real seconds, and trait source count going 0→1→0→1 without skipped required effects or altered alert rate.
- Compare CPU and MSPT under the scenarios documented above; do not claim production improvement from source-level counts or Java 25 CI.
- Keep `main`, deployment, and the WWC 2.0.1 draft release PR unchanged pending real server verification.
