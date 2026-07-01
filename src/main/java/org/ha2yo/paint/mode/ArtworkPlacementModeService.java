package org.ha2yo.paint.mode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.manager.InventorySnapshot;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.session.ArtworkPlacementCandidate;
import org.ha2yo.paint.model.session.ArtworkPlacementSession;

import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ArtworkPlacementModeService {
    private static final long FRAME_RIGHT_CLICK_SWING_GRACE_MILLIS = 300L;
    private static final int FRAME_MATERIAL_SLOT = 1;
    private static final Material DEFAULT_FRAME_MATERIAL = Material.DARK_OAK_PLANKS;

    private final Paint plugin;
    private final PlayerModeStore<ArtworkPlacementSession> sessions = new PlayerModeStore<>();
    private final Map<UUID, Consumer<ExhibitFrameStyle>> frameStyleHandlers = new HashMap<>();
    private final Map<UUID, Consumer<Player>> frameSelectionEndHandlers = new HashMap<>();
    private final Map<UUID, Long> frameRightClickTimes = new HashMap<>();
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
    private final Consumer<ArtworkPlacementSession> previewRemover;
    private final PlacementHandler placementHandler;
    private final FrameStyleApplier frameStyleApplier;
    private final Function<Player, UUID> lookedFrameEntityResolver;

    public ArtworkPlacementModeService(
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
            Consumer<ArtworkPlacementSession> previewRemover,
            PlacementHandler placementHandler,
            FrameStyleApplier frameStyleApplier,
            Function<Player, UUID> lookedFrameEntityResolver
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
        this.frameStyleApplier = frameStyleApplier;
        this.lookedFrameEntityResolver = lookedFrameEntityResolver;
    }

    public boolean contains(UUID playerId) {
        return sessions.contains(playerId);
    }

    public void putInventory(UUID playerId, InventorySnapshot snapshot) {
        sessions.putInventory(playerId, snapshot);
    }

    public void start(Player player, PaintArtwork artwork, int width, int height, ExhibitFrameStyle frameStyle) {
        startHooks.beforeStart(player);
        sessions.put(
                player.getUniqueId(),
                new ArtworkPlacementSession(artwork, width, height, defaultDistance, List.of(), null, frameStyle, false, System.currentTimeMillis())
        );
        giveTool(player);
        update(player);
        player.sendMessage(ChatColor.YELLOW + "벽, 바닥 혹은 천장을 바라보고 우클릭을 눌러 설치하세요 (좌클릭: 취소)");
    }

    public void startFrameSelection(
            Player player,
            PaintArtwork artwork,
            int width,
            int height,
            ExhibitFrameStyle frameStyle,
            Consumer<ExhibitFrameStyle> frameStyleHandler,
            Consumer<Player> frameSelectionEndHandler
    ) {
        UUID playerId = player.getUniqueId();
        sessions.put(
                playerId,
                new ArtworkPlacementSession(artwork, width, height, defaultDistance, List.of(), null, frameStyle, true, System.currentTimeMillis())
        );
        frameStyleHandlers.put(playerId, frameStyleHandler);
        frameSelectionEndHandlers.put(playerId, frameSelectionEndHandler);
        giveTool(player);
        player.sendMessage(ChatColor.YELLOW + "전시 테두리 모드입니다. 전시품을 우클릭하면 테두리가 바뀝니다. 좌클릭으로 취소합니다.");
    }

    public void giveTool(Player player) {
        sessions.captureInventoryIfAbsent(player);
        inventoryCleaner.clear(player);
        ensureTool(player);
    }

    public void ensureTool(Player player) {
        ArtworkPlacementSession session = sessions.get(player.getUniqueId());
        boolean frameSelectionOnly = session != null && session.frameSelectionOnly();
        player.getInventory().setItem(toolSlot, placementTool(frameSelectionOnly));
        if (frameSelectionOnly && !isFrameMaterial(player.getInventory().getItem(FRAME_MATERIAL_SLOT))) {
            player.getInventory().setItem(FRAME_MATERIAL_SLOT, new ItemStack(DEFAULT_FRAME_MATERIAL));
        }
        player.getInventory().setHeldItemSlot(toolSlot);
        player.updateInventory();
    }

    public void adjustDistance(Player player, int delta) {
        if (delta == 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        ArtworkPlacementSession session = sessions.get(playerId);
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
        ArtworkPlacementSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!session.isArmed(System.currentTimeMillis(), armDelayMillis)) {
            return true;
        }
        if (session.frameSelectionOnly() && isRecentFrameRightClick(player.getUniqueId(), System.currentTimeMillis())) {
            return true;
        }
        end(player, true);
        return true;
    }

    public boolean handleInteract(Player player, Action action) {
        ArtworkPlacementSession session = sessions.get(player.getUniqueId());
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
            if (session.frameSelectionOnly()) {
                return cycleLookedFrameStyle(player);
            }
            confirm(player);
            return true;
        }
        return false;
    }

    public boolean confirm(Player player) {
        UUID playerId = player.getUniqueId();
        ArtworkPlacementSession session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        if (session.frameSelectionOnly()) {
            return true;
        }

        update(player);
        session = sessions.get(playerId);
        ArtworkPlacementCandidate candidate = session == null ? null : session.lastCandidate();
        if (session == null || candidate == null) {
            int distance = session == null ? defaultDistance : session.preferredDistance();
            player.sendMessage(ChatColor.RED + "" + distance + "칸 안의 벽, 바닥, 천장을 바라보며 설치 위치를 정해 주세요.");
            return true;
        }
        if (!candidate.valid()) {
            player.sendMessage(ChatColor.RED + "이 위치에는 전시할 수 없습니다.");
            return true;
        }

        try {
            placementHandler.place(player, session.artwork(), candidate, session.width(), session.height(), session.frameStyle());
            end(player, false);
            player.sendMessage(ChatColor.GREEN + "그림을 전시했습니다.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not display Paint artwork: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "그림 전시에 실패했습니다. 파일과 콘솔 로그를 확인해 주세요.");
        }
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
        ArtworkPlacementSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        if (session.frameSelectionOnly()) {
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
        ArtworkPlacementSession session = sessions.remove(playerId);
        boolean frameSelectionOnly = session != null && session.frameSelectionOnly();
        frameStyleHandlers.remove(playerId);
        Consumer<Player> frameSelectionEndHandler = frameSelectionEndHandlers.remove(playerId);
        frameRightClickTimes.remove(playerId);
        previewRemover.accept(session);
        restoreInventory(player);
        if (frameSelectionOnly) {
            if (sendCancelMessage) {
                player.sendMessage(ChatColor.YELLOW + "전시 테두리 선택을 마쳤습니다.");
            }
            if (frameSelectionEndHandler != null) {
                frameSelectionEndHandler.accept(player);
            }
            return;
        }
        if (sendCancelMessage) {
            player.sendMessage(ChatColor.YELLOW + "그림 전시를 취소했습니다.");
        }
    }

    public void restoreInventory(Player player) {
        InventorySnapshot snapshot = sessions.removeInventory(player.getUniqueId());
        inventoryRestorer.restore(player, snapshot);
    }

    public void clearSessions() {
        for (ArtworkPlacementSession session : sessions.sessions()) {
            previewRemover.accept(session);
        }
        sessions.clearSessions();
        frameStyleHandlers.clear();
        frameSelectionEndHandlers.clear();
        frameRightClickTimes.clear();
    }

    public void clearInventories() {
        sessions.clearInventories();
    }

    public void syncVisibility(Player viewer) {
        for (Map.Entry<UUID, ArtworkPlacementSession> entry : sessions.entries()) {
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

    private ItemStack placementTool(boolean frameSelectionOnly) {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + (frameSelectionOnly ? "전시 테두리/취소" : "전시 설치/취소"));
        meta.getPersistentDataContainer().set(previewActionKey, PersistentDataType.STRING, previewAction);
        item.setItemMeta(meta);
        return item;
    }

    private void cycleFrameStyle(Player player, ArtworkPlacementSession session) {
        UUID playerId = player.getUniqueId();
        ExhibitFrameStyle nextStyle = session.frameStyle().next();
        sessions.put(playerId, session.withFrameStyle(nextStyle));
        Consumer<ExhibitFrameStyle> handler = frameStyleHandlers.get(playerId);
        if (handler != null) {
            handler.accept(nextStyle);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("전시 테두리: " + nextStyle.displayName()));
    }

    public boolean cycleFrameStyleForExhibit(Player player, UUID entityId) {
        UUID playerId = player.getUniqueId();
        ArtworkPlacementSession session = sessions.get(playerId);
        if (session == null || !session.frameSelectionOnly()) {
            return false;
        }
        if (!session.isArmed(System.currentTimeMillis(), armDelayMillis)) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (isRecentFrameRightClick(playerId, now)) {
            return true;
        }
        try {
            Optional<ExhibitFrameStyle> nextStyle = frameStyleApplier.apply(entityId, frameMaterial(player));
            if (nextStyle.isEmpty()) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("전시품을 바라보고 우클릭하세요."));
                return true;
            }
            frameRightClickTimes.put(playerId, now);
            sessions.put(playerId, session.withFrameStyle(nextStyle.get()));
            Consumer<ExhibitFrameStyle> handler = frameStyleHandlers.get(playerId);
            if (handler != null) {
                handler.accept(nextStyle.get());
            }
            player.sendActionBar(net.kyori.adventure.text.Component.text("전시 테두리: " + nextStyle.get().displayName()));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update Paint exhibit frame style: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "전시 테두리 변경에 실패했습니다. 콘솔 로그를 확인해 주세요.");
            return true;
        }
    }

    private boolean cycleLookedFrameStyle(Player player) {
        UUID entityId = lookedFrameEntityResolver == null ? null : lookedFrameEntityResolver.apply(player);
        if (entityId == null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("전시품을 바라보고 우클릭하세요."));
            return true;
        }
        return cycleFrameStyleForExhibit(player, entityId);
    }

    private boolean isRecentFrameRightClick(UUID playerId, long now) {
        Long lastRightClick = frameRightClickTimes.get(playerId);
        return lastRightClick != null && now - lastRightClick <= FRAME_RIGHT_CLICK_SWING_GRACE_MILLIS;
    }

    private Material frameMaterial(Player player) {
        ItemStack item = player.getInventory().getItem(FRAME_MATERIAL_SLOT);
        return isFrameMaterial(item) ? item.getType() : DEFAULT_FRAME_MATERIAL;
    }

    private boolean isFrameMaterial(ItemStack item) {
        return item != null && item.getType().isBlock() && !item.getType().isAir();
    }

    public boolean isFrameSelectionOnly(UUID playerId) {
        ArtworkPlacementSession session = sessions.get(playerId);
        return session != null && session.frameSelectionOnly();
    }

    public boolean isFrameMaterialSlot(int slot) {
        return slot == FRAME_MATERIAL_SLOT;
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
        void place(Player player, PaintArtwork artwork, ArtworkPlacementCandidate candidate, int width, int height, ExhibitFrameStyle frameStyle) throws IOException;
    }

    @FunctionalInterface
    public interface FrameStyleApplier {
        Optional<ExhibitFrameStyle> apply(UUID entityId, Material frameMaterial) throws IOException;
    }
}
