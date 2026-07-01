package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.model.tool.Tool;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ToolItemService {
    public static final String PALETTE_TITLE = "픽셀페인터 색상";

    private final NamespacedKey paletteColorKey;
    private final NamespacedKey brushSizeDeltaKey;
    private final NamespacedKey brushSizeValueKey;
    private final NamespacedKey toolKey;

    public ToolItemService(
            NamespacedKey paletteColorKey,
            NamespacedKey brushSizeDeltaKey,
            NamespacedKey brushSizeValueKey,
            NamespacedKey toolKey
    ) {
        this.paletteColorKey = paletteColorKey;
        this.brushSizeDeltaKey = brushSizeDeltaKey;
        this.brushSizeValueKey = brushSizeValueKey;
        this.toolKey = toolKey;
    }

    public void giveToolItems(Player player, Map<UUID, Tool> selectedTools) {
        giveToolItems(player, selectedTools, false);
    }

    public void giveToolItems(Player player, Map<UUID, Tool> selectedTools, boolean advanced) {
        giveToolItems(player, selectedTools, advanced, Tool.defaultTool(advanced).slot());
    }

    public void giveToolItems(Player player, Map<UUID, Tool> selectedTools, boolean advanced, int preferredSlot) {
        for (int slot = 0; slot <= 8; slot++) {
            player.getInventory().setItem(slot, null);
        }
        for (Tool tool : Tool.tools(advanced)) {
            player.getInventory().setItem(tool.slot(), createToolItem(tool));
        }
        Tool selectedTool = Tool.fromSlot(preferredSlot, advanced);
        if (selectedTool == null) {
            selectedTool = Tool.defaultTool(advanced);
        }
        selectedTools.put(player.getUniqueId(), selectedTool);
        player.getInventory().setHeldItemSlot(selectedTool.slot());
    }

    public boolean usesPixelPainterTool(InventoryClickEvent event) {
        if (isPixelPainterTool(event.getCurrentItem()) || isPixelPainterTool(event.getCursor())) {
            return true;
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0 || hotbarButton > 8) {
            return false;
        }

        return isPixelPainterTool(event.getWhoClicked().getInventory().getItem(hotbarButton));
    }

    public boolean isPixelPainterTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.STRING);
    }

    public void openPalette(Player player, int brushRadius) {
        Inventory inventory = Bukkit.createInventory(null, 54, PALETTE_TITLE);
        int slot = 0;

        for (int row = 0; row < 4; row++) {
            float brightness = 1.0F - row * 0.18F;
            float saturation = row == 0 ? 0.7F : 0.9F;
            for (int column = 0; column < 9; column++) {
                float hue = column / 9.0F;
                Color color = Color.getHSBColor(hue, saturation, brightness);
                inventory.setItem(slot++, createPaletteItem(color));
            }
        }

        for (int column = 0; column < 9; column++) {
            int value = Math.round(column / 8.0F * 255.0F);
            Color color = new Color(value, value, value);
            inventory.setItem(36 + column, createPaletteItem(color));
        }

        inventory.setItem(45, createBrushDeltaItem(Material.RED_DYE, "붓 -1", -1));
        inventory.setItem(46, createBrushDeltaItem(Material.ORANGE_DYE, "붓 -3", -3));
        inventory.setItem(47, createBrushSizeItem(1));
        inventory.setItem(48, createBrushSizeItem(3));
        inventory.setItem(49, createBrushStatusItem(brushRadius));
        inventory.setItem(50, createBrushSizeItem(8));
        inventory.setItem(51, createBrushSizeItem(12));
        inventory.setItem(52, createBrushDeltaItem(Material.LIME_DYE, "붓 +3", 3));
        inventory.setItem(53, createBrushDeltaItem(Material.GREEN_DYE, "붓 +1", 1));

        player.openInventory(inventory);
    }

    private ItemStack createToolItem(Tool tool) {
        ItemStack item = new ItemStack(tool.material());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + tool.displayName());
        meta.setLore(List.of(ChatColor.GRAY + tool.description()));
        if (tool.itemModel() != null) {
            meta.setItemModel(new NamespacedKey("paint", tool.itemModel()));
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPaletteItem(Color color) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "#" + hexColor(color));
        meta.setLore(List.of(ChatColor.GRAY + "클릭해서 이 색상을 선택합니다"));
        meta.setItemModel(new NamespacedKey("paint", paletteModelId(color)));
        meta.getPersistentDataContainer().set(paletteColorKey, PersistentDataType.INTEGER, color.getRGB() & 0xFFFFFF);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBrushDeltaItem(Material material, String name, int delta) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(List.of(ChatColor.GRAY + "클릭해서 붓 크기를 조절합니다"));
        meta.getPersistentDataContainer().set(brushSizeDeltaKey, PersistentDataType.INTEGER, delta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBrushSizeItem(int radius) {
        ItemStack item = new ItemStack(Material.SNOWBALL, Math.max(1, Math.min(64, radius)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "붓 " + radius + "px");
        meta.setLore(List.of(ChatColor.GRAY + "클릭해서 붓 크기를 설정합니다"));
        meta.getPersistentDataContainer().set(brushSizeValueKey, PersistentDataType.INTEGER, radius);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBrushStatusItem(int radius) {
        ItemStack item = new ItemStack(Material.BRUSH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "현재 붓: " + radius + "px");
        meta.setLore(List.of(ChatColor.GRAY + "주변 버튼으로 크기를 바꿉니다"));
        item.setItemMeta(meta);
        return item;
    }

    private String hexColor(Color color) {
        return String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private String paletteModelId(Color color) {
        return "palette_color_" + hexColor(color).toLowerCase(Locale.ROOT);
    }
}
