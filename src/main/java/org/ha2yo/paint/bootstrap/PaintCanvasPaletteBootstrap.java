package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkImageService;
import org.ha2yo.paint.service.ArtworkSaveDialogService;
import org.ha2yo.paint.service.ArtworkStorageService;
import org.ha2yo.paint.service.CanvasMapRenderService;
import org.ha2yo.paint.service.CanvasMapSyncService;
import org.ha2yo.paint.service.LayerInteractionService;
import org.ha2yo.paint.service.LayerPanelService;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.workflow.CanvasWorkflowService;
import org.ha2yo.paint.workflow.LayerWorkflowService;
import org.ha2yo.paint.workflow.PaletteWorkflowService;

final class PaintCanvasPaletteBootstrap {
    private PaintCanvasPaletteBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var panelRuntime = runtime.panel();
        artworkRuntime.artworkStorage = new ArtworkStorageService(c.plugin(), PaintApplication.DRAW_SCALE, PaintApplication.BACKGROUND_COLOR);
        artworkRuntime.artworkStorage.load();
        artworkRuntime.artworkSaveDialogs = new ArtworkSaveDialogService(c.plugin(), PaintApplication.ARTWORK_NAME_INPUT_KEY);
        artworkRuntime.artworkImages = new ArtworkImageService(PaintApplication.MAP_SIZE, PaintApplication.BACKGROUND_COLOR);
        artworkRuntime.artworkDisplays = new ArtworkDisplayService(c.plugin(), PaintApplication.MAP_SIZE, PaintApplication.BACKGROUND_COLOR, coreRuntime.featureService::shaderRgbEnabled);
        artworkRuntime.artworkDisplays.load();
        canvasRuntime.canvasMaps = new CanvasMapSyncService(
                c.plugin(),
                coreRuntime.featureService::canvasById,
                PaintApplication.CANVAS_MAP_TILE_SEND_BUDGET,
                PaintApplication.MAX_CANVAS_MAP_TILE_SEND_BUDGET,
                PaintApplication.CANVAS_MAP_SEND_FLUSH_BUDGET,
                PaintApplication.OBSERVER_CANVAS_MAP_SEND_INTERVAL_MILLIS
        );
        canvasRuntime.canvasMapRenderer = new CanvasMapRenderService(
                c.plugin(),
                PaintApplication.BACKGROUND_COLOR,
                coreRuntime.featureService::shaderRgbEnabled,
                coreRuntime.featureService::canvasOwnerName,
                player -> drawingRuntime.advancedToolWorkflow == null ? null : drawingRuntime.advancedToolWorkflow.overlay(player)
        );
        canvasRuntime.layerPanels = new LayerPanelService(
                c.plugin(),
                PaintApplication.CANVAS_LAYER_DISPLAY_TAG,
                PaintApplication.LAYER_OPACITY_INTERACTION_LOCK_MILLIS,
                canvasRuntime.paletteLayerWorkflow::layerPlaneHit
        );
        canvasRuntime.layerWorkflow = new LayerWorkflowService(
                canvasRuntime.layerPanels,
                new LayerInteractionService(PaintApplication.MAX_HISTORY_SIZE),
                canvasRuntime.canvasLifecycle::canvases,
                coreRuntime.featureService::canvasById,
                coreRuntime.featureService::stopDrawing,
                canvasRuntime.canvasMaps::send
        );
        canvasRuntime.paletteBoards = new PaletteBoardService(
                c.plugin(),
                PaintApplication.PALETTE_FRAME_TAG,
                coreRuntime.featureService::shaderRgbEnabled,
                blockKey -> canvasRuntime.canvasLifecycle != null && canvasRuntime.canvasLifecycle.hasBlock(blockKey)
        );
        canvasRuntime.paletteWorkflow = new PaletteWorkflowService(
                canvasRuntime.paletteBoards,
                () -> runtime.placement().placementPreviews,
                coreRuntime.toolItems,
                coreRuntime.paletteColorKey,
                coreRuntime.brushSizeDeltaKey,
                coreRuntime.brushSizeValueKey,
                drawingRuntime.selectedColors,
                drawingRuntime.brushRadii,
                drawingRuntime.paletteModes,
                drawingRuntime.lastPaletteRightClickTimes,
                PaintApplication.DEFAULT_BRUSH_RADIUS,
                PaintApplication.MAX_BRUSH_RADIUS,
                PaintApplication.PALETTE_PLACEMENT_ARM_DELAY_MILLIS,
                ownerId -> drawingRuntime.paletteAccessOwners.contains(ownerId)
                        || (canvasRuntime.canvasLifecycle != null && canvasRuntime.canvasLifecycle.hasCanvas(ownerId)),
                coreRuntime.featureService::stopDrawing,
                coreRuntime.featureService::shaderRgbEnabled
        );
        canvasRuntime.canvasWorkflow = new CanvasWorkflowService(
                canvasRuntime.canvasLifecycle,
                PaintApplication.CANVAS_PIXELS_PER_BLOCK,
                PaintApplication.MAP_SIZE,
                PaintApplication.DRAW_SCALE,
                PaintApplication.BACKGROUND_COLOR,
                PaintApplication.DEFAULT_CANVAS_BLOCK_WIDTH,
                PaintApplication.DEFAULT_CANVAS_BLOCK_HEIGHT,
                coreRuntime.featureService::clampCanvasBlockSize,
                canvasRuntime.paletteLayerWorkflow::removePaletteBoard,
                artworkRuntime.editingArtworkIds::remove,
                canvasRuntime.canvasMaps::send,
                canvasRuntime.paletteLayerWorkflow::updateLayerDisplays,
                coreRuntime.featureService::pushUndo,
                canvasRuntime.paletteLayerWorkflow::removeLayerDisplays,
                coreRuntime.featureService::clearStrokeState,
                drawingRuntime.drawingSessions::removeCanvas,
                drawingRuntime.advancedToolSessions::removeCanvas,
                ownerId -> {
                    if (canvasRuntime.canvasMaps != null) {
                        canvasRuntime.canvasMaps.clearCanvas(ownerId);
                    }
                },
                ownerId -> {
                    if (canvasRuntime.layerWorkflow != null) {
                        canvasRuntime.layerWorkflow.clearOpacityLock(ownerId);
                    }
                },
                c.plugin().getServer()::getPlayer,
                panelRuntime.inventoryToolWorkflow::shouldDeferCanvasOwnerInventoryRestore,
                panelRuntime.inventoryToolWorkflow::deferCanvasOwnerInventoryRestore,
                panelRuntime.inventoryToolWorkflow::restoreInventory,
                panelRuntime.inventoryToolWorkflow::clearStrayPaintTools,
                canvasRuntime.canvasMapRenderer::createMapItem,
                PaintApplication.CanvasHandle::new
        );
    }
}
