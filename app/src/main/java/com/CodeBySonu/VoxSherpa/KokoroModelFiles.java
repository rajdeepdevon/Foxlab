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

public final class KokoroModelFiles {
    public static final String MODEL_TYPE = "kokoro";
    public static final String MODEL_DIRECTORY_NAME = "kokoro-multi-lang-v1_0";
    public static final String MODEL_FILE_NAME = "model.onnx";
    public static final String DISPLAY_NAME = "Kokoro";
    public static final String DISPLAY_SIZE = "350 MB";
    public static final String BASE_URL =
            "https://huggingface.co/seepa22/Kokoro-82M/resolve/main/kokoro-multi-lang/";
    public static final List<String> MODEL_FILES = Collections.unmodifiableList(Arrays.asList(
            MODEL_FILE_NAME, "tokens.txt", "voices.bin"));
    private static final long EXPECTED_DOWNLOAD_BYTES = 350L * 1024 * 1024;

    private KokoroModelFiles() {}

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
                throw new IOException("Unable to create the Kokoro model directory.");
            }
            if (isInstalled(modelDir)) {
                if (listener != null) listener.onProgress(100);
                return modelDir;
            }

            long totalBytesRead = 0;
            for (String fileName : MODEL_FILES) {
                ensureNotCancelled();
                totalBytesRead += downloadFile(fileName, new File(modelDir, fileName),
                        totalBytesRead, listener);
            }
            List<String> invalidFiles = getInvalidFiles(modelDir);
            if (!invalidFiles.isEmpty()) {
                throw new IOException("The Kokoro download is incomplete. Missing or empty files: "
                        + android.text.TextUtils.join(", ", invalidFiles));
            }
            ensureNotCancelled();
            if (listener != null) listener.onProgress(100);
            return modelDir;
        }

        private long downloadFile(String fileName, File target, long previouslyDownloaded,
                                  ProgressListener listener) throws IOException {
            File partial = new File(target.getParentFile(), fileName + ".part");
            byte[] buffer = new byte[32768];
            HttpURLConnection connection =
                    (HttpURLConnection) new URL(BASE_URL + fileName).openConnection();
            activeConnection = connection;
            long bytesRead = 0;
            try {
                ensureNotCancelled();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "VoxSherpa");
                int responseCode = connection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK
                        || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    throw new IOException("Unable to download Kokoro file " + fileName
                            + " (HTTP " + responseCode + ").");
                }
                long contentLength = connection.getContentLengthLong();
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        ensureNotCancelled();
                        output.write(buffer, 0, count);
                        bytesRead += count;
                        if (listener != null) {
                            long bytesDownloaded = previouslyDownloaded + bytesRead;
                            int progress = (int) Math.min(99,
                                    bytesDownloaded * 100 / EXPECTED_DOWNLOAD_BYTES);
                            listener.onProgress(progress);
                        }
                    }
                }
                if (bytesRead == 0 || (contentLength >= 0 && bytesRead != contentLength)) {
                    throw new IOException("Kokoro file " + fileName + " is empty or incomplete.");
                }
                ensureNotCancelled();
                if (target.exists() && !target.delete()) {
                    throw new IOException("Unable to replace Kokoro file " + fileName + ".");
                }
                if (!partial.renameTo(target)) {
                    throw new IOException("Unable to save Kokoro file " + fileName + ".");
                }
                return bytesRead;
            } finally {
                activeConnection = null;
                connection.disconnect();
                if (partial.exists()) partial.delete();
            }
        }

        private void ensureNotCancelled() throws IOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new IOException("Kokoro download was cancelled.");
            }
        }
    }

    public static DownloadTask newDownloadTask() {
        return new DownloadTask();
    }

    public static boolean isKokoroModel(HashMap<String, Object> model) {
        if (model == null) return false;
        Object type = model.get("type");
        Object name = model.get("name");
        Object modelUrl = model.get("model_url");
        return MODEL_TYPE.equalsIgnoreCase(type == null ? "" : type.toString())
                || (name != null && name.toString().toLowerCase(java.util.Locale.ROOT).contains("kokoro"))
                || (modelUrl != null && modelUrl.toString().toLowerCase(java.util.Locale.ROOT).contains("kokoro"));
    }

    public static HashMap<String, Object> createCatalogEntry() {
        HashMap<String, Object> model = new HashMap<>();
        model.put("name", DISPLAY_NAME);
        model.put("type", MODEL_TYPE);
        model.put("language", "Multilingual");
        model.put("gender", "53 speakers");
        model.put("size", DISPLAY_SIZE);
        model.put("quality", "High quality");
        model.put("model_url", BASE_URL);
        model.put("model_files", new ArrayList<>(MODEL_FILES));
        model.put("model_dir_name", MODEL_DIRECTORY_NAME);
        model.put("is_downloading", "false");
        model.put("download_progress", "0");
        return model;
    }

    public static File getModelDirectory(Context context) {
        return new File(new File(context.getFilesDir(), "models"), MODEL_DIRECTORY_NAME);
    }

    public static boolean isInstalled(File modelDir) {
        if (modelDir == null) return false;
        return getInvalidFiles(modelDir).isEmpty();
    }

    public static List<String> getInvalidFiles(File modelDir) {
        List<String> invalidFiles = new ArrayList<>();
        if (modelDir == null) {
            invalidFiles.addAll(MODEL_FILES);
            return invalidFiles;
        }
        for (String fileName : MODEL_FILES) {
            File file = new File(modelDir, fileName);
            if (!file.isFile() || file.length() <= 0) invalidFiles.add(fileName);
        }
        return invalidFiles;
    }

    public static void populateInstalledPaths(HashMap<String, Object> model, File modelDir) {
        model.put("model_dir", modelDir.getAbsolutePath());
        model.put("onnx_path", new File(modelDir, MODEL_FILE_NAME).getAbsolutePath());
        model.put("tokens_path", new File(modelDir, "tokens.txt").getAbsolutePath());
        model.put("voices_bin_path", new File(modelDir, "voices.bin").getAbsolutePath());
    }

    public static void deleteInstalledModel(Context context, HashMap<String, Object> model) {
        deleteTree(getModelDirectory(context));
        clearInstalledPaths(model);
    }

    public static void clearInstalledPaths(HashMap<String, Object> model) {
        model.remove("model_dir");
        model.remove("onnx_path");
        model.remove("tokens_path");
        model.remove("voices_bin_path");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
