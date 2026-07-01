package org.ha2yo.paint.model.session;

import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PaintExhibit;

import java.util.UUID;

public record LookedExhibit(UUID entityId, PaintExhibit exhibit, PaintArtwork artwork) {
}
