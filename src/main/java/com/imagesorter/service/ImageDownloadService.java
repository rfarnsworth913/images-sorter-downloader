package com.imagesorter.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Downloads direct public image URLs; site page extraction belongs in a future adapter. */
public final class ImageDownloadService {
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif",
            "image/webp", ".webp", "image/bmp", ".bmp", "image/tiff", ".tiff",
            "image/avif", ".avif", "image/svg+xml", ".svg");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public int download(List<Path> lists, Path destination, Consumer<String> log) throws IOException, InterruptedException {
        if (lists.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one URL list file.");
        }
        Files.createDirectories(destination);
        int count = 0;
        for (Path list : lists) {
            log.accept("Reading: " + list.getFileName());
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                String url = line.strip();
                if (url.isEmpty() || url.startsWith("#")) continue;
                try {
                    URI uri = URI.create(url);
                    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                        throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported.");
                    }
                    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).GET().build();
                    // Buffer at most MAX_BYTES in the subscriber, keeping the request timeout active during transfer.
                    HttpResponse<byte[]> response = client.send(request, info ->
                            new LimitedBodySubscriber(MAX_BYTES));
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    String type = response.headers().firstValue("Content-Type").orElse("")
                            .split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                    String extension = TYPES.get(type);
                    if (extension == null) throw new IOException("Unsupported image Content-Type: " + type);
                    if (response.body().length == 0) throw new IOException("Empty image response.");
                    Path output = Files.createTempFile(destination, "image-", extension);
                    try {
                        Files.write(output, response.body());
                    } catch (IOException failure) {
                        Files.deleteIfExists(output);
                        throw failure;
                    }
                    count++;
                    log.accept("Saved: " + output.getFileName());
                } catch (IOException | IllegalArgumentException failure) {
                    log.accept("Failed: " + url + " — " + failure.getMessage());
                }
            }
        }
        return count;
    }
}
