package co.surumene.whatawonderfulchicken.persistence;

import co.surumene.whatawonderfulchicken.data.*;
import co.surumene.wgl.api.DecoderIdentity;
import co.surumene.wgl.api.ProfileDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PhenotypeSnapshotCodecV1Test {
    private final PhenotypeSnapshotCodecV1 codec = new PhenotypeSnapshotCodecV1();

    @Test
    void roundTripsCompleteSnapshot() {
        PhenotypeSnapshot source = snapshot();
        assertEquals(source, codec.decode(codec.encode(source)));
    }

    @Test
    void rejectsUnsupportedVersionAndTrailingBytes() {
        byte[] encoded = codec.encode(snapshot());
        encoded[4] = 2;
        assertThrows(PersistenceCodecException.class, () -> codec.decode(encoded));

        byte[] valid = codec.encode(snapshot());
        assertThrows(PersistenceCodecException.class,
                () -> codec.decode(Arrays.copyOf(valid, valid.length + 1)));
    }

    private static PhenotypeSnapshot snapshot() {
        EnumMap<StatType, Double> abilities = new EnumMap<>(StatType.class);
        int i=0;
        for (StatType stat : StatType.values()) abilities.put(stat, 0.2 + i++ * 0.1);

        EnumMap<PersonalityFactor, Double> personality = new EnumMap<>(PersonalityFactor.class);
        i=0;
        for (PersonalityFactor factor : PersonalityFactor.values()) personality.put(factor, 0.25 + i++ * 0.08);

        EnumMap<DevelopmentFactor, Double> development = new EnumMap<>(DevelopmentFactor.class);
        i=0;
        for (DevelopmentFactor factor : DevelopmentFactor.values()) development.put(factor, 0.2 + i++ * 0.1);

        return new PhenotypeSnapshot(
                new DecoderIdentity(3, fingerprint(0x10),
                        new ProfileDescriptor("wonderful-chicken", 1, fingerprint(0x40))),
                abilities,
                personality,
                Nature.GANBARIYA,
                List.of(
                        new ExpressedTrait(Trait.JIKIDEN, TraitStrength.WEAK),
                        new ExpressedTrait(Trait.FUKUTSU, TraitStrength.WEAK)),
                development,
                List.of(new InjuryPhenotype(StatType.GROUND_SPEED, 123.0, 4.5)),
                0.75,
                true);
    }

    private static byte[] fingerprint(int start) {
        byte[] bytes = new byte[32];
        for (int i=0;i<bytes.length;i++) bytes[i]=(byte)(start+i);
        return bytes;
    }
}
