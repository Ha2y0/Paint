package org.ha2yo.paint.model;

import org.ha2yo.paint.model.tool.PaletteMode;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PaletteBoard {
    private final UUID ownerId;
    private final CanvasPlane plane;
    private final Set<BlockKey> blocks = new HashSet<>();
    private final Set<UUID> frameIds = new HashSet<>();
    private final List<CanvasMapTile> mapTiles = new ArrayList<>();
    private final List<BlockState> replacedLightBlocks = new ArrayList<>();
    private UUID rgbDisplayId;
    private UUID colorCursorDisplayId;
    private UUID brightnessCursorDisplayId;
    private Color selectedColor;
    private PaletteMode mode = PaletteMode.GRADIENT;
    private float hue;
    private float saturation;
    private float brightness;
    private int gradientCursorX;
    private int gradientCursorY;
    private int brushRadius;
    private int version = 1;

    public PaletteBoard(UUID ownerId, CanvasPlane plane, Color selectedColor, int brushRadius) {
        this.ownerId = ownerId;
        this.plane = plane;
        this.brushRadius = brushRadius;
        setSelectedColor(selectedColor);
    }

    public boolean isOwner(Player player) {
        return ownerId.equals(player.getUniqueId());
    }

    public UUID ownerId() {
        return ownerId;
    }

    public CanvasPlane plane() {
        return plane;
    }

    public Set<BlockKey> blocks() {
        return blocks;
    }

    public Set<UUID> frameIds() {
        return frameIds;
    }

    public List<CanvasMapTile> mapTiles() {
        return mapTiles;
    }

    public List<BlockState> replacedLightBlocks() {
        return replacedLightBlocks;
    }

    public UUID rgbDisplayId() {
        return rgbDisplayId;
    }

    public void setRgbDisplayId(UUID rgbDisplayId) {
        this.rgbDisplayId = rgbDisplayId;
    }

    public UUID colorCursorDisplayId() {
        return colorCursorDisplayId;
    }

    public void setColorCursorDisplayId(UUID colorCursorDisplayId) {
        this.colorCursorDisplayId = colorCursorDisplayId;
    }

    public UUID brightnessCursorDisplayId() {
        return brightnessCursorDisplayId;
    }

    public void setBrightnessCursorDisplayId(UUID brightnessCursorDisplayId) {
        this.brightnessCursorDisplayId = brightnessCursorDisplayId;
    }

    public int version() {
        return version;
    }

    public PaletteMode mode() {
        return mode;
    }

    public void setMode(PaletteMode mode) {
        this.mode = mode;
    }

    public Color selectedColor() {
        return selectedColor;
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float brightness() {
        return brightness;
    }

    public int gradientCursorX() {
        return gradientCursorX;
    }

    public int gradientCursorY() {
        return gradientCursorY;
    }

    public int brushRadius() {
        return brushRadius;
    }

    public void setBrushRadius(int brushRadius) {
        this.brushRadius = brushRadius;
    }

    public void setSelectedColor(Color color) {
        selectedColor = color;
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (hsb[1] >= 0.01F) {
            hue = hsb[0];
        }
        saturation = hsb[1];
        brightness = hsb[2];
    }

    public void setSelectedColorOnly(Color color) {
        selectedColor = color;
    }

    public void setGradientAreaSelection(int x, int y, Color color) {
        gradientCursorX = x;
        gradientCursorY = y;
        setSelectedColor(color);
    }

    public void setGradientCursor(int x, int y) {
        gradientCursorX = x;
        gradientCursorY = y;
    }

    public void setGradientSelection(float hue, float saturation, float brightness) {
        this.hue = clamp01(hue);
        this.saturation = clamp01(saturation);
        this.brightness = clamp01(brightness);
        selectedColor = Color.getHSBColor(this.hue, this.saturation, this.brightness);
    }

    public void incrementVersion() {
        version++;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
