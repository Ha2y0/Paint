package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.mode.ArtworkPlacementModeService;
import org.ha2yo.paint.mode.CanvasPlacementModeService;
import org.ha2yo.paint.mode.ExhibitRemovalModeService;
import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.model.session.LookedExhibit;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.CanvasLookService.LookResult;
import org.ha2yo.paint.service.PlacementPreviewService;
import org.ha2yo.paint.workflow.ExhibitRemovalWorkflowService;
import org.ha2yo.paint.workflow.PlacementModeWorkflowService;
import org.ha2yo.paint.workflow.ToolModeGuardService;

final class PaintPlacementBootstrap {
    private PaintPlacementBootstrap() {
    }
    static void configure(PaintApplication c) {
        var runtime = c.runtime();
        var coreRuntime = runtime.core();
        var canvasRuntime = runtime.canvas();
        var artworkRuntime = runtime.artwork();
        var placementRuntime = runtime.placement();
        var panelRuntime = runtime.panel();
        placementRuntime.placementPreviews = new PlacementPreviewService(c.plugin(), PaintApplication.ARTWORK_PLACEMENT_PREVIEW_TAG, coreRuntime.featureService::isPlacementBlockBlocked);
        placementRuntime.canvasPlacementModes = new CanvasPlacementModeService(
                c.plugin(),
                coreRuntime.previewActionKey,
                PaintApplication.ARTWORK_PLACEMENT_TOOL_SLOT,
                PaintApplication.DEFAULT_CANVAS_PLACEMENT_DISTANCE,
                PaintApplication.MAX_CANVAS_PLACEMENT_DISTANCE,
                PaintApplication.ARTWORK_PLACEMENT_ARM_DELAY_MILLIS,
                PaintApplication.PREVIEW_ACTION_CANVAS_PLACEMENT,
                player -> {
                    coreRuntime.featureService.endArtworkPreview(player);
                    placementRuntime.placementUiWorkflow.endCanvas(player, false);
                    placementRuntime.placementUiWorkflow.endArtwork(player, false);
                    placementRuntime.placementUiWorkflow.endExhibitRemoval(player, false);
                },
                panelRuntime.inventoryToolWorkflow::clearArtworkPlacementInventory,
                panelRuntime.inventoryToolWorkflow::restoreModeInventory,
                placementRuntime.placementPreviews::canvasCandidate,
                placementRuntime.placementPreviews::spawnDisplays,
                coreRuntime.featureService::removeCanvasPlacementDisplays,
                (player, candidate, width, height) -> coreRuntime.featureService.createCanvas(
                        player.getUniqueId(),
                        candidate.origin().getLocation(),
                        candidate.facing(),
                        candidate.right(),
                        width,
                        height
                ),
                panelRuntime.inventoryModes::putIfAbsent,
                panelRuntime.inventoryToolWorkflow::giveTools
        );
        placementRuntime.artworkPlacementModes = new ArtworkPlacementModeService(
                c.plugin(),
                coreRuntime.previewActionKey,
                PaintApplication.ARTWORK_PLACEMENT_TOOL_SLOT,
                PaintApplication.DEFAULT_ARTWORK_PLACEMENT_DISTANCE,
                PaintApplication.MAX_ARTWORK_PLACEMENT_DISTANCE,
                PaintApplication.ARTWORK_PLACEMENT_ARM_DELAY_MILLIS,
                PaintApplication.PREVIEW_ACTION_ARTWORK_PLACEMENT,
                player -> {
                    coreRuntime.featureService.endArtworkPreview(player);
                    placementRuntime.placementUiWorkflow.endArtwork(player, false);
                },
                panelRuntime.inventoryToolWorkflow::clearArtworkPlacementInventory,
                panelRuntime.inventoryToolWorkflow::restoreModeInventory,
                placementRuntime.placementPreviews::artworkCandidate,
                placementRuntime.placementPreviews::spawnDisplays,
                coreRuntime.featureService::removeArtworkPlacementDisplays,
                (player, artwork, candidate, width, height, frameStyle) -> artworkRuntime.artworkDisplays.place(
                        artwork,
                        artworkRuntime.artworkStorage.imageFile(artwork),
                        new ArtworkDisplayService.Placement(
                                candidate.world(),
                                candidate.origin(),
                                candidate.facing(),
                                candidate.right(),
                                candidate.up(),
                                width,
                                height,
                                frameStyle
                        )
                ),
                artworkRuntime.artworkDisplays::cycleFrameStyleByEntity,
                player -> {
                    if (placementRuntime.exhibitRemovalWorkflow == null) {
                        return null;
                    }
                    LookedExhibit looked = placementRuntime.exhibitRemovalWorkflow.lookedExhibit(player);
                    return looked == null ? null : looked.entityId();
                }
        );
        placementRuntime.exhibitRemovalWorkflow = new ExhibitRemovalWorkflowService(
                artworkRuntime.artworkDisplays,
                artworkRuntime.artworkStorage,
                PaintApplication.EXHIBIT_REMOVE_INTERACTION_DISTANCE
        );
        placementRuntime.exhibitRemovalModes = new ExhibitRemovalModeService(
                c.plugin(),
                coreRuntime.previewActionKey,
                PaintApplication.ARTWORK_PLACEMENT_TOOL_SLOT,
                PaintApplication.EXHIBIT_REMOVE_CONFIRM_MILLIS,
                PaintApplication.EXHIBIT_REMOVE_CLICK_COOLDOWN_MILLIS,
                PaintApplication.EXHIBIT_REMOVE_ACTIONBAR_OVERRIDE_MILLIS,
                PaintApplication.PREVIEW_ACTION_EXHIBIT_REMOVAL,
                player -> {
                    coreRuntime.featureService.endArtworkPreview(player);
                    placementRuntime.placementUiWorkflow.endArtwork(player, false);
                    placementRuntime.placementUiWorkflow.endExhibitRemoval(player, false);
                    player.closeInventory();
                    coreRuntime.featureService.clearPaintPanel(player.getUniqueId());
                },
                panelRuntime.inventoryToolWorkflow::clearArtworkPlacementInventory,
                panelRuntime.inventoryToolWorkflow::restoreModeInventory,
                placementRuntime.exhibitRemovalWorkflow::lookedExhibit,
                player -> {
                    LookResult look = canvasRuntime.canvasLookService.lookedCanvas(
                            player,
                            canvasRuntime.canvasLifecycle.canvases(),
                            PaintApplication.EXHIBIT_REMOVE_INTERACTION_DISTANCE
                    );
                    return look == null ? null : look.canvas();
                },
                placementRuntime.exhibitRemovalWorkflow::canRemoveExhibit,
                placementRuntime.exhibitRemovalWorkflow::canRemoveCanvas,
                placementRuntime.exhibitRemovalWorkflow::removeExhibit,
                canvas -> coreRuntime.featureService.removeCanvas(canvas.ownerId()),
                placementRuntime.exhibitRemovalWorkflow::exhibitInfoText,
                placementRuntime.exhibitRemovalWorkflow::canvasRemovalInfoText
        );
        placementRuntime.placementModeWorkflow = new PlacementModeWorkflowService(
                placementRuntime.artworkPlacementModes,
                placementRuntime.canvasPlacementModes,
                placementRuntime.exhibitRemovalModes,
                coreRuntime.featureService::artworkBlockWidth,
                coreRuntime.featureService::artworkBlockHeight,
                coreRuntime.featureService::clampCanvasBlockSize,
                () -> {
                    if (placementRuntime.placementPreviews != null) {
                        placementRuntime.placementPreviews.clearTaggedDisplays();
                    }
                }
        );
        panelRuntime.toolModeGuards = new ToolModeGuardService(
                PaintApplication.ARTWORK_PLACEMENT_TOOL_SLOT,
                panelRuntime.inventoryToolWorkflow::isPaintPanelModeActive,
                panelRuntime.inventoryToolWorkflow::isArtworkPlacementModeActive,
                placementRuntime.placementModeWorkflow::isArtworkFrameSelectionActive,
                panelRuntime.inventoryToolWorkflow::isCanvasPlacementModeActive,
                panelRuntime.inventoryToolWorkflow::isExhibitRemovalModeActive,
                placementRuntime.placementModeWorkflow::isArtworkFrameMaterialSlot,
                panelRuntime.inventoryToolWorkflow::ensurePaintPanelTool,
                panelRuntime.inventoryToolWorkflow::ensureArtworkPlacementTool,
                panelRuntime.inventoryToolWorkflow::ensureCanvasPlacementTool,
                panelRuntime.inventoryToolWorkflow::ensureExhibitRemovalTool,
                coreRuntime.featureService::adjustArtworkPlacementDistance,
                coreRuntime.featureService::adjustCanvasPlacementDistance,
                placementRuntime.placementUiWorkflow::endArtwork,
                placementRuntime.placementUiWorkflow::endCanvas,
                placementRuntime.placementUiWorkflow::endExhibitRemoval
        );
    }
}
