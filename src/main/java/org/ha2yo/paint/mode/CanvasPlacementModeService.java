package org.ha2yo.paint.mode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.manager.InventorySnapshot;
import org.ha2yo.paint.model.session.ArtworkPlacementCandidate;
import org.ha2yo.paint.model.session.CanvasPlacementSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class CanvasPlacementModeService {
    private final Paint plugin;
    private final PlayerModeStore<CanvasPlacementSession> sessions = new PlayerModeStore<>();
    private final Map<UUID, Consumer<Player>> placementCompletions = new HashMap<>();
    private final NamespacedKey previewActionKey;
    private final int toolSlot;
    private final int defaultDistance;
    private final int maxDistance;
    private final long armDelayMillis;
    private final String previewAction;
    private final StartHooks startHooks;
    private final InventoryCleaner inventoryCleaner;
    private final InventoryRestorer inventoryRestorer;
    private final CandidateResolver candidateResolver;
    private final PreviewSpawner previewSpawner;
    private final Consumer<CanvasPlacementSession> previewRemover;
    private final PlacementHandler placementHandler;
    private final BiConsumer<UUID, InventorySnapshot> placedInventoryHandler;
    private final Consumer<Player> toolGiver;

    public CanvasPlacementModeService(
            Paint plugin,
            NamespacedKey previewActionKey,
            int toolSlot,
            int defaultDistance,
            int maxDistance,
            long armDelayMillis,
            String previewAction,
            StartHooks startHooks,
            InventoryCleaner inventoryCleaner,
            InventoryRestorer inventoryRestorer,
            CandidateResolver candidateResolver,
            PreviewSpawner previewSpawner,
            Consumer<CanvasPlacementSession> previewRemover,
            PlacementHandler placementHandler,
            BiConsumer<UUID, InventorySnapshot> placedInventoryHandler,
            Consumer<Player> toolGiver
    ) {
        this.plugin = plugin;
        this.previewActionKey = previewActionKey;
        this.toolSlot = toolSlot;
        this.defaultDistance = defaultDistance;
        this.maxDistance = maxDistance;
        this.armDelayMillis = armDelayMillis;
        this.previewAction = previewAction;
        this.startHooks = startHooks;
        this.inventoryCleaner = inventoryCleaner;
        this.inventoryRestorer = inventoryRestorer;
        this.candidateResolver = candidateResolver;
        this.previewSpawner = previewSpawner;
        this.previewRemover = previewRemover;
        this.placementHandler = placementHandler;
        this.placedInventoryHandler = placedInventoryHandler;
        this.toolGiver = toolGiver;
    }

    public boolean contains(UUID playerId) {
        return sessions.contains(playerId);
    }

    public void start(Player player, int width, int height) {
        start(player, width, height, null);
    }

    public void start(Player player, int width, int height, Consumer<Player> placementCompletion) {
        startHooks.beforeStart(player);
        sessions.put(
                player.getUniqueId(),
                new CanvasPlacementSession(width, height, defaultDistance, List.of(), null, System.currentTimeMillis())
        );
        if (placementCompletion == null) {
            placementCompletions.remove(player.getUniqueId());
        } else {
            placementCompletions.put(player.getUniqueId(), placementCompletion);
        }
        giveTool(player);
        update(player);
        player.sendMessage(ChatColor.YELLOW + "캔버스를 설치할 공간을 바라보고 우클릭을 눌러 설치하세요. (좌클릭: 취소)");
    }

    public void giveTool(Player player) {
        sessions.captureInventoryIfAbsent(player);
        inventoryCleaner.clear(player);
        ensureTool(player);
    }

    public void ensureTool(Player player) {
        player.getInventory().setItem(toolSlot, tool());
        player.getInventory().setHeldItemSlot(toolSlot);
        player.updateInventory();
    }

    public void adjustDistance(Player player, int delta) {
        if (delta == 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        CanvasPlacementSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        int nextDistance = Math.max(1, Math.min(maxDistance, session.preferredDistance() + delta));
        if (nextDistance == session.preferredDistance()) {
            return;
        }
        sessions.put(playerId, session.withPreferredDistance(nextDistance));
        update(player);
    }

    public boolean handleSwing(Player player) {
        CanvasPlacementSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!session.isArmed(System.currentTimeMillis(), armDelayMillis)) {
            return true;
        }
        end(player, true);
        return true;
    }

    public boolean handleInteract(Player player, Action action) {
        CanvasPlacementSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!session.isArmed(System.currentTimeMillis(), armDelayMillis)) {
            return true;
        }
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            end(player, true);
            return true;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            confirm(player);
            return true;
        }
        return false;
    }

    public boolean confirm(Player player) {
        UUID playerId = player.getUniqueId();
        CanvasPlacementSession session = sessions.get(playerId);
        if (session == null) {
            return false;
        }

        update(player);
        session = sessions.get(playerId);
        ArtworkPlacementCandidate candidate = session == null ? null : session.lastCandidate();
        if (session == null || candidate == null) {
            int distance = session == null ? defaultDistance : session.preferredDistance();
            player.sendMessage(ChatColor.RED + "" + distance + "칸 안에서 캔버스를 설치할 위치를 정해 주세요.");
            return true;
        }
        if (!candidate.valid()) {
            player.sendMessage(ChatColor.RED + "이 위치에는 캔버스를 설치할 수 없습니다.");
            return true;
        }

        InventorySnapshot snapshot = sessions.removeInventory(playerId);
        if (snapshot != null) {
            placedInventoryHandler.accept(playerId, snapshot);
        }

        placementHandler.place(player, candidate, session.width(), session.height());
        CanvasPlacementSession finished = sessions.remove(playerId);
        previewRemover.accept(finished);
        Consumer<Player> completion = placementCompletions.remove(playerId);
        if (completion != null) {
            completion.accept(player);
        }
        toolGiver.accept(player);
        player.sendMessage(ChatColor.GREEN + "Paint 캔버스를 설치했습니다. (" + session.width() + "x" + session.height() + ")");
        return true;
    }

    public void updateAll() {
        if (sessions.isEmpty()) {
            return;
        }
        for (UUID playerId : sessions.playerIds()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                previewRemover.accept(sessions.remove(playerId));
                continue;
            }
            update(player);
        }
    }

    public void update(Player player) {
        UUID playerId = player.getUniqueId();
        CanvasPlacementSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        ArtworkPlacementCandidate candidate = candidateResolver.resolve(player, session.width(), session.height(), session.preferredDistance());
        if ((candidate == null && session.lastCandidate() == null)
                || (candidate != null && candidate.equals(session.lastCandidate()))) {
            return;
        }

        previewRemover.accept(session);
        List<UUID> displayIds = candidate == null ? List.of() : previewSpawner.spawn(playerId, candidate, session.width(), session.height());
        sessions.put(playerId, session.withPreview(displayIds, candidate));
    }

    public void end(Player player, boolean sendCancelMessage) {
        UUID playerId = player.getUniqueId();
        CanvasPlacementSession session = sessions.remove(playerId);
        placementCompletions.remove(playerId);
        previewRemover.accept(session);
        restoreInventory(player);
        if (sendCancelMessage) {
            player.sendMessage(ChatColor.YELLOW + "캔버스 설치를 취소했습니다.");
        }
    }

    public void clearAll() {
        for (CanvasPlacementSession session : sessions.sessions()) {
            previewRemover.accept(session);
        }
        sessions.clearSessions();
        sessions.clearInventories();
        placementCompletions.clear();
    }

    public void syncVisibility(Player viewer) {
        for (Map.Entry<UUID, CanvasPlacementSession> entry : sessions.entries()) {
            boolean owner = viewer.getUniqueId().equals(entry.getKey());
            for (UUID entityId : entry.getValue().displayIds()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null) {
                    continue;
                }
                if (owner) {
                    viewer.showEntity(plugin, entity);
                } else {
                    viewer.hideEntity(plugin, entity);
                }
            }
        }
    }

    private ItemStack tool() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "캔버스 설치/취소");
        meta.getPersistentDataContainer().set(previewActionKey, PersistentDataType.STRING, previewAction);
        item.setItemMeta(meta);
        return item;
    }

    private void restoreInventory(Player player) {
        InventorySnapshot snapshot = sessions.removeInventory(player.getUniqueId());
        inventoryRestorer.restore(player, snapshot);
    }

    @FunctionalInterface
    public interface StartHooks {
        void beforeStart(Player player);
    }

    @FunctionalInterface
    public interface InventoryCleaner {
        void clear(Player player);
    }

    @FunctionalInterface
    public interface InventoryRestorer {
        void restore(Player player, InventorySnapshot snapshot);
    }

    @FunctionalInterface
    public interface CandidateResolver {
        ArtworkPlacementCandidate resolve(Player player, int width, int height, int preferredDistance);
    }

    @FunctionalInterface
    public interface PreviewSpawner {
        List<UUID> spawn(UUID ownerId, ArtworkPlacementCandidate candidate, int width, int height);
    }

    @FunctionalInterface
    public interface PlacementHandler {
        void place(Player player, ArtworkPlacementCandidate candidate, int width, int height);
    }
}
