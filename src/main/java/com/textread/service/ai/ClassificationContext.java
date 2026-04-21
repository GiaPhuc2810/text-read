package com.textread.service.ai;

import com.textread.model.ReadingMode;

public record ClassificationContext(ReadingMode mode, String ocrLanguage, String voiceLanguage) {
}
