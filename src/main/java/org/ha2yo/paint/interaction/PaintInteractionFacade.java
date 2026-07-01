package org.ha2yo.paint.interaction;

import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkPreviewService;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.CanvasLookService;
import org.ha2yo.paint.service.CanvasLookService.LookResult;
import org.ha2yo.paint.service.LayerPanelService.LayerControlLook;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.PaintPanelService;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.service.PaletteBoardService.PaletteLook;
import org.ha2yo.paint.service.ToolItemService;
import org.ha2yo.paint.workflow.ArtworkGalleryWorkflowService;
import org.ha2yo.paint.workflow.InteractionToolWorkflowService;
import org.ha2yo.paint.workflow.LayerWorkflowService;
import org.ha2yo.paint.workflow.ManualStationWorkflowService;
import org.ha2yo.paint.workflow.PaintPanelWorkflowService;
import org.ha2yo.paint.workflow.PaletteWorkflowService;
import org.ha2yo.paint.workflow.PlacementModeWorkflowService;

public final class PaintInteractionFacade {
    private final InteractionToolWorkflowService interactionTools;
    private final ToolItemService toolItems;
    private final PaintPanelService paintPanels;
    private final PaintPanelModeService paintPanelModes;
    private final PaintPanelWorkflowService paintPanelWorkflow;
    private final ArtworkPreviewService artworkPreviews;
    private final ArtworkGalleryWorkflowService artworkGalleryWorkflow;
    private final ArtworkDisplayService artworkDisplays;
    private final PaletteBoardService paletteBoards;
    private final PaletteWorkflowService paletteWorkflow;
    private final CanvasLifecycleService canvasLifecycle;
    private final CanvasLookService canvasLookService;
    private final LayerWorkflowService layerWorkflow;
    private final ManualStationWorkflowService manualStationWorkflow;
    private final PlacementModeWorkflowService placementModeWorkflow;
    private final Map<UUID, Tool> selectedTools;
    private final Map<UUID, Boolean> advancedToolModes;
    private final Map<UUID, Long> lastPaletteRightClickTimes;
    private final double canvasLookDistance;
    private final double advancedShapePlaneMarginBlocks;
    public PaintInteractionFacade(
            InteractionToolWorkflowService interactionTools,
            ToolItemService toolItems,
            PaintPanelService paintPanels,
            PaintPanelModeService paintPanelModes,
            PaintPanelWorkflowService paintPanelWorkflow,
            ArtworkPreviewService artworkPreviews,
            ArtworkGalleryWorkflowService artworkGalleryWorkflow,
            ArtworkDisplayService artworkDisplays,
            PaletteBoardService paletteBoards,
            PaletteWorkflowService paletteWorkflow,
            CanvasLifecycleService canvasLifecycle,
            CanvasLookService canvasLookService,
            LayerWorkflowService layerWorkflow,
            ManualStationWorkflowService manualStationWorkflow,
            PlacementModeWorkflowService placementModeWorkflow,
            Map<UUID, Tool> selectedTools,
            Map<UUID, Boolean> advancedToolModes,
            Map<UUID, Long> lastPaletteRightClickTimes,
            double canvasLookDistance,
            double advancedShapePlaneMarginBlocks
    ) {
        this.interactionTools = interactionTools;
        this.toolItems = toolItems;
        this.paintPanels = paintPanels;
        this.paintPanelModes = paintPanelModes;
        this.paintPanelWorkflow = paintPanelWorkflow;
        this.artworkPreviews = artworkPreviews;
        this.artworkGalleryWorkflow = artworkGalleryWorkflow;
        this.artworkDisplays = artworkDisplays;
        this.paletteBoards = paletteBoards;
        this.paletteWorkflow = paletteWorkflow;
        this.canvasLifecycle = canvasLifecycle;
        this.canvasLookService = canvasLookService;
        this.layerWorkflow = layerWorkflow;
        this.manualStationWorkflow = manualStationWorkflow;
        this.placementModeWorkflow = placementModeWorkflow;
        this.selectedTools = selectedTools;
        this.advancedToolModes = advancedToolModes;
        this.lastPaletteRightClickTimes = lastPaletteRightClickTimes;
        this.canvasLookDistance = canvasLookDistance;
        this.advancedShapePlaneMarginBlocks = advancedShapePlaneMarginBlocks;
    }
    boolean isPaintPanelModeActive(UUID playerId) {
        return paintPanelModes != null && paintPanelModes.contains(playerId);
    }
    boolean isArtworkPlacementModeActive(UUID playerId) {
        return placementModeWorkflow != null && placementModeWorkflow.isArtworkActive(playerId);
    }
    boolean isCanvasPlacementModeActive(UUID playerId) {
        return placementModeWorkflow != null && placementModeWorkflow.isCanvasActive(playerId);
    }
    boolean isPalettePlacementModeActive(UUID playerId) {
        return paletteWorkflow != null && paletteWorkflow.isPlacementActive(playerId);
    }
    boolean isExhibitRemovalModeActive(UUID playerId) {
        return placementModeWorkflow != null && placementModeWorkflow.isExhibitRemovalActive(playerId);
    }
    void ensurePaintPanelTool(Player player) {
        if (paintPanelModes != null) {
            paintPanelModes.ensureTool(player);
        }
    }
    boolean handleLookedPaintPanelClick(Player player) {
        if (paintPanelModes == null) {
            return false;
        }
        PaintPanelModeService.ActionResult result = paintPanelModes.lookedAction(player, canvasLookDistance);
        return handlePaintPanelResult(player, result);
    }
    boolean handlePaintPanelClick(Player player, UUID entityId) {
        if (paintPanelModes != null) {
            PaintPanelModeService.ActionResult result = paintPanelModes.entityAction(player, entityId);
            if (handlePaintPanelResult(player, result)) {
                return true;
            }
        }
        return handleManualControlPanelClick(player, entityId);
    }
    boolean handleLookedArtworkPreviewClick(Player player) {
        return artworkGalleryWorkflow != null && artworkGalleryWorkflow.handleLookedClick(player);
    }
    boolean handleLookedArtworkPreviewLeftClick(Player player) {
        return artworkGalleryWorkflow != null && artworkGalleryWorkflow.handleLookedLeftClick(player);
    }
    boolean isLookingArtworkPreview(Player player) {
        return artworkGalleryWorkflow != null && artworkGalleryWorkflow.isLooking(player);
    }
    boolean handleArtworkPreviewFrameClick(Player player, UUID frameId) {
        return artworkGalleryWorkflow != null && artworkGalleryWorkflow.handleFrameClick(player, frameId);
    }
    boolean handleArtworkPreviewFrameLeftClick(Player player, UUID frameId) {
        return artworkGalleryWorkflow != null && artworkGalleryWorkflow.handleFrameLeftClick(player, frameId);
    }
    boolean handleExhibitRemovalInteract(Player player, Action action) {
        return placementModeWorkflow != null && placementModeWorkflow.handleExhibitRemovalInteract(player, action);
    }
    boolean handleCanvasPlacementInteract(Player player, Action action) {
        return placementModeWorkflow != null && placementModeWorkflow.handleCanvasInteract(player, action);
    }
    boolean handleArtworkPlacementInteract(Player player, Action action) {
        return placementModeWorkflow != null && placementModeWorkflow.handleArtworkInteract(player, action);
    }
    boolean handlePalettePlacementInteract(Player player, Action action) {
        return paletteWorkflow != null && paletteWorkflow.handlePlacementInteract(player, action);
    }
    boolean handlePalettePlacementSwing(Player player) {
        return paletteWorkflow != null && paletteWorkflow.handlePlacementSwing(player);
    }
    boolean confirmPalettePlacement(Player player) {
        return paletteWorkflow != null && paletteWorkflow.confirmPlacement(player);
    }
    void endPalettePlacementPreview(Player player, boolean sendCancelMessage) {
        if (paletteWorkflow != null) {
            paletteWorkflow.endPlacement(player, sendCancelMessage);
        }
    }
    boolean handleArtworkFrameStyleClick(Player player, UUID entityId) {
        return placementModeWorkflow != null && placementModeWorkflow.cycleArtworkFrameStyle(player, entityId);
    }
    boolean isPrimaryUseHand(EquipmentSlot hand, Player player) {
        return interactionTools.isPrimaryUseHand(hand, player);
    }
    LayerControlLook getLookedLayerControl(Player player) {
        return layerWorkflow == null ? null : layerWorkflow.lookedControl(player);
    }
    LayerControlLook getLookedLayerControl(Player player, PlayerCanvas canvas) {
        return layerWorkflow == null ? null : layerWorkflow.lookedControl(player, canvas);
    }
    void handleLayerControlClick(Player player, LayerControlLook look) {
        if (layerWorkflow != null) {
            layerWorkflow.handleControlClick(player, look);
        }
    }
    boolean hasLayerOpacityInteractionLock(Player player) {
        return layerWorkflow != null && layerWorkflow.hasOpacityInteractionLock(player);
    }
    Tool selectedTool(Player player) {
        UUID playerId = player.getUniqueId();
        return selectedTools.computeIfAbsent(playerId, ignored -> Tool.defaultTool(advancedToolModes.getOrDefault(playerId, false)));
    }
    boolean removePaletteBoard(UUID ownerId) {
        return paletteWorkflow != null && paletteWorkflow.removeBoard(ownerId);
    }
    PaletteLook getLookedPaletteBoard(Player player) {
        return paletteWorkflow == null ? null : paletteWorkflow.lookedBoard(player);
    }
    PaletteLook getLookedPaletteBoard(Player player, PaletteBoard board) {
        return paletteWorkflow == null ? null : paletteWorkflow.lookedBoard(player, board);
    }
    void handlePaletteBoardClick(Player player, PaletteBoard board, PaletteLook look) {
        if (paletteWorkflow != null) {
            paletteWorkflow.handleBoardClick(player, board, look);
        }
    }
    void openPaletteBoard(Player player) {
        if (paletteWorkflow != null) {
            paletteWorkflow.openBoard(player);
        }
    }
    LookResult getLookedShapeCanvas(Player player) {
        return canvasLookService.lookedShapeCanvas(
                player,
                canvasLifecycle.canvases(),
                canvasLookDistance,
                advancedShapePlaneMarginBlocks
        );
    }
    LookResult getLookedCanvas(Player player) {
        return canvasLookService.lookedCanvas(player, canvasLifecycle.canvases(), canvasLookDistance);
    }
    void beginPendingAdvancedHold(Player player, Tool tool) {
        interactionTools.beginPendingAdvancedHold(player, tool);
    }
    void beginAdvancedHold(Player player, LookResult look, Tool tool) {
        interactionTools.beginAdvancedHold(player, look, tool);
    }
    void forceShieldUse(Player player) {
        interactionTools.forceShieldUse(player);
    }
    void stopDrawing(UUID playerId) {
        interactionTools.stopDrawing(playerId);
    }
    PlayerCanvas canvasByLayerDisplay(UUID displayId) {
        return layerWorkflow == null ? null : layerWorkflow.canvasByDisplay(displayId);
    }
    boolean confirmCanvasPlacement(Player player) {
        return placementModeWorkflow != null && placementModeWorkflow.confirmCanvas(player);
    }
    boolean confirmArtworkPlacement(Player player) {
        return placementModeWorkflow != null && placementModeWorkflow.confirmArtwork(player);
    }
    void endCanvasPlacementPreview(Player player, boolean sendCancelMessage) {
        if (placementModeWorkflow != null) {
            placementModeWorkflow.endCanvas(player, sendCancelMessage);
        }
    }
    void endArtworkPlacementPreview(Player player, boolean sendCancelMessage) {
        if (placementModeWorkflow != null) {
            placementModeWorkflow.endArtwork(player, sendCancelMessage);
        }
    }
    boolean handleExhibitRemovalConfirmClick(Player player) {
        return placementModeWorkflow != null && placementModeWorkflow.confirmExhibitRemoval(player);
    }
    boolean handleExhibitRemovalSwing(Player player) {
        return placementModeWorkflow != null && placementModeWorkflow.handleExhibitRemovalSwing(player);
    }
    void endExhibitRemovalMode(Player player, boolean sendCancelMessage) {
        if (placementModeWorkflow != null) {
            placementModeWorkflow.endExhibitRemoval(player, sendCancelMessage);
        }
    }
    boolean isPixelPainterTool(ItemStack item) {
        return toolItems.isPixelPainterTool(item);
    }
    void markPaletteRightClick(UUID playerId) {
        lastPaletteRightClickTimes.put(playerId, System.currentTimeMillis());
    }
    boolean isPaintPanelDisplay(UUID entityId) {
        return paintPanels != null && paintPanels.isDisplayEntity(entityId);
    }
    boolean isArtworkPreviewDisplay(UUID entityId) {
        return artworkPreviews != null && artworkPreviews.isDisplayEntity(entityId);
    }
    boolean isArtworkDisplay(UUID entityId) {
        return artworkDisplays != null && artworkDisplays.isDisplayEntity(entityId);
    }
    PaletteBoard paletteBoardByFrame(UUID frameId) {
        return paletteBoards == null ? null : paletteBoards.boardByFrame(frameId);
    }
    boolean hasCanvasFrame(UUID frameId) {
        return canvasLifecycle.hasFrame(frameId);
    }
    PlayerCanvas canvasByFrame(UUID frameId) {
        return canvasLifecycle.canvasByFrame(frameId);
    }
    boolean handleManualStationCanvasClick(Player player, UUID frameId) {
        return manualStationWorkflow != null && manualStationWorkflow.handleCanvasFrameClick(player, frameId);
    }
    void clearManualControlConfirm(Player player) {
        if (manualStationWorkflow != null) {
            manualStationWorkflow.clearControlRemoveConfirm(player);
        }
    }
    private boolean handleManualControlPanelClick(Player player, UUID entityId) {
        if (manualStationWorkflow == null || paintPanels == null) {
            return false;
        }
        PaintPanelService.PanelClick click = paintPanels.click(entityId);
        return click != null && manualStationWorkflow.handleControlAction(player, click.action());
    }
    private boolean handlePaintPanelResult(Player player, PaintPanelModeService.ActionResult result) {
        if (!result.clicked()) {
            return false;
        }
        if (result.action() != null && paintPanelWorkflow != null) {
            paintPanelWorkflow.handlePanelAction(player, result.action());
        }
        return true;
    }
}
