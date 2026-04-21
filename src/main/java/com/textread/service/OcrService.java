package com.textread.service;

import com.textread.model.AppSettings;
import com.textread.model.ReadRegion;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public class OcrService {
    private final Robot robot;
    private volatile boolean missingTessdataWarned = false;

    public OcrService() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new IllegalStateException("Cannot create Robot for screen OCR", e);
        }
    }

    public String readText(AppSettings settings) {
        if (!isTrainedDataAvailable(settings)) {
            if (!missingTessdataWarned) {
                missingTessdataWarned = true;
                System.err.printf(
                        "Missing traineddata: %s/%s.traineddata%n",
                        settings.getTessDataPath(),
                        settings.getOcrLanguage()
                );
            }
            return "";
        }

        BufferedImage capture = robot.createScreenCapture(resolveCaptureArea(settings.getReadRegion()));

        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(settings.getTessDataPath());
        tesseract.setLanguage(settings.getOcrLanguage());

        try {
            return tesseract.doOCR(capture);
        } catch (TesseractException e) {
            return "";
        }
    }

    private boolean isTrainedDataAvailable(AppSettings settings) {
        Path trainedData = Path.of(settings.getTessDataPath(), settings.getOcrLanguage() + ".traineddata");
        return Files.exists(trainedData);
    }

    private Rectangle resolveCaptureArea(ReadRegion configuredRegion) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle fullScreen = new Rectangle(0, 0, screen.width, screen.height);
        if (configuredRegion == null) {
            return fullScreen;
        }
        if (configuredRegion.width() < 200 || configuredRegion.height() < 120) {
            return fullScreen;
        }
        return new Rectangle(
                Math.max(0, configuredRegion.x()),
                Math.max(0, configuredRegion.y()),
                Math.min(configuredRegion.width(), screen.width),
                Math.min(configuredRegion.height(), screen.height)
        );
    }
}
