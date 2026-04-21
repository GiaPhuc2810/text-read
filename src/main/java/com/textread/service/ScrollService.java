package com.textread.service;

import com.textread.model.ReadRegion;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

public class ScrollService {
    private final Robot robot;

    public ScrollService() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new IllegalStateException("Cannot create Robot for scrolling", e);
        }
    }

    public void scrollByRegion(ReadRegion region) {
        int height = region == null ? 700 : Math.max(200, region.height());
        int wheelSteps = Math.max(1, Math.round(height / 45f));
        Point oldMouse = MouseInfo.getPointerInfo() != null ? MouseInfo.getPointerInfo().getLocation() : null;
        if (region != null) {
            int targetX = region.x() + Math.max(20, region.width() / 2);
            int targetY = region.y() + Math.max(20, region.height() / 2);
            robot.mouseMove(targetX, targetY);
        }
        // Ensure the page under scan region receives wheel input.
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(80);
        robot.mouseWheel(wheelSteps);
        if (oldMouse != null) {
            robot.mouseMove(oldMouse.x, oldMouse.y);
        }
    }
}
