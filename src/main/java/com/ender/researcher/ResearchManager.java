package com.ender.researcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.ender.config.ResearchConfigLoader;
import com.ender.config.ResearchEntry;

public class ResearchManager {
    private static final Logger LOGGER = Logger.getLogger("ResearchManager");
    private static final Map<String, ResearchEntry> ENTRIES = new HashMap<>();
    private static final List<ResearchEntry> ORDERED = new ArrayList<>();
    private static String lastSerializedJson = "";

    public static void loadDefaults() {
        List<ResearchEntry> finalList = ResearchConfigLoader.loadEntries();
        applyEntries(finalList);
        lastSerializedJson = ResearchConfigLoader.serializeEntries(ORDERED);
    }

    public static void reloadFromJson(String json) {
        List<ResearchEntry> parsed = ResearchConfigLoader.parseFromJson(json);
        applyEntries(parsed);
        lastSerializedJson = ResearchConfigLoader.serializeEntries(ORDERED);
    }

    public static String getSerializedJson() {
        return lastSerializedJson;
    }

    private static void applyEntries(List<ResearchEntry> entries) {
        ENTRIES.clear();
        ORDERED.clear();
        if (entries == null) {
            LOGGER.warning("No research entries to apply");
            return;
        }
        for (ResearchEntry e : entries) {
            if (e == null || e.id == null) continue;
            ENTRIES.put(e.id, e);
            ORDERED.add(e);
        }

        LOGGER.info("ResearchManager loaded " + ORDERED.size() + " entries");
    }

    public static List<ResearchEntry> getAll() {
        return Collections.unmodifiableList(ORDERED);
    }

    public static ResearchEntry getById(String id) {
        return ENTRIES.get(id);
    }

    public static ResearchEntry getByIndex(int index) {
        if (index < 0 || index >= ORDERED.size()) return null;
        return ORDERED.get(index);
    }

    public static int getIndexForId(String id) {
        for (int i = 0; i < ORDERED.size(); i++) {
            ResearchEntry e = ORDERED.get(i);
            if (e != null && e.id != null && e.id.equals(id)) return i;
        }
        return -1;
    }
}