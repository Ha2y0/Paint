package org.ha2yo.paint.workflow;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.model.session.CanvasSize;
import org.ha2yo.paint.service.PaintMenuService;
import org.ha2yo.paint.service.PaintPanelService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PaintPanelWorkflowService {
    private final PaintMenuService paintMenus;
    private final PaintPanelService paintPanels;
    private final Map<UUID, CanvasSize> pendingMenuCanvasSizes;
    private final Set<UUID> pendingCanvasRemoveConfirms;
    private final int defaultCanvasBlockWidth;
    private final int defaultCanvasBlockHeight;
    private final int maxCanvasBlockSize;
    private final Predicate<Player> canvasCreateGuard;
    private final Function<UUID, Boolean> canvasClearer;
    private final Function<UUID, Boolean> canvasRemover;
    private final Consumer<Player> saveHandler;
    private final Consumer<Player> artworkPreviewStarter;
    private final Consumer<Player> exhibitRemovalStarter;
    private final CanvasPlacementStarter canvasPlacementStarter;
    private final Consumer<Player> paintPanelModeStarter;
    private final BiConsumer<Player, Boolean> paintPanelModeEnder;
    private final Consumer<UUID> paintPanelClearer;
    private final Function<Player, Location> panelAnchorResolver;
    private final Function<Player, BlockFace> facingResolver;
    private final ManualStationWorkflowService manualStationWorkflow;

    public PaintPanelWorkflowService(
            PaintMenuService paintMenus,
            PaintPanelService paintPanels,
            Map<UUID, CanvasSize> pendingMenuCanvasSizes,
            Set<UUID> pendingCanvasRemoveConfirms,
            int defaultCanvasBlockWidth,
            int defaultCanvasBlockHeight,
            int maxCanvasBlockSize,
            Predicate<Player> canvasCreateGuard,
            Function<UUID, Boolean> canvasClearer,
            Function<UUID, Boolean> canvasRemover,
            Consumer<Player> saveHandler,
            Consumer<Player> artworkPreviewStarter,
            Consumer<Player> exhibitRemovalStarter,
            CanvasPlacementStarter canvasPlacementStarter,
            Consumer<Player> paintPanelModeStarter,
            BiConsumer<Player, Boolean> paintPanelModeEnder,
            Consumer<UUID> paintPanelClearer,
            Function<Player, Location> panelAnchorResolver,
            Function<Player, BlockFace> facingResolver,
            ManualStationWorkflowService manualStationWorkflow
    ) {
        this.paintMenus = paintMenus;
        this.paintPanels = paintPanels;
        this.pendingMenuCanvasSizes = pendingMenuCanvasSizes;
        this.pendingCanvasRemoveConfirms = pendingCanvasRemoveConfirms;
        this.defaultCanvasBlockWidth = defaultCanvasBlockWidth;
        this.defaultCanvasBlockHeight = defaultCanvasBlockHeight;
        this.maxCanvasBlockSize = maxCanvasBlockSize;
        this.canvasCreateGuard = canvasCreateGuard;
        this.canvasClearer = canvasClearer;
        this.canvasRemover = canvasRemover;
        this.saveHandler = saveHandler;
        this.artworkPreviewStarter = artworkPreviewStarter;
        this.exhibitRemovalStarter = exhibitRemovalStarter;
        this.canvasPlacementStarter = canvasPlacementStarter;
        this.paintPanelModeStarter = paintPanelModeStarter;
        this.paintPanelModeEnder = paintPanelModeEnder;
        this.paintPanelClearer = paintPanelClearer;
        this.panelAnchorResolver = panelAnchorResolver;
        this.facingResolver = facingResolver;
        this.manualStationWorkflow = manualStationWorkflow;
    }

    public void handlePanelAction(Player player, PaintMenuService.MenuAction action) {
        if (manualStationWorkflow != null && manualStationWorkflow.handleControlAction(player, action)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (action != PaintMenuService.MenuAction.REMOVE) {
            pendingCanvasRemoveConfirms.remove(playerId);
        }

        CanvasSize size = pendingMenuCanvasSizes.getOrDefault(playerId, defaultCanvasSize());
        switch (action) {
            case NEW -> {
                if (!canvasCreateGuard.test(player)) {
                    return;
                }
                openCanvasSizeMenu(player);
            }
            case CLEAR -> {
                paintPanelModeEnder.accept(player, true);
                if (canvasClearer.apply(playerId)) {
                    player.sendMessage(ChatColor.YELLOW + "Paint 캔버스를 비웠습니다.");
                } else {
                    player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
                }
            }
            case REMOVE -> {
                if (!pendingCanvasRemoveConfirms.remove(playerId)) {
                    pendingCanvasRemoveConfirms.add(playerId);
                    openMainPanel(player, true);
                    return;
                }
                paintPanelModeEnder.accept(player, true);
                if (canvasRemover.apply(playerId)) {
                    player.sendMessage(ChatColor.YELLOW + "Paint 캔버스를 제거했습니다.");
                } else {
                    player.sendMessage(ChatColor.RED + "제거할 캔버스가 없습니다.");
                }
            }
            case SAVE -> {
                paintPanelModeEnder.accept(player, true);
                saveHandler.accept(player);
            }
            case LIST -> {
                clearPanel(playerId);
                artworkPreviewStarter.accept(player);
            }
            case SHOW -> {
                paintPanelModeEnder.accept(player, true);
                exhibitRemovalStarter.accept(player);
            }
            case WIDTH_DOWN -> openCanvasSizeMenu(player, new CanvasSize(clampCanvasBlockSize(size.width() - 1), size.height()));
            case WIDTH_UP -> openCanvasSizeMenu(player, new CanvasSize(clampCanvasBlockSize(size.width() + 1), size.height()));
            case HEIGHT_DOWN -> openCanvasSizeMenu(player, new CanvasSize(size.width(), clampCanvasBlockSize(size.height() - 1)));
            case HEIGHT_UP -> openCanvasSizeMenu(player, new CanvasSize(size.width(), clampCanvasBlockSize(size.height() + 1)));
            case BACK -> {
                pendingMenuCanvasSizes.remove(playerId);
                openMainPanel(player, false);
            }
            case CREATE_CANVAS -> {
                if (!canvasCreateGuard.test(player)) {
                    return;
                }
                pendingMenuCanvasSizes.remove(playerId);
                paintPanelModeEnder.accept(player, true);
                canvasPlacementStarter.start(player, size.width(), size.height());
            }
            case CANCEL -> {
                clearPending(playerId);
                paintPanelModeEnder.accept(player, true);
            }
            default -> {
            }
        }
    }

    public void handleInventoryMenuClick(Player player, ItemStack item) {
        PaintMenuService.MenuAction action = paintMenus.actionFrom(item);
        if (action == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (action != PaintMenuService.MenuAction.REMOVE) {
            pendingCanvasRemoveConfirms.remove(playerId);
        }
        switch (action) {
            case NEW -> openCanvasSizeMenu(player);
            case CLEAR -> {
                if (canvasClearer.apply(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "Paint 캔버스를 비웠습니다.");
                    return;
                }
                player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
            }
            case REMOVE -> {
                if (!pendingCanvasRemoveConfirms.remove(playerId)) {
                    pendingCanvasRemoveConfirms.add(playerId);
                    player.getOpenInventory().getTopInventory().setItem(16, paintMenus.canvasRemoveItem(true));
                    player.updateInventory();
                    return;
                }
                player.closeInventory();
                if (canvasRemover.apply(playerId)) {
                    player.sendMessage(ChatColor.YELLOW + "Paint 캔버스를 제거했습니다.");
                    return;
                }
                player.sendMessage(ChatColor.RED + "제거할 캔버스가 없습니다.");
            }
            case SAVE -> {
                player.closeInventory();
                saveHandler.accept(player);
            }
            case LIST -> artworkPreviewStarter.accept(player);
            case SHOW -> {
                player.closeInventory();
                exhibitRemovalStarter.accept(player);
            }
            default -> {
            }
        }
    }

    public void handleCanvasSizeMenuClick(Player player, ItemStack item) {
        PaintMenuService.MenuAction action = paintMenus.actionFrom(item);
        if (action == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        CanvasSize size = pendingMenuCanvasSizes.getOrDefault(playerId, defaultCanvasSize());
        switch (action) {
            case WIDTH_DOWN -> openCanvasSizeMenu(player, new CanvasSize(clampCanvasBlockSize(size.width() - 1), size.height()));
            case WIDTH_UP -> openCanvasSizeMenu(player, new CanvasSize(clampCanvasBlockSize(size.width() + 1), size.height()));
            case HEIGHT_DOWN -> openCanvasSizeMenu(player, new CanvasSize(size.width(), clampCanvasBlockSize(size.height() - 1)));
            case HEIGHT_UP -> openCanvasSizeMenu(player, new CanvasSize(size.width(), clampCanvasBlockSize(size.height() + 1)));
            case BACK -> {
                pendingMenuCanvasSizes.remove(playerId);
                paintMenus.openMainMenu(player);
            }
            case CREATE_CANVAS -> {
                pendingMenuCanvasSizes.remove(playerId);
                player.closeInventory();
                canvasPlacementStarter.start(player, size.width(), size.height());
            }
            default -> {
            }
        }
    }

    public void openMainPanel(Player player, boolean confirmRemove) {
        player.closeInventory();
        if (paintPanels == null) {
            paintMenus.openMainMenu(player, confirmRemove);
            return;
        }
        paintPanelModeStarter.accept(player);
        UUID playerId = player.getUniqueId();
        if (paintPanels.isShowing(playerId)
                && paintPanels.showMainMenuAtCurrentLocation(playerId, confirmRemove)) {
            return;
        }
        paintPanels.showMainMenu(playerId, panelAnchorResolver.apply(player), facingResolver.apply(player), confirmRemove);
    }

    public void openCanvasSizeMenu(Player player) {
        openCanvasSizeMenu(player, pendingMenuCanvasSizes.getOrDefault(player.getUniqueId(), defaultCanvasSize()));
    }

    public void openCanvasSizeMenu(Player player, CanvasSize size) {
        CanvasSize clamped = new CanvasSize(clampCanvasBlockSize(size.width()), clampCanvasBlockSize(size.height()));
        pendingMenuCanvasSizes.put(player.getUniqueId(), clamped);
        if (paintPanels != null && paintPanels.isShowing(player.getUniqueId())) {
            paintPanelModeStarter.accept(player);
            if (!paintPanels.showCanvasSizeMenuAtCurrentLocation(player.getUniqueId(), clamped.width(), clamped.height(), maxCanvasBlockSize)) {
                paintPanels.showCanvasSizeMenu(player.getUniqueId(), panelAnchorResolver.apply(player), facingResolver.apply(player), clamped.width(), clamped.height(), maxCanvasBlockSize);
            }
            return;
        }
        paintMenus.openCanvasSizeMenu(player, clamped.width(), clamped.height(), maxCanvasBlockSize);
    }

    public void clearPanel(UUID playerId) {
        pendingCanvasRemoveConfirms.remove(playerId);
        paintPanelClearer.accept(playerId);
    }

    public void clearPending(UUID playerId) {
        pendingMenuCanvasSizes.remove(playerId);
        pendingCanvasRemoveConfirms.remove(playerId);
    }

    public void clearAllPending() {
        pendingMenuCanvasSizes.clear();
        pendingCanvasRemoveConfirms.clear();
    }

    private CanvasSize defaultCanvasSize() {
        return new CanvasSize(defaultCanvasBlockWidth, defaultCanvasBlockHeight);
    }

    private int clampCanvasBlockSize(int value) {
        return Math.max(1, Math.min(maxCanvasBlockSize, value));
    }

    @FunctionalInterface
    public interface CanvasPlacementStarter {
        void start(Player player, int width, int height);
    }
}
