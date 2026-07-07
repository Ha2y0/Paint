package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.session.ArtworkPlacementCandidate;
import org.ha2yo.paint.model.session.PlacementAxes;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class PlacementPreviewService {
    private final Paint plugin;
    private final String previewTag;
    private final Predicate<BlockKey> blockedBlock;

    public PlacementPreviewService(Paint plugin, String previewTag, Predicate<BlockKey> blockedBlock) {
        this.plugin = plugin;
        this.previewTag = previewTag;
        this.blockedBlock = blockedBlock;
    }

    public ArtworkPlacementCandidate artworkCandidate(Player player, int width, int height, int preferredDistance) {
        BlockFace playerFacing = cardinalFace(player);
        RayTraceResult rayTrace = player.rayTraceBlocks(preferredDistance, FluidCollisionMode.NEVER);
        if (rayTrace == null || rayTrace.getHitBlock() == null || rayTrace.getHitBlockFace() == null) {
            return airArtworkCandidate(player, playerFacing, width, height, preferredDistance);
        }

        BlockFace front = rayTrace.getHitBlockFace();
        if (!isPlacementFace(front)) {
            return airArtworkCandidate(player, playerFacing, width, height, preferredDistance);
        }
        Block targetBlock = backingTarget(rayTrace.getHitBlock(), front);
        PlacementAxes axes = placementAxes(front, playerFacing);
        Block origin = offsetBlock(offsetBlock(targetBlock, axes.right(), -(width / 2)), axes.up(), -(height / 2));
        BlockFace facing = front.getOppositeFace();
        boolean valid = canPlaceArtwork(origin, front, axes.right(), axes.up(), width, height);
        return new ArtworkPlacementCandidate(player.getWorld(), origin, facing, axes.right(), axes.up(), valid, true);
    }

    public ArtworkPlacementCandidate canvasCandidate(Player player, int width, int height, int preferredDistance) {
        BlockFace playerFacing = cardinalFace(player);
        RayTraceResult rayTrace = player.rayTraceBlocks(preferredDistance, FluidCollisionMode.NEVER);
        if (rayTrace != null && rayTrace.getHitBlock() != null && rayTrace.getHitBlockFace() != null) {
            BlockFace front = rayTrace.getHitBlockFace();
            if (isPlacementFace(front) && front.getModY() == 0) {
                BlockFace facing = front.getOppositeFace();
                BlockFace right = rightOf(facing);
                Block centerBlock = rayTrace.getHitBlock();
                Block origin = offsetBlock(offsetBlock(centerBlock, right, -(width / 2)), BlockFace.UP, -(height / 2));
                boolean valid = canPlaceCanvasOnWall(origin, front, right, width, height);
                return new ArtworkPlacementCandidate(player.getWorld(), origin, facing, right, BlockFace.UP, valid, true);
            }
        }
        return airCanvasCandidate(player, playerFacing, width, height, preferredDistance);
    }

    private ArtworkPlacementCandidate airCanvasCandidate(Player player, BlockFace playerFacing, int width, int height, int preferredDistance) {
        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = vectorOf(playerFacing);
        } else {
            direction = direction.normalize();
        }

        Vector center = player.getEyeLocation().toVector().add(direction.multiply(preferredDistance));
        Block centerBlock = player.getWorld().getBlockAt(center.getBlockX(), center.getBlockY(), center.getBlockZ());
        BlockFace right = rightOf(playerFacing);
        Block origin = offsetBlock(offsetBlock(centerBlock, right, -(width / 2)), BlockFace.UP, -(height / 2));
        boolean valid = canPlaceCanvasInAir(origin, playerFacing, right, width, height);
        return new ArtworkPlacementCandidate(player.getWorld(), origin, playerFacing, right, BlockFace.UP, valid, false);
    }

    public List<UUID> spawnDisplays(UUID ownerId, ArtworkPlacementCandidate candidate, int width, int height) {
        Material material = candidate.valid() ? Material.BLUE_CONCRETE : Material.RED_CONCRETE;
        List<UUID> displayIds = new ArrayList<>();
        BlockFace front = candidate.facing().getOppositeFace();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Block backing = candidate.origin().getRelative(candidate.right(), x).getRelative(candidate.up(), y);
                Block displayBlock = previewDisplayBlock(backing, front, candidate.snappedToSurface());
                spawnOutline(ownerId, displayIds, displayBlock, material, candidate.right(), candidate.up(), front, candidate.snappedToSurface());
            }
        }
        return displayIds;
    }

    public void removeDisplays(List<UUID> displayIds) {
        if (displayIds == null) {
            return;
        }
        for (UUID displayId : displayIds) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void clearTaggedDisplays() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getScoreboardTags().contains(previewTag)) {
                    entity.remove();
                }
            }
        }
    }

    private ArtworkPlacementCandidate airArtworkCandidate(Player player, BlockFace playerFacing, int width, int height, int preferredDistance) {
        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = vectorOf(playerFacing);
        } else {
            direction = direction.normalize();
        }

        Vector center = player.getEyeLocation().toVector().add(direction.multiply(preferredDistance));
        Block centerBlock = player.getWorld().getBlockAt(center.getBlockX(), center.getBlockY(), center.getBlockZ());
        PlacementAxes axes = new PlacementAxes(rightOf(playerFacing), BlockFace.UP);
        Block origin = offsetBlock(offsetBlock(centerBlock, axes.right(), -(width / 2)), axes.up(), -(height / 2));
        return new ArtworkPlacementCandidate(player.getWorld(), origin, playerFacing, axes.right(), axes.up(), false, false);
    }

    private Block backingTarget(Block hitBlock, BlockFace front) {
        if (hitBlock.getType() == Material.LIGHT) {
            return hitBlock.getRelative(front.getOppositeFace());
        }
        return hitBlock;
    }

    private boolean isPlacementFace(BlockFace face) {
        return face == BlockFace.NORTH
                || face == BlockFace.SOUTH
                || face == BlockFace.EAST
                || face == BlockFace.WEST
                || face == BlockFace.UP
                || face == BlockFace.DOWN;
    }

    private PlacementAxes placementAxes(BlockFace front, BlockFace playerFacing) {
        if (front == BlockFace.UP) {
            return new PlacementAxes(rightOf(playerFacing), playerFacing);
        }
        if (front == BlockFace.DOWN) {
            return new PlacementAxes(rightOf(playerFacing), playerFacing.getOppositeFace());
        }
        BlockFace facing = front.getOppositeFace();
        return new PlacementAxes(rightOf(facing), BlockFace.UP);
    }

    private boolean canPlaceArtwork(Block origin, BlockFace front, BlockFace right, BlockFace up, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Block backing = origin.getRelative(right, x).getRelative(up, y);
                if (!backing.getType().isSolid()) {
                    return false;
                }
                if (blockedBlock.test(BlockKey.from(backing))) {
                    return false;
                }
                Block displaySpace = backing.getRelative(front);
                if (!isDisplaySpaceClear(displaySpace)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canPlaceCanvasInAir(Block origin, BlockFace facing, BlockFace right, int width, int height) {
        BlockFace front = facing.getOppositeFace();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Block backing = origin.getRelative(right, x).getRelative(BlockFace.UP, y);
                if (!isDisplaySpaceClear(backing)) {
                    return false;
                }
                if (blockedBlock.test(BlockKey.from(backing))) {
                    return false;
                }
                Block displaySpace = backing.getRelative(front);
                if (!isDisplaySpaceClear(displaySpace)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canPlaceCanvasOnWall(Block origin, BlockFace front, BlockFace right, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Block backing = origin.getRelative(right, x).getRelative(BlockFace.UP, y);
                if (blockedBlock.test(BlockKey.from(backing))) {
                    return false;
                }
                Block displaySpace = backing.getRelative(front);
                if (!isDisplaySpaceClear(displaySpace)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isDisplaySpaceClear(Block block) {
        return block.getType().isAir() || block.getType() == Material.LIGHT;
    }

    private Block previewDisplayBlock(Block backing, BlockFace front, boolean snappedToSurface) {
        if (!snappedToSurface || !backing.getType().isSolid()) {
            return backing;
        }
        return backing.getRelative(front);
    }

    private void spawnOutline(
            UUID ownerId,
            List<UUID> displayIds,
            Block displayBlock,
            Material material,
            BlockFace right,
            BlockFace up,
            BlockFace front,
            boolean snappedToSurface
    ) {
        double edge = 0.48D;
        double thickness = 0.045D;
        double depth = 0.035D;
        double surfaceOffset = snappedToSurface ? -0.48D : 0.0D;
        spawnLine(ownerId, displayIds, displayBlock, material, right, 0.0D, up, edge, front, surfaceOffset, 1.0D, thickness, depth);
        spawnLine(ownerId, displayIds, displayBlock, material, right, 0.0D, up, -edge, front, surfaceOffset, 1.0D, thickness, depth);
        spawnLine(ownerId, displayIds, displayBlock, material, right, edge, up, 0.0D, front, surfaceOffset, thickness, 1.0D, depth);
        spawnLine(ownerId, displayIds, displayBlock, material, right, -edge, up, 0.0D, front, surfaceOffset, thickness, 1.0D, depth);
    }

    private void spawnLine(
            UUID ownerId,
            List<UUID> displayIds,
            Block displayBlock,
            Material material,
            BlockFace right,
            double rightOffset,
            BlockFace up,
            double upOffset,
            BlockFace front,
            double frontOffset,
            double rightSize,
            double upSize,
            double frontSize
    ) {
        Location location = displayBlock.getLocation().add(0.5D, 0.5D, 0.5D);
        Vector offset = vectorOf(right).multiply(rightOffset)
                .add(vectorOf(up).multiply(upOffset))
                .add(vectorOf(front).multiply(frontOffset));
        Vector size = axisSize(right, rightSize).add(axisSize(up, upSize)).add(axisSize(front, frontSize));
        Vector translation = offset.clone().subtract(size.clone().multiply(0.5D));

        BlockDisplay display = displayBlock.getWorld().spawn(location, BlockDisplay.class);
        display.addScoreboardTag(previewTag);
        display.setBlock(material.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(8, 8));
        display.setViewRange(16.0F);
        display.setInterpolationDuration(0);
        display.setTransformation(new Transformation(
                new Vector3f((float) translation.getX(), (float) translation.getY(), (float) translation.getZ()),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f((float) size.getX(), (float) size.getY(), (float) size.getZ()),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
        ));
        showOnlyToOwner(display, ownerId);
        displayIds.add(display.getUniqueId());
    }

    private void showOnlyToOwner(Entity entity, UUID ownerId) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(ownerId)) {
                viewer.showEntity(plugin, entity);
            } else {
                viewer.hideEntity(plugin, entity);
            }
        }
    }

    private Vector axisSize(BlockFace face, double size) {
        return new Vector(
                Math.abs(face.getModX()) * size,
                Math.abs(face.getModY()) * size,
                Math.abs(face.getModZ()) * size
        );
    }

    private Block offsetBlock(Block block, BlockFace face, int distance) {
        if (distance == 0) {
            return block;
        }
        BlockFace direction = distance > 0 ? face : face.getOppositeFace();
        Block result = block;
        for (int i = 0; i < Math.abs(distance); i++) {
            result = result.getRelative(direction);
        }
        return result;
    }

    private BlockFace cardinalFace(Player player) {
        float yaw = player.getLocation().getYaw();
        float normalized = (yaw % 360 + 360) % 360;
        if (normalized >= 45 && normalized < 135) {
            return BlockFace.WEST;
        }
        if (normalized >= 135 && normalized < 225) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225 && normalized < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private Vector vectorOf(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }
}
