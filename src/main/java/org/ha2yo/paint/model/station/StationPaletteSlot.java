package org.ha2yo.paint.model.station;

import java.util.UUID;
import org.bukkit.block.BlockFace;

public record StationPaletteSlot(
        UUID worldId,
        int x,
        int y,
        int z,
        BlockFace facing,
        BlockFace right,
        boolean wallBacked
) {
}
