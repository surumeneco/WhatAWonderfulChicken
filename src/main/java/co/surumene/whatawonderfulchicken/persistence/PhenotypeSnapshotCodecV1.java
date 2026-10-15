package co.surumene.whatawonderfulchicken.persistence;

import co.surumene.whatawonderfulchicken.data.DevelopmentFactor;
import co.surumene.whatawonderfulchicken.data.ExpressedTrait;
import co.surumene.whatawonderfulchicken.data.InjuryPhenotype;
import co.surumene.whatawonderfulchicken.data.Nature;
import co.surumene.whatawonderfulchicken.data.PersonalityFactor;
import co.surumene.whatawonderfulchicken.data.PhenotypeSnapshot;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.Trait;
import co.surumene.whatawonderfulchicken.data.TraitStrength;
import co.surumene.wgl.api.DecoderIdentity;
import co.surumene.wgl.api.ProfileDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PhenotypeSnapshotCodecV1 {
    private static final int MAGIC = 0x57435048; // WCPH
    public static final int CONTAINER_VERSION = 1;
    private static final int FINGERPRINT_LENGTH = 32;
    private static final int MAX_STRING_BYTES = 1024;

    public byte[] encode(PhenotypeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(MAGIC);
            out.writeByte(CONTAINER_VERSION);
            writeDecoderIdentity(out, snapshot.decoderIdentity());
            writeEnumScores(out, StatType.values(), snapshot.normalizedAbilities());
            writeEnumScores(out, PersonalityFactor.values(), snapshot.personalityFactors());
            writeString(out, snapshot.personality().name());

            out.writeByte(snapshot.expressedTraits().size());
            for (ExpressedTrait trait : snapshot.expressedTraits()) {
                writeString(out, trait.trait().name());
                writeString(out, trait.strength().name());
            }

            writeEnumScores(out, DevelopmentFactor.values(), snapshot.developmentFactors());

            out.writeByte(snapshot.injuries().size());
            for (InjuryPhenotype injury : snapshot.injuries()) {
                writeString(out, injury.stat().name());
                out.writeDouble(injury.onsetGameDay());
                out.writeDouble(injury.severityRank());
            }

            out.writeDouble(snapshot.divineLineageTotalScore());
            out.writeBoolean(snapshot.divineLineageExpressed());
            out.flush();
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public PhenotypeSnapshot decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC) {
                throw new PersistenceCodecException("invalid phenotype snapshot magic");
            }
            int version = in.readUnsignedByte();
            if (version != CONTAINER_VERSION) {
                throw new PersistenceCodecException(
                        "unsupported phenotype snapshot container version: " + version);
            }

            DecoderIdentity identity = readDecoderIdentity(in);
            EnumMap<StatType, Double> abilities = readEnumScores(in, StatType.class);
            EnumMap<PersonalityFactor, Double> personality =
                    readEnumScores(in, PersonalityFactor.class);
            Nature nature = readEnum(in, Nature.class);

            int traitCount = in.readUnsignedByte();
            if (traitCount > 2) {
                throw new PersistenceCodecException("phenotype snapshot contains too many traits");
            }
            List<ExpressedTrait> traits = new ArrayList<>(traitCount);
            for (int i = 0; i < traitCount; i++) {
                traits.add(new ExpressedTrait(
                        readEnum(in, Trait.class),
                        readEnum(in, TraitStrength.class)));
            }

            EnumMap<DevelopmentFactor, Double> development =
                    readEnumScores(in, DevelopmentFactor.class);

            int injuryCount = in.readUnsignedByte();
            if (injuryCount > StatType.values().length) {
                throw new PersistenceCodecException("phenotype snapshot contains too many injuries");
            }
            List<InjuryPhenotype> injuries = new ArrayList<>(injuryCount);
            for (int i = 0; i < injuryCount; i++) {
                injuries.add(new InjuryPhenotype(
                        readEnum(in, StatType.class),
                        in.readDouble(),
                        in.readDouble()));
            }

            double divineScore = in.readDouble();
            boolean divineExpressed = in.readBoolean();
            if (in.available() != 0) {
                throw new PersistenceCodecException(
                        "trailing bytes are not allowed in phenotype snapshot");
            }

            return new PhenotypeSnapshot(
                    identity,
                    abilities,
                    personality,
                    nature,
                    traits,
                    development,
                    injuries,
                    divineScore,
                    divineExpressed);
        } catch (PersistenceCodecException e) {
            throw e;
        } catch (EOFException e) {
            throw new PersistenceCodecException("truncated phenotype snapshot", e);
        } catch (IOException e) {
            throw new PersistenceCodecException("failed to decode phenotype snapshot", e);
        } catch (IllegalArgumentException e) {
            throw new PersistenceCodecException(
                    "malformed phenotype snapshot: " + e.getMessage(), e);
        }
    }

    private static void writeDecoderIdentity(DataOutputStream out, DecoderIdentity identity)
            throws IOException {
        out.writeInt(identity.engineRevision());
        writeFingerprint(out, identity.engineDecoderConfigFingerprint());
        ProfileDescriptor profile = identity.profileDescriptor();
        writeString(out, profile.profileId());
        out.writeInt(profile.profileVersion());
        writeFingerprint(out, profile.semanticFingerprint());
    }

    private static DecoderIdentity readDecoderIdentity(DataInputStream in) throws IOException {
        int engineRevision = in.readInt();
        byte[] engineFingerprint = readFingerprint(in);
        String profileId = readString(in);
        int profileVersion = in.readInt();
        byte[] profileFingerprint = readFingerprint(in);
        return new DecoderIdentity(
                engineRevision,
                engineFingerprint,
                new ProfileDescriptor(profileId, profileVersion, profileFingerprint));
    }

    private static void writeFingerprint(DataOutputStream out, byte[] fingerprint) throws IOException {
        if (fingerprint.length != FINGERPRINT_LENGTH) {
            throw new IllegalArgumentException("fingerprint must contain 32 bytes");
        }
        out.write(fingerprint);
    }

    private static byte[] readFingerprint(DataInputStream in) throws IOException {
        byte[] bytes = in.readNBytes(FINGERPRINT_LENGTH);
        if (bytes.length != FINGERPRINT_LENGTH) throw new EOFException("truncated fingerprint");
        return bytes;
    }

    private static <E extends Enum<E>> void writeEnumScores(
            DataOutputStream out,
            E[] values,
            Map<E, Double> scores) throws IOException {
        out.writeByte(values.length);
        for (E value : values) {
            writeString(out, value.name());
            out.writeDouble(scores.get(value));
        }
    }

    private static <E extends Enum<E>> EnumMap<E, Double> readEnumScores(
            DataInputStream in,
            Class<E> enumType) throws IOException {
        E[] values = enumType.getEnumConstants();
        int count = in.readUnsignedByte();
        if (count != values.length) {
            throw new PersistenceCodecException(
                    "unexpected " + enumType.getSimpleName() + " score count: " + count);
        }
        EnumMap<E, Double> result = new EnumMap<>(enumType);
        for (int i = 0; i < count; i++) {
            E key = readEnum(in, enumType);
            double value = in.readDouble();
            if (result.put(key, value) != null) {
                throw new PersistenceCodecException(
                        "duplicate " + enumType.getSimpleName() + " score: " + key);
            }
        }
        return result;
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream in, Class<E> enumType)
            throws IOException {
        String name = readString(in);
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException e) {
            throw new PersistenceCodecException(
                    "unknown " + enumType.getSimpleName() + " value: " + name, e);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("string is too long for phenotype snapshot");
        }
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > in.available()) {
            throw new PersistenceCodecException(
                    "invalid string length in phenotype snapshot: " + length);
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
