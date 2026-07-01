package org.ha2yo.paint.model.session;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public record ArtworkPlacementCandidate(
        World world,
        Block origin,
        BlockFace facing,
        BlockFace right,
        BlockFace up,
        boolean valid,
        boolean snappedToSurface
) {
}
