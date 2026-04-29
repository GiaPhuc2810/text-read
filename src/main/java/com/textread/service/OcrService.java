package com.textread.service;

import com.textread.model.AppSettings;
import com.textread.model.ReadRegion;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Color;
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

        Rectangle captureArea = resolveCaptureArea(settings.getReadRegion());
        BufferedImage capture = robot.createScreenCapture(captureArea);
        maskAppWindow(capture, captureArea, settings.getAppWindowRegion());

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
        if (configuredRegion.width() < 20 || configuredRegion.height() < 20) {
            return fullScreen;
        }
        int x = Math.max(0, configuredRegion.x());
        int y = Math.max(0, configuredRegion.y());
        int width = Math.min(configuredRegion.width(), Math.max(1, screen.width - x));
        int height = Math.min(configuredRegion.height(), Math.max(1, screen.height - y));
        return new Rectangle(
                x,
                y,
                width,
                height
        );
    }

    private void maskAppWindow(BufferedImage capture, Rectangle captureArea, ReadRegion appWindowRegion) {
        if (capture == null || captureArea == null || appWindowRegion == null) {
            return;
        }
        if (appWindowRegion.width() <= 1 || appWindowRegion.height() <= 1) {
            return;
        }
        Rectangle appRect = new Rectangle(
                appWindowRegion.x(),
                appWindowRegion.y(),
                appWindowRegion.width(),
                appWindowRegion.height()
        );
        Rectangle overlap = captureArea.intersection(appRect);
        if (overlap.isEmpty()) {
            return;
        }

        int localX = overlap.x - captureArea.x;
        int localY = overlap.y - captureArea.y;
        Graphics2D g2 = capture.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(localX, localY, overlap.width, overlap.height);
        } finally {
            g2.dispose();
        }
    }
}
