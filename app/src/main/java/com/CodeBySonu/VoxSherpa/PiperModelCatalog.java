package com.CodeBySonu.VoxSherpa;

import android.content.Context;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class PiperModelCatalog {
    private static final String RELEASE_BASE =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/";
    private static final String MODEL_FILE_NAME = "model.onnx";
    private static final String TOKENS_FILE_NAME = "tokens.txt";

    private PiperModelCatalog() {}

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public static List<HashMap<String, Object>> createCatalogEntries() {
        List<HashMap<String, Object>> entries = new ArrayList<>();
        entries.add(createEntry("Libritts-high", "English", "Multi", "110 MB",
                "vits-piper-en_US-libritts-high.tar.bz2"));
        entries.add(createEntry("Glados-high", "English", "Female", "96 MB",
                "vits-piper-en_US-glados-high.tar.bz2"));
        entries.add(createEntry("Ljspeech-high", "English", "Female", "96 MB",
                "vits-piper-en_US-ljspeech-high.tar.bz2"));
        entries.add(createEntry("Ryan-high", "English", "Male", "102 MB",
                "vits-piper-en_US-ryan-high.tar.bz2"));
        entries.add(createEntry("Lessac-high", "English", "Female", "96 MB",
                "vits-piper-en_US-lessac-high.tar.bz2"));
        entries.add(createEntry("Cori-high", "English (UK)", "Female", "96 MB",
                "vits-piper-en_GB-cori-high.tar.bz2"));
        entries.add(createEntry("Pratham-medium", "Hindi", "Male", "62 MB",
                "vits-piper-hi_IN-pratham-medium.tar.bz2"));
        entries.add(createEntry("Priyamvada-medium", "Hindi", "Female", "62 MB",
                "vits-piper-hi_IN-priyamvada-medium.tar.bz2"));
        entries.add(createEntry("Rohan-medium", "Hindi", "Male", "62 MB",
                "vits-piper-hi_IN-rohan-medium.tar.bz2"));
        return entries;
    }

    public static List<String> getCatalogUrls() {
        List<String> urls = new ArrayList<>();
        for (HashMap<String, Object> entry : createCatalogEntries()) {
            urls.add(String.valueOf(entry.get("model_url")));
        }
        return urls;
    }

    public static boolean isCatalogModel(HashMap<String, Object> model) {
        if (model == null) return false;
        Object value = model.get("model_url");
        if (value == null) return false;
        String url = value.toString();
        for (String catalogUrl : getCatalogUrls()) {
            if (catalogUrl.equals(url)) return true;
        }
        return false;
    }

    public static boolean isCatalogUrl(String url) {
        return url != null && getCatalogUrls().contains(url);
    }

    public static HashMap<String, Object> createEntry(
            String name, String language, String gender, String size, String archiveName) {
        String archiveUrl = RELEASE_BASE + archiveName;
        String directoryName = archiveName.substring(0, archiveName.length() - ".tar.bz2".length());
        String id = directoryName.substring("vits-piper-".length()).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");

        HashMap<String, Object> model = new HashMap<>();
        model.put("name", name);
        model.put("type", "piper");
        model.put("language", language);
        model.put("gender", gender);
        model.put("size", size);
        model.put("quality", "High quality");
        model.put("model_url", archiveUrl);
        model.put("archive_url", archiveUrl);
        model.put("archive_name", archiveName);
        model.put("model_dir_name", directoryName);
        model.put("model_id", id);
        model.put("is_downloading", "false");
        model.put("download_progress", "0");
        return model;
    }

    public static File getModelDirectory(Context context, HashMap<String, Object> model) {
        String directoryName = String.valueOf(model.get("model_dir_name"));
        return new File(new File(context.getFilesDir(), "models"), directoryName);
    }

    public static boolean isInstalled(File modelDir) {
        if (modelDir == null) return false;
        File onnx = new File(modelDir, MODEL_FILE_NAME);
        File tokens = new File(modelDir, TOKENS_FILE_NAME);
        return onnx.isFile() && onnx.length() > 0 && tokens.isFile() && tokens.length() > 0;
    }

    public static void populateInstalledPaths(HashMap<String, Object> model, File modelDir) {
        model.put("model_dir", modelDir.getAbsolutePath());
        model.put("onnx_path", new File(modelDir, MODEL_FILE_NAME).getAbsolutePath());
        model.put("tokens_path", new File(modelDir, TOKENS_FILE_NAME).getAbsolutePath());
    }

    public static void deleteInstalledModel(HashMap<String, Object> model) {
        Object path = model.get("model_dir");
        if (path != null) deleteTree(new File(path.toString()));
        model.remove("model_dir");
        model.remove("onnx_path");
        model.remove("tokens_path");
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

        public File download(Context context, HashMap<String, Object> model,
                             ProgressListener listener) throws IOException {
            if (context == null) throw new IOException("Context is unavailable.");
            if (!isCatalogModel(model)) throw new IOException("Unknown Piper model catalog entry.");
            File modelDir = getModelDirectory(context, model);
            if (isInstalled(modelDir)) {
                if (listener != null) listener.onProgress(100);
                return modelDir;
            }

            File modelsDir = modelDir.getParentFile();
            if (!modelsDir.exists() && !modelsDir.mkdirs() && !modelsDir.isDirectory()) {
                throw new IOException("Unable to create the Piper model directory.");
            }
            String archiveName = String.valueOf(model.get("archive_name"));
            File archive = new File(modelsDir, archiveName);
            File partial = new File(modelsDir, archiveName + ".part");
            HttpURLConnection connection = (HttpURLConnection)
                    new URL(String.valueOf(model.get("archive_url"))).openConnection();
            activeConnection = connection;
            byte[] buffer = new byte[32768];
            try {
                ensureNotCancelled();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "VoxSherpa");
                int responseCode = connection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK
                        || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    throw new IOException("Unable to download the Piper archive (HTTP "
                            + responseCode + ").");
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
                        if (listener != null && contentLength > 0) {
                            listener.onProgress((int) Math.min(90, bytesRead * 90 / contentLength));
                        }
                    }
                }
                if (bytesRead == 0 || (contentLength >= 0 && bytesRead != contentLength)) {
                    throw new IOException("The Piper archive is empty or incomplete.");
                }
                ensureNotCancelled();
                if (archive.exists() && !archive.delete()) {
                    throw new IOException("Unable to replace the previous Piper archive.");
                }
                if (!partial.renameTo(archive)) {
                    throw new IOException("Unable to save the Piper archive.");
                }
            } finally {
                activeConnection = null;
                connection.disconnect();
                if (partial.exists()) partial.delete();
            }

            try {
                ensureNotCancelled();
                if (listener != null) listener.onProgress(92);
                extractArchive(archive, modelDir, this);
                if (listener != null) listener.onProgress(100);
                return modelDir;
            } catch (IOException error) {
                if (archive.exists()) archive.delete();
                throw error;
            }
        }

        private void ensureNotCancelled() throws IOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new IOException("Piper download was cancelled.");
            }
        }
    }

    public static DownloadTask newDownloadTask() {
        return new DownloadTask();
    }

    static void extractArchive(File archive, File modelDir, DownloadTask task)
            throws IOException {
        if (isInstalled(modelDir)) {
            archive.delete();
            return;
        }
        File stagingDir = new File(modelDir.getParentFile(), modelDir.getName() + ".extracting");
        deleteTree(stagingDir);
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory()) {
            throw new IOException("Unable to create the Piper staging directory.");
        }

        try (InputStream fileInput = new BufferedInputStream(new FileInputStream(archive));
             InputStream bzipInput = new BZip2CompressorInputStream(fileInput);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(bzipInput)) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[32768];
            String stagingRoot = stagingDir.getCanonicalPath() + File.separator;

            while ((entry = tarInput.getNextTarEntry()) != null) {
                task.ensureNotCancelled();
                if (!entry.isFile()) continue;
                String entryName = entry.getName().replace('\\', '/');
                while (entryName.startsWith("./")) entryName = entryName.substring(2);
                String archiveFileName = new File(entryName).getName();
                String outputName;
                if (archiveFileName.toLowerCase(Locale.ROOT).endsWith(".onnx")) {
                    outputName = MODEL_FILE_NAME;
                } else if (isTokenFile(archiveFileName)) {
                    outputName = TOKENS_FILE_NAME;
                } else {
                    continue;
                }

                File output = new File(stagingDir, outputName).getCanonicalFile();
                if (!output.getPath().startsWith(stagingRoot)) {
                    throw new IOException("Unsafe path in Piper archive.");
                }
                if (output.exists()) {
                    throw new IOException("The Piper archive contains multiple model or token files.");
                }
                long bytesRemaining = entry.getSize();
                if (bytesRemaining < 0) {
                    throw new IOException("A Piper archive entry has an invalid size.");
                }
                try (FileOutputStream outputStream = new FileOutputStream(output)) {
                    while (bytesRemaining > 0) {
                        task.ensureNotCancelled();
                        int count = tarInput.read(buffer, 0,
                                (int) Math.min(buffer.length, bytesRemaining));
                        if (count <= 0) {
                            throw new EOFException(
                                    "The Piper archive ended before a file was fully extracted.");
                        }
                        outputStream.write(buffer, 0, count);
                        bytesRemaining -= count;
                    }
                }
            }
        } catch (IOException error) {
            deleteTree(stagingDir);
            throw error;
        }

        if (!isInstalled(stagingDir)) {
            deleteTree(stagingDir);
            throw new IOException("The Piper archive is incomplete or has an unexpected layout.");
        }
        deleteTree(modelDir);
        if (!stagingDir.renameTo(modelDir)) {
            deleteTree(stagingDir);
            throw new IOException("Unable to install the extracted Piper model.");
        }
        archive.delete();
    }

    private static boolean isTokenFile(String fileName) {
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        return "tokens.txt".equals(normalizedName)
                || normalizedName.endsWith(".tokens")
                || normalizedName.endsWith(".tokens.txt")
                || normalizedName.endsWith("_tokens.txt");
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
