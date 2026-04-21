package com.textread.service.ai;

public interface ContentClassifier {
    LineClassification classify(String line, ClassificationContext context);
}
