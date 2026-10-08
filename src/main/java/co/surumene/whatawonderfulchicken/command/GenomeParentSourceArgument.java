package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.BreedingParentSource;
import co.surumene.wgl.api.GenomeEngine;
import java.util.Base64;
import java.util.Objects;

public final class GenomeParentSourceArgument {
    private GenomeParentSourceArgument() {}

    public static String encode(BreedingParentSource source, GenomeEngine engine) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(engine, "engine");
        String type = switch(source) {
            case BreedingParentSource.DiploidParent ignored -> "diploid";
            case BreedingParentSource.Gamete ignored -> "gamete";
        };
        return type + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(engine.encodeParentSource(source));
    }

    public static BreedingParentSource parse(String raw, GenomeEngine engine) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(engine, "engine");
        int colon = raw.indexOf(':');
        if(colon <= 0 || colon == raw.length()-1) {
            throw new IllegalArgumentException("source must be diploid:<data> or gamete:<data>");
        }
        String type = raw.substring(0,colon);
        if(!type.equals("diploid") && !type.equals("gamete")) {
            throw new IllegalArgumentException("unsupported parent source type: " + type);
        }
        final byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(raw.substring(colon+1));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid parent source base64url", ex);
        }
        BreedingParentSource decoded=engine.decodeParentSource(bytes);
        if(type.equals("diploid") && !(decoded instanceof BreedingParentSource.DiploidParent)
                || type.equals("gamete") && !(decoded instanceof BreedingParentSource.Gamete)) {
            throw new IllegalArgumentException("parent source type prefix does not match encoded data");
        }
        if(decoded.chromosomeCount()!=6) {
            throw new IllegalArgumentException("Wonderful Chicken requires six chromosomes");
        }
        return decoded;
    }

    public static Pair splitPair(String raw) {
        if(raw == null) throw new IllegalArgumentException("expected <sourceA> parent <sourceB>");
        String[] tokens=raw.trim().split("\\s+");
        if(tokens.length != 3 || !tokens[1].equals("parent")) {
            throw new IllegalArgumentException("expected <sourceA> parent <sourceB>");
        }
        return new Pair(tokens[0],tokens[2]);
    }

    public record Pair(String first,String second) {}
}
