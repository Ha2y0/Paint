package org.ha2yo.paint.renderer;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class VanillaMapEncoder {
    private static final Map<Integer, Byte> MATCH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Byte> OKLAB_MATCH_CACHE = new ConcurrentHashMap<>();
    private static final List<PaletteEntry> PALETTE = buildPalette();

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
        return MATCH_CACHE.computeIfAbsent(key, ignored -> matchGoodFast(color));
    }

    @SuppressWarnings({"deprecation", "removal"})
    static Color color(byte index) {
        return MapPalette.getColor(index);
    }

    static byte oklabMatch(Color color) {
        int key = color.getRGB() & 0xFFFFFF;
        return OKLAB_MATCH_CACHE.computeIfAbsent(key, ignored -> matchOklab(color));
    }

    @SuppressWarnings({"deprecation", "removal"})
    static void paint(MapCanvas canvas, byte[] indexes, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                canvas.setPixel(x, y, indexes[y * width + x]);
            }
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static List<PaletteEntry> buildPalette() {
        List<PaletteEntry> entries = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int raw = 4; raw < 256; raw++) {
            try {
                Color color = MapPalette.getColor((byte) raw);
                int rgb = color.getRGB() & 0xFFFFFF;
                if (seen.add(rgb)) {
                    entries.add(new PaletteEntry(
                            (byte) raw,
                            color,
                            Oklab.from(color)
                    ));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return List.copyOf(entries);
    }

    private static byte matchOklab(Color color) {
        Oklab source = Oklab.from(color);
        double bestDistance = Double.MAX_VALUE;
        byte bestIndex = PALETTE.get(0).index();
        for (PaletteEntry entry : PALETTE) {
            double deltaL = source.l() - entry.lab().l();
            double deltaA = source.a() - entry.lab().a();
            double deltaB = source.b() - entry.lab().b();
            double distance = deltaL * deltaL * 1.15D + deltaA * deltaA + deltaB * deltaB;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = entry.index();
            }
        }
        return bestIndex;
    }

    private static byte matchGoodFast(Color color) {
        double bestDistance = Double.MAX_VALUE;
        byte bestIndex = PALETTE.get(0).index();
        for (PaletteEntry entry : PALETTE) {
            Color palette = entry.color();
            double red = color.getRed() - palette.getRed();
            double green = color.getGreen() - palette.getGreen();
            double blue = color.getBlue() - palette.getBlue();
            double distance = red * red + green * green + blue * blue;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = entry.index();
            }
        }
        return bestIndex;
    }

    private record PaletteEntry(byte index, Color color, Oklab lab) {
    }

    private record Oklab(double l, double a, double b) {
        private static Oklab from(Color color) {
            double red = linear(color.getRed() / 255.0D);
            double green = linear(color.getGreen() / 255.0D);
            double blue = linear(color.getBlue() / 255.0D);

            double lmsL = Math.cbrt(0.4122214708D * red + 0.5363325363D * green + 0.0514459929D * blue);
            double lmsM = Math.cbrt(0.2119034982D * red + 0.6806995451D * green + 0.1073969566D * blue);
            double lmsS = Math.cbrt(0.0883024619D * red + 0.2817188376D * green + 0.6299787005D * blue);

            return new Oklab(
                    0.2104542553D * lmsL + 0.7936177850D * lmsM - 0.0040720468D * lmsS,
                    1.9779984951D * lmsL - 2.4285922050D * lmsM + 0.4505937099D * lmsS,
                    0.0259040371D * lmsL + 0.7827717662D * lmsM - 0.8086757660D * lmsS
            );
        }
    }

    private static double linear(double value) {
        return value <= 0.04045D
                ? value / 12.92D
                : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }
}
