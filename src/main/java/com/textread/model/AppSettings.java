package com.textread.model;

import com.textread.service.detection.AdFilterMethod;

import java.util.EnumSet;
import java.util.Set;

public class AppSettings {
    private ReadRegion readRegion = new ReadRegion(0, 0, 1280, 720);
    private String tessDataPath = "./tessdata";
    private String ocrLanguage = "vie";
    private String voiceName = "vi-VN";
    private int speechRate = 150;
    private int volume = 100;
    private int scrollSpeed = 5;
    private int pitch = 100;
    private boolean muted = false;
    private final Set<AdFilterMethod> adFilters = EnumSet.noneOf(AdFilterMethod.class);

    public ReadRegion getReadRegion() {
        return readRegion;
    }

    public void setReadRegion(ReadRegion readRegion) {
        this.readRegion = readRegion;
    }

    public String getTessDataPath() {
        return tessDataPath;
    }

    public void setTessDataPath(String tessDataPath) {
        this.tessDataPath = tessDataPath;
    }

    public String getOcrLanguage() {
        return ocrLanguage;
    }

    public void setOcrLanguage(String ocrLanguage) {
        this.ocrLanguage = ocrLanguage;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public int getSpeechRate() {
        return speechRate;
    }

    public void setSpeechRate(int speechRate) {
        this.speechRate = speechRate;
    }

    public int getScrollSpeed() {
        return scrollSpeed;
    }

    public void setScrollSpeed(int scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public int getPitch() {
        return pitch;
    }

    public void setPitch(int pitch) {
        this.pitch = pitch;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public Set<AdFilterMethod> getAdFilters() {
        return adFilters;
    }
}
