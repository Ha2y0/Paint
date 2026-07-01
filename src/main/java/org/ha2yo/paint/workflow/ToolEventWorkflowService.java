package org.ha2yo.paint.workflow;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.session.SelectionRegion;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.AdvancedToolSessionService;
import org.ha2yo.paint.service.PaintMenuService;
import org.ha2yo.paint.service.ToolItemService;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ToolEventWorkflowService {
    private final Paint plugin;
    private final ToolModeGuardService toolModeGuards;
    private final ToolItemService toolItems;
    private final AdvancedToolSessionService advancedToolSessions;
    private final Map<UUID, Tool> selectedTools;
    private final Map<UUID, Boolean> advancedToolModes;
    private final ToolSlotRememberer toolSlotRememberer;
    private final ToolSlotResolver toolSlotResolver;
    private final Predicate<UUID> artworkGallerySessionChecker;
    private final Predicate<String> paintMenuTitleChecker;
    private final BiConsumer<Player, ItemStack> paintMenuClickHandler;
    private final BiConsumer<Player, ItemStack> canvasSizeMenuClickHandler;
    private final BiConsumer<Player, ItemStack> artworkMenuClickHandler;
    private final BiConsumer<Player, ItemStack> paletteInventoryClickHandler;
    private final Consumer<Player> activeLayerToggler;
    private final Consumer<Player> advancedHoldFinisher;
    private final Consumer<UUID> drawingStopper;
    private final Consumer<UUID> strokeStateCleaner;
    private final BiConsumer<UUID, UUID> advancedPreviewCleaner;
    private final ToolGiver toolGiver;

    public ToolEventWorkflowService(
            Paint plugin,
            ToolModeGuardService toolModeGuards,
            ToolItemService toolItems,
            AdvancedToolSessionService advancedToolSessions,
            Map<UUID, Tool> selectedTools,
            Map<UUID, Boolean> advancedToolModes,
            ToolSlotRememberer toolSlotRememberer,
            ToolSlotResolver toolSlotResolver,
            Predicate<UUID> artworkGallerySessionChecker,
            Predicate<String> paintMenuTitleChecker,
            BiConsumer<Player, ItemStack> paintMenuClickHandler,
            BiConsumer<Player, ItemStack> canvasSizeMenuClickHandler,
            BiConsumer<Player, ItemStack> artworkMenuClickHandler,
            BiConsumer<Player, ItemStack> paletteInventoryClickHandler,
            Consumer<Player> activeLayerToggler,
            Consumer<Player> advancedHoldFinisher,
            Consumer<UUID> drawingStopper,
            Consumer<UUID> strokeStateCleaner,
            BiConsumer<UUID, UUID> advancedPreviewCleaner,
            ToolGiver toolGiver
    ) {
        this.plugin = plugin;
        this.toolModeGuards = toolModeGuards;
        this.toolItems = toolItems;
        this.advancedToolSessions = advancedToolSessions;
        this.selectedTools = selectedTools;
        this.advancedToolModes = advancedToolModes;
        this.toolSlotRememberer = toolSlotRememberer;
        this.toolSlotResolver = toolSlotResolver;
        this.artworkGallerySessionChecker = artworkGallerySessionChecker;
        this.paintMenuTitleChecker = paintMenuTitleChecker;
        this.paintMenuClickHandler = paintMenuClickHandler;
        this.canvasSizeMenuClickHandler = canvasSizeMenuClickHandler;
        this.artworkMenuClickHandler = artworkMenuClickHandler;
        this.paletteInventoryClickHandler = paletteInventoryClickHandler;
        this.activeLayerToggler = activeLayerToggler;
        this.advancedHoldFinisher = advancedHoldFinisher;
        this.drawingStopper = drawingStopper;
        this.strokeStateCleaner = strokeStateCleaner;
        this.advancedPreviewCleaner = advancedPreviewCleaner;
        this.toolGiver = toolGiver;
    }

    public void onToolDrop(PlayerDropItemEvent event) {
        if (toolModeGuards != null && toolModeGuards.guardDrop(event)) {
            return;
        }
        if (!toolItems.isPixelPainterTool(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        restoreToolLayoutNextTick(event.getPlayer());
    }

    public void onToolDrag(InventoryDragEvent event) {
        if (toolModeGuards != null && toolModeGuards.guardDrag(event)) {
            return;
        }
        if (paintMenuTitleChecker.test(event.getView().getTitle())) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getWhoClicked() instanceof Player clickedPlayer ? clickedPlayer : null;
        for (ItemStack item : event.getNewItems().values()) {
            if (toolItems.isPixelPainterTool(item)) {
                event.setCancelled(true);
                if (player != null) {
                    restoreToolLayoutNextTick(player);
                }
                return;
            }
        }
        if (player != null && hasPaintToolInHotbar(player) && affectsHotbarSlot(player, event)) {
            event.setCancelled(true);
            restoreToolLayoutNextTick(player);
        }
    }

    public void onPaletteClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (isCreativeClonePaintToolClick(event)) {
            event.setCancelled(true);
            if (toolItems.isPixelPainterTool(player.getItemOnCursor())) {
                player.setItemOnCursor(null);
            }
            restoreToolLayoutNextTick(player);
            return;
        }
        if (toolModeGuards != null && toolModeGuards.guardInventoryClick(player, event)) {
            return;
        }
        String title = event.getView().getTitle();
        if (PaintMenuService.MAIN_MENU_TITLE.equals(title)) {
            event.setCancelled(true);
            paintMenuClickHandler.accept(player, event.getCurrentItem());
            return;
        }
        if (PaintMenuService.CANVAS_SIZE_MENU_TITLE.equals(title)) {
            event.setCancelled(true);
            canvasSizeMenuClickHandler.accept(player, event.getCurrentItem());
            return;
        }
        if (PaintMenuService.ARTWORK_LIST_TITLE.equals(title)
                || PaintMenuService.ARTWORK_SHOW_TITLE.equals(title)) {
            event.setCancelled(true);
            artworkMenuClickHandler.accept(player, event.getCurrentItem());
            return;
        }
        if (ToolItemService.PALETTE_TITLE.equals(title)) {
            event.setCancelled(true);
            paletteInventoryClickHandler.accept(player, event.getCurrentItem());
            return;
        }
        if (isPaintToolLayoutClick(player, event)) {
            event.setCancelled(true);
            restoreToolLayoutNextTick(player);
        }
    }

    private boolean isCreativeClonePaintToolClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.MIDDLE && event.getAction() != InventoryAction.CLONE_STACK) {
            return false;
        }
        if (toolItems.isPixelPainterTool(event.getCurrentItem()) || toolItems.isPixelPainterTool(event.getCursor())) {
            return true;
        }
        int hotbarButton = event.getHotbarButton();
        return hotbarButton >= 0
                && hotbarButton <= 8
                && toolItems.isPixelPainterTool(event.getWhoClicked().getInventory().getItem(hotbarButton));
    }

    public void onToolSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (toolModeGuards != null && toolModeGuards.guardSlotChange(event)) {
            return;
        }
        if (artworkGallerySessionChecker.test(player.getUniqueId())
                && !toolItems.isPixelPainterTool(player.getInventory().getItem(event.getNewSlot()))) {
            return;
        }
        boolean advanced = advancedToolModes.getOrDefault(player.getUniqueId(), false);
        Tool tool = Tool.fromSlot(event.getNewSlot(), advanced);
        if (tool == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        selectedTools.put(playerId, tool);
        toolSlotRememberer.remember(playerId, advanced, event.getNewSlot());
        drawingStopper.accept(playerId);
        strokeStateCleaner.accept(playerId);
    }

    public void onToolSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (toolModeGuards != null && toolModeGuards.guardSwap(event)) {
            return;
        }
        if (!toolItems.isPixelPainterTool(event.getMainHandItem())
                && !toolItems.isPixelPainterTool(event.getOffHandItem())) {
            return;
        }

        event.setCancelled(true);
        if (player.isSneaking()) {
            activeLayerToggler.accept(player);
            return;
        }
        toggleAdvancedToolMode(player);
    }

    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        advancedHoldFinisher.accept(event.getPlayer());
        drawingStopper.accept(event.getPlayer().getUniqueId());
    }

    public void onPickItem(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        if (!hasPaintToolInHotbar(player)) {
            return;
        }
        event.setCancelled(true);
        restoreToolLayoutNextTick(player);
    }

    public void onToolBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!toolItems.isPixelPainterTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        restoreToolLayoutNextTick(player);
    }

    private void toggleAdvancedToolMode(Player player) {
        UUID playerId = player.getUniqueId();
        boolean wasAdvanced = advancedToolModes.getOrDefault(playerId, false);
        int currentSlot = player.getInventory().getHeldItemSlot();
        toolSlotRememberer.remember(playerId, wasAdvanced, currentSlot);
        boolean advanced = !wasAdvanced;
        drawingStopper.accept(playerId);

        SelectionRegion removedSelection = advancedToolSessions.removeSelection(playerId);
        if (removedSelection != null) {
            advancedPreviewCleaner.accept(playerId, removedSelection.canvasId());
        }

        advancedToolModes.put(playerId, advanced);
        toolGiver.give(player, selectedTools, advanced, toolSlotResolver.resolve(playerId, advanced, currentSlot));
    }

    private boolean isPaintToolLayoutClick(Player player, InventoryClickEvent event) {
        return toolItems.usesPixelPainterTool(event)
                || (hasPaintToolInHotbar(player) && affectsHotbarSlot(player, event));
    }

    private boolean affectsHotbarSlot(Player player, InventoryClickEvent event) {
        if (event.getHotbarButton() >= 0 && event.getHotbarButton() <= 8) {
            return true;
        }
        return event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && event.getSlot() >= 0
                && event.getSlot() <= 8;
    }

    private boolean affectsHotbarSlot(Player player, InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                continue;
            }
            int slot = event.getView().convertSlot(rawSlot);
            if (slot >= 0 && slot <= 8) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPaintToolInHotbar(Player player) {
        for (int slot = 0; slot <= 8; slot++) {
            if (toolItems.isPixelPainterTool(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private void restoreToolLayoutNextTick(Player player) {
        UUID playerId = player.getUniqueId();
        boolean advanced = advancedToolModes.getOrDefault(playerId, false);
        int heldSlot = player.getInventory().getHeldItemSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            toolGiver.give(player, selectedTools, advanced, toolSlotResolver.resolve(playerId, advanced, heldSlot));
            player.updateInventory();
        });
    }

    @FunctionalInterface
    public interface ToolSlotRememberer {
        void remember(UUID playerId, boolean advanced, int slot);
    }

    @FunctionalInterface
    public interface ToolSlotResolver {
        int resolve(UUID playerId, boolean advanced, int fallbackSlot);
    }

    @FunctionalInterface
    public interface ToolGiver {
        void give(Player player, Map<UUID, Tool> selectedTools, boolean advanced, int heldSlot);
    }
}
