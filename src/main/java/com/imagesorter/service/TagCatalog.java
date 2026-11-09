package com.imagesorter.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Editable filename placeholders and image labels shared by every selector. */
public final class TagCatalog {
    public record Entry(String type, String parent, String tag) { }
    private final List<Entry> entries;
    private final Path file;
    public TagCatalog(List<Entry> entries) { this(entries, Path.of("data", "tags.csv")); }
    private TagCatalog(List<Entry> entries, Path file) { this.entries = new ArrayList<>(entries); this.file = file; }
    public synchronized List<Entry> entries() { return List.copyOf(entries); }
    public static TagCatalog load() throws IOException { return load(Path.of("data", "tags.csv")); }
    public static TagCatalog load(Path external) throws IOException {
        InputStream stream = Files.exists(external) ? Files.newInputStream(external) : TagCatalog.class.getResourceAsStream("/data/tags.csv");
        if (stream == null) throw new IOException("Missing tag library: " + external);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.replace("\uFEFF", "").equals("type,parent,tag")) throw new IOException("Tag CSV must start with type,parent,tag");
            List<Entry> entries = new ArrayList<>(); Set<String> unique = new HashSet<>();
            int number = 1; String line;
            while ((line = reader.readLine()) != null) {
                number++; if (line.isBlank()) continue;
                List<String> cells = parseRow(line);
                if (cells.size() != 3) throw new IOException("Expected three columns at tag CSV line " + number);
                String type = cells.get(0).strip(), parent = cells.get(1).strip(), tag = cells.get(2).strip();
                boolean validTag = type.equals("custom") ? validCustom(tag) : tag.matches("[a-zA-Z0-9_]+|#{1,9}");
                if (!Set.of("pattern", "category", "content", "custom").contains(type) || !validTag
                        || (type.equals("content") ? !parent.matches("[a-zA-Z0-9_]+") : !parent.isEmpty()))
                    throw new IOException("Invalid tag CSV entry at line " + number);
                if (!unique.add(type + ":" + parent + ":" + tag.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate tag at line " + number);
                entries.add(new Entry(type, parent, tag));
            }
            Set<String> categories = new HashSet<>();
            entries.stream().filter(e -> e.type().equals("category")).forEach(e -> categories.add(e.tag()));
            for (Entry entry : entries) if (entry.type().equals("content") && !categories.contains(entry.parent())) throw new IOException("Unknown parent category: " + entry.parent());
            return new TagCatalog(entries, external);
        }
    }
    public synchronized List<String> knownTags() { return entries.stream().map(Entry::tag).distinct().toList(); }
    public synchronized List<String> patterns() { return entries.stream().filter(e -> e.type().equals("pattern")).map(Entry::tag).toList(); }
    public static boolean validCustom(String text) {
        return !text.isBlank() && text.equals(text.strip()) && text.length() <= 100
                && !text.matches(".*[,;\\p{Cntrl}{}].*");
    }
    /** Persist first; a failed write never leaves a phantom tag in the UI. */
    public synchronized String create(String text) throws IOException {
        String tag = text.strip();
        for (String existing : knownTags()) if (existing.equalsIgnoreCase(tag)) return existing;
        if (!validCustom(tag)) throw new IllegalArgumentException("Use 1-100 characters, without commas, semicolons, braces, or control characters.");
        List<Entry> updated = new ArrayList<>(entries); updated.add(new Entry("custom", "", tag));
        Path absolute = file.toAbsolutePath(); Files.createDirectories(absolute.getParent());
        StringBuilder csv = new StringBuilder("type,parent,tag\n");
        for (Entry entry : updated) csv.append(escape(entry.type())).append(',').append(escape(entry.parent())).append(',').append(escape(entry.tag())).append('\n');
        Path temporary = Files.createTempFile(absolute.getParent(), "tags-", ".csv");
        try {
            Files.writeString(temporary, csv, StandardCharsets.UTF_8);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        entries.add(new Entry("custom", "", tag)); return tag;
    }
    private static String escape(String text) { return text.indexOf('"') >= 0 || text.indexOf(',') >= 0 ? "\"" + text.replace("\"", "\"\"") + "\"" : text; }
    private static List<String> parseRow(String line) throws IOException {
        List<String> cells = new ArrayList<>(); int pos = 0;
        while (true) {
            StringBuilder cell = new StringBuilder();
            if (pos < line.length() && line.charAt(pos) == '"') {
                pos++; boolean closed = false;
                while (pos < line.length()) {
                    char c = line.charAt(pos++);
                    if (c != '"') cell.append(c);
                    else if (pos < line.length() && line.charAt(pos) == '"') { cell.append('"'); pos++; }
                    else { closed = true; break; }
                }
                if (!closed || (pos < line.length() && line.charAt(pos) != ',')) throw new IOException("Invalid CSV quoting");
            } else {
                while (pos < line.length() && line.charAt(pos) != ',') {
                    char c = line.charAt(pos++); if (c == '"') throw new IOException("Invalid CSV quote"); cell.append(c);
                }
            }
            cells.add(cell.toString());
            if (pos == line.length()) break;
            pos++;
            if (pos == line.length()) { cells.add(""); break; }
        }
        return cells;
    }
}
