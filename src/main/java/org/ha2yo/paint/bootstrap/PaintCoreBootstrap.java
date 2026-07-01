package org.ha2yo.paint.bootstrap;

import org.bukkit.NamespacedKey;
import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.PaintMenuService;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.PaintPanelService;
import org.ha2yo.paint.service.PaintTransientCleanupService;
import org.ha2yo.paint.service.ToolItemService;
import org.ha2yo.paint.workflow.InventoryToolWorkflowService;
import org.ha2yo.paint.workflow.PaintControllerFeatureService;
import org.ha2yo.paint.workflow.PaletteLayerWorkflowService;
import org.ha2yo.paint.workflow.PlacementUiWorkflowService;

final class PaintCoreBootstrap {
    private PaintCoreBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var drawingRuntime = runtime.drawing();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        coreRuntime.paletteColorKey = new NamespacedKey(c.plugin(), "palette_color");
        coreRuntime.brushSizeDeltaKey = new NamespacedKey(c.plugin(), "brush_size_delta");
        coreRuntime.brushSizeValueKey = new NamespacedKey(c.plugin(), "brush_size_value");
        coreRuntime.toolKey = new NamespacedKey(c.plugin(), "tool");
        coreRuntime.menuActionKey = new NamespacedKey(c.plugin(), "menu_action");
        coreRuntime.artworkIdKey = new NamespacedKey(c.plugin(), "artwork_id");
        coreRuntime.previewActionKey = new NamespacedKey(c.plugin(), "preview_action");
        coreRuntime.transientCleanup = new PaintTransientCleanupService(PaintApplication.PAINT_TRANSIENT_ENTITY_TAGS, PaintApplication.CANVAS_FRAME_TAG, PaintApplication.EXHIBIT_DISPLAY_TAG);
        canvasRuntime.canvasLifecycle = new CanvasLifecycleService(PaintApplication.CANVAS_FRAME_TAG);
        coreRuntime.toolItems = new ToolItemService(coreRuntime.paletteColorKey, coreRuntime.brushSizeDeltaKey, coreRuntime.brushSizeValueKey, coreRuntime.toolKey);
        coreRuntime.paintMenus = new PaintMenuService(coreRuntime.menuActionKey, coreRuntime.artworkIdKey);
        coreRuntime.featureService = new PaintControllerFeatureService(
                c.plugin(),
                canvasRuntime.paintWindows,
                drawingRuntime.drawingInteractions,
                drawingRuntime.drawingSessions,
                drawingRuntime.selectedTools,
                drawingRuntime.advancedToolModes,
                PaintApplication.DEFAULT_CANVAS_BLOCK_WIDTH,
                PaintApplication.DEFAULT_CANVAS_BLOCK_HEIGHT,
                PaintApplication.MAX_CANVAS_BLOCK_SIZE,
                PaintApplication.CANVAS_PIXELS_PER_BLOCK,
                () -> canvasRuntime.canvasLifecycle,
                () -> canvasRuntime.canvasWorkflow,
                () -> drawingRuntime.advancedToolWorkflow,
                () -> placementRuntime.placementModeWorkflow,
                () -> panelRuntime.paintPanelWorkflow,
                () -> panelRuntime.paintPanelModes,
                () -> artworkRuntime.artworkGalleryWorkflow,
                () -> artworkRuntime.artworkSaveWorkflow,
                () -> placementRuntime.placementPreviews,
                () -> canvasRuntime.paletteBoards,
                () -> artworkRuntime.artworkDisplays
        );
        panelRuntime.paintPanels = new PaintPanelService(c.plugin(), PaintApplication.BACKGROUND_COLOR, coreRuntime.featureService::shaderRgbEnabled);
        panelRuntime.paintPanelModes = new PaintPanelModeService(
                panelRuntime.paintPanels,
                coreRuntime.previewActionKey,
                PaintApplication.ARTWORK_PLACEMENT_TOOL_SLOT,
                PaintApplication.PAINT_PANEL_CLICK_COOLDOWN_MILLIS,
                PaintApplication.PREVIEW_ACTION_PAINT_PANEL,
                PaintApplication.PAINT_PANEL_ENTITY_INTERACTION_RANGE
        );
        panelRuntime.inventoryToolWorkflow = new InventoryToolWorkflowService(
                panelRuntime.inventoryModes,
                coreRuntime.toolItems,
                panelRuntime.paintPanelModes,
                () -> placementRuntime.placementModeWorkflow,
                drawingRuntime.selectedTools,
                drawingRuntime.advancedToolModes,
                drawingRuntime.rememberedBasicToolSlots,
                drawingRuntime.rememberedAdvancedToolSlots
        );
        placementRuntime.placementUiWorkflow = new PlacementUiWorkflowService(() -> placementRuntime.placementModeWorkflow);
        canvasRuntime.paletteLayerWorkflow = new PaletteLayerWorkflowService(
                () -> canvasRuntime.paletteWorkflow,
                () -> canvasRuntime.layerWorkflow,
                canvasRuntime.canvasLookService,
                10.0D
        );
        c.clearPaintTransientWorldState();
    }
}
