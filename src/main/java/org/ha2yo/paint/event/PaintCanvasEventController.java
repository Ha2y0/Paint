package org.ha2yo.paint.event;

import java.util.function.Consumer;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class PaintCanvasEventController implements Listener {
    private final Consumer<BlockDamageEvent> canvasPressHandler;
    private final Consumer<PlayerAnimationEvent> paletteSwingHandler;
    private final Consumer<PlayerInteractEvent> canvasClickHandler;
    private final Consumer<EntityDamageByEntityEvent> canvasFrameHitHandler;
    private final Consumer<PlayerInteractAtEntityEvent> artworkPlacementDisplayUseHandler;
    private final Consumer<PlayerInteractEntityEvent> canvasFrameUseHandler;
    private final Consumer<PlayerBucketEmptyEvent> toolBucketEmptyHandler;
    private final Consumer<BlockBreakEvent> canvasBreakHandler;
    private final Consumer<HangingBreakEvent> canvasFrameBreakHandler;
    public PaintCanvasEventController(
            Consumer<BlockDamageEvent> canvasPressHandler,
            Consumer<PlayerAnimationEvent> paletteSwingHandler,
            Consumer<PlayerInteractEvent> canvasClickHandler,
            Consumer<EntityDamageByEntityEvent> canvasFrameHitHandler,
            Consumer<PlayerInteractAtEntityEvent> artworkPlacementDisplayUseHandler,
            Consumer<PlayerInteractEntityEvent> canvasFrameUseHandler,
            Consumer<PlayerBucketEmptyEvent> toolBucketEmptyHandler,
            Consumer<BlockBreakEvent> canvasBreakHandler,
            Consumer<HangingBreakEvent> canvasFrameBreakHandler
    ) {
        this.canvasPressHandler = canvasPressHandler;
        this.paletteSwingHandler = paletteSwingHandler;
        this.canvasClickHandler = canvasClickHandler;
        this.canvasFrameHitHandler = canvasFrameHitHandler;
        this.artworkPlacementDisplayUseHandler = artworkPlacementDisplayUseHandler;
        this.canvasFrameUseHandler = canvasFrameUseHandler;
        this.toolBucketEmptyHandler = toolBucketEmptyHandler;
        this.canvasBreakHandler = canvasBreakHandler;
        this.canvasFrameBreakHandler = canvasFrameBreakHandler;
    }
    @EventHandler
    public void onCanvasPress(BlockDamageEvent event) {
        canvasPressHandler.accept(event);
    }
    @EventHandler
    public void onPaletteSwing(PlayerAnimationEvent event) {
        paletteSwingHandler.accept(event);
    }
    @EventHandler
    public void onCanvasClick(PlayerInteractEvent event) {
        canvasClickHandler.accept(event);
    }
    @EventHandler
    public void onCanvasFrameHit(EntityDamageByEntityEvent event) {
        canvasFrameHitHandler.accept(event);
    }
    @EventHandler
    public void onArtworkPlacementDisplayUse(PlayerInteractAtEntityEvent event) {
        artworkPlacementDisplayUseHandler.accept(event);
    }
    @EventHandler
    public void onCanvasFrameUse(PlayerInteractEntityEvent event) {
        canvasFrameUseHandler.accept(event);
    }
    @EventHandler
    public void onToolBucketEmpty(PlayerBucketEmptyEvent event) {
        toolBucketEmptyHandler.accept(event);
    }
    @EventHandler
    public void onCanvasBreak(BlockBreakEvent event) {
        canvasBreakHandler.accept(event);
    }
    @EventHandler
    public void onCanvasFrameBreak(HangingBreakEvent event) {
        canvasFrameBreakHandler.accept(event);
    }
}
