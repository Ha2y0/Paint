package org.ha2yo.paint.workflow;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.ha2yo.paint.service.AdvancedToolSessionService;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.DrawingSessionService;
import org.ha2yo.paint.service.PaintPanelService;
import org.ha2yo.paint.service.ToolItemService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaintTickWorkflowService {
    private static final long TOOL_HINT_ACTION_BAR_INTERVAL_MILLIS = 1000L;

    private final Server server;
    private final CanvasLifecycleService canvasLifecycle;
    private final DrawingSessionService drawingSessions;
    private final AdvancedToolSessionService advancedToolSessions;
    private final PlacementModeWorkflowService placementModeWorkflow;
    private final PaintPanelService paintPanels;
    private final ToolItemService toolItems;
    private final Map<UUID, Boolean> advancedToolModes;
    private final PaletteWorkflowService paletteWorkflow;
    private final ArtworkGalleryWorkflowService artworkGalleryWorkflow;
    private final AdvancedToolWorkflowService advancedToolWorkflow;
    private final ManualStationWorkflowService manualStationWorkflow;
    private final PaintingInteractionWorkflowService paintingInteractions;
    private final double paintPanelInteractionDistance;
    private final Runnable strokeStateCleaner;
    private final Map<UUID, Long> toolHintActionBarTimes = new HashMap<>();

    public PaintTickWorkflowService(
            Server server,
            CanvasLifecycleService canvasLifecycle,
            DrawingSessionService drawingSessions,
            AdvancedToolSessionService advancedToolSessions,
            PlacementModeWorkflowService placementModeWorkflow,
            PaintPanelService paintPanels,
            ToolItemService toolItems,
            Map<UUID, Boolean> advancedToolModes,
            PaletteWorkflowService paletteWorkflow,
            ArtworkGalleryWorkflowService artworkGalleryWorkflow,
            AdvancedToolWorkflowService advancedToolWorkflow,
            ManualStationWorkflowService manualStationWorkflow,
            PaintingInteractionWorkflowService paintingInteractions,
            double paintPanelInteractionDistance,
            Runnable strokeStateCleaner
    ) {
        this.server = server;
        this.canvasLifecycle = canvasLifecycle;
        this.drawingSessions = drawingSessions;
        this.advancedToolSessions = advancedToolSessions;
        this.placementModeWorkflow = placementModeWorkflow;
        this.paintPanels = paintPanels;
        this.toolItems = toolItems;
        this.advancedToolModes = advancedToolModes;
        this.paletteWorkflow = paletteWorkflow;
        this.artworkGalleryWorkflow = artworkGalleryWorkflow;
        this.advancedToolWorkflow = advancedToolWorkflow;
        this.manualStationWorkflow = manualStationWorkflow;
        this.paintingInteractions = paintingInteractions;
        this.paintPanelInteractionDistance = paintPanelInteractionDistance;
        this.strokeStateCleaner = strokeStateCleaner;
    }

    public void tick() {
        updateToolActionBars();
        updatePlacementPreviews();
        updatePaintPanelTooltips();
        updateArtworkPreviewTooltips();
        if (canvasLifecycle.isEmpty()) {
            drawingSessions.clearAll();
            advancedToolSessions.clearAll();
            strokeStateCleaner.run();
            return;
        }

        updateAdvancedPreviews();
        if (paintingInteractions != null) {
            paintingInteractions.paintForActivePlayers(server.getOnlinePlayers());
        }
    }

    private void updateToolActionBars() {
        if (toolItems == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : server.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!toolItems.isPixelPainterTool(player.getInventory().getItemInMainHand())) {
                toolHintActionBarTimes.remove(playerId);
                continue;
            }
            Long lastShownAt = toolHintActionBarTimes.get(playerId);
            if (lastShownAt != null && now - lastShownAt < TOOL_HINT_ACTION_BAR_INTERVAL_MILLIS) {
                continue;
            }
            boolean advanced = advancedToolModes.getOrDefault(playerId, false);
            player.sendActionBar(Component.text(advanced ? "F: 기본도구" : "F: 고급도구"));
            toolHintActionBarTimes.put(playerId, now);
        }
    }

    private void updatePlacementPreviews() {
        if (placementModeWorkflow != null) {
            placementModeWorkflow.updateArtworkPreviews();
            placementModeWorkflow.updateCanvasPreviews();
            placementModeWorkflow.updateExhibitRemovalActionBars();
        }
        if (paletteWorkflow != null) {
            paletteWorkflow.updatePlacementPreviews();
        }
        if (manualStationWorkflow != null) {
            manualStationWorkflow.updatePlacementPreviews();
        }
    }

    private void updatePaintPanelTooltips() {
        if (paintPanels == null) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            paintPanels.updateTooltip(player, paintPanelInteractionDistance);
        }
    }

    private void updateArtworkPreviewTooltips() {
        if (artworkGalleryWorkflow == null) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            artworkGalleryWorkflow.updateTooltip(player);
        }
    }

    private void updateAdvancedPreviews() {
        if (advancedToolWorkflow != null) {
            advancedToolWorkflow.updatePreviews();
        }
    }
}
