package com.textread.service.tts;

import com.textread.model.AppSettings;
import com.textread.service.TtsService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WindowsPowerShellTtsService implements TtsService {
    private final BlockingQueue<SpeechTask> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;
    private volatile Process currentProcess;
    private volatile boolean warnedNoVietnameseVoice = false;

    public WindowsPowerShellTtsService() {
        worker = new Thread(this::consume, "windows-tts-worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void speakAsync(String text, AppSettings settings) {
        if (text == null || text.isBlank() || settings.isMuted()) {
            return;
        }
        for (String chunk : splitIntoChunks(text, 180)) {
            queue.offer(new SpeechTask(chunk, settings.getSpeechRate(), settings.getVolume()));
        }
    }

    @Override
    public void cancel() {
        queue.clear();
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
    }

    @Override
    public void shutdown() {
        running = false;
        worker.interrupt();
        cancel();
    }

    private void consume() {
        while (running) {
            try {
                SpeechTask task = queue.take();
                speak(task);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void speak(SpeechTask task) {
        String text = normalizeSpeechText(task.text());
        if (text.isBlank()) {
            return;
        }
        int rate = normalizeRate(task.speechRate());
        int volume = normalizeVolume(task.volume());
        if (speakWithPowerShellCom(text, rate, volume)) {
            return;
        }
        speakWithMshta(text, rate, volume);
    }

    private boolean speakWithPowerShellCom(String text, int rate, int volume) {
        String escaped = escapeForPowerShellSingleQuotedString(text);
        String script = """
                $ErrorActionPreference = 'Stop'
                $voice = New-Object -ComObject SAPI.SpVoice
                $voice.Rate = %d
                $voice.Volume = %d
                $tokens = $voice.GetVoices()
                $selectedVi = $false
                for ($i = 0; $i -lt $tokens.Count; $i++) {
                    $d = $tokens.Item($i).GetDescription()
                    if ($d -match 'Vietnam|Vietnamese|Viet|Việt') {
                        $voice.Voice = $tokens.Item($i)
                        $selectedVi = $true
                        break
                    }
                }
                if (-not $selectedVi) { Write-Output '__NO_VI_VOICE__' }
                [void]$voice.Speak('%s')
                """.formatted(rate, volume, escaped);
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
        try {
            pb.redirectErrorStream(true);
            currentProcess = pb.start();
            String output = new String(currentProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = currentProcess.waitFor();
            if (!warnedNoVietnameseVoice && output.contains("__NO_VI_VOICE__")) {
                warnedNoVietnameseVoice = true;
                System.err.println("Vietnamese voice not installed on Windows. Current voice will be non-Vietnamese.");
            }
            return exitCode == 0;
        } catch (Exception ex) {
            System.err.println("TTS powershell COM error: " + ex.getClass().getSimpleName());
            return false;
        } finally {
            currentProcess = null;
        }
    }

    private boolean speakWithMshta(String text, int rate, int volume) {
        String escaped = text.replace("\"", "\"\"");
        String vbScript = "vbscript:Execute(\"Set v=CreateObject(\"\"SAPI.SpVoice\"\"):v.Rate=" + rate
                + ":v.Volume=" + volume
                + ":v.Speak \"" + escaped + "\":close\")";
        ProcessBuilder pb = new ProcessBuilder("mshta", vbScript);
        try {
            currentProcess = pb.start();
            int exitCode = currentProcess.waitFor();
            return exitCode == 0;
        } catch (Exception ex) {
            System.err.println("TTS mshta error: " + ex.getClass().getSimpleName());
            return false;
        } finally {
            currentProcess = null;
        }
    }

    private int normalizeRate(int appRate) {
        int mapped = Math.round((appRate - 150) / 15.0f);
        if (mapped > 10) {
            return 10;
        }
        if (mapped < -10) {
            return -10;
        }
        return mapped;
    }

    private int normalizeVolume(int appVolume) {
        if (appVolume > 100) {
            return 100;
        }
        if (appVolume < 0) {
            return 0;
        }
        return appVolume;
    }

    private String escapeForPowerShellSingleQuotedString(String input) {
        return input.replace("'", "''");
    }

    private String normalizeSpeechText(String input) {
        String normalized = input.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 380) {
            return normalized.substring(0, 380);
        }
        return normalized;
    }

    private java.util.List<String> splitIntoChunks(String text, int maxLen) {
        String normalized = normalizeSpeechText(text);
        if (normalized.isBlank()) {
            return java.util.List.of();
        }
        String[] sentences = normalized.split("(?<=[.!?;:])\\s+");
        java.util.List<String> chunks = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.isBlank()) {
                continue;
            }
            if (current.length() + sentence.length() + 1 > maxLen && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(sentence).append(' ');
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        if (chunks.isEmpty()) {
            chunks.add(normalized);
        }
        return chunks;
    }

    private record SpeechTask(String text, int speechRate, int volume) {
    }
}
