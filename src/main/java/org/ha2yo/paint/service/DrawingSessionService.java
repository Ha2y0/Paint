package org.ha2yo.paint.service;

import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.tool.DrawingMode;
import org.ha2yo.paint.model.tool.Tool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DrawingSessionService {
    private final Map<UUID, DrawingMode> drawingModes = new HashMap<>();
    private final Map<UUID, PixelPoint> lastStrokePoints = new HashMap<>();
    private final Map<UUID, UUID> lastStrokeCanvases = new HashMap<>();
    private final Map<UUID, Long> lastStrokeTimes = new HashMap<>();
    private final Map<UUID, PlaneSample> lastStrokePlaneSamples = new HashMap<>();
    private final Map<UUID, UUID> activeHoldUndoCanvases = new HashMap<>();
    private final Set<UUID> rightHoldPainters = new HashSet<>();

    public void clearAll() {
        drawingModes.clear();
        rightHoldPainters.clear();
        activeHoldUndoCanvases.clear();
        clearStrokeState();
    }

    public void removeCanvas(UUID canvasId) {
        activeHoldUndoCanvases.remove(canvasId);
        lastStrokeCanvases.entrySet().removeIf(entry -> entry.getValue().equals(canvasId));
        lastStrokePlaneSamples.entrySet().removeIf(entry -> entry.getValue().canvasId().equals(canvasId));
    }

    public boolean isHolding(UUID playerId) {
        return rightHoldPainters.contains(playerId);
    }

    public DrawingMode mode(UUID playerId) {
        return drawingModes.getOrDefault(playerId, DrawingMode.OFF);
    }

    public void beginHold(UUID playerId, Tool tool) {
        if (!rightHoldPainters.contains(playerId)) {
            clearStrokeState(playerId);
            activeHoldUndoCanvases.remove(playerId);
        }
        rightHoldPainters.add(playerId);
        drawingModes.put(playerId, tool == Tool.ERASER ? DrawingMode.ERASE : DrawingMode.PAINT);
    }

    public void stop(UUID playerId) {
        rightHoldPainters.remove(playerId);
        drawingModes.put(playerId, DrawingMode.OFF);
        activeHoldUndoCanvases.remove(playerId);
        clearStrokeState(playerId);
    }

    public boolean hasUndoStarted(UUID playerId, UUID canvasId) {
        return canvasId.equals(activeHoldUndoCanvases.get(playerId));
    }

    public void markUndoStarted(UUID playerId, UUID canvasId) {
        activeHoldUndoCanvases.put(playerId, canvasId);
    }

    public Long lastStrokeTime(UUID playerId) {
        return lastStrokeTimes.get(playerId);
    }

    public PixelPoint lastStrokePoint(UUID playerId) {
        return lastStrokePoints.get(playerId);
    }

    public UUID lastStrokeCanvasId(UUID playerId) {
        return lastStrokeCanvases.get(playerId);
    }

    public boolean hasLastStrokePoint(UUID playerId) {
        return lastStrokePoints.containsKey(playerId);
    }

    public void setStrokePoint(UUID playerId, PixelPoint point, UUID canvasId) {
        lastStrokePoints.put(playerId, point);
        lastStrokeCanvases.put(playerId, canvasId);
    }

    public void markStroke(UUID playerId, UUID canvasId, PixelPoint point, double u, double v, double distance) {
        lastStrokePoints.put(playerId, point);
        lastStrokeTimes.put(playerId, System.currentTimeMillis());
        lastStrokeCanvases.put(playerId, canvasId);
        lastStrokePlaneSamples.put(playerId, new PlaneSample(canvasId, u, v, distance));
    }

    public PlaneSample planeSample(UUID playerId) {
        return lastStrokePlaneSamples.get(playerId);
    }

    public void setPlaneSample(UUID playerId, PlaneSample sample) {
        lastStrokePlaneSamples.put(playerId, sample);
    }

    public void removePlaneSample(UUID playerId) {
        lastStrokePlaneSamples.remove(playerId);
    }

    public void clearStrokeState() {
        lastStrokePoints.clear();
        lastStrokeCanvases.clear();
        lastStrokeTimes.clear();
        lastStrokePlaneSamples.clear();
    }

    public void clearStrokeState(UUID playerId) {
        lastStrokePlaneSamples.remove(playerId);
        clearStrokePointState(playerId);
    }

    public void clearStrokePointState(UUID playerId) {
        lastStrokePoints.remove(playerId);
        lastStrokeCanvases.remove(playerId);
        lastStrokeTimes.remove(playerId);
    }

    public record PlaneSample(UUID canvasId, double u, double v, double distance) {
    }
}
