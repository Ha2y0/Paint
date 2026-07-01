package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.service.ArtworkGalleryService;
import org.ha2yo.paint.service.ArtworkPreviewService;
import org.ha2yo.paint.workflow.ArtworkGalleryWorkflowService;
import org.ha2yo.paint.workflow.ArtworkSaveWorkflowService;

final class PaintGalleryBootstrap {
    private PaintGalleryBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        artworkRuntime.artworkPreviews = new ArtworkPreviewService(c.plugin(), PaintApplication.MAP_SIZE, PaintApplication.BACKGROUND_COLOR, coreRuntime.featureService::shaderRgbEnabled);
        artworkRuntime.artworkGalleries = new ArtworkGalleryService(
                c.plugin(),
                artworkRuntime.artworkStorage,
                artworkRuntime.artworkPreviews,
                PaintApplication.ARTWORK_PREVIEW_INTERACTION_DISTANCE,
                PaintApplication.ARTWORK_PREVIEW_OPEN_GRACE_MILLIS,
                PaintApplication.ARTWORK_PREVIEW_CLICK_COOLDOWN_MILLIS
        );
        artworkRuntime.artworkGalleryWorkflow = new ArtworkGalleryWorkflowService(
                c.plugin(),
                artworkRuntime.artworkGalleries,
                artworkRuntime.artworkStorage,
                artworkRuntime.artworkImages,
                coreRuntime.paintMenus,
                PaintApplication.MAP_SIZE,
                PaintApplication.CANVAS_PIXELS_PER_BLOCK,
                PaintApplication.MAX_CANVAS_BLOCK_SIZE,
                PaintApplication.ARTWORK_SEARCH_INPUT_KEY,
                PaintApplication.BACKGROUND_COLOR,
                coreRuntime.featureService::artworkGalleryAnchor,
                canvasRuntime.paintWindows::cardinalFace,
                panelRuntime.inventoryToolWorkflow::startPaintPanelModeIfNeeded,
                player -> coreRuntime.featureService.endPaintPanelModeIfActive(player, panelRuntime.inventoryToolWorkflow),
                placementRuntime.placementUiWorkflow::startArtwork,
                placementRuntime.placementUiWorkflow::startArtworkFrameSelection,
                placementRuntime.placementUiWorkflow::startCanvas,
                canvasRuntime.canvasLifecycle::canvas,
                artworkRuntime.editingArtworkIds::put,
                canvasRuntime.canvasMaps::send,
                canvasRuntime.paletteLayerWorkflow::updateLayerDisplays,
                coreRuntime.featureService::shaderRgbEnabled
        );
        artworkRuntime.artworkSaveWorkflow = new ArtworkSaveWorkflowService(
                c.plugin(),
                artworkRuntime.artworkSaveDialogs,
                artworkRuntime.artworkStorage,
                artworkRuntime.editingArtworkIds,
                coreRuntime.featureService::canvas,
                canvasRuntime.canvasLifecycle::canvas,
                coreRuntime.featureService::removeCanvas,
                artworkId -> {
                    if (artworkRuntime.artworkGalleryWorkflow != null) {
                        artworkRuntime.artworkGalleryWorkflow.clearCachedPreview(artworkId);
                    }
                },
                player -> {
                    if (panelRuntime.manualStationWorkflow != null) {
                        panelRuntime.manualStationWorkflow.onCanvasSaved(player);
                    }
                }
        );
    }
}
