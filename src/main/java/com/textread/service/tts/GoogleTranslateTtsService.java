package com.textread.service.tts;

import com.textread.model.AppSettings;
import com.textread.service.DebugLogger;
import com.textread.service.TtsService;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class GoogleTranslateTtsService implements TtsService {
    private final BlockingQueue<SpeechTask> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile boolean running = true;
    private volatile boolean cancelRequested = false;
    private volatile SourceDataLine currentLine;
    private final AtomicBoolean speaking = new AtomicBoolean(false);

    public GoogleTranslateTtsService() {
        worker = new Thread(this::consume, "google-tts-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void speakAsync(String text, AppSettings settings) {
        if (text == null || text.isBlank() || settings.isMuted()) {
            return;
        }
        String language = normalizeLanguage(settings.getVoiceName());
        float playbackRate = normalizePlaybackRate(settings.getSpeechRate());
        for (SpeechChunk chunk : splitIntoChunksWithPause(text, 170)) {
            queue.offer(new SpeechTask(chunk.text(), playbackRate, settings.getVolume(), language, chunk.pauseMs()));
        }
    }

    @Override
    public void cancel() {
        cancelRequested = true;
        queue.clear();
        SourceDataLine line = currentLine;
        if (line != null && line.isOpen()) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    @Override
    public void shutdown() {
        running = false;
        cancel();
        worker.interrupt();
    }

    @Override
    public boolean isIdle() {
        return queue.isEmpty() && !speaking.get();
    }

    private void consume() {
        while (running) {
            try {
                SpeechTask task = queue.take();
                cancelRequested = false;
                byte[] mp3 = requestSpeechMp3(task);
                if (mp3.length == 0 || cancelRequested) {
                    DebugLogger.log("TTS skip: empty audio response");
                    continue;
                }
                speaking.set(true);
                DebugLogger.log("TTS play: " + preview(task.text()));
                playMp3(mp3, task.volume(), task.playbackRate());
                if (!cancelRequested && task.pauseMs() > 0) {
                    Thread.sleep(task.pauseMs());
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                DebugLogger.log("TTS error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            } finally {
                speaking.set(false);
            }
        }
    }

    private byte[] requestSpeechMp3(SpeechTask task) throws IOException, InterruptedException {
        String speakable = sanitizeForSpeak(task.text());
        if (speakable.isBlank()) {
            return new byte[0];
        }
        String encodedText = URLEncoder.encode(speakable, StandardCharsets.UTF_8);
        String url = "https://translate.googleapis.com/translate_tts?ie=UTF-8&client=tw-ob&tl="
                + task.language() + "&q=" + encodedText;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new byte[0];
        }
        return response.body();
    }

    private void playMp3(byte[] mp3Data, int volume, float playbackRate) throws JavaLayerException {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3Data));
        Decoder decoder = new Decoder();
        SourceDataLine line = null;
        try {
            while (!cancelRequested) {
                Header frameHeader = bitstream.readFrame();
                if (frameHeader == null) {
                    break;
                }
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
                if (line == null) {
                    float sourceRate = output.getSampleFrequency();
                    float adjustedRate = Math.max(8000f, sourceRate * playbackRate);
                    AudioFormat format = new AudioFormat(
                            adjustedRate,
                            16,
                            output.getChannelCount(),
                            true,
                            false
                    );
                    line = AudioSystem.getSourceDataLine(format);
                    line.open(format);
                    line.start();
                    currentLine = line;
                }
                byte[] pcm = shortsToBytes(output.getBuffer(), output.getBufferLength(), volume);
                line.write(pcm, 0, pcm.length);
                bitstream.closeFrame();
            }
        } catch (Exception ignored) {
        } finally {
            if (line != null) {
                line.drain();
                line.stop();
                line.close();
            }
            currentLine = null;
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
            }
        }
    }

    private byte[] shortsToBytes(short[] input, int length, int volume) {
        double gain = Math.max(0, Math.min(100, volume)) / 100.0;
        byte[] out = new byte[length * 2];
        int idx = 0;
        for (int i = 0; i < length; i++) {
            int sample = (int) Math.round(input[i] * gain);
            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            }
            if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }
            out[idx++] = (byte) (sample & 0xFF);
            out[idx++] = (byte) ((sample >> 8) & 0xFF);
        }
        return out;
    }

    private String normalizeLanguage(String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return "vi";
        }
        String trimmed = voiceName.trim().toLowerCase();
        return switch (trimmed) {
            case "en", "en-us" -> "en";
            default -> "vi";
        };
    }

    private float normalizePlaybackRate(int speechRate) {
        float rate = speechRate / 150.0f;
        if (rate < 0.65f) {
            return 0.65f;
        }
        if (rate > 1.7f) {
            return 1.7f;
        }
        return rate;
    }

    private List<SpeechChunk> splitIntoChunksWithPause(String text, int maxLen) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<SpeechChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            current.append(c);
            if (current.length() >= maxLen) {
                addChunk(chunks, current.toString(), 0);
                current.setLength(0);
            }
            if (c == ',') {
                addChunk(chunks, current.toString(), 180);
                current.setLength(0);
            } else if (c == '.' || c == '!' || c == '?' || c == ';' || c == ':') {
                addChunk(chunks, current.toString(), 340);
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            addChunk(chunks, current.toString(), 0);
        }
        return chunks;
    }

    private void addChunk(List<SpeechChunk> chunks, String text, int pauseMs) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (!cleaned.isBlank() && cleaned.matches(".*\\p{L}.*")) {
            chunks.add(new SpeechChunk(cleaned, pauseMs));
        }
    }

    private String sanitizeForSpeak(String text) {
        return text.replaceAll("[,\\.\\!\\?;:]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record SpeechChunk(String text, int pauseMs) {
    }

    private record SpeechTask(String text, float playbackRate, int volume, String language, int pauseMs) {
    }

    private String preview(String value) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 140) {
            return compact;
        }
        return compact.substring(0, 140) + "...";
    }
}
