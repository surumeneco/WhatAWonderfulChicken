# WWC 2.0.1 configuration and compatibility

## Active Genome Profile settings

- `founder-target.natural.abilities` and `founder-target.chicken-trap.abilities` independently configure normal Target mean/standard deviation. Existing natural (0..0.75) and trap (0.5..1.5) rejection ranges are fixed.
- `founder-target.personality` and `founder-target.development` configure both origins.
- `genome-profile.decoder.*`, `genome-profile.synthesizer.*`, and `genome-profile.breeding-policy.*` configure only Chicken-owned Profile behavior. Homology/mutation/crossover remain WGL-owned.
- Values are parsed as typed immutable records. Default values equal the previous constants. Invalid types, ranges, and incompatible region ratios reject config changes before normal runtime resync.
- `/wwc reload` and valid `/wwc config set/reset` replace the registered Profile for future founders/breeding. An operation already holding a Profile continues with its existing immutable settings. Saved Genome/Phenotype Snapshot is not retroactively decoded.

## Current gameplay settings

- `stats.*`: canonical ability projection/display. `stats.range-change-policy` remains active in legacy numeric synchronization.
- `nature.adjustment`, `natural-spawn.chance`, `chicken-trap.*`, `traits.*`, `flight.*`, `feeding.*`, `road.*`, `follow.*`, `runtime.age.*`, `commands.*`, and `storage.*` remain active.
- `breeding.genetic-mutation-rate` remains used only by legacy shadow Genetics and ancestor recovery, not WGL Genome mutation.

## Retired 1.x compatibility config keys

The bundled default config and active validators/getters no longer include `natural-spawn.max-normalized`, `natural-spawn.distribution.*`, `breeding.stat-mutation-rate`, `breeding.direct-inheritance-rate` or `breeding.gaussian.*`. They had no effect on WGL-based founders and breeding. Existing administrator YAML keys are **not silently deleted**; old unknown keys are retained on disk but do not control 2.0.1 behavior.

Old 1.x PDC individual/lineage migration is maintained independently. Previous numeric configuration is not automatically given a different Genome meaning.

## Deployment

Use WGL 1.0.1 and WWC 2.0.1 together. Validate Paper, migration, reload/commands and TPS/MSPT on the actual server; CI success only covers automated build and tests.
