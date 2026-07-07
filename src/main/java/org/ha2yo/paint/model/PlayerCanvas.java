package org.ha2yo.paint.model;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerCanvas {
    private final UUID ownerId;
    private final CanvasPlane plane;
    private final PixelCanvas pixelCanvas;
    private final int tileCount;
    private final Set<BlockKey> blocks = new HashSet<>();
    private final Set<UUID> frameIds = new HashSet<>();
    private final Set<UUID> editorIds = new HashSet<>();
    private final Set<UUID> hiddenViewerIds = new HashSet<>();
    private final List<CanvasMapTile> mapTiles = new ArrayList<>();
    private final Map<UUID, int[]> sentTileVersionsByPlayer = new HashMap<>();
    private final Deque<PixelCanvas.LayerSnapshot> undoSnapshots = new ArrayDeque<>();
    private final Deque<PixelCanvas.LayerSnapshot> redoSnapshots = new ArrayDeque<>();
    private boolean layerPanelOpen;
    private int layerSettingsIndex = -1;

    public PlayerCanvas(UUID ownerId, CanvasPlane plane, PixelCanvas pixelCanvas, int tileCount) {
        this.ownerId = ownerId;
        this.plane = plane;
        this.pixelCanvas = pixelCanvas;
        this.tileCount = tileCount;
        resetSentTileVersions();
    }

    public boolean isOwner(Player player) {
        return ownerId.equals(player.getUniqueId());
    }

    public boolean canEdit(Player player) {
        UUID playerId = player.getUniqueId();
        return ownerId.equals(playerId) || editorIds.contains(playerId);
    }

    public boolean grantEditor(UUID playerId) {
        return playerId != null && editorIds.add(playerId);
    }

    public boolean revokeEditor(UUID playerId) {
        return playerId != null && editorIds.remove(playerId);
    }

    public Set<UUID> editorIds() {
        return Set.copyOf(editorIds);
    }

    public boolean isHiddenFor(UUID playerId) {
        return playerId != null && hiddenViewerIds.contains(playerId);
    }

    public void setVisibleFor(UUID playerId, boolean visible) {
        if (playerId == null) {
            return;
        }
        if (visible) {
            hiddenViewerIds.remove(playerId);
        } else {
            hiddenViewerIds.add(playerId);
        }
    }

    public UUID ownerId() {
        return ownerId;
    }

    public CanvasPlane plane() {
        return plane;
    }

    public PixelCanvas pixelCanvas() {
        return pixelCanvas;
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

    public Deque<PixelCanvas.LayerSnapshot> undoSnapshots() {
        return undoSnapshots;
    }

    public Deque<PixelCanvas.LayerSnapshot> redoSnapshots() {
        return redoSnapshots;
    }

    public boolean layerPanelOpen() {
        return layerPanelOpen;
    }

    public void setLayerPanelOpen(boolean layerPanelOpen) {
        this.layerPanelOpen = layerPanelOpen;
        if (!layerPanelOpen) {
            layerSettingsIndex = -1;
        }
    }

    public int layerSettingsIndex() {
        return layerSettingsIndex;
    }

    public void setLayerSettingsIndex(int layerSettingsIndex) {
        this.layerSettingsIndex = layerSettingsIndex;
    }

    public int[] sentTileVersions(UUID playerId) {
        return sentTileVersionsByPlayer.computeIfAbsent(playerId, ignored -> {
            int[] versions = new int[tileCount];
            java.util.Arrays.fill(versions, -1);
            return versions;
        });
    }

    public void resetSentTileVersions() {
        sentTileVersionsByPlayer.clear();
    }
}
