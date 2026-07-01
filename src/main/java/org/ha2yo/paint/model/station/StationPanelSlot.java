package org.ha2yo.paint.model.station;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

public record StationPanelSlot(
        UUID worldId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        BlockFace facing,
        Layout layout
) {
    public enum Layout {
        HORIZONTAL,
        VERTICAL
    }

    public StationPanelSlot(
            UUID worldId,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            BlockFace facing
    ) {
        this(worldId, x, y, z, yaw, pitch, facing, Layout.HORIZONTAL);
    }

    public static StationPanelSlot from(Location location, BlockFace facing) {
        return from(location, facing, Layout.HORIZONTAL);
    }

    public static StationPanelSlot from(Location location, BlockFace facing, Layout layout) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location world is null");
        }
        return new StationPanelSlot(
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                facing,
                layout == null ? Layout.HORIZONTAL : layout
        );
    }

    public boolean vertical() {
        return layout == Layout.VERTICAL;
    }
}
