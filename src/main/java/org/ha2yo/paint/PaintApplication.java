package org.ha2yo.paint;

import java.awt.Color;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.Location;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.api.PaintService;
import org.ha2yo.paint.bootstrap.PaintBootstrapService;
import org.ha2yo.paint.bootstrap.PaintShutdownService;
import org.ha2yo.paint.interaction.PaintInteractionRouter;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.runtime.PaintRuntime;
import org.ha2yo.paint.service.CanvasMapSyncService;
import org.ha2yo.paint.service.PaintTransientCleanupService;
import org.ha2yo.paint.workflow.InventoryToolWorkflowService;
import org.ha2yo.paint.workflow.PaintControllerFeatureService;

public final class PaintApplication {
    public static final int DEFAULT_CANVAS_BLOCK_WIDTH = 5;
    public static final int DEFAULT_CANVAS_BLOCK_HEIGHT = 5;
    public static final int MAX_CANVAS_BLOCK_SIZE = 10;
    public static final int MAP_SIZE = 128;
    public static final int DRAW_SCALE = 2;
    public static final int CANVAS_PIXELS_PER_BLOCK = MAP_SIZE / DRAW_SCALE;
    public static final int DEFAULT_BRUSH_RADIUS = 3;
    public static final int MAX_BRUSH_RADIUS = 24;
    public static final int MAX_HISTORY_SIZE = 20;
    public static final int CANVAS_MAP_TILE_SEND_BUDGET = 8;
    public static final int MAX_CANVAS_MAP_TILE_SEND_BUDGET = 48;
    public static final int CANVAS_MAP_SEND_FLUSH_BUDGET = 40;
    public static final long PAINT_INTERVAL_MILLIS = 35L;
    public static final long STROKE_JOIN_MILLIS = 250L;
    public static final long LAYER_OPACITY_INTERACTION_LOCK_MILLIS = 350L;
    public static final long OBSERVER_CANVAS_MAP_SEND_INTERVAL_MILLIS = 450L;
    public static final long PALETTE_RIGHT_CLICK_SWING_GRACE_MILLIS = 250L;
    public static final long ARTWORK_PREVIEW_CLICK_COOLDOWN_MILLIS = 120L;
    public static final long ARTWORK_PREVIEW_OPEN_GRACE_MILLIS = 350L;
    public static final long PAINT_PANEL_CLICK_COOLDOWN_MILLIS = 120L;
    public static final double ARTWORK_PREVIEW_INTERACTION_DISTANCE = 32.0D;
    public static final double EXHIBIT_REMOVE_INTERACTION_DISTANCE = 128.0D;
    public static final double PAINT_PANEL_INTERACTION_DISTANCE = 32.0D;
    public static final double PAINT_PANEL_ENTITY_INTERACTION_RANGE = 10.0D;
    public static final int DEFAULT_ARTWORK_PLACEMENT_DISTANCE = 10;
    public static final int DEFAULT_CANVAS_PLACEMENT_DISTANCE = 5;
    public static final int MAX_ARTWORK_PLACEMENT_DISTANCE = 32;
    public static final int MAX_CANVAS_PLACEMENT_DISTANCE = 16;
    public static final String ARTWORK_PLACEMENT_PREVIEW_TAG = "paint_artwork_placement_preview";
    public static final String ARTWORK_PREVIEW_TAG = "paint_artwork_preview";
    public static final String MENU_PANEL_TAG = "paint_menu_panel";
    public static final String CANVAS_FRAME_TAG = "paint_canvas_frame";
    public static final String PALETTE_FRAME_TAG = "paint_palette_frame";
    public static final String CANVAS_LAYER_DISPLAY_TAG = "paint_canvas_layer_display";
    public static final String EXHIBIT_DISPLAY_TAG = "paint_exhibit_display";
    public static final Set<String> PAINT_TRANSIENT_ENTITY_TAGS = Set.of(
            ARTWORK_PLACEMENT_PREVIEW_TAG,
            ARTWORK_PREVIEW_TAG,
            MENU_PANEL_TAG,
            CANVAS_FRAME_TAG,
            PALETTE_FRAME_TAG,
            CANVAS_LAYER_DISPLAY_TAG
    );
    public static final String PREVIEW_ACTION_ARTWORK_PLACEMENT = "artwork_placement";
    public static final String PREVIEW_ACTION_CANVAS_PLACEMENT = "canvas_placement";
    public static final String PREVIEW_ACTION_EXHIBIT_REMOVAL = "exhibit_removal";
    public static final String PREVIEW_ACTION_PAINT_PANEL = "paint_panel";
    public static final String PREVIEW_ACTION_MANUAL_STATION = "manual_station";
    public static final long ARTWORK_PLACEMENT_ARM_DELAY_MILLIS = 250L;
    public static final long PALETTE_PLACEMENT_ARM_DELAY_MILLIS = 250L;
    public static final long EXHIBIT_REMOVE_CONFIRM_MILLIS = 4000L;
    public static final long EXHIBIT_REMOVE_CLICK_COOLDOWN_MILLIS = 120L;
    public static final long EXHIBIT_REMOVE_ACTIONBAR_OVERRIDE_MILLIS = 1400L;
    public static final int ARTWORK_PLACEMENT_TOOL_SLOT = 0;
    public static final String ARTWORK_NAME_INPUT_KEY = "artwork_name";
    public static final String ARTWORK_SEARCH_INPUT_KEY = "artwork_search";
    public static final double ADVANCED_SHAPE_PLANE_MARGIN_BLOCKS = 8.0D;
    public static final Color BACKGROUND_COLOR = new Color(238, 236, 228);
    private final Paint plugin;
    private final PaintRuntime runtime = new PaintRuntime();
    public PaintApplication(Paint plugin) {
        this.plugin = plugin;
    }
    public Paint plugin() {
        return plugin;
    }
    public PaintRuntime runtime() {
        return runtime;
    }
    public PaintService paintService() {
        return runtime.core().paintService;
    }
    public void enable() {
        PaintBootstrapService.enable(this);
    }
    public void disable() {
        PaintShutdownService.disable(this);
    }
    public void clearPaintTransientWorldState() {
        var cleanup = transientCleanup();
        if (cleanup != null) {
            cleanup.clearWorldState(plugin.getServer().getWorlds());
        }
    }
    public PaintCanvas createCanvas(Player player) {
        return features().createCanvas(player);
    }
    public PaintCanvas createCanvas(Player player, int blockWidth, int blockHeight) {
        return features().createCanvas(player, blockWidth, blockHeight);
    }
    public PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right) {
        return features().createCanvas(ownerId, origin, facing, right);
    }
    public PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right, int blockWidth, int blockHeight) {
        return features().createCanvas(ownerId, origin, facing, right, blockWidth, blockHeight);
    }
    public boolean removeCanvas(UUID ownerId) {
        return features().removeCanvas(ownerId);
    }
    public boolean clearCanvas(UUID ownerId) {
        return features().clearCanvas(ownerId);
    }
    public void giveTools(Player player) {
        inventoryTools().giveTools(player);
    }
    public Optional<PaintCanvas> canvas(UUID ownerId) {
        return features().canvas(ownerId);
    }
    public boolean hasCanvas(UUID ownerId) {
        return features().hasCanvas(ownerId);
    }
    public void onCanvasClick(PlayerInteractEvent event) {
        interactionRouter().onCanvasClick(event);
    }
    public void onCanvasFrameHit(EntityDamageByEntityEvent event) {
        interactionRouter().onCanvasFrameHit(event);
    }
    public void onArtworkPlacementDisplayUse(PlayerInteractAtEntityEvent event) {
        interactionRouter().onArtworkPlacementDisplayUse(event);
    }
    public void onCanvasFrameUse(PlayerInteractEntityEvent event) {
        interactionRouter().onCanvasFrameUse(event);
    }
    public void stopCanvasMapSendTask() {
        var maps = canvasMaps();
        if (maps != null) {
            maps.stopTask();
        }
    }
    private PaintControllerFeatureService features() {
        return runtime.core().featureService;
    }
    private InventoryToolWorkflowService inventoryTools() {
        return runtime.panel().inventoryToolWorkflow;
    }
    private PaintInteractionRouter interactionRouter() {
        return runtime.panel().interactionRouter;
    }
    private PaintTransientCleanupService transientCleanup() {
        return runtime.core().transientCleanup;
    }
    private CanvasMapSyncService canvasMaps() {
        return runtime.canvas().canvasMaps;
    }

    public record CanvasHandle(PlayerCanvas canvas) implements PaintCanvas {
        @Override
        public UUID ownerId() {
            return canvas.ownerId();
        }
        @Override
        public int width() {
            return canvas.pixelCanvas().width();
        }
        @Override
        public int height() {
            return canvas.pixelCanvas().height();
        }
        @Override
        public boolean hasPaintedPixels() {
            return canvas.pixelCanvas().hasPaintedPixels();
        }
        @Override
        public Color[] snapshot() {
            return canvas.pixelCanvas().snapshot();
        }
    }
}
