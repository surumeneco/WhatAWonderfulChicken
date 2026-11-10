---
kind: code-to-spec
status: Candidate
work_id: simplecoins-g1-wwc-ownership-qb
source_task: TASK-20261108-007
worker_id: queue-b
created: 2026-11-10
normative_authority: unconfirmed
---

# SimpleCoins G1 / WWC relationship provenance — source-observed Candidate

## Boundary and provenance

This is a **source-derived observation**, not a new approved game rule, a Confirmed CAT Process/PI/TCE, or evidence that G1 integration is implemented. It preserves the distinction between a real player UUID and a role assigned to that UUID.

- WWC `develop@a5bde19906083226dbcbc1d999c79c53ffda746f`: `src/main/java/co/surumene/whatawonderfulchicken/service/WonderfulChickenStorageReader.java`, Git blob `fed4ea0ef3b0d9f8bbea23f14db7ee2e8cd1bed0`; `WonderfulChickenData.java` Git blob `40b5f4f4c5090e239141679f92cb6695db029084`; `WonderfulChickenStore.java` blob `eebd9fabaf550b5a6d78b492e03cfb3da904b508`.
- WCL `develop@cd4389e39145c840839626dd1185303beda367ee`: `wcl-api/.../inventory/StorageSnapshot.java` blob `94b2baaecef32c62828a9cfa934582ffb8beeab2`; `wcl-plugin/.../inventory/WonderfulStorageReader.java` blob `0c0d1b5eb37e31f3aeebdbd6efe2c7214916322a`.
- WWW `develop@442ae2cdece202dcbaeb78c82c9df26800951925`: `src/main/java/co/surumene/www/persistence/WonderfulWolfStorageReader.java` blob `37e35aff978686183056bbc090b54e9b44e20ac9`.
- Product design authority: `forGPT/XPlayServer/OriginalPlugins/SimpleCoins/02A_所有者・統計定義.md`, Drive file ID `1NDERmCBmKqFmBNYZQBKifQ6kXChdbB4g`, ownership priority paragraph under `所有者の定義（確定事項）`: commander → owner → free, then current BAN/playerdata classification. This Candidate does **not** establish that WWC's `follow_target` is either role.

## Current code observation (not inferred semantics)

1. WWC's `readLoaded` delegates to `readStored` with the loaded chicken's PDC. `readStored` checks the `wonderful_chicken` marker and supported schema, then constructs a `StorageSnapshot` with `relationships` containing **only** key `follow_target` when that PDC string exists and parses as a UUID. No `commander` or `owner` relationship is emitted by this reader at the above blob. The successful item slots are `carpet`, `shulker` and `head` with original serialized ItemStack bytes.
2. WWC `WonderfulChickenData` has `followTarget` but no role-named `ownerId` or `commanderId` field in this class; `WonderfulChickenStore` persists/loads `follow_target`. This establishes a source-format observation, **not** a conclusion that other components cannot know an owner.
3. WCL `StorageSnapshot.relationships` is an immutable `Map<String, UUID>`, not a typed role hierarchy. The public `WonderfulStorageReader` has distinct `readLoaded` and `readStored` entrypoints; `readStored` takes a caller-supplied PDC and must not load chunks or write PDC.
4. WWW's reader explicitly exports keys `owner` and `commander` from its separate owner/commander UUID fields. Thus the two consumers expose **different relationship vocabularies** even though they implement the same WCL interface.

## Integration gap / decision required

No verified mapping establishes whether WWC `follow_target` denotes a SimpleCoins **commander**, a persistent **owner**, a transient following target, or neither. The code as read cannot prove that SimpleCoins G1 can apply its existing commander → owner → free priority for WWC without additional domain provenance. An implementation **must not silently promote** `follow_target` to `commander` or `owner`, invent an identity, or mark absent/unreadable relationship data as a successful zero-value read. Determine the authoritative WWC relation semantics and offline persistence compatibility before changing a production mapping; preserve existing payload bytes and read-only behavior.

## Source-bound validation cases for later approved Work

- Loaded WWC with a valid follow target: snapshot succeeds; `follow_target` is present verbatim; no role-named `owner`/`commander` is inferred by this reader.
- Stored/offline WWC with the same PDC: relationship result is identical without chunk loading, PDC mutation or file writes.
- WWC with absent follow key: no identity is fabricated; any intended free classification requires the separate SimpleCoins contract.
- WWC with malformed UUID or broken serialized item: `StorageReadResult.Failure` (e.g., `CORRUPT_DATA`), **not** an empty successful snapshot; check error code against real fixture.
- WWW with explicit owner and commander: retains both distinct UUID roles; check the commander-precedence rule only in a separately Confirmed integration test.
- Cross-provider: a storage result with `NotFound` or `Failure` cannot be substituted for a proven empty inventory when total currency is computed.

## Status

`code-to-spec` Candidate, no Confirmed normative promotion; the listed validation cases are a proposed test matrix, not executed JUnit/Paper evidence. Product source change: none. CAT Process Closure, Model Conformance, TDD Red/Green and real Paper deployment: not run. Link to TaskQueue `TASK-20261108-007` and retain status `ready` until G1 AC is established.
