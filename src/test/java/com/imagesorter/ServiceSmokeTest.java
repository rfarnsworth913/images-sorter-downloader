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
                String text = com.imagesorter.service.Json.encode(Map.of("series", "Series", "character", List.of("A", "B"), "content", "Portrait", "artist", "Artist", "album", "Album"));
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
