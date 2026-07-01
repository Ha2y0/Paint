package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.command.PaintCommand;
import org.ha2yo.paint.event.PaintCanvasEventController;
import org.ha2yo.paint.event.PaintPlayerEventController;
import org.ha2yo.paint.event.PaintToolEventController;
import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.runtime.DefaultPaintService;

final class PaintRegistrationBootstrap {
    private PaintRegistrationBootstrap() {
    }
    static void register(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var canvasRuntime = runtime.canvas();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        c.plugin().getServer().getPluginManager().registerEvents(new PaintToolEventController(
                panelRuntime.toolEventWorkflow::onToolDrop,
                panelRuntime.toolEventWorkflow::onToolDrag,
                panelRuntime.toolEventWorkflow::onPaletteClick,
                panelRuntime.toolEventWorkflow::onToolSlotChange,
                panelRuntime.toolEventWorkflow::onToolSwap,
                panelRuntime.toolEventWorkflow::onStopUsingItem,
                panelRuntime.toolEventWorkflow::onPickItem
        ), c.plugin());
        c.plugin().getServer().getPluginManager().registerEvents(new PaintCanvasEventController(
                panelRuntime.canvasEventWorkflow::onCanvasPress,
                panelRuntime.canvasEventWorkflow::onPaletteSwing,
                c::onCanvasClick,
                c::onCanvasFrameHit,
                c::onArtworkPlacementDisplayUse,
                c::onCanvasFrameUse,
                panelRuntime.toolEventWorkflow::onToolBucketEmpty,
                panelRuntime.canvasEventWorkflow::onCanvasBreak,
                panelRuntime.canvasEventWorkflow::onCanvasFrameBreak
        ), c.plugin());
        c.plugin().getServer().getPluginManager().registerEvents(new PaintPlayerEventController(
                c.plugin(),
                canvasRuntime.paletteLayerWorkflow::syncLayerVisibility,
                canvasRuntime.paletteBoards::syncVisibility,
                placementRuntime.placementUiWorkflow::syncCanvasVisibility,
                placementRuntime.placementUiWorkflow::syncArtworkVisibility,
                coreRuntime.featureService::hasCanvas,
                panelRuntime.inventoryToolWorkflow::giveTools,
                panelRuntime.inventoryToolWorkflow::clearStrayPaintTools,
                panelRuntime.playerLifecycleWorkflow::onQuit
        ), c.plugin());
        if (c.plugin().getCommand("paint") != null) {
            PaintCommand paintCommand = new PaintCommand(panelRuntime.paintCommandWorkflow);
            c.plugin().getCommand("paint").setExecutor(paintCommand);
            c.plugin().getCommand("paint").setTabCompleter(paintCommand);
        }
        coreRuntime.paintService = new DefaultPaintService(
                () -> coreRuntime.featureService,
                () -> panelRuntime.inventoryToolWorkflow,
                () -> runtime.artwork().artworkStorage,
                () -> runtime.artwork().artworkDisplays,
                () -> canvasRuntime.paletteBoards,
                () -> runtime.drawing().selectedColors,
                () -> runtime.drawing().brushRadii,
                () -> runtime.drawing().paletteModes,
                () -> runtime.drawing().paletteAccessOwners
        );
        coreRuntime.paintTask = c.plugin().getServer().getScheduler().runTaskTimer(c.plugin(), panelRuntime.paintTickWorkflow::tick, 1L, 1L);
    }
}
