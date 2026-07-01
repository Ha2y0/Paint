package org.ha2yo.paint.model.session;

import java.util.List;
import java.util.UUID;

public record CanvasPlacementSession(
        int width,
        int height,
        int preferredDistance,
        List<UUID> displayIds,
        ArtworkPlacementCandidate lastCandidate,
        long armedAtMillis
) {
    public CanvasPlacementSession withPreview(List<UUID> displayIds, ArtworkPlacementCandidate candidate) {
        return new CanvasPlacementSession(width, height, preferredDistance, displayIds, candidate, armedAtMillis);
    }

    public CanvasPlacementSession withPreferredDistance(int preferredDistance) {
        return new CanvasPlacementSession(width, height, preferredDistance, displayIds, lastCandidate, armedAtMillis);
    }

    public boolean isArmed(long now, long armDelayMillis) {
        return now >= armedAtMillis + armDelayMillis;
    }
}
