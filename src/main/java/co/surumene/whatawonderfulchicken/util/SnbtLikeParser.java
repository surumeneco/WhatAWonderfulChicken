package co.surumene.whatawonderfulchicken.util;

import java.util.Map;

/** Compatibility facade for the identical SNBT-like parser now owned by WCL. */
public final class SnbtLikeParser {
    private final co.surumene.wcl.api.text.SnbtLikeParser parser;

    public SnbtLikeParser(String source) {
        parser = new co.surumene.wcl.api.text.SnbtLikeParser(source);
    }

    public Map<String, Object> parseCompound() {
        return parser.parseCompound();
    }
}
