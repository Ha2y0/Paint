package org.ha2yo.paint.runtime;

import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.CanvasLookService;
import org.ha2yo.paint.service.CanvasMapRenderService;
import org.ha2yo.paint.service.CanvasMapSyncService;
import org.ha2yo.paint.service.LayerPanelService;
import org.ha2yo.paint.service.PaintWindowPlacementService;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.workflow.CanvasWorkflowService;
import org.ha2yo.paint.workflow.LayerWorkflowService;
import org.ha2yo.paint.workflow.PaletteLayerWorkflowService;
import org.ha2yo.paint.workflow.PaletteWorkflowService;

public final class CanvasRuntime {
    public final CanvasLookService canvasLookService = new CanvasLookService();
    public final PaintWindowPlacementService paintWindows = new PaintWindowPlacementService();
    public CanvasLifecycleService canvasLifecycle;
    public CanvasMapSyncService canvasMaps;
    public CanvasMapRenderService canvasMapRenderer;
    public LayerPanelService layerPanels;
    public PaletteBoardService paletteBoards;
    public CanvasWorkflowService canvasWorkflow;
    public PaletteWorkflowService paletteWorkflow;
    public PaletteLayerWorkflowService paletteLayerWorkflow;
    public LayerWorkflowService layerWorkflow;
}