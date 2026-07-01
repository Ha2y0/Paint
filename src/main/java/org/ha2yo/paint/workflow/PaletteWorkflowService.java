package org.ha2yo.paint.workflow;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.mode.PlayerModeStore;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.session.ArtworkPlacementCandidate;
import org.ha2yo.paint.model.session.PalettePlacementSession;
import org.ha2yo.paint.model.tool.PaletteMode;
import org.ha2yo.paint.renderer.PaletteMapRenderer;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.service.PaletteBoardService.PaletteLook;
import org.ha2yo.paint.service.PlacementPreviewService;
import org.ha2yo.paint.service.ToolItemService;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.ha2yo.paint.renderer.PaletteMapRenderer.PALETTE_BRUSH_RESET_ACTION;

public final class PaletteWorkflowService {
    private static final PaletteMode DEFAULT_PALETTE_MODE = PaletteMode.GRADIENT;

    private final PlayerModeStore<PalettePlacementSession> placementSessions = new PlayerModeStore<>();
    private final PaletteBoardService paletteBoards;
    private final Supplier<PlacementPreviewService> placementPreviews;
    private final ToolItemService toolItems;
    private final NamespacedKey paletteColorKey;
    private final NamespacedKey brushSizeDeltaKey;
    private final NamespacedKey brushSizeValueKey;
    private final Map<UUID, Color> selectedColors;
    private final Map<UUID, Integer> brushRadii;
    private final Map<UUID, PaletteMode> paletteModes;
    private final Map<UUID, Long> lastRightClickTimes;
    private final int defaultBrushRadius;
    private final int maxBrushRadius;
    private final long placementArmDelayMillis;
    private final Predicate<UUID> canvasExists;
    private final Consumer<UUID> drawingStopper;
    private final BooleanSupplier shaderRgbEnabled;

    public PaletteWorkflowService(
            PaletteBoardService paletteBoards,
            Supplier<PlacementPreviewService> placementPreviews,
            ToolItemService toolItems,
            NamespacedKey paletteColorKey,
            NamespacedKey brushSizeDeltaKey,
            NamespacedKey brushSizeValueKey,
            Map<UUID, Color> selectedColors,
            Map<UUID, Integer> brushRadii,
            Map<UUID, PaletteMode> paletteModes,
            Map<UUID, Long> lastRightClickTimes,
            int defaultBrushRadius,
            int maxBrushRadius,
            long placementArmDelayMillis,
            Predicate<UUID> canvasExists,
            Consumer<UUID> drawingStopper,
            BooleanSupplier shaderRgbEnabled
    ) {
        this.paletteBoards = paletteBoards;
        this.placementPreviews = placementPreviews;
        this.toolItems = toolItems;
        this.paletteColorKey = paletteColorKey;
        this.brushSizeDeltaKey = brushSizeDeltaKey;
        this.brushSizeValueKey = brushSizeValueKey;
        this.selectedColors = selectedColors;
        this.brushRadii = brushRadii;
        this.paletteModes = paletteModes;
        this.lastRightClickTimes = lastRightClickTimes;
        this.defaultBrushRadius = defaultBrushRadius;
        this.maxBrushRadius = maxBrushRadius;
        this.placementArmDelayMillis = placementArmDelayMillis;
        this.canvasExists = canvasExists;
        this.drawingStopper = drawingStopper;
        this.shaderRgbEnabled = shaderRgbEnabled;
    }

