package org.ha2yo.paint.service;

import org.bukkit.entity.Player;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.service.LayerPanelService.LayerControlAction;
import org.ha2yo.paint.service.LayerPanelService.LayerControlLook;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class LayerInteractionService {
    private final int maxHistorySize;

    public LayerInteractionService(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public void handleClick(
            Player player,
            LayerControlLook look,
            Predicate<Player> opacityLocked,
            Consumer<UUID> opacityLocker,
            Consumer<UUID> drawingStopper,
            Consumer<PlayerCanvas> mapSender,
            Consumer<PlayerCanvas> panelUpdater
    ) {
        PlayerCanvas canvas = look.canvas();
        if (!canvas.canEdit(player)) {
            drawingStopper.accept(player.getUniqueId());
            return;
        }

        UUID playerId = player.getUniqueId();
        if (look.action() != LayerControlAction.SET_LAYER_OPACITY && opacityLocked.test(player)) {
            drawingStopper.accept(playerId);
            return;
        }

        if (look.action() == LayerControlAction.TOGGLE_PANEL) {
            canvas.setLayerPanelOpen(!canvas.layerPanelOpen());
            panelUpdater.accept(canvas);
            return;
        }

        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        if (look.action() == LayerControlAction.ADD_LAYER) {
            if (applyUndoableLayerChange(canvas, pixelCanvas::addLayer)) {
                canvas.setLayerSettingsIndex(-1);
                canvas.resetSentTileVersions();
                mapSender.accept(canvas);
            }
            panelUpdater.accept(canvas);
            return;
        }
        if (look.action() == LayerControlAction.DELETE_LAYER) {
            if (applyUndoableLayerChange(canvas, () -> pixelCanvas.deleteLayer(look.layerIndex()))) {
                canvas.setLayerSettingsIndex(-1);
                canvas.resetSentTileVersions();
                mapSender.accept(canvas);
            }
            panelUpdater.accept(canvas);
            return;
        }
        if (look.action() == LayerControlAction.TOGGLE_LAYER_SETTINGS) {
            canvas.setLayerSettingsIndex(openLayerSettingsIndex(canvas) == look.layerIndex() ? -1 : look.layerIndex());
            panelUpdater.accept(canvas);
            return;
        }
        if (look.action() == LayerControlAction.TOGGLE_VISIBILITY) {
            pixelCanvas.setLayerVisible(look.layerIndex(), !pixelCanvas.isLayerVisible(look.layerIndex()));
            canvas.resetSentTileVersions();
            mapSender.accept(canvas);
        } else if (look.action() == LayerControlAction.SET_LAYER_OPACITY) {
            opacityLocker.accept(playerId);
            drawingStopper.accept(playerId);
            if (pixelCanvas.setLayerOpacityPercent(look.layerIndex(), look.opacityPercent())) {
                canvas.resetSentTileVersions();
                mapSender.accept(canvas);
            }
        } else if (look.action() == LayerControlAction.MOVE_LAYER_UP) {
            if (applyUndoableLayerChange(canvas, () -> pixelCanvas.moveLayerDown(look.layerIndex()))) {
                canvas.setLayerSettingsIndex(-1);
                canvas.resetSentTileVersions();
                mapSender.accept(canvas);
            }
        } else if (look.action() == LayerControlAction.MOVE_LAYER_DOWN) {
            if (applyUndoableLayerChange(canvas, () -> pixelCanvas.moveLayerUp(look.layerIndex()))) {
                canvas.setLayerSettingsIndex(-1);
                canvas.resetSentTileVersions();
                mapSender.accept(canvas);
            }
        } else {
            boolean wasVisible = pixelCanvas.isLayerVisible(look.layerIndex());
            pixelCanvas.setActiveLayerIndex(look.layerIndex());
            pixelCanvas.setLayerVisible(look.layerIndex(), true);
            if (!wasVisible) {
                canvas.resetSentTileVersions();
                mapSender.accept(canvas);
            }
        }
        panelUpdater.accept(canvas);
    }

    private boolean applyUndoableLayerChange(PlayerCanvas canvas, BooleanSupplier change) {
        PixelCanvas.LayerSnapshot before = canvas.pixelCanvas().layerSnapshot();
        if (!change.getAsBoolean()) {
            return false;
        }

        canvas.undoSnapshots().push(before);
        while (canvas.undoSnapshots().size() > maxHistorySize) {
            canvas.undoSnapshots().removeLast();
        }
        canvas.redoSnapshots().clear();
        return true;
    }

    private int openLayerSettingsIndex(PlayerCanvas canvas) {
        int index = canvas.layerSettingsIndex();
        if (index < 0 || index >= canvas.pixelCanvas().activeLayerCount()) {
            return -1;
        }
        return index;
    }
}
