package org.ha2yo.paint.workflow;

import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PlacementUiWorkflowService {
    private final Supplier<PlacementModeWorkflowService> placementModeWorkflow;

    public PlacementUiWorkflowService(Supplier<PlacementModeWorkflowService> placementModeWorkflow) {
        this.placementModeWorkflow = placementModeWorkflow;
    }

    public boolean isArtworkActive(UUID playerId) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.isArtworkActive(playerId);
    }

    public boolean isCanvasActive(UUID playerId) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.isCanvasActive(playerId);
    }

    public boolean isExhibitRemovalActive(UUID playerId) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.isExhibitRemovalActive(playerId);
    }

    public void startArtwork(Player player, PaintArtwork artwork) {
        startArtwork(player, artwork, ExhibitFrameStyle.NONE);
    }

    public void startArtwork(Player player, PaintArtwork artwork, ExhibitFrameStyle frameStyle) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.startArtwork(player, artwork, frameStyle);
        }
    }

    public void startArtworkFrameSelection(
            Player player,
            PaintArtwork artwork,
            ExhibitFrameStyle frameStyle,
            Consumer<ExhibitFrameStyle> frameStyleHandler,
            Consumer<Player> frameSelectionEndHandler
    ) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.startArtworkFrameSelection(player, artwork, frameStyle, frameStyleHandler, frameSelectionEndHandler);
        }
    }

    public void startCanvas(Player player, int blockWidth, int blockHeight) {
        startCanvas(player, blockWidth, blockHeight, null);
    }

    public void startCanvas(Player player, int blockWidth, int blockHeight, Consumer<Player> placementCompletion) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.startCanvas(player, blockWidth, blockHeight, placementCompletion);
        }
    }

    public void startExhibitRemoval(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.startExhibitRemoval(player);
        }
    }

    public boolean handleArtworkSwing(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.handleArtworkSwing(player);
    }

    public boolean handleCanvasSwing(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.handleCanvasSwing(player);
    }

    public boolean handleExhibitRemovalSwing(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.handleExhibitRemovalSwing(player);
    }

    public boolean handleArtworkInteract(Player player, Action action) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.handleArtworkInteract(player, action);
    }

    public boolean handleCanvasInteract(Player player, Action action) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.handleCanvasInteract(player, action);
    }

    public boolean handleExhibitRemovalInteract(Player player, Action action) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.handleExhibitRemovalInteract(player, action);
    }

    public boolean confirmArtwork(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.confirmArtwork(player);
    }

    public boolean confirmCanvas(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.confirmCanvas(player);
    }

    public boolean confirmExhibitRemoval(Player player) {
        PlacementModeWorkflowService workflow = workflow();
        return workflow != null && workflow.confirmExhibitRemoval(player);
    }

    public void endArtwork(Player player, boolean sendCancelMessage) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.endArtwork(player, sendCancelMessage);
        }
    }

    public void endCanvas(Player player, boolean sendCancelMessage) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.endCanvas(player, sendCancelMessage);
        }
    }

    public void endExhibitRemoval(Player player, boolean sendCancelMessage) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.endExhibitRemoval(player, sendCancelMessage);
        }
    }

    public void clearCanvasPreviews() {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.clearCanvasPreviews();
        }
    }

    public void clearArtworkPreviews() {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.clearArtworkPreviews();
        }
    }

    public void clearExhibitRemovalModes() {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.clearExhibitRemovalModes();
        }
    }

    public void syncCanvasVisibility(Player viewer) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.syncCanvasVisibility(viewer);
        }
    }

    public void syncArtworkVisibility(Player viewer) {
        PlacementModeWorkflowService workflow = workflow();
        if (workflow != null) {
            workflow.syncArtworkVisibility(viewer);
        }
    }

    private PlacementModeWorkflowService workflow() {
        return placementModeWorkflow.get();
    }
}
