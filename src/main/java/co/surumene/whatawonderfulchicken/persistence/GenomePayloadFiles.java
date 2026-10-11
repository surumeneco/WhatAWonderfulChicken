package co.surumene.whatawonderfulchicken.persistence;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Immutable, content-checked Genome + Phenotype payloads.
 * A record is durable before its ID may be committed to an entity's PDC.
 * No garbage collection: itemized or unloaded entities can still reference any record.
 */
public final class GenomePayloadFiles {
    private static final int MAGIC = 0x57475046; // WGPF
    private static final int VERSION = 1;
    private static final int MAX_PAYLOAD = 16 * 1024 * 1024;
    private final Path directory;
    private final Map<UUID, Payload> recent = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Payload> eldest) {
            return size() > 128;
        }
    };

    public GenomePayloadFiles(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public synchronized UUID writeOrReuse(UUID previous, byte[] genome, byte[] phenotype) {
        Objects.requireNonNull(genome, "genome");
        Objects.requireNonNull(phenotype, "phenotype");
        checkSize(genome.length, phenotype.length);
        if (previous != null) {
            Payload current = read(previous);
            if (Arrays.equals(current.genome(), genome)
                    && Arrays.equals(current.phenotype(), phenotype)) return previous;
        }
        UUID id = UUID.randomUUID();
        byte[] serialized = encode(genome, phenotype);
        Path tmp = directory.resolve(id + ".tmp");
        Path dest = file(id);
        try {
            Files.createDirectories(directory);
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(serialized);
                while (buffer.hasRemaining()) ch.write(buffer);
                ch.force(true);
            }
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Never publish a reference until the final file is in place.
                Files.move(tmp, dest);
            }
            recent.put(id, new Payload(genome, phenotype));
            return id;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not persist genome payload " + id, ex);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    public synchronized Payload read(UUID id) {
        Objects.requireNonNull(id, "id");
        Payload cached = recent.get(id);
        if (cached != null) return cached;
        try {
            Path path = file(id);
            long bytes = Files.size(path);
            if (bytes < 20 || bytes > (long) MAX_PAYLOAD + 20) {
                throw new IOException("Invalid payload file length: " + bytes);
            }
            Payload payload = decode(Files.readAllBytes(path));
            recent.put(id, payload);
            return payload;
        } catch (IOException ex) {
            throw new IllegalStateException("Missing/corrupt genome payload " + id, ex);
        }
    }

    private Path file(UUID id) { return directory.resolve(id + ".bin"); }

    private static void checkSize(int a, int b) {
        if (a <= 0 || b <= 0 || (long) a + b > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Invalid genome/snapshot length");
        }
    }

    private static byte[] encode(byte[] genome, byte[] phenotype) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(20 + genome.length + phenotype.length);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(genome.length);
            out.writeInt(phenotype.length);
            out.write(genome);
            out.write(phenotype);
            CRC32 crc = new CRC32();
            byte[] content = bytes.toByteArray();
            crc.update(content);
            out.writeInt((int) crc.getValue());
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Payload decode(byte[] bytes) throws IOException {
        if (bytes.length < 20) throw new IOException("Truncated payload");
        ByteBuffer input = ByteBuffer.wrap(bytes);
        if (input.getInt() != MAGIC || input.getInt() != VERSION) {
            throw new IOException("Unknown genome payload format");
        }
        int genomeLength = input.getInt();
        int phenotypeLength = input.getInt();
        try { checkSize(genomeLength, phenotypeLength); }
        catch (IllegalArgumentException ex) { throw new IOException(ex); }
        if (bytes.length != (long) genomeLength + phenotypeLength + 20) {
            throw new IOException("Payload length mismatch");
        }
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length - 4);
        if ((int) crc.getValue() != ByteBuffer.wrap(bytes, bytes.length - 4, 4).getInt()) {
            throw new IOException("Genome payload checksum mismatch");
        }
        byte[] genome = new byte[genomeLength], phenotype = new byte[phenotypeLength];
        input.get(genome);
        input.get(phenotype);
        return new Payload(genome, phenotype);
    }

    public record Payload(byte[] genome, byte[] phenotype) {
        public Payload {
            genome = genome.clone();
            phenotype = phenotype.clone();
        }
        @Override public byte[] genome() { return genome.clone(); }
        @Override public byte[] phenotype() { return phenotype.clone(); }
    }
}
