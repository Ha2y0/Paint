package org.ha2yo.paint.service;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.CanvasMapTile;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.renderer.PixelMapRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class CanvasMapRenderService {
    private final Paint plugin;
    private final Color backgroundColor;
    private final BooleanSupplier shaderRgbEnabled;
    private final Function<UUID, String> ownerNameResolver;
    private final Function<org.bukkit.entity.Player, PixelMapRenderer.PreviewOverlay> previewOverlayResolver;

    public CanvasMapRenderService(
            Paint plugin,
            Color backgroundColor,
            BooleanSupplier shaderRgbEnabled,
            Function<UUID, String> ownerNameResolver,
            Function<org.bukkit.entity.Player, PixelMapRenderer.PreviewOverlay> previewOverlayResolver
    ) {
        this.plugin = plugin;
        this.backgroundColor = backgroundColor;
        this.shaderRgbEnabled = shaderRgbEnabled;
        this.ownerNameResolver = ownerNameResolver;
        this.previewOverlayResolver = previewOverlayResolver;
    }

    public ItemStack createMapItem(World world, PlayerCanvas canvas, int tileX, int tileY) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        MapView mapView = plugin.getServer().createMap(world);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        PixelMapRenderer renderer = new PixelMapRenderer(
                pixelCanvas,
                canvas.ownerId(),
                canvas.ownerId(),
                tileX,
                tileY,
                pixelCanvas.mapSize(),
                pixelCanvas.mapMarginX(),
                pixelCanvas.mapMarginY(),
                pixelCanvas.drawScale(),
                pixelCanvas.width(),
                pixelCanvas.height(),
                backgroundColor,
                ownerNameResolver.apply(canvas.ownerId()),
                "",
                () -> 0,
                false,
                shaderRgbEnabled,
                previewOverlayResolver
        );
        renderer.prepareSync(canvas.pixelCanvas().tileVersion(tileX, tileY), 0);
        canvas.mapTiles().add(new CanvasMapTile(mapView, tileX, tileY, renderer));
        mapView.addRenderer(renderer);

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }
}
