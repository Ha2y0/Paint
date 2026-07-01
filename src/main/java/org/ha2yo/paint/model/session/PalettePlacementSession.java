package org.ha2yo.paint.model.session;

import java.util.List;
import java.util.UUID;

public record PalettePlacementSession(
        List<UUID> displayIds,
        ArtworkPlacementCandidate lastCandidate,
        long armedAtMillis
) {
    public PalettePlacementSession withPreview(List<UUID> displayIds, ArtworkPlacementCandidate candidate) {
        return new PalettePlacementSession(displayIds, candidate, armedAtMillis);
    }

    public boolean isArmed(long now, long armDelayMillis) {
        return now >= armedAtMillis + armDelayMillis;
    }
}
