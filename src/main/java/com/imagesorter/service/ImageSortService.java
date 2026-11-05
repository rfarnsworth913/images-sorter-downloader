package com.imagesorter.service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;

public final class ImageSortService {
    public static final List<String> TAGS = List.of("series", "character", "content", "artist", "album");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff");
    public enum Decision { KEEP_BOTH, REPLACE, SKIP }
    @FunctionalInterface public interface Tagger { Map<String, String> identify(Path image) throws Exception; }
    public interface Interaction {
        Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) throws Exception;
        Decision conflict(Path incoming, Path existing, boolean duplicate) throws Exception;
    }
    public int sort(Path source, Path destination, String series, String character, String content, Consumer<String> log) throws IOException {
        return sort(source, destination, "{{series}}/{{character}}/{{content}}/{{filename}}", Map.of("series", series, "character", character, "content", content), log);
    }
    public int sort(Path source, Path destination, String pattern, Map<String, String> tags, Consumer<String> log) throws IOException {
        Map<String, String> values = new HashMap<>();
        TAGS.forEach(key -> values.put(key, "Unknown"));
        values.putAll(tags);
        try {
            return sort(source, destination, pattern, image -> values, new Interaction() {
                public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) { return values; }
                public Decision conflict(Path incoming, Path existing, boolean duplicate) { return Decision.SKIP; }
            }, log, (done, total) -> {});
        } catch (IOException | IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IOException(e); }
    }
    public int sort(Path source, Path destination, String pattern, Tagger tagger, Interaction interaction,
                    Consumer<String> log, BiConsumer<Integer, Integer> progress) throws Exception {
        if (!Files.isDirectory(source)) throw new IOException("Choose an existing source folder.");
        Map<String, String> sample = new HashMap<>(); TAGS.forEach(key -> sample.put(key, "Sample"));
        SortPattern.resolve(pattern, sample, "sample.jpg", 1);
        Files.createDirectories(destination);
        Path target = destination.toRealPath();
        if (Files.isSameFile(source, target)) throw new IOException("Source and output folders must differ.");
        List<Path> images;
        try (var files = Files.list(source)) { images = files.filter(Files::isRegularFile).filter(ImageSortService::isImage).sorted().toList(); }
        Map<String, List<Path>> hashes = new HashMap<>();
        log.accept("Scanning output for duplicates...");
        try (var files = Files.walk(target)) {
            for (Path file : files.filter(Files::isRegularFile).filter(ImageSortService::isImage).toList()) {
                if (file.toRealPath().startsWith(target)) hashes.computeIfAbsent(hash(file), key -> new ArrayList<>()).add(file);
            }
        }
        int count = 0, sequence = 0;
        progress.accept(0, images.size());
        for (Path file : images) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            sequence++;
            log.accept("Identifying: " + file.getFileName());
            Map<String, String> tags = Map.of();
            String reason = null;
            try { tags = tagger.identify(file); validate(tags, pattern, file, sequence); }
            catch (InterruptedException e) { throw e; }
            catch (Exception e) { reason = e.getMessage(); }
            if (reason != null) {
                log.accept("Manual tags needed: " + file.getFileName() + " (" + reason + ")");
                tags = interaction.manualTags(file, tags == null ? Map.of() : tags, reason);
                if (tags == null) { log.accept("Skipped: " + file.getFileName()); progress.accept(sequence, images.size()); continue; }
                validate(tags, pattern, file, sequence);
            }
            Path output = target.resolve(SortPattern.resolve(pattern, tags, file.getFileName().toString(), sequence)).normalize();
            if (!output.startsWith(target)) throw new IOException("Sorted path must stay inside output folder.");
            // Check every existing parent before creating directories, including symlinks.
            for (Path parent = output.getParent(); parent != null && parent.startsWith(target); parent = parent.getParent()) {
                if (Files.exists(parent) && !parent.toRealPath().startsWith(target)) throw new IOException("Output path escapes through a symbolic link.");
            }
            String fingerprint = hash(file);
            boolean skip = false, replace = false;
            Set<Path> approvedDuplicates = new HashSet<>();
            for (Path existing : new ArrayList<>(hashes.getOrDefault(fingerprint, List.of()))) {
                if (!Files.exists(existing) || Files.isSameFile(file, existing)) continue;
                Decision decision = interaction.conflict(file, existing, true);
                if (decision == Decision.SKIP) { skip = true; break; }
                if (decision == Decision.REPLACE) { output = existing; replace = true; break; }
                approvedDuplicates.add(existing.toAbsolutePath().normalize());
            }
            if (!skip && !replace && Files.exists(output)) {
                if (Files.isSameFile(file, output)) skip = true;
                else {
                    Decision decision = approvedDuplicates.contains(output.toAbsolutePath().normalize())
                            ? Decision.KEEP_BOTH : interaction.conflict(file, output, false);
                    if (decision == Decision.SKIP) skip = true;
                    else if (decision == Decision.KEEP_BOTH) output = unique(output);
                    else replace = true;
                }
            }
            if (skip) log.accept("Skipped: " + file.getFileName());
            else {
                Files.createDirectories(output.getParent());
                if (!output.getParent().toRealPath().startsWith(target)) throw new IOException("Output path escapes the output folder.");
                for (List<Path> entries : hashes.values()) entries.remove(output);
                move(file, output, replace);
                hashes.computeIfAbsent(fingerprint, key -> new ArrayList<>()).add(output);
                count++; log.accept("Moved: " + file.getFileName() + " -> " + target.relativize(output));
            }
            progress.accept(sequence, images.size());
        }
        return count;
    }
    private static void validate(Map<String, String> tags, String pattern, Path image, int sequence) {
        for (String key : List.of("series", "character", "content", "artist"))
            if (tags == null || tags.getOrDefault(key, "").isBlank()) throw new IllegalArgumentException("Missing tag: " + key);
        SortPattern.resolve(pattern, tags, image.getFileName().toString(), sequence);
    }
    private static void move(Path from, Path to, boolean replace) throws IOException {
        if (Files.isSymbolicLink(to)) throw new IOException("Cannot replace a symbolic link: " + to);
        if (replace) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); else Files.move(from, to);
    }
    private static Path unique(Path output) {
        String name = output.getFileName().toString(); int dot = name.lastIndexOf('.');
        for (int i = 2; ; i++) {
            Path candidate = output.resolveSibling(name.substring(0, dot) + " (" + i + ")" + name.substring(dot));
            if (!Files.exists(candidate)) return candidate;
        }
    }
    private static boolean isImage(Path file) {
        String name = file.getFileName().toString(); int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
    private static String hash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(file)) { byte[] buffer = new byte[65536]; int n; while ((n = stream.read(buffer)) >= 0) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
