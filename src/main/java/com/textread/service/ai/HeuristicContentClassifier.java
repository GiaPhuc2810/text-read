package com.textread.service.ai;

import com.textread.model.ReadingMode;

import java.util.Locale;
import java.util.Set;

public class HeuristicContentClassifier implements ContentClassifier {
    private static final Set<String> NOISE_HINTS = Set.of(
            "nettruyen", "nettruuen", "index.com", "http://", "https://", "www.", "quang cao", "sponsored"
    );

    @Override
    public LineClassification classify(String line, ClassificationContext context) {
        if (line == null || line.isBlank()) {
            return new LineClassification(ContentDecision.SKIP, "blank");
        }
        String compact = line.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase(Locale.ROOT);

        for (String hint : NOISE_HINTS) {
            if (lower.contains(hint)) {
                return new LineClassification(ContentDecision.SKIP, "noise-hint");
            }
        }

        int letters = countMatches(compact, "\\p{L}");
        int digits = countMatches(compact, "\\p{N}");
        if (letters == 0) {
            return new LineClassification(ContentDecision.SKIP, "no-letter");
        }

        ReadingMode mode = context.mode() == null ? ReadingMode.AUTO : context.mode();
        return switch (mode) {
            case COMIC -> classifyComic(compact, letters, digits);
            case ARTICLE -> classifyArticle(compact, letters, digits);
            case AUTO -> classifyAuto(compact, letters, digits);
        };
    }

    private LineClassification classifyComic(String line, int letters, int digits) {
        if (line.length() < 2 || letters < 2) {
            return new LineClassification(ContentDecision.SKIP, "comic-too-short");
        }
        if (digits > letters) {
            return new LineClassification(ContentDecision.SKIP, "comic-digit-heavy");
        }
        return new LineClassification(ContentDecision.KEEP, "comic-keep");
    }

    private LineClassification classifyArticle(String line, int letters, int digits) {
        int words = line.split("\\s+").length;
        if (words < 3 && line.length() < 16) {
            return new LineClassification(ContentDecision.SKIP, "article-too-short");
        }
        if (digits > letters && words < 4) {
            return new LineClassification(ContentDecision.SKIP, "article-digit-heavy");
        }
        return new LineClassification(ContentDecision.KEEP, "article-keep");
    }

    private LineClassification classifyAuto(String line, int letters, int digits) {
        int words = line.split("\\s+").length;
        if (words >= 5) {
            return new LineClassification(ContentDecision.KEEP, "auto-article-like");
        }
        if (line.length() >= 6 && letters >= 3 && digits <= letters) {
            return new LineClassification(ContentDecision.KEEP, "auto-comic-like");
        }
        return new LineClassification(ContentDecision.SKIP, "auto-low-confidence");
    }

    private int countMatches(String value, String pattern) {
        return value.replaceAll("[^" + pattern + "]", "").length();
    }
}
