package org.ha2yo.paint.workflow;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ToolModeGuardService {
    private final int toolSlot;
    private final Predicate<UUID> paintPanelActive;
    private final Predicate<UUID> artworkPlacementActive;
    private final Predicate<UUID> artworkFrameSelectionActive;
    private final Predicate<UUID> canvasPlacementActive;
    private final Predicate<UUID> exhibitRemovalActive;
    private final Predicate<Integer> artworkFrameMaterialSlot;
    private final Consumer<Player> paintPanelToolEnsurer;
    private final Consumer<Player> artworkPlacementToolEnsurer;
    private final Consumer<Player> canvasPlacementToolEnsurer;
    private final Consumer<Player> exhibitRemovalToolEnsurer;
    private final BiConsumer<Player, Integer> artworkDistanceAdjuster;
    private final BiConsumer<Player, Integer> canvasDistanceAdjuster;
    private final BiConsumer<Player, Boolean> artworkPlacementEnder;
    private final BiConsumer<Player, Boolean> canvasPlacementEnder;
    private final BiConsumer<Player, Boolean> exhibitRemovalEnder;

    public ToolModeGuardService(
            int toolSlot,
            Predicate<UUID> paintPanelActive,
            Predicate<UUID> artworkPlacementActive,
            Predicate<UUID> artworkFrameSelectionActive,
            Predicate<UUID> canvasPlacementActive,
            Predicate<UUID> exhibitRemovalActive,
            Predicate<Integer> artworkFrameMaterialSlot,
            Consumer<Player> paintPanelToolEnsurer,
            Consumer<Player> artworkPlacementToolEnsurer,
            Consumer<Player> canvasPlacementToolEnsurer,
            Consumer<Player> exhibitRemovalToolEnsurer,
            BiConsumer<Player, Integer> artworkDistanceAdjuster,
            BiConsumer<Player, Integer> canvasDistanceAdjuster,
            BiConsumer<Player, Boolean> artworkPlacementEnder,
            BiConsumer<Player, Boolean> canvasPlacementEnder,
            BiConsumer<Player, Boolean> exhibitRemovalEnder
    ) {
        this.toolSlot = toolSlot;
        this.paintPanelActive = paintPanelActive;
        this.artworkPlacementActive = artworkPlacementActive;
        this.artworkFrameSelectionActive = artworkFrameSelectionActive;
        this.canvasPlacementActive = canvasPlacementActive;
        this.exhibitRemovalActive = exhibitRemovalActive;
        this.artworkFrameMaterialSlot = artworkFrameMaterialSlot;
        this.paintPanelToolEnsurer = paintPanelToolEnsurer;
        this.artworkPlacementToolEnsurer = artworkPlacementToolEnsurer;
        this.canvasPlacementToolEnsurer = canvasPlacementToolEnsurer;
        this.exhibitRemovalToolEnsurer = exhibitRemovalToolEnsurer;
        this.artworkDistanceAdjuster = artworkDistanceAdjuster;
        this.canvasDistanceAdjuster = canvasDistanceAdjuster;
        this.artworkPlacementEnder = artworkPlacementEnder;
        this.canvasPlacementEnder = canvasPlacementEnder;
        this.exhibitRemovalEnder = exhibitRemovalEnder;
    }

    public boolean guardDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!hasGuardedMode(player.getUniqueId())) {
            return false;
        }
        event.setCancelled(true);
        ensureModeTool(player);
        return true;
    }

    public boolean guardDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !hasGuardedMode(player.getUniqueId())) {
            return false;
        }
        event.setCancelled(true);
        ensureModeTool(player);
        return true;
    }

    public boolean guardInventoryClick(Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!hasGuardedMode(playerId)) {
            return false;
        }
        if (artworkFrameSelectionActive.test(playerId) && isArtworkFrameMaterialClick(player, event)) {
            return false;
        }
        event.setCancelled(true);
        ensureModeTool(player);
        return true;
    }

    public boolean guardSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (paintPanelActive.test(playerId)) {
            if (event.getNewSlot() != toolSlot) {
                event.setCancelled(true);
                paintPanelToolEnsurer.accept(player);
            }
            return true;
        }
        if (artworkPlacementActive.test(playerId)) {
            event.setCancelled(true);
            artworkDistanceAdjuster.accept(player, hotbarScrollDelta(event.getPreviousSlot(), event.getNewSlot()));
            artworkPlacementToolEnsurer.accept(player);
            return true;
        }
        if (canvasPlacementActive.test(playerId)) {
            event.setCancelled(true);
            canvasDistanceAdjuster.accept(player, hotbarScrollDelta(event.getPreviousSlot(), event.getNewSlot()));
            canvasPlacementToolEnsurer.accept(player);
            return true;
        }
        if (exhibitRemovalActive.test(playerId)) {
            if (event.getNewSlot() != toolSlot) {
                event.setCancelled(true);
                exhibitRemovalToolEnsurer.accept(player);
            }
            return true;
        }
        return false;
    }

    public boolean guardSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!hasGuardedMode(player.getUniqueId())) {
            return false;
        }
        event.setCancelled(true);
        ensureModeTool(player);
        return true;
    }

    public boolean guardCanvasPress(BlockDamageEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (paintPanelActive.test(playerId)) {
            event.setInstaBreak(false);
            event.setCancelled(true);
            paintPanelToolEnsurer.accept(player);
            return true;
        }
        if (canvasPlacementActive.test(playerId)) {
            event.setInstaBreak(false);
            event.setCancelled(true);
            canvasPlacementEnder.accept(player, true);
            return true;
        }
        if (artworkPlacementActive.test(playerId)) {
            event.setInstaBreak(false);
            event.setCancelled(true);
            artworkPlacementEnder.accept(player, true);
            return true;
        }
        if (exhibitRemovalActive.test(playerId)) {
            event.setInstaBreak(false);
            event.setCancelled(true);
            exhibitRemovalEnder.accept(player, true);
            return true;
        }
        return false;
    }

    private boolean hasGuardedMode(UUID playerId) {
        return paintPanelActive.test(playerId)
                || artworkPlacementActive.test(playerId)
                || canvasPlacementActive.test(playerId)
                || exhibitRemovalActive.test(playerId);
    }

    private boolean isArtworkFrameMaterialClick(Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && artworkFrameMaterialSlot.test(event.getSlot())) {
            return true;
        }
        int hotbarButton = event.getHotbarButton();
        return hotbarButton >= 0 && artworkFrameMaterialSlot.test(hotbarButton);
    }

    private void ensureModeTool(Player player) {
        UUID playerId = player.getUniqueId();
        if (paintPanelActive.test(playerId)) {
            paintPanelToolEnsurer.accept(player);
            return;
        }
        if (artworkPlacementActive.test(playerId)) {
            artworkPlacementToolEnsurer.accept(player);
            return;
        }
        if (canvasPlacementActive.test(playerId)) {
            canvasPlacementToolEnsurer.accept(player);
            return;
        }
        if (exhibitRemovalActive.test(playerId)) {
            exhibitRemovalToolEnsurer.accept(player);
        }
    }

    private int hotbarScrollDelta(int previousSlot, int newSlot) {
        int forward = Math.floorMod(newSlot - previousSlot, 9);
        int backward = Math.floorMod(previousSlot - newSlot, 9);
        if (forward == 0 && backward == 0) {
            return 0;
        }
        return forward <= backward ? forward : -backward;
    }
}
