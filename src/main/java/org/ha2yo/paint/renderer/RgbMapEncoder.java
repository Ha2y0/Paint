package org.ha2yo.paint.renderer;

import java.awt.Color;

final class RgbMapEncoder {
    private static final int RGB_BLOCK_SIZE = 2;
    private static final int MAP_COLOR_OFFSET = 4;

    private RgbMapEncoder() {
    }

    static byte[] encode(Color[] colors, int width, int height) {
        if (colors.length != width * height) {
            throw new IllegalArgumentException("Map colors must match the target dimensions");
        }

        byte[] indexes = new byte[colors.length];
        for (int y = 0; y < height; y += RGB_BLOCK_SIZE) {
            for (int x = 0; x < width; x += RGB_BLOCK_SIZE) {
                writePixel(indexes, width, height, x, y, colors[y * width + x]);
            }
        }
        return indexes;
    }

    static void writePixel(byte[] indexes, int width, int height, int x, int y, Color color) {
        int blockX = x - Math.floorMod(x, RGB_BLOCK_SIZE);
        int blockY = y - Math.floorMod(y, RGB_BLOCK_SIZE);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        int meta = ((blue >>> 7) & 1)
                | (((green >>> 7) & 1) << 1)
                | (((red >>> 7) & 1) << 2);

        set(indexes, width, height, blockX, blockY, mapIndex(blue & 0x7F));
        set(indexes, width, height, blockX + 1, blockY, mapIndex(green & 0x7F));
        set(indexes, width, height, blockX, blockY + 1, mapIndex(red & 0x7F));
        set(indexes, width, height, blockX + 1, blockY + 1, mapIndex(meta));
    }

    private static void set(byte[] indexes, int width, int height, int x, int y, byte value) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        indexes[y * width + x] = value;
    }

    private static byte mapIndex(int shaderIndex) {
        return (byte) (shaderIndex + MAP_COLOR_OFFSET);
    }
}
