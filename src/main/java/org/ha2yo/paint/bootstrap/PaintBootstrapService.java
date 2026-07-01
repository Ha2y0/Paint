package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;

public final class PaintBootstrapService {
    private PaintBootstrapService() {
    }
    public static void enable(PaintApplication c) {
        PaintCoreBootstrap.configure(c);
        PaintCanvasPaletteBootstrap.configure(c);
        PaintPlacementBootstrap.configure(c);
        PaintInteractionBootstrap.configure(c);
        PaintRegistrationBootstrap.register(c);
    }
}