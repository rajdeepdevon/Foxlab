package com.CodeBySonu.VoxSherpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PiperModelCatalogTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void builtInCatalogContainsExactlyElevenEntriesInRequestedOrder() {
        List<HashMap<String, Object>> entries = VoiceModelCatalog.createBuiltInEntries();
        String[] names = {
                "Kokoro",
                "Supertonic V3",
                "Libritts-high",
                "Glados-high",
                "Ljspeech-high",
                "Ryan-high",
                "Lessac-high",
                "Cori-high",
                "Pratham-medium",
                "Priyamvada-medium",
                "Rohan-medium"
        };

        assertEquals(names.length, entries.size());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], entries.get(i).get("name"));
        }
    }

    @Test
    public void catalogHasTheNineRequestedModelsInOrder() {
        List<HashMap<String, Object>> entries = PiperModelCatalog.createCatalogEntries();
        String[] names = {
                "Libritts-high",
                "Glados-high",
                "Ljspeech-high",
                "Ryan-high",
                "Lessac-high",
                "Cori-high",
                "Pratham-medium",
                "Priyamvada-medium",
                "Rohan-medium"
        };
        String[] urls = {
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-en_US-libritts-high.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-en_US-glados-high.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-en_US-ljspeech-high.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-en_US-ryan-high.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-en_US-lessac-high.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-en_GB-cori-high.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-hi_IN-pratham-medium.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-hi_IN-priyamvada-medium.tar.bz2",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
                        + "vits-piper-hi_IN-rohan-medium.tar.bz2"
        };
        String[] subtitles = {
                "English|Multi|110 MB",
                "English|Female|96 MB",
                "English|Female|96 MB",
                "English|Male|102 MB",
                "English|Female|96 MB",
                "English (UK)|Female|96 MB",
                "Hindi|Male|62 MB",
                "Hindi|Female|62 MB",
                "Hindi|Male|62 MB"
        };

        assertEquals(names.length, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HashMap<String, Object> entry = entries.get(i);
            assertEquals(names[i], entry.get("name"));
            assertEquals(urls[i], entry.get("model_url"));
            assertEquals("piper", entry.get("type"));
            assertEquals(subtitles[i],
                    entry.get("language") + "|" + entry.get("gender") + "|" + entry.get("size"));
            assertEquals(entry.get("model_url"), entry.get("archive_url"));
        }
    }

    @Test
    public void catalogRecognizesOnlyItsArchiveUrls() {
        List<HashMap<String, Object>> entries = PiperModelCatalog.createCatalogEntries();

        for (HashMap<String, Object> entry : entries) {
            assertTrue(PiperModelCatalog.isCatalogModel(entry));
        }
    }

    @Test
    public void installedFilesMapToThePiperEngineInputs() throws Exception {
        File modelDir = temporaryFolder.newFolder("piper-model");
        for (String name : new String[] {"model.onnx", "tokens.txt"}) {
            try (FileOutputStream output = new FileOutputStream(new File(modelDir, name))) {
                output.write(1);
            }
        }

        assertTrue(PiperModelCatalog.isInstalled(modelDir));
        HashMap<String, Object> model = new HashMap<>();
        PiperModelCatalog.populateInstalledPaths(model, modelDir);

        assertEquals(new File(modelDir, "model.onnx").getAbsolutePath(), model.get("onnx_path"));
        assertEquals(new File(modelDir, "tokens.txt").getAbsolutePath(), model.get("tokens_path"));
    }

    @Test
    public void extractionFindsDynamicallyNamedNestedModelAndTokens() throws Exception {
        File archive = temporaryFolder.newFile("piper.tar.bz2");
        File modelDir = temporaryFolder.newFolder("installed-piper");
        try (OutputStream fileOutput = new FileOutputStream(archive);
             BZip2CompressorOutputStream bzipOutput =
                     new BZip2CompressorOutputStream(fileOutput);
             TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(bzipOutput)) {
            addEntry(tarOutput, "vits-piper-hi_IN-pratham-medium/"
                            + "hi_IN-pratham-medium.onnx", "onnx".getBytes(StandardCharsets.UTF_8));
            addEntry(tarOutput, "vits-piper-hi_IN-pratham-medium/"
                            + "hi_IN-pratham-medium.tokens",
                    "tokens".getBytes(StandardCharsets.UTF_8));
        }

        PiperModelCatalog.extractArchive(
                archive, modelDir, PiperModelCatalog.newDownloadTask());

        assertTrue(PiperModelCatalog.isInstalled(modelDir));
        HashMap<String, Object> model = new HashMap<>();
        PiperModelCatalog.populateInstalledPaths(model, modelDir);
        assertEquals(new File(modelDir, "model.onnx").getAbsolutePath(), model.get("onnx_path"));
        assertEquals(new File(modelDir, "tokens.txt").getAbsolutePath(), model.get("tokens_path"));
        assertEquals("onnx", new String(
                java.nio.file.Files.readAllBytes(new File(modelDir, "model.onnx").toPath()),
                StandardCharsets.UTF_8));
        assertEquals("tokens", new String(
                java.nio.file.Files.readAllBytes(new File(modelDir, "tokens.txt").toPath()),
                StandardCharsets.UTF_8));
    }

    private static void addEntry(
            TarArchiveOutputStream tarOutput, String name, byte[] content) throws Exception {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        tarOutput.putArchiveEntry(entry);
        tarOutput.write(content);
        tarOutput.closeArchiveEntry();
    }
}
