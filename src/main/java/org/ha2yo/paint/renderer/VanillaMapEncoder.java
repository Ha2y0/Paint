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
    private static final Map<Integer, Byte> HUE_SAFE_MATCH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Byte> POSTER_MATCH_CACHE = new ConcurrentHashMap<>();
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

    static byte hueSafeMatch(Color color) {
        int key = color.getRGB() & 0xFFFFFF;
        return HUE_SAFE_MATCH_CACHE.computeIfAbsent(key, ignored -> matchHueSafe(color));
    }

    static byte posterMatch(Color original, Color posterized) {
        int key = ((original.getRGB() & 0xFFFFFF) * 31) ^ (posterized.getRGB() & 0xFFFFFF);
        return POSTER_MATCH_CACHE.computeIfAbsent(key, ignored -> matchPoster(original, posterized));
    }

    static byte familyMatch(Color color) {
        int key = color.getRGB() & 0xFFFFFF;
        return POSTER_MATCH_CACHE.computeIfAbsent(key, ignored -> matchFamily(color));
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
                            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null),
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

    private static byte matchHueSafe(Color color) {
        float[] sourceHsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        double sourceLuma = luma(color);
        double bestDistance = Double.MAX_VALUE;
        byte bestIndex = match(color);

        for (PaletteEntry entry : PALETTE) {
            Color palette = entry.color();
            double red = channelDelta(color.getRed(), palette.getRed());
            double green = channelDelta(color.getGreen(), palette.getGreen());
            double blue = channelDelta(color.getBlue(), palette.getBlue());
            double distance = red * red + green * green + blue * blue;

            double brightnessDelta = sourceHsb[2] - entry.hsb()[2];
            double saturationDelta = sourceHsb[1] - entry.hsb()[1];
            distance += brightnessDelta * brightnessDelta * 0.75D;
            distance += saturationDelta * saturationDelta * 0.28D;

            if (sourceHsb[1] > 0.12F && entry.hsb()[1] > 0.10F) {
                double hueDelta = hueDistance(sourceHsb[0], entry.hsb()[0]);
                distance += hueDelta * hueDelta * hueWeight(sourceHsb[1], sourceLuma);
            }
            if (sourceHsb[1] > 0.20F && entry.hsb()[1] < 0.08F) {
                distance += 0.08D;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = entry.index();
            }
        }
        return bestIndex;
    }

    private static byte matchPoster(Color original, Color posterized) {
        float[] originalHsb = Color.RGBtoHSB(original.getRed(), original.getGreen(), original.getBlue(), null);
        float[] posterHsb = Color.RGBtoHSB(posterized.getRed(), posterized.getGreen(), posterized.getBlue(), null);
        double originalLuma = luma(original);
        HueFamily originalFamily = hueFamily(originalHsb);

        double bestDistance = Double.MAX_VALUE;
        byte bestIndex = hueSafeMatch(posterized);
        for (PaletteEntry entry : PALETTE) {
            HueFamily candidateFamily = hueFamily(entry.hsb());
            double distance = posterDistance(posterized, posterHsb, originalLuma, entry);
            distance += familyPenalty(originalFamily, candidateFamily, originalHsb, entry.hsb());

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = entry.index();
            }
        }
        return bestIndex;
    }

    private static byte matchFamily(Color color) {
        float[] sourceHsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        HueFamily sourceFamily = hueFamily(sourceHsb);
        double sourceLuma = luma(color);
        double bestDistance = Double.MAX_VALUE;
        byte bestIndex = match(color);

        for (PaletteEntry entry : PALETTE) {
            HueFamily candidateFamily = hueFamily(entry.hsb());
            double distance = perceptualRgbDistance(color, entry.color());
            distance += familyPenalty(sourceFamily, candidateFamily, sourceHsb, entry.hsb()) * 2.2D;

            double hueDelta = hueDistance(sourceHsb[0], entry.hsb()[0]);
            if (sourceFamily != HueFamily.NEUTRAL && candidateFamily != HueFamily.NEUTRAL) {
                distance += hueDelta * hueDelta * hueWeight(sourceHsb[1], sourceLuma) * 1.8D;
            }
            if (sourceFamily == HueFamily.PINK && (candidateFamily == HueFamily.GREEN || candidateFamily == HueFamily.YELLOW)) {
                distance += 2.5D;
            }
            if (sourceFamily == HueFamily.WARM && candidateFamily == HueFamily.GREEN) {
                distance += 2.0D;
            }
            if (sourceFamily == HueFamily.YELLOW && candidateFamily == HueFamily.GREEN && sourceHsb[0] < 0.14F) {
                distance += 1.2D;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = entry.index();
            }
        }
        return bestIndex;
    }

    private static double perceptualRgbDistance(Color source, Color target) {
        double red = channelDelta(source.getRed(), target.getRed());
        double green = channelDelta(source.getGreen(), target.getGreen());
        double blue = channelDelta(source.getBlue(), target.getBlue());
        return red * red * 0.30D + green * green * 0.59D + blue * blue * 0.26D;
    }

    private static double posterDistance(Color source, float[] sourceHsb, double sourceLuma, PaletteEntry entry) {
        Color palette = entry.color();
        double red = channelDelta(source.getRed(), palette.getRed());
        double green = channelDelta(source.getGreen(), palette.getGreen());
        double blue = channelDelta(source.getBlue(), palette.getBlue());
        double distance = red * red + green * green + blue * blue;

        double hueDelta = hueDistance(sourceHsb[0], entry.hsb()[0]);
        double saturationDelta = sourceHsb[1] - entry.hsb()[1];
        double brightnessDelta = sourceHsb[2] - entry.hsb()[2];
        distance += hueDelta * hueDelta * hueWeight(sourceHsb[1], sourceLuma) * 1.35D;
        distance += saturationDelta * saturationDelta * 0.35D;
        distance += brightnessDelta * brightnessDelta * 0.55D;
        return distance;
    }

    private static double familyPenalty(HueFamily original, HueFamily candidate, float[] originalHsb, float[] candidateHsb) {
        if (original == HueFamily.NEUTRAL || candidate == HueFamily.NEUTRAL) {
            return 0.0D;
        }
        if (original == candidate) {
            return 0.0D;
        }
        if (original == HueFamily.WARM && candidate == HueFamily.YELLOW) {
            return 0.35D;
        }
        if (original == HueFamily.YELLOW && candidate == HueFamily.WARM) {
            return 0.18D;
        }
        if (original == HueFamily.WARM && candidate == HueFamily.GREEN) {
            return 1.4D;
        }
        if (original == HueFamily.PINK && candidate == HueFamily.YELLOW) {
            return 0.75D;
        }
        if (original == HueFamily.PINK && candidate == HueFamily.GREEN) {
            return 1.6D;
        }
        if (originalHsb[1] > 0.18F && candidateHsb[1] > 0.12F) {
            return 0.55D;
        }
        return 0.20D;
    }

    private static HueFamily hueFamily(float[] hsb) {
        if (hsb[1] < 0.10F) {
            return HueFamily.NEUTRAL;
        }
        float hue = hsb[0];
        if (hue >= 0.91F || hue < 0.09F) {
            return HueFamily.WARM;
        }
        if (hue < 0.17F) {
            return HueFamily.YELLOW;
        }
        if (hue < 0.42F) {
            return HueFamily.GREEN;
        }
        if (hue < 0.72F) {
            return HueFamily.COOL;
        }
        return HueFamily.PINK;
    }

    private static double hueWeight(float saturation, double luma) {
        double weight = 1.5D + saturation * 2.5D;
        if (luma > 0.18D && luma < 0.86D) {
            weight += 1.2D;
        }
        return weight;
    }

    private static double channelDelta(int source, int target) {
        return (source - target) / 255.0D;
    }

    private static double hueDistance(float first, float second) {
        double distance = Math.abs(first - second);
        return Math.min(distance, 1.0D - distance);
    }

    private static double luma(Color color) {
        return (color.getRed() * 0.2126D + color.getGreen() * 0.7152D + color.getBlue() * 0.0722D) / 255.0D;
    }

    private record PaletteEntry(byte index, Color color, float[] hsb, Oklab lab) {
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

    private enum HueFamily {
        WARM,
        YELLOW,
        GREEN,
        COOL,
        PINK,
        NEUTRAL
    }
}
