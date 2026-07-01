package org.ha2yo.paint.event;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import java.util.function.Consumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class PaintToolEventController implements Listener {
    private final Consumer<PlayerDropItemEvent> toolDropHandler;
    private final Consumer<InventoryDragEvent> toolDragHandler;
    private final Consumer<InventoryClickEvent> paletteClickHandler;
    private final Consumer<PlayerItemHeldEvent> toolSlotChangeHandler;
    private final Consumer<PlayerSwapHandItemsEvent> toolSwapHandler;
    private final Consumer<PlayerStopUsingItemEvent> stopUsingItemHandler;
    private final Consumer<PlayerPickItemEvent> pickItemHandler;
    public PaintToolEventController(
            Consumer<PlayerDropItemEvent> toolDropHandler,
            Consumer<InventoryDragEvent> toolDragHandler,
            Consumer<InventoryClickEvent> paletteClickHandler,
            Consumer<PlayerItemHeldEvent> toolSlotChangeHandler,
            Consumer<PlayerSwapHandItemsEvent> toolSwapHandler,
            Consumer<PlayerStopUsingItemEvent> stopUsingItemHandler,
            Consumer<PlayerPickItemEvent> pickItemHandler
    ) {
        this.toolDropHandler = toolDropHandler;
        this.toolDragHandler = toolDragHandler;
        this.paletteClickHandler = paletteClickHandler;
        this.toolSlotChangeHandler = toolSlotChangeHandler;
        this.toolSwapHandler = toolSwapHandler;
        this.stopUsingItemHandler = stopUsingItemHandler;
        this.pickItemHandler = pickItemHandler;
    }
    @EventHandler
    public void onToolDrop(PlayerDropItemEvent event) {
        toolDropHandler.accept(event);
    }
    @EventHandler
    public void onToolDrag(InventoryDragEvent event) {
        toolDragHandler.accept(event);
    }
    @EventHandler
    public void onPaletteClick(InventoryClickEvent event) {
        paletteClickHandler.accept(event);
    }
    @EventHandler
    public void onToolSlotChange(PlayerItemHeldEvent event) {
        toolSlotChangeHandler.accept(event);
    }
    @EventHandler
    public void onToolSwap(PlayerSwapHandItemsEvent event) {
        toolSwapHandler.accept(event);
    }
    @EventHandler
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        stopUsingItemHandler.accept(event);
    }
    @EventHandler
    public void onPickItem(PlayerPickItemEvent event) {
        pickItemHandler.accept(event);
    }
}
