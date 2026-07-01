package org.ha2yo.paint.model;

import java.awt.Color;
import java.util.Arrays;
import java.util.Objects;

public final class PixelCanvas {
    public static final int LAYER_COUNT = 3;

    private final int width;
    private final int height;
    private final int blockWidth;
    private final int blockHeight;
    private final int mapSize;
    private final int drawScale;
    private final int mapMarginX;
    private final int mapMarginY;
    private final Color backgroundColor;
    private final Color[][] layers;
    private final Color[] compositePixels;
    private final boolean[] layerVisible;
    private final int[] layerOpacityPercent;
    private final int[] layerLabels;
    private final int[] tileVersions;
    private final boolean[] dirtyTiles;
    private int activeLayerCount = 1;
    private int activeLayerIndex;
    private int version;

    public PixelCanvas(
            int width,
            int height,
            int blockWidth,
            int blockHeight,
            int mapSize,
            int drawScale,
            int mapMarginX,
            int mapMarginY,
            Color backgroundColor
    ) {
        this.width = width;
        this.height = height;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.mapSize = mapSize;
        this.drawScale = drawScale;
        this.mapMarginX = mapMarginX;
        this.mapMarginY = mapMarginY;
        this.backgroundColor = backgroundColor;
        this.layers = new Color[LAYER_COUNT][width * height];
        this.compositePixels = new Color[width * height];
        this.layerVisible = new boolean[LAYER_COUNT];
        this.layerOpacityPercent = new int[LAYER_COUNT];
        this.layerLabels = new int[LAYER_COUNT];
        Arrays.fill(layerVisible, true);
        Arrays.fill(layerOpacityPercent, 100);
        this.tileVersions = new int[blockWidth * blockHeight];
        this.dirtyTiles = new boolean[blockWidth * blockHeight];
        clear();
    }

    public synchronized void clear() {
        for (int layer = 0; layer < layers.length; layer++) {
            Arrays.fill(layers[layer], null);
        }
        Arrays.fill(layerVisible, true);
        Arrays.fill(layerOpacityPercent, 100);
        for (int layer = 0; layer < layerLabels.length; layer++) {
            layerLabels[layer] = layer + 1;
        }
        activeLayerCount = 1;
        activeLayerIndex = 0;
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized void clearPixels() {
        for (int layer = 0; layer < layers.length; layer++) {
            Arrays.fill(layers[layer], null);
        }
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized void clearActiveLayer() {
        Arrays.fill(layers[activeLayerIndex], null);
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized void setPixel(int x, int y, Color color) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }

        int index = y * width + x;
        if (Objects.equals(layers[activeLayerIndex][index], color)) {
            return;
        }

        layers[activeLayerIndex][index] = color;
        updateCompositePixel(index);
        version++;
        markDirtyTiles(x, y);
    }

    public synchronized void erasePixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }

        int index = y * width + x;
        if (layers[activeLayerIndex][index] == null) {
            return;
        }

