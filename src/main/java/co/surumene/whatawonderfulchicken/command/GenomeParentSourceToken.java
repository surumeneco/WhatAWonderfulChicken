package co.surumene.whatawonderfulchicken.command;

import co.surumene.wgl.api.BreedingParentSource;
import co.surumene.wgl.api.GenomeEngine;
import java.util.Base64;
import java.util.Objects;

/** An opaque WGLP typed parent-source binary container encoded as a command token. */
public final class GenomeParentSourceToken {
    private static final String PREFIX = "wglp:";
    private static final int MAX_BYTES = 1048576;
    private GenomeParentSourceToken() {}

    public static String encode(GenomeEngine engine, BreedingParentSource source) {
        Objects.requireNonNull(engine, "engine");
        byte[] data=engine.encodeParentSource(Objects.requireNonNull(source, "source"));
        if(data.length > MAX_BYTES) throw new IllegalArgumentException("WGLP source is too large");
        return PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static BreedingParentSource decode(GenomeEngine engine, String token) {
        Objects.requireNonNull(engine, "engine");
        if(token == null || !token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("parent source must begin with wglp:");
        }
        String value=token.substring(PREFIX.length());
        if(value.isEmpty() || value.length() > 1398108 || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("invalid WGLP source token");
        }
        try {
            byte[] binary=Base64.getUrlDecoder().decode(value);
            if(binary.length > MAX_BYTES) throw new IllegalArgumentException("WGLP source is too large");
            return engine.decodeParentSource(binary);
        } catch(RuntimeException exception) {
            throw new IllegalArgumentException("invalid WGLP parent source: "+exception.getMessage(), exception);
        }
    }
}
