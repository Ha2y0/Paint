package org.ha2yo.paint.service;

import org.ha2yo.paint.model.session.SelectionRegion;
import org.ha2yo.paint.model.session.ShapeHold;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.renderer.PixelMapRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdvancedToolSessionService {
    private final Map<UUID, ShapeHold> activeShapeHolds = new HashMap<>();
    private final Map<UUID, Tool> pendingAdvancedShapeHolds = new HashMap<>();
    private final Map<UUID, SelectionRegion> selectionRegions = new HashMap<>();
    private final Map<UUID, PixelMapRenderer.PreviewOverlay> advancedPreviewOverlays = new HashMap<>();
    private int advancedPreviewVersion;

    public void clearAll() {
        activeShapeHolds.clear();
        pendingAdvancedShapeHolds.clear();
        selectionRegions.clear();
        advancedPreviewOverlays.clear();
    }

    public void removePlayer(UUID playerId) {
        activeShapeHolds.remove(playerId);
        pendingAdvancedShapeHolds.remove(playerId);
        selectionRegions.remove(playerId);
        advancedPreviewOverlays.remove(playerId);
    }

    public void removeCanvas(UUID canvasId) {
        activeShapeHolds.entrySet().removeIf(entry -> entry.getValue().canvasId().equals(canvasId));
        pendingAdvancedShapeHolds.remove(canvasId);
        selectionRegions.entrySet().removeIf(entry -> entry.getValue().canvasId().equals(canvasId));
        advancedPreviewOverlays.entrySet().removeIf(entry -> entry.getValue().canvasId().equals(canvasId));
    }

    public ShapeHold activeHold(UUID playerId) {
        return activeShapeHolds.get(playerId);
    }

    public void putActiveHold(UUID playerId, ShapeHold hold) {
        activeShapeHolds.put(playerId, hold);
    }

    public ShapeHold removeActiveHold(UUID playerId) {
        return activeShapeHolds.remove(playerId);
    }

    public List<Map.Entry<UUID, ShapeHold>> activeHoldEntries() {
        return new ArrayList<>(activeShapeHolds.entrySet());
    }

    public void putPendingHold(UUID playerId, Tool tool) {
        pendingAdvancedShapeHolds.put(playerId, tool);
    }

    public void removePendingHold(UUID playerId) {
        pendingAdvancedShapeHolds.remove(playerId);
    }

    public List<Map.Entry<UUID, Tool>> pendingHoldEntries() {
        return new ArrayList<>(pendingAdvancedShapeHolds.entrySet());
    }

    public SelectionRegion selection(UUID playerId) {
        return selectionRegions.get(playerId);
    }

    public void putSelection(UUID playerId, SelectionRegion selection) {
        selectionRegions.put(playerId, selection);
    }

    public SelectionRegion removeSelection(UUID playerId) {
        return selectionRegions.remove(playerId);
    }

    public PixelMapRenderer.PreviewOverlay overlay(UUID playerId) {
        return advancedPreviewOverlays.get(playerId);
    }

    public void putOverlay(UUID playerId, PixelMapRenderer.PreviewOverlay overlay) {
        advancedPreviewOverlays.put(playerId, overlay);
    }

    public void removeOverlay(UUID playerId) {
        advancedPreviewOverlays.remove(playerId);
    }

    public int nextPreviewVersion() {
        advancedPreviewVersion++;
        if (advancedPreviewVersion == 0) {
            advancedPreviewVersion++;
        }
        return advancedPreviewVersion;
    }
}
