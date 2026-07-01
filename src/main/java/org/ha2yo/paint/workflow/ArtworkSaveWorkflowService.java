package org.ha2yo.paint.workflow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.service.ArtworkSaveDialogService;
import org.ha2yo.paint.service.ArtworkStorageService;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ArtworkSaveWorkflowService {
    private final Paint plugin;
    private final ArtworkSaveDialogService dialogs;
    private final ArtworkStorageService storage;
    private final Map<UUID, UUID> editingArtworkIds;
    private final Function<UUID, Optional<PaintCanvas>> canvasResolver;
    private final Function<UUID, PlayerCanvas> playerCanvasResolver;
    private final Consumer<UUID> canvasRemover;
    private final Consumer<UUID> cachedPreviewClearer;
    private final Consumer<Player> afterSuccessfulSave;

    public ArtworkSaveWorkflowService(
            Paint plugin,
            ArtworkSaveDialogService dialogs,
            ArtworkStorageService storage,
            Map<UUID, UUID> editingArtworkIds,
            Function<UUID, Optional<PaintCanvas>> canvasResolver,
            Function<UUID, PlayerCanvas> playerCanvasResolver,
            Consumer<UUID> canvasRemover,
            Consumer<UUID> cachedPreviewClearer,
            Consumer<Player> afterSuccessfulSave
    ) {
        this.plugin = plugin;
        this.dialogs = dialogs;
        this.storage = storage;
        this.editingArtworkIds = editingArtworkIds;
        this.canvasResolver = canvasResolver;
        this.playerCanvasResolver = playerCanvasResolver;
        this.canvasRemover = canvasRemover;
        this.cachedPreviewClearer = cachedPreviewClearer;
        this.afterSuccessfulSave = afterSuccessfulSave;
    }

    public void beginNameInput(Player player) {
        if (!canSaveCurrentCanvas(player)) {
            return;
        }
        showNameDialog(player, initialName(player));
    }

    public void saveWithName(Player player, String rawName) {
        saveWithName(player, rawName, false);
    }

    private void saveWithName(Player player, String rawName, boolean reopenDialogOnRetry) {
        UUID playerId = player.getUniqueId();
        String title = sanitizeTitle(rawName);
        if ("취소".equals(title) || "cancel".equalsIgnoreCase(title)) {
            player.sendMessage(ChatColor.YELLOW + "그림 저장을 취소했습니다.");
            return;
        }
        if (title.isBlank()) {
            player.sendMessage(ChatColor.RED + "그림 이름을 입력해 주세요.");
            reopenNameDialog(player, title, reopenDialogOnRetry);
            return;
        }
        if (title.length() > 32) {
            player.sendMessage(ChatColor.RED + "그림 이름은 32자 이하로 입력해 주세요.");
            reopenNameDialog(player, title, reopenDialogOnRetry);
            return;
        }

        UUID editingArtworkId = editingArtworkIds.get(playerId);
        Optional<PaintArtwork> conflict = storage.findByTitle(playerId, title, editingArtworkId);
        if (conflict.isPresent()) {
            showConflictDialog(player, title, editingArtworkId);
            return;
        }

        saveUsingTitle(player, title, targetArtworkIdForTitle(editingArtworkId, title));
    }

    private String initialName(Player player) {
        UUID artworkId = editingArtworkIds.get(player.getUniqueId());
        if (artworkId == null) {
            return "";
        }
        return storage.find(artworkId)
                .map(PaintArtwork::displayName)
                .orElse("");
    }

    private void showNameDialog(Player player, String initialName) {
        String initial = sanitizeTitle(initialName);
        dialogs.showNameDialog(player, initial, (current, title) -> saveWithName(current, title, true));
    }

    private void showConflictDialog(Player player, String title, UUID editingArtworkId) {
        String proposedTitle = storage.uniqueTitle(player.getUniqueId(), title, editingArtworkId);
        dialogs.showConflictDialog(
                player,
                title,
                proposedTitle,
                (current, acceptedTitle) -> saveUsingTitle(current, acceptedTitle, targetArtworkIdForTitle(editingArtworkId, acceptedTitle)),
                this::showNameDialog
        );
    }

    private UUID targetArtworkIdForTitle(UUID editingArtworkId, String title) {
        if (editingArtworkId == null) {
            return null;
        }
        return storage.find(editingArtworkId)
                .filter(artwork -> sameTitle(artwork.title(), title))
                .map(PaintArtwork::id)
                .orElse(null);
    }

    private boolean sameTitle(String left, String right) {
        return sanitizeTitle(left).equalsIgnoreCase(sanitizeTitle(right));
    }

    private void saveUsingTitle(Player player, String saveTitle, UUID targetArtworkId) {
        UUID playerId = player.getUniqueId();
        Optional<PaintCanvas> canvas = canvasResolver.apply(playerId);
        PlayerCanvas playerCanvas = playerCanvasResolver.apply(playerId);
        if (canvas.isEmpty() || playerCanvas == null) {
            player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
            return;
        }
        if (!canvas.get().hasPaintedPixels()) {
            player.sendMessage(ChatColor.RED + "빈 캔버스는 저장할 수 없습니다.");
            return;
        }

        try {
            PixelCanvas.LayerSnapshot layerSnapshot = playerCanvas.pixelCanvas().layerSnapshot();
            PaintArtwork artwork = targetArtworkId == null
                    ? storage.save(player, canvas.get(), saveTitle, layerSnapshot)
                    : storage.overwrite(player, canvas.get(), saveTitle, targetArtworkId, layerSnapshot);
            cachedPreviewClearer.accept(artwork.id());
            player.sendMessage(ChatColor.GREEN + "그림을 저장했습니다. " + ChatColor.WHITE + artwork.displayName());
            canvasRemover.accept(playerId);
            if (afterSuccessfulSave != null) {
                afterSuccessfulSave.accept(player);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save Paint artwork: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "그림 저장에 실패했습니다. 콘솔 로그를 확인해 주세요.");
        }
    }

    private void reopenNameDialog(Player player, String title, boolean reopenDialogOnRetry) {
        if (reopenDialogOnRetry) {
            Bukkit.getScheduler().runTask(plugin, () -> showNameDialog(player, title));
        }
    }

    private boolean canSaveCurrentCanvas(Player player) {
        Optional<PaintCanvas> canvas = canvasResolver.apply(player.getUniqueId());
        if (canvas.isEmpty()) {
            player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
            return false;
        }
        if (!canvas.get().hasPaintedPixels()) {
            player.sendMessage(ChatColor.RED + "빈 캔버스는 저장할 수 없습니다.");
            return false;
        }
        return true;
    }

    private String sanitizeTitle(String rawName) {
        return rawName == null ? "" : rawName.trim().replaceAll("\\p{Cntrl}", "");
    }
}
