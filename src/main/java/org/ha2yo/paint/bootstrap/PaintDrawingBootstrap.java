package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.workflow.AdvancedToolWorkflowService;
import org.ha2yo.paint.workflow.InteractionToolWorkflowService;
import org.ha2yo.paint.workflow.PaintingInteractionWorkflowService;

final class PaintDrawingBootstrap {
    private PaintDrawingBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var panelRuntime = runtime.panel();
        drawingRuntime.advancedToolWorkflow = new AdvancedToolWorkflowService(
                c.plugin(),
                drawingRuntime.advancedToolSessions,
                drawingRuntime.drawingService,
                drawingRuntime.selectedColors,
                drawingRuntime.brushRadii,
                PaintApplication.DEFAULT_BRUSH_RADIUS,
                coreRuntime.featureService::canvasById,
                (player, canvas) -> canvasRuntime.canvasLookService.lookedCanvasExtended(player, canvas, 10.0D),
                (player, canvas) -> canvasRuntime.canvasLookService.lookedCanvasClamped(player, canvas, 10.0D),
                player -> canvasRuntime.canvasLookService.lookedShapeCanvas(
                        player,
                        canvasRuntime.canvasLifecycle.canvases(),
                        10.0D,
                        PaintApplication.ADVANCED_SHAPE_PLANE_MARGIN_BLOCKS
                ),
                coreRuntime.featureService::selectedTool,
                coreRuntime.toolItems::isPixelPainterTool,
                drawingRuntime.drawingSessions::stop,
                coreRuntime.featureService::pushUndo,
                canvasRuntime.canvasMaps::send,
                canvasRuntime.canvasMaps::sendTo
        );
        panelRuntime.interactionToolWorkflow = new InteractionToolWorkflowService(
                c.plugin(),
                drawingRuntime.advancedToolWorkflow,
                drawingRuntime.drawingSessions,
                drawingRuntime.selectedTools
        );
        drawingRuntime.paintingInteractions = new PaintingInteractionWorkflowService(
                drawingRuntime.drawingService,
                drawingRuntime.drawingSessions,
                drawingRuntime.drawingInteractions,
                coreRuntime.toolItems,
                canvasRuntime.canvasLifecycle,
                canvasRuntime.canvasLookService,
                drawingRuntime.selectedColors,
                drawingRuntime.brushRadii,
                PaintApplication.DEFAULT_BRUSH_RADIUS,
                PaintApplication.PAINT_INTERVAL_MILLIS,
                coreRuntime.featureService::selectedTool,
                canvasRuntime.canvasMaps::send,
                (playerId, color) -> {
                    if (canvasRuntime.paletteBoards != null) {
                        canvasRuntime.paletteBoards.updateSelectedColor(playerId, color);
                    }
                },
                canvasRuntime.paletteLayerWorkflow::updateLayerDisplays
        );
    }
}
