package org.ha2yo.paint.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ha2yo.paint.mode.ArtworkPlacementModeService;
import org.ha2yo.paint.mode.CanvasPlacementModeService;
import org.ha2yo.paint.mode.ExhibitRemovalModeService;
import org.ha2yo.paint.model.session.CanvasSize;
import org.ha2yo.paint.service.PlacementPreviewService;
import org.ha2yo.paint.workflow.ExhibitRemovalWorkflowService;
import org.ha2yo.paint.workflow.PlacementModeWorkflowService;
import org.ha2yo.paint.workflow.PlacementUiWorkflowService;

public final class PlacementRuntime {
    public final Map<UUID, CanvasSize> pendingMenuCanvasSizes = new HashMap<>();
    public final Set<UUID> pendingCanvasRemoveConfirms = new HashSet<>();
    public ArtworkPlacementModeService artworkPlacementModes;
    public CanvasPlacementModeService canvasPlacementModes;
    public ExhibitRemovalModeService exhibitRemovalModes;
    public PlacementPreviewService placementPreviews;
    public ExhibitRemovalWorkflowService exhibitRemovalWorkflow;
    public PlacementModeWorkflowService placementModeWorkflow;
    public PlacementUiWorkflowService placementUiWorkflow;
}