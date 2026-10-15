package co.surumene.whatawonderfulchicken.command;

import java.util.ArrayList;
import java.util.List;

public final class GenomeCommandArguments {
    private GenomeCommandArguments() {}

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "one or two haplotype arguments are required");
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean tokenStarted = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                if (c == ',') {
                    current.append('\\').append(',');
                } else {
                    current.append(c);
                }
                escaped = false;
                tokenStarted = true;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                tokenStarted = true;
                continue;
            }
            if (quote != 0) {
                if (c == quote) quote = 0;
                else current.append(c);
                tokenStarted = true;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                tokenStarted = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (tokenStarted) {
                    result.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
                continue;
            }
            current.append(c);
            tokenStarted = true;
        }

        if (escaped) current.append('\\');
        if (quote != 0) {
            throw new IllegalArgumentException("unclosed quote");
        }
        if (tokenStarted) result.add(current.toString());
        if (result.isEmpty() || result.size() > 2) {
            throw new IllegalArgumentException(
                    "one or two haplotype arguments are required");
        }
        return List.copyOf(result);
    }
}
