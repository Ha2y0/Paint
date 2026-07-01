package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.workflow.ToolEventWorkflowService;

final class PaintToolInteractionBootstrap {
    private PaintToolInteractionBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var panelRuntime = runtime.panel();
        panelRuntime.toolEventWorkflow = new ToolEventWorkflowService(
                c.plugin(),
                panelRuntime.toolModeGuards,
                coreRuntime.toolItems,
                drawingRuntime.advancedToolSessions,
                drawingRuntime.selectedTools,
                drawingRuntime.advancedToolModes,
                panelRuntime.inventoryToolWorkflow::rememberToolSlot,
                panelRuntime.inventoryToolWorkflow::rememberedToolSlot,
                playerId -> artworkRuntime.artworkGalleryWorkflow != null && artworkRuntime.artworkGalleryWorkflow.hasSession(playerId),
                coreRuntime.featureService::isPaintMenuTitle,
                coreRuntime.featureService::handlePaintMenuClick,
                coreRuntime.featureService::handleCanvasSizeMenuClick,
                coreRuntime.featureService::handleArtworkMenuClick,
                canvasRuntime.paletteLayerWorkflow::handlePaletteInventoryClick,
                canvasRuntime.paletteLayerWorkflow::toggleActiveLayer,
                coreRuntime.featureService::finishAdvancedHold,
                coreRuntime.featureService::stopDrawing,
                coreRuntime.featureService::clearStrokeState,
                coreRuntime.featureService::clearAdvancedPreview,
                coreRuntime.toolItems::giveToolItems
        );
    }
}
