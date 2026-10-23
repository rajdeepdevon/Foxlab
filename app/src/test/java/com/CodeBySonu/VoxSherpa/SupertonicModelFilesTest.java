package com.CodeBySonu.VoxSherpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SupertonicModelFilesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void catalogEntryListsRawModelFiles() {
        HashMap<String, Object> entry = SupertonicModelFiles.createCatalogEntry();

        assertEquals("400 MB", entry.get("size"));
        assertEquals(SupertonicModelFiles.BASE_URL, entry.get("model_url"));
        assertEquals(Arrays.asList(
                "vector_estimator.onnx",
                "vocoder.onnx",
                "text_encoder.onnx",
                "duration_predictor.onnx",
                "tts.json",
                "unicode_indexer.bin",
                "voice.bin"
        ), entry.get("model_files"));
        assertFalse(entry.containsKey("archive_name"));
        assertTrue(SupertonicModelFiles.MODEL_FILES.contains("vector_estimator.onnx"));
        assertTrue(SupertonicModelFiles.MODEL_FILES.contains("vocoder.onnx"));
        assertTrue(SupertonicModelFiles.MODEL_FILES.contains("text_encoder.onnx"));
        assertTrue(SupertonicModelFiles.MODEL_FILES.contains("duration_predictor.onnx"));
    }

    @Test
    public void installedPathsPointIntoTheRawModelDirectory() throws Exception {
        File modelDir = temporaryFolder.newFolder("supertonic");
        String[] files = {
                "duration_predictor.onnx",
                "text_encoder.onnx",
                "vector_estimator.onnx",
                "vocoder.onnx",
                "tts.json",
                "unicode_indexer.bin",
                "voice.bin"
        };
        for (String name : files) {
            try (FileOutputStream output = new FileOutputStream(new File(modelDir, name))) {
                output.write(1);
            }
        }

        assertTrue(SupertonicModelFiles.isInstalled(modelDir));
        HashMap<String, Object> model = new HashMap<>();
        SupertonicModelFiles.populateInstalledPaths(model, modelDir);

        assertEquals(modelDir.getAbsolutePath(), model.get("model_dir"));
        assertEquals(new File(modelDir, "duration_predictor.onnx").getAbsolutePath(),
                model.get("onnx_path"));
        assertEquals(new File(modelDir, "text_encoder.onnx").getAbsolutePath(),
                model.get("text_encoder_path"));
        assertEquals(new File(modelDir, "vector_estimator.onnx").getAbsolutePath(),
                model.get("vector_estimator_path"));
        assertEquals(new File(modelDir, "vocoder.onnx").getAbsolutePath(),
                model.get("vocoder_path"));
        assertEquals(new File(modelDir, "voice.bin").getAbsolutePath(), model.get("voices_bin_path"));
    }
}
