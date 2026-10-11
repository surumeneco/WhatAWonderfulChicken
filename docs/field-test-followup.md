# Field test follow-up fixes

## WWW
- FOLLOW/GUARD actively return to their online commander, teleporting only at long distances after finding solid ground and two unobstructed blocks. Never teleport in WANDER or WAIT.
- An existing Wonderful Wolf newly tamed by a player should start FOLLOW with that player as both owner and commander; don't override a previously chosen mode.
- Chest GUI uses 9 management slots and the minimum whole number of nine-slot cargo rows; remaining last-row cells are visually disabled. Stored 0-based cargo slot IDs stay unchanged.
- ClickMobs-created replacement wolves must register their saved PDC on WanWand interaction even when normal chunk-load events do not run; reject corrupt records.
## WWC
- Pedigree displays must not present legacy shadow Genetics as if it were the Genome-derived parent's real personality and traits. Capture immutable birth snapshots.
- Admin info should follow the sectioned WWW presentation without removing WWC-specific stats.
- Previously saved pedigree should remain readable and should not be modified without evidence.
## Test
- Boundary capacity (0,1,9,10,45), GUI persistence, lazy restoration, tame event, long-distance follow, family snapshot integrity. Trap cases are still untested.
