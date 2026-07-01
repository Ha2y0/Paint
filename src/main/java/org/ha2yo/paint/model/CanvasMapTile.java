package org.ha2yo.paint.model;

import org.ha2yo.paint.renderer.PixelMapRenderer;
import org.bukkit.map.MapView;

public record CanvasMapTile(MapView mapView, int tileX, int tileY, PixelMapRenderer renderer) {
    public CanvasMapTile(MapView mapView, int tileX, int tileY) {
        this(mapView, tileX, tileY, null);
    }
}
