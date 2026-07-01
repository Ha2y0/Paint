package org.ha2yo.paint.service;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.ha2yo.paint.model.CanvasPlane;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.PlayerCanvas;

import java.util.Collection;

public final class CanvasLookService {
    public LookResult lookedCanvas(Player player, Collection<PlayerCanvas> canvases, double maxDistance) {
        LookResult closest = null;
        for (PlayerCanvas canvas : canvases) {
            LookResult look = lookedCanvas(player, canvas, maxDistance);
            if (look == null) {
                continue;
            }
            if (closest == null || look.distance() < closest.distance()) {
                closest = look;
            }
        }
        return closest;
    }

    public LookResult lookedCanvas(Player player, PlayerCanvas canvas, double maxDistance) {
        CanvasPlaneHit hit = planeHit(player, canvas, maxDistance);
        if (hit == null || !insideCanvas(canvas, hit.u(), hit.v())) {
            return null;
        }
        return createLookResult(canvas, hit.u(), hit.v(), hit.distance());
    }

    public LookResult lookedShapeCanvas(Player player, Collection<PlayerCanvas> canvases, double maxDistance, double margin) {
        LookResult closest = null;
        for (PlayerCanvas canvas : canvases) {
            if (!canvas.canEdit(player)) {
                continue;
            }
            CanvasPlaneHit hit = planeHit(player, canvas, maxDistance);
            if (hit == null || !insideExtendedCanvas(canvas, hit.u(), hit.v(), margin)) {
                continue;
            }

            LookResult look = createExtendedLookResult(canvas, hit.u(), hit.v(), hit.distance());
            if (closest == null || look.distance() < closest.distance()) {
                closest = look;
            }
        }
        return closest;
    }

    public LookResult lookedCanvasClamped(Player player, PlayerCanvas canvas, double maxDistance) {
        CanvasPlaneHit hit = planeHit(player, canvas, maxDistance);
        if (hit == null) {
            return null;
        }
        return createLookResult(canvas, hit.u(), hit.v(), hit.distance());
    }

    public LookResult lookedCanvasExtended(Player player, PlayerCanvas canvas, double maxDistance) {
        CanvasPlaneHit hit = planeHit(player, canvas, maxDistance);
        if (hit == null) {
            return null;
        }
        return createExtendedLookResult(canvas, hit.u(), hit.v(), hit.distance());
    }

    public CanvasPlaneHit planeHit(Player player, PlayerCanvas canvas, double maxDistance) {
        CanvasPlane plane = canvas.plane();
        if (!plane.worldId().equals(player.getWorld().getUID())) {
            return null;
        }

        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector normal = vectorOf(plane.facing());
        Vector planePoint = plane.facePoint();
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 0.000001D) {
            return null;
        }

        double distance = planePoint.clone().subtract(eye).dot(normal) / denominator;
        if (distance < 0.0D || distance > maxDistance) {
            return null;
        }

        Vector hit = eye.clone().add(direction.multiply(distance));
        Vector fromOrigin = hit.clone().subtract(planePoint);
        return new CanvasPlaneHit(fromOrigin.dot(vectorOf(plane.right())), fromOrigin.getY(), distance);
    }

    public boolean insideCanvas(PlayerCanvas canvas, double u, double v) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        return u >= 0.0D && u < pixelCanvas.blockWidth() && v >= 0.0D && v < pixelCanvas.blockHeight();
    }

    public boolean insideExtendedCanvas(PlayerCanvas canvas, double u, double v, double margin) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        return u >= -margin
                && u < pixelCanvas.blockWidth() + margin
                && v >= -margin
                && v < pixelCanvas.blockHeight() + margin;
    }

    public LookResult createLookResult(PlayerCanvas canvas, double u, double v, double distance) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        double blockWidth = pixelCanvas.blockWidth();
        double blockHeight = pixelCanvas.blockHeight();
        double clampedU = Math.max(0.0D, Math.min(blockWidth - 0.000001D, u));
        double clampedV = Math.max(0.0D, Math.min(blockHeight - 0.000001D, v));
        int x = Math.min(pixelCanvas.width() - 1, (int) Math.floor(clampedU / blockWidth * pixelCanvas.width()));
        int y = Math.min(pixelCanvas.height() - 1, (int) Math.floor((blockHeight - clampedV) / blockHeight * pixelCanvas.height()));
        return new LookResult(canvas, new PixelPoint(x, y), distance, clampedU, clampedV);
    }

    public LookResult createExtendedLookResult(PlayerCanvas canvas, double u, double v, double distance) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        int x = (int) Math.floor(u / pixelCanvas.blockWidth() * pixelCanvas.width());
        int y = (int) Math.floor((pixelCanvas.blockHeight() - v) / pixelCanvas.blockHeight() * pixelCanvas.height());
        return new LookResult(canvas, new PixelPoint(x, y), distance, u, v);
    }

    private Vector vectorOf(org.bukkit.block.BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    public record LookResult(PlayerCanvas canvas, PixelPoint point, double distance, double u, double v) {
    }

    public record CanvasPlaneHit(double u, double v, double distance) {
    }
}
