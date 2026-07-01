package org.ha2yo.paint.renderer;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VanillaMapEncoder {
    private static final Map<Integer, Byte> MATCH_CACHE = new ConcurrentHashMap<>();

    private VanillaMapEncoder() {
    }

    static byte[] encode(Color[] colors, int width, int height) {
        if (colors.length != width * height) {
            throw new IllegalArgumentException("Map colors must match the target dimensions");
        }
        byte[] indexes = new byte[colors.length];
        for (int index = 0; index < colors.length; index++) {
            indexes[index] = match(colors[index]);
        }
        return indexes;
    }

    @SuppressWarnings({"deprecation", "removal"})
    static byte match(Color color) {
        int key = color.getRGB() & 0xFFFFFF;
        return MATCH_CACHE.computeIfAbsent(key, ignored -> MapPalette.matchColor(color));
    }

    @SuppressWarnings({"deprecation", "removal"})
    static void paint(MapCanvas canvas, byte[] indexes, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                canvas.setPixel(x, y, indexes[y * width + x]);
            }
        }
    }
}
