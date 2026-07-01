package org.ha2yo.paint.bootstrap;

import org.ha2yo.paint.PaintApplication;

final class PaintInteractionBootstrap {
    private PaintInteractionBootstrap() {
    }
    static void configure(PaintApplication c) {
        PaintToolInteractionBootstrap.configure(c);
        PaintGalleryBootstrap.configure(c);
        PaintDrawingBootstrap.configure(c);
        PaintPanelCommandBootstrap.configure(c);
        PaintRuntimeInteractionBootstrap.configure(c);
    }
}