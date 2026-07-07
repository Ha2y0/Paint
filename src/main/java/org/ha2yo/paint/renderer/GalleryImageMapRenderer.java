package org.ha2yo.paint.renderer;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.image.BufferedImage;
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
