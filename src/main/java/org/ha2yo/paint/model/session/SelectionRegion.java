package org.ha2yo.paint.model.session;

import org.ha2yo.paint.model.PixelPoint;

import java.util.UUID;

public record SelectionRegion(
        UUID canvasId,
        PixelPoint start,
        PixelPoint end,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int previewVersion
) {
    public static SelectionRegion of(UUID canvasId, PixelPoint start, PixelPoint end, int previewVersion) {
        return new SelectionRegion(
                canvasId,
                start,
                end,
                Math.min(start.x(), end.x()),
                Math.max(start.x(), end.x()),
                Math.min(start.y(), end.y()),
                Math.max(start.y(), end.y()),
                previewVersion
        );
    }

    public boolean contains(PixelPoint point) {
        return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY;
    }

    public SelectionRegion move(int dx, int dy, int previewVersion) {
        return of(canvasId, new PixelPoint(start.x() + dx, start.y() + dy), new PixelPoint(end.x() + dx, end.y() + dy), previewVersion);
    }
}
