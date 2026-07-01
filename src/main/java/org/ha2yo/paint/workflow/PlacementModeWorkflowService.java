package org.ha2yo.paint.workflow;

import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.ha2yo.paint.manager.InventorySnapshot;
import org.ha2yo.paint.mode.ArtworkPlacementModeService;
import org.ha2yo.paint.mode.CanvasPlacementModeService;
import org.ha2yo.paint.mode.ExhibitRemovalModeService;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

public final class PlacementModeWorkflowService {
    private final ArtworkPlacementModeService artworkPlacementModes;
    private final CanvasPlacementModeService canvasPlacementModes;
    private final ExhibitRemovalModeService exhibitRemovalModes;
    private final ToIntFunction<PaintArtwork> artworkBlockWidth;
    private final ToIntFunction<PaintArtwork> artworkBlockHeight;
    private final IntUnaryOperator canvasBlockSizeClamp;
    private final Runnable artworkTaggedDisplayCleaner;

    public PlacementModeWorkflowService(
            ArtworkPlacementModeService artworkPlacementModes,
            CanvasPlacementModeService canvasPlacementModes,
            ExhibitRemovalModeService exhibitRemovalModes,
            ToIntFunction<PaintArtwork> artworkBlockWidth,
            ToIntFunction<PaintArtwork> artworkBlockHeight,
            IntUnaryOperator canvasBlockSizeClamp,
            Runnable artworkTaggedDisplayCleaner
    ) {
        this.artworkPlacementModes = artworkPlacementModes;
        this.canvasPlacementModes = canvasPlacementModes;
        this.exhibitRemovalModes = exhibitRemovalModes;
        this.artworkBlockWidth = artworkBlockWidth;
        this.artworkBlockHeight = artworkBlockHeight;
        this.canvasBlockSizeClamp = canvasBlockSizeClamp;
        this.artworkTaggedDisplayCleaner = artworkTaggedDisplayCleaner;
    }

    public boolean isArtworkActive(UUID playerId) {
        return artworkPlacementModes != null && artworkPlacementModes.contains(playerId);
    }

    public boolean isArtworkFrameSelectionActive(UUID playerId) {
        return artworkPlacementModes != null && artworkPlacementModes.isFrameSelectionOnly(playerId);
    }

    public boolean isArtworkFrameMaterialSlot(int slot) {
        return artworkPlacementModes != null && artworkPlacementModes.isFrameMaterialSlot(slot);
    }

    public boolean isCanvasActive(UUID playerId) {
        return canvasPlacementModes != null && canvasPlacementModes.contains(playerId);
    }

    public boolean isExhibitRemovalActive(UUID playerId) {
        return exhibitRemovalModes != null && exhibitRemovalModes.contains(playerId);
    }

