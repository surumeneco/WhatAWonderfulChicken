package co.surumene.whatawonderfulchicken.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public record PedigreeData(
        AncestorSnapshot parentA,
        AncestorSnapshot parentB,
        AncestorSnapshot grandparentAA,
        AncestorSnapshot grandparentAB,
        AncestorSnapshot grandparentBA,
        AncestorSnapshot grandparentBB) {

    public static final PedigreeData EMPTY = new PedigreeData(null, null, null, null, null, null);

    public byte[] serialize() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(2);
                write(out, parentA);
                write(out, parentB);
                write(out, grandparentAA);
                write(out, grandparentAB);
                write(out, grandparentBA);
                write(out, grandparentBB);
            }
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize pedigree", ex);
        }
    }

    public static PedigreeData deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return EMPTY;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readInt();
            if (version != 1 && version != 2) return EMPTY;
            return new PedigreeData(read(in, version), read(in, version), read(in, version),
                    read(in, version), read(in, version), read(in, version));
        } catch (IOException ex) {
            return EMPTY;
        }
    }

    private static void write(DataOutputStream out, AncestorSnapshot snapshot) throws IOException {
        out.writeBoolean(snapshot != null);
        if (snapshot == null) return;
        out.writeUTF(snapshot.name() == null ? "" : snapshot.name());
        out.writeInt(snapshot.generation());
        out.writeUTF(snapshot.bloodlineId() == null ? "" : snapshot.bloodlineId());
        out.writeBoolean(snapshot.genetics() != null);
        if (snapshot.genetics() != null) out.write(snapshot.genetics().toBytes());
    }

    private static AncestorSnapshot read(DataInputStream in, int version) throws IOException {
        if (!in.readBoolean()) return null;
        String name = in.readUTF();
        int generation = in.readInt();
        String bloodline = in.readUTF();
        Genetics genes = version == 2 && in.readBoolean() ? Genetics.fromBytes(in.readNBytes(4)) : null;
        return new AncestorSnapshot(name, generation, bloodline, genes);
    }
}