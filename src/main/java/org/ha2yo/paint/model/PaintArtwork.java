package org.ha2yo.paint.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaintArtwork(
        UUID id,
        UUID ownerId,
        String ownerName,
        String title,
        int width,
        int height,
        LocalDateTime createdAt,
        String imagePath
) {
    public String displayName() {
        return title == null || title.isBlank() ? imagePath : title;
    }
}
