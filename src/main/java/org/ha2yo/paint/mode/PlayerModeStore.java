package org.ha2yo.paint.mode;

import org.bukkit.entity.Player;
import org.ha2yo.paint.manager.InventorySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public final class PlayerModeStore<S> {
    private final Map<UUID, S> sessions = new HashMap<>();
    private final Map<UUID, InventorySnapshot> inventories = new HashMap<>();

    public boolean contains(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public S get(UUID playerId) {
        return sessions.get(playerId);
    }

    public void put(UUID playerId, S session) {
        sessions.put(playerId, session);
    }

    public S remove(UUID playerId) {
        return sessions.remove(playerId);
    }

    public List<UUID> playerIds() {
        return new ArrayList<>(sessions.keySet());
    }

    public List<S> sessions() {
        return new ArrayList<>(sessions.values());
    }

    public List<Map.Entry<UUID, S>> entries() {
        return new ArrayList<>(sessions.entrySet());
    }

    public void clearSessions() {
        sessions.clear();
    }

    public void captureInventoryIfAbsent(Player player) {
        inventories.computeIfAbsent(player.getUniqueId(), ignored -> InventorySnapshot.capture(player));
    }

    public void putInventory(UUID playerId, InventorySnapshot snapshot) {
        inventories.put(playerId, snapshot);
    }

    public InventorySnapshot removeInventory(UUID playerId) {
        return inventories.remove(playerId);
    }

    public void clearInventories() {
        inventories.clear();
    }
}
