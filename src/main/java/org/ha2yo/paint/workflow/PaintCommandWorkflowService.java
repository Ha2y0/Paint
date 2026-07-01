package org.ha2yo.paint.workflow;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.station.StationPanelSlot;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkStorageService;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.PaintPanelService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PaintCommandWorkflowService {
    private final PaintPanelWorkflowService paintPanelWorkflow;
    private final PaintPanelService paintPanels;
    private final PaintPanelModeService paintPanelModes;
    private final ArtworkGalleryWorkflowService artworkGalleryWorkflow;
    private final ArtworkSaveWorkflowService artworkSaveWorkflow;
    private final ArtworkStorageService artworkStorage;
    private final ArtworkDisplayService artworkDisplays;
    private final CanvasWorkflowService canvasWorkflow;
    private final PlacementModeWorkflowService placementModeWorkflow;
    private final PaletteWorkflowService paletteWorkflow;
    private final ManualStationWorkflowService manualStationWorkflow;
    private final Map<String, Color> palette;
    private final Map<UUID, Color> selectedColors;
    private final int defaultCanvasBlockWidth;
    private final int defaultCanvasBlockHeight;
    private final int maxCanvasBlockSize;
    private final int maxBrushRadius;

    public PaintCommandWorkflowService(
            PaintPanelWorkflowService paintPanelWorkflow,
            PaintPanelService paintPanels,
            PaintPanelModeService paintPanelModes,
            ArtworkGalleryWorkflowService artworkGalleryWorkflow,
            ArtworkSaveWorkflowService artworkSaveWorkflow,
            ArtworkStorageService artworkStorage,
            ArtworkDisplayService artworkDisplays,
            CanvasWorkflowService canvasWorkflow,
            PlacementModeWorkflowService placementModeWorkflow,
            PaletteWorkflowService paletteWorkflow,
            ManualStationWorkflowService manualStationWorkflow,
            Map<String, Color> palette,
            Map<UUID, Color> selectedColors,
            int defaultCanvasBlockWidth,
            int defaultCanvasBlockHeight,
            int maxCanvasBlockSize,
            int maxBrushRadius
    ) {
        this.paintPanelWorkflow = paintPanelWorkflow;
        this.paintPanels = paintPanels;
        this.paintPanelModes = paintPanelModes;
        this.artworkGalleryWorkflow = artworkGalleryWorkflow;
        this.artworkSaveWorkflow = artworkSaveWorkflow;
        this.artworkStorage = artworkStorage;
        this.artworkDisplays = artworkDisplays;
        this.canvasWorkflow = canvasWorkflow;
        this.placementModeWorkflow = placementModeWorkflow;
        this.paletteWorkflow = paletteWorkflow;
        this.manualStationWorkflow = manualStationWorkflow;
        this.palette = palette;
        this.selectedColors = selectedColors;
        this.defaultCanvasBlockWidth = defaultCanvasBlockWidth;
        this.defaultCanvasBlockHeight = defaultCanvasBlockHeight;
        this.maxCanvasBlockSize = maxCanvasBlockSize;
        this.maxBrushRadius = maxBrushRadius;
    }

    public void repositionPaintUi(Player player) {
        UUID playerId = player.getUniqueId();
        if (artworkGalleryWorkflow != null && artworkGalleryWorkflow.hasSession(playerId)) {
            artworkGalleryWorkflow.end(player);
            openPaintMainPanel(player, false);
            return;
        }

        if (paintPanelWorkflow != null) {
            paintPanelWorkflow.clearPending(playerId);
        }
        if (paintPanels != null && paintPanels.isShowing(playerId)) {
            paintPanels.clear(playerId);
        }
        openPaintMainPanel(player, false);
    }

    public void resetPaintUiForSubcommand(Player player) {
        UUID playerId = player.getUniqueId();
        if (artworkGalleryWorkflow != null && artworkGalleryWorkflow.hasSession(playerId)) {
            artworkGalleryWorkflow.end(player);
        }
        if (paintPanelWorkflow != null) {
            paintPanelWorkflow.clearPending(playerId);
        }
        if (paintPanels != null && paintPanels.isShowing(playerId)) {
            paintPanels.clear(playerId);
        }
        if (isPaintPanelModeActive(playerId) && paintPanelModes != null) {
            paintPanelModes.end(player, true);
        }
    }

    public boolean canUseAdminPaintCommands(Player player) {
        return player.isOp() || player.hasPermission("paint.admin");
    }

    public boolean isFreeMode(Player player) {
        return manualStationWorkflow == null || manualStationWorkflow.isFreeMode(player);
    }

    public boolean isPaintUiOpen(UUID playerId) {
        return (paintPanels != null && paintPanels.isShowing(playerId))
                || (artworkGalleryWorkflow != null && artworkGalleryWorkflow.hasSession(playerId));
    }

    public List<String> artworkOwnerNames() {
        return artworkStorage.ownerNames();
    }

    public List<String> paletteNames() {
        return new ArrayList<>(palette.keySet());
    }

    public boolean canCreateNewCanvas(Player player) {
        if (canvasWorkflow.hasCanvas(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "이미 캔버스가 있습니다. 먼저 기존 캔버스를 제거해 주세요.");
            return false;
        }
        return true;
    }

    public void openPaintMainPanel(Player player, boolean confirmRemove) {
        paintPanelWorkflow.openMainPanel(player, confirmRemove);
    }

    public int defaultCanvasBlockWidth() {
        return defaultCanvasBlockWidth;
    }

    public int defaultCanvasBlockHeight() {
        return defaultCanvasBlockHeight;
    }

    public int maxCanvasBlockSize() {
        return maxCanvasBlockSize;
    }

    public boolean isValidCanvasBlockSizeForCommand(int value) {
        return value >= 1 && value <= maxCanvasBlockSize;
    }

    public boolean clearCanvas(UUID ownerId) {
        return canvasWorkflow.clear(ownerId);
    }

    public boolean removeCanvas(UUID ownerId) {
        return canvasWorkflow.remove(ownerId);
    }

    public void startCanvasPlacementPreview(Player player, int blockWidth, int blockHeight) {
        if (placementModeWorkflow != null) {
            placementModeWorkflow.startCanvas(player, blockWidth, blockHeight);
        }
    }

    public void beginArtworkNameInput(Player player) {
        artworkSaveWorkflow.beginNameInput(player);
    }

    public void saveArtworkWithName(Player player, String rawName) {
        artworkSaveWorkflow.saveWithName(player, rawName);
    }

    public void startArtworkPreview(Player player, boolean exhibitMode) {
        if (artworkGalleryWorkflow != null) {
            artworkGalleryWorkflow.start(player, exhibitMode);
        }
    }

    public void openArtworkPreviewForOwnerName(Player player, String ownerName) {
        if (artworkGalleryWorkflow != null) {
            artworkGalleryWorkflow.openForOwnerName(player, ownerName);
        }
    }

    public void reloadArtworkDisplays(Player player) {
        artworkDisplays.load();
        player.sendMessage(ChatColor.GREEN + "Paint 전시품 데이터를 다시 불러왔습니다.");
    }

    public void startExhibitRemovalMode(Player player) {
        if (placementModeWorkflow != null) {
            placementModeWorkflow.startExhibitRemoval(player);
        }
    }

    public int maxBrushRadius() {
        return maxBrushRadius;
    }

    public void setBrushRadius(Player player, int radius) {
        if (paletteWorkflow != null) {
            paletteWorkflow.setBrushRadius(player, radius);
        }
    }

    public void selectPaletteColor(Player player, String colorName) {
        Color color = palette.get(colorName.toLowerCase(Locale.ROOT));
        if (color == null) {
            player.sendMessage(ChatColor.RED + "알 수 없는 색상입니다. " + colorName);
            return;
        }
        selectedColors.put(player.getUniqueId(), color);
        player.sendMessage(ChatColor.GREEN + "선택한 색상: " + colorName.toLowerCase(Locale.ROOT));
    }

    public void setManualStationCanvas(Player player, String stationId, int width, int height) {
        if (manualStationWorkflow != null) {
            manualStationWorkflow.setCanvasSlot(player, stationId, width, height);
        }
    }

    public void setManualStationGallery(Player player, String stationId) {
        if (manualStationWorkflow != null) {
            manualStationWorkflow.setGallerySlot(player, stationId);
        }
    }

    public void setManualStationControl(Player player, String stationId, StationPanelSlot.Layout layout) {
        if (manualStationWorkflow != null) {
            manualStationWorkflow.setControlSlot(player, stationId, layout);
        }
    }

    public void listManualStations(Player player) {
        if (manualStationWorkflow != null) {
            manualStationWorkflow.listStations(player);
        }
    }

    public void removeManualStation(Player player, String stationId) {
        if (manualStationWorkflow != null) {
            manualStationWorkflow.removeStation(player, stationId);
        }
    }

    private boolean isPaintPanelModeActive(UUID playerId) {
        return paintPanelModes != null && paintPanelModes.contains(playerId);
    }
}
