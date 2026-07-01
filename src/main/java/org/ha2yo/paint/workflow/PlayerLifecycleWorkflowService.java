package org.ha2yo.paint.workflow;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.ha2yo.paint.service.AdvancedToolSessionService;
import org.ha2yo.paint.service.DrawingSessionService;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PlayerLifecycleWorkflowService {
    private final DrawingSessionService drawingSessions;
    private final AdvancedToolSessionService advancedToolSessions;
    private final Map<UUID, Boolean> advancedToolModes;
    private final Map<UUID, Integer> rememberedBasicToolSlots;
    private final Map<UUID, Integer> rememberedAdvancedToolSlots;
    private final Map<UUID, Long> lastPaletteRightClickTimes;
    private final Map<UUID, UUID> editingArtworkIds;
    private final Consumer<UUID> paletteBoardRemover;
    private final BiConsumer<Player, Boolean> artworkPreviewEnder;
    private final BiConsumer<Player, Boolean> canvasPlacementPreviewEnder;
    private final BiConsumer<Player, Boolean> artworkPlacementPreviewEnder;
    private final BiConsumer<Player, Boolean> palettePlacementPreviewEnder;
    private final BiConsumer<Player, Boolean> exhibitRemovalModeEnder;
    private final Consumer<UUID> paintPanelPendingClearer;
    private final BiConsumer<Player, Boolean> paintPanelModeEnder;
    private final Consumer<Player> inventoryRestorer;
    private final Consumer<UUID> strokeStateCleaner;
    private final Consumer<Player> manualStationQuitHandler;

    public PlayerLifecycleWorkflowService(
            DrawingSessionService drawingSessions,
            AdvancedToolSessionService advancedToolSessions,
            Map<UUID, Boolean> advancedToolModes,
            Map<UUID, Integer> rememberedBasicToolSlots,
            Map<UUID, Integer> rememberedAdvancedToolSlots,
            Map<UUID, Long> lastPaletteRightClickTimes,
            Map<UUID, UUID> editingArtworkIds,
            Consumer<UUID> paletteBoardRemover,
            BiConsumer<Player, Boolean> artworkPreviewEnder,
            BiConsumer<Player, Boolean> canvasPlacementPreviewEnder,
            BiConsumer<Player, Boolean> artworkPlacementPreviewEnder,
            BiConsumer<Player, Boolean> palettePlacementPreviewEnder,
            BiConsumer<Player, Boolean> exhibitRemovalModeEnder,
            Consumer<UUID> paintPanelPendingClearer,
            BiConsumer<Player, Boolean> paintPanelModeEnder,
            Consumer<Player> inventoryRestorer,
            Consumer<UUID> strokeStateCleaner,
            Consumer<Player> manualStationQuitHandler
    ) {
        this.drawingSessions = drawingSessions;
        this.advancedToolSessions = advancedToolSessions;
        this.advancedToolModes = advancedToolModes;
        this.rememberedBasicToolSlots = rememberedBasicToolSlots;
        this.rememberedAdvancedToolSlots = rememberedAdvancedToolSlots;
        this.lastPaletteRightClickTimes = lastPaletteRightClickTimes;
        this.editingArtworkIds = editingArtworkIds;
        this.paletteBoardRemover = paletteBoardRemover;
        this.artworkPreviewEnder = artworkPreviewEnder;
        this.canvasPlacementPreviewEnder = canvasPlacementPreviewEnder;
        this.artworkPlacementPreviewEnder = artworkPlacementPreviewEnder;
        this.palettePlacementPreviewEnder = palettePlacementPreviewEnder;
        this.exhibitRemovalModeEnder = exhibitRemovalModeEnder;
        this.paintPanelPendingClearer = paintPanelPendingClearer;
        this.paintPanelModeEnder = paintPanelModeEnder;
        this.inventoryRestorer = inventoryRestorer;
        this.strokeStateCleaner = strokeStateCleaner;
        this.manualStationQuitHandler = manualStationQuitHandler;
    }

    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (manualStationQuitHandler != null) {
            manualStationQuitHandler.accept(player);
        }
        drawingSessions.stop(playerId);
        paletteBoardRemover.accept(playerId);
        advancedToolSessions.removePlayer(playerId);
        advancedToolModes.remove(playerId);
        rememberedBasicToolSlots.remove(playerId);
        rememberedAdvancedToolSlots.remove(playerId);
        artworkPreviewEnder.accept(player, true);
        canvasPlacementPreviewEnder.accept(player, false);
        artworkPlacementPreviewEnder.accept(player, false);
        palettePlacementPreviewEnder.accept(player, false);
        exhibitRemovalModeEnder.accept(player, false);
        lastPaletteRightClickTimes.remove(playerId);
        paintPanelPendingClearer.accept(playerId);
        editingArtworkIds.remove(playerId);
        paintPanelModeEnder.accept(player, false);
        inventoryRestorer.accept(player);
        strokeStateCleaner.accept(playerId);
    }
}
