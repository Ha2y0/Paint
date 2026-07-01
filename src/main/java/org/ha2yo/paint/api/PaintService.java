package org.ha2yo.paint.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public interface PaintService {
    PaintCanvas createCanvas(Player player);

    PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right);

    PaintCanvas createCanvas(UUID ownerId, Location origin, BlockFace facing, BlockFace right, int width, int height);

    boolean removeCanvas(UUID ownerId);

    boolean clearCanvas(UUID ownerId);

    Optional<PaintCanvas> canvas(UUID ownerId);

    boolean hasCanvas(UUID ownerId);

    boolean grantCanvasEditAccess(UUID ownerId, UUID editorId);

    boolean revokeCanvasEditAccess(UUID ownerId, UUID editorId);

    Optional<Color> selectedColor(UUID playerId);

    OptionalInt brushRadius(UUID playerId);

    boolean selectColor(UUID playerId, Color color);

    boolean grantPaletteAccess(UUID playerId);

    boolean revokePaletteAccess(UUID playerId);

    void resetPaletteState(UUID playerId);

    void resetPaletteStates();

    void clearPaletteBoards();

    Optional<PaintArtwork> saveCanvas(UUID ownerId, String title);

    Optional<PaintArtwork> saveArtwork(UUID ownerId, String ownerName, String title, int width, int height, Color[] pixels);

    List<PaintArtwork> artworks(UUID ownerId);

    Optional<PaintArtwork> artwork(UUID artworkId);

    Optional<PaintExhibit> displayArtwork(
            UUID artworkId,
            Location origin,
            BlockFace facing,
            BlockFace right,
            BlockFace up,
            int width,
            int height
    );

    Optional<PaintExhibit> displayArtwork(
            UUID artworkId,
            Location origin,
            BlockFace facing,
            BlockFace right,
            BlockFace up,
            int width,
            int height,
            Material frameMaterial
    );

    boolean removeExhibit(UUID exhibitId);

    Optional<PaintExhibit> exhibitByEntity(UUID entityId);

    List<PaintExhibit> exhibits();

    void giveTools(Player player);
}
