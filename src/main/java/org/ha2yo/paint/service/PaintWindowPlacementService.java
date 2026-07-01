package org.ha2yo.paint.service;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.function.Function;

public final class PaintWindowPlacementService {
    private final Function<BlockFace, Vector> faceVector;

    public PaintWindowPlacementService() {
        this(PaintWindowPlacementService::vectorOf);
    }

    public PaintWindowPlacementService(Function<BlockFace, Vector> faceVector) {
        this.faceVector = faceVector;
    }

    public BlockFace cardinalFace(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) {
            yaw += 360;
        }
        if (yaw >= 315 || yaw < 45) {
            return BlockFace.SOUTH;
        }
        if (yaw < 135) {
            return BlockFace.WEST;
        }
        if (yaw < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    public BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    public Location anchor(
            Player player,
            double preferredDistance,
            double preferredHeight,
            int columns,
            int rows
    ) {
        BlockFace facing = cardinalFace(player);
        return anchor(player, facing, rightOf(facing), preferredDistance, preferredHeight, columns, rows);
    }

    public Location anchor(
            Player player,
            BlockFace facing,
            BlockFace right,
            double preferredDistance,
            double preferredHeight,
            int columns,
            int rows
    ) {
        Location playerLocation = player.getLocation();
        World world = player.getWorld();
        BlockFace front = facing.getOppositeFace();
        double[] distanceOffsets = {0.0D, -0.75D, -1.25D, 0.75D, 1.5D, 2.25D, 3.0D, 4.0D, 5.0D};
        double[] sideOffsets = {0.0D, 1.0D, -1.0D, 2.0D, -2.0D, 3.0D, -3.0D, 4.0D, -4.0D, 5.0D, -5.0D};
        double[] heightOffsets = {0.0D, 0.75D, 1.5D, -0.5D, 2.25D, 3.0D, 4.0D};

        for (double heightOffset : heightOffsets) {
            for (double distanceOffset : distanceOffsets) {
                double distance = Math.max(1.75D, preferredDistance + distanceOffset);
                for (double sideOffset : sideOffsets) {
                    Vector center = playerLocation.toVector()
                            .add(faceVector.apply(facing).multiply(distance))
                            .add(faceVector.apply(right).multiply(sideOffset))
                            .add(new Vector(0.0D, preferredHeight + heightOffset, 0.0D));
                    if (isAnchorClear(world, player.getEyeLocation().toVector(), center, right, front, columns, rows)) {
                        Location location = new Location(world, center.getX(), center.getY(), center.getZ());
                        location.setYaw(playerLocation.getYaw());
                        location.setPitch(0.0F);
                        return location;
                    }
                }
            }
        }

        return playerLocation.clone()
                .add(faceVector.apply(facing).multiply(preferredDistance))
                .add(0.0D, preferredHeight, 0.0D);
    }

    private boolean isAnchorClear(
            World world,
            Vector eye,
            Vector center,
            BlockFace right,
            BlockFace front,
            int columns,
            int rows
    ) {
        if (!isLineClear(world, eye, center)) {
            return false;
        }

        double minRight = -columns / 2.0D;
        double maxRight = columns / 2.0D;
        double minUp = -rows / 2.0D;
        double maxUp = rows / 2.0D;
        double sampleStep = 0.25D;
        Vector rightVector = faceVector.apply(right);

        for (double rightOffset = minRight; rightOffset <= maxRight + 1.0E-6D; rightOffset += sampleStep) {
            for (double upOffset = minUp; upOffset <= maxUp + 1.0E-6D; upOffset += sampleStep) {
                Vector planePoint = center.clone()
                        .add(rightVector.clone().multiply(rightOffset))
                        .add(new Vector(0.0D, upOffset, 0.0D));
                if (!isDepthClear(world, planePoint, front)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isLineClear(World world, Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double distance = delta.length();
        if (distance < 1.0E-6D) {
            return true;
        }
        Vector step = delta.normalize().multiply(0.45D);
        Vector cursor = from.clone().add(step.clone().multiply(2.0D));
        for (double travelled = 0.9D; travelled < distance - 0.35D; travelled += 0.45D) {
            if (!isBlockClear(world, cursor)) {
                return false;
            }
            cursor.add(step);
        }
        return true;
    }

    private boolean isDepthClear(World world, Vector point, BlockFace front) {
        Vector frontVector = faceVector.apply(front);
        double[] depthOffsets = {-0.85D, -0.45D, 0.0D, 0.45D, 0.85D};
        for (double depthOffset : depthOffsets) {
            if (!isBlockClear(world, point.clone().add(frontVector.clone().multiply(depthOffset)))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlockClear(World world, Vector point) {
        int blockY = point.getBlockY();
        if (blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) {
            return false;
        }
        Block block = world.getBlockAt(point.getBlockX(), blockY, point.getBlockZ());
        return block.isPassable();
    }

    public static Vector vectorOf(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }
}
