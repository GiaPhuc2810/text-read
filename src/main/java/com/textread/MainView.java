package com.textread;

import com.textread.model.AppSettings;
import com.textread.model.ReadRegion;
import com.textread.model.ReadingMode;
import com.textread.repository.SettingsRepository;
import com.textread.service.DebugLogger;
import com.textread.service.ReadingOrchestrator;
import com.textread.service.detection.AdFilterMethod;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainView {
    private static final String FILTER_OFF = "Off";
    private static final String FILTER_AI = "AI Heuristic";
    private static final String FILTER_TEMPLATE = "Template Based";
    private static final String FILTER_BOTH = "AI + Template";

    private final AppSettings settings;
    private final ReadingOrchestrator orchestrator;
    private final SettingsRepository settingsRepository;

    public MainView(AppSettings settings, ReadingOrchestrator orchestrator, SettingsRepository settingsRepository) {
        this.settings = settings;
        this.orchestrator = orchestrator;
        this.settingsRepository = settingsRepository;
    }

    public Parent build() {
        Label title = new Label("Text Read Controller");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Slider speechRate = createSlider(80, 260, settings.getSpeechRate());
        Slider volume = createSlider(0, 100, settings.getVolume());

        Label speechValue = new Label((int) speechRate.getValue() + "");
        Label volumeValue = new Label((int) volume.getValue() + "");

        speechRate.valueProperty().addListener((obs, oldVal, newVal) -> {
            int value = newVal.intValue();
            speechValue.setText(String.valueOf(value));
            orchestrator.updateSpeechRate(value);
        });
        volume.valueProperty().addListener((obs, oldVal, newVal) -> {
            int value = newVal.intValue();
            volumeValue.setText(String.valueOf(value));
            orchestrator.updateVolume(value);
        });

        Button startBtn = new Button("Start");
        Button stopBtn = new Button("Stop");
        Button muteBtn = new Button("Mute/Unmute");
        Button testVoiceBtn = new Button("Test Voice");
        Button settingsBtn = new Button("Advanced Settings");
        Button clearLogBtn = new Button("Clear Log");

        startBtn.setOnAction(e -> orchestrator.start());
        stopBtn.setOnAction(e -> orchestrator.stop());
        muteBtn.setOnAction(e -> orchestrator.toggleMute());
        testVoiceBtn.setOnAction(e -> orchestrator.testVoice());
        settingsBtn.setOnAction(e -> openSettingsWindow());

        HBox speechBox = new HBox(10, new Label("Speech speed"), speechRate, speechValue);
        HBox volumeBox = new HBox(10, new Label("Volume"), volume, volumeValue);
        HBox actionBox = new HBox(10, startBtn, stopBtn, muteBtn, testVoiceBtn);
        HBox logActionBox = new HBox(10, settingsBtn, clearLogBtn);

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(10);
        logArea.setPromptText("Debug logs will appear here...");
        for (String line : DebugLogger.recent()) {
            logArea.appendText(line + System.lineSeparator());
        }
        DebugLogger.addListener(line -> Platform.runLater(() ->
                logArea.appendText(line + System.lineSeparator())
        ));
        clearLogBtn.setOnAction(e -> {
            DebugLogger.clear();
            logArea.clear();
        });

        VBox root = new VBox(12, title, speechBox, volumeBox, actionBox, logActionBox, logArea);
        root.setPadding(new Insets(16));
        return root;
    }

    private Slider createSlider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setPrefWidth(240);
        return slider;
    }

    private void openSettingsWindow() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Advanced Settings");

        Label regionLabel = new Label(formatRegion(settings.getReadRegion()));
        Button selectRegionBtn = new Button("Select Scan Area");
        selectRegionBtn.setOnAction(e -> openRegionSelector(dialog, regionLabel));

        javafx.scene.control.TextField tessDataPath = new javafx.scene.control.TextField(settings.getTessDataPath());

        ChoiceBox<String> languageChoice = new ChoiceBox<>(FXCollections.observableArrayList("Tieng Viet", "English"));
        languageChoice.setValue("eng".equalsIgnoreCase(settings.getOcrLanguage()) ? "English" : "Tieng Viet");
        ChoiceBox<String> readingModeChoice = new ChoiceBox<>(FXCollections.observableArrayList("Auto", "Comic", "Article"));
        readingModeChoice.setValue(toModeLabel(settings.getReadingMode()));

        ChoiceBox<String> adFilterChoice = new ChoiceBox<>(
                FXCollections.observableArrayList(FILTER_OFF, FILTER_AI, FILTER_TEMPLATE, FILTER_BOTH)
        );
        adFilterChoice.setValue(resolveFilterChoiceFromSettings());

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.addRow(0, new Label("Scan Area"), regionLabel, selectRegionBtn);
        grid.addRow(1, new Label("Tessdata Path"), tessDataPath);
        grid.addRow(2, new Label("Language"), languageChoice);
        grid.addRow(3, new Label("Reading Mode"), readingModeChoice);
        grid.addRow(4, new Label("Ad Filter"), adFilterChoice);

        VBox root = new VBox(12, grid);
        root.setPadding(new Insets(16));

        Button save = new Button("Save");
        save.setOnAction(e -> {
            settings.setTessDataPath(tessDataPath.getText());
            applyLanguageChoice(languageChoice.getValue());
            settings.setReadingMode(fromModeLabel(readingModeChoice.getValue()));
            applyFilterChoice(adFilterChoice.getValue());

            orchestrator.refreshSettings();
            settingsRepository.save(settings);
            dialog.close();
        });

        root.getChildren().add(save);
        dialog.setScene(new Scene(root, 620, 280));
        dialog.showAndWait();
    }

    private void applyLanguageChoice(String value) {
        if ("English".equals(value)) {
            settings.setOcrLanguage("eng");
            settings.setVoiceName("en");
            return;
        }
        settings.setOcrLanguage("vie");
        settings.setVoiceName("vi");
    }

    private String toModeLabel(ReadingMode mode) {
        if (mode == null) {
            return "Auto";
        }
        return switch (mode) {
            case COMIC -> "Comic";
            case ARTICLE -> "Article";
            default -> "Auto";
        };
    }

    private ReadingMode fromModeLabel(String label) {
        if ("Comic".equalsIgnoreCase(label)) {
            return ReadingMode.COMIC;
        }
        if ("Article".equalsIgnoreCase(label)) {
            return ReadingMode.ARTICLE;
        }
        return ReadingMode.AUTO;
    }

    private String resolveFilterChoiceFromSettings() {
        boolean ai = settings.getAdFilters().contains(AdFilterMethod.AI_HEURISTIC);
        boolean template = settings.getAdFilters().contains(AdFilterMethod.TEMPLATE_BASED);
        if (ai && template) {
            return FILTER_BOTH;
        }
        if (ai) {
            return FILTER_AI;
        }
        if (template) {
            return FILTER_TEMPLATE;
        }
        return FILTER_OFF;
    }

    private void applyFilterChoice(String value) {
        settings.getAdFilters().clear();
        if (FILTER_AI.equals(value)) {
            settings.getAdFilters().add(AdFilterMethod.AI_HEURISTIC);
        } else if (FILTER_TEMPLATE.equals(value)) {
            settings.getAdFilters().add(AdFilterMethod.TEMPLATE_BASED);
        } else if (FILTER_BOTH.equals(value)) {
            settings.getAdFilters().add(AdFilterMethod.AI_HEURISTIC);
            settings.getAdFilters().add(AdFilterMethod.TEMPLATE_BASED);
        }
    }

    private String formatRegion(ReadRegion region) {
        return "x=" + region.x() + ", y=" + region.y() + ", w=" + region.width() + ", h=" + region.height();
    }

    private void openRegionSelector(Stage owner, Label regionLabel) {
        Stage overlay = new Stage(StageStyle.TRANSPARENT);
        overlay.initOwner(owner);
        overlay.initModality(Modality.APPLICATION_MODAL);
        overlay.setAlwaysOnTop(true);

        Rectangle2D bounds = Screen.getPrimary().getBounds();
        Pane root = new Pane();
        root.setStyle("-fx-background-color: rgba(0,0,0,0.28);");
        root.setPrefSize(bounds.getWidth(), bounds.getHeight());

        Label tip = new Label("Drag mouse to select scan area. Release to apply, ESC to cancel.");
        tip.setTextFill(Color.WHITE);
        tip.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        tip.setLayoutX(20);
        tip.setLayoutY(16);

        Rectangle selection = new Rectangle();
        selection.setStroke(Color.DODGERBLUE);
        selection.setFill(Color.color(0.2, 0.55, 1.0, 0.25));
        selection.getStrokeDashArray().addAll(7.0, 5.0);

        final double[] startX = {0};
        final double[] startY = {0};

        root.setOnMousePressed(e -> {
            startX[0] = e.getSceneX();
            startY[0] = e.getSceneY();
            selection.setX(startX[0]);
            selection.setY(startY[0]);
            selection.setWidth(0);
            selection.setHeight(0);
        });

        root.setOnMouseDragged(e -> {
            double x = Math.min(startX[0], e.getSceneX());
            double y = Math.min(startY[0], e.getSceneY());
            double w = Math.abs(e.getSceneX() - startX[0]);
            double h = Math.abs(e.getSceneY() - startY[0]);
            selection.setX(x);
            selection.setY(y);
            selection.setWidth(w);
            selection.setHeight(h);
        });

        root.setOnMouseReleased(e -> {
            if (selection.getWidth() < 40 || selection.getHeight() < 30) {
                overlay.close();
                return;
            }
            settings.setReadRegion(new ReadRegion(
                    (int) selection.getX(),
                    (int) selection.getY(),
                    (int) selection.getWidth(),
                    (int) selection.getHeight()
            ));
            regionLabel.setText(formatRegion(settings.getReadRegion()));
            orchestrator.refreshSettings();
            overlay.close();
        });

        root.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> overlay.close();
                default -> {
                }
            }
        });

        root.getChildren().addAll(selection, tip);
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight(), Color.TRANSPARENT);
        overlay.setScene(scene);
        overlay.setX(bounds.getMinX());
        overlay.setY(bounds.getMinY());
        overlay.show();
        root.requestFocus();
    }
}
