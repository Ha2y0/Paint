package org.ha2yo.paint.model;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.UUID;

public record CanvasPlane(UUID worldId, int x, int y, int z, BlockFace facing, BlockFace right) {
    public Vector facePoint() {
        BlockFace front = facing.getOppositeFace();
        double px = x;
        double py = y;
        double pz = z;

        if (front.getModX() > 0) {
            px += 1;
        }
        if (front.getModZ() > 0) {
            pz += 1;
        }
        if (right.getModX() < 0) {
            px += 1;
        }
        if (right.getModZ() < 0) {
            pz += 1;
        }

        return new Vector(px, py, pz);
    }
}
