package org.ha2yo.paint.bootstrap;

import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.ha2yo.paint.PaintApplication;

public final class PaintShutdownService {
    private PaintShutdownService() {
    }
    public static void disable(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        if (coreRuntime.paintTask != null) {
            coreRuntime.paintTask.cancel();
            coreRuntime.paintTask = null;
        }
        c.stopCanvasMapSendTask();
        for (Player player : c.plugin().getServer().getOnlinePlayers()) {
            if (canvasRuntime.paletteWorkflow != null) {
                canvasRuntime.paletteWorkflow.endPlacement(player, false);
            }
            if (panelRuntime.manualStationWorkflow != null) {
                panelRuntime.manualStationWorkflow.endPlacement(player, false);
            }
            if (placementRuntime.placementUiWorkflow != null) {
                placementRuntime.placementUiWorkflow.endCanvas(player, false);
                placementRuntime.placementUiWorkflow.endArtwork(player, false);
                placementRuntime.placementUiWorkflow.endExhibitRemoval(player, false);
            }
            if (panelRuntime.inventoryToolWorkflow != null) {
                panelRuntime.inventoryToolWorkflow.endPaintPanelMode(player, true);
                panelRuntime.inventoryToolWorkflow.restoreArtworkPlacementInventory(player);
                panelRuntime.inventoryToolWorkflow.restoreExhibitRemovalInventory(player);
                panelRuntime.inventoryToolWorkflow.restoreInventory(player);
            }
        }
        placementRuntime.placementUiWorkflow.clearCanvasPreviews();
        placementRuntime.placementUiWorkflow.clearArtworkPreviews();
        placementRuntime.placementUiWorkflow.clearExhibitRemovalModes();
        if (canvasRuntime.paletteWorkflow != null) {
            canvasRuntime.paletteWorkflow.clearPlacementPreviews();
        }
        if (panelRuntime.manualStationWorkflow != null) {
            panelRuntime.manualStationWorkflow.clearPlacementPreviews();
        }
        if (artworkRuntime.artworkDisplays != null) {
            artworkRuntime.artworkDisplays.clearDisplays();
        }
        if (artworkRuntime.artworkPreviews != null) {
            artworkRuntime.artworkPreviews.clearAll();
        }
        if (panelRuntime.paintPanels != null) {
            panelRuntime.paintPanels.clearAll();
        }
        for (UUID ownerId : new ArrayList<>(canvasRuntime.canvasLifecycle.ownerIds())) {
            c.removeCanvas(ownerId);
        }
        if (canvasRuntime.paletteBoards != null) {
            canvasRuntime.paletteBoards.clearAll();
        }
        panelRuntime.inventoryModes.clearAll();
        drawingRuntime.selectedColors.clear();
        drawingRuntime.selectedTools.clear();
        drawingRuntime.advancedToolModes.clear();
        drawingRuntime.rememberedBasicToolSlots.clear();
        drawingRuntime.rememberedAdvancedToolSlots.clear();
        drawingRuntime.brushRadii.clear();
        drawingRuntime.paletteModes.clear();
        drawingRuntime.lastPaletteRightClickTimes.clear();
        drawingRuntime.paletteAccessOwners.clear();
        drawingRuntime.drawingSessions.clearAll();
        if (canvasRuntime.layerWorkflow != null) {
            canvasRuntime.layerWorkflow.clearAll();
        }
        drawingRuntime.advancedToolSessions.clearAll();
        if (canvasRuntime.canvasMaps != null) {
            canvasRuntime.canvasMaps.clearAll();
        }
        if (panelRuntime.paintPanelWorkflow != null) {
            panelRuntime.paintPanelWorkflow.clearAllPending();
        }
        artworkRuntime.editingArtworkIds.clear();
        if (artworkRuntime.artworkGalleryWorkflow != null) {
            artworkRuntime.artworkGalleryWorkflow.clearAll();
        }
        if (placementRuntime.placementModeWorkflow != null) {
            placementRuntime.placementModeWorkflow.clearInventories();
        }
        if (panelRuntime.paintPanelModes != null) {
            panelRuntime.paintPanelModes.clearAll();
        }
        coreRuntime.featureService.clearStrokeState();
    }
}
