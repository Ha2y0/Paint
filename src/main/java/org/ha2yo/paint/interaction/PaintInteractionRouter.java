package org.ha2yo.paint.interaction;

import java.util.UUID;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.CanvasLookService.LookResult;
import org.ha2yo.paint.service.LayerPanelService.LayerControlLook;
import org.ha2yo.paint.service.PaletteBoardService.PaletteLook;
import org.ha2yo.paint.workflow.PaintingInteractionWorkflowService;

public final class PaintInteractionRouter {
    private final PaintInteractionFacade facade;
    private final PaintingInteractionWorkflowService paintingInteractions;
    public PaintInteractionRouter(
            PaintInteractionFacade facade,
            PaintingInteractionWorkflowService paintingInteractions
    ) {
        this.facade = facade;
        this.paintingInteractions = paintingInteractions;
    }
    public void onCanvasClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if ((facade.isExhibitRemovalModeActive(player.getUniqueId())
                || facade.isCanvasPlacementModeActive(player.getUniqueId())
                || facade.isArtworkPlacementModeActive(player.getUniqueId())
                || facade.isPalettePlacementModeActive(player.getUniqueId())
                || facade.isManualStationPlacementActive(player.getUniqueId()))
                && !facade.isPrimaryUseHand(event.getHand(), player)) {
            event.setCancelled(true);
            return;
        }
        if (facade.handleExhibitRemovalInteract(player, action)) {
            event.setCancelled(true);
            return;
        }
        if (facade.handleCanvasPlacementInteract(player, action)) {
            event.setCancelled(true);
            return;
        }
        if (facade.handleArtworkPlacementInteract(player, action)) {
            event.setCancelled(true);
            return;
        }
        if (facade.handlePalettePlacementInteract(player, action)) {
            event.setCancelled(true);
            return;
        }
        if (facade.handleManualStationPlacementInteract(player, action)) {
            event.setCancelled(true);
            return;
        }
        if (facade.isPaintPanelModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            if (!facade.isPrimaryUseHand(event.getHand(), player)) {
                return;
            }
            facade.ensurePaintPanelTool(player);
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                if (facade.handleLookedArtworkPreviewLeftClick(player)) {
                    facade.clearManualControlConfirm(player);
                    return;
                }
            }
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (facade.handleLookedArtworkPreviewClick(player)) {
                    facade.clearManualControlConfirm(player);
                    return;
                }
                if (facade.isLookingArtworkPreview(player)) {
                    return;
                }
            }
            facade.handleLookedPaintPanelClick(player);
            return;
        }
        if (!facade.isPrimaryUseHand(event.getHand(), player)) {
            return;
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (facade.handleLookedArtworkPreviewLeftClick(player)) {
                event.setCancelled(true);
                facade.clearManualControlConfirm(player);
                return;
            }
            if (facade.isLookingArtworkPreview(player)) {
                event.setCancelled(true);
                return;
            }
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            LayerControlLook layerLook = facade.getLookedLayerControl(player);
            if (layerLook != null) {
                event.setCancelled(true);
                facade.clearManualControlConfirm(player);
                facade.handleLayerControlClick(player, layerLook);
                return;
            }
            if (facade.handleLookedArtworkPreviewClick(player)) {
                event.setCancelled(true);
                facade.clearManualControlConfirm(player);
                return;
            }
            if (facade.isLookingArtworkPreview(player)) {
                event.setCancelled(true);
                return;
            }
        }
        if (facade.hasLayerOpacityInteractionLock(player)) {
            event.setCancelled(true);
            return;
        }
        if (!facade.isPixelPainterTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        denyVanillaToolUse(event);
        Tool tool = facade.selectedTool(player);
        if (tool == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (tool == Tool.PALETTE && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            facade.markPaletteRightClick(playerId);
        }
        if (tool == Tool.PALETTE && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.removePaletteBoard(playerId);
            return;
        }
        if (tool == Tool.PALETTE) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            PaletteLook paletteLook = facade.getLookedPaletteBoard(player);
            if (paletteLook != null) {
                facade.handlePaletteBoardClick(player, paletteLook.board(), paletteLook);
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                facade.openPaletteBoard(player);
            } else {
                facade.removePaletteBoard(player.getUniqueId());
            }
            return;
        }
        PaletteLook paletteLook = facade.getLookedPaletteBoard(player);
        if (paletteLook != null) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.handlePaletteBoardClick(player, paletteLook.board(), paletteLook);
            return;
        }
        if ((tool.isShapeTool() || tool.isSelectionMoveTool()) && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            LookResult look = tool.isShapeTool() ? facade.getLookedShapeCanvas(player) : facade.getLookedCanvas(player);
            if (look == null || !look.canvas().canEdit(player)) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.ALLOW);
                if (tool.isShapeTool()) {
                    facade.beginPendingAdvancedHold(player, tool);
                    facade.forceShieldUse(player);
                } else {
                    event.setCancelled(true);
                    facade.clearManualControlConfirm(player);
                    facade.stopDrawing(playerId);
                }
                return;
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.ALLOW);
            facade.clearManualControlConfirm(player);
            facade.beginAdvancedHold(player, look, tool);
            facade.forceShieldUse(player);
            return;
        }
        if (tool.isContinuous() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            LookResult look = facade.getLookedCanvas(player);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.ALLOW);
            if (look == null) {
                facade.clearManualControlConfirm(player);
                paintingInteractions.beginRightHold(player, tool);
                facade.forceShieldUse(player);
                return;
            }
            if (!look.canvas().canEdit(player)) {
                event.setCancelled(true);
                facade.clearManualControlConfirm(player);
                facade.stopDrawing(playerId);
                return;
            }
            facade.clearManualControlConfirm(player);
            paintingInteractions.beginRightHold(player, look.canvas(), tool);
            facade.forceShieldUse(player);
            return;
        }
        LookResult look = facade.getLookedCanvas(player);
        if (look == null || !look.canvas().canEdit(player)) {
            return;
        }
        event.setCancelled(true);
        facade.clearManualControlConfirm(player);
        paintingInteractions.useOneShotTool(player, look, tool);
    }
    private void denyVanillaToolUse(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }
    public void onCanvasFrameHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (facade.isCanvasPlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.endCanvasPlacementPreview(player, true);
            return;
        }
        if (facade.isArtworkPlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.endArtworkPlacementPreview(player, true);
            return;
        }
        if (facade.isPalettePlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.endPalettePlacementPreview(player, true);
            return;
        }
        if (facade.isManualStationPlacementActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.endManualStationPlacementPreview(player, true);
            return;
        }
        if (facade.isExhibitRemovalModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.handleExhibitRemovalSwing(player);
            return;
        }
        if (facade.isPaintPanelModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.ensurePaintPanelTool(player);
            if (facade.isArtworkPreviewDisplay(event.getEntity().getUniqueId())) {
                facade.clearManualControlConfirm(player);
                facade.handleArtworkPreviewFrameLeftClick(player, event.getEntity().getUniqueId());
            }
            return;
        }
        if (facade.isPaintPanelDisplay(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        PlayerCanvas layerCanvas = facade.canvasByLayerDisplay(event.getEntity().getUniqueId());
        if (layerCanvas != null) {
            event.setCancelled(true);
            return;
        }
        if (facade.isArtworkPreviewDisplay(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.handleArtworkPreviewFrameLeftClick(player, event.getEntity().getUniqueId());
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }
        if (facade.isArtworkDisplay(frame.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (facade.isArtworkPreviewDisplay(frame.getUniqueId())) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.handleArtworkPreviewFrameLeftClick(player, frame.getUniqueId());
            return;
        }
        PaletteBoard paletteBoard = facade.paletteBoardByFrame(frame.getUniqueId());
        if (paletteBoard != null) {
            event.setCancelled(true);
            if (facade.selectedTool(player) == Tool.PALETTE) {
                facade.clearManualControlConfirm(player);
                facade.handlePaletteBoardClick(player, paletteBoard, facade.getLookedPaletteBoard(player, paletteBoard));
            }
            return;
        }
        if (!facade.hasCanvasFrame(frame.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (facade.hasLayerOpacityInteractionLock(player)) {
            return;
        }
        Tool tool = facade.selectedTool(player);
        if (tool == null) {
            return;
        }
        LookResult look = facade.getLookedCanvas(player);
        if (look != null && look.canvas().canEdit(player)) {
            if (tool.isShapeTool() || tool.isSelectionMoveTool()) {
                facade.clearManualControlConfirm(player);
                facade.beginAdvancedHold(player, look, tool);
                facade.forceShieldUse(player);
                return;
            }
            if (tool.isContinuous()) {
                facade.clearManualControlConfirm(player);
                paintingInteractions.beginRightHold(player, look.canvas(), tool);
                facade.forceShieldUse(player);
                return;
            }
            facade.clearManualControlConfirm(player);
            paintingInteractions.useOneShotTool(player, look, tool);
        }
    }
    public void onArtworkPlacementDisplayUse(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!facade.isPrimaryUseHand(event.getHand(), player)) {
            return;
        }
        if (facade.isCanvasPlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.confirmCanvasPlacement(player);
            return;
        }
        if (facade.isPalettePlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.confirmPalettePlacement(player);
            return;
        }
        if (!facade.isArtworkPlacementModeActive(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (facade.handleArtworkFrameStyleClick(player, event.getRightClicked().getUniqueId())) {
            return;
        }
        facade.confirmArtworkPlacement(player);
    }
    public void onCanvasFrameUse(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!facade.isPrimaryUseHand(event.getHand(), player)) {
            return;
        }
        if (facade.isExhibitRemovalModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.handleExhibitRemovalConfirmClick(player);
            return;
        }
        if (facade.isCanvasPlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.confirmCanvasPlacement(player);
            return;
        }
        if (facade.isArtworkPlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            if (facade.handleArtworkFrameStyleClick(player, event.getRightClicked().getUniqueId())) {
                return;
            }
            facade.confirmArtworkPlacement(player);
            return;
        }
        if (facade.isPalettePlacementModeActive(player.getUniqueId())) {
            event.setCancelled(true);
            facade.confirmPalettePlacement(player);
            return;
        }
        if (facade.isPaintPanelModeActive(player.getUniqueId())) {
            facade.ensurePaintPanelTool(player);
            event.setCancelled(true);
            if (facade.isPaintPanelDisplay(event.getRightClicked().getUniqueId())) {
                facade.handlePaintPanelClick(player, event.getRightClicked().getUniqueId());
            } else if (facade.isArtworkPreviewDisplay(event.getRightClicked().getUniqueId())) {
                facade.handleArtworkPreviewFrameClick(player, event.getRightClicked().getUniqueId());
            }
            return;
        }
        if (facade.isPaintPanelDisplay(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
            facade.handlePaintPanelClick(player, event.getRightClicked().getUniqueId());
            return;
        }
        PlayerCanvas layerCanvas = facade.canvasByLayerDisplay(event.getRightClicked().getUniqueId());
        if (layerCanvas != null) {
            event.setCancelled(true);
            LayerControlLook layerLook = facade.getLookedLayerControl(player, layerCanvas);
            if (layerLook != null) {
                facade.clearManualControlConfirm(player);
                facade.handleLayerControlClick(player, layerLook);
            }
            return;
        }
        if (facade.isArtworkPreviewDisplay(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.handleArtworkPreviewFrameClick(player, event.getRightClicked().getUniqueId());
            return;
        }
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        if (facade.isArtworkDisplay(frame.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (facade.isArtworkPreviewDisplay(frame.getUniqueId())) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.handleArtworkPreviewFrameClick(player, frame.getUniqueId());
            return;
        }
        if (facade.handleManualStationCanvasClick(player, frame.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        PaletteBoard paletteBoard = facade.paletteBoardByFrame(frame.getUniqueId());
        if (paletteBoard != null) {
            event.setCancelled(true);
            facade.clearManualControlConfirm(player);
            facade.handlePaletteBoardClick(event.getPlayer(), paletteBoard, facade.getLookedPaletteBoard(event.getPlayer(), paletteBoard));
            return;
        }
        if (!facade.hasCanvasFrame(frame.getUniqueId())) {
            return;
        }
        Tool tool = facade.selectedTool(player);
        if (tool == null || !facade.isPixelPainterTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (facade.hasLayerOpacityInteractionLock(player)) {
            return;
        }
        if (tool.isShapeTool() || tool.isSelectionMoveTool()) {
            LookResult look = facade.getLookedCanvas(player);
            if (look != null && look.canvas().canEdit(player)) {
                facade.clearManualControlConfirm(player);
                facade.beginAdvancedHold(player, look, tool);
                facade.forceShieldUse(player);
            }
            return;
        }
        if (tool.isContinuous()) {
            PlayerCanvas canvas = facade.canvasByFrame(frame.getUniqueId());
            if (canvas == null || !canvas.canEdit(player)) {
                facade.clearManualControlConfirm(player);
                facade.stopDrawing(player.getUniqueId());
                return;
            }
            facade.clearManualControlConfirm(player);
            paintingInteractions.beginRightHold(player, canvas, tool);
            facade.forceShieldUse(player);
            return;
        }
        LookResult look = facade.getLookedCanvas(player);
        if (look != null && look.canvas().canEdit(player)) {
            facade.clearManualControlConfirm(player);
            paintingInteractions.useOneShotTool(player, look, tool);
        }
    }
}
