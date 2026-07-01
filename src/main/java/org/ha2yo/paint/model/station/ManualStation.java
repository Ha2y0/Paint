package org.ha2yo.paint.model.station;

import java.util.UUID;

public record ManualStation(
        String id,
        StationCanvasSlot canvas,
        StationPanelSlot gallery,
        StationPanelSlot control,
        UUID occupantId
) {
    public ManualStation withCanvas(StationCanvasSlot slot) {
        return new ManualStation(id, slot, gallery, control, occupantId);
    }

    public ManualStation withGallery(StationPanelSlot slot) {
        return new ManualStation(id, canvas, slot, control, occupantId);
    }

    public ManualStation withControl(StationPanelSlot slot) {
        return new ManualStation(id, canvas, gallery, slot, occupantId);
    }

    public ManualStation withOccupant(UUID playerId) {
        return new ManualStation(id, canvas, gallery, control, playerId);
    }

    public boolean ready() {
        return canvas != null && gallery != null && control != null;
    }

    public boolean occupied() {
        return occupantId != null;
    }
}
