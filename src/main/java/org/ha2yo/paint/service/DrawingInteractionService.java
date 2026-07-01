package org.ha2yo.paint.service;

import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.Tool;

import java.awt.Color;
import java.util.UUID;

public final class DrawingInteractionService {
    private final DrawingService drawingService;
    private final DrawingSessionService drawingSessions;
    private final int maxHistorySize;

    public DrawingInteractionService(DrawingService drawingService, DrawingSessionService drawingSessions, int maxHistorySize) {
        this.drawingService = drawingService;
        this.drawingSessions = drawingSessions;
        this.maxHistorySize = maxHistorySize;
    }

    public void pushUndo(PlayerCanvas canvas) {
        canvas.undoSnapshots().push(canvas.pixelCanvas().layerSnapshot());
        while (canvas.undoSnapshots().size() > maxHistorySize) {
            canvas.undoSnapshots().removeLast();
        }
        canvas.redoSnapshots().clear();
    }

    public boolean undo(PlayerCanvas canvas) {
        if (canvas.undoSnapshots().isEmpty()) {
            return false;
        }
        canvas.redoSnapshots().push(canvas.pixelCanvas().layerSnapshot());
        canvas.pixelCanvas().restore(canvas.undoSnapshots().pop());
        return true;
    }

    public boolean redo(PlayerCanvas canvas) {
        if (canvas.redoSnapshots().isEmpty()) {
            return false;
        }
        canvas.undoSnapshots().push(canvas.pixelCanvas().layerSnapshot());
        canvas.pixelCanvas().restore(canvas.redoSnapshots().pop());
        return true;
    }

    public void drawFromLastPoint(UUID playerId, PlayerCanvas canvas, PixelPoint point, Tool tool, int brushRadius, Color color, double u, double v, double distance) {
        PixelPoint lastPoint = drawingSessions.lastStrokePoint(playerId);
        UUID lastCanvasId = drawingSessions.lastStrokeCanvasId(playerId);
        if (lastPoint == null || !canvas.ownerId().equals(lastCanvasId)) {
            drawingService.drawLine(canvas, brushRadius, point, point, color, tool);
        } else {
            drawingService.drawLine(canvas, brushRadius, lastPoint, point, color, tool);
        }
        drawingSessions.markStroke(playerId, canvas.ownerId(), point, u, v, distance);
    }
}
