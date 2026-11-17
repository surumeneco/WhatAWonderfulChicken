# Trap founder preparation — CAT Issue/Work Draft

status: Draft (human-specified behavior; implementation detail pending CAT conformance)
request-origin: user conversation, 2026-11-14
flow: issue-work → spec-implementation
upstream-cat: surumeneco/CAT-TDD develop 896cae8313a533a3c74073a8c3150daad314ff90

## Human-decided behavior
1. Only direct player or player-launched projectile attacks activate traps in both WWC and WWW. Mob-caused damage never starts them.
2. Trap waiting mobs cannot take damage and cannot activate before both founder genomes are fully prepared.
3. Any entity attack on an unprepared waiting mob produces totem-of-undying particles and use sound, while cancelling the damage.

## Derived implementation design (Draft)
- Initiate asynchronous preparation for both founders on waiting mob creation (also on restoration / chunk load). Store prepared immutable game data in memory only, bound to the waiting mob UUID and current runtime identity.
- Before preparation completion all damage events targeting ARMED waiting mobs are cancelled. Entity attacks also play the totem feedback; environmental damage does not play feedback.
- A READY waiting mob may activate by proximity after the existing grace period or by a player attack irrespective of grace. The triggering attack is consumed to prevent instant death while converting the waiting mob to riders.
- Keep bounded asynchronous workers, discard stale callbacks when an entity dies, unloads, or is replaced. Retry failures with bounded delay and diagnostic warnings. Re-prepare after reload because prepared results are deliberately not persisted.
- WWC dawn expiry still deletes ARMED waiting mobs; WWW retains existing persistent waiting behavior.
- Completion of the ready-to-rider transition must not use incomplete/failed synthesis results.

## Verification targets
- Nonplayer attacks never activate WWC/WWW.
- Damage cancelled and totem visual/audio feedback only for attack events while unprepared.
- No proximity or attack activation before successful preparation; player-initiated activation works afterward.
- Both founder genomes prepared before either mount is spawned.
- Shutdown, reload, chunk unload, dawn, death, rejected/failing workers do not leave an unprotected unprepared Mob.
- Existing rider AI, entity PDC, natural spawn conditions and config defaults unchanged.

CAT Gate state: not-run (this Draft is neither a Confirmed Artifact nor a passing Process Closure or implementation-conformance result).
