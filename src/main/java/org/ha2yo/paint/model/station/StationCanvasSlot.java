package org.ha2yo.paint.model.station;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

public record StationCanvasSlot(
        UUID worldId,
        int x,
        int y,
        int z,
        BlockFace facing,
        BlockFace right,
        int width,
        int height
) {
    public static StationCanvasSlot from(Location origin, BlockFace facing, BlockFace right, int width, int height) {
        if (origin.getWorld() == null) {
            throw new IllegalArgumentException("origin world is null");
        }
        return new StationCanvasSlot(
                origin.getWorld().getUID(),
                origin.getBlockX(),
                origin.getBlockY(),
                origin.getBlockZ(),
                facing,
                right,
                width,
                height
        );
    }
}
