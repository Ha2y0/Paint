package org.ha2yo.paint.runtime;

import org.ha2yo.paint.interaction.PaintInteractionRouter;
import org.ha2yo.paint.service.PaintInventoryModeService;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.PaintPanelService;
import org.ha2yo.paint.service.ManualStationService;
import org.ha2yo.paint.workflow.CanvasEventWorkflowService;
import org.ha2yo.paint.workflow.InteractionToolWorkflowService;
import org.ha2yo.paint.workflow.InventoryToolWorkflowService;
import org.ha2yo.paint.workflow.ManualStationWorkflowService;
import org.ha2yo.paint.workflow.PaintCommandWorkflowService;
import org.ha2yo.paint.workflow.PaintPanelWorkflowService;
import org.ha2yo.paint.workflow.PaintTickWorkflowService;
import org.ha2yo.paint.workflow.PlayerLifecycleWorkflowService;
import org.ha2yo.paint.workflow.ToolEventWorkflowService;
import org.ha2yo.paint.workflow.ToolModeGuardService;

public final class PanelRuntime {
    public final PaintInventoryModeService inventoryModes = new PaintInventoryModeService();
    public PaintPanelService paintPanels;
    public PaintPanelModeService paintPanelModes;
    public InventoryToolWorkflowService inventoryToolWorkflow;
    public InteractionToolWorkflowService interactionToolWorkflow;
    public PaintPanelWorkflowService paintPanelWorkflow;
    public ManualStationService manualStations;
    public ManualStationWorkflowService manualStationWorkflow;
    public PaintCommandWorkflowService paintCommandWorkflow;
    public PlayerLifecycleWorkflowService playerLifecycleWorkflow;
    public PaintTickWorkflowService paintTickWorkflow;
    public ToolModeGuardService toolModeGuards;
    public ToolEventWorkflowService toolEventWorkflow;
    public CanvasEventWorkflowService canvasEventWorkflow;
    public PaintInteractionRouter interactionRouter;
}
