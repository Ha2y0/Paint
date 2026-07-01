package org.ha2yo.paint.runtime;

public final class PaintRuntime {
    private final CoreRuntime core = new CoreRuntime();
    private final DrawingRuntime drawing = new DrawingRuntime();
    private final CanvasRuntime canvas = new CanvasRuntime();
    private final ArtworkRuntime artwork = new ArtworkRuntime();
    private final PlacementRuntime placement = new PlacementRuntime();
    private final PanelRuntime panel = new PanelRuntime();

    public CoreRuntime core() {
        return core;
    }

    public DrawingRuntime drawing() {
        return drawing;
    }

    public CanvasRuntime canvas() {
        return canvas;
    }

    public ArtworkRuntime artwork() {
        return artwork;
    }

    public PlacementRuntime placement() {
        return placement;
    }

    public PanelRuntime panel() {
        return panel;
    }
}