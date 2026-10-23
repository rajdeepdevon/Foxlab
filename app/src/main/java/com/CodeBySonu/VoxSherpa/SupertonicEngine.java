package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import org.json.JSONTokener;

public final class SupertonicEngine {
    public static final int MIN_GENERATION_STEPS = 1;
    public static final int MAX_GENERATION_STEPS = 16;
    public static final int DEFAULT_GENERATION_STEPS = 16;

    private static final String[] SUPPORTED_LANGUAGES = {
            "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et",
            "fi", "fr", "hi", "hr", "hu", "id", "it", "lt", "lv", "nl", "pl",
            "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi"
    };

    static {
        try {
            System.loadLibrary("sherpa-onnx-jni");
        } catch (UnsatisfiedLinkError error) {
            Log.e("SupertonicEngine", "Unable to load sherpa-onnx-jni.", error);
        }
    }

    private static volatile SupertonicEngine instance;
    private final Object nativeLock = new Object();
    private volatile OfflineTts tts;
    private volatile boolean cancelRequested;
    private volatile boolean isGenerating;
    private volatile int activeSpeakerId;
    private volatile String activeLanguage = "en";
    private String activeModelDir = "";

    private SupertonicEngine() {}

    public static SupertonicEngine getInstance() {
        if (instance == null) {
            synchronized (SupertonicEngine.class) {
                if (instance == null) instance = new SupertonicEngine();
            }
        }
        return instance;
    }

    public static String[] getSupportedLanguages() {
        return SUPPORTED_LANGUAGES.clone();
    }

