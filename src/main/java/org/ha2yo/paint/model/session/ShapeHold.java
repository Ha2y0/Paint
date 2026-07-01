package org.ha2yo.paint.model.session;

import org.ha2yo.paint.model.PixelPoint;
import org.ha2yo.paint.model.tool.Tool;

import java.util.UUID;

public record ShapeHold(Tool tool, UUID canvasId, PixelPoint start, PixelPoint previewEnd, int previewVersion) {
    public ShapeHold withPreviewEnd(PixelPoint previewEnd, int previewVersion) {
        return new ShapeHold(tool, canvasId, start, previewEnd, previewVersion);
    }
}