        layers[activeLayerIndex][index] = null;
        updateCompositePixel(index);
        version++;
        markDirtyTiles(x, y);
    }

    public synchronized Color getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return backgroundColor;
        }
        int index = y * width + x;
        return compositePixels[index];
    }

    public synchronized Color getActiveLayerPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return layers[activeLayerIndex][y * width + x];
    }

    public synchronized Color getPixelWithoutActiveLayer(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return backgroundColor;
        }
        return compositePixel(y * width + x, activeLayerIndex);
    }

    public synchronized boolean hasPaintedPixels() {
        for (Color[] layer : layers) {
            for (Color color : layer) {
                if (color != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized int version() {
        return version;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int blockWidth() {
        return blockWidth;
    }

    public int blockHeight() {
        return blockHeight;
    }

    public int mapSize() {
        return mapSize;
    }

    public int drawScale() {
        return drawScale;
    }

    public int mapMarginX() {
        return mapMarginX;
    }

    public int mapMarginY() {
        return mapMarginY;
    }

    public synchronized Color[] snapshot() {
        return compositePixels.clone();
    }

    public synchronized Color[] snapshotMapTile(int tileX, int tileY) {
        Color[] snapshot = new Color[mapSize * mapSize];
        int panelOffsetX = tileX * mapSize;
        int panelOffsetY = tileY * mapSize;
        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                int panelX = panelOffsetX + x;
                int panelY = panelOffsetY + y;
                if (panelX < mapMarginX || panelY < mapMarginY) {
                    continue;
                }

                int imageX = (panelX - mapMarginX) / drawScale;
                int imageY = (panelY - mapMarginY) / drawScale;
                if (imageX < 0 || imageX >= width || imageY < 0 || imageY >= height) {
                    continue;
                }

                snapshot[y * mapSize + x] = compositePixels[imageY * width + imageX];
            }
        }
        return snapshot;
    }

    public synchronized void restore(Color[] snapshot) {
        if (snapshot.length != layers[0].length) {
            return;
        }

        for (int layer = 0; layer < layers.length; layer++) {
            Arrays.fill(layers[layer], null);
        }
        System.arraycopy(snapshot, 0, layers[0], 0, layers[0].length);
        Arrays.fill(layerVisible, true);
        Arrays.fill(layerOpacityPercent, 100);
        for (int layer = 0; layer < layerLabels.length; layer++) {
            layerLabels[layer] = layer + 1;
        }
        activeLayerCount = 1;
        activeLayerIndex = 0;
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized LayerSnapshot layerSnapshot() {
        Color[][] layerCopies = new Color[layers.length][];
        for (int layer = 0; layer < layers.length; layer++) {
            layerCopies[layer] = layers[layer].clone();
        }
        return new LayerSnapshot(layerCopies, layerVisible.clone(), layerOpacityPercent.clone(), layerLabels.clone(), activeLayerCount, activeLayerIndex);
    }

    public synchronized void restore(LayerSnapshot snapshot) {
        if (snapshot.layers().length != layers.length
                || snapshot.visible().length != layerVisible.length
                || snapshot.opacityPercent().length != layerOpacityPercent.length
                || snapshot.labels().length != layerLabels.length) {
            return;
        }
        for (int layer = 0; layer < layers.length; layer++) {
            if (snapshot.layers()[layer].length != layers[layer].length) {
                return;
            }
        }

        for (int layer = 0; layer < layers.length; layer++) {
            System.arraycopy(snapshot.layers()[layer], 0, layers[layer], 0, layers[layer].length);
        }
        System.arraycopy(snapshot.visible(), 0, layerVisible, 0, layerVisible.length);
        System.arraycopy(snapshot.opacityPercent(), 0, layerOpacityPercent, 0, layerOpacityPercent.length);
        System.arraycopy(snapshot.labels(), 0, layerLabels, 0, layerLabels.length);
        activeLayerCount = Math.max(1, Math.min(layers.length, snapshot.activeLayerCount()));
        activeLayerIndex = Math.max(0, Math.min(activeLayerCount - 1, snapshot.activeLayerIndex()));
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized int activeLayerCount() {
        return activeLayerCount;
    }

    public synchronized int activeLayerIndex() {
        return activeLayerIndex;
    }

    public synchronized int layerLabel(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= activeLayerCount) {
            return layerIndex + 1;
        }
        return layerLabels[layerIndex];
    }

    public synchronized void setActiveLayerIndex(int layerIndex) {
        int clamped = Math.max(0, Math.min(activeLayerCount - 1, layerIndex));
        if (activeLayerIndex == clamped) {
            return;
        }
        activeLayerIndex = clamped;
    }

    public synchronized boolean isLayerVisible(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= activeLayerCount) {
            return false;
        }
        return layerVisible[layerIndex];
    }

    public synchronized void setLayerVisible(int layerIndex, boolean visible) {
        if (layerIndex < 0 || layerIndex >= activeLayerCount || layerVisible[layerIndex] == visible) {
            return;
        }
        layerVisible[layerIndex] = visible;
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized int layerOpacityPercent(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= activeLayerCount) {
            return 100;
        }
        return layerOpacityPercent[layerIndex];
    }

    public synchronized boolean setLayerOpacityPercent(int layerIndex, int opacityPercent) {
        if (layerIndex < 0 || layerIndex >= activeLayerCount) {
            return false;
        }

        int clamped = Math.max(0, Math.min(100, opacityPercent));
        if (layerOpacityPercent[layerIndex] == clamped) {
            return false;
        }

        layerOpacityPercent[layerIndex] = clamped;
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
        return true;
    }

    public synchronized boolean addLayer() {
        if (activeLayerCount >= layers.length) {
            return false;
        }

        Arrays.fill(layers[activeLayerCount], null);
        layerVisible[activeLayerCount] = true;
        layerOpacityPercent[activeLayerCount] = 100;
        layerLabels[activeLayerCount] = nextAvailableLayerLabel();
        activeLayerIndex = activeLayerCount;
        activeLayerCount++;
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
        return true;
    }

    public synchronized boolean moveLayerUp(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= activeLayerCount - 1) {
            return false;
        }

        swapLayers(layerIndex, layerIndex + 1);
        return true;
    }

    public synchronized boolean moveLayerDown(int layerIndex) {
        if (layerIndex <= 0 || layerIndex >= activeLayerCount) {
            return false;
        }

        swapLayers(layerIndex, layerIndex - 1);
        return true;
    }

    public synchronized boolean moveLayerTo(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= activeLayerCount || toIndex < 0 || toIndex >= activeLayerCount || fromIndex == toIndex) {
            return false;
        }

        Color[] movingLayer = layers[fromIndex];
        boolean movingVisible = layerVisible[fromIndex];
        int movingOpacityPercent = layerOpacityPercent[fromIndex];
        int movingLabel = layerLabels[fromIndex];
        if (fromIndex < toIndex) {
            System.arraycopy(layers, fromIndex + 1, layers, fromIndex, toIndex - fromIndex);
            System.arraycopy(layerVisible, fromIndex + 1, layerVisible, fromIndex, toIndex - fromIndex);
            System.arraycopy(layerOpacityPercent, fromIndex + 1, layerOpacityPercent, fromIndex, toIndex - fromIndex);
            System.arraycopy(layerLabels, fromIndex + 1, layerLabels, fromIndex, toIndex - fromIndex);
        } else {
            System.arraycopy(layers, toIndex, layers, toIndex + 1, fromIndex - toIndex);
            System.arraycopy(layerVisible, toIndex, layerVisible, toIndex + 1, fromIndex - toIndex);
            System.arraycopy(layerOpacityPercent, toIndex, layerOpacityPercent, toIndex + 1, fromIndex - toIndex);
            System.arraycopy(layerLabels, toIndex, layerLabels, toIndex + 1, fromIndex - toIndex);
        }
        layers[toIndex] = movingLayer;
        layerVisible[toIndex] = movingVisible;
        layerOpacityPercent[toIndex] = movingOpacityPercent;
        layerLabels[toIndex] = movingLabel;

        if (activeLayerIndex == fromIndex) {
            activeLayerIndex = toIndex;
        } else if (fromIndex < activeLayerIndex && activeLayerIndex <= toIndex) {
            activeLayerIndex--;
        } else if (toIndex <= activeLayerIndex && activeLayerIndex < fromIndex) {
            activeLayerIndex++;
        }

        version++;
        rebuildComposite();
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
        return true;
    }

    public synchronized boolean deleteLayer(int layerIndex) {
        if (activeLayerCount <= 1 || layerIndex < 0 || layerIndex >= activeLayerCount) {
            return false;
        }

        boolean deletedActiveLayer = activeLayerIndex == layerIndex;
        int lastIndex = activeLayerCount - 1;
        if (layerIndex < lastIndex) {
            System.arraycopy(layers, layerIndex + 1, layers, layerIndex, lastIndex - layerIndex);
            System.arraycopy(layerVisible, layerIndex + 1, layerVisible, layerIndex, lastIndex - layerIndex);
            System.arraycopy(layerOpacityPercent, layerIndex + 1, layerOpacityPercent, layerIndex, lastIndex - layerIndex);
            System.arraycopy(layerLabels, layerIndex + 1, layerLabels, layerIndex, lastIndex - layerIndex);
        }
        layers[lastIndex] = new Color[width * height];
        layerVisible[lastIndex] = true;
        layerOpacityPercent[lastIndex] = 100;
        layerLabels[lastIndex] = lastIndex + 1;
        activeLayerCount--;
        if (deletedActiveLayer) {
            activeLayerIndex = Math.min(layerIndex, activeLayerCount - 1);
        } else if (activeLayerIndex > layerIndex) {
            activeLayerIndex--;
        }
        rebuildComposite();
        version++;
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
        return true;
    }

    public synchronized int tileVersion(int tileX, int tileY) {
        return tileVersions[tileY * blockWidth + tileX];
    }

    private Color compositePixel(int index) {
        return compositePixel(index, -1);
    }

    private Color compositePixel(int index, int excludedLayer) {
        Color result = backgroundColor;
        for (int layer = activeLayerCount - 1; layer >= 0; layer--) {
            if (layer == excludedLayer || !layerVisible[layer]) {
                continue;
            }
            Color color = layers[layer][index];
            if (color != null) {
                result = blend(result, color, layerOpacityPercent[layer]);
            }
        }
        return result;
    }

    private void rebuildComposite() {
        for (int index = 0; index < compositePixels.length; index++) {
            updateCompositePixel(index);
        }
    }

    private void updateCompositePixel(int index) {
        compositePixels[index] = compositePixel(index);
    }

    private Color blend(Color bottom, Color top, int opacityPercent) {
        if (opacityPercent >= 100) {
            return top;
        }
        if (opacityPercent <= 0) {
            return bottom;
        }

        double opacity = opacityPercent / 100.0D;
        double inverse = 1.0D - opacity;
        return new Color(
                (int) Math.round(top.getRed() * opacity + bottom.getRed() * inverse),
                (int) Math.round(top.getGreen() * opacity + bottom.getGreen() * inverse),
                (int) Math.round(top.getBlue() * opacity + bottom.getBlue() * inverse)
        );
    }

    private void swapLayers(int first, int second) {
        Color[] colors = layers[first];
        layers[first] = layers[second];
        layers[second] = colors;

        boolean visible = layerVisible[first];
        layerVisible[first] = layerVisible[second];
        layerVisible[second] = visible;

        int opacityPercent = layerOpacityPercent[first];
        layerOpacityPercent[first] = layerOpacityPercent[second];
        layerOpacityPercent[second] = opacityPercent;

        int label = layerLabels[first];
        layerLabels[first] = layerLabels[second];
        layerLabels[second] = label;

        if (activeLayerIndex == first) {
            activeLayerIndex = second;
        } else if (activeLayerIndex == second) {
            activeLayerIndex = first;
        }

        version++;
        rebuildComposite();
        Arrays.fill(tileVersions, version);
        markAllTilesDirty();
    }

    public synchronized int[] dirtyTileIndexes() {
        int count = 0;
        for (boolean dirtyTile : dirtyTiles) {
            if (dirtyTile) {
                count++;
            }
        }
        int[] indexes = new int[count];
        int writeIndex = 0;
        for (int index = 0; index < dirtyTiles.length; index++) {
            if (dirtyTiles[index]) {
                indexes[writeIndex++] = index;
            }
        }
        return indexes;
    }

    public synchronized void clearDirtyTile(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= dirtyTiles.length) {
            return;
        }
        dirtyTiles[tileIndex] = false;
    }

    private int nextAvailableLayerLabel() {
        for (int label = 1; label <= LAYER_COUNT; label++) {
            boolean used = false;
            for (int layer = 0; layer < activeLayerCount; layer++) {
                if (layerLabels[layer] == label) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return label;
            }
        }
        return activeLayerCount + 1;
    }

    private void markDirtyTiles(int x, int y) {
        int panelX1 = mapMarginX + x * drawScale;
        int panelY1 = mapMarginY + y * drawScale;
        int panelX2 = panelX1 + drawScale - 1;
        int panelY2 = panelY1 + drawScale - 1;

        markDirtyTile(panelX1 / mapSize, panelY1 / mapSize);
        markDirtyTile(panelX2 / mapSize, panelY1 / mapSize);
        markDirtyTile(panelX1 / mapSize, panelY2 / mapSize);
        markDirtyTile(panelX2 / mapSize, panelY2 / mapSize);
    }

    private void markDirtyTile(int tileX, int tileY) {
        if (tileX < 0 || tileX >= blockWidth || tileY < 0 || tileY >= blockHeight) {
            return;
        }
        int tileIndex = tileY * blockWidth + tileX;
        tileVersions[tileIndex] = version;
        dirtyTiles[tileIndex] = true;
    }

    private void markAllTilesDirty() {
        Arrays.fill(dirtyTiles, true);
    }

    public record LayerSnapshot(Color[][] layers, boolean[] visible, int[] opacityPercent, int[] labels, int activeLayerCount, int activeLayerIndex) {
    }
}
