package org.ha2yo.paint.runtime;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.model.tool.PaletteMode;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.AdvancedToolSessionService;
import org.ha2yo.paint.service.DrawingInteractionService;
import org.ha2yo.paint.service.DrawingService;
import org.ha2yo.paint.service.DrawingSessionService;
import org.ha2yo.paint.workflow.AdvancedToolWorkflowService;
import org.ha2yo.paint.workflow.PaintingInteractionWorkflowService;

public final class DrawingRuntime {
    public final DrawingService drawingService = new DrawingService();
    public final DrawingSessionService drawingSessions = new DrawingSessionService();
    public final DrawingInteractionService drawingInteractions = new DrawingInteractionService(drawingService, drawingSessions, PaintApplication.MAX_HISTORY_SIZE);
    public final AdvancedToolSessionService advancedToolSessions = new AdvancedToolSessionService();
    public final Map<UUID, Color> selectedColors = new HashMap<>();
    public final Map<UUID, Tool> selectedTools = new HashMap<>();
    public final Map<UUID, Boolean> advancedToolModes = new HashMap<>();
    public final Map<UUID, Integer> rememberedBasicToolSlots = new HashMap<>();
    public final Map<UUID, Integer> rememberedAdvancedToolSlots = new HashMap<>();
    public final Map<UUID, Integer> brushRadii = new HashMap<>();
    public final Map<UUID, PaletteMode> paletteModes = new HashMap<>();
    public final Map<UUID, Long> lastPaletteRightClickTimes = new HashMap<>();
    public final Set<UUID> paletteAccessOwners = new HashSet<>();
    public final Map<String, Color> palette = Map.ofEntries(
            Map.entry("black", Color.BLACK),
            Map.entry("white", Color.WHITE),
            Map.entry("red", Color.RED),
            Map.entry("orange", Color.ORANGE),
            Map.entry("yellow", Color.YELLOW),
            Map.entry("green", Color.GREEN),
            Map.entry("blue", Color.BLUE),
            Map.entry("cyan", Color.CYAN),
            Map.entry("magenta", Color.MAGENTA),
            Map.entry("pink", Color.PINK),
            Map.entry("gray", Color.GRAY)
    );
    public AdvancedToolWorkflowService advancedToolWorkflow;
    public PaintingInteractionWorkflowService paintingInteractions;
}
