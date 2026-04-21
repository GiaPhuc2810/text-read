package com.textread;

import com.textread.model.AppSettings;
import com.textread.repository.SettingsRepository;
import com.textread.service.AdDetectionService;
import com.textread.service.OcrService;
import com.textread.service.ReadingOrchestrator;
import com.textread.service.ScrollService;
import com.textread.service.TtsService;
import com.textread.service.detection.AiHeuristicAdDetector;
import com.textread.service.detection.TemplateAdDetector;
import com.textread.service.tts.GoogleTranslateTtsService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TextReadApplication extends Application {
    private ReadingOrchestrator orchestrator;
    private SettingsRepository settingsRepository;

    @Override
    public void start(Stage stage) {
        String dbUrl = envOrDefault("TEXT_READ_DB_URL",
                "jdbc:sqlserver://LAPTOP-ECBL64KP;instanceName=SQLEXPRESS;databaseName=text-read;encrypt=false;trustServerCertificate=true");
        String dbUser = envOrDefault("TEXT_READ_DB_USER", "phuc");
        String dbPassword = envOrDefault("TEXT_READ_DB_PASSWORD", "123");

        settingsRepository = new SettingsRepository(
                dbUrl,
                dbUser,
                dbPassword
        );

        AppSettings settings = settingsRepository.loadOrDefault();
        normalizeVietnameseDefaults(settings);
        OcrService ocrService = new OcrService();
        TtsService ttsService = createTtsService();
        ScrollService scrollService = new ScrollService();
        AdDetectionService adDetectionService = new AdDetectionService(
                new AiHeuristicAdDetector(),
                new TemplateAdDetector()
        );

        orchestrator = new ReadingOrchestrator(settings, ocrService, ttsService, scrollService, adDetectionService);
        MainView mainView = new MainView(settings, orchestrator, settingsRepository);

        stage.setTitle("Text Read Tool");
        stage.setAlwaysOnTop(true);
        stage.setScene(new Scene(mainView.build(), 440, 360));
        stage.show();
    }

    @Override
    public void stop() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
        if (settingsRepository != null && orchestrator != null) {
            settingsRepository.save(orchestrator.getSettings());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private TtsService createTtsService() {
        return new GoogleTranslateTtsService();
    }

    private void normalizeVietnameseDefaults(AppSettings settings) {
        settings.setMuted(false);
        int currentVolume = settings.getVolume();
        settings.setVolume(currentVolume <= 0 ? 100 : Math.max(0, Math.min(100, currentVolume)));
        if (settings.getTessDataPath() == null || settings.getTessDataPath().isBlank()) {
            settings.setTessDataPath("./tessdata");
        }
        String ocr = settings.getOcrLanguage() == null ? "" : settings.getOcrLanguage().trim().toLowerCase();
        String voice = settings.getVoiceName() == null ? "" : settings.getVoiceName().trim().toLowerCase();
        boolean preferEnglish = "eng".equals(ocr) || "en".equals(voice);
        if (preferEnglish) {
            settings.setOcrLanguage("eng");
            settings.setVoiceName("en");
        } else {
            settings.setOcrLanguage("vie");
            settings.setVoiceName("vi");
        }
        settings.getAdFilters().clear();
    }
}
