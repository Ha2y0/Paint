package org.ha2yo.paint.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.ha2yo.paint.api.PaintArtwork;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.api.PaintExhibit;
import org.ha2yo.paint.api.PaintService;
import org.ha2yo.paint.PaintApplication;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.PaletteMode;
import org.ha2yo.paint.service.PaletteBoardService;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkStorageService;
import org.ha2yo.paint.workflow.InventoryToolWorkflowService;
import org.ha2yo.paint.workflow.PaintControllerFeatureService;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultPaintService implements PaintService {
    private static final Color DEFAULT_SELECTED_COLOR = Color.BLACK;
    private static final PaletteMode DEFAULT_PALETTE_MODE = PaletteMode.GRADIENT;

    private final Supplier<PaintControllerFeatureService> featureService;
    private final Supplier<InventoryToolWorkflowService> inventoryToolWorkflow;
    private final Supplier<ArtworkStorageService> artworkStorage;
    private final Supplier<ArtworkDisplayService> artworkDisplays;
    private final Supplier<PaletteBoardService> paletteBoards;
    private final Supplier<Map<UUID, Color>> selectedColors;
    private final Supplier<Map<UUID, Integer>> brushRadii;
    private final Supplier<Map<UUID, PaletteMode>> paletteModes;
    private final Supplier<Set<UUID>> paletteAccessOwners;

    public DefaultPaintService(
            Supplier<PaintControllerFeatureService> featureService,
            Supplier<InventoryToolWorkflowService> inventoryToolWorkflow,
            Supplier<ArtworkStorageService> artworkStorage,
            Supplier<ArtworkDisplayService> artworkDisplays,
            Supplier<PaletteBoardService> paletteBoards,
            Supplier<Map<UUID, Color>> selectedColors,
            Supplier<Map<UUID, Integer>> brushRadii,
            Supplier<Map<UUID, PaletteMode>> paletteModes,
            Supplier<Set<UUID>> paletteAccessOwners
    ) {
        this.featureService = featureService;
        this.inventoryToolWorkflow = inventoryToolWorkflow;
        this.artworkStorage = artworkStorage;
        this.artworkDisplays = artworkDisplays;
        this.paletteBoards = paletteBoards;
        this.selectedColors = selectedColors;
        this.brushRadii = brushRadii;
        this.paletteModes = paletteModes;
        this.paletteAccessOwners = paletteAccessOwners;
    }

    @Override
    public PaintCanvas createCanvas(Player player) {
        PaintCanvas canvas = featureService.get().createCanvas(player);
        if (canvas != null) {
            resetPaletteState(player.getUniqueId());
        }
        return canvas;
    }

    @Override
    public PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right) {
        PaintCanvas canvas = featureService.get().createCanvas(ownerId, origin, facing, right);
        if (canvas != null) {
            resetPaletteState(ownerId);
        }
        return canvas;
    }

    @Override
    public PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right, int width, int height) {
        PaintCanvas canvas = featureService.get().createCanvas(ownerId, origin, facing, right, width, height);
        if (canvas != null) {
            resetPaletteState(ownerId);
        }
        return canvas;
    }

    @Override
    public boolean removeCanvas(UUID ownerId) {
        boolean removed = featureService.get().removeCanvas(ownerId);
        if (removed) {
            resetPaletteState(ownerId);
        }
        return removed;
    }

    @Override
    public boolean clearCanvas(UUID ownerId) {
        boolean cleared = featureService.get().clearCanvas(ownerId);
        if (cleared) {
            resetPaletteState(ownerId);
        }
        return cleared;
    }

    @Override
    public Optional<PaintCanvas> canvas(UUID ownerId) {
        return featureService.get().canvas(ownerId);
    }

    @Override
    public boolean hasCanvas(UUID ownerId) {
        return featureService.get().hasCanvas(ownerId);
    }

    @Override
    public boolean grantCanvasEditAccess(UUID ownerId, UUID editorId) {
        if (ownerId == null || editorId == null) {
            return false;
        }
        return featureService.get().grantCanvasEditAccess(ownerId, editorId);
    }

    @Override
    public boolean revokeCanvasEditAccess(UUID ownerId, UUID editorId) {
        if (ownerId == null || editorId == null) {
            return false;
        }
        return featureService.get().revokeCanvasEditAccess(ownerId, editorId);
    }

    @Override
    public boolean setCanvasVisibleFor(UUID ownerId, Player viewer, boolean visible) {
        if (ownerId == null || viewer == null) {
            return false;
        }
        return featureService.get().setCanvasVisibleFor(ownerId, viewer, visible);
    }

    @Override
    public Optional<Color> selectedColor(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(selectedColors.get().getOrDefault(playerId, DEFAULT_SELECTED_COLOR));
    }

    @Override
    public OptionalInt brushRadius(UUID playerId) {
        if (playerId == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(brushRadii.get().getOrDefault(playerId, PaintApplication.DEFAULT_BRUSH_RADIUS));
    }

    @Override
    public boolean selectColor(UUID playerId, Color color) {
        if (playerId == null || color == null) {
            return false;
        }
        selectedColors.get().put(playerId, color);
        PaletteBoardService boards = paletteBoards.get();
        if (boards != null) {
            boards.updateSelectedColor(playerId, color);
        }
        return true;
    }

    @Override
    public boolean grantPaletteAccess(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return paletteAccessOwners.get().add(playerId);
    }

    @Override
    public boolean revokePaletteAccess(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return paletteAccessOwners.get().remove(playerId);
    }

    @Override
    public Optional<PaintArtwork> saveCanvas(UUID ownerId, String title) {
        Player player = Bukkit.getPlayer(ownerId);
        if (player == null) {
            return Optional.empty();
        }
        Optional<PaintCanvas> canvas = featureService.get().canvas(ownerId);
        Optional<PlayerCanvas> playerCanvas = featureService.get().playerCanvas(ownerId);
        if (canvas.isEmpty() || playerCanvas.isEmpty() || !canvas.get().hasPaintedPixels()) {
            return Optional.empty();
        }
        try {
            return Optional.of(toApi(artworkStorage.get().save(player, canvas.get(), title, playerCanvas.get().pixelCanvas().layerSnapshot())));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaintArtwork> saveArtwork(UUID ownerId, String ownerName, String title, int width, int height, Color[] pixels) {
        if (ownerId == null || width <= 0 || height <= 0 || pixels == null || pixels.length != width * height) {
            return Optional.empty();
        }
        SnapshotCanvas canvas = new SnapshotCanvas(ownerId, width, height, pixels.clone());
        if (!canvas.hasPaintedPixels()) {
            return Optional.empty();
        }
        try {
            return Optional.of(toApi(artworkStorage.get().save(ownerId, ownerName, canvas, title)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaintArtwork> artworks(UUID ownerId) {
        return artworkStorage.get().listByOwner(ownerId).stream()
                .map(this::toApi)
                .toList();
    }

    @Override
    public Optional<PaintArtwork> artwork(UUID artworkId) {
        return artworkStorage.get().find(artworkId).map(this::toApi);
    }

    @Override
    public Optional<PaintExhibit> displayArtwork(
            UUID artworkId,
            Location origin,
            BlockFace facing,
            BlockFace right,
            BlockFace up,
            int width,
            int height
    ) {
        return displayArtwork(artworkId, origin, facing, right, up, width, height, null);
    }

    @Override
    public Optional<PaintExhibit> displayArtwork(
            UUID artworkId,
            Location origin,
            BlockFace facing,
            BlockFace right,
            BlockFace up,
            int width,
            int height,
            Material frameMaterial
    ) {
        if (origin == null || origin.getWorld() == null) {
            return Optional.empty();
        }
        Optional<org.ha2yo.paint.model.PaintArtwork> artwork = artworkStorage.get().find(artworkId);
        if (artwork.isEmpty()) {
            return Optional.empty();
        }
        ExhibitFrameStyle frameStyle = frameMaterial == null ? ExhibitFrameStyle.NONE : ExhibitFrameStyle.FRAME;
        String frameMaterialName = frameStyle == ExhibitFrameStyle.NONE ? "" : frameMaterial.name();
        try {
            Block originBlock = origin.getBlock();
            org.ha2yo.paint.model.PaintExhibit exhibit = artworkDisplays.get().place(
                    artwork.get(),
                    artworkStorage.get().imageFile(artwork.get()),
                    new ArtworkDisplayService.Placement(
                            origin.getWorld(),
                            originBlock,
                            facing,
                            right,
                            up,
                            width,
                            height,
                            frameStyle,
                            frameMaterialName
                    )
            );
            return Optional.of(toApi(exhibit));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean removeExhibit(UUID exhibitId) {
        try {
            return artworkDisplays.get().removeById(exhibitId);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Optional<PaintExhibit> exhibitByEntity(UUID entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        return artworkDisplays.get().findByEntity(entityId).map(this::toApi);
    }

    @Override
    public List<PaintExhibit> exhibits() {
        return artworkDisplays.get().exhibits().stream()
                .map(this::toApi)
                .toList();
    }

    @Override
    public void giveTools(Player player) {
        inventoryToolWorkflow.get().giveTools(player);
    }

    @Override
    public void resetPaletteState(UUID ownerId) {
        if (ownerId == null) {
            return;
        }

        selectedColors.get().put(ownerId, DEFAULT_SELECTED_COLOR);
        brushRadii.get().put(ownerId, PaintApplication.DEFAULT_BRUSH_RADIUS);
        paletteModes.get().put(ownerId, DEFAULT_PALETTE_MODE);
        PaletteBoardService boards = paletteBoards.get();
        if (boards != null) {
            boards.updateSelectedColor(ownerId, DEFAULT_SELECTED_COLOR);
        }
    }

    @Override
    public void resetPaletteStates() {
        Set<UUID> ownerIds = new java.util.HashSet<>();
        ownerIds.addAll(selectedColors.get().keySet());
        ownerIds.addAll(brushRadii.get().keySet());
        ownerIds.addAll(paletteModes.get().keySet());
        ownerIds.addAll(paletteAccessOwners.get());
        for (UUID ownerId : ownerIds) {
            resetPaletteState(ownerId);
        }
    }

    @Override
    public void clearPaletteBoards() {
        PaletteBoardService boards = paletteBoards.get();
        if (boards != null) {
            boards.clearAll();
        }
    }

    private PaintArtwork toApi(org.ha2yo.paint.model.PaintArtwork artwork) {
        return new PaintArtwork(
                artwork.id(),
                artwork.ownerId(),
                artwork.ownerName(),
                artwork.title(),
                artwork.width(),
                artwork.height(),
                artwork.createdAt(),
                artwork.imagePath()
        );
    }

    private PaintExhibit toApi(org.ha2yo.paint.model.PaintExhibit exhibit) {
        return new PaintExhibit(
                exhibit.id(),
                exhibit.artworkId(),
                exhibit.imagePath(),
                exhibit.worldName(),
                exhibit.x(),
                exhibit.y(),
                exhibit.z(),
                exhibit.facing(),
                exhibit.right(),
                exhibit.up(),
                exhibit.width(),
                exhibit.height(),
                exhibit.frameStyle().name(),
                exhibit.frameMaterial()
        );
    }

    private record SnapshotCanvas(UUID ownerId, int width, int height, Color[] pixels) implements PaintCanvas {
        @Override
        public boolean hasPaintedPixels() {
            return Arrays.stream(pixels).anyMatch(color -> color != null);
        }

        @Override
        public Color[] snapshot() {
            return pixels.clone();
        }
    }
}
