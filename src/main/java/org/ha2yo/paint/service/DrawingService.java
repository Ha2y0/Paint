package org.ha2yo.paint.service;

import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.Tool;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DrawingService {
    public void drawLine(PlayerCanvas canvas, int brushSize, PixelPoint start, PixelPoint end, Color color, Tool tool) {
        if (tool != Tool.SPRAY) {
            drawThickSegment(canvas, brushSize, start, end, color);
            return;
        }

        int x0 = start.x();
        int y0 = start.y();
        int x1 = end.x();
        int y1 = end.y();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;

        while (true) {
            stampTool(canvas, brushSize, new PixelPoint(x0, y0), color, tool);
            if (x0 == x1 && y0 == y1) {
                break;
            }

            int doubleError = 2 * error;
            if (doubleError > -dy) {
                error -= dy;
                x0 += sx;
            }
            if (doubleError < dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private void drawThickSegment(PlayerCanvas canvas, int brushSize, PixelPoint start, PixelPoint end, Color color) {
        int size = Math.max(1, brushSize);
        if (size == 1) {
            drawThinSegment(canvas, start.x(), start.y(), end.x(), end.y(), color);
            return;
        }

        List<PixelPoint> linePixels = new ArrayList<>();
        addLinePixels(linePixels, start.x(), start.y(), end.x(), end.y());
        for (PixelPoint point : linePixels) {
            stampBrush(canvas, size, point, color);
        }
    }

    private void addThickSegmentPixels(List<PixelPoint> pixels, PixelCanvas canvas, int brushSize, PixelPoint start, PixelPoint end) {
        List<PixelPoint> linePixels = new ArrayList<>();
        addLinePixels(linePixels, start.x(), start.y(), end.x(), end.y());
        pixels.addAll(thickenPixels(linePixels, canvas, brushSize));
    }

    private void drawThinSegment(PlayerCanvas canvas, int x0, int y0, int x1, int y1, Color color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;

        while (true) {
            stampPixel(canvas, x0, y0, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }

            int doubleError = 2 * error;
            if (doubleError > -dy) {
                error -= dy;
                x0 += sx;
            }
            if (doubleError < dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    public void floodFill(PixelCanvas canvas, PixelPoint start, Color replacement) {
        Color target = canvas.getActiveLayerPixel(start.x(), start.y());
        if (Objects.equals(target, replacement)) {
            return;
        }

        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        boolean[] visited = new boolean[canvasWidth * canvasHeight];
        Deque<PixelPoint> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            PixelPoint point = queue.removeFirst();
            int x = point.x();
            int y = point.y();
            if (x < 0 || x >= canvasWidth || y < 0 || y >= canvasHeight) {
                continue;
            }

            int index = y * canvasWidth + x;
            if (visited[index] || !Objects.equals(canvas.getActiveLayerPixel(x, y), target)) {
                continue;
            }

            visited[index] = true;
            canvas.setPixel(x, y, replacement);
            queue.addLast(new PixelPoint(x + 1, y));
            queue.addLast(new PixelPoint(x - 1, y));
            queue.addLast(new PixelPoint(x, y + 1));
            queue.addLast(new PixelPoint(x, y - 1));
        }
    }

    public void drawShape(PixelCanvas canvas, PixelPoint start, PixelPoint end, Color color, Tool tool) {
        for (PixelPoint point : shapePixels(canvas, start, end, tool)) {
            canvas.setPixel(point.x(), point.y(), color);
        }
    }

    public void drawShape(PixelCanvas canvas, PixelPoint start, PixelPoint end, Color color, Tool tool, int brushSize) {
        for (PixelPoint point : shapePixels(canvas, start, end, tool, brushSize)) {
            canvas.setPixel(point.x(), point.y(), color);
        }
    }

    public List<PixelPoint> shapePixels(PixelCanvas canvas, PixelPoint start, PixelPoint end, Tool tool) {
        return shapePixels(canvas, start, end, tool, 1);
    }

    public List<PixelPoint> shapePixels(PixelCanvas canvas, PixelPoint start, PixelPoint end, Tool tool, int brushSize) {
        List<PixelPoint> pixels = new ArrayList<>();
        switch (tool) {
            case LINE -> {
                if (brushSize > 1) {
                    addThickSegmentPixels(pixels, canvas, brushSize, start, end);
                } else {
                    addLinePixels(pixels, start.x(), start.y(), end.x(), end.y());
                }
            }
            case RECTANGLE -> addRectanglePixels(pixels, canvas, start, end, false);
            case FILLED_RECTANGLE -> addRectanglePixels(pixels, canvas, start, end, true);
            case TRIANGLE -> addTrianglePixels(pixels, canvas, start, end, false);
            case FILLED_TRIANGLE -> addTrianglePixels(pixels, canvas, start, end, true);
            case ELLIPSE -> addEllipsePixels(pixels, canvas, start, end, false);
            case FILLED_ELLIPSE -> addEllipsePixels(pixels, canvas, start, end, true);
            default -> {
            }
        }
        return usesBrushThickness(tool) && tool != Tool.LINE && brushSize > 1 ? thickenPixels(pixels, canvas, brushSize) : pixels;
    }

    public boolean moveActiveLayerSelection(PixelCanvas canvas, PixelPoint selectionStart, PixelPoint selectionEnd, PixelPoint dragStart, PixelPoint dragEnd) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        int minX = Math.max(0, Math.min(selectionStart.x(), selectionEnd.x()));
        int maxX = Math.min(canvasWidth - 1, Math.max(selectionStart.x(), selectionEnd.x()));
        int minY = Math.max(0, Math.min(selectionStart.y(), selectionEnd.y()));
        int maxY = Math.min(canvasHeight - 1, Math.max(selectionStart.y(), selectionEnd.y()));
        if (minX > maxX || minY > maxY) {
            return false;
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        Color[] pixels = new Color[width * height];
        boolean hasPixels = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = canvas.getActiveLayerPixel(minX + x, minY + y);
                pixels[y * width + x] = color;
                hasPixels |= color != null;
            }
        }
        if (!hasPixels) {
            return false;
        }

        int dx = dragEnd.x() - dragStart.x();
        int dy = dragEnd.y() - dragStart.y();
        if (dx == 0 && dy == 0) {
            return false;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                canvas.erasePixel(x, y);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = pixels[y * width + x];
                if (color == null) {
                    continue;
                }

                canvas.setPixel(minX + x + dx, minY + y + dy, color);
            }
        }
        return true;
    }

    private void drawRectangle(PixelCanvas canvas, PixelPoint start, PixelPoint end, Color color, boolean filled) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        int minX = Math.max(0, Math.min(start.x(), end.x()));
        int maxX = Math.min(canvasWidth - 1, Math.max(start.x(), end.x()));
        int minY = Math.max(0, Math.min(start.y(), end.y()));
        int maxY = Math.min(canvasHeight - 1, Math.max(start.y(), end.y()));
        if (filled) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    canvas.setPixel(x, y, color);
                }
            }
            return;
        }

        drawLinePixels(canvas, minX, minY, maxX, minY, color);
        drawLinePixels(canvas, maxX, minY, maxX, maxY, color);
        drawLinePixels(canvas, maxX, maxY, minX, maxY, color);
        drawLinePixels(canvas, minX, maxY, minX, minY, color);
    }

    private void addRectanglePixels(List<PixelPoint> pixels, PixelCanvas canvas, PixelPoint start, PixelPoint end, boolean filled) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        int minX = Math.min(start.x(), end.x());
        int maxX = Math.max(start.x(), end.x());
        int minY = Math.min(start.y(), end.y());
        int maxY = Math.max(start.y(), end.y());
        if (filled) {
            int clippedMinX = Math.max(0, minX);
            int clippedMaxX = Math.min(canvasWidth - 1, maxX);
            int clippedMinY = Math.max(0, minY);
            int clippedMaxY = Math.min(canvasHeight - 1, maxY);
            for (int y = clippedMinY; y <= clippedMaxY; y++) {
                for (int x = clippedMinX; x <= clippedMaxX; x++) {
                    pixels.add(new PixelPoint(x, y));
                }
            }
            return;
        }

        addLinePixels(pixels, minX, minY, maxX, minY);
        addLinePixels(pixels, maxX, minY, maxX, maxY);
        addLinePixels(pixels, maxX, maxY, minX, maxY);
        addLinePixels(pixels, minX, maxY, minX, minY);
    }

    private void drawTriangle(PixelCanvas canvas, PixelPoint start, PixelPoint end, Color color, boolean filled) {
        TriangleVertices vertices = triangleVertices(start, end);
        int minX = Math.max(0, Math.min(vertices.apexX(), Math.min(vertices.baseAX(), vertices.baseBX())));
        int maxX = Math.min(canvas.width() - 1, Math.max(vertices.apexX(), Math.max(vertices.baseAX(), vertices.baseBX())));
        int minY = Math.max(0, Math.min(vertices.apexY(), Math.min(vertices.baseAY(), vertices.baseBY())));
        int maxY = Math.min(canvas.height() - 1, Math.max(vertices.apexY(), Math.max(vertices.baseAY(), vertices.baseBY())));
        if (!filled) {
            drawLinePixels(canvas, vertices.apexX(), vertices.apexY(), vertices.baseAX(), vertices.baseAY(), color);
            drawLinePixels(canvas, vertices.baseAX(), vertices.baseAY(), vertices.baseBX(), vertices.baseBY(), color);
            drawLinePixels(canvas, vertices.baseBX(), vertices.baseBY(), vertices.apexX(), vertices.apexY(), color);
            return;
        }

        double area = triangleArea(vertices.apexX(), vertices.apexY(), vertices.baseAX(), vertices.baseAY(), vertices.baseBX(), vertices.baseBY());
        if (area <= 0.0D) {
            drawLinePixels(canvas, start.x(), start.y(), end.x(), end.y(), color);
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double a = triangleArea(x, y, vertices.baseAX(), vertices.baseAY(), vertices.baseBX(), vertices.baseBY());
                double b = triangleArea(vertices.apexX(), vertices.apexY(), x, y, vertices.baseBX(), vertices.baseBY());
                double c = triangleArea(vertices.apexX(), vertices.apexY(), vertices.baseAX(), vertices.baseAY(), x, y);
                if (Math.abs(area - (a + b + c)) <= 0.5D) {
                    canvas.setPixel(x, y, color);
                }
            }
        }
    }

    private void addTrianglePixels(List<PixelPoint> pixels, PixelCanvas canvas, PixelPoint start, PixelPoint end, boolean filled) {
        TriangleVertices vertices = triangleVertices(start, end);
        int minX = Math.max(0, Math.min(vertices.apexX(), Math.min(vertices.baseAX(), vertices.baseBX())));
        int maxX = Math.min(canvas.width() - 1, Math.max(vertices.apexX(), Math.max(vertices.baseAX(), vertices.baseBX())));
        int minY = Math.max(0, Math.min(vertices.apexY(), Math.min(vertices.baseAY(), vertices.baseBY())));
        int maxY = Math.min(canvas.height() - 1, Math.max(vertices.apexY(), Math.max(vertices.baseAY(), vertices.baseBY())));
        if (!filled) {
            addLinePixels(pixels, vertices.apexX(), vertices.apexY(), vertices.baseAX(), vertices.baseAY());
            addLinePixels(pixels, vertices.baseAX(), vertices.baseAY(), vertices.baseBX(), vertices.baseBY());
            addLinePixels(pixels, vertices.baseBX(), vertices.baseBY(), vertices.apexX(), vertices.apexY());
            return;
        }

        double area = triangleArea(vertices.apexX(), vertices.apexY(), vertices.baseAX(), vertices.baseAY(), vertices.baseBX(), vertices.baseBY());
        if (area <= 0.0D) {
            addLinePixels(pixels, start.x(), start.y(), end.x(), end.y());
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double a = triangleArea(x, y, vertices.baseAX(), vertices.baseAY(), vertices.baseBX(), vertices.baseBY());
                double b = triangleArea(vertices.apexX(), vertices.apexY(), x, y, vertices.baseBX(), vertices.baseBY());
                double c = triangleArea(vertices.apexX(), vertices.apexY(), vertices.baseAX(), vertices.baseAY(), x, y);
                if (Math.abs(area - (a + b + c)) <= 0.5D) {
                    pixels.add(new PixelPoint(x, y));
                }
            }
        }
    }

    private TriangleVertices triangleVertices(PixelPoint start, PixelPoint end) {
        int dx = end.x() - start.x();
        int dy = end.y() - start.y();
        if (Math.abs(dx) > Math.abs(dy)) {
            int halfHeight = triangleHalfSize(dy, dx);
            return new TriangleVertices(
                    start.x(),
                    start.y(),
                    end.x(),
                    start.y() - halfHeight,
                    end.x(),
                    start.y() + halfHeight
            );
        }

        int halfWidth = triangleHalfSize(dx, dy);
        return new TriangleVertices(
                start.x(),
                start.y(),
                start.x() - halfWidth,
                end.y(),
                start.x() + halfWidth,
                end.y()
        );
    }

    private int triangleHalfSize(int secondaryDelta, int primaryDelta) {
        int size = Math.abs(secondaryDelta);
        if (size == 0) {
            size = Math.abs(primaryDelta);
        }
        return Math.max(1, size);
    }

    private void drawEllipse(PixelCanvas canvas, PixelPoint start, PixelPoint end, Color color, boolean filled) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        int minX = Math.max(0, Math.min(start.x(), end.x()));
        int maxX = Math.min(canvasWidth - 1, Math.max(start.x(), end.x()));
        int minY = Math.max(0, Math.min(start.y(), end.y()));
        int maxY = Math.min(canvasHeight - 1, Math.max(start.y(), end.y()));
        double centerX = (minX + maxX) / 2.0D;
        double centerY = (minY + maxY) / 2.0D;
        double radiusX = Math.max(0.5D, (maxX - minX) / 2.0D);
        double radiusY = Math.max(0.5D, (maxY - minY) / 2.0D);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double normalized = Math.pow((x - centerX) / radiusX, 2.0D)
                        + Math.pow((y - centerY) / radiusY, 2.0D);
                if (filled ? normalized <= 1.0D : normalized >= 0.86D && normalized <= 1.14D) {
                    canvas.setPixel(x, y, color);
                }
            }
        }
    }

    private void addEllipsePixels(List<PixelPoint> pixels, PixelCanvas canvas, PixelPoint start, PixelPoint end, boolean filled) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        int minX = Math.min(start.x(), end.x());
        int maxX = Math.max(start.x(), end.x());
        int minY = Math.min(start.y(), end.y());
        int maxY = Math.max(start.y(), end.y());
        double centerX = (minX + maxX) / 2.0D;
        double centerY = (minY + maxY) / 2.0D;
        double radiusX = Math.max(0.5D, (maxX - minX) / 2.0D);
        double radiusY = Math.max(0.5D, (maxY - minY) / 2.0D);

        if (!filled) {
            addEllipseOutlinePixels(pixels, canvas, centerX, centerY, radiusX, radiusY);
            return;
        }

        int clippedMinX = Math.max(0, minX);
        int clippedMaxX = Math.min(canvasWidth - 1, maxX);
        int clippedMinY = Math.max(0, minY);
        int clippedMaxY = Math.min(canvasHeight - 1, maxY);
        for (int y = clippedMinY; y <= clippedMaxY; y++) {
            for (int x = clippedMinX; x <= clippedMaxX; x++) {
                double normalized = Math.pow((x - centerX) / radiusX, 2.0D)
                        + Math.pow((y - centerY) / radiusY, 2.0D);
                if (normalized <= 1.0D) {
                    pixels.add(new PixelPoint(x, y));
                }
            }
        }
    }

    private void addEllipseOutlinePixels(List<PixelPoint> pixels, PixelCanvas canvas, double centerX, double centerY, double radiusX, double radiusY) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        double outerRadiusX = radiusX + 0.55D;
        double outerRadiusY = radiusY + 0.55D;
        double innerRadiusX = Math.max(0.0D, radiusX - 0.55D);
        double innerRadiusY = Math.max(0.0D, radiusY - 0.55D);
        int minX = Math.max(0, (int) Math.floor(centerX - outerRadiusX));
        int maxX = Math.min(canvasWidth - 1, (int) Math.ceil(centerX + outerRadiusX));
        int minY = Math.max(0, (int) Math.floor(centerY - outerRadiusY));
        int maxY = Math.min(canvasHeight - 1, (int) Math.ceil(centerY + outerRadiusY));

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double outer = ellipseValue(x, y, centerX, centerY, outerRadiusX, outerRadiusY);
                if (outer > 1.0D) {
                    continue;
                }
                if (innerRadiusX <= 0.0D || innerRadiusY <= 0.0D) {
                    pixels.add(new PixelPoint(x, y));
                    continue;
                }

                double inner = ellipseValue(x, y, centerX, centerY, innerRadiusX, innerRadiusY);
                if (inner >= 1.0D) {
                    pixels.add(new PixelPoint(x, y));
                }
            }
        }
    }

    private double ellipseValue(double x, double y, double centerX, double centerY, double radiusX, double radiusY) {
        return Math.pow((x - centerX) / radiusX, 2.0D)
                + Math.pow((y - centerY) / radiusY, 2.0D);
    }

    private boolean usesBrushThickness(Tool tool) {
        return tool == Tool.LINE || tool == Tool.RECTANGLE || tool == Tool.TRIANGLE || tool == Tool.ELLIPSE;
    }

    private List<PixelPoint> thickenPixels(List<PixelPoint> pixels, PixelCanvas canvas, int brushSize) {
        int canvasWidth = canvas.width();
        int canvasHeight = canvas.height();
        Set<PixelPoint> thickened = new LinkedHashSet<>();
        int size = Math.max(1, brushSize);
        int start = -size / 2;
        int end = start + size - 1;
        double radius = size / 2.0D;
        double offset = size % 2 == 0 ? 0.5D : 0.0D;
        double radiusSquared = radius * radius;
        for (PixelPoint pixel : pixels) {
            for (int dx = start; dx <= end; dx++) {
                for (int dy = start; dy <= end; dy++) {
                    double sampleX = dx + offset;
                    double sampleY = dy + offset;
                    if (sampleX * sampleX + sampleY * sampleY > radiusSquared) {
                        continue;
                    }
                    int x = pixel.x() + dx;
                    int y = pixel.y() + dy;
                    if (x >= 0 && x < canvasWidth && y >= 0 && y < canvasHeight) {
                        thickened.add(new PixelPoint(x, y));
                    }
                }
            }
        }
        return new ArrayList<>(thickened);
    }

    private void drawLinePixels(PixelCanvas canvas, int x0, int y0, int x1, int y1, Color color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;

        while (true) {
            canvas.setPixel(x0, y0, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }

            int doubleError = error * 2;
            if (doubleError > -dy) {
                error -= dy;
                x0 += sx;
            }
            if (doubleError < dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private void addLinePixels(List<PixelPoint> pixels, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;

        while (true) {
            pixels.add(new PixelPoint(x0, y0));
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int twiceError = 2 * error;
            if (twiceError > -dy) {
                error -= dy;
                x0 += sx;
            }
            if (twiceError < dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private double triangleArea(int ax, int ay, int bx, int by, int cx, int cy) {
        return Math.abs((ax * (by - cy) + bx * (cy - ay) + cx * (ay - by)) / 2.0D);
    }

    private void stampTool(PlayerCanvas canvas, int brushSize, PixelPoint center, Color color, Tool tool) {
        if (tool == Tool.SPRAY) {
            sprayBrush(canvas, brushSize, center, color);
            return;
        }

        stampBrush(canvas, brushSize, center, color);
    }

    private void stampBrush(PlayerCanvas canvas, int brushSize, PixelPoint center, Color color) {
        int size = Math.max(1, brushSize);
        if (size == 1) {
            stampPixel(canvas, center.x(), center.y(), color);
            return;
        }

        int start = -size / 2;
        int end = start + size - 1;
        double radius = size / 2.0D;
        double offset = size % 2 == 0 ? 0.5D : 0.0D;
        double radiusSquared = radius * radius;

        for (int dx = start; dx <= end; dx++) {
            for (int dy = start; dy <= end; dy++) {
                double sampleX = dx + offset;
                double sampleY = dy + offset;
                if (sampleX * sampleX + sampleY * sampleY > radiusSquared) {
                    continue;
                }

                stampPixel(canvas, center.x() + dx, center.y() + dy, color);
            }
        }
    }

    private void sprayBrush(PlayerCanvas canvas, int brushSize, PixelPoint center, Color color) {
        int radius = Math.max(brushSize * 3, 6);
        int drops = Math.max(radius * 3, 18);

        for (int i = 0; i < drops; i++) {
            double angle = Math.random() * Math.PI * 2.0D;
            double distance = Math.sqrt(Math.random()) * radius;
            int x = center.x() + (int) Math.round(Math.cos(angle) * distance);
            int y = center.y() + (int) Math.round(Math.sin(angle) * distance);
            stampPixel(canvas, x, y, color);
        }
    }

    private void stampPixel(PlayerCanvas canvas, int x, int y, Color color) {
        if (color == null) {
            canvas.pixelCanvas().erasePixel(x, y);
            return;
        }
        canvas.pixelCanvas().setPixel(x, y, color);
    }

    private record TriangleVertices(int apexX, int apexY, int baseAX, int baseAY, int baseBX, int baseBY) {
    }

}
