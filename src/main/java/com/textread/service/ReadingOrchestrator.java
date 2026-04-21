package com.textread.service;

import com.textread.model.AppSettings;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReadingOrchestrator {
    private static final long STARTUP_SCAN_DELAY_MS = 1200;
    private static final String VIETNAMESE_MARKED_CHARS =
            "\u0103\u00e2\u0111\u00ea\u00f4\u01a1\u01b0"
                    + "\u00e1\u00e0\u1ea3\u00e3\u1ea1\u1eaf\u1eb1\u1eb3\u1eb5\u1eb7\u1ea5\u1ea7\u1ea9\u1eab\u1ead"
                    + "\u00e9\u00e8\u1ebb\u1ebd\u1eb9\u1ebf\u1ec1\u1ec3\u1ec5\u1ec7"
                    + "\u00ed\u00ec\u1ec9\u0129\u1ecb"
                    + "\u00f3\u00f2\u1ecf\u00f5\u1ecd\u1ed1\u1ed3\u1ed5\u1ed7\u1ed9\u1edb\u1edd\u1edf\u1ee1\u1ee3"
                    + "\u00fa\u00f9\u1ee7\u0169\u1ee5\u1ee9\u1eeb\u1eed\u1eef\u1ef1"
                    + "\u00fd\u1ef3\u1ef7\u1ef9\u1ef5";
    private static final String VIETNAMESE_VOWELS =
            "aeiouy"
                    + "\u0103\u00e2\u00ea\u00f4\u01a1\u01b0"
                    + "\u00e1\u00e0\u1ea3\u00e3\u1ea1\u1eaf\u1eb1\u1eb3\u1eb5\u1eb7\u1ea5\u1ea7\u1ea9\u1eab\u1ead"
                    + "\u00e9\u00e8\u1ebb\u1ebd\u1eb9\u1ebf\u1ec1\u1ec3\u1ec5\u1ec7"
                    + "\u00ed\u00ec\u1ec9\u0129\u1ecb"
                    + "\u00f3\u00f2\u1ecf\u00f5\u1ecd\u1ed1\u1ed3\u1ed5\u1ed7\u1ed9\u1edb\u1edd\u1edf\u1ee1\u1ee3"
                    + "\u00fa\u00f9\u1ee7\u0169\u1ee5\u1ee9\u1eeb\u1eed\u1eef\u1ef1"
                    + "\u00fd\u1ef3\u1ef7\u1ef9\u1ef5";
    private static final String FORBIDDEN_VI_CHARS = "fjwz";
    private static final Set<String> VI_COMMON_WORDS = Set.of(
            "la", "cua", "cau", "toi", "ban", "khong", "co", "mot", "voi", "se", "da", "nay", "kia", "trong",
            "di", "\u0111i", "ve", "cho", "nhe", "nua", "anh", "em", "ong", "ba", "gi", "sao", "nhu", "thoi", "roi", "day"
    );
    private static final Set<String> NOISE_MARKERS = Set.of(
            "nettruyen", "nettruuen", "truyen duoc dang", "truyện được đăng", "http://", "https://", "www."
    );

    private final AppSettings settings;
    private final OcrService ocrService;
    private final TtsService ttsService;
    private final ScrollService scrollService;
    private final AdDetectionService adDetectionService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String lastDeliveredText = "";
    private volatile ScheduledFuture<?> readingTask;
    private volatile boolean waitingForReadCompletion = false;
    private volatile long nextAllowedScrollAtMs = 0L;

    public ReadingOrchestrator(
            AppSettings settings,
            OcrService ocrService,
            TtsService ttsService,
            ScrollService scrollService,
            AdDetectionService adDetectionService
    ) {
        this.settings = settings;
        this.ocrService = ocrService;
        this.ttsService = ttsService;
        this.scrollService = scrollService;
        this.adDetectionService = adDetectionService;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            if (readingTask != null) {
                readingTask.cancel(true);
            }
            DebugLogger.log("Start reading");
            waitingForReadCompletion = false;
            readingTask = scheduler.scheduleWithFixedDelay(
                    this::tick,
                    STARTUP_SCAN_DELAY_MS,
                    1800,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public void stop() {
        DebugLogger.log("Stop reading");
        running.set(false);
        if (readingTask != null) {
            readingTask.cancel(true);
            readingTask = null;
        }
        waitingForReadCompletion = false;
        nextAllowedScrollAtMs = 0L;
        ttsService.cancel();
    }

    public void toggleMute() {
        settings.setMuted(!settings.isMuted());
    }

    public void updateSpeechRate(int rate) {
        settings.setSpeechRate(rate);
    }

    public void updateVolume(int volume) {
        settings.setVolume(volume);
    }

    public void testVoice() {
        ttsService.speakAsync("Dang kiem tra am thanh tieng Viet", settings);
    }

    public void refreshSettings() {
        lastDeliveredText = "";
        waitingForReadCompletion = false;
        nextAllowedScrollAtMs = 0L;
    }

    public AppSettings getSettings() {
        return settings;
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
        ttsService.shutdown();
    }

    private void tick() {
        if (!running.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (waitingForReadCompletion) {
            if (ttsService.isIdle()) {
                scrollService.scrollByRegion(settings.getReadRegion());
                DebugLogger.log("Scroll: moved to next region chunk");
                waitingForReadCompletion = false;
                nextAllowedScrollAtMs = now + 700;
            }
            return;
        }

        String rawText = ocrService.readText(settings);
        String filteredText = adDetectionService.filterText(rawText, settings);
        String deliveredText = filteredText.isBlank()
                ? normalizeRawText(rawText, settings)
                : normalizeRawText(filteredText, settings);

        DebugLogger.log("OCR raw: " + preview(rawText));
        DebugLogger.log("OCR normalized: " + preview(deliveredText));

        if (!isLikelyReadable(deliveredText, settings)) {
            DebugLogger.log("Skip: text not readable enough");
            deliveredText = "";
        }

        if (!deliveredText.isBlank() && !Objects.equals(deliveredText, lastDeliveredText)) {
            DebugLogger.log("Speak: " + preview(deliveredText));
            ttsService.speakAsync(deliveredText, settings);
            lastDeliveredText = deliveredText;
            waitingForReadCompletion = true;
            return;
        }

        if (deliveredText.isBlank() && now >= nextAllowedScrollAtMs) {
            scrollService.scrollByRegion(settings.getReadRegion());
            DebugLogger.log("Auto-scroll: no readable text, move and rescan");
            nextAllowedScrollAtMs = now + 700;
        }
    }

    private String normalizeRawText(String rawText, AppSettings settings) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFKC);
        String[] lines = normalized.split("\\R");
        StringBuilder out = new StringBuilder();
        boolean vietnameseMode = isVietnameseMode(settings);

        for (String line : lines) {
            String cleanedLine = cleanLineForSpeech(stripKnownNoiseSegments(line), vietnameseMode);
            if (cleanedLine.isBlank()) {
                continue;
            }
            if (vietnameseMode && !isLikelyVietnameseSentence(cleanedLine)) {
                continue;
            }
            out.append(cleanedLine).append(' ');
        }
        String merged = out.toString().replaceAll("\\s+", " ").trim();
        if (vietnameseMode) {
            merged = cleanupVietnameseOutput(merged);
        }
        return merged;
    }

    private String cleanLineForSpeech(String line, boolean vietnameseMode) {
        if (line == null || line.isBlank()) {
            return "";
        }
        if (vietnameseMode) {
            StringBuilder sb = new StringBuilder(line.length());
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (isAllowedVietnameseTextChar(c)) {
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
            }
            return sb.toString().replaceAll("\\s+", " ").trim();
        }
        String cleaned = line.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String stripKnownNoiseSegments(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        String lower = line.toLowerCase();
        int cutAt = -1;
        for (String marker : NOISE_MARKERS) {
            int idx = lower.indexOf(marker);
            if (idx >= 0 && (cutAt < 0 || idx < cutAt)) {
                cutAt = idx;
            }
        }
        String trimmed = cutAt >= 0 ? line.substring(0, cutAt) : line;
        trimmed = trimmed.replaceAll("(?i)\\b\\S+\\.(com|net|org|io|me|vn)\\b", " ");
        return trimmed;
    }

    private String cleanupVietnameseOutput(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] rawTokens = text.split("\\s+");
        StringBuilder out = new StringBuilder();
        int keptWords = 0;
        for (String token : rawTokens) {
            String word = token.replaceAll("[,\\.\\!\\?;:]+", "");
            if (word.isBlank()) {
                continue;
            }
            if (isDomainLikeToken(word)) {
                continue;
            }
            if (word.length() == 1
                    && !Character.isDigit(word.charAt(0))
                    && !VI_COMMON_WORDS.contains(word.toLowerCase())) {
                continue;
            }
            if (isSuspiciousVietnameseWord(word) && !isStrongVietnameseWord(word)) {
                continue;
            }
            out.append(token).append(' ');
            keptWords++;
        }
        if (keptWords < 2) {
            return "";
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private boolean isDomainLikeToken(String word) {
        String lower = word.toLowerCase();
        return lower.contains("www")
                || lower.contains("http")
                || lower.contains("nettruyen")
                || lower.contains("index")
                || lower.matches(".*\\.(com|net|org|io|me|vn).*");
    }

    private boolean isLikelyVietnameseSentence(String line) {
        String alphaLine = line.replaceAll("[,\\.\\!\\?;:]", " ").replaceAll("\\s+", " ").trim();
        String[] words = alphaLine.split(" ");
        if (words.length < 2) {
            return false;
        }

        int validWords = 0;
        int oneCharWords = 0;
        int markedWords = 0;
        int commonWords = 0;
        int vowelChars = 0;
        int letterChars = 0;
        int strongWords = 0;
        int suspiciousWords = 0;
        int plausibleWords = 0;

        for (String word : words) {
            if (word.isBlank() || !word.matches("[\\p{L}]+")) {
                continue;
            }
            validWords++;
            if (word.length() == 1) {
                oneCharWords++;
            }
            if (containsVietnameseMarkedChars(word)) {
                markedWords++;
            }
            if (VI_COMMON_WORDS.contains(word.toLowerCase())) {
                commonWords++;
            }
            if (isSuspiciousVietnameseWord(word)) {
                suspiciousWords++;
            }
            if (isStrongVietnameseWord(word)) {
                strongWords++;
            }
            if (isPlausibleVietnameseWord(word)) {
                plausibleWords++;
            }
            for (int i = 0; i < word.length(); i++) {
                char c = Character.toLowerCase(word.charAt(i));
                if (Character.isLetter(c)) {
                    letterChars++;
                    if (VIETNAMESE_VOWELS.indexOf(c) >= 0) {
                        vowelChars++;
                    }
                }
            }
        }

        if (validWords < 2) {
            return false;
        }
        double oneCharRatio = oneCharWords / (double) validWords;
        if (oneCharRatio > 0.70) {
            return false;
        }
        if (suspiciousWords >= validWords) {
            return false;
        }
        double vowelRatio = letterChars == 0 ? 0 : (vowelChars / (double) letterChars);

        if (commonWords >= 1) {
            return true;
        }
        if (markedWords >= 1) {
            return strongWords >= 1 || plausibleWords >= 1;
        }
        if (plausibleWords >= 1 && validWords >= 2) {
            return true;
        }
        return strongWords >= 2 && validWords >= 4 && vowelRatio >= 0.26;
    }

    private boolean isLikelyReadable(String text, AppSettings settings) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.length() < 6) {
            return false;
        }
        if (!isVietnameseMode(settings)) {
            return true;
        }

        String[] words = text.replaceAll("[,\\.\\!\\?;:]", " ").split(" ");
        int lettersOnly = 0;
        int marked = 0;
        int commonWords = 0;
        int strongWords = 0;
        int suspiciousWords = 0;
        int plausibleWords = 0;

        for (String word : words) {
            if (word.matches("[\\p{L}]+")) {
                lettersOnly++;
            }
            if (containsVietnameseMarkedChars(word)) {
                marked++;
            }
            if (VI_COMMON_WORDS.contains(word.toLowerCase())) {
                commonWords++;
            }
            if (isStrongVietnameseWord(word)) {
                strongWords++;
            }
            if (isSuspiciousVietnameseWord(word)) {
                suspiciousWords++;
            }
            if (isPlausibleVietnameseWord(word)) {
                plausibleWords++;
            }
        }

        if (lettersOnly < 2) {
            return false;
        }
        if (suspiciousWords >= lettersOnly) {
            return false;
        }
        if (commonWords >= 1) {
            return true;
        }
        if (marked >= 1 && plausibleWords >= 1) {
            return true;
        }
        if (plausibleWords >= 1 && lettersOnly >= 3 && suspiciousWords <= 1) {
            return true;
        }
        return strongWords >= 2;
    }

    private boolean isVietnameseMode(AppSettings settings) {
        return "vie".equalsIgnoreCase(settings.getOcrLanguage())
                || "vi".equalsIgnoreCase(settings.getVoiceName());
    }

    private boolean containsVietnameseMarkedChars(String text) {
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            if (VIETNAMESE_MARKED_CHARS.indexOf(lower.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrongVietnameseWord(String word) {
        String w = word == null ? "" : word.toLowerCase().trim();
        if (!w.matches("[\\p{L}\\p{N}]+")) {
            return false;
        }
        if (w.matches("\\d+")) {
            return true;
        }
        if (w.length() < 2) {
            return false;
        }
        if (containsForbiddenVietnameseChars(w)) {
            return false;
        }
        return containsVietnameseMarkedChars(w) || containsVietnameseVowel(w);
    }

    private boolean isSuspiciousVietnameseWord(String word) {
        String w = word == null ? "" : word.toLowerCase().trim();
        if (w.isBlank()) {
            return true;
        }
        if (w.matches("\\d+")) {
            return false;
        }
        if (!w.matches("[\\p{L}\\p{N}]+")) {
            return true;
        }
        if (containsForbiddenVietnameseChars(w)) {
            return true;
        }
        int markedCount = countMarkedChars(w);
        if (w.length() <= 3 && markedCount >= 2) {
            return true;
        }
        if (w.length() <= 2 && markedCount >= 1 && !VI_COMMON_WORDS.contains(w)) {
            return true;
        }
        if (w.length() == 1 && !containsVietnameseMarkedChars(w)) {
            return true;
        }
        return !containsVietnameseVowel(w) && !containsVietnameseMarkedChars(w);
    }

    private boolean isPlausibleVietnameseWord(String word) {
        String w = word == null ? "" : word.toLowerCase().trim();
        if (!w.matches("[\\p{L}\\p{N}]+")) {
            return false;
        }
        if (w.matches("\\d+")) {
            return true;
        }
        if (VI_COMMON_WORDS.contains(w)) {
            return true;
        }
        if (w.length() <= 1) {
            return false;
        }
        if (containsForbiddenVietnameseChars(w)) {
            return false;
        }
        if (!containsVietnameseVowel(w)) {
            return false;
        }
        if (w.length() <= 3 && countMarkedChars(w) >= 2) {
            return false;
        }
        return true;
    }

    private boolean containsForbiddenVietnameseChars(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (FORBIDDEN_VI_CHARS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean containsVietnameseVowel(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if (VIETNAMESE_VOWELS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedVietnameseTextChar(char c) {
        if (Character.isWhitespace(c)) {
            return true;
        }
        if (Character.isDigit(c)) {
            return true;
        }
        if (",.!?;:".indexOf(c) >= 0) {
            return true;
        }
        char lower = Character.toLowerCase(c);
        if (lower >= 'a' && lower <= 'z') {
            return true;
        }
        if (lower == '\u0111') {
            return true;
        }
        return VIETNAMESE_MARKED_CHARS.indexOf(lower) >= 0;
    }

    private int countMarkedChars(String word) {
        int count = 0;
        for (int i = 0; i < word.length(); i++) {
            if (VIETNAMESE_MARKED_CHARS.indexOf(Character.toLowerCase(word.charAt(i))) >= 0) {
                count++;
            }
        }
        return count;
    }

    private String preview(String value) {
        if (value == null) {
            return "(null)";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "(empty)";
        }
        if (compact.length() <= 180) {
            return compact;
        }
        return compact.substring(0, 180) + "...";
    }
}
