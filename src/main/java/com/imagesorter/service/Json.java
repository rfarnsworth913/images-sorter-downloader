package com.imagesorter.service;

import java.util.*;

/** Small JSON codec for the ComfyUI protocol. */
public final class Json {
    private final String text;
    private int pos;
    private Json(String text) { this.text = text; }
    public static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value();
        parser.space();
        if (parser.pos != text.length()) throw new IllegalArgumentException("Trailing JSON data");
        return value;
    }
    private void space() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
    private char take() { if (pos >= text.length()) throw new IllegalArgumentException("Incomplete JSON"); return text.charAt(pos++); }
    private Object value() {
        space();
        char c = take();
        if (c == '"') {
            StringBuilder s = new StringBuilder();
            while ((c = take()) != '"') {
                if (c == '\\') {
                    c = take();
                    switch (c) {
                        case '"', '\\', '/' -> s.append(c);
                        case 'n' -> s.append('\n'); case 'r' -> s.append('\r'); case 't' -> s.append('\t');
                        case 'b' -> s.append('\b'); case 'f' -> s.append('\f');
                        case 'u' -> { s.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4; }
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    }
                } else { if (c < 32) throw new IllegalArgumentException("Invalid JSON string"); s.append(c); }
            }
            return s.toString();
        }
        if (c == '{' || c == '[') {
            Map<String, Object> map = new LinkedHashMap<>(); List<Object> list = new ArrayList<>();
            char end = c == '{' ? '}' : ']'; space();
            if (pos < text.length() && text.charAt(pos) == end) { pos++; return c == '{' ? map : list; }
            while (true) {
                if (c == '{') {
                    Object key = value(); space();
                    if (!(key instanceof String) || take() != ':') throw new IllegalArgumentException("Invalid JSON object");
                    map.put((String) key, value());
                } else list.add(value());
                space(); char next = take(); if (next == end) break;
                if (next != ',') throw new IllegalArgumentException("Invalid JSON separator");
            }
            return c == '{' ? map : list;
        }
        int start = pos - 1;
        while (pos < text.length() && ",]} \r\n\t".indexOf(text.charAt(pos)) < 0) pos++;
        String token = text.substring(start, pos);
        return switch (token) { case "true" -> true; case "false" -> false; case "null" -> null; default -> new java.math.BigDecimal(token); };
    }
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            StringBuilder result = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                if (c == '"' || c == '\\') result.append('\\').append(c);
                else if (c < 32) result.append(String.format("\\u%04x", (int)c));
                else result.append(c);
            }
            return result.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream().map(e -> encode(e.getKey().toString()) + ":" + encode(e.getValue())).toList()) + "}";
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(Json::encode).toList()) + "]";
        return value.toString();
    }
}
