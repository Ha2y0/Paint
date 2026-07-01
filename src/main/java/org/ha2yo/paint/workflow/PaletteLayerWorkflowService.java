package org.ha2yo.paint.workflow;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.service.CanvasLookService;
import org.ha2yo.paint.service.CanvasLookService.CanvasPlaneHit;
import org.ha2yo.paint.service.LayerPanelService;
import org.ha2yo.paint.service.LayerPanelService.LayerControlLook;
import org.ha2yo.paint.service.PaletteBoardService.PaletteLook;

import java.util.UUID;
import java.util.function.Supplier;

public final class PaletteLayerWorkflowService {
    private final Supplier<PaletteWorkflowService> paletteWorkflow;
    private final Supplier<LayerWorkflowService> layerWorkflow;
    private final CanvasLookService canvasLookService;
    private final double layerLookDistance;

    public PaletteLayerWorkflowService(
            Supplier<PaletteWorkflowService> paletteWorkflow,
            Supplier<LayerWorkflowService> layerWorkflow,
            CanvasLookService canvasLookService,
            double layerLookDistance
    ) {
        this.paletteWorkflow = paletteWorkflow;
        this.layerWorkflow = layerWorkflow;
        this.canvasLookService = canvasLookService;
        this.layerLookDistance = layerLookDistance;
    }

    public void toggleActiveLayer(Player player) {
        LayerWorkflowService workflow = layerWorkflow.get();
        if (workflow != null) {
            workflow.toggleActiveLayer(player);
        }
    }

    public void updateLayerDisplays(PlayerCanvas canvas) {
        LayerWorkflowService workflow = layerWorkflow.get();
        if (workflow != null) {
            workflow.updateDisplays(canvas);
        }
    }

    public void removeLayerDisplays(UUID ownerId) {
        LayerWorkflowService workflow = layerWorkflow.get();
        if (workflow != null) {
            workflow.removeDisplays(ownerId);
        }
    }

    public void syncLayerVisibility(Player viewer) {
        LayerWorkflowService workflow = layerWorkflow.get();
        if (workflow != null) {
            workflow.syncVisibility(viewer);
        }
    }

    public PlayerCanvas canvasByLayerDisplay(UUID displayId) {
        LayerWorkflowService workflow = layerWorkflow.get();
        return workflow == null ? null : workflow.canvasByDisplay(displayId);
    }

    public LayerControlLook lookedLayerControl(Player player) {
        LayerWorkflowService workflow = layerWorkflow.get();
        return workflow == null ? null : workflow.lookedControl(player);
    }

    public LayerControlLook lookedLayerControl(Player player, PlayerCanvas canvas) {
        LayerWorkflowService workflow = layerWorkflow.get();
        return workflow == null ? null : workflow.lookedControl(player, canvas);
    }

    public LayerPanelService.LayerPlaneHit layerPlaneHit(Player player, PlayerCanvas canvas) {
        CanvasPlaneHit hit = canvasLookService.planeHit(player, canvas, layerLookDistance);
        return hit == null ? null : new LayerPanelService.LayerPlaneHit(hit.u(), hit.v(), hit.distance());
    }

    public void handleLayerControlClick(Player player, LayerControlLook look) {
        LayerWorkflowService workflow = layerWorkflow.get();
        if (workflow != null) {
            workflow.handleControlClick(player, look);
        }
    }

    public boolean hasLayerOpacityInteractionLock(Player player) {
        LayerWorkflowService workflow = layerWorkflow.get();
        return workflow != null && workflow.hasOpacityInteractionLock(player);
    }

    public void openPaletteBoard(Player player) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        if (workflow != null) {
            workflow.openBoard(player);
        }
    }

    public void sendPaletteMaps(PaletteBoard board) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        if (workflow != null) {
            workflow.sendMaps(board);
        }
    }

    public PaletteLook lookedPaletteBoard(Player player) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        return workflow == null ? null : workflow.lookedBoard(player);
    }

    public PaletteLook lookedPaletteBoard(Player player, PaletteBoard board) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        return workflow == null ? null : workflow.lookedBoard(player, board);
    }

    public void handlePaletteBoardClick(Player player, PaletteBoard board, PaletteLook look) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        if (workflow != null) {
            workflow.handleBoardClick(player, board, look);
        }
    }

    public boolean removePaletteBoard(UUID ownerId) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        return workflow != null && workflow.removeBoard(ownerId);
    }

    public void handlePaletteInventoryClick(Player player, ItemStack item) {
        PaletteWorkflowService workflow = paletteWorkflow.get();
        if (workflow != null) {
            workflow.handleInventoryClick(player, item);
        }
    }
}
