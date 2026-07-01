package org.ha2yo.paint.manager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record InventorySnapshot(
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack[] extraContents,
        ItemStack cursor,
        int heldItemSlot
) {
    public static InventorySnapshot capture(Player player) {
        return new InventorySnapshot(
                cloneContents(player.getInventory().getStorageContents()),
                cloneContents(player.getInventory().getArmorContents()),
                cloneContents(player.getInventory().getExtraContents()),
                cloneItem(player.getItemOnCursor()),
                player.getInventory().getHeldItemSlot()
        );
    }

    public void restore(Player player) {
        player.getInventory().setStorageContents(cloneContents(storageContents));
        player.getInventory().setArmorContents(cloneContents(armorContents));
        player.getInventory().setExtraContents(cloneContents(extraContents));
        player.setItemOnCursor(cloneItem(cursor));
        player.getInventory().setHeldItemSlot(heldItemSlot);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clones = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            clones[index] = cloneItem(contents[index]);
        }
        return clones;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
