package com.CodeBySonu.VoxSherpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KokoroModelFilesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void catalogEntryUsesTheRawMultiFileSource() {
        HashMap<String, Object> entry = KokoroModelFiles.createCatalogEntry();

        assertEquals("Kokoro", entry.get("name"));
        assertEquals("350 MB", entry.get("size"));
        assertEquals("kokoro", entry.get("type"));
        assertEquals(
                "https://huggingface.co/seepa22/Kokoro-82M/resolve/main/kokoro-multi-lang/",
                entry.get("model_url"));
        assertEquals("model.onnx", KokoroModelFiles.MODEL_FILE_NAME);
        assertEquals(java.util.Arrays.asList("model.onnx", "tokens.txt", "voices.bin"),
                entry.get("model_files"));
        assertFalse(entry.containsKey("archive_url"));
        assertFalse(entry.containsKey("archive_name"));
    }

    @Test
    public void installedPathsRequireAndReferenceTheFullPrecisionModelFiles() throws Exception {
        File modelDir = temporaryFolder.newFolder(KokoroModelFiles.MODEL_DIRECTORY_NAME);
        String[] files = {KokoroModelFiles.MODEL_FILE_NAME, "tokens.txt", "voices.bin"};
        for (String name : files) {
            try (FileOutputStream output = new FileOutputStream(new File(modelDir, name))) {
                output.write(1);
            }
        }

        assertTrue(KokoroModelFiles.isInstalled(modelDir));
        HashMap<String, Object> model = new HashMap<>();
        KokoroModelFiles.populateInstalledPaths(model, modelDir);

        assertEquals(modelDir.getAbsolutePath(), model.get("model_dir"));
        assertEquals(new File(modelDir, "model.onnx").getAbsolutePath(), model.get("onnx_path"));
        assertEquals(new File(modelDir, "tokens.txt").getAbsolutePath(), model.get("tokens_path"));
        assertEquals(new File(modelDir, "voices.bin").getAbsolutePath(), model.get("voices_bin_path"));
    }

    @Test
    public void recognizesKokoroCatalogEntries() {
        HashMap<String, Object> entry = new HashMap<>();
        entry.put("type", "kokoro");

        assertTrue(KokoroModelFiles.isKokoroModel(entry));
    }

    @Test
    public void installationValidationRequiresAllRawModelFiles() throws Exception {
        File modelDir = temporaryFolder.newFolder(KokoroModelFiles.MODEL_DIRECTORY_NAME);
        try (FileOutputStream output = new FileOutputStream(
                new File(modelDir, KokoroModelFiles.MODEL_FILE_NAME))) {
            output.write(1);
        }

        assertFalse(KokoroModelFiles.isInstalled(modelDir));
        assertEquals(java.util.Arrays.asList("tokens.txt", "voices.bin"),
                KokoroModelFiles.getInvalidFiles(modelDir));
    }
}
