package org.ha2yo.paint.workflow;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.service.CanvasLifecycleService;

import java.awt.Color;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

public final class CanvasWorkflowService {
    private final CanvasLifecycleService canvasLifecycle;
    private final int pixelsPerBlock;
    private final int mapSize;
    private final int drawScale;
    private final Color backgroundColor;
    private final int defaultCanvasBlockWidth;
    private final int defaultCanvasBlockHeight;
    private final IntUnaryOperator blockSizeClamp;
    private final PaletteBoardRemover paletteBoardRemover;
    private final CanvasRemover editingArtworkCleaner;
    private final Consumer<PlayerCanvas> canvasMapSender;
    private final Consumer<PlayerCanvas> layerDisplayUpdater;
    private final Consumer<PlayerCanvas> undoPusher;
    private final Consumer<UUID> layerDisplayRemover;
    private final Consumer<UUID> strokeStateCleaner;
    private final Consumer<UUID> drawingSessionCanvasRemover;
    private final Consumer<UUID> advancedSessionCanvasRemover;
    private final Consumer<UUID> canvasMapCleaner;
    private final Consumer<UUID> layerOpacityLockCleaner;
    private final Function<UUID, Player> onlinePlayerResolver;
    private final Predicate<UUID> inventoryRestoreDeferrer;
    private final Consumer<UUID> deferredInventoryRestorer;
    private final Consumer<Player> inventoryRestorer;
    private final Consumer<Player> strayToolCleaner;
    private final CanvasLifecycleService.MapItemFactory canvasCreator;
    private final Function<PlayerCanvas, PaintCanvas> canvasHandleFactory;

    public CanvasWorkflowService(
            CanvasLifecycleService canvasLifecycle,
            int pixelsPerBlock,
            int mapSize,
            int drawScale,
            Color backgroundColor,
            int defaultCanvasBlockWidth,
            int defaultCanvasBlockHeight,
            IntUnaryOperator blockSizeClamp,
            PaletteBoardRemover paletteBoardRemover,
            CanvasRemover editingArtworkCleaner,
            Consumer<PlayerCanvas> canvasMapSender,
            Consumer<PlayerCanvas> layerDisplayUpdater,
            Consumer<PlayerCanvas> undoPusher,
            Consumer<UUID> layerDisplayRemover,
            Consumer<UUID> strokeStateCleaner,
            Consumer<UUID> drawingSessionCanvasRemover,
            Consumer<UUID> advancedSessionCanvasRemover,
            Consumer<UUID> canvasMapCleaner,
            Consumer<UUID> layerOpacityLockCleaner,
            Function<UUID, Player> onlinePlayerResolver,
            Predicate<UUID> inventoryRestoreDeferrer,
            Consumer<UUID> deferredInventoryRestorer,
            Consumer<Player> inventoryRestorer,
            Consumer<Player> strayToolCleaner,
            CanvasLifecycleService.MapItemFactory canvasCreator,
            Function<PlayerCanvas, PaintCanvas> canvasHandleFactory
    ) {
        this.canvasLifecycle = canvasLifecycle;
        this.pixelsPerBlock = pixelsPerBlock;
        this.mapSize = mapSize;
        this.drawScale = drawScale;
        this.backgroundColor = backgroundColor;
        this.defaultCanvasBlockWidth = defaultCanvasBlockWidth;
        this.defaultCanvasBlockHeight = defaultCanvasBlockHeight;
        this.blockSizeClamp = blockSizeClamp;
        this.paletteBoardRemover = paletteBoardRemover;
        this.editingArtworkCleaner = editingArtworkCleaner;
        this.canvasMapSender = canvasMapSender;
        this.layerDisplayUpdater = layerDisplayUpdater;
        this.undoPusher = undoPusher;
        this.layerDisplayRemover = layerDisplayRemover;
        this.strokeStateCleaner = strokeStateCleaner;
        this.drawingSessionCanvasRemover = drawingSessionCanvasRemover;
        this.advancedSessionCanvasRemover = advancedSessionCanvasRemover;
        this.canvasMapCleaner = canvasMapCleaner;
        this.layerOpacityLockCleaner = layerOpacityLockCleaner;
        this.onlinePlayerResolver = onlinePlayerResolver;
        this.inventoryRestoreDeferrer = inventoryRestoreDeferrer;
        this.deferredInventoryRestorer = deferredInventoryRestorer;
        this.inventoryRestorer = inventoryRestorer;
        this.strayToolCleaner = strayToolCleaner;
        this.canvasCreator = canvasCreator;
        this.canvasHandleFactory = canvasHandleFactory;
    }

