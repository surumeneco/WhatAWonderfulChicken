package co.surumene.whatawonderfulchicken.util;

import java.util.ArrayList;
import java.util.List;

public final class CommandText {
    private CommandText() {}

    public static List<String> extractTopLevelCompounds(String source) {
        List<String> result = new ArrayList<>();
        if (source == null) return result;
        int depth = 0;
        int start = -1;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) throw new IllegalArgumentException("Unexpected '}'");
                if (depth == 0 && start >= 0) {
                    result.add(source.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        if (depth != 0 || quote != 0) throw new IllegalArgumentException("Unclosed compound or string");
        return result;
    }

    public static int firstCompoundIndex(String source) {
        if (source == null) return -1;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '{') return i;
        }
        return -1;
    }
}