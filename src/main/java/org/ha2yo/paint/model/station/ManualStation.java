package org.ha2yo.paint.model.station;

import java.util.UUID;

public record ManualStation(
        String id,
        StationCanvasSlot canvas,
        StationPanelSlot gallery,
        StationPanelSlot control,
        StationPaletteSlot palette,
        UUID occupantId
) {
    public ManualStation withCanvas(StationCanvasSlot slot) {
        return new ManualStation(id, slot, gallery, control, palette, occupantId);
    }

    public ManualStation withGallery(StationPanelSlot slot) {
        return new ManualStation(id, canvas, slot, control, palette, occupantId);
    }

    public ManualStation withControl(StationPanelSlot slot) {
        return new ManualStation(id, canvas, gallery, slot, palette, occupantId);
    }

    public ManualStation withPalette(StationPaletteSlot slot) {
        return new ManualStation(id, canvas, gallery, control, slot, occupantId);
    }

    public ManualStation withOccupant(UUID playerId) {
        return new ManualStation(id, canvas, gallery, control, palette, playerId);
    }

    public boolean ready() {
        return canvas != null && gallery != null && control != null && palette != null;
    }

    public boolean occupied() {
        return occupantId != null;
    }
}
