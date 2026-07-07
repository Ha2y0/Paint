package org.ha2yo.paint.workflow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.session.ArtworkPlacementSession;
import org.ha2yo.paint.model.session.CanvasPlacementSession;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.CanvasMapSyncService;
import org.ha2yo.paint.service.DrawingInteractionService;
import org.ha2yo.paint.service.DrawingSessionService;
import org.ha2yo.paint.service.PaintMenuService;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.PaintWindowPlacementService;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.service.PlacementPreviewService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class PaintControllerFeatureService {
    private final Paint plugin;
    private final PaintWindowPlacementService paintWindows;
    private final DrawingInteractionService drawingInteractions;
    private final DrawingSessionService drawingSessions;
    private final Map<UUID, Tool> selectedTools;
    private final Map<UUID, Boolean> advancedToolModes;
    private final int defaultCanvasBlockWidth;
    private final int defaultCanvasBlockHeight;
    private final int maxCanvasBlockSize;
    private final int canvasPixelsPerBlock;
    private final Supplier<CanvasLifecycleService> canvasLifecycle;
    private final Supplier<CanvasWorkflowService> canvasWorkflow;
    private final Supplier<AdvancedToolWorkflowService> advancedToolWorkflow;
    private final Supplier<PlacementModeWorkflowService> placementModeWorkflow;
    private final Supplier<PaintPanelWorkflowService> paintPanelWorkflow;
    private final Supplier<PaintPanelModeService> paintPanelModes;
    private final Supplier<ArtworkGalleryWorkflowService> artworkGalleryWorkflow;
    private final Supplier<ArtworkSaveWorkflowService> artworkSaveWorkflow;
    private final Supplier<PlacementPreviewService> placementPreviews;
    private final Supplier<LayerWorkflowService> layerWorkflow;
    private final Supplier<PaletteBoardService> paletteBoards;
    private final Supplier<ArtworkDisplayService> artworkDisplays;
    private final Supplier<CanvasMapSyncService> canvasMaps;

    public PaintControllerFeatureService(
            Paint plugin,
            PaintWindowPlacementService paintWindows,
            DrawingInteractionService drawingInteractions,
            DrawingSessionService drawingSessions,
            Map<UUID, Tool> selectedTools,
            Map<UUID, Boolean> advancedToolModes,
            int defaultCanvasBlockWidth,
            int defaultCanvasBlockHeight,
            int maxCanvasBlockSize,
            int canvasPixelsPerBlock,
            Supplier<CanvasLifecycleService> canvasLifecycle,
            Supplier<CanvasWorkflowService> canvasWorkflow,
            Supplier<AdvancedToolWorkflowService> advancedToolWorkflow,
            Supplier<PlacementModeWorkflowService> placementModeWorkflow,
            Supplier<PaintPanelWorkflowService> paintPanelWorkflow,
            Supplier<PaintPanelModeService> paintPanelModes,
            Supplier<ArtworkGalleryWorkflowService> artworkGalleryWorkflow,
            Supplier<ArtworkSaveWorkflowService> artworkSaveWorkflow,
            Supplier<PlacementPreviewService> placementPreviews,
            Supplier<LayerWorkflowService> layerWorkflow,
            Supplier<PaletteBoardService> paletteBoards,
            Supplier<ArtworkDisplayService> artworkDisplays,
            Supplier<CanvasMapSyncService> canvasMaps
    ) {
        this.plugin = plugin;
        this.paintWindows = paintWindows;
        this.drawingInteractions = drawingInteractions;
        this.drawingSessions = drawingSessions;
        this.selectedTools = selectedTools;
        this.advancedToolModes = advancedToolModes;
        this.defaultCanvasBlockWidth = defaultCanvasBlockWidth;
        this.defaultCanvasBlockHeight = defaultCanvasBlockHeight;
        this.maxCanvasBlockSize = maxCanvasBlockSize;
        this.canvasPixelsPerBlock = canvasPixelsPerBlock;
        this.canvasLifecycle = canvasLifecycle;
        this.canvasWorkflow = canvasWorkflow;
        this.advancedToolWorkflow = advancedToolWorkflow;
        this.placementModeWorkflow = placementModeWorkflow;
        this.paintPanelWorkflow = paintPanelWorkflow;
        this.paintPanelModes = paintPanelModes;
        this.artworkGalleryWorkflow = artworkGalleryWorkflow;
        this.artworkSaveWorkflow = artworkSaveWorkflow;
        this.placementPreviews = placementPreviews;
        this.layerWorkflow = layerWorkflow;
        this.paletteBoards = paletteBoards;
        this.artworkDisplays = artworkDisplays;
        this.canvasMaps = canvasMaps;
    }

    public PaintCanvas createCanvas(Player player) {
        return createCanvas(player, defaultCanvasBlockWidth, defaultCanvasBlockHeight);
    }

    public PaintCanvas createCanvas(Player player, int blockWidth, int blockHeight) {
        BlockFace facing = paintWindows.cardinalFace(player);
        Location origin = player.getLocation().getBlock().getRelative(facing, 4).getLocation();
        return createCanvas(player.getUniqueId(), origin, facing, paintWindows.rightOf(facing), blockWidth, blockHeight);
    }

    public PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right) {
        return canvasWorkflow.get().create(ownerId, origin, facing, right);
    }

    public PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right, int blockWidth, int blockHeight) {
        return canvasWorkflow.get().create(ownerId, origin, facing, right, blockWidth, blockHeight);
    }

    public boolean removeCanvas(UUID ownerId) {
        return canvasWorkflow.get().remove(ownerId);
    }

    public boolean clearCanvas(UUID ownerId) {
        return canvasWorkflow.get().clear(ownerId);
    }

    public Optional<PaintCanvas> canvas(UUID ownerId) {
        return canvasWorkflow.get().canvas(ownerId);
    }

    public Optional<PlayerCanvas> playerCanvas(UUID ownerId) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        return lifecycle == null ? Optional.empty() : Optional.ofNullable(lifecycle.canvas(ownerId));
    }

    public boolean hasCanvas(UUID ownerId) {
        return canvasWorkflow.get().hasCanvas(ownerId);
    }

    public boolean grantCanvasEditAccess(UUID ownerId, UUID editorId) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        if (lifecycle == null || !lifecycle.grantEditAccess(ownerId, editorId)) {
            return false;
        }
        updateLayerDisplays(ownerId);
        return true;
    }

    public boolean revokeCanvasEditAccess(UUID ownerId, UUID editorId) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        if (lifecycle == null || !lifecycle.revokeEditAccess(ownerId, editorId)) {
            return false;
        }
        updateLayerDisplays(ownerId);
        return true;
    }

    public boolean setCanvasVisibleFor(UUID ownerId, Player viewer, boolean visible) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        if (lifecycle == null || viewer == null || !lifecycle.setCanvasVisibleFor(ownerId, viewer, visible)) {
            return false;
        }
        if (visible) {
            PlayerCanvas canvas = lifecycle.canvas(ownerId);
            CanvasMapSyncService maps = canvasMaps.get();
            if (canvas != null && maps != null) {
                maps.sendTo(canvas, viewer, true);
            }
        }
        return true;
    }

    private void updateLayerDisplays(UUID ownerId) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        LayerWorkflowService workflow = layerWorkflow.get();
        if (lifecycle == null || workflow == null) {
            return;
        }
        PlayerCanvas canvas = lifecycle.canvas(ownerId);
        if (canvas != null) {
            workflow.updateDisplays(canvas);
        }
    }

    public void adjustArtworkPlacementDistance(Player player, int delta) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.adjustArtworkDistance(player, delta);
        }
    }

    public void adjustCanvasPlacementDistance(Player player, int delta) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.adjustCanvasDistance(player, delta);
        }
    }

    public void pushUndo(PlayerCanvas canvas) {
        drawingInteractions.pushUndo(canvas);
    }

    public void finishAdvancedHold(Player player) {
        AdvancedToolWorkflowService workflow = advancedToolWorkflow.get();
        if (workflow != null) {
            workflow.finishHold(player);
        }
    }

    public void stopDrawing(UUID playerId) {
        drawingSessions.stop(playerId);
        AdvancedToolWorkflowService workflow = advancedToolWorkflow.get();
        if (workflow != null) {
            workflow.cancelHold(playerId);
        }
    }

    public PlayerCanvas canvasById(UUID canvasId) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        return lifecycle == null ? null : lifecycle.canvas(canvasId);
    }

    public void clearAdvancedPreview(UUID playerId, UUID canvasId) {
        AdvancedToolWorkflowService workflow = advancedToolWorkflow.get();
        if (workflow != null) {
            workflow.clearPreview(playerId, canvasId);
        }
    }

    public void clearPaintPanel(UUID playerId) {
        PaintPanelWorkflowService workflow = paintPanelWorkflow.get();
        if (workflow != null) {
            workflow.clearPanel(playerId);
        }
    }

    public void clearPaintPanelOnly(UUID playerId) {
        PaintPanelModeService modes = paintPanelModes.get();
        if (modes != null) {
            modes.clearPanel(playerId);
        }
    }

    public boolean canCreateNewCanvas(Player player) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        if (lifecycle != null && lifecycle.hasCanvas(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "이미 캔버스가 있습니다. 먼저 기존 캔버스를 제거해 주세요.");
            return false;
        }
        return true;
    }

    public void handlePaintMenuClick(Player player, ItemStack item) {
        PaintPanelWorkflowService workflow = paintPanelWorkflow.get();
        if (workflow != null) {
            workflow.handleInventoryMenuClick(player, item);
        }
    }

    public void handleArtworkMenuClick(Player player, ItemStack item) {
        ArtworkGalleryWorkflowService workflow = artworkGalleryWorkflow.get();
        if (workflow != null) {
            workflow.handleInventoryClick(player, item);
        }
    }

    public void handleSaveArtwork(Player player) {
        ArtworkSaveWorkflowService workflow = artworkSaveWorkflow.get();
        if (workflow != null) {
            workflow.beginNameInput(player);
        }
    }

    public Location artworkGalleryAnchor(Player player) {
        return paintWindows.anchor(player, 4.0D, 2.7D, 6, 6);
    }

    public void endPaintPanelModeIfActive(Player player, InventoryToolWorkflowService inventoryToolWorkflow) {
        if (inventoryToolWorkflow.isPaintPanelModeActive(player.getUniqueId())) {
            inventoryToolWorkflow.endPaintPanelMode(player, true);
        }
    }

    public int artworkBlockWidth(PaintArtwork artwork) {
        return Math.max(1, Math.min(maxCanvasBlockSize, (int) Math.ceil(artwork.width() / (double) canvasPixelsPerBlock)));
    }

    public int artworkBlockHeight(PaintArtwork artwork) {
        return Math.max(1, Math.min(maxCanvasBlockSize, (int) Math.ceil(artwork.height() / (double) canvasPixelsPerBlock)));
    }

    public boolean isPlacementBlockBlocked(BlockKey blockKey) {
        CanvasLifecycleService lifecycle = canvasLifecycle.get();
        PaletteBoardService palettes = paletteBoards.get();
        ArtworkDisplayService displays = artworkDisplays.get();
        return (lifecycle != null && lifecycle.hasBlock(blockKey))
                || (palettes != null && palettes.isBlockOccupied(blockKey))
                || (displays != null && displays.isProtectedBlock(blockKey));
    }

    public void removeArtworkPlacementDisplays(ArtworkPlacementSession session) {
        if (session == null) {
            return;
        }
        PlacementPreviewService previews = placementPreviews.get();
        if (previews != null) {
            previews.removeDisplays(session.displayIds());
        }
    }

    public void removeCanvasPlacementDisplays(CanvasPlacementSession session) {
        if (session == null) {
            return;
        }
        PlacementPreviewService previews = placementPreviews.get();
        if (previews != null) {
            previews.removeDisplays(session.displayIds());
        }
    }

    public void endArtworkPreview(Player player) {
        ArtworkGalleryWorkflowService workflow = artworkGalleryWorkflow.get();
        if (workflow != null) {
            workflow.end(player);
        }
    }

    public void handleCanvasSizeMenuClick(Player player, ItemStack item) {
        PaintPanelWorkflowService workflow = paintPanelWorkflow.get();
        if (workflow != null) {
            workflow.handleCanvasSizeMenuClick(player, item);
        }
    }

    public Location paintPanelAnchor(Player player) {
        return paintWindows.anchor(player, 3.0D, 1.7D, 6, 1);
    }

    public boolean isPaintMenuTitle(String title) {
        return PaintMenuService.MAIN_MENU_TITLE.equals(title)
                || PaintMenuService.CANVAS_SIZE_MENU_TITLE.equals(title)
                || PaintMenuService.ARTWORK_LIST_TITLE.equals(title)
                || PaintMenuService.ARTWORK_SHOW_TITLE.equals(title);
    }

    public Tool selectedTool(Player player) {
        UUID playerId = player.getUniqueId();
        return selectedTools.computeIfAbsent(playerId, ignored -> Tool.defaultTool(advancedToolModes.getOrDefault(playerId, false)));
    }

    public String canvasOwnerName(UUID ownerId) {
        Player player = Bukkit.getPlayer(ownerId);
        return player == null ? "" : player.getName();
    }

    public boolean shaderRgbEnabled() {
        return plugin.getConfig().getBoolean("map-render.canvas-rgb-mode", true);
    }

    public boolean displayShaderRgbEnabled() {
        return plugin.getConfig().getBoolean("map-render.display-rgb-mode", false);
    }

    public int clampCanvasBlockSize(int value) {
        return Math.max(1, Math.min(maxCanvasBlockSize, value));
    }

    public void clearStrokeState() {
        drawingSessions.clearStrokeState();
    }

    public void clearStrokeState(UUID playerId) {
        drawingSessions.clearStrokeState(playerId);
    }
}
