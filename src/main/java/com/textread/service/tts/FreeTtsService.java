package com.textread.service.tts;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.textread.model.AppSettings;
import com.textread.service.TtsService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class FreeTtsService implements TtsService {
    private final BlockingQueue<SpeechTask> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public FreeTtsService() {
        worker = new Thread(this::consume, "tts-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void speakAsync(String text, AppSettings settings) {
        if (text == null || text.isBlank() || settings.isMuted()) {
            return;
        }
        queue.offer(new SpeechTask(text, settings.getVoiceName(), settings.getSpeechRate(), settings.getPitch()));
    }

    @Override
    public void cancel() {
        queue.clear();
    }

    @Override
    public void shutdown() {
        running = false;
        worker.interrupt();
        queue.clear();
    }

    @Override
    public boolean isIdle() {
        return queue.isEmpty() && !busy.get();
    }

    private void consume() {
        while (running) {
            try {
                SpeechTask task = queue.take();
                busy.set(true);
                speak(task);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                busy.set(false);
            }
        }
    }

    private void speak(SpeechTask task) {
        Voice voice = VoiceManager.getInstance().getVoice(task.voiceName());
        if (voice == null) {
            voice = VoiceManager.getInstance().getVoice("kevin16");
            if (voice == null) {
                return;
            }
        }
        try {
            voice.allocate();
            voice.setRate(task.rate());
            voice.setPitch(task.pitch());
            voice.speak(task.text());
        } finally {
            voice.deallocate();
        }
    }

    private record SpeechTask(String text, String voiceName, int rate, int pitch) {
    }
}
