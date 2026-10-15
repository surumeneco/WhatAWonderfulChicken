# WWC Genome marker lineage and player information contract

## Identity
- Current physical Genome determines the visible pedigree marker using WGL Marker Scheme V1, matching WWW. The formatted marker is a **non-unique visualization** and cannot be used as an entity identifier, lookup key, or storage primary key.
- For every newly synthesized founder (natural spawn, `/wwc summon`, Chicken Trap, or legacy migration synthesis), independently select each of the two marker anchors on each chromosome and haplotype from 49 equally likely variants: the canonical 48-bit anchor, or exactly one of its 48 bits flipped. This mirrors WWW and preserves at most two bits of distance between independently generated anchors at the same locus. Other anchors remain canonical, and WGL Marker Scheme V1 itself is unchanged.
- Existing saved Genome data is never rewritten to invent initial marker differences. Recomputed markers remain faithful to stored physical alleles; marker collisions remain possible.
- Entity UUID (Bukkit) continues to identify individual entities. New founder, migrated and bred chickens never receive a random UUID-shaped lineage *display* ID.
- Existing legacy UUID-shaped lineage IDs are retained only as migration aliases for references in pre-existing offspring pedigree snapshots. Before replacing a legacy entity's saved lineage field, move the UUID into a dedicated legacy alias PDC key; preserve it during subsequent saves.
- Existing pedigree references may contain old UUID-shaped IDs. When the real ancestor is loaded and its legacy alias matches, upgrade that snapshot's displayed lineage to the Genome marker; do not invent a marker for an unloaded/unresolvable ancestor. Explicitly label unresolved legacy IDs.
- Historical pedigree labels are birth-time snapshots, not recalculated from the child's Genome. After migration, new children capture their actual parents' marker and phenotype on the main thread before asynchronous WGL breeding, with inherited grandparents' stored snapshots.

## Player GUI presentation
- Follow WWW's split: information icon lore starts with personality, then age, followed by ranked nine Chicken abilities with personality arrows and injury indicators. If normalized/effective ranks differ, show progression with an arrow. Keep per-stat config display='none' hidden; configurable value/rank/both remains supported when explicitly configured.
- Pedigree icon lore starts with expressed trait names and a divine-lineage indicator, then Genome marker, generation, both parents and four grandparents. Parent snapshots show actual recorded personality and expressed traits where available.
- GUI clicks show the same information in a headed contiguous chat block (including the current entity name), without leaking administrative UUID, raw Genome, hidden injury onset, or debug-only numeric information.
- Admin /wwc info can retain expanded data and must use the same marker and ancestry semantics.

## Compatibility
- Old v1/v2/v3 pedigree data remains readable. The missing phenotype or markers cannot be inferred from shadow Genetics or a child's Genome. No lineage-related Genome regeneration.
- Check two-parent breeding, marker persistence after save and reload, legacy alias conversion, older ancestry records, GUI lore/click consistency, and ClickMobs roundtrips before promoting to main.
