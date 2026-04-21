package com.textread.service;

import com.textread.model.AppSettings;
import com.textread.service.detection.AdFilterMethod;
import com.textread.service.detection.AiHeuristicAdDetector;
import com.textread.service.detection.TemplateAdDetector;

import java.util.ArrayList;
import java.util.List;

public class AdDetectionService {
    private final AiHeuristicAdDetector aiDetector;
    private final TemplateAdDetector templateDetector;

    public AdDetectionService(AiHeuristicAdDetector aiDetector, TemplateAdDetector templateDetector) {
        this.aiDetector = aiDetector;
        this.templateDetector = templateDetector;
    }

    public String filterText(String text, AppSettings settings) {
        String[] lines = text.split("\\R");
        List<String> accepted = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (isAdLine(line, settings)) {
                continue;
            }
            accepted.add(line.trim());
        }
        return String.join(". ", accepted);
    }

    private boolean isAdLine(String line, AppSettings settings) {
        if (settings.getAdFilters().contains(AdFilterMethod.AI_HEURISTIC) && aiDetector.looksLikeAd(line)) {
            return true;
        }
        if (settings.getAdFilters().contains(AdFilterMethod.TEMPLATE_BASED) && templateDetector.matchesTemplate(line)) {
            return true;
        }
        if (settings.getAdFilters().contains(AdFilterMethod.MANUAL_REGION) && line.length() < 3) {
            return true;
        }
        return false;
    }
}
