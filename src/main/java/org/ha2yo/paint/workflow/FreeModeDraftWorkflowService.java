package org.ha2yo.paint.workflow;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.FreeModeDraftStorageService;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class FreeModeDraftWorkflowService {
    private final Paint plugin;
    private final FreeModeDraftStorageService storage;
    private final CanvasLifecycleService canvasLifecycle;
    private final CanvasWorkflowService canvasWorkflow;
    private final PlacementUiWorkflowService placementWorkflow;
    private final Consumer<PlayerCanvas> canvasMapSender;
    private final Consumer<PlayerCanvas> layerDisplayUpdater;
    private final Function<UUID, UUID> editingArtworkGetter;
    private final BiConsumer<UUID, UUID> editingArtworkSetter;
    private final Predicate<Player> freeModeChecker;
    private final int pixelsPerBlock;
    private final int maxCanvasBlockSize;

    public FreeModeDraftWorkflowService(
            Paint plugin,
            FreeModeDraftStorageService storage,
            CanvasLifecycleService canvasLifecycle,
            CanvasWorkflowService canvasWorkflow,
            PlacementUiWorkflowService placementWorkflow,
            Consumer<PlayerCanvas> canvasMapSender,
            Consumer<PlayerCanvas> layerDisplayUpdater,
            Function<UUID, UUID> editingArtworkGetter,
            BiConsumer<UUID, UUID> editingArtworkSetter,
            Predicate<Player> freeModeChecker,
            int pixelsPerBlock,
            int maxCanvasBlockSize
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.canvasLifecycle = canvasLifecycle;
        this.canvasWorkflow = canvasWorkflow;
        this.placementWorkflow = placementWorkflow;
        this.canvasMapSender = canvasMapSender;
        this.layerDisplayUpdater = layerDisplayUpdater;
        this.editingArtworkGetter = editingArtworkGetter;
        this.editingArtworkSetter = editingArtworkSetter;
        this.freeModeChecker = freeModeChecker;
        this.pixelsPerBlock = pixelsPerBlock;
        this.maxCanvasBlockSize = maxCanvasBlockSize;
    }

    public boolean hasDraft(Player player) {
        return isFreeMode(player) && storage.exists(player.getUniqueId());
    }

    public void onJoin(Player player) {
        if (!hasDraft(player) || canvasLifecycle.hasCanvas(player.getUniqueId())) {
            return;
        }
        player.sendMessage(ChatColor.AQUA + "이어 그릴 임시 그림이 있습니다.");
        player.sendMessage(ChatColor.GRAY + "새 그림판을 선택하면 임시 그림 크기로 설치하고 이어서 그립니다."
                + " 새로 시작하려면 캔버스 제거를 두 번 누르세요.");
    }

    public void onQuit(Player player) {
        if (!isFreeMode(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerCanvas canvas = canvasLifecycle.canvas(playerId);
        if (canvas == null) {
            return;
        }

        PixelCanvas pixels = canvas.pixelCanvas();
        if (!pixels.hasPaintedPixels()) {
            if (storage.exists(playerId) && !deleteDraft(playerId, "empty canvas cleanup")) {
                return;
            }
            canvasWorkflow.remove(playerId);
            return;
        }

        try {
            storage.save(
                    playerId,
                    pixels.blockWidth(),
                    pixels.blockHeight(),
                    pixels.width(),
                    pixels.height(),
                    pixels.layerSnapshot(),
                    editingArtworkGetter.apply(playerId)
            );
            canvasWorkflow.remove(playerId);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save free-mode draft for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void startCanvasPlacement(Player player, int requestedWidth, int requestedHeight) {
        if (canvasLifecycle.hasCanvas(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "이미 캔버스가 있습니다. 먼저 기존 캔버스를 제거해 주세요.");
            return;
        }
        if (!isFreeMode(player)) {
            placementWorkflow.startCanvas(player, requestedWidth, requestedHeight);
            return;
        }

        Optional<FreeModeDraftStorageService.Draft> stored;
        try {
            stored = storage.load(player.getUniqueId());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load free-mode draft for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "임시 그림을 읽을 수 없습니다. 관리자에게 문의하거나 캔버스 제거로 삭제해 주세요.");
            return;
        }

        if (stored.isEmpty()) {
            placementWorkflow.startCanvas(player, requestedWidth, requestedHeight);
            return;
        }

        FreeModeDraftStorageService.Draft draft = stored.get();
        if (!isValidDraftSize(draft)) {
            plugin.getLogger().warning("Invalid free-mode draft canvas size for " + player.getName()
                    + ": " + draft.blockWidth() + "x" + draft.blockHeight());
            player.sendMessage(ChatColor.RED + "임시 그림의 크기 정보가 올바르지 않습니다. 관리자에게 문의해 주세요.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "임시 그림 " + draft.blockWidth() + "x" + draft.blockHeight()
                + " 크기로 캔버스 설치를 시작합니다.");
        placementWorkflow.startCanvas(
                player,
                draft.blockWidth(),
                draft.blockHeight(),
                current -> restoreDraft(current, draft)
        );
    }

    public boolean removeCanvasOrDraft(UUID playerId) {
        boolean canvasRemoved = canvasWorkflow.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && !isFreeMode(player)) {
            return canvasRemoved;
        }
        boolean draftRemoved = deleteDraft(playerId, "explicit removal");
        return canvasRemoved || draftRemoved;
    }

    public void onCanvasSaved(Player player) {
        if (isFreeMode(player)) {
            deleteDraft(player.getUniqueId(), "completed artwork save");
        }
    }

    private void restoreDraft(Player player, FreeModeDraftStorageService.Draft draft) {
        UUID playerId = player.getUniqueId();
        PlayerCanvas canvas = canvasLifecycle.canvas(playerId);
        if (canvas == null) {
            player.sendMessage(ChatColor.RED + "설치된 캔버스를 찾지 못해 임시 그림을 복원하지 못했습니다.");
            return;
        }

        PixelCanvas pixels = canvas.pixelCanvas();
        if (pixels.blockWidth() != draft.blockWidth()
                || pixels.blockHeight() != draft.blockHeight()
                || pixels.width() != draft.pixelWidth()
                || pixels.height() != draft.pixelHeight()) {
            canvasWorkflow.remove(playerId);
            player.sendMessage(ChatColor.RED + "캔버스 크기가 달라 임시 그림을 복원하지 못했습니다.");
            return;
        }

        try {
            pixels.restore(draft.snapshot());
            canvas.undoSnapshots().clear();
            canvas.redoSnapshots().clear();
            canvas.resetSentTileVersions();
            if (draft.editingArtworkId() != null) {
                editingArtworkSetter.accept(playerId, draft.editingArtworkId());
            }
            canvasMapSender.accept(canvas);
            layerDisplayUpdater.accept(canvas);
            player.sendMessage(ChatColor.GREEN + "임시 그림을 불러왔습니다.");
        } catch (RuntimeException e) {
            canvasWorkflow.remove(playerId);
            plugin.getLogger().warning("Could not restore free-mode draft for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "임시 그림 복원에 실패했습니다. 콘솔 로그를 확인해 주세요.");
        }
    }

    private boolean isValidDraftSize(FreeModeDraftStorageService.Draft draft) {
        if (draft.blockWidth() < 1 || draft.blockWidth() > maxCanvasBlockSize
                || draft.blockHeight() < 1 || draft.blockHeight() > maxCanvasBlockSize) {
            return false;
        }
        try {
            return draft.pixelWidth() == Math.multiplyExact(draft.blockWidth(), pixelsPerBlock)
                    && draft.pixelHeight() == Math.multiplyExact(draft.blockHeight(), pixelsPerBlock);
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private boolean deleteDraft(UUID playerId, String reason) {
        try {
            return storage.delete(playerId);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete free-mode draft during " + reason + " for "
                    + playerId + ": " + e.getMessage());
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "임시 그림 파일을 삭제하지 못했습니다. 콘솔 로그를 확인해 주세요.");
            }
            return false;
        }
    }

    private boolean isFreeMode(Player player) {
        return player != null && freeModeChecker.test(player);
    }
}
