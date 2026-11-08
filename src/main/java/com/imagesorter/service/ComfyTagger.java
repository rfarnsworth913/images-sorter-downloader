package com.imagesorter.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Runs a user-supplied vision workflow; its output node must emit JSON text. */
public final class ComfyTagger implements ImageSortService.Tagger {
    private final URI base;
    private final String workflow, outputNode;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public ComfyTagger(String url, Path workflowFile, String outputNode) throws IOException {
        base = URI.create(url.replaceAll("/+$", "") + "/");
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null)
            throw new IllegalArgumentException("Enter a valid ComfyUI HTTP(S) URL.");
        workflow = Files.readString(workflowFile);
        if (!(Json.parse(workflow) instanceof Map) || !workflow.contains("{{image}}"))
            throw new IllegalArgumentException("Use an API workflow with {{image}} in the image input.");
        this.outputNode = outputNode.strip();
        if (this.outputNode.isEmpty()) throw new IllegalArgumentException("Enter the text output node ID.");
    }
    private Map<?, ?> request(String route, HttpRequest.BodyPublisher body, String type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(route)).timeout(Duration.ofSeconds(45));
        if (body == null) builder.GET(); else builder.header("Content-Type", type).POST(body);
        HttpResponse<byte[]> response = client.send(builder.build(), info -> new LimitedBodySubscriber(4 * 1024 * 1024));
        if (response.statusCode() / 100 != 2) throw new IOException("ComfyUI HTTP " + response.statusCode());
        return (Map<?, ?>) Json.parse(new String(response.body(), StandardCharsets.UTF_8));
    }
    public Map<String, String> identify(Path image) throws Exception {
        String boundary = "sorter" + UUID.randomUUID();
        String name = UUID.randomUUID() + image.getFileName().toString().substring(image.getFileName().toString().lastIndexOf('.'));
        byte[] header = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"" + name
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        Map<?, ?> upload = request("upload/image", HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(header),
                HttpRequest.BodyPublishers.ofFile(image), HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n")), "multipart/form-data; boundary=" + boundary);
        String uploaded = Objects.toString(upload.get("subfolder"), "");
        uploaded = (uploaded.isEmpty() ? "" : uploaded + "/") + Objects.toString(upload.get("name"));
        String escaped = Json.encode(uploaded);
        Object prompt = Json.parse(workflow.replace("{{image}}", escaped.substring(1, escaped.length() - 1)));
        Map<?, ?> queued = request("prompt", HttpRequest.BodyPublishers.ofString(Json.encode(Map.of("prompt", prompt))), "application/json");
        if (!(queued.get("prompt_id") instanceof String id)) throw new IOException("ComfyUI rejected the workflow: " + Json.encode(queued));
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (System.nanoTime() < deadline) {
            Map<?, ?> history = request("history/" + id, null, null);
            if (history.get(id) instanceof Map<?, ?> entry) {
                if (entry.get("status") instanceof Map<?, ?> status && "error".equals(status.get("status_str")))
                    throw new IOException("ComfyUI workflow failed.");
                if (entry.get("outputs") instanceof Map<?, ?> outputs && outputs.get(outputNode) instanceof Map<?, ?> output) {
                    Object text = output.get("text");
                    if (text instanceof List<?> list && !list.isEmpty()) text = list.get(0);
                    if (!(text instanceof String)) throw new IOException("Output node must expose JSON in its text output.");
                    Map<?, ?> values = (Map<?, ?>) Json.parse((String) text);
                    Map<String, String> tags = new LinkedHashMap<>();
                    for (String key : ImageSortService.TAGS) {
                        Object value = values.get(key);
                        if (key.equals("character") && value instanceof List<?> characters)
                            value = characters.size() > 1 ? "group" : characters.isEmpty() ? "" : characters.get(0);
                        if (value instanceof String s) tags.put(key, s.strip());
                    }
                    for (String key : List.of("rating", "tags")) {
                        Object value = values.get(key);
                        if (value instanceof List<?> labels && labels.stream().allMatch(String.class::isInstance))
                            tags.put(key, String.join(", ", labels.stream().map(String.class::cast).toList()));
                        else if (value instanceof String label) tags.put(key, label.strip());
                    }
                    return tags;
                }
                if (entry.get("status") instanceof Map<?, ?> status && Boolean.TRUE.equals(status.get("completed")))
                    throw new IOException("Workflow finished without the configured text output.");
            }
            Thread.sleep(500);
        }
        throw new IOException("ComfyUI tagging timed out after five minutes.");
    }
}
