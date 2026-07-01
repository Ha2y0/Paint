package org.ha2yo.paint.model;

import org.bukkit.block.BlockFace;

import java.util.UUID;

public record PaintExhibit(
        UUID id,
        UUID artworkId,
        String imagePath,
        String worldName,
        int x,
        int y,
        int z,
        BlockFace facing,
        BlockFace right,
        BlockFace up,
        int width,
        int height,
        ExhibitFrameStyle frameStyle,
        String frameMaterial
) {
    public PaintExhibit(
            UUID id,
            UUID artworkId,
            String imagePath,
            String worldName,
            int x,
            int y,
            int z,
            BlockFace facing,
            BlockFace right,
            BlockFace up,
            int width,
            int height,
            ExhibitFrameStyle frameStyle
    ) {
        this(id, artworkId, imagePath, worldName, x, y, z, facing, right, up, width, height, frameStyle, "");
    }
}
