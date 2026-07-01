package org.ha2yo.paint.workflow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.DrawingMode;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.CanvasLookService;
import org.ha2yo.paint.service.CanvasLookService.CanvasPlaneHit;
import org.ha2yo.paint.service.CanvasLookService.LookResult;
import org.ha2yo.paint.service.DrawingInteractionService;
import org.ha2yo.paint.service.DrawingService;
import org.ha2yo.paint.service.DrawingSessionService;
import org.ha2yo.paint.service.DrawingSessionService.PlaneSample;
import org.ha2yo.paint.service.ToolItemService;

import java.awt.Color;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class PaintingInteractionWorkflowService {
    private final DrawingService drawingService;
    private final DrawingSessionService drawingSessions;
    private final DrawingInteractionService drawingInteractions;
    private final ToolItemService toolItems;
    private final CanvasLifecycleService canvasLifecycle;
    private final CanvasLookService canvasLookService;
    private final Map<UUID, Color> selectedColors;
    private final Map<UUID, Integer> brushRadii;
    private final int defaultBrushRadius;
    private final long paintIntervalMillis;
    private final Function<Player, Tool> selectedTool;
    private final Consumer<PlayerCanvas> canvasMapSender;
    private final BiConsumer<UUID, Color> paletteColorUpdater;
    private final Consumer<PlayerCanvas> layerDisplayUpdater;

    public PaintingInteractionWorkflowService(
            DrawingService drawingService,
            DrawingSessionService drawingSessions,
            DrawingInteractionService drawingInteractions,
            ToolItemService toolItems,
            CanvasLifecycleService canvasLifecycle,
            CanvasLookService canvasLookService,
            Map<UUID, Color> selectedColors,
            Map<UUID, Integer> brushRadii,
            int defaultBrushRadius,
            long paintIntervalMillis,
            Function<Player, Tool> selectedTool,
            Consumer<PlayerCanvas> canvasMapSender,
            BiConsumer<UUID, Color> paletteColorUpdater,
            Consumer<PlayerCanvas> layerDisplayUpdater
    ) {
        this.drawingService = drawingService;
        this.drawingSessions = drawingSessions;
        this.drawingInteractions = drawingInteractions;
        this.toolItems = toolItems;
        this.canvasLifecycle = canvasLifecycle;
        this.canvasLookService = canvasLookService;
        this.selectedColors = selectedColors;
        this.brushRadii = brushRadii;
        this.defaultBrushRadius = defaultBrushRadius;
        this.paintIntervalMillis = paintIntervalMillis;
        this.selectedTool = selectedTool;
        this.canvasMapSender = canvasMapSender;
        this.paletteColorUpdater = paletteColorUpdater;
        this.layerDisplayUpdater = layerDisplayUpdater;
    }

    public void paintForActivePlayers(Iterable<? extends Player> players) {
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            DrawingMode mode = drawingSessions.mode(playerId);
            if (!drawingSessions.isHolding(playerId)
                    || mode == DrawingMode.OFF
                    || !toolItems.isPixelPainterTool(player.getInventory().getItemInMainHand())) {
                clearStrokeState(playerId);
                continue;
            }

            long now = System.currentTimeMillis();
            Long lastTime = drawingSessions.lastStrokeTime(playerId);
            if (lastTime != null && now - lastTime < paintIntervalMillis) {
                continue;
            }

            LookResult look = getPaintingLookedCanvas(player);
            if (look == null || !look.canvas().canEdit(player)) {
                LookResult exitLook = getCanvasExitLook(player, playerId);
                if (exitLook != null) {
                    ensureHoldUndoStarted(playerId, exitLook.canvas());
                    drawFromLastPoint(player, exitLook, mode);
                }
                rememberStrokePlaneSample(player, playerId);
                clearStrokePointState(playerId);
                continue;
            }

            LookResult entryLook = getCanvasEntryLook(playerId, look);
            if (entryLook != null) {
                drawingSessions.setStrokePoint(playerId, entryLook.point(), entryLook.canvas().ownerId());
            }
            ensureHoldUndoStarted(playerId, look.canvas());
            drawFromLastPoint(player, look, mode);
        }
    }

    public void useOneShotTool(Player player, LookResult look, Tool tool) {
        UUID playerId = player.getUniqueId();
        PlayerCanvas canvas = look.canvas();
        switch (tool) {
            case CLEAR -> {
                drawingInteractions.pushUndo(canvas);
                canvas.pixelCanvas().clearActiveLayer();
                canvasMapSender.accept(canvas);
                playToolSound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.50F, 1.0F);
                clearStrokeState(playerId);
            }
            case UNDO -> undo(canvas);
            case REDO -> redo(canvas);
            case BUCKET -> {
                drawingInteractions.pushUndo(canvas);
                drawingService.floodFill(canvas.pixelCanvas(), look.point(), selectedColors.getOrDefault(playerId, Color.BLACK));
                canvasMapSender.accept(canvas);
                playToolSound(player, Sound.ITEM_BUCKET_EMPTY, 0.8F, 1.05F);
                clearStrokeState(playerId);
            }
            case EYEDROPPER -> {
                Color color = canvas.pixelCanvas().getPixel(look.point().x(), look.point().y());
                selectedColors.put(playerId, color);
                paletteColorUpdater.accept(playerId, color);
                sendPickedColorMessage(player, color);
                clearStrokeState(playerId);
            }
            default -> clearStrokeState(playerId);
        }
    }

    public void beginRightHold(Player player, Tool tool) {
        drawingSessions.beginHold(player.getUniqueId(), tool);
    }

    public void beginRightHold(Player player, PlayerCanvas canvas, Tool tool) {
        beginRightHold(player, tool);
        ensureHoldUndoStarted(player.getUniqueId(), canvas);
    }

    private void drawFromLastPoint(Player player, LookResult look, DrawingMode mode) {
        UUID playerId = player.getUniqueId();
        Tool tool = mode == DrawingMode.ERASE ? Tool.ERASER : selectedTool.apply(player);
        Color color = tool == Tool.ERASER ? null : selectedColors.getOrDefault(playerId, Color.BLACK);
        drawingInteractions.drawFromLastPoint(
                playerId,
                look.canvas(),
                look.point(),
                tool,
                brushRadii.getOrDefault(playerId, defaultBrushRadius),
                color,
                look.u(),
                look.v(),
                look.distance()
        );
        canvasMapSender.accept(look.canvas());
    }

    private void ensureHoldUndoStarted(UUID playerId, PlayerCanvas canvas) {
        UUID canvasId = canvas.ownerId();
        if (drawingSessions.hasUndoStarted(playerId, canvasId)) {
            return;
        }

        drawingInteractions.pushUndo(canvas);
        drawingSessions.markUndoStarted(playerId, canvasId);
    }

    private void undo(PlayerCanvas canvas) {
        if (drawingInteractions.undo(canvas)) {
            canvasMapSender.accept(canvas);
            layerDisplayUpdater.accept(canvas);
        }
    }

    private void redo(PlayerCanvas canvas) {
        if (drawingInteractions.redo(canvas)) {
            canvasMapSender.accept(canvas);
            layerDisplayUpdater.accept(canvas);
        }
    }

    private LookResult getPaintingLookedCanvas(Player player) {
        UUID canvasId = drawingSessions.lastStrokeCanvasId(player.getUniqueId());
        if (canvasId != null) {
            PlayerCanvas canvas = canvasLifecycle.canvas(canvasId);
            return canvas == null ? null : canvasLookService.lookedCanvas(player, canvas, 10.0D);
        }
        return canvasLookService.lookedCanvas(player, canvasLifecycle.canvases(), 10.0D);
    }

    private LookResult getCanvasExitLook(Player player, UUID playerId) {
        PixelPoint lastPoint = drawingSessions.lastStrokePoint(playerId);
        UUID canvasId = drawingSessions.lastStrokeCanvasId(playerId);
        if (lastPoint == null || canvasId == null) {
            return null;
        }

        PlayerCanvas canvas = canvasLifecycle.canvas(canvasId);
        if (canvas == null || !canvas.canEdit(player)) {
            return null;
        }

        CanvasPlaneHit hit = canvasLookService.planeHit(player, canvas, 10.0D);
        if (hit == null || canvasLookService.insideCanvas(canvas, hit.u(), hit.v())) {
            return null;
        }

        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        double blockWidth = pixelCanvas.blockWidth();
        double blockHeight = pixelCanvas.blockHeight();
        double lastU = (lastPoint.x() + 0.5D) / pixelCanvas.width() * blockWidth;
        double lastV = blockHeight - (lastPoint.y() + 0.5D) / pixelCanvas.height() * blockHeight;
        double deltaU = hit.u() - lastU;
        double deltaV = hit.v() - lastV;
        double exitT = 1.0D;

        if (hit.u() < 0.0D && deltaU < 0.0D) {
            exitT = Math.min(exitT, (0.0D - lastU) / deltaU);
        } else if (hit.u() >= blockWidth && deltaU > 0.0D) {
            exitT = Math.min(exitT, (blockWidth - 0.000001D - lastU) / deltaU);
        }

        if (hit.v() < 0.0D && deltaV < 0.0D) {
            exitT = Math.min(exitT, (0.0D - lastV) / deltaV);
        } else if (hit.v() >= blockHeight && deltaV > 0.0D) {
            exitT = Math.min(exitT, (blockHeight - 0.000001D - lastV) / deltaV);
        }

        if (exitT < 0.0D || exitT > 1.0D) {
            return null;
        }

        double exitU = lastU + deltaU * exitT;
        double exitV = lastV + deltaV * exitT;
        LookResult result = canvasLookService.createLookResult(canvas, exitU, exitV, hit.distance());
        return result.point().equals(lastPoint) ? null : result;
    }

    private LookResult getCanvasEntryLook(UUID playerId, LookResult look) {
        if (drawingSessions.hasLastStrokePoint(playerId)) {
            return null;
        }

        PlaneSample previous = drawingSessions.planeSample(playerId);
        if (previous == null || !previous.canvasId().equals(look.canvas().ownerId())) {
            return null;
        }
        if (canvasLookService.insideCanvas(look.canvas(), previous.u(), previous.v())) {
            return null;
        }

        PixelCanvas pixelCanvas = look.canvas().pixelCanvas();
        double blockWidth = pixelCanvas.blockWidth();
        double blockHeight = pixelCanvas.blockHeight();
        double deltaU = look.u() - previous.u();
        double deltaV = look.v() - previous.v();
        double entryT = 0.0D;
        boolean crossing = false;

        if (previous.u() < 0.0D) {
            if (deltaU <= 0.0D) {
                return null;
            }
            entryT = Math.max(entryT, (0.0D - previous.u()) / deltaU);
            crossing = true;
        } else if (previous.u() >= blockWidth) {
            if (deltaU >= 0.0D) {
                return null;
            }
            entryT = Math.max(entryT, (blockWidth - 0.000001D - previous.u()) / deltaU);
            crossing = true;
        }

        if (previous.v() < 0.0D) {
            if (deltaV <= 0.0D) {
                return null;
            }
            entryT = Math.max(entryT, (0.0D - previous.v()) / deltaV);
            crossing = true;
        } else if (previous.v() >= blockHeight) {
            if (deltaV >= 0.0D) {
                return null;
            }
            entryT = Math.max(entryT, (blockHeight - 0.000001D - previous.v()) / deltaV);
            crossing = true;
        }

        if (!crossing || entryT < 0.0D || entryT > 1.0D) {
            return null;
        }

        double entryU = previous.u() + deltaU * entryT;
        double entryV = previous.v() + deltaV * entryT;
        LookResult entryLook = canvasLookService.createLookResult(look.canvas(), entryU, entryV, look.distance());
        return entryLook.point().equals(look.point()) ? null : entryLook;
    }

    private void rememberStrokePlaneSample(Player player, UUID playerId) {
        PlaneSample sample = getClosestStrokePlaneSample(player);
        if (sample == null) {
            drawingSessions.removePlaneSample(playerId);
            return;
        }
        drawingSessions.setPlaneSample(playerId, sample);
    }

    private PlaneSample getClosestStrokePlaneSample(Player player) {
        PlaneSample closest = null;
        for (PlayerCanvas canvas : canvasLifecycle.canvases()) {
            if (!canvas.canEdit(player)) {
                continue;
            }
            CanvasPlaneHit hit = canvasLookService.planeHit(player, canvas, 10.0D);
            if (hit == null) {
                continue;
            }
            if (closest == null || hit.distance() < closest.distance()) {
                closest = new PlaneSample(canvas.ownerId(), hit.u(), hit.v(), hit.distance());
            }
        }
        return closest;
    }

    private void clearStrokeState(UUID playerId) {
        drawingSessions.clearStrokeState(playerId);
    }

    private void clearStrokePointState(UUID playerId) {
        drawingSessions.clearStrokePointState(playerId);
    }

    private void playToolSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void sendPickedColorMessage(Player player, Color color) {
        player.sendMessage(Component.text("선택한 색상: ", NamedTextColor.GREEN)
                .append(Component.text("■", TextColor.color(color.getRed(), color.getGreen(), color.getBlue()))));
    }
}
