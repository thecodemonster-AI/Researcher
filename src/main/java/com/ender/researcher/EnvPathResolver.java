package com.ender.researcher;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central place to resolve config/log directories depending on whether the mod
 * is running on a dedicated server or a local/client environment.
 */
public final class EnvPathResolver {
    private EnvPathResolver() {}

    private static boolean isDedicatedServer() {
        return FMLLoader.getDist().isDedicatedServer();
    }

    public static Path getConfigDir() {
        Path base = FMLPaths.CONFIGDIR.get();
        return base.resolve("researchtable");
    }

    public static Path getPendingDir() {
        return getConfigDir().resolve("pending");
    }

    public static Path getLogsDir() {
        Path base = FMLPaths.GAMEDIR.get().resolve("logs");
        return base.resolve("researchtable");
    }

    public static Path getPrimaryLogFile() {
        return getLogsDir().resolve("research.log");
    }

    public static Path getInitMarker() {
        return getLogsDir().resolve("research_init.txt");
    }
}