package com.imagesorter.service;

import java.nio.file.Path;
import java.util.*;

/** First entry is the fallback; remaining entries are checked in display order. */
public record OutputDestination(Path folder, Set<String> requiredTags) {
    public OutputDestination {
        Objects.requireNonNull(folder, "Choose an output folder.");
        requiredTags = Set.copyOf(requiredTags.stream().map(OutputDestination::normalize).toList());
        if (requiredTags.contains("")) throw new IllegalArgumentException("Destination tags cannot be blank.");
    }
    public static Path select(List<OutputDestination> destinations, Map<String, String> tags) {
        validate(destinations);
        Set<String> labels = labels(tags);
        return destinations.stream().skip(1).filter(d -> labels.containsAll(d.requiredTags()))
                .findFirst().orElse(destinations.get(0)).folder();
    }
    public static void validate(List<OutputDestination> destinations) {
        if (destinations.isEmpty() || !destinations.get(0).requiredTags().isEmpty())
            throw new IllegalArgumentException("The first destination must be the default folder without tags.");
        if (destinations.stream().skip(1).anyMatch(d -> d.requiredTags().isEmpty()))
            throw new IllegalArgumentException("Select at least one tag for each additional destination.");
    }
    public static Set<String> labels(Map<String, String> tags) {
        Set<String> labels = new HashSet<>();
        tags.forEach((key, value) -> {
            if (value == null || value.isBlank() || normalize(value).equals("unknown")) return;
            // Metadata placeholder names match the presence of recognized metadata.
            if (ImageSortService.TAGS.contains(key)) labels.add(normalize(key));
            labels.add(normalize(value));
            for (String label : value.split("[,;]")) if (!label.isBlank()) labels.add(normalize(label));
        });
        return labels;
    }
    private static String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT); }
}
