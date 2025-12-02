package com.ender.config;

import com.ender.researcher.EnvPathResolver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResearchConfigLoader {
    private static final Logger LOGGER = Logger.getLogger("ResearchConfigLoader");
    private static final String DEFAULT_CLASSPATH = "assets/researchtable/research_default.json";
    private static final String ROOT_KEY = "research";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type WRAPPER_TYPE = new TypeToken<Map<String, List<ResearchEntry>>>(){}.getType();

    public static List<ResearchEntry> loadEntries() {
        Path configFile = getConfigFile();
        List<ResearchEntry> fromDisk = readConfig(configFile);
        if (!fromDisk.isEmpty()) {
            return fromDisk;
        }

        List<ResearchEntry> defaults = readBundledDefaults();
        if (!defaults.isEmpty()) {
            copyDefaultToConfig(configFile);
        }
        return defaults;
    }

    private static Path getConfigFile() {
        return EnvPathResolver.getConfigDir().resolve("research.json");
    }

    private static List<ResearchEntry> readConfig(Path file) {
        if (file == null || !Files.exists(file)) {
            return Collections.emptyList();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parseWrapper(reader);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load research config from " + file.toAbsolutePath(), e);
            return Collections.emptyList();
        }
    }

    private static List<ResearchEntry> readBundledDefaults() {
        try (InputStream stream = ResearchConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CLASSPATH)) {
            if (stream == null) {
                LOGGER.log(Level.WARNING, "Default research config not found on classpath: " + DEFAULT_CLASSPATH);
                return Collections.emptyList();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parseWrapper(reader);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read bundled research defaults", e);
            return Collections.emptyList();
        }
    }

    private static List<ResearchEntry> parseWrapper(Reader reader) throws IOException {
        Map<String, List<ResearchEntry>> map = GSON.fromJson(reader, WRAPPER_TYPE);
        if (map == null) {
            return new ArrayList<>();
        }
        List<ResearchEntry> list = map.get(ROOT_KEY);
        if (list == null) {
            return new ArrayList<>();
        }
        return list;
    }

    public static List<ResearchEntry> parseFromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try (Reader reader = new StringReader(json)) {
            return parseWrapper(reader);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to parse research config JSON", e);
            return new ArrayList<>();
        }
    }

    public static String serializeEntries(List<ResearchEntry> entries) {
        Map<String, List<ResearchEntry>> wrapper = new HashMap<>();
        wrapper.put(ROOT_KEY, entries != null ? new ArrayList<>(entries) : Collections.emptyList());
        return GSON.toJson(wrapper);
    }

    private static void copyDefaultToConfig(Path target) {
        if (target == null) {
            return;
        }
        try (InputStream stream = ResearchConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CLASSPATH)) {
            if (stream == null) {
                return;
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to copy default research config to " + target.toAbsolutePath(), e);
        }
    }
}