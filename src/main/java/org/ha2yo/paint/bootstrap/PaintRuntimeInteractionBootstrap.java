package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.interaction.PaintInteractionFacade;
import org.ha2yo.paint.interaction.PaintInteractionRouter;
import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.workflow.CanvasEventWorkflowService;
import org.ha2yo.paint.workflow.PaintTickWorkflowService;
import org.ha2yo.paint.workflow.PlayerLifecycleWorkflowService;

final class PaintRuntimeInteractionBootstrap {
    private PaintRuntimeInteractionBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        PaintInteractionFacade interactionFacade = new PaintInteractionFacade(
                panelRuntime.interactionToolWorkflow,
                coreRuntime.toolItems,
                panelRuntime.paintPanels,
                panelRuntime.paintPanelModes,
                panelRuntime.paintPanelWorkflow,
                artworkRuntime.artworkPreviews,
                artworkRuntime.artworkGalleryWorkflow,
                artworkRuntime.artworkDisplays,
                canvasRuntime.paletteBoards,
                canvasRuntime.paletteWorkflow,
                canvasRuntime.canvasLifecycle,
                canvasRuntime.canvasLookService,
                canvasRuntime.layerWorkflow,
                panelRuntime.manualStationWorkflow,
                placementRuntime.placementModeWorkflow,
                drawingRuntime.selectedTools,
                drawingRuntime.advancedToolModes,
                drawingRuntime.lastPaletteRightClickTimes,
                10.0D,
                PaintApplication.ADVANCED_SHAPE_PLANE_MARGIN_BLOCKS
        );
        panelRuntime.interactionRouter = new PaintInteractionRouter(interactionFacade, drawingRuntime.paintingInteractions);
        panelRuntime.canvasEventWorkflow = new CanvasEventWorkflowService(
                panelRuntime.toolModeGuards,
                coreRuntime.toolItems,
                canvasRuntime.canvasLifecycle,
                canvasRuntime.paletteBoards,
                artworkRuntime.artworkDisplays,
                artworkRuntime.artworkPreviews,
                panelRuntime.paintPanels,
                drawingRuntime.selectedTools,
                drawingRuntime.lastPaletteRightClickTimes,
                PaintApplication.PALETTE_RIGHT_CLICK_SWING_GRACE_MILLIS,
                canvasRuntime.paletteLayerWorkflow::removePaletteBoard,
                player -> artworkRuntime.artworkGalleryWorkflow != null
                        && artworkRuntime.artworkGalleryWorkflow.handleLookedLeftClick(player),
                placementRuntime.placementUiWorkflow::handleExhibitRemovalSwing,
                placementRuntime.placementUiWorkflow::handleCanvasSwing,
                placementRuntime.placementUiWorkflow::handleArtworkSwing,
                canvasRuntime.paletteWorkflow::handlePlacementSwing,
                canvasRuntime.paletteLayerWorkflow::hasLayerOpacityInteractionLock
        );
        panelRuntime.playerLifecycleWorkflow = new PlayerLifecycleWorkflowService(
                drawingRuntime.drawingSessions,
                drawingRuntime.advancedToolSessions,
                drawingRuntime.advancedToolModes,
                drawingRuntime.rememberedBasicToolSlots,
                drawingRuntime.rememberedAdvancedToolSlots,
                drawingRuntime.lastPaletteRightClickTimes,
                artworkRuntime.editingArtworkIds,
                canvasRuntime.paletteLayerWorkflow::removePaletteBoard,
                (player, restoreInventory) -> coreRuntime.featureService.endArtworkPreview(player),
                placementRuntime.placementUiWorkflow::endCanvas,
                placementRuntime.placementUiWorkflow::endArtwork,
                canvasRuntime.paletteWorkflow::endPlacement,
                placementRuntime.placementUiWorkflow::endExhibitRemoval,
                playerId -> {
                    if (panelRuntime.paintPanelWorkflow != null) {
                        panelRuntime.paintPanelWorkflow.clearPending(playerId);
                    }
                },
                panelRuntime.inventoryToolWorkflow::endPaintPanelMode,
                panelRuntime.inventoryToolWorkflow::restoreInventory,
                coreRuntime.featureService::clearStrokeState,
                player -> {
                    if (panelRuntime.manualStationWorkflow != null) {
                        panelRuntime.manualStationWorkflow.onQuit(player);
                    }
                }
        );
        panelRuntime.paintTickWorkflow = new PaintTickWorkflowService(
                c.plugin().getServer(),
                canvasRuntime.canvasLifecycle,
                drawingRuntime.drawingSessions,
                drawingRuntime.advancedToolSessions,
                placementRuntime.placementModeWorkflow,
                panelRuntime.paintPanels,
                coreRuntime.toolItems,
                drawingRuntime.advancedToolModes,
                canvasRuntime.paletteWorkflow,
                artworkRuntime.artworkGalleryWorkflow,
                drawingRuntime.advancedToolWorkflow,
                drawingRuntime.paintingInteractions,
                PaintApplication.PAINT_PANEL_INTERACTION_DISTANCE,
                coreRuntime.featureService::clearStrokeState
        );
    }
}
