package org.ha2yo.paint.renderer;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.tool.PaletteMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class PaletteMapRenderer extends MapRenderer {
    public static final int MAP_SIZE = 128;
    public static final int PALETTE_BLOCK_WIDTH = 4;
    public static final int PALETTE_BLOCK_HEIGHT = 2;
    public static final int PALETTE_PIXEL_WIDTH = PALETTE_BLOCK_WIDTH * MAP_SIZE;
    public static final int PALETTE_PIXEL_HEIGHT = PALETTE_BLOCK_HEIGHT * MAP_SIZE;
    public static final int PALETTE_BRUSH_RESET_ACTION = Integer.MIN_VALUE;

    private static final int PALETTE_GRID_COLUMNS = 32;
    private static final int PALETTE_GRID_ROWS = 8;
    private static final int PALETTE_GRID_X = 16;
    private static final int PALETTE_GRID_Y = 38;
    private static final int PALETTE_SWATCH_WIDTH = 14;
    private static final int PALETTE_SWATCH_HEIGHT = 25;
    private static final int PALETTE_SWATCH_GAP = 1;
    private static final int PALETTE_GRADIENT_X = 10;
    private static final int PALETTE_GRADIENT_Y = 38;
    private static final int PALETTE_GRADIENT_WIDTH = 332;
    private static final int PALETTE_GRADIENT_HEIGHT = 208;
    private static final int PALETTE_GRADIENT_COLUMNS = 12;
    private static final int PALETTE_GRADIENT_ROWS = 6;
    private static final int PALETTE_PREVIEW_X = 356;
    private static final int PALETTE_PREVIEW_Y = 38;
    private static final int PALETTE_PREVIEW_WIDTH = 54;
    private static final int PALETTE_PREVIEW_HEIGHT = 208;
    private static final int PALETTE_BRIGHTNESS_X = 438;
    private static final int PALETTE_BRIGHTNESS_Y = 38;
    private static final int PALETTE_BRIGHTNESS_WIDTH = 18;
    private static final int PALETTE_BRIGHTNESS_HEIGHT = 208;
    private static final int PALETTE_BRUSH_X = 10;
    private static final int PALETTE_BRUSH_Y = 7;
    private static final int PALETTE_BRUSH_BUTTON_WIDTH = 32;
    private static final int PALETTE_BRUSH_BUTTON_HEIGHT = 22;
    private static final int PALETTE_BRUSH_GAP = 6;
    private static final int PALETTE_BRUSH_VALUE_X = 90;
    private static final int PALETTE_BRUSH_VALUE_WIDTH = 110;
    private static final int PALETTE_MODE_X = 350;
    private static final int PALETTE_MODE_Y = 7;
    private static final int PALETTE_MODE_WIDTH = 74;
    private static final int PALETTE_MODE_HEIGHT = 22;
    private static final int PALETTE_MODE_GAP = 8;
    private static final int PALETTE_COLOR_PICKER_X = 16;
    private static final int PALETTE_COLOR_PICKER_Y = 38;
    private static final int PALETTE_COLOR_PICKER_WIDTH = 400;
    private static final int PALETTE_COLOR_PICKER_HEIGHT = 208;
    private static final int PALETTE_SELECTED_BAR_X = 426;
    private static final int PALETTE_SELECTED_BAR_Y = 38;
    private static final int PALETTE_SELECTED_BAR_WIDTH = 20;
    private static final int PALETTE_SELECTED_BAR_HEIGHT = 208;
    private static final int PALETTE_HUE_BAR_X = 456;
    private static final int PALETTE_HUE_BAR_Y = 38;
    private static final int PALETTE_HUE_BAR_WIDTH = 48;
    private static final int PALETTE_HUE_BAR_HEIGHT = 208;
    private static final int MAP_COLOR_HUE_GROUPS = 18;
    private static final Color[] REPRESENTATIVE_COLORS = {
            new Color(245, 238, 224),
            new Color(184, 145, 112),
            new Color(118, 74, 44),
            new Color(218, 38, 38),
            new Color(236, 92, 36),
            new Color(244, 166, 93),
            new Color(241, 207, 54),
            new Color(151, 197, 38),
            new Color(48, 164, 65),
            new Color(42, 142, 108),
            new Color(55, 174, 176),
            new Color(65, 151, 211),
            new Color(58, 86, 197),
            new Color(91, 62, 172),
            new Color(151, 59, 177),
            new Color(207, 65, 143),
            new Color(112, 62, 42),
            new Color(42, 42, 42)
    };
    private static final Color PALETTE_BACKGROUND = new Color(64, 67, 62);
    private static final Color PALETTE_BORDER = new Color(35, 36, 35);
    private static final Color PALETTE_MODE_ACTIVE = new Color(250, 128, 36);
    private static final Color PALETTE_CURSOR = Color.WHITE;
    private static final MapColorGroup[] MAP_COLOR_GROUPS = createMapColorGroups();
    private static final Map<Integer, Color[]> TONE_COLOR_CACHE = new HashMap<>();
    private static final Color[][] GRADIENT_SWATCHES = createGradientSwatches();
    private static final Color[][] RGB_PALETTE_GRID = createRgbPaletteGrid();
    private static final Color[][] VANILLA_PALETTE_GRID = createVanillaPaletteGrid();

    private final PaletteBoard board;
    private final int tileX;
    private final int tileY;
    private final BooleanSupplier shaderRgbSupplier;
    private final Map<UUID, Integer> renderedVersions = new HashMap<>();
    private final Map<UUID, Boolean> renderedShaderRgbStates = new HashMap<>();

    public PaletteMapRenderer(
            PaletteBoard board,
            int tileX,
            int tileY,
            BooleanSupplier shaderRgbSupplier
    ) {
        super(false);
        this.board = board;
        this.tileX = tileX;
        this.tileY = tileY;
        this.shaderRgbSupplier = shaderRgbSupplier;
    }

    @Override
    public void render(MapView map, MapCanvas mapCanvas, Player player) {
        int renderedVersion = renderedVersions.getOrDefault(player.getUniqueId(), -1);
        boolean shaderRgb = shaderRgbSupplier.getAsBoolean();
        boolean renderedShaderRgb = renderedShaderRgbStates.getOrDefault(player.getUniqueId(), false);
        if (renderedVersion == board.version() && renderedShaderRgb == shaderRgb) {
            return;
        }

        int panelOffsetX = tileX * MAP_SIZE;
        int panelOffsetY = tileY * MAP_SIZE;
        if (shaderRgb) {
            Color[] colors = new Color[MAP_SIZE * MAP_SIZE];
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    int panelX = panelOffsetX + x;
                    int panelY = panelOffsetY + y;
                    colors[y * MAP_SIZE + x] = palettePixelColor(board, panelX, panelY, true);
                }
            }
            VanillaMapEncoder.paint(mapCanvas, RgbMapEncoder.encode(colors, MAP_SIZE, MAP_SIZE), MAP_SIZE, MAP_SIZE);
        } else {
            byte[] indexes = new byte[MAP_SIZE * MAP_SIZE];
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    int panelX = panelOffsetX + x;
                    int panelY = panelOffsetY + y;
                    indexes[y * MAP_SIZE + x] = palettePixelColorIndex(board, panelX, panelY);
                }
            }
            VanillaMapEncoder.paint(mapCanvas, indexes, MAP_SIZE, MAP_SIZE);
        }

        renderedVersions.put(player.getUniqueId(), board.version());
        renderedShaderRgbStates.put(player.getUniqueId(), shaderRgb);
    }

    public static Integer brushActionAt(int x, int y) {
        if (y < PALETTE_BRUSH_Y || y >= PALETTE_BRUSH_Y + PALETTE_BRUSH_BUTTON_HEIGHT) {
            return null;
        }

        if (isInRect(x, y, PALETTE_BRUSH_VALUE_X, PALETTE_BRUSH_Y, PALETTE_BRUSH_VALUE_WIDTH, PALETTE_BRUSH_BUTTON_HEIGHT)) {
            return PALETTE_BRUSH_RESET_ACTION;
        }

        int[] deltas = {-3, -1, 1, 3};
        for (int index = 0; index < deltas.length; index++) {
            int buttonX = paletteBrushButtonX(index);
            if (x >= buttonX && x < buttonX + PALETTE_BRUSH_BUTTON_WIDTH) {
                return deltas[index];
            }
        }
        return null;
    }

    public static PaletteMode modeAt(int x, int y) {
        if (isInRect(x, y, paletteModeButtonX(PaletteMode.GRID), PALETTE_MODE_Y, PALETTE_MODE_WIDTH, PALETTE_MODE_HEIGHT)) {
            return PaletteMode.GRID;
        }
        if (isInRect(x, y, paletteModeButtonX(PaletteMode.GRADIENT), PALETTE_MODE_Y, PALETTE_MODE_WIDTH, PALETTE_MODE_HEIGHT)) {
            return PaletteMode.GRADIENT;
        }
        return null;
    }

    public static Color colorAt(PaletteBoard board, int x, int y, boolean shaderRgb) {
        if (board.mode() == PaletteMode.GRID) {
            return gridPaletteColorAt(x, y, shaderRgb);
        }

        if (isInRect(x, y, PALETTE_COLOR_PICKER_X, PALETTE_COLOR_PICKER_Y, PALETTE_COLOR_PICKER_WIDTH, PALETTE_COLOR_PICKER_HEIGHT)) {
            int localX = colorPickerLocalX(x);
            int localY = colorPickerLocalY(y);
            float saturation = localX / (float) (PALETTE_COLOR_PICKER_WIDTH - 1);
            float brightness = 1.0F - localY / (float) (PALETTE_COLOR_PICKER_HEIGHT - 1);
            board.setGradientCursor(localX, localY);
            board.setGradientSelection(board.hue(), saturation, brightness);
            return shaderRgb ? board.selectedColor() : snapToMapColor(board.selectedColor());
        }

        if (isInRect(x, y, PALETTE_HUE_BAR_X, PALETTE_HUE_BAR_Y, PALETTE_HUE_BAR_WIDTH, PALETTE_HUE_BAR_HEIGHT)) {
            int localY = hueBarLocalY(y);
            float hue = localY / (float) (PALETTE_HUE_BAR_HEIGHT - 1);
            board.setGradientSelection(hue, board.saturation(), board.brightness());
            int pickerX = Math.round(board.saturation() * (PALETTE_COLOR_PICKER_WIDTH - 1));
            int pickerY = Math.round((1.0F - board.brightness()) * (PALETTE_COLOR_PICKER_HEIGHT - 1));
            board.setGradientCursor(pickerX, pickerY);
            return shaderRgb ? board.selectedColor() : snapToMapColor(board.selectedColor());
        }

        return null;
    }

    public static PixelPoint colorCursorPoint(PaletteBoard board) {
        if (board.mode() == PaletteMode.GRID) {
            return null;
        }

        int localX = Math.max(0, Math.min(PALETTE_COLOR_PICKER_WIDTH - 1, board.gradientCursorX()));
        int localY = Math.max(0, Math.min(PALETTE_COLOR_PICKER_HEIGHT - 1, board.gradientCursorY()));
        int x = PALETTE_COLOR_PICKER_X + localX;
        int y = PALETTE_COLOR_PICKER_Y + localY;
        return new PixelPoint(x, y);
    }

    public static PixelPoint brightnessCursorPoint(PaletteBoard board) {
        if (board.mode() == PaletteMode.GRID) {
            return null;
        }

        int x = PALETTE_HUE_BAR_X + PALETTE_HUE_BAR_WIDTH / 2;
        int localY = Math.max(0, Math.min(PALETTE_HUE_BAR_HEIGHT - 1, Math.round(board.hue() * (PALETTE_HUE_BAR_HEIGHT - 1))));
        int y = PALETTE_HUE_BAR_Y + localY;
        return new PixelPoint(x, y);
    }

    public static void updateGradientCursorForColor(PaletteBoard board, Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        int localX = Math.round(hsb[1] * (PALETTE_COLOR_PICKER_WIDTH - 1));
        int localY = Math.round((1.0F - hsb[2]) * (PALETTE_COLOR_PICKER_HEIGHT - 1));
        board.setGradientCursor(localX, localY);
    }

    private static Color gridPaletteColorAt(int x, int y, boolean shaderRgb) {
        int column = paletteGridColumn(x);
        int row = paletteGridRow(y);
        if (column < 0 || row < 0) {
            return null;
        }
        return paletteGrid(shaderRgb)[row][column];
    }

    private static Color gradientPaletteColorAt(PaletteBoard board, int x, int y) {
        if (isInRect(x, y, PALETTE_GRADIENT_X, PALETTE_GRADIENT_Y, PALETTE_GRADIENT_WIDTH, PALETTE_GRADIENT_HEIGHT)) {
            int column = gradientColumnAt(x);
            int row = gradientRowAt(y);
            Color color = GRADIENT_SWATCHES[row][column];
            board.setGradientAreaSelection(column, row, color);
            return board.selectedColor();
        }

        if (isInRect(x, y, PALETTE_BRIGHTNESS_X, PALETTE_BRIGHTNESS_Y, PALETTE_BRIGHTNESS_WIDTH, PALETTE_BRIGHTNESS_HEIGHT)) {
            float brightness = 1.0F - (y - PALETTE_BRIGHTNESS_Y) / (float) (PALETTE_BRIGHTNESS_HEIGHT - 1);
            board.setGradientSelection(board.hue(), board.saturation(), brightness);
            return board.selectedColor();
        }

        return null;
    }

    private static Color palettePixelColor(PaletteBoard board, int x, int y, boolean shaderRgb) {
        Color brushPixel = paletteBrushPixelColor(board, x, y);
        if (brushPixel != null) {
            return brushPixel;
        }

        Color modePixel = paletteModePixelColor(board, x, y);
        if (modePixel != null) {
            return modePixel;
        }

        return board.mode() == PaletteMode.GRID
                ? gridPalettePixelColor(board, x, y, shaderRgb)
                : verticalPalettePixelColor(board, x, y);
    }

    private static byte palettePixelColorIndex(PaletteBoard board, int x, int y) {
        Color color = palettePixelColor(board, x, y, false);
        return VanillaMapEncoder.match(color);
    }

    private static Color gridPalettePixelColor(PaletteBoard board, int x, int y, boolean shaderRgb) {
        int column = paletteGridColumn(x);
        int row = paletteGridRow(y);
        if (column >= 0 && row >= 0) {
            Color color = paletteGrid(shaderRgb)[row][column];
            if (color == null) {
                return PALETTE_BACKGROUND;
            }
            if (isSelectedSwatchFrame(board, paletteGrid(shaderRgb), column, row, x, y)) {
                return PALETTE_CURSOR;
            }
            return color;
        }

        if (isPaletteGridFrame(x, y)) {
            return PALETTE_BORDER;
        }

        return PALETTE_BACKGROUND;
    }

    private static int paletteBrushButtonX(int index) {
        if (index < 2) {
            return PALETTE_BRUSH_X + index * (PALETTE_BRUSH_BUTTON_WIDTH + PALETTE_BRUSH_GAP);
        }
        return PALETTE_BRUSH_VALUE_X + PALETTE_BRUSH_VALUE_WIDTH + PALETTE_BRUSH_GAP
                + (index - 2) * (PALETTE_BRUSH_BUTTON_WIDTH + PALETTE_BRUSH_GAP);
    }

    private static Color paletteBrushPixelColor(PaletteBoard board, int x, int y) {
        int[] deltas = {-3, -1, 1, 3};
        Color[] colors = {
                new Color(129, 40, 35),
                new Color(171, 64, 45),
                new Color(65, 142, 54),
                new Color(49, 112, 52)
        };

        int stripX = PALETTE_BRUSH_X - 5;
        int stripY = PALETTE_BRUSH_Y - 4;
        int stripWidth = paletteBrushButtonX(deltas.length - 1) + PALETTE_BRUSH_BUTTON_WIDTH - stripX + 5;
        int stripHeight = PALETTE_BRUSH_BUTTON_HEIGHT + 8;
        if (isFrame(x, y, stripX, stripY, stripWidth, stripHeight)) {
            return PALETTE_BORDER;
        }
        if (isInRect(x, y, stripX, stripY, stripWidth, stripHeight)) {
            Color buttonPixel = paletteBrushButtonPixelColor(x, y, deltas, colors);
            if (buttonPixel != null) {
                return buttonPixel;
            }

            Color valuePixel = paletteBrushValuePixelColor(board, x, y);
            if (valuePixel != null) {
                return valuePixel;
            }

            return new Color(48, 50, 47);
        }

        return null;
    }

    private static Color paletteBrushButtonPixelColor(int x, int y, int[] deltas, Color[] colors) {
        for (int index = 0; index < deltas.length; index++) {
            int buttonX = paletteBrushButtonX(index);
            if (x >= buttonX - 2 && x < buttonX + PALETTE_BRUSH_BUTTON_WIDTH + 2
                    && y >= PALETTE_BRUSH_Y - 2 && y < PALETTE_BRUSH_Y + PALETTE_BRUSH_BUTTON_HEIGHT + 2) {
                if (!isInRect(x, y, buttonX, PALETTE_BRUSH_Y, PALETTE_BRUSH_BUTTON_WIDTH, PALETTE_BRUSH_BUTTON_HEIGHT)) {
                    return PALETTE_BORDER;
                }

                String label = deltas[index] > 0 ? "+" + deltas[index] : Integer.toString(deltas[index]);
                if (isCenteredTinyTextPixel(label, x, y, buttonX, PALETTE_BRUSH_Y, PALETTE_BRUSH_BUTTON_WIDTH, PALETTE_BRUSH_BUTTON_HEIGHT, 2)) {
                    return new Color(246, 242, 226);
                }
                return colors[index];
            }
        }

        return null;
    }

    private static Color paletteBrushValuePixelColor(PaletteBoard board, int x, int y) {
        if (x >= PALETTE_BRUSH_VALUE_X - 2 && x < PALETTE_BRUSH_VALUE_X + PALETTE_BRUSH_VALUE_WIDTH + 2
                && y >= PALETTE_BRUSH_Y - 2 && y < PALETTE_BRUSH_Y + PALETTE_BRUSH_BUTTON_HEIGHT + 2) {
            if (!isInRect(x, y, PALETTE_BRUSH_VALUE_X, PALETTE_BRUSH_Y, PALETTE_BRUSH_VALUE_WIDTH, PALETTE_BRUSH_BUTTON_HEIGHT)) {
                return PALETTE_BORDER;
            }

            String label = board.brushRadius() + "PX";
            if (isCenteredTinyTextPixel(label, x, y, PALETTE_BRUSH_VALUE_X, PALETTE_BRUSH_Y, PALETTE_BRUSH_VALUE_WIDTH, PALETTE_BRUSH_BUTTON_HEIGHT, 3)) {
                return new Color(32, 32, 29);
            }
            return new Color(242, 236, 211);
        }

        return null;
    }

    private static Color verticalPalettePixelColor(PaletteBoard board, int x, int y) {
        if (isFrame(x, y, PALETTE_COLOR_PICKER_X, PALETTE_COLOR_PICKER_Y, PALETTE_COLOR_PICKER_WIDTH, PALETTE_COLOR_PICKER_HEIGHT)
                || isFrame(x, y, PALETTE_HUE_BAR_X, PALETTE_HUE_BAR_Y, PALETTE_HUE_BAR_WIDTH, PALETTE_HUE_BAR_HEIGHT)
                || isFrame(x, y, PALETTE_SELECTED_BAR_X, PALETTE_SELECTED_BAR_Y, PALETTE_SELECTED_BAR_WIDTH, PALETTE_SELECTED_BAR_HEIGHT)) {
            return PALETTE_BORDER;
        }

        if (isInRect(x, y, PALETTE_COLOR_PICKER_X, PALETTE_COLOR_PICKER_Y, PALETTE_COLOR_PICKER_WIDTH, PALETTE_COLOR_PICKER_HEIGHT)) {
            if (isColorBarCursorPixel(board, x, y)) {
                return cursorColorFor(board.selectedColor());
            }
            return colorPickerColorAt(board, x, y);
        }

        if (isInRect(x, y, PALETTE_HUE_BAR_X, PALETTE_HUE_BAR_Y, PALETTE_HUE_BAR_WIDTH, PALETTE_HUE_BAR_HEIGHT)) {
            if (isBrightnessCursorPixel(board, x, y)) {
                return cursorColorFor(board.selectedColor());
            }
            return hueBarColorAt(y);
        }

        if (isInRect(x, y, PALETTE_SELECTED_BAR_X, PALETTE_SELECTED_BAR_Y, PALETTE_SELECTED_BAR_WIDTH, PALETTE_SELECTED_BAR_HEIGHT)) {
            return board.selectedColor();
        }

        return PALETTE_BACKGROUND;
    }

    private static boolean isCenteredTinyTextPixel(String text, int x, int y, int areaX, int areaY, int areaWidth, int areaHeight, int scale) {
        int textWidth = tinyTextWidth(text, scale);
        int textHeight = 5 * scale;
        int textX = areaX + (areaWidth - textWidth) / 2;
        int textY = areaY + (areaHeight - textHeight) / 2;
        return isTinyTextPixel(text, x - textX, y - textY, scale);
    }

    private static int tinyTextWidth(String text, int scale) {
        if (text.isEmpty()) {
            return 0;
        }
        return (text.length() * 3 + (text.length() - 1)) * scale;
    }

    private static boolean isTinyTextPixel(String text, int localX, int localY, int scale) {
        if (localX < 0 || localY < 0 || localY >= 5 * scale) {
            return false;
        }

        int cellWidth = 4 * scale;
        int charIndex = localX / cellWidth;
        if (charIndex < 0 || charIndex >= text.length()) {
            return false;
        }

        int charX = localX - charIndex * cellWidth;
        if (charX >= 3 * scale) {
            return false;
        }

        return tinyGlyphPixel(text.charAt(charIndex), charX / scale, localY / scale);
    }

    private static boolean tinyGlyphPixel(char character, int x, int y) {
        String[] glyph = switch (character) {
            case '0' -> new String[]{"111", "101", "101", "101", "111"};
            case '1' -> new String[]{"010", "110", "010", "010", "111"};
            case '2' -> new String[]{"111", "001", "111", "100", "111"};
            case '3' -> new String[]{"111", "001", "111", "001", "111"};
            case '4' -> new String[]{"101", "101", "111", "001", "001"};
            case '5' -> new String[]{"111", "100", "111", "001", "111"};
            case '6' -> new String[]{"111", "100", "111", "101", "111"};
            case '7' -> new String[]{"111", "001", "010", "010", "010"};
            case '8' -> new String[]{"111", "101", "111", "101", "111"};
            case '9' -> new String[]{"111", "101", "111", "001", "111"};
            case '+' -> new String[]{"000", "010", "111", "010", "000"};
            case '-' -> new String[]{"000", "000", "111", "000", "000"};
            case 'A' -> new String[]{"010", "101", "111", "101", "101"};
            case 'B' -> new String[]{"110", "101", "110", "101", "110"};
            case 'D' -> new String[]{"110", "101", "101", "101", "110"};
            case 'E' -> new String[]{"111", "100", "110", "100", "111"};
            case 'G' -> new String[]{"111", "100", "101", "101", "111"};
            case 'H' -> new String[]{"101", "101", "111", "101", "101"};
            case 'M' -> new String[]{"101", "111", "111", "101", "101"};
            case 'P' -> new String[]{"110", "101", "110", "100", "100"};
            case 'R' -> new String[]{"110", "101", "110", "101", "101"};
            case 'S' -> new String[]{"111", "100", "111", "001", "111"};
            case 'V' -> new String[]{"101", "101", "101", "101", "010"};
            case 'X' -> new String[]{"101", "101", "010", "101", "101"};
            default -> new String[]{"000", "000", "000", "000", "000"};
        };
        return glyph[y].charAt(x) == '1';
    }

    private static Color gradientPalettePixelColor(PaletteBoard board, int x, int y) {
        if (isFrame(x, y, PALETTE_GRADIENT_X, PALETTE_GRADIENT_Y, PALETTE_GRADIENT_WIDTH, PALETTE_GRADIENT_HEIGHT)
                || isFrame(x, y, PALETTE_PREVIEW_X, PALETTE_PREVIEW_Y, PALETTE_PREVIEW_WIDTH, PALETTE_PREVIEW_HEIGHT)
                || isFrame(x, y, PALETTE_BRIGHTNESS_X, PALETTE_BRIGHTNESS_Y, PALETTE_BRIGHTNESS_WIDTH, PALETTE_BRIGHTNESS_HEIGHT)) {
            return PALETTE_BORDER;
        }

        if (isInRect(x, y, PALETTE_GRADIENT_X, PALETTE_GRADIENT_Y, PALETTE_GRADIENT_WIDTH, PALETTE_GRADIENT_HEIGHT)) {
            if (isGradientCursorPixel(board, x, y)) {
                return PALETTE_CURSOR;
            }
            int column = gradientColumnAt(x);
            int row = gradientRowAt(y);
            if (isGradientCellDivider(x, y, column, row)) {
                return PALETTE_BORDER;
            }
            return GRADIENT_SWATCHES[row][column];
        }

        if (isInRect(x, y, PALETTE_PREVIEW_X, PALETTE_PREVIEW_Y, PALETTE_PREVIEW_WIDTH, PALETTE_PREVIEW_HEIGHT)) {
            return board.selectedColor();
        }

        if (isInRect(x, y, PALETTE_BRIGHTNESS_X, PALETTE_BRIGHTNESS_Y, PALETTE_BRIGHTNESS_WIDTH, PALETTE_BRIGHTNESS_HEIGHT)) {
            if (isBrightnessCursorPixel(board, x, y)) {
                return PALETTE_CURSOR;
            }
            float brightness = 1.0F - (y - PALETTE_BRIGHTNESS_Y) / (float) (PALETTE_BRIGHTNESS_HEIGHT - 1);
            return Color.getHSBColor(clamp01(board.hue()), clamp01(board.saturation()), clamp01(brightness));
        }

        return PALETTE_BACKGROUND;
    }

    private static Color fullGradientColor(float hue, float brightness) {
        return Color.getHSBColor(clamp01(hue), 1.0F, clamp01(brightness));
    }

    private static int colorPickerLocalX(int x) {
        return Math.max(0, Math.min(PALETTE_COLOR_PICKER_WIDTH - 1, x - PALETTE_COLOR_PICKER_X));
    }

    private static int colorPickerLocalY(int y) {
        return Math.max(0, Math.min(PALETTE_COLOR_PICKER_HEIGHT - 1, y - PALETTE_COLOR_PICKER_Y));
    }

    private static int hueBarLocalY(int y) {
        return Math.max(0, Math.min(PALETTE_HUE_BAR_HEIGHT - 1, y - PALETTE_HUE_BAR_Y));
    }

    private static Color colorPickerColorAt(PaletteBoard board, int x, int y) {
        float saturation = colorPickerLocalX(x) / (float) (PALETTE_COLOR_PICKER_WIDTH - 1);
        float brightness = 1.0F - colorPickerLocalY(y) / (float) (PALETTE_COLOR_PICKER_HEIGHT - 1);
        return Color.getHSBColor(clamp01(board.hue()), clamp01(saturation), clamp01(brightness));
    }

    private static Color hueBarColorAt(int y) {
        float hue = hueBarLocalY(y) / (float) (PALETTE_HUE_BAR_HEIGHT - 1);
        return Color.getHSBColor(clamp01(hue), 1.0F, 1.0F);
    }

    private static int nearestGroupIndex(Color color) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < MAP_COLOR_GROUPS.length; index++) {
            int distance = colorDistance(color, MAP_COLOR_GROUPS[index].representative());
            if (distance >= bestDistance) {
                continue;
            }

            bestDistance = distance;
            bestIndex = index;
        }
        return bestIndex;
    }

    private static int nearestValueIndex(MapColorGroup group, Color color) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < group.colors().length; index++) {
            int distance = colorDistance(color, group.colors()[index]);
            if (distance >= bestDistance) {
                continue;
            }

            bestDistance = distance;
            bestIndex = index;
        }
        return bestIndex;
    }

    private static int colorDistance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return red * red + green * green + blue * blue;
    }

    private static MapColorGroup[] createMapColorGroups() {
        MapColorGroup[] groups = new MapColorGroup[REPRESENTATIVE_COLORS.length];
        for (int index = 0; index < REPRESENTATIVE_COLORS.length; index++) {
            Color color = REPRESENTATIVE_COLORS[index];
            groups[index] = new MapColorGroup(color, new Color[]{color});
        }
        return groups;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static Color[] minecraftMapColors() {
        Set<Integer> seenColors = new LinkedHashSet<>();
        List<Color> colors = new ArrayList<>();
        for (int rawIndex = 4; rawIndex < 256; rawIndex++) {
            Color color;
            try {
                color = MapPalette.getColor((byte) rawIndex);
            } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException ignored) {
                break;
            }
            if (color.getAlpha() < 128) {
                continue;
            }

            int rgb = color.getRGB() & 0xFFFFFF;
            if (seenColors.add(rgb)) {
                colors.add(new Color(rgb));
            }
        }
        return colors.toArray(Color[]::new);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static Color snapToMapColor(Color color) {
        Color mapColor = MapPalette.getColor(VanillaMapEncoder.match(color));
        return new Color(mapColor.getRGB() & 0xFFFFFF);
    }

    private static Color[][] createMapColorPaletteGrid() {
        Color[] colors = minecraftMapColors();
        Color[][] grid = new Color[PALETTE_GRID_ROWS][PALETTE_GRID_COLUMNS];

        List<Color> neutralColors = new ArrayList<>();
        List<List<Color>> hueColumns = new ArrayList<>();
        for (int column = 1; column < PALETTE_GRID_COLUMNS; column++) {
            hueColumns.add(new ArrayList<>());
        }

        for (Color color : colors) {
            if (saturation(color) < 0.16D) {
                neutralColors.add(color);
                continue;
            }

            int column = Math.min(hueColumns.size() - 1, (int) (hue(color) * hueColumns.size()));
            hueColumns.get(column).add(color);
        }

        List<Color> overflow = new ArrayList<>();
        neutralColors.sort(mapPaletteToneComparator());
        fillPaletteColumn(grid, 0, neutralColors, overflow);
        for (int index = 0; index < hueColumns.size(); index++) {
            List<Color> columnColors = hueColumns.get(index);
            columnColors.sort(mapPaletteToneComparator());
            fillPaletteColumn(grid, index + 1, columnColors, overflow);
        }
        fillPaletteEmptyCells(grid, overflow);
        return grid;
    }

    private static Comparator<Color> mapPaletteToneComparator() {
        return Comparator
                .comparingDouble(PaletteMapRenderer::perceivedBrightness)
                .reversed()
                .thenComparing(Comparator.comparingDouble(PaletteMapRenderer::saturation).reversed());
    }

    private static void fillPaletteColumn(Color[][] grid, int column, List<Color> colors, List<Color> overflow) {
        int row = 0;
        for (Color color : colors) {
            if (row < PALETTE_GRID_ROWS) {
                grid[row++][column] = color;
            } else {
                overflow.add(color);
            }
        }
    }

    private static void fillPaletteEmptyCells(Color[][] grid, List<Color> colors) {
        int colorIndex = 0;
        for (int row = 0; row < PALETTE_GRID_ROWS && colorIndex < colors.size(); row++) {
            for (int column = 0; column < PALETTE_GRID_COLUMNS && colorIndex < colors.size(); column++) {
                if (grid[row][column] == null) {
                    grid[row][column] = colors.get(colorIndex++);
                }
            }
        }
    }

    private static int mapColorGroupIndex(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0];
        float saturation = hsb[1];
        if (saturation < 0.12F) {
            return 0;
        }
        return Math.min(MAP_COLOR_HUE_GROUPS - 1, (int) (hue * MAP_COLOR_HUE_GROUPS)) + 1;
    }

    private static Color representativeColor(List<Color> colors) {
        return colors.stream()
                .max(Comparator.comparingDouble(color -> perceivedBrightness(color) + saturation(color) * 0.35D))
                .orElse(Color.WHITE);
    }

    private static double perceivedBrightness(Color color) {
        return (color.getRed() * 0.2126D + color.getGreen() * 0.7152D + color.getBlue() * 0.0722D) / 255.0D;
    }

    private static double hue(Color color) {
        return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
    }

    private static double saturation(Color color) {
        return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[1];
    }

    private static Color[][] paletteGrid(boolean shaderRgb) {
        return shaderRgb ? RGB_PALETTE_GRID : VANILLA_PALETTE_GRID;
    }

    private static Color[][] createRgbPaletteGrid() {
        Color[][] grid = new Color[PALETTE_GRID_ROWS][PALETTE_GRID_COLUMNS];
        for (int row = 0; row < PALETTE_GRID_ROWS; row++) {
            float brightness = 1.0F - row / (float) (PALETTE_GRID_ROWS - 1) * 0.84F;
            float saturation = Math.min(1.0F, 0.22F + row / (float) (PALETTE_GRID_ROWS - 1) * 0.78F);
            for (int column = 0; column < PALETTE_GRID_COLUMNS; column++) {
                if (column == 0) {
                    grid[row][column] = Color.getHSBColor(0.0F, 0.0F, brightness);
                    continue;
                }

                float hue = (column - 1) / (float) (PALETTE_GRID_COLUMNS - 1);
                grid[row][column] = Color.getHSBColor(hue, saturation, brightness);
            }
        }
        return grid;
    }

    private static Color[][] createVanillaPaletteGrid() {
        Color[][] grid = new Color[PALETTE_GRID_ROWS][PALETTE_GRID_COLUMNS];
        for (int row = 0; row < PALETTE_GRID_ROWS; row++) {
            for (int column = 0; column < PALETTE_GRID_COLUMNS; column++) {
                grid[row][column] = snapToMapColor(RGB_PALETTE_GRID[row][column]);
            }
        }
        return grid;
    }

    private static Color cursorColorFor(Color color) {
        return perceivedBrightness(color) > 0.62D ? Color.BLACK : Color.WHITE;
    }

    private static boolean isColorBarCursorPixel(PaletteBoard board, int x, int y) {
        PixelPoint cursor = colorCursorPoint(board);
        int dx = Math.abs(x - cursor.x());
        int dy = Math.abs(y - cursor.y());
        return dx <= 7 && dy <= 7 && (dx >= 5 || dy >= 5);
    }

    private static Color shade(Color color, float multiplier) {
        return new Color(
                Math.max(0, Math.min(255, Math.round(color.getRed() * multiplier))),
                Math.max(0, Math.min(255, Math.round(color.getGreen() * multiplier))),
                Math.max(0, Math.min(255, Math.round(color.getBlue() * multiplier)))
        );
    }

    private static Color blend(Color from, Color to, float amount) {
        float clamped = clamp01(amount);
        float inverse = 1.0F - clamped;
        return new Color(
                Math.max(0, Math.min(255, Math.round(from.getRed() * inverse + to.getRed() * clamped))),
                Math.max(0, Math.min(255, Math.round(from.getGreen() * inverse + to.getGreen() * clamped))),
                Math.max(0, Math.min(255, Math.round(from.getBlue() * inverse + to.getBlue() * clamped)))
        );
    }

    private static int gradientColumnAt(int x) {
        int localX = Math.max(0, Math.min(PALETTE_GRADIENT_WIDTH - 1, x - PALETTE_GRADIENT_X));
        return Math.min(PALETTE_GRADIENT_COLUMNS - 1, localX * PALETTE_GRADIENT_COLUMNS / PALETTE_GRADIENT_WIDTH);
    }

    private static int gradientRowAt(int y) {
        int localY = Math.max(0, Math.min(PALETTE_GRADIENT_HEIGHT - 1, y - PALETTE_GRADIENT_Y));
        return Math.min(PALETTE_GRADIENT_ROWS - 1, localY * PALETTE_GRADIENT_ROWS / PALETTE_GRADIENT_HEIGHT);
    }

    private static int gradientCellStartX(int column) {
        return PALETTE_GRADIENT_X + column * PALETTE_GRADIENT_WIDTH / PALETTE_GRADIENT_COLUMNS;
    }

    private static int gradientCellStartY(int row) {
        return PALETTE_GRADIENT_Y + row * PALETTE_GRADIENT_HEIGHT / PALETTE_GRADIENT_ROWS;
    }

    private static int gradientCellWidth(int column) {
        int start = column * PALETTE_GRADIENT_WIDTH / PALETTE_GRADIENT_COLUMNS;
        int end = (column + 1) * PALETTE_GRADIENT_WIDTH / PALETTE_GRADIENT_COLUMNS;
        return end - start;
    }

    private static int gradientCellHeight(int row) {
        int start = row * PALETTE_GRADIENT_HEIGHT / PALETTE_GRADIENT_ROWS;
        int end = (row + 1) * PALETTE_GRADIENT_HEIGHT / PALETTE_GRADIENT_ROWS;
        return end - start;
    }

    private static boolean isGradientCellDivider(int x, int y, int column, int row) {
        int cellX = gradientCellStartX(column);
        int cellY = gradientCellStartY(row);
        int localX = x - cellX;
        int localY = y - cellY;
        return localX < 2 || localY < 2;
    }

    private static Color[][] createGradientSwatches() {
        return new Color[][]{
                {
                        c(0x000000), c(0xFFFFFF), c(0xF01818), c(0xF06A18),
                        c(0xF2D322), c(0x74D624), c(0x17A84E), c(0x17C9C3),
                        c(0x56B9F2), c(0x2758E8), c(0x7B34D8), c(0xEF5CAB)
                },
                {
                        c(0x555555), c(0x9E1B1B), c(0xB94717), c(0xA8891B),
                        c(0x4D7D1D), c(0x0C6F46), c(0x147174), c(0x16518F),
                        c(0x262B8F), c(0x59258F), c(0x9B236D), c(0x7A4B28)
                },
                {
                        c(0xB7B7B7), c(0xFF8A8A), c(0xFFB077), c(0xFFF07A),
                        c(0xC9FF74), c(0x91F0A0), c(0x93F0E5), c(0x9AD8FF),
                        c(0x9BA6FF), c(0xC59AFF), c(0xFF9DDF), c(0xD8B08D)
                },
                {
                        c(0xF7D6B2), c(0xE7B887), c(0xC98E5B), c(0x8E5D35),
                        c(0xF0D28A), c(0xC7A64A), c(0x8B8731), c(0x626B2A),
                        c(0xB65F3C), c(0x884328), c(0x5C3024), c(0x2D211B)
                },
                {
                        c(0x3A1C1C), c(0x6F241C), c(0x8D4A16), c(0x716614),
                        c(0x244A22), c(0x1B4F3A), c(0x1A4750), c(0x1D355C),
                        c(0x202044), c(0x3B2555), c(0x5C2646), c(0x303030)
                },
                {
                        c(0xFF3B30), c(0xFF9500), c(0xCCFF00), c(0x39FF14),
                        c(0x00FFC8), c(0x00A2FF), c(0x7A5CFF), c(0xFF2BD6),
                        c(0xFF6F61), c(0xD4AF37), c(0xC0C0C0), c(0x101820)
                }
        };
    }

    private static Color c(int rgb) {
        return new Color(rgb);
    }

    private static boolean isGradientCursorPixel(PaletteBoard board, int x, int y) {
        PixelPoint cursor = colorCursorPoint(board);
        if (cursor == null) {
            return false;
        }
        int cursorX = cursor.x();
        int cursorY = cursor.y();
        int dx = Math.abs(x - cursorX);
        int dy = Math.abs(y - cursorY);
        int distanceSquared = dx * dx + dy * dy;
        return distanceSquared <= 100 && distanceSquared >= 64;
    }

    private static boolean isBrightnessCursorPixel(PaletteBoard board, int x, int y) {
        PixelPoint cursor = brightnessCursorPoint(board);
        if (cursor == null) {
            return false;
        }
        int cursorY = cursor.y();
        int dy = Math.abs(y - cursorY);
        boolean inHandle = x >= PALETTE_HUE_BAR_X - 5
                && x < PALETTE_HUE_BAR_X + PALETTE_HUE_BAR_WIDTH + 5
                && dy <= 5;
        boolean inside = x >= PALETTE_HUE_BAR_X
                && x < PALETTE_HUE_BAR_X + PALETTE_HUE_BAR_WIDTH
                && dy <= 2;
        return inHandle && !inside;
    }

    private static int paletteGridColumn(int x) {
        for (int column = 0; column < PALETTE_GRID_COLUMNS; column++) {
            int swatchX = PALETTE_GRID_X + column * (PALETTE_SWATCH_WIDTH + PALETTE_SWATCH_GAP);
            if (x >= swatchX && x < swatchX + PALETTE_SWATCH_WIDTH) {
                return column;
            }
        }
        return -1;
    }

    private static int paletteGridRow(int y) {
        for (int row = 0; row < PALETTE_GRID_ROWS; row++) {
            int swatchY = PALETTE_GRID_Y + row * (PALETTE_SWATCH_HEIGHT + PALETTE_SWATCH_GAP);
            if (y >= swatchY && y < swatchY + PALETTE_SWATCH_HEIGHT) {
                return row;
            }
        }
        return -1;
    }

    private static boolean isSelectedSwatchFrame(PaletteBoard board, Color[][] grid, int column, int row, int x, int y) {
        Color color = grid[row][column];
        if (color == null || !sameColor(board.selectedColor(), color)) {
            return false;
        }

        int swatchX = PALETTE_GRID_X + column * (PALETTE_SWATCH_WIDTH + PALETTE_SWATCH_GAP);
        int swatchY = PALETTE_GRID_Y + row * (PALETTE_SWATCH_HEIGHT + PALETTE_SWATCH_GAP);
        return x - swatchX < 2
                || swatchX + PALETTE_SWATCH_WIDTH - 1 - x < 2
                || y - swatchY < 2
                || swatchY + PALETTE_SWATCH_HEIGHT - 1 - y < 2;
    }

    private static boolean sameColor(Color first, Color second) {
        return first.getRed() == second.getRed()
                && first.getGreen() == second.getGreen()
                && first.getBlue() == second.getBlue();
    }

    private static boolean isPaletteGridFrame(int x, int y) {
        int gridX1 = PALETTE_GRID_X - 3;
        int gridY1 = PALETTE_GRID_Y - 3;
        int gridX2 = PALETTE_GRID_X + PALETTE_GRID_COLUMNS * PALETTE_SWATCH_WIDTH
                + (PALETTE_GRID_COLUMNS - 1) * PALETTE_SWATCH_GAP + 2;
        int gridY2 = PALETTE_GRID_Y + PALETTE_GRID_ROWS * PALETTE_SWATCH_HEIGHT
                + (PALETTE_GRID_ROWS - 1) * PALETTE_SWATCH_GAP + 2;
        if (x < gridX1 || x > gridX2 || y < gridY1 || y > gridY2) {
            return false;
        }
        return paletteGridColumn(x) < 0 || paletteGridRow(y) < 0;
    }

    private static Color paletteModePixelColor(PaletteBoard board, int x, int y) {
        Color gridPixel = paletteModeButtonPixelColor(board, PaletteMode.GRID, x, y);
        if (gridPixel != null) {
            return gridPixel;
        }
        return paletteModeButtonPixelColor(board, PaletteMode.GRADIENT, x, y);
    }

    private static Color paletteModeButtonPixelColor(PaletteBoard board, PaletteMode mode, int x, int y) {
        int buttonX = paletteModeButtonX(mode);
        boolean inFrame = x >= buttonX - 3 && x < buttonX + PALETTE_MODE_WIDTH + 3
                && y >= PALETTE_MODE_Y - 3 && y < PALETTE_MODE_Y + PALETTE_MODE_HEIGHT + 3;
        if (!inFrame) {
            return null;
        }

        if (!isInRect(x, y, buttonX, PALETTE_MODE_Y, PALETTE_MODE_WIDTH, PALETTE_MODE_HEIGHT)) {
            return board.mode() == mode ? PALETTE_MODE_ACTIVE : PALETTE_BORDER;
        }

        String label = mode == PaletteMode.GRID ? "BASE" : "RGB";
        if (isCenteredTinyTextPixel(label, x, y, buttonX, PALETTE_MODE_Y, PALETTE_MODE_WIDTH, PALETTE_MODE_HEIGHT, 2)) {
            return board.mode() == mode ? Color.WHITE : new Color(212, 210, 196);
        }

        if (mode == PaletteMode.GRID) {
            return board.mode() == mode ? new Color(91, 76, 55) : new Color(54, 51, 44);
        }
        return board.mode() == mode ? new Color(68, 85, 75) : new Color(45, 51, 47);
    }

    private static int paletteModeButtonX(PaletteMode mode) {
        return mode == PaletteMode.GRID
                ? PALETTE_MODE_X
                : PALETTE_MODE_X + PALETTE_MODE_WIDTH + PALETTE_MODE_GAP;
    }

    private static boolean isFrame(int x, int y, int frameX, int frameY, int width, int height) {
        return x >= frameX - 2 && x < frameX + width + 2
                && y >= frameY - 2 && y < frameY + height + 2
                && !isInRect(x, y, frameX, frameY, width, height);
    }

    private static boolean isInRect(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && x < rectX + width && y >= rectY && y < rectY + height;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record MapColorGroup(Color representative, Color[] colors) {
    }
}