    public static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return "en";
        String normalized = language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int separator = normalized.indexOf('-');
        if (separator >= 0) normalized = normalized.substring(0, separator);
        for (String supported : SUPPORTED_LANGUAGES) {
            if (supported.equals(normalized)) return supported;
            try {
                if (Locale.forLanguageTag(supported).getISO3Language().equalsIgnoreCase(normalized)) return supported;
            } catch (Exception ignored) {}
        }
        return "en";
    }

    public void cancel() {
        cancelRequested = true;
    }

    public boolean isBusy() {
        return isGenerating;
    }

    public synchronized String loadModel(Context context, String modelDir, String language) {
        try {
            return initializeModel(context, modelDir, language);
        } catch (Throwable error) {
            Log.e("SupertonicEngine", "Unexpected error while initializing Supertonic.", error);
            return "Error: " + (error.getMessage() == null
                    ? "Unable to initialize the Supertonic model." : error.getMessage());
        }
    }

    private String initializeModel(Context context, String modelDir, String language) {
        if (context == null) {
            Log.e("SupertonicEngine", "Cannot initialize Supertonic: context is unavailable.");
            return "Error: Context is unavailable.";
        }

        File directory = SupertonicModelFiles.getModelDirectory(context);
        if (modelDir != null && !modelDir.trim().isEmpty()
                && !new File(modelDir).getAbsoluteFile().equals(directory.getAbsoluteFile())) {
            Log.w("SupertonicEngine", "Ignoring stale model directory " + modelDir
                    + "; extracted model files are stored in " + directory.getAbsolutePath());
        }
        java.util.List<String> invalidFiles = SupertonicModelFiles.getInvalidFiles(directory);
        if (!invalidFiles.isEmpty()) {
            String message = "Missing or empty Supertonic model files in " + directory.getAbsolutePath()
                    + ": " + android.text.TextUtils.join(", ", invalidFiles);
            Log.e("SupertonicEngine", message);
            return "Error: " + message;
        }
        for (String jsonFile : new String[]{"tts.json"}) {
            File file = new File(directory, jsonFile);
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8)) {
                StringBuilder contents = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    contents.append(buffer, 0, count);
                }
                new JSONTokener(contents.toString()).nextValue();
            } catch (Throwable error) {
                Log.e("SupertonicEngine", "Invalid or unreadable Supertonic JSON file: "
                        + file.getAbsolutePath(), error);
                return "Error: Supertonic model file is invalid or unreadable: " + jsonFile;
            }
        }

        String normalizedLanguage = normalizeLanguage(language);
        if (tts != null && activeModelDir.equals(directory.getAbsolutePath())
                && activeLanguage.equals(normalizedLanguage)) {
            cancelRequested = false;
            return "Success";
        }

        cancelRequested = false;
        destroy();
        String modelDirectory = directory.getAbsolutePath();
        String[] providers = {"xnnpack", "cpu"};
        String lastError = "Unable to initialize the Supertonic model.";
        for (String provider : providers) {
            OfflineTts candidate = null;
            try {
                OfflineTtsSupertonicModelConfig supertonic = new OfflineTtsSupertonicModelConfig();
                supertonic.setDurationPredictor(new File(directory, "duration_predictor.onnx").getAbsolutePath());
                supertonic.setTextEncoder(new File(directory, "text_encoder.onnx").getAbsolutePath());
                supertonic.setVectorEstimator(new File(directory, "vector_estimator.onnx").getAbsolutePath());
                supertonic.setVocoder(new File(directory, "vocoder.onnx").getAbsolutePath());
                supertonic.setTtsJson(new File(directory, "tts.json").getAbsolutePath());
                supertonic.setUnicodeIndexer(new File(directory, "unicode_indexer.bin").getAbsolutePath());
                supertonic.setVoiceStyle(new File(directory, "voice.bin").getAbsolutePath());

                OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
                modelConfig.setSupertonic(supertonic);
                modelConfig.setNumThreads(getOptimalThreadCount());
                modelConfig.setProvider(provider);
                modelConfig.setDebug(false);

                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(modelConfig);
                candidate = new OfflineTts(null, config);

                GeneratedAudio test = candidate.generateWithConfig(
                        "Hello.", createGenerationConfig(1, 1.0f, normalizedLanguage, DEFAULT_GENERATION_STEPS));
                if (test != null && test.getSamples() != null && test.getSamples().length > 0) {
                    tts = candidate;
                    activeModelDir = modelDirectory;
                    activeLanguage = normalizedLanguage;
                    return "Success";
                }
                lastError = "Supertonic returned no audio during initialization.";
            } catch (Throwable error) {
                Log.e("SupertonicEngine", "Supertonic initialization failed with provider "
                        + provider + " using model directory " + modelDirectory, error);
                if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                    lastError = error.getMessage();
                }
            }
            if (candidate != null) {
                try {
                    candidate.release();
                } catch (Throwable error) {
                    Log.e("SupertonicEngine", "Could not release failed Supertonic initialization.", error);
                }
            }
        }
        Log.e("SupertonicEngine", "All Supertonic providers failed: " + lastError);
        return "Error: " + lastError;
    }

    public byte[] generateAudioPCM(String text, float speed, float pitch) {
        return generateAudioPCM(text, speed, pitch, activeLanguage, DEFAULT_GENERATION_STEPS);
    }

    public byte[] generateAudioPCM(String text, float speed, float pitch, String language) {
        return generateAudioPCM(text, speed, pitch, language, DEFAULT_GENERATION_STEPS);
    }

    public byte[] generateAudioPCM(String text, float speed, float pitch, String language, int numSteps) {
        if (cancelRequested || text == null || text.trim().isEmpty()) return null;
        String generationLanguage = normalizeLanguage(language);
        int generationSteps = Math.max(MIN_GENERATION_STEPS, Math.min(MAX_GENERATION_STEPS, numSteps));
        synchronized (this) {
            if (tts == null || cancelRequested || isGenerating) return null;
            isGenerating = true;
        }
        try {
            GeneratedAudio audio;
            synchronized (nativeLock) {
                OfflineTts current = tts;
                if (current == null || cancelRequested) return null;
                audio = current.generateWithConfig(
                        text.trim(),
                        createGenerationConfig(activeSpeakerId, speed, generationLanguage, generationSteps));
            }
            if (cancelRequested || audio == null || audio.getSamples() == null
                    || audio.getSamples().length == 0) return null;

            float[] samples = audio.getSamples();
            short[] pcm = new short[samples.length];
            for (int i = 0; i < samples.length; i++) {
                float sample = Math.max(-1.0f, Math.min(1.0f, samples[i]));
                pcm[i] = (short) (sample * 32767.0f);
            }

            int sampleRate = getSampleRate();
            if (pitch != 1.0f && sampleRate > 0 && !cancelRequested) {
                Sonic sonic = new Sonic(sampleRate, 1);
                sonic.setPitch(pitch);
                sonic.writeShortToStream(pcm, pcm.length);
                sonic.flushStream();
                int available = sonic.samplesAvailable();
                if (available > 0) {
                    short[] pitched = new short[available];
                    sonic.readShortFromStream(pitched, available);
                    pcm = pitched;
                }
            }
            if (cancelRequested) return null;

            byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                bytes[i * 2] = (byte) (pcm[i] & 0xff);
                bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
            }
            return bytes;
        } catch (Throwable error) {
            android.util.Log.e("SupertonicEngine", "Audio generation failed.", error);
            return null;
        } finally {
            isGenerating = false;
        }
    }

    public synchronized int getSampleRate() {
        OfflineTts current = tts;
        if (current == null) return 0;
        try {
            return current.sampleRate();
        } catch (Throwable error) {
            android.util.Log.e("SupertonicEngine", "Could not read model sample rate.", error);
            return 0;
        }
    }

    public int getNumSpeakers() {
        OfflineTts current = tts;
        if (current == null) return 10;
        try {
            return current.numSpeakers();
        } catch (Throwable error) {
            android.util.Log.e("SupertonicEngine", "Could not read model speaker count.", error);
            return 10;
        }
    }

    public void setActiveSpeakerId(int speakerId) {
        if (speakerId >= 0 && speakerId < getNumSpeakers()) activeSpeakerId = speakerId;
    }

    public int getActiveSpeakerId() {
        return activeSpeakerId;
    }

    public synchronized void setActiveLanguage(String language) {
        activeLanguage = normalizeLanguage(language);
    }

    public synchronized boolean isReady() {
        return tts != null;
    }

    public void destroy() {
        cancelRequested = false;
        OfflineTts oldTts;
        synchronized (nativeLock) {
            oldTts = tts;
            tts = null;
            activeModelDir = "";
        }
        isGenerating = false;
        if (oldTts != null) {
            new Thread(() -> {
                synchronized (nativeLock) {
                    try {
                        oldTts.release();
                    } catch (Throwable error) {
                        android.util.Log.e("SupertonicEngine", "Could not release model.", error);
                    }
                }
            }).start();
        }
    }

    private static GenerationConfig createGenerationConfig(int speakerId, float speed, String language, int numSteps) {
        GenerationConfig config = new GenerationConfig();
        config.setSid(speakerId);
        config.setSpeed(speed > 0 ? speed : 1.0f);
        config.setNumSteps(numSteps);
        HashMap<String, String> extra = new HashMap<>();
        extra.put("lang", normalizeLanguage(language));
        config.setExtra(Collections.unmodifiableMap(extra));
        return config;
    }

    private static int getOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 8) return 4;
        if (cores >= 6) return 3;
        if (cores >= 4) return 2;
        return 1;
    }
}
