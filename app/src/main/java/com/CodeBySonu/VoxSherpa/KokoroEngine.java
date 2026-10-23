package com.CodeBySonu.VoxSherpa;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;

public class KokoroEngine {

    static {
        try {
            System.loadLibrary("sherpa-onnx-jni");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static volatile KokoroEngine instance;
    private static final int KOKORO_SAMPLE_RATE = 24000;
    private OfflineTts tts;
    private String activeModelUri = "";
    private String activeTokensUri = "";
    private String activeVoicesBinUri = "";
    private Context activeContext = null;
    private String espeakDataPath = "";
    private String ttsFrontendDataPath = "";
    private int activeSpeakerId = 32;
    private volatile boolean cancelRequested = false;
    
    // Track engine active generation status to eliminate UI lock contention
    private volatile boolean isGenerating = false;
    
    // Lock object to prevent native memory release while generating (Use-After-Free fix)
    private final Object nativeLock = new Object();
    

    private KokoroEngine() {}

    // Singleton implementation with thread-safe double-checked locking
    public static KokoroEngine getInstance() {
        if (instance == null) {
            synchronized (KokoroEngine.class) {
                if (instance == null) {
                    instance = new KokoroEngine();
                }
            }
        }
        return instance;
    }

    public void cancel() {
        cancelRequested = true;
    }

    private int getOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores >= 8) return 4;
        if (cores >= 6) return 3;
        if (cores >= 4) return 2;
        return 1;
    }

    private java.util.List<KokoroVoiceHelper.VoiceItem> getVoiceList() {
        return KokoroVoiceHelper.getAllVoices();
    }

    private KokoroVoiceHelper.VoiceItem getVoiceById(int speakerId) {
        return KokoroVoiceHelper.getById(speakerId);
    }

    // espeak-ng-data extract
    private synchronized void extractEspeakData(Context context) {
        if (context == null) return;
        File destDir = new File(context.getFilesDir(), "espeak-ng-data");
        String[] existing = destDir.list();

        if (!destDir.exists() || existing == null || existing.length == 0) {
            destDir.mkdirs();
            try (InputStream is = context.getAssets().open("espeak-ng-data.zip");
                 ZipInputStream zis = new ZipInputStream(is)) {

                ZipEntry ze;
                byte[] buffer = new byte[32768];
                while ((ze = zis.getNextEntry()) != null) {
                    File newFile = new File(destDir, ze.getName());
                    if (ze.isDirectory()) {
                        newFile.mkdirs();
                    } else {
                        File parent = newFile.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                        }
                    }
                    zis.closeEntry();
                }
            } catch (Exception ignored) {}
        }

