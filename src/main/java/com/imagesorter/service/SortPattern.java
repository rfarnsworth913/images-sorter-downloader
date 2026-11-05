package com.imagesorter.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared formatting for the UI preview and actual destination filenames. */
public final class SortPattern {
    public static final String DEFAULT = "{{artist}}\\{{album}}\\{{character}} - {{##}}";
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([^{}]+)}}");

    private SortPattern() { }

    public static Path resolve(String pattern, Map<String, String> tags, String filename, int number) {
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        Matcher matcher = TOKEN.matcher(pattern.strip());
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value;
            if (key.matches("#{1,9}")) {
                value = String.format(Locale.ROOT, "%0" + key.length() + "d", number);
            } else if (key.equals("filename")) {
                value = stem;
            } else {
                value = tags.get(key);
                if (value == null) throw new IllegalArgumentException("Unknown tag: {{" + key + "}}");
            }
            validatePart(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        if (result.indexOf("{") >= 0 || result.indexOf("}") >= 0) {
            throw new IllegalArgumentException("Use double braces for tags, for example {{artist}}.");
        }
        String[] parts = result.toString().replace('\\', '/').split("/", -1);
        for (String part : parts) validatePart(part);
        // Patterns describe the filename without its extension; always preserve the source type.
        parts[parts.length - 1] += extension;
        return Path.of(String.join("/", parts));
    }

    private static void validatePart(String value) {
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") || value.endsWith(".")
                || value.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IllegalArgumentException("Tags and path segments must be valid file names, without slashes or '..'.");
        }
    }
}
