package org.ha2yo.paint.service;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.CanvasMapTile;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.renderer.PixelMapRenderer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class CanvasMapSyncService {
    private final Paint plugin;
    private final Function<UUID, PlayerCanvas> canvasResolver;
    private final int tileSendBudget;
    private final int maxTileSendBudget;
    private final int flushBudget;
    private final long observerSendIntervalMillis;
    private final Set<UUID> pendingCanvasMapSends = new LinkedHashSet<>();
    private final Queue<UUID> rendererCompletedCanvasMapSends = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean rendererCompletedCanvasMapSendTaskQueued = new AtomicBoolean();
    private final Map<UUID, Integer> nextCanvasMapTileSendOffsets = new HashMap<>();
    private final Map<UUID, Long> lastObserverCanvasMapSends = new HashMap<>();
    private BukkitTask canvasMapSendTask;

    public CanvasMapSyncService(
            Paint plugin,
            Function<UUID, PlayerCanvas> canvasResolver,
            int tileSendBudget,
            int maxTileSendBudget,
            int flushBudget,
            long observerSendIntervalMillis
    ) {
        this.plugin = plugin;
        this.canvasResolver = canvasResolver;
        this.tileSendBudget = tileSendBudget;
        this.maxTileSendBudget = maxTileSendBudget;
        this.flushBudget = flushBudget;
        this.observerSendIntervalMillis = observerSendIntervalMillis;
    }

    public void send(PlayerCanvas canvas) {
        if (canvas.mapTiles().isEmpty()) {
            return;
        }

        queue(canvas.ownerId());
    }

    public void sendTo(PlayerCanvas canvas, Player player, boolean force) {
        if (canvas.mapTiles().isEmpty() || player == null) {
            return;
        }

        sendToRecipients(canvas, List.of(player), force, null, false);
    }

    public void clearCanvas(UUID canvasId) {
        pendingCanvasMapSends.remove(canvasId);
        rendererCompletedCanvasMapSends.remove(canvasId);
        nextCanvasMapTileSendOffsets.remove(canvasId);
        lastObserverCanvasMapSends.remove(canvasId);
    }

    public void clearAll() {
        pendingCanvasMapSends.clear();
        rendererCompletedCanvasMapSends.clear();
        rendererCompletedCanvasMapSendTaskQueued.set(false);
        nextCanvasMapTileSendOffsets.clear();
        lastObserverCanvasMapSends.clear();
    }

    public void stopTask() {
        if (canvasMapSendTask != null) {
            canvasMapSendTask.cancel();
            canvasMapSendTask = null;
        }
    }

    private void queue(UUID canvasId) {
        pendingCanvasMapSends.add(canvasId);
        ensureTask();
    }

    private void ensureTask() {
        if (canvasMapSendTask != null) {
            return;
        }
        canvasMapSendTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushPending, 0L, 1L);
    }

    private void flushPending() {
        if (pendingCanvasMapSends.isEmpty()) {
            stopTask();
            return;
        }

        List<UUID> canvasIds = new ArrayList<>();
        for (UUID canvasId : pendingCanvasMapSends) {
            canvasIds.add(canvasId);
            if (canvasIds.size() >= flushBudget) {
                break;
            }
        }
        pendingCanvasMapSends.removeAll(canvasIds);

        for (UUID canvasId : canvasIds) {
            PlayerCanvas canvas = canvasResolver.apply(canvasId);
            if (canvas != null) {
                sendNow(canvas);
            }
        }
        if (pendingCanvasMapSends.isEmpty()) {
            stopTask();
        }
    }

    private void sendNow(PlayerCanvas canvas) {
        if (canvas.mapTiles().isEmpty()) {
            return;
        }

        int[] dirtyTileIndexes = canvas.pixelCanvas().dirtyTileIndexes();
        int[] tileIndexesToSend = limitedDirtyTileIndexes(canvas, dirtyTileIndexes);
        boolean hasDeferredDirtyTiles = dirtyTileIndexes.length > tileIndexesToSend.length;

        List<Player> ownerRecipients = ownerRecipients(canvas);
        if (ownerRecipients.isEmpty()) {
            sendToObserversIfDue(canvas, System.currentTimeMillis());
            nextCanvasMapTileSendOffsets.remove(canvas.ownerId());
            return;
        }

        sendToRecipients(canvas, ownerRecipients, false, tileIndexesToSend, true);
        clearSentDirtyTiles(canvas, ownerRecipients, tileIndexesToSend);
        sendToObserversIfDue(canvas, System.currentTimeMillis());
        queueDeferred(canvas, hasDeferredDirtyTiles);
    }

    private void sendToRecipients(PlayerCanvas canvas, List<Player> recipients, boolean force, int[] dirtyTileIndexes, boolean prepareSynchronously) {
        if (recipients.isEmpty()) {
            return;
        }

        List<Player> validRecipients = new ArrayList<>();
        for (Player player : recipients) {
            if (player != null
                    && canvas.plane().worldId().equals(player.getWorld().getUID())
                    && !canvas.isHiddenFor(player.getUniqueId())) {
                validRecipients.add(player);
            }
        }
        if (validRecipients.isEmpty()) {
            return;
        }

        for (CanvasMapTile tile : canvas.mapTiles()) {
            int tileVersion = canvas.pixelCanvas().tileVersion(tile.tileX(), tile.tileY());
            int tileIndex = tile.tileY() * canvas.pixelCanvas().blockWidth() + tile.tileX();
            if (!force && dirtyTileIndexes != null && dirtyTileIndexes.length > 0 && !containsTileIndex(dirtyTileIndexes, tileIndex)) {
                continue;
            }

            List<Player> recipientsToSend = new ArrayList<>();
            for (Player player : validRecipients) {
                int[] sentTileVersions = canvas.sentTileVersions(player.getUniqueId());
                if (force || sentTileVersions[tileIndex] != tileVersion) {
                    recipientsToSend.add(player);
                }
            }
            if (recipientsToSend.isEmpty()) {
                continue;
            }

            PixelMapRenderer renderer = tile.renderer();
            if (renderer != null) {
                if (prepareSynchronously) {
                    renderer.prepareSync(tileVersion, 0);
                } else if (!renderer.prepareAsync(tileVersion, 0, () -> queueAfterRender(canvas.ownerId()))) {
                    continue;
                }
            }

            for (Player player : recipientsToSend) {
                int[] sentTileVersions = canvas.sentTileVersions(player.getUniqueId());
                player.sendMap(tile.mapView());
                sentTileVersions[tileIndex] = tileVersion;
            }
        }
    }

    private void clearSentDirtyTiles(PlayerCanvas canvas, List<Player> recipients, int[] dirtyTileIndexes) {
        if (dirtyTileIndexes.length == 0 || recipients.isEmpty()) {
            return;
        }

        for (int tileIndex : dirtyTileIndexes) {
            CanvasMapTile tile = mapTileByIndex(canvas, tileIndex);
            if (tile == null) {
                canvas.pixelCanvas().clearDirtyTile(tileIndex);
                continue;
            }

            int tileVersion = canvas.pixelCanvas().tileVersion(tile.tileX(), tile.tileY());
            boolean sentToAllRecipients = true;
            for (Player recipient : recipients) {
                int[] sentTileVersions = canvas.sentTileVersions(recipient.getUniqueId());
                if (tileIndex >= sentTileVersions.length || sentTileVersions[tileIndex] != tileVersion) {
                    sentToAllRecipients = false;
                    break;
                }
            }
            if (sentToAllRecipients) {
                canvas.pixelCanvas().clearDirtyTile(tileIndex);
            }
        }
    }

    private int[] limitedDirtyTileIndexes(PlayerCanvas canvas, int[] dirtyTileIndexes) {
        int currentTileSendBudget = canvasMapTileSendBudget(canvas);
        if (dirtyTileIndexes.length <= currentTileSendBudget) {
            nextCanvasMapTileSendOffsets.remove(canvas.ownerId());
            return dirtyTileIndexes;
        }

        int[] limitedTileIndexes = new int[currentTileSendBudget];
        int startOffset = Math.floorMod(
                nextCanvasMapTileSendOffsets.getOrDefault(canvas.ownerId(), 0),
                dirtyTileIndexes.length
        );
        for (int index = 0; index < limitedTileIndexes.length; index++) {
            limitedTileIndexes[index] = dirtyTileIndexes[(startOffset + index) % dirtyTileIndexes.length];
        }
        nextCanvasMapTileSendOffsets.put(
                canvas.ownerId(),
                (startOffset + limitedTileIndexes.length) % dirtyTileIndexes.length
        );
        return limitedTileIndexes;
    }

    private int canvasMapTileSendBudget(PlayerCanvas canvas) {
        int tileCount = canvas.pixelCanvas().blockWidth() * canvas.pixelCanvas().blockHeight();
        if (tileCount <= 25) {
            return Math.max(tileSendBudget, tileCount);
        }
        return Math.min(maxTileSendBudget, Math.max(tileSendBudget, (tileCount + 1) / 2));
    }

    private void queueDeferred(PlayerCanvas canvas, boolean hasDeferredDirtyTiles) {
        if (hasDeferredDirtyTiles) {
            queue(canvas.ownerId());
        } else {
            nextCanvasMapTileSendOffsets.remove(canvas.ownerId());
        }
    }

    private void queueAfterRender(UUID canvasId) {
        rendererCompletedCanvasMapSends.add(canvasId);
        if (!rendererCompletedCanvasMapSendTaskQueued.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, this::flushRendererCompleted);
    }

    private void flushRendererCompleted() {
        UUID canvasId;
        while ((canvasId = rendererCompletedCanvasMapSends.poll()) != null) {
            pendingCanvasMapSends.add(canvasId);
        }
        rendererCompletedCanvasMapSendTaskQueued.set(false);
        if (!rendererCompletedCanvasMapSends.isEmpty()
                && rendererCompletedCanvasMapSendTaskQueued.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTask(plugin, this::flushRendererCompleted);
        }
        if (!pendingCanvasMapSends.isEmpty()) {
            ensureTask();
        }
    }

    private CanvasMapTile mapTileByIndex(PlayerCanvas canvas, int tileIndex) {
        int blockWidth = canvas.pixelCanvas().blockWidth();
        int tileX = tileIndex % blockWidth;
        int tileY = tileIndex / blockWidth;
        for (CanvasMapTile tile : canvas.mapTiles()) {
            if (tile.tileX() == tileX && tile.tileY() == tileY) {
                return tile;
            }
        }
        return null;
    }

    private boolean containsTileIndex(int[] tileIndexes, int tileIndex) {
        if (tileIndexes == null) {
            return false;
        }
        for (int current : tileIndexes) {
            if (current == tileIndex) {
                return true;
            }
        }
        return false;
    }

    private List<Player> ownerRecipients(PlayerCanvas canvas) {
        List<Player> recipients = new ArrayList<>();
        Player owner = plugin.getServer().getPlayer(canvas.ownerId());
        if (owner != null
                && owner.getWorld().getUID().equals(canvas.plane().worldId())
                && !canvas.isHiddenFor(owner.getUniqueId())) {
            recipients.add(owner);
        }
        for (UUID editorId : canvas.editorIds()) {
            Player editor = plugin.getServer().getPlayer(editorId);
            if (editor != null
                    && editor.getWorld().getUID().equals(canvas.plane().worldId())
                    && !canvas.isHiddenFor(editor.getUniqueId())) {
                recipients.add(editor);
            }
        }
        return recipients;
    }

    private List<Player> observerRecipients(PlayerCanvas canvas) {
        List<Player> recipients = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getUniqueId().equals(canvas.ownerId())
                    && !canvas.editorIds().contains(player.getUniqueId())
                    && !canvas.isHiddenFor(player.getUniqueId())
                    && player.getWorld().getUID().equals(canvas.plane().worldId())) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    private void sendToObserversIfDue(PlayerCanvas canvas, long nowMillis) {
        List<Player> observers = observerRecipients(canvas);
        if (observers.isEmpty()) {
            return;
        }

        long lastSentMillis = lastObserverCanvasMapSends.getOrDefault(canvas.ownerId(), 0L);
        if (nowMillis - lastSentMillis < observerSendIntervalMillis) {
            return;
        }

        lastObserverCanvasMapSends.put(canvas.ownerId(), nowMillis);
        sendToRecipients(canvas, observers, false, null, false);
    }
}
