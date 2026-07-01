package org.ha2yo.paint.model.session;

import org.ha2yo.paint.service.ArtworkPreviewService;

public record ArtworkPreviewClickStamp(
        ArtworkPreviewService.PreviewClickAction action,
        int artworkIndex,
        long timeMillis
) {
    public boolean matches(ArtworkPreviewService.PreviewClick click) {
        return action == click.action() && artworkIndex == click.artworkIndex();
    }
}
