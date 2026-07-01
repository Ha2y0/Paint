package org.ha2yo.paint.workflow;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.service.LayerInteractionService;
import org.ha2yo.paint.service.LayerPanelService;
import org.ha2yo.paint.service.LayerPanelService.LayerControlLook;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LayerWorkflowService {
    private final LayerPanelService layerPanels;
    private final LayerInteractionService layerInteractions;
    private final Supplier<Collection<PlayerCanvas>> canvases;
    private final Function<UUID, PlayerCanvas> canvasResolver;
    private final Consumer<UUID> drawingStopper;
    private final Consumer<PlayerCanvas> mapSender;

    public LayerWorkflowService(
            LayerPanelService layerPanels,
            LayerInteractionService layerInteractions,
            Supplier<Collection<PlayerCanvas>> canvases,
            Function<UUID, PlayerCanvas> canvasResolver,
            Consumer<UUID> drawingStopper,
            Consumer<PlayerCanvas> mapSender
    ) {
        this.layerPanels = layerPanels;
        this.layerInteractions = layerInteractions;
        this.canvases = canvases;
        this.canvasResolver = canvasResolver;
        this.drawingStopper = drawingStopper;
        this.mapSender = mapSender;
    }

    public void toggleActiveLayer(Player player) {
        PlayerCanvas canvas = canvasResolver.apply(player.getUniqueId());
        if (canvas == null) {
            return;
        }

        PixelCanvas pixelCanvas = canvas.pixelCanvas();
        int nextLayer = (pixelCanvas.activeLayerIndex() + 1) % pixelCanvas.activeLayerCount();
        pixelCanvas.setActiveLayerIndex(nextLayer);
        updateDisplays(canvas);
        player.sendActionBar(Component.text("Layer " + activeLayerName(pixelCanvas, nextLayer)));
    }

    public void updateDisplays(PlayerCanvas canvas) {
        if (layerPanels != null) {
            layerPanels.update(canvas);
        }
    }

    public void removeDisplays(UUID ownerId) {
        if (layerPanels != null) {
            layerPanels.remove(ownerId);
        }
    }

    public void clearAll() {
        if (layerPanels != null) {
            layerPanels.clearAll();
        }
    }

    public void clearOpacityLock(UUID ownerId) {
        if (layerPanels != null) {
            layerPanels.clearOpacityLock(ownerId);
        }
    }

    public void syncVisibility(Player viewer) {
        if (layerPanels != null) {
            layerPanels.syncVisibility(viewer);
        }
    }

    public PlayerCanvas canvasByDisplay(UUID displayId) {
        return layerPanels == null ? null : layerPanels.canvasByDisplay(displayId, canvasResolver);
    }

    public LayerControlLook lookedControl(Player player) {
        return layerPanels == null ? null : layerPanels.looked(player, canvases.get());
    }

    public LayerControlLook lookedControl(Player player, PlayerCanvas canvas) {
        return layerPanels == null ? null : layerPanels.looked(player, canvas);
    }

    public void handleControlClick(Player player, LayerControlLook look) {
        layerInteractions.handleClick(
                player,
                look,
                this::hasOpacityInteractionLock,
                this::lockOpacityInteractions,
                drawingStopper,
                mapSender,
                this::updateDisplays
        );
    }

    public boolean hasOpacityInteractionLock(Player player) {
        return layerPanels != null && layerPanels.hasOpacityInteractionLock(player);
    }

    private void lockOpacityInteractions(UUID playerId) {
        if (layerPanels != null) {
            layerPanels.lockOpacityInteraction(playerId);
        }
    }

    private String activeLayerName(PixelCanvas pixelCanvas, int layerIndex) {
        return Integer.toString(pixelCanvas.layerLabel(layerIndex));
    }
}
