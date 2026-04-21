package com.textread.service.detection;

import java.util.List;
import java.util.Locale;

public class TemplateAdDetector {
    private static final List<String> TEMPLATES = List.of(
            "[ad]", "banner", "click here", "sponsor", "watch now"
    );

    public boolean matchesTemplate(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return TEMPLATES.stream().anyMatch(normalized::contains);
    }
}