    public void putArtworkInventory(UUID playerId, InventorySnapshot snapshot) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.putInventory(playerId, snapshot);
        }
    }

    public void putExhibitRemovalInventory(UUID playerId, InventorySnapshot snapshot) {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.putInventory(playerId, snapshot);
        }
    }

    public void clearInventories() {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.clearInventories();
        }
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.clearInventories();
        }
    }

    public void startArtwork(Player player, PaintArtwork artwork) {
        startArtwork(player, artwork, ExhibitFrameStyle.NONE);
    }

    public void startArtwork(Player player, PaintArtwork artwork, ExhibitFrameStyle frameStyle) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.start(player, artwork, artworkBlockWidth.applyAsInt(artwork), artworkBlockHeight.applyAsInt(artwork), frameStyle);
        }
    }

    public void startArtworkFrameSelection(
            Player player,
            PaintArtwork artwork,
            ExhibitFrameStyle frameStyle,
            Consumer<ExhibitFrameStyle> frameStyleHandler,
            Consumer<Player> frameSelectionEndHandler
    ) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.startFrameSelection(
                    player,
                    artwork,
                    artworkBlockWidth.applyAsInt(artwork),
                    artworkBlockHeight.applyAsInt(artwork),
                    frameStyle,
                    frameStyleHandler,
                    frameSelectionEndHandler
            );
        }
    }

    public void startCanvas(Player player, int blockWidth, int blockHeight) {
        startCanvas(player, blockWidth, blockHeight, null);
    }

    public void startCanvas(Player player, int blockWidth, int blockHeight, Consumer<Player> placementCompletion) {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.start(player, canvasBlockSizeClamp.applyAsInt(blockWidth), canvasBlockSizeClamp.applyAsInt(blockHeight), placementCompletion);
        }
    }

    public void startExhibitRemoval(Player player) {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.start(player);
        }
    }

    public void ensureArtworkTool(Player player) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.ensureTool(player);
        }
    }

    public void ensureCanvasTool(Player player) {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.ensureTool(player);
        }
    }

    public void ensureExhibitRemovalTool(Player player) {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.ensureTool(player);
        }
    }

    public void restoreArtworkInventory(Player player) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.restoreInventory(player);
        }
    }

    public void restoreExhibitRemovalInventory(Player player) {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.restoreInventory(player);
        }
    }

    public void adjustArtworkDistance(Player player, int delta) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.adjustDistance(player, delta);
        }
    }

    public void adjustCanvasDistance(Player player, int delta) {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.adjustDistance(player, delta);
        }
    }

    public boolean handleArtworkSwing(Player player) {
        return artworkPlacementModes != null && artworkPlacementModes.handleSwing(player);
    }

    public boolean handleCanvasSwing(Player player) {
        return canvasPlacementModes != null && canvasPlacementModes.handleSwing(player);
    }

    public boolean handleExhibitRemovalSwing(Player player) {
        return exhibitRemovalModes != null && exhibitRemovalModes.handleSwing(player);
    }

    public boolean handleArtworkInteract(Player player, Action action) {
        return artworkPlacementModes != null && artworkPlacementModes.handleInteract(player, action);
    }

    public boolean handleCanvasInteract(Player player, Action action) {
        return canvasPlacementModes != null && canvasPlacementModes.handleInteract(player, action);
    }

    public boolean handleExhibitRemovalInteract(Player player, Action action) {
        return exhibitRemovalModes != null && exhibitRemovalModes.handleInteract(player, action);
    }

    public boolean confirmArtwork(Player player) {
        return artworkPlacementModes != null && artworkPlacementModes.confirm(player);
    }

    public boolean cycleArtworkFrameStyle(Player player, UUID entityId) {
        return artworkPlacementModes != null && artworkPlacementModes.cycleFrameStyleForExhibit(player, entityId);
    }

    public boolean confirmCanvas(Player player) {
        return canvasPlacementModes != null && canvasPlacementModes.confirm(player);
    }

    public boolean confirmExhibitRemoval(Player player) {
        return exhibitRemovalModes != null && exhibitRemovalModes.handleConfirmClick(player);
    }

    public void updateArtworkPreviews() {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.updateAll();
        }
    }

    public void updateCanvasPreviews() {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.updateAll();
        }
    }

    public void updateExhibitRemovalActionBars() {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.updateActionBars();
        }
    }

    public void endArtwork(Player player, boolean sendCancelMessage) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.end(player, sendCancelMessage);
        }
    }

    public void endCanvas(Player player, boolean sendCancelMessage) {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.end(player, sendCancelMessage);
        }
    }

    public void endExhibitRemoval(Player player, boolean sendCancelMessage) {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.end(player, sendCancelMessage);
        }
    }

    public void clearCanvasPreviews() {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.clearAll();
        }
    }

    public void clearArtworkPreviews() {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.clearSessions();
        }
        artworkTaggedDisplayCleaner.run();
    }

    public void clearExhibitRemovalModes() {
        if (exhibitRemovalModes != null) {
            exhibitRemovalModes.clearAll();
        }
    }

    public void syncCanvasVisibility(Player viewer) {
        if (canvasPlacementModes != null) {
            canvasPlacementModes.syncVisibility(viewer);
        }
    }

    public void syncArtworkVisibility(Player viewer) {
        if (artworkPlacementModes != null) {
            artworkPlacementModes.syncVisibility(viewer);
        }
    }
}
