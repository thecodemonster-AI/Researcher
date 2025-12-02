package com.ender.researcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple file-backed fallback logger that appends lines to both logs/research.log and run/logs/research.log.
 * This ensures the file is present under the common working directories used by the dev environment.
 */
public class ResearchFileLogger {
    private static Path[] resolvePaths() {
        Path primary = EnvPathResolver.getPrimaryLogFile();
        Path secondary = primary.getParent() != null ? primary.getParent().resolve("research.log") : primary;
        return new Path[] {primary, secondary};
    }
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static synchronized void log(String msg) {
        String line = LocalDateTime.now().format(FMT) + " " + msg + System.lineSeparator();
        for (Path path : resolvePaths()) {
            try {
                File f = path.toFile();
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileWriter w = new FileWriter(f, true)) {
                    w.write(line);
                }
            } catch (IOException e) {
                System.err.println("ResearchFileLogger failed to write to " + path + ": " + e.getMessage());
            }
        }
    }
}