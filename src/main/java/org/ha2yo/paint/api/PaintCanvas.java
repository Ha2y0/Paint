package org.ha2yo.paint.api;

import java.awt.Color;
import java.util.UUID;

public interface PaintCanvas {
    UUID ownerId();

    int width();

    int height();

    boolean hasPaintedPixels();

    Color[] snapshot();
}