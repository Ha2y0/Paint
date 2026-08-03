package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.service.FreeModeDraftStorageService;
import org.ha2yo.paint.service.ManualStationDraftStorageService;
import org.ha2yo.paint.service.ManualStationService;
import org.ha2yo.paint.workflow.FreeModeDraftWorkflowService;
import org.ha2yo.paint.workflow.ManualStationWorkflowService;
import org.ha2yo.paint.workflow.PaintCommandWorkflowService;
import org.ha2yo.paint.workflow.PaintPanelWorkflowService;

final class PaintPanelCommandBootstrap {
    private PaintPanelCommandBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        panelRuntime.manualStations = new ManualStationService(c.plugin());
        var manualDrafts = new ManualStationDraftStorageService(c.plugin());
        panelRuntime.manualStationWorkflow = new ManualStationWorkflowService(
                c.plugin(),
                panelRuntime.manualStations,
                coreRuntime.previewActionKey,
                PaintApplication.ARTWORK_PLACEMENT_TOOL_SLOT,
                PaintApplication.PREVIEW_ACTION_MANUAL_STATION,
                canvasRuntime.canvasLifecycle,
                canvasRuntime.canvasWorkflow,
                canvasRuntime.canvasMapRenderer,
                canvasRuntime.paintWindows,
                panelRuntime.paintPanels,
                panelRuntime.paintPanelModes,
                artworkRuntime.artworkGalleryWorkflow,
                artworkRuntime.artworkSaveWorkflow,
                manualDrafts,
                canvasRuntime.paletteWorkflow,
                () -> placementRuntime.placementPreviews,
                canvasRuntime.canvasMaps::send,
                canvasRuntime.paletteLayerWorkflow::updateLayerDisplays,
                artworkRuntime.editingArtworkIds::get,
                artworkRuntime.editingArtworkIds::put,
                player -> c.plugin().getConfig().getBoolean("free-mode", true),
                panelRuntime.inventoryToolWorkflow::giveTools,
                PaintApplication.CANVAS_PIXELS_PER_BLOCK,
                PaintApplication.MAP_SIZE,
                PaintApplication.DRAW_SCALE,
                PaintApplication.BACKGROUND_COLOR,
                coreRuntime.featureService::clampCanvasBlockSize
        );
        panelRuntime.manualStationWorkflow.loadAndSpawn();
        panelRuntime.freeModeDraftWorkflow = new FreeModeDraftWorkflowService(
                c.plugin(),
                new FreeModeDraftStorageService(
                        c.plugin(),
                        PaintApplication.CANVAS_PIXELS_PER_BLOCK,
                        PaintApplication.MAX_CANVAS_BLOCK_SIZE
                ),
                canvasRuntime.canvasLifecycle,
                canvasRuntime.canvasWorkflow,
                placementRuntime.placementUiWorkflow,
                canvasRuntime.canvasMaps::send,
                canvasRuntime.paletteLayerWorkflow::updateLayerDisplays,
                artworkRuntime.editingArtworkIds::get,
                artworkRuntime.editingArtworkIds::put,
                player -> c.plugin().getConfig().getBoolean("free-mode", true),
                PaintApplication.CANVAS_PIXELS_PER_BLOCK,
                PaintApplication.MAX_CANVAS_BLOCK_SIZE
        );
        panelRuntime.paintPanelWorkflow = new PaintPanelWorkflowService(
                coreRuntime.paintMenus,
                panelRuntime.paintPanels,
                placementRuntime.pendingMenuCanvasSizes,
                placementRuntime.pendingCanvasRemoveConfirms,
                PaintApplication.DEFAULT_CANVAS_BLOCK_WIDTH,
                PaintApplication.DEFAULT_CANVAS_BLOCK_HEIGHT,
                PaintApplication.MAX_CANVAS_BLOCK_SIZE,
                coreRuntime.featureService::canCreateNewCanvas,
                panelRuntime.freeModeDraftWorkflow::hasDraft,
                coreRuntime.featureService::clearCanvas,
                panelRuntime.freeModeDraftWorkflow::removeCanvasOrDraft,
                coreRuntime.featureService::handleSaveArtwork,
                player -> {
                    if (artworkRuntime.artworkGalleryWorkflow != null) {
                        artworkRuntime.artworkGalleryWorkflow.start(player, false);
                    }
                },
                placementRuntime.placementUiWorkflow::startExhibitRemoval,
                panelRuntime.freeModeDraftWorkflow::startCanvasPlacement,
                panelRuntime.inventoryToolWorkflow::startPaintPanelModeIfNeeded,
                panelRuntime.inventoryToolWorkflow::endPaintPanelMode,
                coreRuntime.featureService::clearPaintPanelOnly,
                coreRuntime.featureService::paintPanelAnchor,
                canvasRuntime.paintWindows::cardinalFace,
                panelRuntime.manualStationWorkflow
        );
        panelRuntime.paintCommandWorkflow = new PaintCommandWorkflowService(
                panelRuntime.paintPanelWorkflow,
                panelRuntime.paintPanels,
                panelRuntime.paintPanelModes,
                artworkRuntime.artworkGalleryWorkflow,
                artworkRuntime.artworkSaveWorkflow,
                artworkRuntime.artworkStorage,
                artworkRuntime.artworkDisplays,
                canvasRuntime.canvasWorkflow,
                placementRuntime.placementModeWorkflow,
                canvasRuntime.paletteWorkflow,
                panelRuntime.manualStationWorkflow,
                panelRuntime.freeModeDraftWorkflow,
                drawingRuntime.palette,
                drawingRuntime.selectedColors,
                PaintApplication.DEFAULT_CANVAS_BLOCK_WIDTH,
                PaintApplication.DEFAULT_CANVAS_BLOCK_HEIGHT,
                PaintApplication.MAX_CANVAS_BLOCK_SIZE,
                PaintApplication.MAX_BRUSH_RADIUS
        );
    }
}
