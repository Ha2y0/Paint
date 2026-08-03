package org.ha2yo.paint.service;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.CanvasMapTile;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.renderer.PixelMapRenderer;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class CanvasMapRenderService {
    private final Paint plugin;
    private final Color backgroundColor;
    private final BooleanSupplier shaderRgbEnabled;
    private final Function<UUID, String> ownerNameResolver;
    private final Function<org.bukkit.entity.Player, PixelMapRenderer.PreviewOverlay> previewOverlayResolver;
    private final ReusableMapPoolService reusableMaps;
    private final Map<UUID, ReusableMapPoolService.MapLease> mapLeases = new HashMap<>();

    public CanvasMapRenderService(
            Paint plugin,
            Color backgroundColor,
            BooleanSupplier shaderRgbEnabled,
            Function<UUID, String> ownerNameResolver,
            Function<org.bukkit.entity.Player, PixelMapRenderer.PreviewOverlay> previewOverlayResolver,
            ReusableMapPoolService reusableMaps
    ) {
        this.plugin = plugin;
        this.backgroundColor = backgroundColor;
        this.shaderRgbEnabled = shaderRgbEnabled;
        this.ownerNameResolver = ownerNameResolver;
        this.previewOverlayResolver = previewOverlayResolver;
        this.reusableMaps = reusableMaps;
    }

    public ItemStack createMapItem(World world, PlayerCanvas canvas, int tileX, int tileY) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        ReusableMapPoolService.MapLease mapLease = mapLeases.computeIfAbsent(
                canvas.ownerId(),
                ignored -> reusableMaps.acquire(world, pixelCanvas.blockWidth() * pixelCanvas.blockHeight())
        );
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
        int mapIndex = tileY * pixelCanvas.blockWidth() + tileX;
        ReusableMapPoolService.PreparedMap preparedMap = reusableMaps.prepareMap(mapLease, mapIndex, renderer);
        canvas.mapTiles().add(new CanvasMapTile(preparedMap.mapView(), tileX, tileY, renderer));
        return preparedMap.itemStack();
    }

    public void releaseCanvas(UUID ownerId) {
        ReusableMapPoolService.MapLease mapLease = mapLeases.remove(ownerId);
        reusableMaps.release(mapLease);
    }
}
