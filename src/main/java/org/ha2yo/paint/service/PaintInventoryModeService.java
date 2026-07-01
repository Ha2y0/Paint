package org.ha2yo.paint.service;

import org.bukkit.entity.Player;
import org.ha2yo.paint.manager.InventorySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class PaintInventoryModeService {
    private final Map<UUID, InventorySnapshot> savedInventories = new HashMap<>();

    public void saveIfNeeded(Player player) {
        savedInventories.computeIfAbsent(player.getUniqueId(), ignored -> InventorySnapshot.capture(player));
    }

    public void restore(Player player) {
        InventorySnapshot snapshot = savedInventories.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }

        snapshot.restore(player);
        player.updateInventory();
    }

    public void restoreWhenNoMode(Player player, InventorySnapshot snapshot, Predicate<UUID> activeModeCheck) {
        if (snapshot == null || activeModeCheck.test(player.getUniqueId())) {
            return;
        }
        snapshot.restore(player);
        player.updateInventory();
    }

    public InventorySnapshot snapshot(UUID playerId) {
        return savedInventories.get(playerId);
    }

    public void putIfAbsent(UUID playerId, InventorySnapshot snapshot) {
        savedInventories.putIfAbsent(playerId, snapshot);
    }

    public void clearAll() {
        savedInventories.clear();
    }
}
