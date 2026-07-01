package org.ha2yo.paint.mode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.manager.InventorySnapshot;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.session.ExhibitRemovalSession;
import org.ha2yo.paint.model.session.LookedExhibit;
import org.ha2yo.paint.model.session.TimedActionBarMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExhibitRemovalModeService {
    private static final long START_INPUT_GRACE_MILLIS = 300L;

    private final Paint plugin;
    private final PlayerModeStore<ExhibitRemovalSession> sessions = new PlayerModeStore<>();
    private final Map<UUID, Long> lastClicks = new HashMap<>();
    private final Map<UUID, Long> inputGraceUntil = new HashMap<>();
    private final Map<UUID, TimedActionBarMessage> actionBarOverrides = new HashMap<>();
    private final NamespacedKey previewActionKey;
    private final int toolSlot;
    private final long confirmMillis;
    private final long clickCooldownMillis;
    private final long overrideMillis;
    private final String previewAction;
    private final StartHooks startHooks;
    private final InventoryCleaner inventoryCleaner;
    private final InventoryRestorer inventoryRestorer;
    private final LookedExhibitResolver exhibitResolver;
    private final LookedCanvasResolver canvasResolver;
    private final ExhibitPermission exhibitPermission;
    private final CanvasPermission canvasPermission;
    private final ExhibitRemover exhibitRemover;
    private final CanvasRemover canvasRemover;
    private final ExhibitText exhibitText;
    private final CanvasText canvasText;

    public ExhibitRemovalModeService(
            Paint plugin,
            NamespacedKey previewActionKey,
            int toolSlot,
            long confirmMillis,
            long clickCooldownMillis,
            long overrideMillis,
            String previewAction,
            StartHooks startHooks,
            InventoryCleaner inventoryCleaner,
            InventoryRestorer inventoryRestorer,
            LookedExhibitResolver exhibitResolver,
            LookedCanvasResolver canvasResolver,
            ExhibitPermission exhibitPermission,
            CanvasPermission canvasPermission,
            ExhibitRemover exhibitRemover,
            CanvasRemover canvasRemover,
            ExhibitText exhibitText,
            CanvasText canvasText
    ) {
        this.plugin = plugin;
        this.previewActionKey = previewActionKey;
        this.toolSlot = toolSlot;
        this.confirmMillis = confirmMillis;
        this.clickCooldownMillis = clickCooldownMillis;
        this.overrideMillis = overrideMillis;
        this.previewAction = previewAction;
        this.startHooks = startHooks;
        this.inventoryCleaner = inventoryCleaner;
        this.inventoryRestorer = inventoryRestorer;
        this.exhibitResolver = exhibitResolver;
        this.canvasResolver = canvasResolver;
        this.exhibitPermission = exhibitPermission;
        this.canvasPermission = canvasPermission;
        this.exhibitRemover = exhibitRemover;
        this.canvasRemover = canvasRemover;
        this.exhibitText = exhibitText;
        this.canvasText = canvasText;
    }

    public boolean contains(UUID playerId) {
        return sessions.contains(playerId);
    }

    public void putInventory(UUID playerId, InventorySnapshot snapshot) {
        sessions.putInventory(playerId, snapshot);
    }

    public void start(Player player) {
        UUID playerId = player.getUniqueId();
        startHooks.beforeStart(player);
        sessions.put(playerId, new ExhibitRemovalSession(null, null, 0L));
        inputGraceUntil.put(playerId, System.currentTimeMillis() + START_INPUT_GRACE_MILLIS);
        giveTool(player);
        updateActionBar(player);
        player.sendMessage(ChatColor.YELLOW + "그림을 바라보고 우클릭을 눌러 삭제하세요 (좌클릭: 취소)");
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

    public boolean handleInteract(Player player, Action action) {
        if (!sessions.contains(player.getUniqueId())) {
            return false;
        }
        if (isInStartInputGrace(player.getUniqueId())) {
            return true;
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            end(player, true);
            return true;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleConfirmClick(player);
            return true;
        }
        return true;
    }

    public boolean handleSwing(Player player) {
        if (!sessions.contains(player.getUniqueId())) {
            return false;
        }
        if (isInStartInputGrace(player.getUniqueId())) {
            return true;
        }
        end(player, true);
        return true;
    }

    public boolean handleConfirmClick(Player player) {
        UUID playerId = player.getUniqueId();
        ExhibitRemovalSession session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        if (isInStartInputGrace(playerId)) {
            return true;
        }

        long now = System.currentTimeMillis();
        long lastClick = lastClicks.getOrDefault(playerId, 0L);
        if (now - lastClick <= clickCooldownMillis) {
            return true;
        }
        lastClicks.put(playerId, now);
        ensureTool(player);

        LookedExhibit looked = exhibitResolver.look(player);
        if (looked != null) {
            return handleExhibitConfirmClick(player, session, looked, now);
        }

        PlayerCanvas canvas = canvasPermission.canRemove(player) ? canvasResolver.look(player) : null;
        if (canvas != null) {
            return handleCanvasConfirmClick(player, session, canvas, now);
        }

        sessions.put(playerId, session.clearConfirm());
        player.sendActionBar(Component.text(canvasPermission.canRemove(player)
                ? "삭제할 전시품이나 캔버스를 바라봐 주세요."
                : "삭제할 전시품을 바라봐 주세요.", NamedTextColor.RED));
        return true;
    }

    public void updateActionBars() {
        if (sessions.isEmpty()) {
            return;
        }
        for (UUID playerId : sessions.playerIds()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                sessions.remove(playerId);
                lastClicks.remove(playerId);
                inputGraceUntil.remove(playerId);
                actionBarOverrides.remove(playerId);
                continue;
            }
            updateActionBar(player);
        }
    }

    public void updateActionBar(Player player) {
        UUID playerId = player.getUniqueId();
        ExhibitRemovalSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (session.confirmExpired(now)) {
            session = session.clearConfirm();
            sessions.put(playerId, session);
        }

        TimedActionBarMessage override = actionBarOverrides.get(playerId);
        if (override != null) {
            if (override.untilMillis() >= now) {
                player.sendActionBar(override.component());
                return;
            }
            actionBarOverrides.remove(playerId);
        }

        LookedExhibit looked = exhibitResolver.look(player);
        if (looked != null) {
            if (session.confirmExhibitActive(looked.exhibit().id(), now)) {
                player.sendActionBar(Component.text("삭제하려면 한 번 더 우클릭하세요.", NamedTextColor.RED));
                return;
            }
            player.sendActionBar(Component.text(exhibitText.text(looked), NamedTextColor.YELLOW));
            return;
        }

        PlayerCanvas canvas = canvasPermission.canRemove(player) ? canvasResolver.look(player) : null;
        if (canvas != null) {
            if (session.confirmCanvasActive(canvas.ownerId(), now)) {
                player.sendActionBar(Component.text("삭제하려면 한 번 더 우클릭하세요.", NamedTextColor.RED));
                return;
            }
            player.sendActionBar(Component.text(canvasText.text(canvas), NamedTextColor.YELLOW));
            return;
        }

        player.sendActionBar(Component.text(canvasPermission.canRemove(player)
                ? "삭제할 전시품이나 캔버스를 바라봐 주세요."
                : "삭제할 전시품을 바라봐 주세요.", NamedTextColor.GRAY));
    }

    public void end(Player player, boolean sendCancelMessage) {
        UUID playerId = player.getUniqueId();
        sessions.remove(playerId);
        lastClicks.remove(playerId);
        inputGraceUntil.remove(playerId);
        actionBarOverrides.remove(playerId);
        restoreInventory(player);
        if (sendCancelMessage) {
            player.sendMessage(ChatColor.YELLOW + "전시품 삭제 모드를 종료했습니다.");
        }
    }

    public void restoreInventory(Player player) {
        InventorySnapshot snapshot = sessions.removeInventory(player.getUniqueId());
        inventoryRestorer.restore(player, snapshot);
    }

    public void clearAll() {
        sessions.clearSessions();
        lastClicks.clear();
        inputGraceUntil.clear();
        actionBarOverrides.clear();
    }

    public void clearInventories() {
        sessions.clearInventories();
    }

    private boolean handleExhibitConfirmClick(Player player, ExhibitRemovalSession session, LookedExhibit looked, long now) {
        UUID playerId = player.getUniqueId();
        if (!exhibitPermission.canRemove(player, looked)) {
            sessions.put(playerId, session.clearConfirm());
            showActionBarOverride(player, "다른 사람의 전시품은 삭제할 수 없습니다.", NamedTextColor.RED);
            return true;
        }

        if (!session.confirmExhibitActive(looked.exhibit().id(), now)) {
            sessions.put(playerId, session.withExhibitConfirm(looked.exhibit().id(), now + confirmMillis));
            player.sendActionBar(Component.text("삭제하려면 한 번 더 우클릭하세요.", NamedTextColor.RED));
            return true;
        }

        try {
            if (exhibitRemover.remove(looked)) {
                sessions.put(playerId, session.clearConfirm());
                player.sendActionBar(Component.text("전시품을 삭제했습니다.", NamedTextColor.GREEN));
            } else {
                sessions.put(playerId, session.clearConfirm());
                player.sendActionBar(Component.text("삭제할 전시품을 찾지 못했습니다.", NamedTextColor.RED));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not remove Paint exhibit: " + e.getMessage());
            player.sendActionBar(Component.text("전시품 삭제에 실패했습니다. 콘솔 로그를 확인해 주세요.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleCanvasConfirmClick(Player player, ExhibitRemovalSession session, PlayerCanvas canvas, long now) {
        UUID playerId = player.getUniqueId();
        if (!canvasPermission.canRemove(player)) {
            sessions.put(playerId, session.clearConfirm());
            player.sendActionBar(Component.text("캔버스는 관리자만 삭제할 수 있습니다.", NamedTextColor.RED));
            return true;
        }

        if (!session.confirmCanvasActive(canvas.ownerId(), now)) {
            sessions.put(playerId, session.withCanvasConfirm(canvas.ownerId(), now + confirmMillis));
            player.sendActionBar(Component.text("삭제하려면 한 번 더 우클릭하세요.", NamedTextColor.RED));
            return true;
        }

        if (canvasRemover.remove(canvas)) {
            sessions.put(playerId, session.clearConfirm());
            if (sessions.contains(playerId)) {
                ensureTool(player);
            }
            player.sendActionBar(Component.text("캔버스를 삭제했습니다.", NamedTextColor.GREEN));
        } else {
            sessions.put(playerId, session.clearConfirm());
            if (sessions.contains(playerId)) {
                ensureTool(player);
            }
            player.sendActionBar(Component.text("캔버스 삭제에 실패했습니다.", NamedTextColor.RED));
        }
        return true;
    }

    private void showActionBarOverride(Player player, String text, NamedTextColor color) {
        Component component = Component.text(text, color);
        actionBarOverrides.put(
                player.getUniqueId(),
                new TimedActionBarMessage(component, System.currentTimeMillis() + overrideMillis)
        );
        player.sendActionBar(component);
    }

    private boolean isInStartInputGrace(UUID playerId) {
        Long untilMillis = inputGraceUntil.get(playerId);
        if (untilMillis == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now <= untilMillis) {
            return true;
        }
        inputGraceUntil.remove(playerId);
        return false;
    }

    private ItemStack tool() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "전시 제거/취소");
        meta.getPersistentDataContainer().set(previewActionKey, PersistentDataType.STRING, previewAction);
        item.setItemMeta(meta);
        return item;
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
    public interface LookedExhibitResolver {
        LookedExhibit look(Player player);
    }

    @FunctionalInterface
    public interface LookedCanvasResolver {
        PlayerCanvas look(Player player);
    }

    @FunctionalInterface
    public interface ExhibitPermission {
        boolean canRemove(Player player, LookedExhibit looked);
    }

    @FunctionalInterface
    public interface CanvasPermission {
        boolean canRemove(Player player);
    }

    @FunctionalInterface
    public interface ExhibitRemover {
        boolean remove(LookedExhibit looked) throws IOException;
    }

    @FunctionalInterface
    public interface CanvasRemover {
        boolean remove(PlayerCanvas canvas);
    }

    @FunctionalInterface
    public interface ExhibitText {
        String text(LookedExhibit looked);
    }

    @FunctionalInterface
    public interface CanvasText {
        String text(PlayerCanvas canvas);
    }
}
