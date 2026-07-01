package org.ha2yo.paint.workflow;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.manager.InventorySnapshot;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.PaintInventoryModeService;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.ToolItemService;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class InventoryToolWorkflowService {
    private final PaintInventoryModeService inventoryModes;
    private final ToolItemService toolItems;
    private final PaintPanelModeService paintPanelModes;
    private final Supplier<PlacementModeWorkflowService> placementModeWorkflow;
    private final Map<UUID, Tool> selectedTools;
    private final Map<UUID, Boolean> advancedToolModes;
    private final Map<UUID, Integer> rememberedBasicToolSlots;
    private final Map<UUID, Integer> rememberedAdvancedToolSlots;

    public InventoryToolWorkflowService(
            PaintInventoryModeService inventoryModes,
            ToolItemService toolItems,
            PaintPanelModeService paintPanelModes,
            Supplier<PlacementModeWorkflowService> placementModeWorkflow,
            Map<UUID, Tool> selectedTools,
            Map<UUID, Boolean> advancedToolModes,
            Map<UUID, Integer> rememberedBasicToolSlots,
            Map<UUID, Integer> rememberedAdvancedToolSlots
    ) {
        this.inventoryModes = inventoryModes;
        this.toolItems = toolItems;
        this.paintPanelModes = paintPanelModes;
        this.placementModeWorkflow = placementModeWorkflow;
        this.selectedTools = selectedTools;
        this.advancedToolModes = advancedToolModes;
        this.rememberedBasicToolSlots = rememberedBasicToolSlots;
        this.rememberedAdvancedToolSlots = rememberedAdvancedToolSlots;
    }

    public void giveTools(Player player) {
        UUID playerId = player.getUniqueId();
        boolean advanced = advancedToolModes.getOrDefault(playerId, false);
        saveInventoryIfNeeded(player);
        toolItems.giveToolItems(player, selectedTools, advanced, rememberedToolSlot(playerId, advanced, player.getInventory().getHeldItemSlot()));
    }

    public void saveInventoryIfNeeded(Player player) {
        inventoryModes.saveIfNeeded(player);
    }

    public void restoreInventory(Player player) {
        inventoryModes.restore(player);
    }

    public void restoreModeInventory(Player player, InventorySnapshot snapshot) {
        inventoryModes.restoreWhenNoMode(player, snapshot, this::hasActiveToolOverrideMode);
    }

    public void clearStrayPaintTools(Player player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!toolItems.isPixelPainterTool(item)) {
                continue;
            }
            player.getInventory().setItem(slot, null);
            changed = true;
        }
        if (toolItems.isPixelPainterTool(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    public boolean hasActiveToolOverrideMode(UUID playerId) {
        return isPaintPanelModeActive(playerId)
                || isArtworkPlacementModeActive(playerId)
                || isCanvasPlacementModeActive(playerId)
                || isExhibitRemovalModeActive(playerId);
    }

    public boolean isPaintPanelModeActive(UUID playerId) {
        return paintPanelModes != null && paintPanelModes.contains(playerId);
    }

    public boolean isArtworkPlacementModeActive(UUID playerId) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        return workflow != null && workflow.isArtworkActive(playerId);
    }

    public boolean isCanvasPlacementModeActive(UUID playerId) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        return workflow != null && workflow.isCanvasActive(playerId);
    }

    public boolean isExhibitRemovalModeActive(UUID playerId) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        return workflow != null && workflow.isExhibitRemovalActive(playerId);
    }

    public void startPaintPanelModeIfNeeded(Player player) {
        if (paintPanelModes != null) {
            paintPanelModes.startIfNeeded(player, () -> clearArtworkPlacementInventory(player));
        }
    }

    public void ensurePaintPanelTool(Player player) {
        if (paintPanelModes != null) {
            paintPanelModes.ensureTool(player);
        }
    }

    public void endPaintPanelMode(Player player, boolean restoreInventory) {
        if (paintPanelModes != null) {
            paintPanelModes.end(player, restoreInventory);
        }
    }

    public boolean shouldDeferCanvasOwnerInventoryRestore(UUID ownerId) {
        return hasActiveToolOverrideMode(ownerId);
    }

    public void deferCanvasOwnerInventoryRestore(UUID ownerId) {
        InventorySnapshot snapshot = inventoryModes.snapshot(ownerId);
        if (snapshot == null) {
            return;
        }
        if (isPaintPanelModeActive(ownerId)) {
            paintPanelModes.putInventory(ownerId, snapshot);
        }

        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow == null) {
            return;
        }
        if (isArtworkPlacementModeActive(ownerId)) {
            workflow.putArtworkInventory(ownerId, snapshot);
        }
        if (isExhibitRemovalModeActive(ownerId)) {
            workflow.putExhibitRemovalInventory(ownerId, snapshot);
        }
    }

    public void clearArtworkPlacementInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.setItemOnCursor(null);
    }

    public void ensureArtworkPlacementTool(Player player) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.ensureArtworkTool(player);
        }
    }

    public void ensureCanvasPlacementTool(Player player) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.ensureCanvasTool(player);
        }
    }

    public void ensureExhibitRemovalTool(Player player) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.ensureExhibitRemovalTool(player);
        }
    }

    public void restoreArtworkPlacementInventory(Player player) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.restoreArtworkInventory(player);
        }
    }

    public void restoreExhibitRemovalInventory(Player player) {
        PlacementModeWorkflowService workflow = placementModeWorkflow.get();
        if (workflow != null) {
            workflow.restoreExhibitRemovalInventory(player);
        }
    }

    public void rememberToolSlot(UUID playerId, boolean advanced, int slot) {
        if (Tool.fromSlot(slot, advanced) == null) {
            return;
        }
        if (advanced) {
            rememberedAdvancedToolSlots.put(playerId, slot);
            return;
        }
        rememberedBasicToolSlots.put(playerId, slot);
    }

    public int rememberedToolSlot(UUID playerId, boolean advanced, int fallbackSlot) {
        Map<UUID, Integer> slots = advanced ? rememberedAdvancedToolSlots : rememberedBasicToolSlots;
        Integer slot = slots.get(playerId);
        if (slot != null && Tool.fromSlot(slot, advanced) != null) {
            return slot;
        }
        if (Tool.fromSlot(fallbackSlot, advanced) != null) {
            return fallbackSlot;
        }
        return Tool.defaultTool(advanced).slot();
    }
}