        File nestedDir = new File(destDir, "espeak-ng-data");
        espeakDataPath = new File(nestedDir, "phontab").exists()
                ? nestedDir.getAbsolutePath()
                : destDir.getAbsolutePath();
    }
    
    private synchronized void extractTtsFrontendData(Context context) throws IOException {
        if (context == null) return;
        File destDir = new File(context.getFilesDir(), "tts_frontend_data");
        File lexicon = new File(destDir, "lexicon-us-en.txt");

        if (!lexicon.isFile() || lexicon.length() == 0) {
            if (!destDir.exists() && !destDir.mkdirs()) {
                throw new IOException("Unable to create Kokoro frontend data directory.");
            }
            try (InputStream is = context.getAssets().open("tts_frontend.zip");
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                boolean found = false;
                byte[] buffer = new byte[32768];
                while ((entry = zis.getNextEntry()) != null) {
                    if ("lexicon-us-en.txt".equals(new File(entry.getName()).getName())) {
                        try (FileOutputStream fos = new FileOutputStream(lexicon)) {
                            int length;
                            while ((length = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, length);
                            }
                        }
                        found = true;
                        break;
                    }
                    zis.closeEntry();
                }
                if (!found || lexicon.length() == 0) {
                    throw new IOException("Kokoro English lexicon is missing or empty.");
                }
            }
        }
        ttsFrontendDataPath = destDir.getAbsolutePath();
    }

    // Provider fallback: XNNPACK to CPU
    private OfflineTts createTtsWithFallback(String onnxPath, String tokensPath, String voicesBinPath) {
        String[] providers = {"xnnpack", "cpu"};

        for (String provider : providers) {
            try {
                if (cancelRequested) return null;

                KokoroVoiceHelper.VoiceItem currentVoice = getVoiceById(activeSpeakerId);
                if (currentVoice == null) return null;

                OfflineTtsKokoroModelConfig kokoroConfig = new OfflineTtsKokoroModelConfig();
                kokoroConfig.setModel(onnxPath);
                kokoroConfig.setTokens(tokensPath);
                kokoroConfig.setVoices(voicesBinPath);
                kokoroConfig.setDataDir(espeakDataPath);
                kokoroConfig.setLang("en-us");

                OfflineTtsConfig config = new OfflineTtsConfig();

                String lexiconEn = new File(ttsFrontendDataPath, "lexicon-us-en.txt").getAbsolutePath();
                kokoroConfig.setLexicon(lexiconEn);

                OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
                modelConfig.setKokoro(kokoroConfig);
                modelConfig.setNumThreads(getOptimalThreadCount());
                modelConfig.setProvider(provider);
                modelConfig.setDebug(false);

                config.setModel(modelConfig);
                config.setMaxNumSentences(1);
                
                config.setSilenceScale(0.2f);

                OfflineTts candidate = new OfflineTts(null, config);

                GeneratedAudio test = candidate.generate("...", activeSpeakerId, 1.0f);
                if (test != null && test.getSamples() != null && test.getSamples().length > 0) {
                    return candidate;
                }

                try { candidate.release(); } catch (Throwable ignored) {}

            } catch (Throwable ignored) {}
        }

        return null;
    }
    
    
    // Load model
    public synchronized String loadModel(Context context, String onnxPath, String tokensPath, String voicesBinPath) {
        cancelRequested = false; 

        if (tts != null && activeModelUri.equals(onnxPath)) {
            return "Success";
        }

        if (onnxPath == null || onnxPath.isEmpty())           return "Error: ONNX path is empty.";
        if (tokensPath == null || tokensPath.isEmpty())       return "Error: Tokens path is empty.";
        if (voicesBinPath == null || voicesBinPath.isEmpty()) return "Error: voices.bin path is empty.";

        File fOnnx   = new File(onnxPath);
        File fTokens = new File(tokensPath);
        File fVoices = new File(voicesBinPath);

        if (!fOnnx.exists()   || fOnnx.length() == 0)   return "Error: ONNX file missing.";
        if (!fTokens.exists() || fTokens.length() == 0) return "Error: Tokens file missing.";
        if (!fVoices.exists() || fVoices.length() == 0) return "Error: voices.bin missing.";

        try {
            destroy();
            extractEspeakData(context);
            extractTtsFrontendData(context); 

            if (espeakDataPath == null || espeakDataPath.isEmpty())
                return "Error: espeak-ng-data extraction failed.";

            tts = createTtsWithFallback(onnxPath, tokensPath, voicesBinPath);

            if (tts == null) return "Error: Model load failed on all providers.";
            int sampleRate = tts.sampleRate();
            if (sampleRate != KOKORO_SAMPLE_RATE) {
                destroy();
                return "Error: Kokoro expected " + KOKORO_SAMPLE_RATE + " Hz output but reported " + sampleRate + " Hz.";
            }

            activeModelUri = onnxPath;
            activeTokensUri = tokensPath;
            activeVoicesBinUri = voicesBinPath;
            activeContext = context.getApplicationContext();
            return "Success";

        } catch (Throwable t) {
            activeModelUri = "";
            tts = null;
            return "Error: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }
    
    // Check if native engine is actively processing speech generation
    public boolean isBusy() {
        return isGenerating;
    }

    // Generate audio PCM
    public byte[] generateAudioPCM(String inputText, float speedValue, float pitchValue) {
        if (cancelRequested) {
            return null;
        }
        if (inputText == null || inputText.trim().isEmpty()) {
            return null;
        }

        String safeText = inputText.trim();
        float[] audioFloats = null;
        int sampleRate = 0;

        // Guard Clause & State Locking: Prevent concurrent execution from Java side
        synchronized (this) {
            if (tts == null || cancelRequested || isGenerating) {
                return null;
            }
            isGenerating = true;
        }

        // Main execution block is kept at the bottom to avoid deep nesting
        try {
            try {
                GeneratedAudio audio = null;
                synchronized (nativeLock) {
                    if (tts != null && !cancelRequested) {
                        audio = tts.generate(safeText, activeSpeakerId, speedValue);
                        if (audio != null) {
                            sampleRate = tts.sampleRate();
                        }
                    }
                }
                
                if (audio != null) {
                    audioFloats = audio.getSamples();
                }
            } catch (Throwable t) {
                return null;
            }

            if (cancelRequested || audioFloats == null || audioFloats.length == 0) {
                return null;
            }

            // Convert Float array to Short array safely with clipping
            short[] shortSamples = new short[audioFloats.length];
            for (int i = 0; i < audioFloats.length; i++) {
                float f = audioFloats[i];
                if (f > 1.0f) {
                    f = 1.0f;
                } else if (f < -1.0f) {
                    f = -1.0f;
                }
                shortSamples[i] = (short) (f * 32767.0f);
            }

            // Apply pitch modification via Sonic if requested
            if (pitchValue != 1.0f && sampleRate > 0) {
                if (cancelRequested) {
                    return null;
                }
                try {
                    com.CodeBySonu.VoxSherpa.Sonic sonic = new com.CodeBySonu.VoxSherpa.Sonic(sampleRate, 1);
                    sonic.setPitch(pitchValue);
                    sonic.writeShortToStream(shortSamples, shortSamples.length);
                    sonic.flushStream();
                    int available = sonic.samplesAvailable();
                    if (available > 0) {
                        short[] outSamples = new short[available];
                        sonic.readShortFromStream(outSamples, available);
                        shortSamples = outSamples;
                    }
                } catch (Throwable ignored) {}
            }

            if (cancelRequested) {
                return null;
            }

            // Convert Short array to Little-Endian Byte array
            byte[] pcmData = new byte[shortSamples.length * 2];
            for (int i = 0; i < shortSamples.length; i++) {
                pcmData[i * 2]     = (byte) (shortSamples[i] & 0xff);
                pcmData[i * 2 + 1] = (byte) ((shortSamples[i] >> 8) & 0xff);
            }

            return pcmData;

        } catch (Throwable t) {
            return null;
        } finally {
            // isGenerating is volatile, direct assignment is thread-safe
            isGenerating = false;
        }
    }
    
    // Sample rate
    public synchronized int getSampleRate() {
        if (tts == null) return 0;
        try {
            return tts.sampleRate();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // Speaker / Voice control
    public void setActiveSpeakerId(int speakerId) {
        this.activeSpeakerId = speakerId;
    }

    public int getActiveSpeakerId() {
        return activeSpeakerId;
    }

    public String getActiveVoiceName() {
        try {
            KokoroVoiceHelper.VoiceItem voice = getVoiceById(activeSpeakerId);
            return voice != null ? voice.getFullLabel() : "Unknown Voice";
        } catch (Throwable ignored) {
            return "Unknown Voice";
        }
    }

    public int getNumSpeakers() {
        try {
            return getVoiceList().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // State
    public synchronized boolean isReady() {
        return tts != null;
    }

    public void destroy() {
        cancelRequested = false;
        final OfflineTts oldTts = tts;
        tts = null;
        activeModelUri = "";
        activeTokensUri = "";
        activeVoicesBinUri = "";
        activeContext = null;
        isGenerating = false;
        if (oldTts != null) {
            new Thread(() -> {
                synchronized (nativeLock) {
                    try { 
                        oldTts.release(); 
                    } catch (Throwable ignored) {}
                }
            }).start();
        }
    }
}