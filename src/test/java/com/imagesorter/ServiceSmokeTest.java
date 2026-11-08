package com.imagesorter;

import com.imagesorter.service.ImageDownloadService;
import com.imagesorter.service.ImageSortService;
import com.imagesorter.service.SortPattern;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("image-service-test-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=");
            Path source = Files.createDirectory(root.resolve("source"));
            Files.write(source.resolve("sample.PNG"), png);
            Files.writeString(source.resolve("notes.txt"), "not an image");
            Path output = root.resolve("sorted");
            var sort = new ImageSortService();
            Map<String, String> tags = Map.of("series", "Series", "character", "Hero", "content", "Portrait", "artist", "Artist", "album", "Album");
            List<Integer> progress = new ArrayList<>();
            int[] manual = {0};
            ImageSortService.Interaction skip = new ImageSortService.Interaction() {
                public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) { manual[0]++; return tags; }
                public ImageSortService.Decision conflict(Path incoming, Path existing, boolean duplicate) { return ImageSortService.Decision.SKIP; }
            };
            check(sort.sort(source, output, SortPattern.DEFAULT, image -> { throw new java.io.IOException("AI unavailable"); }, skip,
                    message -> {}, (done, total) -> progress.add(done)) == 1, "manual fallback move");
            check(manual[0] == 1 && !Files.exists(source.resolve("sample.PNG")), "manual fallback and original moved");
            Path existing = output.resolve("Artist/Album/Hero - 01.PNG");
            check(java.util.Arrays.equals(png, Files.readAllBytes(existing)), "move contents");
            Files.write(source.resolve("duplicate.PNG"), png);
            check(sort.sort(source, output, SortPattern.DEFAULT, image -> tags, skip, message -> {}, (done, total) -> {}) == 0, "duplicate skip");
            check(Files.exists(source.resolve("duplicate.PNG")), "skipped source preserved");
            ImageSortService.Interaction keep = new ImageSortService.Interaction() {
                public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) { return tags; }
                public ImageSortService.Decision conflict(Path incoming, Path target, boolean duplicate) { return ImageSortService.Decision.KEEP_BOTH; }
            };
            check(sort.sort(source, output, SortPattern.DEFAULT, image -> tags, keep, message -> {}, (done, total) -> {}) == 1, "keep both");
            check(Files.exists(output.resolve("Artist/Album/Hero - 01 (2).PNG")), "unique collision name");
            Files.write(source.resolve("replacement.PNG"), png);
            int[] prompts = {0};
            ImageSortService.Interaction replace = new ImageSortService.Interaction() {
                public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) { return tags; }
                public ImageSortService.Decision conflict(Path incoming, Path target, boolean duplicate) { prompts[0]++; return ImageSortService.Decision.REPLACE; }
            };
            check(sort.sort(source, output, SortPattern.DEFAULT, image -> tags, replace, message -> {}, (done, total) -> {}) == 1, "duplicate replace");
            check(prompts[0] == 1 && !Files.exists(source.resolve("replacement.PNG")), "single replacement prompt");
            check(progress.equals(List.of(0, 1)), "determinate progress");
            Files.write(source.resolve("different.PNG"), new byte[]{1, 2, 3});
            check(sort.sort(source, output, SortPattern.DEFAULT, image -> tags, replace, message -> {}, (done, total) -> {}) == 1, "filename conflict replace");
            for (String invalid : List.of("../{{filename}}", "/absolute", "C:\\absolute", "{{missing}}", "{{artist}", "folder/", "CON")) {
                try { SortPattern.resolve(invalid, tags, "sample.jpg", 1); throw new AssertionError("Invalid pattern accepted"); }
                catch (IllegalArgumentException expected) { }
            }
            Path recursiveSource = Files.createDirectories(root.resolve("recursive"));
            Path nested = Files.createDirectories(recursiveSource.resolve("nested/deeper"));
            Files.write(nested.resolve("nested.png"), new byte[]{7, 8, 9});
            Files.write(recursiveSource.resolve("top.png"), new byte[]{10, 11, 12});
            Path recursiveOutput = Files.createDirectories(recursiveSource.resolve("output"));
            Files.write(recursiveOutput.resolve("previous.png"), new byte[]{13, 14, 15});
            List<Integer> recursiveProgress = new ArrayList<>();
            check(sort.sort(recursiveSource, recursiveOutput, "{{filename}}", image -> tags, keep,
                    message -> {}, (done, total) -> {}) == 1, "default excludes nested files");
            check(Files.exists(nested.resolve("nested.png")), "nested source untouched by default");
            check(sort.sort(recursiveSource, recursiveOutput, "{{filename}}", true, image -> tags, keep,
                    message -> {}, (done, total) -> recursiveProgress.add(total)) == 1, "recursive scan moves nested image");
            check(!Files.exists(nested.resolve("nested.png")) && Files.exists(recursiveOutput.resolve("nested.png")), "nested move output");
            check(recursiveProgress.equals(List.of(1, 1)), "nested output excluded from scan and progress");
            check(Files.exists(recursiveOutput.resolve("previous.png")) && Files.exists(recursiveOutput.resolve("top.png")), "existing output untouched");
            checkRouting(root, sort, tags);
            server.createContext("/upload/image", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] body = "{\"name\":\"uploaded.png\",\"subfolder\":\"\"}".getBytes();
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/prompt", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes());
                byte[] body = (request.contains("uploaded.png") ? "{\"prompt_id\":\"test\"}" : "{}").getBytes();
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/history/test", exchange -> {
                String text = com.imagesorter.service.Json.encode(Map.of("series", "Series", "character", List.of("A", "B"), "content", "Portrait", "artist", "Artist", "album", "Album", "rating", "nsfw", "tags", List.of("erotic_art", "fantasy")));
                byte[] body = com.imagesorter.service.Json.encode(Map.of("test", Map.of("outputs", Map.of("9", Map.of("text", List.of(text)))))).getBytes();
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/image", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, png.length);
                try (var body = exchange.getResponseBody()) { body.write(png); }
            });
            server.createContext("/page", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, 1);
                try (var body = exchange.getResponseBody()) { body.write(65); }
            });
            server.start();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Path workflow = root.resolve("workflow.json");
            Files.writeString(workflow, "{\"1\":{\"inputs\":{\"image\":\"{{image}}\"},\"class_type\":\"LoadImage\"}}");
            Map<String, String> recognized = new com.imagesorter.service.ComfyTagger(base, workflow, "9").identify(existing);
            check(recognized.get("character").equals("group") && recognized.get("artist").equals("Artist"), "ComfyUI workflow and group recognition");
            check(recognized.get("rating").equals("nsfw") && recognized.get("tags").equals("erotic_art, fantasy"), "ComfyUI rating and routing tags preserved");
            Path urls = root.resolve("urls.txt");
            Files.writeString(urls, "# comment\n\nfile:///invalid\n" + base + "/page\n" + base + "/missing\n" + base + "/image\n" + base + "/image\n");
            List<String> logs = new ArrayList<>();
            Path downloads = root.resolve("downloads");
            check(new ImageDownloadService().download(List.of(urls), downloads, logs::add) == 2, "successful downloads");
            check(logs.stream().filter(s -> s.startsWith("Failed:")).count() == 3, "failures logged and continued");
            try (var files = Files.list(downloads)) {
                List<Path> saved = files.toList();
                check(saved.size() == 2, "unique filenames");
                for (Path file : saved) check(java.util.Arrays.equals(png, Files.readAllBytes(file)), "download contents");
            }
            System.out.println("PASS: moving, manual fallback, duplicates, conflicts, progress, ComfyUI, validation, downloads.");
        } finally {
            server.stop(0);
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void checkRouting(Path root, ImageSortService sorter, Map<String, String> metadata) throws Exception {
        Path source = Files.createDirectories(root.resolve("routing-source"));
        Path fallback = source.resolve("default"), specific = source.resolve("specific"), adult = source.resolve("adult");
        var routes = List.of(new com.imagesorter.service.OutputDestination(fallback, java.util.Set.of()),
                new com.imagesorter.service.OutputDestination(specific, java.util.Set.of("nsfw", "erotic_art")),
                new com.imagesorter.service.OutputDestination(adult, java.util.Set.of("nsfw")));
        Files.createDirectories(fallback); Files.createDirectories(specific); Files.createDirectories(adult);
        Files.write(fallback.resolve("previous.png"), new byte[]{31});
        Files.write(specific.resolve("previous.png"), new byte[]{32}); Files.write(adult.resolve("previous.png"), new byte[]{33});
        Files.write(source.resolve("a.png"), new byte[]{31}); Files.write(source.resolve("b.png"), new byte[]{34});
        Files.write(source.resolve("c.png"), new byte[]{35}); Files.write(source.resolve("d.png"), new byte[]{36});
        var tagger = (ImageSortService.Tagger) image -> {
            Map<String, String> result = new java.util.HashMap<>(metadata);
            switch(image.getFileName().toString()) {
                case "a.png" -> { result.put("rating", "NSFW"); result.put("tags", "erotic_art, fantasy"); }
                case "b.png" -> result.put("rating", "nsfw");
                case "c.png" -> result.put("rating", "sfw");
                default -> { }
            }
            return result;
        };
        var interaction = new ImageSortService.Interaction() {
            public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) {
                var result = new java.util.HashMap<>(metadata); result.put("rating", "nsfw"); return result;
            }
            public ImageSortService.Decision conflict(Path incoming, Path existing, boolean duplicate) {
                throw new AssertionError("Unexpected conflict across routing destinations: " + existing);
            }
        };
        List<Integer> totals = new ArrayList<>();
        check(sorter.sort(source, routes, "{{series}}/{{filename}}", true, tagger, interaction, message -> {},
                (done, total) -> totals.add(total)) == 4, "multi-folder routing count");
        check(Files.exists(specific.resolve("Series/a.png")), "all-tags match uses first matching destination");
        check(Files.exists(adult.resolve("Series/b.png")), "partial match falls through to next rule");
        check(Files.exists(fallback.resolve("Series/c.png")) && Files.exists(fallback.resolve("Series/d.png")), "default fallback");
        check(totals.equals(List.of(4, 4, 4, 4, 4)), "all nested output folders excluded");
        Files.write(source.resolve("duplicate.png"), new byte[]{31});
        int[] duplicates = {0};
        var skipDuplicate = new ImageSortService.Interaction() {
            public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) { return metadata; }
            public ImageSortService.Decision conflict(Path incoming, Path existing, boolean duplicate) {
                check(duplicate && existing.startsWith(specific), "duplicate detection uses selected destination");
                duplicates[0]++; return ImageSortService.Decision.SKIP;
            }
        };
        Map<String, String> adultTags = new java.util.HashMap<>(metadata); adultTags.put("tags", "NSFW, erotic_art");
        check(sorter.sort(source, routes, "{{filename}}", true, image -> adultTags, skipDuplicate, message -> {}, (done,total)->{}) == 0
                && duplicates[0] == 1 && Files.exists(source.resolve("duplicate.png")), "routed duplicate skip preserves source");
        Files.delete(source.resolve("duplicate.png"));
        Files.write(source.resolve("manual.png"), new byte[]{37});
        check(sorter.sort(source, routes, "{{filename}}", true, image -> { throw new java.io.IOException("Manual required"); },
                interaction, message -> {}, (done,total)->{}) == 1 && Files.exists(adult.resolve("manual.png")), "manual tags choose routing destination");
        try {
            sorter.sort(source, List.of(routes.get(0), new com.imagesorter.service.OutputDestination(adult, java.util.Set.of())),
                    "{{filename}}", true, tagger, interaction, message -> {}, (done,total)->{});
            throw new AssertionError("Additional destination without tags accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            sorter.sort(source, List.of(routes.get(0), new com.imagesorter.service.OutputDestination(source, java.util.Set.of("sfw"))),
                    "{{filename}}", true, tagger, interaction, message -> {}, (done,total)->{});
            throw new AssertionError("Source used as routed destination");
        } catch (java.io.IOException expected) { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
