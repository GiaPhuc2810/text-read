package com.textread.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class DebugLogger {
    private static final int MAX_LINES = 500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> LINES = new ArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

    private DebugLogger() {
    }

    public static synchronized void log(String message) {
        String line = "[" + LocalTime.now().format(TIME_FORMAT) + "] " + message;
        LINES.add(line);
        if (LINES.size() > MAX_LINES) {
            LINES.remove(0);
        }
        for (Consumer<String> listener : LISTENERS) {
            listener.accept(line);
        }
    }

    public static synchronized List<String> recent() {
        return new ArrayList<>(LINES);
    }

    public static synchronized void clear() {
        LINES.clear();
    }

    public static void addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
    }
}
