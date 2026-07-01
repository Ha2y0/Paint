package org.ha2yo.paint.service;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.manager.InventorySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaintPanelModeService {
    private final PaintPanelService panels;
    private final NamespacedKey previewActionKey;
    private final int toolSlot;
    private final long clickCooldownMillis;
    private final String toolAction;
    private final double entityInteractionRange;
    private final Map<UUID, InventorySnapshot> savedInventories = new HashMap<>();
    private final Map<UUID, Double> savedEntityInteractionRanges = new HashMap<>();
    private final Map<UUID, Long> lastClicks = new HashMap<>();

    public PaintPanelModeService(
            PaintPanelService panels,
            NamespacedKey previewActionKey,
            int toolSlot,
            long clickCooldownMillis,
            String toolAction,
            double entityInteractionRange
    ) {
        this.panels = panels;
        this.previewActionKey = previewActionKey;
        this.toolSlot = toolSlot;
        this.clickCooldownMillis = clickCooldownMillis;
        this.toolAction = toolAction;
        this.entityInteractionRange = entityInteractionRange;
    }

    public boolean contains(UUID playerId) {
        return savedInventories.containsKey(playerId);
    }

    public void startIfNeeded(Player player, Runnable clearInventory) {
        UUID playerId = player.getUniqueId();
        if (!contains(playerId)) {
            savedInventories.put(playerId, InventorySnapshot.capture(player));
            saveEntityInteractionRange(player);
            clearInventory.run();
        }
        ensureTool(player);
    }

    public void ensureTool(Player player) {
        applyEntityInteractionRange(player);
        player.getInventory().setItem(toolSlot, toolItem());
        player.getInventory().setHeldItemSlot(toolSlot);
        player.updateInventory();
    }

    public void applyInteractionRangeOnly(Player player) {
        saveEntityInteractionRange(player);
        applyEntityInteractionRange(player);
    }

    public void restoreInteractionRangeOnly(Player player) {
        if (!contains(player.getUniqueId())) {
            restoreEntityInteractionRange(player);
        }
    }

    public void end(Player player, boolean restoreInventory) {
        UUID playerId = player.getUniqueId();
        clearPanel(playerId);
        InventorySnapshot snapshot = savedInventories.remove(playerId);
        restoreEntityInteractionRange(player);
        if (restoreInventory && snapshot != null) {
            snapshot.restore(player);
            player.updateInventory();
        }
    }

    public void clearAll() {
        savedInventories.clear();
        savedEntityInteractionRanges.clear();
        lastClicks.clear();
    }

    public void putInventory(UUID playerId, InventorySnapshot snapshot) {
        if (savedInventories.containsKey(playerId)) {
            savedInventories.put(playerId, snapshot);
        }
    }

    public boolean isTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String action = item.getItemMeta().getPersistentDataContainer().get(previewActionKey, PersistentDataType.STRING);
        return toolAction.equals(action);
    }

    public ActionResult lookedAction(Player player, double maxDistance) {
        if (!contains(player.getUniqueId()) || !isTool(player.getInventory().getItemInMainHand())) {
            return ActionResult.none();
        }
        PaintPanelService.PanelClick click = panels.lookedClick(player, maxDistance);
        return click == null ? ActionResult.none() : cooldownAction(player.getUniqueId(), click.action());
    }

    public ActionResult entityAction(Player player, UUID entityId) {
        if (!contains(player.getUniqueId()) || !isTool(player.getInventory().getItemInMainHand())) {
            return ActionResult.none();
        }
        PaintPanelService.PanelClick click = panels.click(entityId);
        return click == null ? ActionResult.none() : cooldownAction(player.getUniqueId(), click.action());
    }

    public void clearPanel(UUID playerId) {
        panels.clear(playerId);
    }

    private ActionResult cooldownAction(UUID playerId, PaintMenuService.MenuAction action) {
        long now = System.currentTimeMillis();
        long lastClick = lastClicks.getOrDefault(playerId, 0L);
        if (now - lastClick <= clickCooldownMillis) {
            return ActionResult.handled();
        }
        lastClicks.put(playerId, now);
        return new ActionResult(true, action);
    }

    private ItemStack toolItem() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Paint 메뉴 조작");
        meta.getPersistentDataContainer().set(previewActionKey, PersistentDataType.STRING, toolAction);
        item.setItemMeta(meta);
        return item;
    }

    private void saveEntityInteractionRange(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attribute != null) {
            savedEntityInteractionRanges.putIfAbsent(player.getUniqueId(), attribute.getBaseValue());
        }
    }

    private void applyEntityInteractionRange(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attribute != null && attribute.getBaseValue() < entityInteractionRange) {
            attribute.setBaseValue(entityInteractionRange);
        }
    }

    private void restoreEntityInteractionRange(Player player) {
        Double previousRange = savedEntityInteractionRanges.remove(player.getUniqueId());
        if (previousRange == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attribute != null) {
            attribute.setBaseValue(previousRange);
        }
    }

    public record ActionResult(boolean clicked, PaintMenuService.MenuAction action) {
        public static ActionResult none() {
            return new ActionResult(false, null);
        }

        public static ActionResult handled() {
            return new ActionResult(true, null);
        }
    }
}
