package org.ha2yo.paint.workflow;

import org.bukkit.ChatColor;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkPreviewService;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.PaintPanelService;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.service.ToolItemService;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class CanvasEventWorkflowService {
    private final ToolModeGuardService toolModeGuards;
    private final ToolItemService toolItems;
    private final CanvasLifecycleService canvasLifecycle;
    private final PaletteBoardService paletteBoards;
    private final ArtworkDisplayService artworkDisplays;
    private final ArtworkPreviewService artworkPreviews;
    private final PaintPanelService paintPanels;
    private final Map<UUID, Tool> selectedTools;
    private final Map<UUID, Long> lastPaletteRightClickTimes;
    private final long paletteRightClickSwingGraceMillis;
    private final Consumer<UUID> paletteBoardRemover;
    private final Predicate<Player> artworkPreviewLeftClickHandler;
    private final Predicate<Player> exhibitRemovalSwingHandler;
    private final Predicate<Player> canvasPlacementSwingHandler;
    private final Predicate<Player> artworkPlacementSwingHandler;
    private final Predicate<Player> palettePlacementSwingHandler;
    private final Predicate<Player> manualStationPlacementSwingHandler;
    private final Predicate<Player> layerOpacityInteractionLockChecker;

    public CanvasEventWorkflowService(
            ToolModeGuardService toolModeGuards,
            ToolItemService toolItems,
            CanvasLifecycleService canvasLifecycle,
            PaletteBoardService paletteBoards,
            ArtworkDisplayService artworkDisplays,
            ArtworkPreviewService artworkPreviews,
            PaintPanelService paintPanels,
            Map<UUID, Tool> selectedTools,
            Map<UUID, Long> lastPaletteRightClickTimes,
            long paletteRightClickSwingGraceMillis,
            Consumer<UUID> paletteBoardRemover,
            Predicate<Player> artworkPreviewLeftClickHandler,
            Predicate<Player> exhibitRemovalSwingHandler,
            Predicate<Player> canvasPlacementSwingHandler,
            Predicate<Player> artworkPlacementSwingHandler,
            Predicate<Player> palettePlacementSwingHandler,
            Predicate<Player> manualStationPlacementSwingHandler,
            Predicate<Player> layerOpacityInteractionLockChecker
    ) {
        this.toolModeGuards = toolModeGuards;
        this.toolItems = toolItems;
        this.canvasLifecycle = canvasLifecycle;
        this.paletteBoards = paletteBoards;
        this.artworkDisplays = artworkDisplays;
        this.artworkPreviews = artworkPreviews;
        this.paintPanels = paintPanels;
        this.selectedTools = selectedTools;
        this.lastPaletteRightClickTimes = lastPaletteRightClickTimes;
        this.paletteRightClickSwingGraceMillis = paletteRightClickSwingGraceMillis;
        this.paletteBoardRemover = paletteBoardRemover;
        this.artworkPreviewLeftClickHandler = artworkPreviewLeftClickHandler;
        this.exhibitRemovalSwingHandler = exhibitRemovalSwingHandler;
        this.canvasPlacementSwingHandler = canvasPlacementSwingHandler;
        this.artworkPlacementSwingHandler = artworkPlacementSwingHandler;
        this.palettePlacementSwingHandler = palettePlacementSwingHandler;
        this.manualStationPlacementSwingHandler = manualStationPlacementSwingHandler;
        this.layerOpacityInteractionLockChecker = layerOpacityInteractionLockChecker;
    }

    public void onCanvasPress(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (toolModeGuards != null && toolModeGuards.guardCanvasPress(event)) {
            return;
        }
        if (palettePlacementSwingHandler.test(player)) {
            event.setCancelled(true);
            return;
        }
        if (manualStationPlacementSwingHandler.test(player)) {
            event.setCancelled(true);
            return;
        }
        if (selectedTools.getOrDefault(player.getUniqueId(), Tool.PENCIL) == Tool.PALETTE) {
            paletteBoardRemover.accept(player.getUniqueId());
        }

        BlockKey key = BlockKey.from(event.getBlock());
        if (artworkDisplays != null && artworkDisplays.isProtectedBlock(key)) {
            event.setInstaBreak(false);
            event.setCancelled(true);
            return;
        }
        if (artworkPreviews != null && artworkPreviews.isProtectedBlock(key)) {
            event.setInstaBreak(false);
            event.setCancelled(true);
            return;
        }
        PlayerCanvas canvas = canvasLifecycle.canvasByBlock(key);
        PaletteBoard paletteBoard = paletteBoards == null ? null : paletteBoards.boardByBlock(key);
        if (canvas == null && paletteBoard == null) {
            return;
        }

        event.setInstaBreak(false);
        event.setCancelled(true);
    }

    public void onPaletteSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (exhibitRemovalSwingHandler.test(player)) {
            return;
        }
        if (canvasPlacementSwingHandler.test(player)) {
            return;
        }
        if (artworkPlacementSwingHandler.test(player)) {
            return;
        }
        if (palettePlacementSwingHandler.test(player)) {
            return;
        }
        if (manualStationPlacementSwingHandler.test(player)) {
            return;
        }
        if (artworkPreviewLeftClickHandler.test(player)) {
            event.setCancelled(true);
            return;
        }
        if (layerOpacityInteractionLockChecker.test(player)) {
            return;
        }
        if (!toolItems.isPixelPainterTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (selectedTools.getOrDefault(playerId, Tool.PENCIL) != Tool.PALETTE) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastRightClick = lastPaletteRightClickTimes.getOrDefault(playerId, 0L);
        if (now - lastRightClick <= paletteRightClickSwingGraceMillis) {
            return;
        }

        paletteBoardRemover.accept(playerId);
    }

    public void onCanvasBreak(BlockBreakEvent event) {
        BlockKey key = BlockKey.from(event.getBlock());
        PaletteBoard paletteBoard = paletteBoards == null ? null : paletteBoards.boardByBlock(key);
        if (paletteBoard != null) {
            event.setCancelled(true);
            if (paletteBoard.isOwner(event.getPlayer())) {
                paletteBoardRemover.accept(paletteBoard.ownerId());
                event.getPlayer().sendMessage(ChatColor.YELLOW + "Paint 팔레트 보드를 제거했습니다.");
            }
            return;
        }
        if (artworkDisplays != null && artworkDisplays.isProtectedBlock(key)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Paint 전시품 보호 블럭은 부술 수 없습니다.");
            return;
        }
        if (artworkPreviews != null && artworkPreviews.isProtectedBlock(key)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Paint 미리보기 보호 블럭은 부술 수 없습니다.");
            return;
        }
        PlayerCanvas canvas = canvasLifecycle.canvasByBlock(key);
        if (canvas == null) {
            return;
        }
        event.setCancelled(true);
        if (canvas.isOwner(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatColor.RED + "Paint 캔버스는 직접 부술 수 없습니다. /paint remove 명령어를 사용해 주세요.");
        }
    }

    public void onCanvasFrameBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)
                || (!canvasLifecycle.hasFrame(frame.getUniqueId())
                && (paletteBoards == null || !paletteBoards.isFrame(frame.getUniqueId()))
                && (artworkDisplays == null || !artworkDisplays.isDisplayEntity(frame.getUniqueId()))
                && (artworkPreviews == null || !artworkPreviews.isDisplayEntity(frame.getUniqueId()))
                && (paintPanels == null || !paintPanels.isDisplayEntity(frame.getUniqueId())))) {
            return;
        }
        event.setCancelled(true);
    }
}
