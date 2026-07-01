package org.ha2yo.paint.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkGalleryService;
import org.ha2yo.paint.service.ArtworkImageService;
import org.ha2yo.paint.service.ArtworkPreviewService;
import org.ha2yo.paint.service.ArtworkSaveDialogService;
import org.ha2yo.paint.service.ArtworkStorageService;
import org.ha2yo.paint.workflow.ArtworkGalleryWorkflowService;
import org.ha2yo.paint.workflow.ArtworkSaveWorkflowService;

public final class ArtworkRuntime {
    public final Map<UUID, UUID> editingArtworkIds = new HashMap<>();
    public ArtworkStorageService artworkStorage;
    public ArtworkSaveDialogService artworkSaveDialogs;
    public ArtworkImageService artworkImages;
    public ArtworkDisplayService artworkDisplays;
    public ArtworkPreviewService artworkPreviews;
    public ArtworkGalleryService artworkGalleries;
    public ArtworkSaveWorkflowService artworkSaveWorkflow;
    public ArtworkGalleryWorkflowService artworkGalleryWorkflow;
}