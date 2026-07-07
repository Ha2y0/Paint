package org.ha2yo.paint.renderer;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GalleryImageMapRenderer extends MapRenderer {
    private final byte[] indexes;
    private final int mapSize;
    private final Set<UUID> renderedPlayers = new HashSet<>();

    public GalleryImageMapRenderer(BufferedImage image, int tileX, int tileY, int mapSize, Color backgroundColor, boolean shaderRgb) {
        super(false);
        this.mapSize = mapSize;
        this.indexes = encodeTile(image, tileX, tileY, mapSize, backgroundColor, shaderRgb);
    }

    public GalleryImageMapRenderer(
            BufferedImage image,
            int tileX,
            int tileY,
            int mapSize,
            Color backgroundColor,
            boolean shaderRgb,
            BlockFace front,
            BlockFace right,
            BlockFace up
    ) {
        super(false);
        this.mapSize = mapSize;
        this.indexes = encodeProjectedTile(image, tileX, tileY, mapSize, backgroundColor, shaderRgb, front, right, up);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (!renderedPlayers.add(player.getUniqueId())) {
            return;
        }

        VanillaMapEncoder.paint(canvas, indexes, mapSize, mapSize);
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static BufferedImage burksToVanillaMapColors(BufferedImage image, Color backgroundColor, double strength) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double[][][] errors = new double[3][3][width + 4];
        double errorStrength = Math.max(0.0D, Math.min(1.0D, strength));

        for (int y = 0; y < height; y++) {
            int currentRow = y % 3;
            int nextRow = (y + 1) % 3;
            int nextNextRow = (y + 2) % 3;
            for (int x = 0; x < width; x++) {
                int errorIndex = x + 2;
                Color source = colorAt(image, x, y, backgroundColor);
                int red = clampColor(source.getRed() + errors[0][currentRow][errorIndex]);
                int green = clampColor(source.getGreen() + errors[1][currentRow][errorIndex]);
                int blue = clampColor(source.getBlue() + errors[2][currentRow][errorIndex]);

                Color adjusted = new Color(red, green, blue);
                Color mapped = MapPalette.getColor(VanillaMapEncoder.match(adjusted));
                output.setRGB(x, y, mapped.getRGB());

                double redError = (red - mapped.getRed()) * errorStrength;
                double greenError = (green - mapped.getGreen()) * errorStrength;
                double blueError = (blue - mapped.getBlue()) * errorStrength;
                addBurksError(errors, currentRow, errorIndex + 1, redError, greenError, blueError, 8.0D / 32.0D);
                addBurksError(errors, currentRow, errorIndex + 2, redError, greenError, blueError, 4.0D / 32.0D);

                addBurksError(errors, nextRow, errorIndex - 2, redError, greenError, blueError, 2.0D / 32.0D);
                addBurksError(errors, nextRow, errorIndex - 1, redError, greenError, blueError, 4.0D / 32.0D);
                addBurksError(errors, nextRow, errorIndex, redError, greenError, blueError, 8.0D / 32.0D);
                addBurksError(errors, nextRow, errorIndex + 1, redError, greenError, blueError, 4.0D / 32.0D);
                addBurksError(errors, nextRow, errorIndex + 2, redError, greenError, blueError, 2.0D / 32.0D);

                addBurksError(errors, nextNextRow, errorIndex - 2, redError, greenError, blueError, 1.0D / 32.0D);
                addBurksError(errors, nextNextRow, errorIndex - 1, redError, greenError, blueError, 2.0D / 32.0D);
                addBurksError(errors, nextNextRow, errorIndex, redError, greenError, blueError, 4.0D / 32.0D);
                addBurksError(errors, nextNextRow, errorIndex + 1, redError, greenError, blueError, 2.0D / 32.0D);
                addBurksError(errors, nextNextRow, errorIndex + 2, redError, greenError, blueError, 1.0D / 32.0D);
            }

            for (int channel = 0; channel < errors.length; channel++) {
                Arrays.fill(errors[channel][currentRow], 0.0D);
            }
        }

        return output;
    }

    private static void addBurksError(
            double[][][] errors,
            int row,
            int index,
            double redError,
            double greenError,
            double blueError,
            double weight
    ) {
        if (index < 0 || index >= errors[0][row].length) {
            return;
        }
        errors[0][row][index] += redError * weight;
        errors[1][row][index] += greenError * weight;
        errors[2][row][index] += blueError * weight;
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static BufferedImage ditherToVanillaMapColors(BufferedImage image, Color backgroundColor, double strength) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double[][] currentErrors = new double[3][width + 2];
        double[][] nextErrors = new double[3][width + 2];
        double baseStrength = Math.max(0.0D, Math.min(1.0D, strength));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int errorIndex = x + 1;
                Color source = colorAt(image, x, y, backgroundColor);
                int red = clampColor(source.getRed() + currentErrors[0][errorIndex]);
                int green = clampColor(source.getGreen() + currentErrors[1][errorIndex]);
                int blue = clampColor(source.getBlue() + currentErrors[2][errorIndex]);

                Color adjusted = new Color(red, green, blue);
                Color mapped = MapPalette.getColor(VanillaMapEncoder.match(adjusted));
                output.setRGB(x, y, mapped.getRGB());

                double redError = (red - mapped.getRed()) * baseStrength;
                double greenError = (green - mapped.getGreen()) * baseStrength;
                double blueError = (blue - mapped.getBlue()) * baseStrength;
                diffuse(currentErrors, nextErrors, errorIndex, redError, greenError, blueError);
            }

            for (int channel = 0; channel < currentErrors.length; channel++) {
                double[] swap = currentErrors[channel];
                currentErrors[channel] = nextErrors[channel];
                nextErrors[channel] = swap;
                Arrays.fill(nextErrors[channel], 0.0D);
            }
        }

        return output;
    }

    private static void diffuse(
            double[][] currentErrors,
            double[][] nextErrors,
            int errorIndex,
            double redError,
            double greenError,
            double blueError
    ) {
        addError(currentErrors, 0, errorIndex + 1, redError, 7.0D / 16.0D);
        addError(currentErrors, 1, errorIndex + 1, greenError, 7.0D / 16.0D);
        addError(currentErrors, 2, errorIndex + 1, blueError, 7.0D / 16.0D);

        addError(nextErrors, 0, errorIndex - 1, redError, 3.0D / 16.0D);
        addError(nextErrors, 1, errorIndex - 1, greenError, 3.0D / 16.0D);
        addError(nextErrors, 2, errorIndex - 1, blueError, 3.0D / 16.0D);

        addError(nextErrors, 0, errorIndex, redError, 5.0D / 16.0D);
        addError(nextErrors, 1, errorIndex, greenError, 5.0D / 16.0D);
        addError(nextErrors, 2, errorIndex, blueError, 5.0D / 16.0D);

        addError(nextErrors, 0, errorIndex + 1, redError, 1.0D / 16.0D);
        addError(nextErrors, 1, errorIndex + 1, greenError, 1.0D / 16.0D);
        addError(nextErrors, 2, errorIndex + 1, blueError, 1.0D / 16.0D);
    }

    private static void addError(double[][] errors, int channel, int index, double error, double weight) {
        if (index >= 0 && index < errors[channel].length) {
            errors[channel][index] += error * weight;
        }
    }

    public static BufferedImage imageMapToVanillaMapColors(BufferedImage image, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color source = colorAt(image, x, y, backgroundColor);
                output.setRGB(x, y, VanillaMapEncoder.color(VanillaMapEncoder.familyMatch(source)).getRGB());
            }
        }

        return output;
    }

    public static BufferedImage oklabToVanillaMapColors(BufferedImage image, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color source = colorAt(image, x, y, backgroundColor);
                output.setRGB(x, y, VanillaMapEncoder.color(VanillaMapEncoder.oklabMatch(source)).getRGB());
            }
        }

        return output;
    }

    public static BufferedImage imageMapDitherToVanillaMapColors(BufferedImage image, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double[][] currentErrors = new double[3][width + 2];
        double[][] nextErrors = new double[3][width + 2];

        for (int y = 0; y < height; y++) {
            boolean leftToRight = (y & 1) == 0;
            int start = leftToRight ? 0 : width - 1;
            int end = leftToRight ? width : -1;
            int step = leftToRight ? 1 : -1;

            for (int x = start; x != end; x += step) {
                int errorIndex = x + 1;
                Color source = colorAt(image, x, y, backgroundColor);
                int red = clampColor(source.getRed() + currentErrors[0][errorIndex]);
                int green = clampColor(source.getGreen() + currentErrors[1][errorIndex]);
                int blue = clampColor(source.getBlue() + currentErrors[2][errorIndex]);

                Color adjusted = new Color(red, green, blue);
                Color mapped = VanillaMapEncoder.color(VanillaMapEncoder.match(adjusted));
                output.setRGB(x, y, mapped.getRGB());

                double redError = red - mapped.getRed();
                double greenError = green - mapped.getGreen();
                double blueError = blue - mapped.getBlue();
                diffuseSerpentine(currentErrors, nextErrors, errorIndex, leftToRight, redError, greenError, blueError);
            }

            for (int channel = 0; channel < currentErrors.length; channel++) {
                double[] swap = currentErrors[channel];
                currentErrors[channel] = nextErrors[channel];
                nextErrors[channel] = swap;
                Arrays.fill(nextErrors[channel], 0.0D);
            }
        }

        return output;
    }

    private static void diffuseSerpentine(
            double[][] currentErrors,
            double[][] nextErrors,
            int errorIndex,
            boolean leftToRight,
            double redError,
            double greenError,
            double blueError
    ) {
        int forward = leftToRight ? 1 : -1;
        addError(currentErrors, 0, errorIndex + forward, redError, 7.0D / 16.0D);
        addError(currentErrors, 1, errorIndex + forward, greenError, 7.0D / 16.0D);
        addError(currentErrors, 2, errorIndex + forward, blueError, 7.0D / 16.0D);

        addError(nextErrors, 0, errorIndex - forward, redError, 3.0D / 16.0D);
        addError(nextErrors, 1, errorIndex - forward, greenError, 3.0D / 16.0D);
        addError(nextErrors, 2, errorIndex - forward, blueError, 3.0D / 16.0D);

        addError(nextErrors, 0, errorIndex, redError, 5.0D / 16.0D);
        addError(nextErrors, 1, errorIndex, greenError, 5.0D / 16.0D);
        addError(nextErrors, 2, errorIndex, blueError, 5.0D / 16.0D);

        addError(nextErrors, 0, errorIndex + forward, redError, 1.0D / 16.0D);
        addError(nextErrors, 1, errorIndex + forward, greenError, 1.0D / 16.0D);
        addError(nextErrors, 2, errorIndex + forward, blueError, 1.0D / 16.0D);
    }

    private static int clampColor(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static BufferedImage selectiveDitherToVanillaMapColors(BufferedImage image, Color backgroundColor, double strength) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double[][] currentErrors = new double[3][width + 2];
        double[][] nextErrors = new double[3][width + 2];
        double errorStrength = Math.max(0.0D, Math.min(1.0D, strength));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int errorIndex = x + 1;
                Color source = colorAt(image, x, y, backgroundColor);
                Color direct = MapPalette.getColor(VanillaMapEncoder.hueSafeMatch(source));
                if (!shouldDither(source, direct)) {
                    output.setRGB(x, y, direct.getRGB());
                    continue;
                }

                int red = clampColor(source.getRed() + currentErrors[0][errorIndex]);
                int green = clampColor(source.getGreen() + currentErrors[1][errorIndex]);
                int blue = clampColor(source.getBlue() + currentErrors[2][errorIndex]);

                Color adjusted = new Color(red, green, blue);
                Color mapped = MapPalette.getColor(VanillaMapEncoder.hueSafeMatch(adjusted));
                output.setRGB(x, y, mapped.getRGB());

                double redError = (red - mapped.getRed()) * errorStrength;
                double greenError = (green - mapped.getGreen()) * errorStrength;
                double blueError = (blue - mapped.getBlue()) * errorStrength;
                diffuse(currentErrors, nextErrors, errorIndex, redError, greenError, blueError);
            }

            for (int channel = 0; channel < currentErrors.length; channel++) {
                double[] swap = currentErrors[channel];
                currentErrors[channel] = nextErrors[channel];
                nextErrors[channel] = swap;
                Arrays.fill(nextErrors[channel], 0.0D);
            }
        }

        return output;
    }

    private static boolean shouldDither(Color source, Color direct) {
        float[] hsb = Color.RGBtoHSB(source.getRed(), source.getGreen(), source.getBlue(), null);
        double luma = luminance(source);
        if (luma < 0.16D || luma > 0.94D || hsb[1] < 0.12F) {
            return false;
        }
        if (maxChannelDelta(source, direct) <= 28) {
            return false;
        }
        return squaredColorDistance(source, direct) > 0.030D;
    }

    private static int maxChannelDelta(Color first, Color second) {
        return Math.max(
                Math.abs(first.getRed() - second.getRed()),
                Math.max(Math.abs(first.getGreen() - second.getGreen()), Math.abs(first.getBlue() - second.getBlue()))
        );
    }

    private static double squaredColorDistance(Color first, Color second) {
        double red = (first.getRed() - second.getRed()) / 255.0D;
        double green = (first.getGreen() - second.getGreen()) / 255.0D;
        double blue = (first.getBlue() - second.getBlue()) / 255.0D;
        return red * red + green * green + blue * blue;
    }

    private static double luminance(Color color) {
        return (color.getRed() * 0.2126D + color.getGreen() * 0.7152D + color.getBlue() * 0.0722D) / 255.0D;
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static BufferedImage posterizeToVanillaMapColors(BufferedImage image, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color source = colorAt(image, x, y, backgroundColor);
                Color sampled = linePixel(image, x, y, backgroundColor, source)
                        ? source
                        : smoothedColor(image, x, y, backgroundColor);
                Color posterized = linePixel(image, x, y, backgroundColor, source)
                        ? preserveDarkLine(sampled)
                        : posterizeColor(sampled);
                output.setRGB(x, y, MapPalette.getColor(VanillaMapEncoder.posterMatch(source, posterized)).getRGB());
            }
        }

        return output;
    }

    private static Color smoothedColor(BufferedImage image, int x, int y, Color backgroundColor) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int weightSum = 0;
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                int sampleX = x + offsetX;
                int sampleY = y + offsetY;
                if (sampleX < 0 || sampleX >= image.getWidth() || sampleY < 0 || sampleY >= image.getHeight()) {
                    continue;
                }
                int weight = offsetX == 0 && offsetY == 0 ? 4 : 1;
                Color sample = colorAt(image, sampleX, sampleY, backgroundColor);
                red += sample.getRed() * weight;
                green += sample.getGreen() * weight;
                blue += sample.getBlue() * weight;
                weightSum += weight;
            }
        }
        return new Color(red / weightSum, green / weightSum, blue / weightSum);
    }

    private static boolean linePixel(BufferedImage image, int x, int y, Color backgroundColor, Color source) {
        double luma = luminance(source);
        if (luma < 0.11D) {
            return true;
        }
        if (luma > 0.32D) {
            return false;
        }

        double brightestNeighbor = luma;
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if (offsetX == 0 && offsetY == 0) {
                    continue;
                }
                int sampleX = x + offsetX;
                int sampleY = y + offsetY;
                if (sampleX < 0 || sampleX >= image.getWidth() || sampleY < 0 || sampleY >= image.getHeight()) {
                    continue;
                }
                brightestNeighbor = Math.max(brightestNeighbor, luminance(colorAt(image, sampleX, sampleY, backgroundColor)));
            }
        }
        return brightestNeighbor - luma > 0.20D;
    }

    private static Color preserveDarkLine(Color color) {
        return new Color(
                clampColor(color.getRed() * 0.82D),
                clampColor(color.getGreen() * 0.82D),
                clampColor(color.getBlue() * 0.82D)
        );
    }

    private static Color posterizeColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float saturation = hsb[1] < 0.10F ? hsb[1] : Math.min(1.0F, hsb[1] * 1.08F);
        float brightness = posterizeBrightness(hsb[2]);
        int rgb = Color.HSBtoRGB(hsb[0], saturation, brightness);
        return new Color(rgb);
    }

    private static float posterizeBrightness(float brightness) {
        if (brightness < 0.18F) {
            return 0.12F;
        }
        if (brightness < 0.36F) {
            return 0.28F;
        }
        if (brightness < 0.58F) {
            return 0.48F;
        }
        if (brightness < 0.78F) {
            return 0.68F;
        }
        return 0.90F;
    }

    @SuppressWarnings({"deprecation", "removal"})
    public static BufferedImage naturalToVanillaMapColors(BufferedImage image, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color source = colorAt(image, x, y, backgroundColor);
                output.setRGB(x, y, MapPalette.getColor(VanillaMapEncoder.hueSafeMatch(source)).getRGB());
            }
        }

        return output;
    }

    private static byte[] encodeTile(BufferedImage image, int tileX, int tileY, int mapSize, Color backgroundColor, boolean shaderRgb) {
        Color[] colors = new Color[mapSize * mapSize];
        int offsetX = tileX * mapSize;
        int offsetY = tileY * mapSize;
        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                int sourceX = offsetX + x;
                int sourceY = offsetY + y;
                colors[y * mapSize + x] = sourceX >= 0 && sourceX < image.getWidth()
                        && sourceY >= 0 && sourceY < image.getHeight()
                        ? colorAt(image, sourceX, sourceY, backgroundColor)
                        : backgroundColor;
            }
        }
        return shaderRgb
                ? RgbMapEncoder.encode(colors, mapSize, mapSize)
                : VanillaMapEncoder.encode(colors, mapSize, mapSize);
    }

    private static byte[] encodeProjectedTile(
            BufferedImage image,
            int tileX,
            int tileY,
            int mapSize,
            Color backgroundColor,
            boolean shaderRgb,
            BlockFace front,
            BlockFace right,
            BlockFace up
    ) {
        Color[] colors = new Color[mapSize * mapSize];
        BlockFace mapRight = defaultMapRight(front);
        BlockFace mapUp = defaultMapUp(front);
        int offsetX = tileX * mapSize;
        int offsetUp = tileY * mapSize;
        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                int sourceX = offsetX + localCoordinate(right, mapRight, mapUp, x, y, mapSize);
                int sourceY = image.getHeight() - 1 - (offsetUp + localCoordinate(up, mapRight, mapUp, x, y, mapSize));
                colors[y * mapSize + x] = sourceX >= 0 && sourceX < image.getWidth()
                        && sourceY >= 0 && sourceY < image.getHeight()
                        ? colorAt(image, sourceX, sourceY, backgroundColor)
                        : backgroundColor;
            }
        }
        return shaderRgb
                ? RgbMapEncoder.encode(colors, mapSize, mapSize)
                : VanillaMapEncoder.encode(colors, mapSize, mapSize);
    }

    private static int localCoordinate(BlockFace axis, BlockFace mapRight, BlockFace mapUp, int x, int y, int mapSize) {
        if (axis == mapRight) {
            return x;
        }
        if (axis == mapRight.getOppositeFace()) {
            return mapSize - 1 - x;
        }
        if (axis == mapUp) {
            return mapSize - 1 - y;
        }
        if (axis == mapUp.getOppositeFace()) {
            return y;
        }
        return 0;
    }

    private static BlockFace defaultMapUp(BlockFace front) {
        return switch (front) {
            case UP -> BlockFace.NORTH;
            case DOWN -> BlockFace.SOUTH;
            default -> BlockFace.UP;
        };
    }

    private static BlockFace defaultMapRight(BlockFace front) {
        return switch (front) {
            case UP, DOWN -> BlockFace.EAST;
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    private static Color colorAt(BufferedImage image, int x, int y, Color backgroundColor) {
        int argb = image.getRGB(x, y);
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha >= 255) {
            return new Color(argb, false);
        }
        if (alpha <= 0) {
            return backgroundColor;
        }

        double ratio = alpha / 255.0D;
        int red = blend((argb >>> 16) & 0xFF, backgroundColor.getRed(), ratio);
        int green = blend((argb >>> 8) & 0xFF, backgroundColor.getGreen(), ratio);
        int blue = blend(argb & 0xFF, backgroundColor.getBlue(), ratio);
        return new Color(red, green, blue);
    }

    private static int blend(int foreground, int background, double ratio) {
        return (int) Math.round(foreground * ratio + background * (1.0D - ratio));
    }
}
