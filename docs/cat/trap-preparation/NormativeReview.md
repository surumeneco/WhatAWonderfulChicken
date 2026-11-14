# Trap preparation: normative provenance and review

This review maps the **user's explicit decision** from 2026-11-14 (UTC 2026-11-13T21:18:12Z) to the pre-existing product specification at https://drive.google.com/file/d/1A075R-gDEQW2-SO6DKEbrzNatPpuxVj8/view. Source code, CI, and actual JE/BE effects are distinct evidence sources.

| User-approved behavior / existing product contract | CAT rule IDs | Verification |
| --- | --- | --- |
| Pre-generate two founders; do not activate while pending | appear, synthesis.success, synthesis.failed | preparation tests, runtime inspection |
| Pending bait blocks all damage | damage.pending.entity, damage.pending.other | listener cancellation ordering tests |
| Entity attacks during preparation show totem visuals and sound | damage.pending.entity | listener effect-call tests; **JE/BE rendering still unverified** |
| After preparation, player direct/projectile attack triggers; other mobs do not trigger | damage.ready.player, damage.ready.other | player-only listener tests |
| Proximity activates only when ready and outside grace | proximity.trigger, proximity.no_trigger | existing proximity policy tests |

The TCE is a bounded *waiting bait / activation contract*, not a claim that the entire WWC/WWW plugin is a finite closed Process. Lifecycle of post-activation entities and unrelated mechanics remain in their product specs and existing tests.

## Implementation evidence

- GitHub `develop` snapshot before this documentation change: `99c2f80c66e9bb7edd875a6aca82ad86824c16be`.
- Both target repositories built and passed their complete Java 25 Gradle suites on that snapshot.
- `cat_promote.py` was exercised on the unchanged semantic Draft body with `decision_ref: user-chat:2026-11-13T21:18:12Z`; the confirmed Artifact metadata points to the user's instructions, not to an invented approval of unrequested features.
- Canonical CAT source `896cae8313a533a3c74073a8c3150daad314ff90` / subtree `8d9049a0d0d2b732772329ba869f983f241868dd`.
- In the isolated CAT verification, `process-closure`, `model-conformance`, model compile and nine vectors were successful after confirmation of the source semantics. Formal `implementation-conformance` is **inconclusive** because the pinned upstream only has a TypeScript technology inspector and no verified CAT-owned Java/JUnit Green receipts. A successful ordinary Gradle suite is not that Gate.
- JE/BE in-game particle/audio, interacting Paper plugins and chunk transitions remain for human real-server acceptance.

## Boundary

No `main` release or server deployment is authorized by this evidence. Never report completed human acceptance on the basis of CI.
