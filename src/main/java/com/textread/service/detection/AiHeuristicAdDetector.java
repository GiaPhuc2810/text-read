package com.textread.service.detection;

import java.util.List;
import java.util.Locale;

public class AiHeuristicAdDetector {
    private static final List<String> AD_KEYWORDS = List.of(
            "sponsored", "advertisement", "promo", "buy now", "limited offer", "install app", "shop now"
    );

    public boolean looksLikeAd(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        for (String keyword : AD_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return normalized.matches(".*\\b\\d{1,3}%\\s*off\\b.*");
    }
}
