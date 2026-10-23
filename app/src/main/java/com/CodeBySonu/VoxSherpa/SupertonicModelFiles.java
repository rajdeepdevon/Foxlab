package com.CodeBySonu.VoxSherpa;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public final class SupertonicModelFiles {
    public static final String MODEL_TYPE = "supertonic";
    public static final String DISPLAY_SIZE = "400 MB";
    public static final String BASE_URL =
            "https://huggingface.co/seepa22/Supertonic-voxsherpa/resolve/main/supertonic-v3/";
    public static final List<String> MODEL_FILES = Collections.unmodifiableList(Arrays.asList(
            "vector_estimator.onnx",
            "vocoder.onnx",
            "text_encoder.onnx",
            "duration_predictor.onnx",
            "tts.json",
            "unicode_indexer.bin",
            "voice.bin"
    ));

    private SupertonicModelFiles() {}

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public static final class DownloadTask {
        private volatile boolean cancelled;
        private volatile HttpURLConnection activeConnection;

        private DownloadTask() {}

        public void cancel() {
            cancelled = true;
            HttpURLConnection connection = activeConnection;
            if (connection != null) connection.disconnect();
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public File download(Context context, ProgressListener listener) throws IOException {
            if (context == null) throw new IOException("Context is unavailable.");

            File modelDir = getModelDirectory(context);
            if (!modelDir.exists() && !modelDir.mkdirs() && !modelDir.isDirectory()) {
                throw new IOException("Unable to create the Supertonic model directory.");
            }
            if (isInstalled(modelDir)) {
                if (listener != null) listener.onProgress(100);
                return modelDir;
            }

            for (int index = 0; index < MODEL_FILES.size(); index++) {
                ensureNotCancelled();
                String fileName = MODEL_FILES.get(index);
                downloadFile(fileName, new File(modelDir, fileName), index, listener);
            }

            List<String> invalidFiles = getInvalidFiles(modelDir);
            if (!invalidFiles.isEmpty()) {
                throw new IOException("The Supertonic download is incomplete. Missing or empty files: "
                        + android.text.TextUtils.join(", ", invalidFiles));
            }
            ensureNotCancelled();
            if (listener != null) listener.onProgress(100);
            return modelDir;
        }

        private void downloadFile(
                String fileName, File target, int fileIndex, ProgressListener listener) throws IOException {
            File partial = new File(target.getParentFile(), fileName + ".part");
            byte[] buffer = new byte[32768];
            HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + fileName).openConnection();
            activeConnection = connection;
            try {
                ensureNotCancelled();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "VoxSherpa");
                int responseCode = connection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK
                        || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    throw new IOException("Unable to download Supertonic file " + fileName
                            + " (HTTP " + responseCode + ").");
                }

                long contentLength = connection.getContentLengthLong();
                long bytesRead = 0;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        ensureNotCancelled();
                        output.write(buffer, 0, count);
                        bytesRead += count;
                        if (listener != null) {
                            int fileProgress = contentLength > 0
                                    ? (int) Math.min(99, bytesRead * 100 / contentLength)
                                    : 0;
                            int progress = Math.min(99,
                                    (fileIndex * 100 + fileProgress) / MODEL_FILES.size());
                            listener.onProgress(progress);
                        }
                    }
                }
                if (bytesRead == 0 || (contentLength >= 0 && bytesRead != contentLength)) {
                    throw new IOException("Supertonic file " + fileName + " is empty or incomplete.");
                }
                ensureNotCancelled();
                if (target.exists() && !target.delete()) {
                    throw new IOException("Unable to replace Supertonic file " + fileName + ".");
                }
                if (!partial.renameTo(target)) {
                    throw new IOException("Unable to save Supertonic file " + fileName + ".");
                }
            } finally {
                activeConnection = null;
                connection.disconnect();
                if (partial.exists()) partial.delete();
            }
        }

        private void ensureNotCancelled() throws IOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new IOException("Supertonic download was cancelled.");
            }
        }
    }

    public static DownloadTask newDownloadTask() {
        return new DownloadTask();
    }

    public static boolean isSupertonicModel(HashMap<String, Object> model) {
        if (model == null) return false;
        return isSupertonicIdentifier(model.get("type"))
                || isSupertonicIdentifier(model.get("model_type"))
                || isSupertonicIdentifier(model.get("modelType"))
                || containsSupertonic(model.get("name"))
                || containsSupertonic(model.get("model_id"))
                || containsSupertonic(model.get("modelId"))
                || containsSupertonic(model.get("id"));
    }

    private static boolean isSupertonicIdentifier(Object value) {
        if (value == null) return false;
        String identifier = value.toString().trim();
        return MODEL_TYPE.equalsIgnoreCase(identifier) || containsSupertonic(identifier);
    }

    private static boolean containsSupertonic(Object value) {
        return value != null && value.toString().toLowerCase(java.util.Locale.ROOT).contains(MODEL_TYPE);
    }

    public static HashMap<String, Object> createCatalogEntry() {
        HashMap<String, Object> model = new HashMap<>();
        model.put("name", "Supertonic V3");
        model.put("type", MODEL_TYPE);
        model.put("language", "Multilingual");
        model.put("gender", "10 speakers");
        model.put("size", DISPLAY_SIZE);
        model.put("quality", "High quality");
        model.put("model_url", BASE_URL);
        model.put("model_files", new ArrayList<>(MODEL_FILES));
        model.put("is_downloading", "false");
        model.put("download_progress", "0");
        return model;
    }

    public static File getModelDirectory(Context context) {
        return new File(new File(context.getFilesDir(), "models"), "supertonic");
    }

    public static boolean isInstalled(File modelDir) {
        return getInvalidFiles(modelDir).isEmpty();
    }

    public static List<String> getInvalidFiles(File modelDir) {
        List<String> invalidFiles = new ArrayList<>();
        for (String fileName : MODEL_FILES) {
            File file = modelDir == null ? null : new File(modelDir, fileName);
            if (file == null || !file.isFile() || file.length() <= 0) {
                invalidFiles.add(fileName);
            }
        }
        return invalidFiles;
    }

    public static void populateInstalledPaths(HashMap<String, Object> model, File modelDir) {
        model.put("model_dir", modelDir.getAbsolutePath());
        model.put("onnx_path", new File(modelDir, "duration_predictor.onnx").getAbsolutePath());
        model.put("text_encoder_path", new File(modelDir, "text_encoder.onnx").getAbsolutePath());
        model.put("vector_estimator_path", new File(modelDir, "vector_estimator.onnx").getAbsolutePath());
        model.put("vocoder_path", new File(modelDir, "vocoder.onnx").getAbsolutePath());
        model.put("tokens_path", new File(modelDir, "tts.json").getAbsolutePath());
        model.put("unicode_indexer_path", new File(modelDir, "unicode_indexer.bin").getAbsolutePath());
        model.put("voices_bin_path", new File(modelDir, "voice.bin").getAbsolutePath());
    }

    public static void deleteInstalledModel(HashMap<String, Object> model) {
        Object path = model.get("model_dir");
        if (path != null) deleteTree(new File(path.toString()));
        String[] installedKeys = {
                "model_dir", "onnx_path", "text_encoder_path", "vector_estimator_path",
                "vocoder_path", "tokens_path", "unicode_indexer_path", "voices_bin_path"
        };
        for (String key : installedKeys) model.remove(key);
    }

    public static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
