package com.CodeBySonu.VoxSherpa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class VoiceModelCatalog {

    private VoiceModelCatalog() {}

    public static List<HashMap<String, Object>> createBuiltInEntries() {
        List<HashMap<String, Object>> entries = new ArrayList<>();
        entries.add(KokoroModelFiles.createCatalogEntry());
        entries.add(SupertonicModelFiles.createCatalogEntry());
        entries.addAll(PiperModelCatalog.createCatalogEntries());
        return entries;
    }
}
