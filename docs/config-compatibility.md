# WWC 2.0 settings audit

Approved source: Google Drive "What a Wonderful Chicken アップデート予定.md".

## Live settings

- `stats.*`: decoded ability display and projection; existing genome and birth snapshot stay unchanged.
- `nature.adjustment`: live personality multiplier, not the decoded personality factors.
- `natural-spawn.chance`: natural entity conversion probability only.
- `traits.*`, `flight.*`, `feeding.*`, `road.*`, `follow.*`: live gameplay tuning.
- `runtime.age.*`: biological clock, growth and age stat modifiers.
- `commands.*`, `storage.*`: operator output and state persistence.
- Chicken Trap-specific keys belong to the separate Phase 9 PR #34.

## Legacy 1.x keys retained for compatibility

- `natural-spawn.max-normalized` and `natural-spawn.distribution.*` do not tune WGL Founder synthesis. Its generator uses profile distribution N(0.42,0.22) with natural target range 0.0-0.75 and Chicken Trap range 0.5-1.5.
- `breeding.stat-mutation-rate`, `breeding.direct-inheritance-rate`, `breeding.gaussian.*` do not tune WGL recombination or Genome mutation.
- `breeding.genetic-mutation-rate` is still used for legacy shadow Genetics and ancestor recovery, but not for WGL Genome mutation.

Do not quietly repurpose old administrator overrides for WGL; changing these semantics requires an approved design change.

## Load/migration behavior

Validate before applying reload. When `set` fails validation, restore the old value. `YamlKeyMerger` fills only absent keys, copies bundled comments where appropriate, and preserves existing values and comments. No mutation of stored Genome / birth Phenotype Snapshot occurs on reload.
