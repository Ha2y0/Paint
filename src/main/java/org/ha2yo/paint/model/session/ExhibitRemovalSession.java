package org.ha2yo.paint.model.session;

import java.util.UUID;

public record ExhibitRemovalSession(UUID confirmExhibitId, UUID confirmCanvasOwnerId, long confirmUntilMillis) {
    public ExhibitRemovalSession withExhibitConfirm(UUID exhibitId, long untilMillis) {
        return new ExhibitRemovalSession(exhibitId, null, untilMillis);
    }

    public ExhibitRemovalSession withCanvasConfirm(UUID canvasOwnerId, long untilMillis) {
        return new ExhibitRemovalSession(null, canvasOwnerId, untilMillis);
    }

    public ExhibitRemovalSession clearConfirm() {
        return new ExhibitRemovalSession(null, null, 0L);
    }

    public boolean confirmExhibitActive(UUID exhibitId, long now) {
        return confirmExhibitId != null && confirmExhibitId.equals(exhibitId) && now <= confirmUntilMillis;
    }

    public boolean confirmCanvasActive(UUID canvasOwnerId, long now) {
        return confirmCanvasOwnerId != null && confirmCanvasOwnerId.equals(canvasOwnerId) && now <= confirmUntilMillis;
    }

    public boolean confirmExpired(long now) {
        return (confirmExhibitId != null || confirmCanvasOwnerId != null) && now > confirmUntilMillis;
    }
}
