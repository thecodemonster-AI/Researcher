package com.ender.researcher;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small utility helpers used by persistence code to serialize ItemStacks and
 * perform atomic JSON file writes.
 */
public final class PersistenceUtils {
    private PersistenceUtils() {}

    public static String itemStackToSNBT(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            CompoundTag tag = stack.save(new CompoundTag());
            return tag.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    public static ItemStack itemStackFromSNBT(String snbt) {
        if (snbt == null || snbt.isEmpty()) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            return ItemStack.of(tag);
        } catch (Exception ex) {
            return ItemStack.EMPTY;
        }
    }

    // Write text to file atomically: write to temp file then move
    public static void writeStringAtomic(Path path, String text) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.createDirectories(path.getParent());
        Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String readString(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
