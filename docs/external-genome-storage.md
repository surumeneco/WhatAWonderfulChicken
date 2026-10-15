# External Genome payload storage (WGL consumers)

## Storage contract
- The canonical immutable Genome binary and Phenotype Snapshot are stored in `<first-loaded-world>/data/wonderful/wwc/genomes/<record-uuid>.bin`.
- The entity PDC retains its species/type marker, schema version and `genome_ref` string (UUID). Small mutable runtime data remains in the entity.
- Each file contains a WGPF v1 magic/version, signed lengths, both binary payloads and a CRC32, and is written to a temporary file before an atomic move to its final UUID filename.
- A save reuses the previous immutable record when the binary payloads match; changed payloads produce a new ID (copy-on-write). Entity UUID and record UUID have different purposes.
- **Never garbage-collect orphan record files automatically.** Itemized ClickMobs animals, offline inventories and backup archives can refer to records even if no live entity does.
- Migrate old inline-PDC payloads upon the next successful save. Existing old records remain readable.
- A failed write must not change the committed entity reference. Missing or corrupt records are treated as errors rather than silently synthesizing a replacement genome.
- Backup the entire world and its `data/wonderful` subtree together. Moving only entity region files without the external records will break their references.
- This storage only shortens future entity snapshots. Already-invalid oversized ClickMobs items require separate recovery.

## Concurrent operations
- Save/load of entity PDC is confined to the Paper main thread. The current file record implementation performs bounded disk work synchronously while enforcing atomic commit; moving I/O to a dedicated worker would require an additional durable pending-commit protocol.
- CPU-intensive WGL synthesis and decode in founder conversion is performed on a bounded worker queue. Bukkit entities and World are touched only on the main thread and the entity is revalidated when results arrive.
- Operator commands and breeding pathways should be profiled separately; do not increase the Paper Watchdog timeout to hide work on the server thread.

## Testing
- See `GenomePayloadFilesTest` for immutability, persistent reopen, checksum, copy-on-write, and failed-write behavior.
- WWW additionally tests schema 1 inline restore followed by schema 2 external save and restore.
- Live ClickMobs pickup and restore in single-world and cross-world contexts still needs physical-server verification.
