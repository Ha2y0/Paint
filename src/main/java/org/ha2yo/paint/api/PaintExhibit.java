package org.ha2yo.paint.api;

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
        String frameStyle,
        String frameMaterial
) {
}
