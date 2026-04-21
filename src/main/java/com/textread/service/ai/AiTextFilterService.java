package com.textread.service.ai;

import com.textread.model.AppSettings;
import com.textread.model.ReadingMode;
import com.textread.service.DebugLogger;

import java.util.ArrayList;
import java.util.List;

public class AiTextFilterService {
    private final ContentClassifier classifier;

    public AiTextFilterService(ContentClassifier classifier) {
        this.classifier = classifier;
    }

    public String filter(String normalizedText, AppSettings settings) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return "";
        }
        String[] lines = normalizedText.split("\\R");
        List<String> kept = new ArrayList<>();
        ClassificationContext context = new ClassificationContext(
                settings.getReadingMode(),
                settings.getOcrLanguage(),
                settings.getVoiceName()
        );
        for (String line : lines) {
            String compact = line.replaceAll("\\s+", " ").trim();
            if (compact.isBlank()) {
                continue;
            }
            LineClassification result = classifier.classify(compact, context);
            if (result.decision() == ContentDecision.KEEP) {
                kept.add(compact);
            } else {
                DebugLogger.log("AI skip [" + safeMode(settings.getReadingMode()) + "]: " + compact + " (" + result.reason() + ")");
            }
        }
        return String.join(" ", kept).replaceAll("\\s+", " ").trim();
    }

    private String safeMode(ReadingMode mode) {
        return mode == null ? ReadingMode.AUTO.name() : mode.name();
    }
}
