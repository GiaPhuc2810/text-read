package com.textread.service;

import com.textread.model.AppSettings;

public interface TtsService {
    void speakAsync(String text, AppSettings settings);

    void cancel();

    void shutdown();

    default boolean isIdle() {
        return true;
    }
}