    public void openBoard(Player player) {
        if (paletteBoards == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (paletteBoards.hasBoard(playerId)) {
            return;
        }
        if (!canvasExists.test(playerId)) {
            player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
            return;
        }

        placementSessions.put(playerId, new PalettePlacementSession(List.of(), null, System.currentTimeMillis()));
        updatePlacementPreview(player);
        player.sendMessage(ChatColor.YELLOW + "팔레트 보드를 놓을 공간을 바라보고 우클릭을 눌러 설치하세요. (좌클릭: 취소)");
    }

    public void sendMaps(PaletteBoard board) {
        if (paletteBoards != null) {
            paletteBoards.sendMaps(board);
        }
    }

    public PaletteLook lookedBoard(Player player) {
        return paletteBoards == null ? null : paletteBoards.looked(player);
    }

    public PaletteLook lookedBoard(Player player, PaletteBoard board) {
        return paletteBoards == null ? null : paletteBoards.looked(player, board);
    }

    public boolean isPlacementActive(UUID playerId) {
        return placementSessions.contains(playerId);
    }

    public boolean handlePlacementInteract(Player player, Action action) {
        PalettePlacementSession session = placementSessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!session.isArmed(System.currentTimeMillis(), placementArmDelayMillis)) {
            return true;
        }
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            endPlacement(player, true);
            return true;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            confirmPlacement(player);
            return true;
        }
        return false;
    }

    public boolean handlePlacementSwing(Player player) {
        PalettePlacementSession session = placementSessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!session.isArmed(System.currentTimeMillis(), placementArmDelayMillis)) {
            return true;
        }
        endPlacement(player, true);
        return true;
    }

    public boolean confirmPlacement(Player player) {
        UUID playerId = player.getUniqueId();
        PalettePlacementSession session = placementSessions.get(playerId);
        if (session == null) {
            return false;
        }

        updatePlacementPreview(player);
        session = placementSessions.get(playerId);
        ArtworkPlacementCandidate candidate = session == null ? null : session.lastCandidate();
        if (candidate == null || !candidate.valid()) {
            player.sendMessage(ChatColor.RED + "이 위치에는 팔레트 보드를 설치할 수 없습니다.");
            return true;
        }

        boolean opened = paletteBoards.openAt(
                player,
                selectedColors.getOrDefault(playerId, Color.BLACK),
                brushRadii.getOrDefault(playerId, defaultBrushRadius),
                DEFAULT_PALETTE_MODE,
                canvasExists.test(playerId),
                candidate
        );
        if (opened) {
            paletteModes.put(playerId, DEFAULT_PALETTE_MODE);
            removePlacementSession(playerId);
        }
        return true;
    }

    public void updatePlacementPreviews() {
        if (paletteBoards == null || placementSessions.isEmpty()) {
            return;
        }
        for (UUID playerId : placementSessions.playerIds()) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                removePlacementSession(playerId);
                continue;
            }
            updatePlacementPreview(player);
        }
    }

    public void endPlacement(Player player, boolean sendCancelMessage) {
        if (removePlacementSession(player.getUniqueId()) && sendCancelMessage) {
            player.sendMessage(ChatColor.YELLOW + "팔레트 보드 설치를 취소했습니다.");
        }
    }

    public void clearPlacementPreviews() {
        for (PalettePlacementSession session : placementSessions.sessions()) {
            removePlacementDisplays(session);
        }
        placementSessions.clearSessions();
    }

    public void handleBoardClick(Player player, PaletteBoard board, PaletteLook look) {
        if (!board.isOwner(player)) {
            drawingStopper.accept(player.getUniqueId());
            return;
        }
        if (look == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        lastRightClickTimes.put(playerId, System.currentTimeMillis());
        Integer brushAction = PaletteMapRenderer.brushActionAt(look.x(), look.y());
        if (brushAction != null) {
            int current = brushRadii.getOrDefault(playerId, defaultBrushRadius);
            int nextRadius = brushAction == PALETTE_BRUSH_RESET_ACTION ? defaultBrushRadius : current + brushAction;
            int clampedRadius = Math.max(1, Math.min(maxBrushRadius, nextRadius));
            brushRadii.put(playerId, clampedRadius);
            board.setBrushRadius(clampedRadius);
            board.incrementVersion();
            sendMaps(board);
            return;
        }

        PaletteMode mode = PaletteMapRenderer.modeAt(look.x(), look.y());
        if (mode != null) {
            board.setMode(mode);
            paletteModes.put(playerId, mode);
            board.incrementVersion();
            sendMaps(board);
            return;
        }

        Color color = PaletteMapRenderer.colorAt(board, look.x(), look.y(), shaderRgbEnabled.getAsBoolean());
        if (color == null) {
            return;
        }

        selectedColors.put(playerId, color);
        if (board.mode() == PaletteMode.GRADIENT) {
            board.setSelectedColorOnly(color);
        } else {
            board.setSelectedColor(color);
            PaletteMapRenderer.updateGradientCursorForColor(board, color);
        }
        board.incrementVersion();
        sendMaps(board);
    }

    public boolean removeBoard(UUID ownerId) {
        removePlacementSession(ownerId);
        return paletteBoards != null && paletteBoards.remove(ownerId);
    }

    public void handleInventoryClick(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        Integer rgb = item.getItemMeta().getPersistentDataContainer().get(paletteColorKey, PersistentDataType.INTEGER);
        if (rgb != null) {
            selectedColors.put(player.getUniqueId(), new Color(rgb));
            player.sendMessage(ChatColor.GREEN + "선택한 색상: #" + String.format("%06X", rgb));
            return;
        }
        Integer delta = item.getItemMeta().getPersistentDataContainer().get(brushSizeDeltaKey, PersistentDataType.INTEGER);
        if (delta != null) {
            setBrushRadius(player, brushRadii.getOrDefault(player.getUniqueId(), defaultBrushRadius) + delta);
            toolItems.openPalette(player, brushRadii.getOrDefault(player.getUniqueId(), defaultBrushRadius));
            return;
        }
        Integer radius = item.getItemMeta().getPersistentDataContainer().get(brushSizeValueKey, PersistentDataType.INTEGER);
        if (radius != null) {
            setBrushRadius(player, radius);
            toolItems.openPalette(player, brushRadii.getOrDefault(player.getUniqueId(), defaultBrushRadius));
        }
    }

    public void setBrushRadius(Player player, int radius) {
        int clamped = Math.max(1, Math.min(maxBrushRadius, radius));
        brushRadii.put(player.getUniqueId(), clamped);
        player.sendMessage(ChatColor.GREEN + "브러시 크기를 " + clamped + "px로 설정했습니다.");
    }

    private void updatePlacementPreview(Player player) {
        UUID playerId = player.getUniqueId();
        PalettePlacementSession session = placementSessions.get(playerId);
        if (session == null || paletteBoards == null) {
            return;
        }

        ArtworkPlacementCandidate candidate = paletteBoards.placementCandidate(player);
        if ((candidate == null && session.lastCandidate() == null)
                || (candidate != null && candidate.equals(session.lastCandidate()))) {
            return;
        }

        removePlacementDisplays(session);
        PlacementPreviewService previews = placementPreviews.get();
        List<UUID> displayIds = candidate == null || previews == null
                ? List.of()
                : previews.spawnDisplays(playerId, candidate, PaletteMapRenderer.PALETTE_BLOCK_WIDTH, PaletteMapRenderer.PALETTE_BLOCK_HEIGHT);
        placementSessions.put(playerId, session.withPreview(displayIds, candidate));
    }

    private boolean removePlacementSession(UUID playerId) {
        PalettePlacementSession session = placementSessions.remove(playerId);
        removePlacementDisplays(session);
        return session != null;
    }

    private void removePlacementDisplays(PalettePlacementSession session) {
        if (session == null) {
            return;
        }
        PlacementPreviewService previews = placementPreviews.get();
        if (previews != null) {
            previews.removeDisplays(session.displayIds());
        }
    }
}
