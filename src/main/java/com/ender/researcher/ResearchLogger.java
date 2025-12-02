package com.ender.researcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Centralized logger initializer for research-related logs.
 * Writes research logs to run/logs/research.log (appending).
 */
public class ResearchLogger {
    private static boolean initialized = false;
    private static final Path LOG_PATH = EnvPathResolver.getPrimaryLogFile();
    private static final Path INIT_MARKER = EnvPathResolver.getInitMarker();
    private static final String[] LOGGER_NAMES = new String[] {
            "StartResearchPacket",
            "ResearchTableBlockEntity",
            "ClaimResearchPacket"
    };

    public static synchronized void init() {
        if (initialized) return;
        FileHandler fh = null;
        try {
            File file = LOG_PATH.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            // Create a FileHandler that appends to the log file
            fh = new FileHandler(file.getPath(), true);
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.ALL);

            for (String name : LOGGER_NAMES) {
                Logger logger = Logger.getLogger(name);
                // disable parent handlers so we only write via our file handler (avoid double logging)
                logger.setUseParentHandlers(false);
                // avoid adding duplicate handlers
                boolean has = false;
                for (Handler h : logger.getHandlers()) {
                    if (h instanceof FileHandler) { has = true; break; }
                }
                if (!has) {
                    logger.addHandler(fh);
                }
                // Allow detailed messages
                logger.setLevel(Level.ALL);
            }

            // Emit a test line for each logger so the file gets created immediately if init is called
            for (String name : LOGGER_NAMES) {
                Logger.getLogger(name).info("ResearchLogger initialized for logger: " + name + " at " + Instant.now().toString());
            }

            // Create a small marker file so users can quickly verify init ran
            File initFile = INIT_MARKER.toFile();
            File initParent = initFile.getParentFile();
            if (initParent != null && !initParent.exists()) initParent.mkdirs();
            try (FileWriter w = new FileWriter(initFile, true)) {
                w.write("ResearchLogger init at " + Instant.now().toString() + System.lineSeparator());
            } catch (IOException ignored) { }

            // Ensure file handler is closed when JVM exits to flush contents
            final FileHandler toClose = fh;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (toClose != null) toClose.close();
                } catch (Exception ignored) { }
            }));

            initialized = true;
        } catch (IOException e) {
            System.err.println("ResearchLogger: failed to initialize file logging: " + e.getMessage());
            if (fh != null) try { fh.close(); } catch (Exception ignored) {}
        }
    }
}