package org.ha2yo.paint.model.session;

import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.ExhibitFrameStyle;

import java.util.List;
import java.util.UUID;

public record ArtworkPlacementSession(
        PaintArtwork artwork,
        int width,
        int height,
        int preferredDistance,
        List<UUID> displayIds,
        ArtworkPlacementCandidate lastCandidate,
        ExhibitFrameStyle frameStyle,
        boolean frameSelectionOnly,
        long armedAtMillis
) {
    public ArtworkPlacementSession withPreview(List<UUID> displayIds, ArtworkPlacementCandidate candidate) {
        return new ArtworkPlacementSession(artwork, width, height, preferredDistance, displayIds, candidate, frameStyle, frameSelectionOnly, armedAtMillis);
    }

    public ArtworkPlacementSession withPreferredDistance(int preferredDistance) {
        return new ArtworkPlacementSession(artwork, width, height, preferredDistance, displayIds, lastCandidate, frameStyle, frameSelectionOnly, armedAtMillis);
    }

    public ArtworkPlacementSession withFrameStyle(ExhibitFrameStyle frameStyle) {
        return new ArtworkPlacementSession(artwork, width, height, preferredDistance, displayIds, lastCandidate, frameStyle, frameSelectionOnly, armedAtMillis);
    }

    public boolean isArmed(long now, long armDelayMillis) {
        return now >= armedAtMillis + armDelayMillis;
    }
}
