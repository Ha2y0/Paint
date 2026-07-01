package org.ha2yo.paint.workflow;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.session.SelectionRegion;
import org.ha2yo.paint.model.session.ShapeHold;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.renderer.PixelMapRenderer;
import org.ha2yo.paint.service.AdvancedToolSessionService;
import org.ha2yo.paint.service.CanvasLookService.LookResult;
import org.ha2yo.paint.service.DrawingService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AdvancedToolWorkflowService {
    private final Paint plugin;
    private final AdvancedToolSessionService sessions;
    private final DrawingService drawingService;
    private final Map<UUID, Color> selectedColors;
    private final Map<UUID, Integer> brushRadii;
    private final int defaultBrushRadius;
    private final Function<UUID, PlayerCanvas> canvasResolver;
    private final BiFunction<Player, PlayerCanvas, LookResult> extendedLookResolver;
    private final BiFunction<Player, PlayerCanvas, LookResult> clampedLookResolver;
    private final Function<Player, LookResult> shapeLookResolver;
    private final Function<Player, Tool> selectedToolResolver;
    private final Predicate<ItemStack> paintToolPredicate;
    private final Consumer<UUID> drawingStopper;
    private final Consumer<PlayerCanvas> undoPusher;
    private final Consumer<PlayerCanvas> canvasMapSender;
    private final CanvasMapSenderTo canvasMapSenderTo;

    public AdvancedToolWorkflowService(
            Paint plugin,
            AdvancedToolSessionService sessions,
            DrawingService drawingService,
            Map<UUID, Color> selectedColors,
            Map<UUID, Integer> brushRadii,
            int defaultBrushRadius,
            Function<UUID, PlayerCanvas> canvasResolver,
            BiFunction<Player, PlayerCanvas, LookResult> extendedLookResolver,
            BiFunction<Player, PlayerCanvas, LookResult> clampedLookResolver,
            Function<Player, LookResult> shapeLookResolver,
            Function<Player, Tool> selectedToolResolver,
            Predicate<ItemStack> paintToolPredicate,
            Consumer<UUID> drawingStopper,
            Consumer<PlayerCanvas> undoPusher,
            Consumer<PlayerCanvas> canvasMapSender,
            CanvasMapSenderTo canvasMapSenderTo
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.drawingService = drawingService;
        this.selectedColors = selectedColors;
        this.brushRadii = brushRadii;
        this.defaultBrushRadius = defaultBrushRadius;
        this.canvasResolver = canvasResolver;
        this.extendedLookResolver = extendedLookResolver;
        this.clampedLookResolver = clampedLookResolver;
        this.shapeLookResolver = shapeLookResolver;
        this.selectedToolResolver = selectedToolResolver;
        this.paintToolPredicate = paintToolPredicate;
        this.drawingStopper = drawingStopper;
        this.undoPusher = undoPusher;
        this.canvasMapSender = canvasMapSender;
        this.canvasMapSenderTo = canvasMapSenderTo;
    }

    public void beginHold(Player player, LookResult look, Tool tool) {
        UUID playerId = player.getUniqueId();
        clearExistingHold(playerId);
        drawingStopper.accept(playerId);
        sessions.putActiveHold(playerId, new ShapeHold(tool, look.canvas().ownerId(), look.point(), look.point(), nextPreviewVersion()));
        refreshOverlay(playerId);
        canvasMapSenderTo.send(look.canvas(), player, true);
    }

    public void beginPendingHold(Player player, Tool tool) {
        UUID playerId = player.getUniqueId();
        clearExistingHold(playerId);
        drawingStopper.accept(playerId);
        sessions.putPendingHold(playerId, tool);
    }

    public void updatePreviews() {
        updatePendingHolds();
        for (Map.Entry<UUID, ShapeHold> entry : sessions.activeHoldEntries()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            ShapeHold hold = entry.getValue();
            PlayerCanvas canvas = canvasResolver.apply(hold.canvasId());
            if (player == null || canvas == null || !canvas.canEdit(player)) {
                sessions.removeActiveHold(entry.getKey());
                sessions.removeOverlay(entry.getKey());
                continue;
            }

            LookResult look = hold.tool().isShapeTool()
                    ? extendedLookResolver.apply(player, canvas)
                    : clampedLookResolver.apply(player, canvas);
            PixelPoint previewEnd = look == null ? hold.previewEnd() : look.point();
            if (previewEnd.equals(hold.previewEnd())) {
                continue;
            }

            sessions.putActiveHold(entry.getKey(), hold.withPreviewEnd(previewEnd, nextPreviewVersion()));
            refreshOverlay(entry.getKey());
            canvasMapSenderTo.send(canvas, player, true);
        }
    }

    public void finishHold(Player player) {
        UUID playerId = player.getUniqueId();
        ShapeHold hold = sessions.removeActiveHold(playerId);
        if (hold == null) {
            return;
        }
        sessions.removeOverlay(playerId);

        PlayerCanvas canvas = canvasResolver.apply(hold.canvasId());
        if (canvas == null || !canvas.canEdit(player)) {
            sessions.removeSelection(playerId);
            return;
        }

        LookResult endLook = hold.tool().isShapeTool()
                ? extendedLookResolver.apply(player, canvas)
                : clampedLookResolver.apply(player, canvas);
        PixelPoint endPoint = endLook == null ? hold.previewEnd() : endLook.point();
        if (hold.tool().isShapeTool()) {
            undoPusher.accept(canvas);
            drawingService.drawShape(
                    canvas.pixelCanvas(),
                    hold.start(),
                    endPoint,
                    selectedColors.getOrDefault(playerId, Color.BLACK),
                    hold.tool(),
                    brushRadii.getOrDefault(playerId, defaultBrushRadius)
            );
            canvas.resetSentTileVersions();
            canvasMapSender.accept(canvas);
            return;
        }

        if (hold.tool().isSelectionMoveTool()) {
            finishSelectionMoveHold(player, canvas, hold.start(), endPoint);
        }
    }

    public void clearPreview(UUID playerId, UUID canvasId) {
        sessions.removeOverlay(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        PlayerCanvas canvas = canvasResolver.apply(canvasId);
        if (player != null && canvas != null) {
            canvasMapSenderTo.send(canvas, player, true);
        }
    }

    public void cancelHold(UUID playerId) {
        clearExistingHold(playerId);
    }

    public PixelMapRenderer.PreviewOverlay overlay(Player player) {
        return sessions.overlay(player.getUniqueId());
    }

    public void refreshOverlay(UUID playerId) {
        ShapeHold hold = sessions.activeHold(playerId);
        if (hold != null) {
            PlayerCanvas canvas = canvasResolver.apply(hold.canvasId());
            if (canvas == null) {
                sessions.removeOverlay(playerId);
                return;
            }

            Color color = selectedColors.getOrDefault(playerId, Color.BLACK);
            if (hold.tool().isShapeTool()) {
                sessions.putOverlay(playerId, new PixelMapRenderer.PreviewOverlay(
                        hold.canvasId(),
                        hold.previewVersion(),
                        drawingService.shapePixels(
                                        canvas.pixelCanvas(),
                                        hold.start(),
                                        hold.previewEnd(),
                                        hold.tool(),
                                        brushRadii.getOrDefault(playerId, defaultBrushRadius)
                                ).stream()
                                .map(point -> new PixelMapRenderer.PreviewPixel(point.x(), point.y(), color))
                                .toList()
                ));
                return;
            }

            SelectionRegion selected = sessions.selection(playerId);
            SelectionRegion previewRegion = selected != null
                    && selected.canvasId().equals(hold.canvasId())
                    && selected.contains(hold.start())
                    ? selected.move(hold.previewEnd().x() - hold.start().x(), hold.previewEnd().y() - hold.start().y(), hold.previewVersion())
                    : SelectionRegion.of(hold.canvasId(), hold.start(), hold.previewEnd(), hold.previewVersion());
            sessions.putOverlay(playerId, selectionMoveOverlay(hold, selected, previewRegion));
            return;
        }

        SelectionRegion selected = sessions.selection(playerId);
        if (selected == null) {
            sessions.removeOverlay(playerId);
        } else {
            sessions.putOverlay(playerId, selectionOverlay(selected, selected.previewVersion()));
        }
    }

    private void updatePendingHolds() {
        for (Map.Entry<UUID, Tool> entry : sessions.pendingHoldEntries()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Tool tool = entry.getValue();
            if (player == null
                    || tool == null
                    || !tool.isShapeTool()
                    || selectedToolResolver.apply(player) != tool
                    || !paintToolPredicate.test(player.getInventory().getItemInMainHand())) {
                sessions.removePendingHold(entry.getKey());
                continue;
            }

            LookResult look = shapeLookResolver.apply(player);
            if (look == null || !look.canvas().canEdit(player)) {
                continue;
            }

            sessions.removePendingHold(entry.getKey());
            beginHold(player, look, tool);
        }
    }

    private void clearExistingHold(UUID playerId) {
        sessions.removePendingHold(playerId);
        ShapeHold removedHold = sessions.removeActiveHold(playerId);
        if (removedHold != null) {
            clearPreview(playerId, removedHold.canvasId());
        }
    }

    private void finishSelectionMoveHold(Player player, PlayerCanvas canvas, PixelPoint start, PixelPoint end) {
        UUID playerId = player.getUniqueId();
        SelectionRegion selected = sessions.selection(playerId);
        if (selected != null && selected.canvasId().equals(canvas.ownerId()) && selected.contains(start)) {
            undoPusher.accept(canvas);
            boolean moved = drawingService.moveActiveLayerSelection(
                    canvas.pixelCanvas(),
                    selected.start(),
                    selected.end(),
                    start,
                    end
            );
            if (moved) {
                sessions.putSelection(playerId, selected.move(end.x() - start.x(), end.y() - start.y(), nextPreviewVersion()));
                SelectionRegion movedSelection = sessions.selection(playerId);
                sessions.putOverlay(playerId, selectionOverlay(movedSelection, movedSelection.previewVersion()));
                canvas.resetSentTileVersions();
                canvasMapSender.accept(canvas);
                canvasMapSenderTo.send(canvas, player, true);
                player.sendActionBar(Component.text("선택 영역을 이동했습니다."));
            } else {
                canvas.undoSnapshots().pop();
                sessions.putOverlay(playerId, selectionOverlay(selected, selected.previewVersion()));
                canvasMapSenderTo.send(canvas, player, true);
            }
            return;
        }

        SelectionRegion next = SelectionRegion.of(canvas.ownerId(), start, end, nextPreviewVersion());
        sessions.putSelection(playerId, next);
        sessions.putOverlay(playerId, selectionOverlay(next, next.previewVersion()));
        canvasMapSenderTo.send(canvas, player, true);
        player.sendActionBar(Component.text("선택 영역: "
                + (next.maxX() - next.minX() + 1)
                + "x"
                + (next.maxY() - next.minY() + 1)));
    }

    private PixelMapRenderer.PreviewOverlay selectionOverlay(SelectionRegion region, int version) {
        return new PixelMapRenderer.PreviewOverlay(region.canvasId(), version, dottedRectanglePixels(region));
    }

    private PixelMapRenderer.PreviewOverlay selectionMoveOverlay(ShapeHold hold, SelectionRegion selected, SelectionRegion previewRegion) {
        if (selected == null || !selected.canvasId().equals(hold.canvasId()) || !selected.contains(hold.start())) {
            return selectionOverlay(previewRegion, hold.previewVersion());
        }

        PlayerCanvas canvas = canvasResolver.apply(hold.canvasId());
        if (canvas == null) {
            return selectionOverlay(previewRegion, hold.previewVersion());
        }

        int dx = hold.previewEnd().x() - hold.start().x();
        int dy = hold.previewEnd().y() - hold.start().y();
        List<PixelMapRenderer.PreviewPixel> pixels = new ArrayList<>();
        for (int y = selected.minY(); y <= selected.maxY(); y++) {
            for (int x = selected.minX(); x <= selected.maxX(); x++) {
                pixels.add(PixelMapRenderer.PreviewPixel.clearActiveLayer(x, y));
            }
        }

        for (int y = selected.minY(); y <= selected.maxY(); y++) {
            for (int x = selected.minX(); x <= selected.maxX(); x++) {
                Color color = canvas.pixelCanvas().getActiveLayerPixel(x, y);
                if (color == null) {
                    continue;
                }

                pixels.add(new PixelMapRenderer.PreviewPixel(x + dx, y + dy, color));
            }
        }

        pixels.addAll(dottedRectanglePixels(canvas, previewRegion));
        return new PixelMapRenderer.PreviewOverlay(hold.canvasId(), hold.previewVersion(), pixels);
    }

    private List<PixelMapRenderer.PreviewPixel> dottedRectanglePixels(SelectionRegion region) {
        PlayerCanvas canvas = canvasResolver.apply(region.canvasId());
        return canvas == null ? List.of() : dottedRectanglePixels(canvas, region);
    }

    private List<PixelMapRenderer.PreviewPixel> dottedRectanglePixels(PlayerCanvas canvas, SelectionRegion region) {
        List<PixelMapRenderer.PreviewPixel> pixels = new ArrayList<>();
        Color color = new Color(85, 255, 85);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            if (((x - region.minX()) / 2) % 2 == 0) {
                addSelectionPreviewPixel(pixels, canvas, x, region.minY(), color);
                addSelectionPreviewPixel(pixels, canvas, x, region.maxY(), color);
            }
        }
        for (int y = region.minY(); y <= region.maxY(); y++) {
            if (((y - region.minY()) / 2) % 2 == 0) {
                addSelectionPreviewPixel(pixels, canvas, region.minX(), y, color);
                addSelectionPreviewPixel(pixels, canvas, region.maxX(), y, color);
            }
        }
        return pixels;
    }

    private void addSelectionPreviewPixel(List<PixelMapRenderer.PreviewPixel> pixels, PlayerCanvas canvas, int x, int y, Color color) {
        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                int pixelX = x + dx;
                int pixelY = y + dy;
                if (pixelX >= 0 && pixelX < pixelCanvas.width() && pixelY >= 0 && pixelY < pixelCanvas.height()) {
                    pixels.add(new PixelMapRenderer.PreviewPixel(pixelX, pixelY, color));
                }
            }
        }
    }

    private int nextPreviewVersion() {
        return sessions.nextPreviewVersion();
    }

    @FunctionalInterface
    public interface CanvasMapSenderTo {
        void send(PlayerCanvas canvas, Player player, boolean force);
    }
}