    public PaintCanvas create(UUID ownerId, Location origin, BlockFace facing, BlockFace right) {
        return create(ownerId, origin, facing, right, defaultCanvasBlockWidth, defaultCanvasBlockHeight);
    }

    public PaintCanvas create(UUID ownerId, Location origin, BlockFace facing, BlockFace right, int blockWidth, int blockHeight) {
        int canvasBlockWidth = blockSizeClamp.applyAsInt(blockWidth);
        int canvasBlockHeight = blockSizeClamp.applyAsInt(blockHeight);
        remove(ownerId);
        paletteBoardRemover.remove(ownerId);
        PixelCanvas pixelCanvas = new PixelCanvas(
                canvasBlockWidth * pixelsPerBlock,
                canvasBlockHeight * pixelsPerBlock,
                canvasBlockWidth,
                canvasBlockHeight,
                mapSize,
                drawScale,
                0,
                0,
                backgroundColor
        );
        PlayerCanvas canvas = canvasLifecycle.create(ownerId, pixelCanvas, origin, facing, right, canvasCreator);
        canvasMapSender.accept(canvas);
        layerDisplayUpdater.accept(canvas);
        return canvasHandleFactory.apply(canvas);
    }

    public boolean remove(UUID ownerId) {
        editingArtworkCleaner.remove(ownerId);
        PlayerCanvas canvas = canvasLifecycle.remove(ownerId);
        if (canvas == null) {
            return false;
        }

        layerDisplayRemover.accept(ownerId);
        strokeStateCleaner.accept(ownerId);
        paletteBoardRemover.remove(ownerId);
        drawingSessionCanvasRemover.accept(ownerId);
        advancedSessionCanvasRemover.accept(ownerId);
        canvasMapCleaner.accept(ownerId);
        layerOpacityLockCleaner.accept(ownerId);

        Player owner = onlinePlayerResolver.apply(ownerId);
        if (owner != null) {
            if (inventoryRestoreDeferrer.test(ownerId)) {
                deferredInventoryRestorer.accept(ownerId);
            } else {
                inventoryRestorer.accept(owner);
            }
            strayToolCleaner.accept(owner);
        }
        return true;
    }

    public boolean clear(UUID ownerId) {
        PlayerCanvas canvas = canvasLifecycle.canvas(ownerId);
        if (canvas == null) {
            return false;
        }
        undoPusher.accept(canvas);
        canvas.pixelCanvas().clearPixels();
        canvasMapSender.accept(canvas);
        layerDisplayUpdater.accept(canvas);
        return true;
    }

    public Optional<PaintCanvas> canvas(UUID ownerId) {
        PlayerCanvas canvas = canvasLifecycle.canvas(ownerId);
        return canvas == null ? Optional.empty() : Optional.of(canvasHandleFactory.apply(canvas));
    }

    public boolean hasCanvas(UUID ownerId) {
        return canvasLifecycle.hasCanvas(ownerId);
    }

    @FunctionalInterface
    public interface PaletteBoardRemover {
        boolean remove(UUID ownerId);
    }

    @FunctionalInterface
    public interface CanvasRemover {
        void remove(UUID ownerId);
    }

}
