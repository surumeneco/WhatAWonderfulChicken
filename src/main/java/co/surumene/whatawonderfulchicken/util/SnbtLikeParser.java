package co.surumene.whatawonderfulchicken.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SnbtLikeParser {
    private final String source;
    private int pos;

    public SnbtLikeParser(String source) {
        this.source = source == null ? "" : source.trim();
    }

    public Map<String, Object> parseCompound() {
        skipWhitespace();
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) { pos++; return result; }
        while (true) {
            String key = parseKey();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            if (peek('}')) { pos++; break; }
            expect(',');
        }
        skipWhitespace();
        if (pos != source.length()) throw error("Trailing characters");
        return result;
    }

    private Object parseValue() {
        skipWhitespace();
        if (peek('{')) return parseNestedCompound();
        if (peek('"') || peek('\'')) return parseQuoted();
        String token = parseBareToken();
        if (token.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (token.equalsIgnoreCase("false")) return Boolean.FALSE;
        String numeric = token.replaceFirst("(?i)[bslfd]$", "");
        try {
            if (numeric.contains(".") || numeric.contains("e") || numeric.contains("E")) return Double.parseDouble(numeric);
            return Long.parseLong(numeric);
        } catch (NumberFormatException ignored) {
            return token;
        }
    }

    private Map<String, Object> parseNestedCompound() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) { pos++; return result; }
        while (true) {
            String key = parseKey();
            skipWhitespace();
            expect(':');
            result.put(key, parseValue());
            skipWhitespace();
            if (peek('}')) { pos++; return result; }
            expect(',');
        }
    }

    private String parseKey() {
        skipWhitespace();
        if (peek('"') || peek('\'')) return parseQuoted();
        String token = parseBareTokenUntil(':');
        if (token.isBlank()) throw error("Empty key");
        return token;
    }

    private String parseQuoted() {
        char quote = source.charAt(pos++);
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        while (pos < source.length()) {
            char c = source.charAt(pos++);
            if (escaped) {
                out.append(switch (c) { case 'n' -> '\n'; case 't' -> '\t'; default -> c; });
                escaped = false;
            } else if (c == '\\') escaped = true;
            else if (c == quote) return out.toString();
            else out.append(c);
        }
        throw error("Unclosed quoted string");
    }

    private String parseBareToken() {
        int start = pos;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isWhitespace(c) || c == ',' || c == '}') break;
            pos++;
        }
        if (start == pos) throw error("Expected value");
        return source.substring(start, pos);
    }

    private String parseBareTokenUntil(char stop) {
        int start = pos;
        while (pos < source.length() && source.charAt(pos) != stop) {
            char c = source.charAt(pos);
            if (c == ',' || c == '}') break;
            pos++;
        }
        return source.substring(start, pos).trim();
    }

    private void skipWhitespace() { while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++; }
    private boolean peek(char c) { return pos < source.length() && source.charAt(pos) == c; }
    private void expect(char c) { skipWhitespace(); if (!peek(c)) throw error("Expected '" + c + "'"); pos++; }
    private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at position " + pos + " in " + source); }
}