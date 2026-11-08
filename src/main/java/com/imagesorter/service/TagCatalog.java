package com.imagesorter.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Shared filename placeholders and a separate, editable content vocabulary. */
public record TagCatalog(List<Entry> entries) {
    public record Entry(String type, String parent, String tag) { }
    public TagCatalog { entries = List.copyOf(entries); }
    public static TagCatalog load() throws IOException {
        Path external = Path.of("data", "tags.csv");
        InputStream stream = Files.exists(external) ? Files.newInputStream(external)
                : TagCatalog.class.getResourceAsStream("/data/tags.csv");
        if (stream == null) throw new IOException("Missing tag library: data/tags.csv");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.replace("\uFEFF", "").equals("type,parent,tag"))
                throw new IOException("Tag CSV must start with type,parent,tag");
            List<Entry> entries = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            int lineNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                String[] cells = line.split(",", -1);
                if (cells.length != 3) throw new IOException("Expected three columns at tag CSV line " + lineNumber);
                String type = cells[0].strip(), parent = cells[1].strip(), tag = cells[2].strip();
                if (!Set.of("pattern", "category", "content").contains(type)
                        || !tag.matches("[a-zA-Z0-9_]+|#{1,9}")
                        || (type.equals("content") ? !parent.matches("[a-zA-Z0-9_]+") : !parent.isEmpty()))
                    throw new IOException("Invalid tag CSV entry at line " + lineNumber);
                if (!unique.add(type + ":" + parent + ":" + tag))
                    throw new IOException("Duplicate tag CSV entry at line " + lineNumber);
                entries.add(new Entry(type, parent, tag));
            }
            Set<String> categories = new HashSet<>();
            entries.stream().filter(e -> e.type().equals("category")).forEach(e -> categories.add(e.tag()));
            for (Entry entry : entries)
                if (entry.type().equals("content") && !categories.contains(entry.parent()))
                    throw new IOException("Unknown parent category: " + entry.parent());
            return new TagCatalog(entries);
        }
    }
    public List<String> patterns() {
        return entries.stream().filter(e -> e.type().equals("pattern")).map(Entry::tag).toList();
    }
}
