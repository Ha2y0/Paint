package org.ha2yo.paint.service;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.CanvasMapTile;
import org.ha2yo.paint.model.CanvasPlane;
import org.ha2yo.paint.model.PaletteBoard;
import org.ha2yo.paint.model.session.ArtworkPlacementCandidate;
import org.ha2yo.paint.model.tool.PaletteMode;
import org.ha2yo.paint.renderer.PaletteMapRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.ha2yo.paint.renderer.PaletteMapRenderer.PALETTE_BLOCK_HEIGHT;
import static org.ha2yo.paint.renderer.PaletteMapRenderer.PALETTE_BLOCK_WIDTH;
import static org.ha2yo.paint.renderer.PaletteMapRenderer.PALETTE_PIXEL_HEIGHT;
import static org.ha2yo.paint.renderer.PaletteMapRenderer.PALETTE_PIXEL_WIDTH;

public final class PaletteBoardService {
    private static final double CURSOR_PLACE_DISTANCE = 5.0D;

    private final Paint plugin;
    private final String frameTag;
    private final BooleanSupplier shaderRgbSupplier;
    private final Predicate<BlockKey> blockedBlockPredicate;
    private final Map<UUID, PaletteBoard> boards = new HashMap<>();
    private final Map<BlockKey, PaletteBoard> blockBoards = new HashMap<>();
    private final Map<UUID, PaletteBoard> frameBoards = new HashMap<>();

    public PaletteBoardService(
            Paint plugin,
            String frameTag,
            BooleanSupplier shaderRgbSupplier,
            Predicate<BlockKey> blockedBlockPredicate
    ) {
        this.plugin = plugin;
        this.frameTag = frameTag;
        this.shaderRgbSupplier = shaderRgbSupplier;
        this.blockedBlockPredicate = blockedBlockPredicate;
    }

    public boolean open(Player player, Color selectedColor, int brushRadius, PaletteMode mode, boolean hasCanvas) {
        UUID playerId = player.getUniqueId();
        if (boards.containsKey(playerId)) {
            return true;
        }
        if (!hasCanvas) {
            player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
            return false;
        }

        ArtworkPlacementCandidate candidate = placementCandidate(player);
        if (candidate == null || !candidate.valid()) {
            player.sendMessage(ChatColor.RED + "팔레트 보드를 놓을 공간을 찾지 못했습니다.");
            return false;
        }

        return openAt(player, selectedColor, brushRadius, mode, hasCanvas, candidate);
    }

    public boolean openAt(
            Player player,
            Color selectedColor,
            int brushRadius,
            PaletteMode mode,
            boolean hasCanvas,
            ArtworkPlacementCandidate candidate
    ) {
        UUID playerId = player.getUniqueId();
        if (boards.containsKey(playerId)) {
            return true;
        }
        if (!hasCanvas) {
            player.sendMessage(ChatColor.RED + "먼저 캔버스에 그림을 그려주세요.");
            return false;
        }
        if (candidate == null || !candidate.valid()) {
            player.sendMessage(ChatColor.RED + "팔레트 보드를 놓을 공간을 찾지 못했습니다.");
            return false;
        }

        remove(playerId);
        PalettePlacement placement = placementFromCandidate(candidate);
        PaletteBoard board = new PaletteBoard(
                playerId,
                new CanvasPlane(placement.world().getUID(), placement.origin().getX(), placement.origin().getY(), placement.origin().getZ(), placement.facing(), placement.right()),
                selectedColor,
                brushRadius
        );
        board.setMode(mode);
        PaletteMapRenderer.updateGradientCursorForColor(board, selectedColor);
        boards.put(playerId, board);

        for (int y = 0; y < PALETTE_BLOCK_HEIGHT; y++) {
            for (int x = 0; x < PALETTE_BLOCK_WIDTH; x++) {
                Block block = placement.origin().getRelative(placement.right(), x).getRelative(BlockFace.UP, y);
                Block frameSpace = block.getRelative(placement.front());
                if (!placement.wallBacked()) {
                    rememberAndClearTransientBlock(board, block);
                }
                rememberAndClearTransientBlock(board, frameSpace);

                BlockKey blockKey = BlockKey.from(block);
                BlockKey frameSpaceKey = BlockKey.from(frameSpace);
                if (!placement.wallBacked()) {
                    board.blocks().add(blockKey);
                    blockBoards.put(blockKey, board);
                }
                board.blocks().add(frameSpaceKey);
                blockBoards.put(frameSpaceKey, board);

                ItemFrame frame = spawnFloatingMapFrame(placement.world(), block, placement.front());
                frame.addScoreboardTag(frameTag);
                board.frameIds().add(frame.getUniqueId());
                frameBoards.put(frame.getUniqueId(), board);
                frame.setItem(createMapItem(placement.world(), board, x, PALETTE_BLOCK_HEIGHT - 1 - y), false);
                makeEntityVisibleOnlyToOwner(frame, playerId);
            }
        }

        sendMaps(board);
        player.sendMessage(ChatColor.GREEN + "팔레트 보드를 열었습니다. 색상과 브러시 크기를 선택할 수 있습니다.");
        return true;
    }

    public ArtworkPlacementCandidate placementCandidate(Player player) {
        PalettePlacement placement = resolvePlacement(player);
        if (placement == null) {
            return null;
        }
        return new ArtworkPlacementCandidate(
                placement.world(),
                placement.origin(),
                placement.facing(),
                placement.right(),
                BlockFace.UP,
                placement.valid(),
                placement.wallBacked()
        );
    }

    public boolean remove(UUID ownerId) {
        PaletteBoard board = boards.remove(ownerId);
        if (board == null) {
            return false;
        }

        for (UUID frameId : board.frameIds()) {
            frameBoards.remove(frameId);
            for (World world : plugin.getServer().getWorlds()) {
                Entity entity = world.getEntity(frameId);
                if (entity != null) {
                    entity.remove();
                    break;
                }
            }
        }

        for (BlockKey key : board.blocks()) {
            blockBoards.remove(key);
            World world = plugin.getServer().getWorld(key.worldId());
            if (world != null) {
                world.getBlockAt(key.x(), key.y(), key.z()).setType(Material.AIR, false);
            }
        }
        restoreTransientBlocks(board);
        return true;
    }

    public boolean hasBoard(UUID ownerId) {
        return boards.containsKey(ownerId);
    }

    public void clearAll() {
        for (UUID ownerId : new ArrayList<>(boards.keySet())) {
            remove(ownerId);
        }
    }

    public PaletteBoard boardByBlock(BlockKey blockKey) {
        return blockBoards.get(blockKey);
    }

    public PaletteBoard boardByFrame(UUID frameId) {
        return frameBoards.get(frameId);
    }

    public boolean updateSelectedColor(UUID ownerId, Color color) {
        PaletteBoard board = boards.get(ownerId);
        if (board == null || color == null) {
            return false;
        }

        board.setSelectedColor(color);
        PaletteMapRenderer.updateGradientCursorForColor(board, color);
        board.incrementVersion();
        sendMaps(board);
        return true;
    }

    public boolean isFrame(UUID frameId) {
        return frameBoards.containsKey(frameId);
    }

    public boolean isBlockOccupied(BlockKey blockKey) {
        return blockBoards.containsKey(blockKey);
    }

    public void syncVisibility(Player viewer) {
        for (PaletteBoard board : boards.values()) {
            boolean owner = board.isOwner(viewer);
            for (UUID frameId : board.frameIds()) {
                Entity entity = Bukkit.getEntity(frameId);
                if (entity == null) {
                    continue;
                }
                if (owner) {
                    viewer.showEntity(plugin, entity);
                } else {
                    viewer.hideEntity(plugin, entity);
                }
            }
        }
    }

    public PaletteLook looked(Player player) {
        PaletteLook closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (PaletteBoard board : boards.values()) {
            if (!board.isOwner(player)) {
                continue;
            }
            PaletteLook look = looked(player, board);
            if (look == null || look.distance() >= closestDistance) {
                continue;
            }
            closest = look;
            closestDistance = look.distance();
        }
        return closest;
    }

    public PaletteLook looked(Player player, PaletteBoard board) {
        if (!board.isOwner(player)) {
            return null;
        }
        CanvasPlane plane = board.plane();
        if (!plane.worldId().equals(player.getWorld().getUID())) {
            return null;
        }

        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector normal = vectorOf(plane.facing());
        Vector planePoint = plane.facePoint();

        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 0.000001D) {
            return null;
        }

        double distance = planePoint.clone().subtract(eye).dot(normal) / denominator;
        if (distance < 0.0D || distance > 10.0D) {
            return null;
        }

        Vector hit = eye.clone().add(direction.multiply(distance));
        Vector fromOrigin = hit.clone().subtract(planePoint);
        double u = fromOrigin.dot(vectorOf(plane.right()));
        double v = fromOrigin.getY();

        if (u < 0.0D || u >= PALETTE_BLOCK_WIDTH || v < 0.0D || v >= PALETTE_BLOCK_HEIGHT) {
            return null;
        }

        return createLook(board, u, v, distance);
    }

    public void sendMaps(PaletteBoard board) {
        Player player = plugin.getServer().getPlayer(board.ownerId());
        if (player == null || !board.plane().worldId().equals(player.getWorld().getUID())) {
            return;
        }

        for (CanvasMapTile tile : board.mapTiles()) {
            player.sendMap(tile.mapView());
        }
    }

    private PalettePlacement resolvePlacement(Player player) {
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                CURSOR_PLACE_DISTANCE,
                FluidCollisionMode.NEVER,
                true
        );
        if (hit != null && hit.getHitBlock() != null && isHorizontal(hit.getHitBlockFace())) {
            BlockFace front = hit.getHitBlockFace();
            BlockFace facing = front.getOppositeFace();
            BlockFace right = rightOf(facing);
            Block origin = wallOrigin(hit.getHitBlock(), right, hit.getHitPosition());
            return new PalettePlacement(player.getWorld(), origin, facing, right, front, true, canPlaceAt(origin, right, front, false));
        }

        BlockFace facing = cardinalFace(player);
        BlockFace right = rightOf(facing);
        BlockFace front = facing.getOppositeFace();
        Block origin = floatingOrigin(player, facing, right);
        return new PalettePlacement(player.getWorld(), origin, facing, right, front, false, canPlaceAt(origin, right, front, true));
    }

    private PalettePlacement placementFromCandidate(ArtworkPlacementCandidate candidate) {
        BlockFace front = candidate.facing().getOppositeFace();
        return new PalettePlacement(
                candidate.world(),
                candidate.origin(),
                candidate.facing(),
                candidate.right(),
                front,
                candidate.snappedToSurface(),
                candidate.valid()
        );
    }

    private Block wallOrigin(Block hitBlock, BlockFace right, Vector hitPosition) {
        int bottomY = (int) Math.floor(hitPosition.getY() - PALETTE_BLOCK_HEIGHT / 2.0D);
        Block origin = hitBlock.getWorld().getBlockAt(hitBlock.getX(), bottomY, hitBlock.getZ());
        return origin.getRelative(right, -(PALETTE_BLOCK_WIDTH / 2));
    }

    private Block floatingOrigin(Player player, BlockFace facing, BlockFace right) {
        Location eye = player.getEyeLocation();
        Vector center = eye.toVector().add(eye.getDirection().normalize().multiply(CURSOR_PLACE_DISTANCE));
        int bottomY = (int) Math.floor(center.getY() - PALETTE_BLOCK_HEIGHT / 2.0D);
        Block centerBlock = player.getWorld().getBlockAt(
                (int) Math.floor(center.getX()),
                bottomY,
                (int) Math.floor(center.getZ())
        );
        return centerBlock.getRelative(facing, -1).getRelative(right, -(PALETTE_BLOCK_WIDTH / 2));
    }

    private boolean canPlaceAt(Block origin, BlockFace right, BlockFace front, boolean requireBackingReplaceable) {
        for (int y = 0; y < PALETTE_BLOCK_HEIGHT; y++) {
            for (int x = 0; x < PALETTE_BLOCK_WIDTH; x++) {
                Block backingBlock = origin.getRelative(right, x).getRelative(BlockFace.UP, y);
                Block frameSpace = backingBlock.getRelative(front);
                if ((requireBackingReplaceable && !isReplaceable(backingBlock)) || !isReplaceable(frameSpace)) {
                    return false;
                }

                BlockKey backingKey = BlockKey.from(backingBlock);
                BlockKey frameKey = BlockKey.from(frameSpace);
                if ((requireBackingReplaceable && blockedBlockPredicate.test(backingKey))
                        || blockedBlockPredicate.test(frameKey)
                        || (requireBackingReplaceable && blockBoards.containsKey(backingKey))
                        || blockBoards.containsKey(frameKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isReplaceable(Block block) {
        return block.getType().isAir()
                || block.getType() == Material.LIGHT
                || block.getType() == Material.SHORT_GRASS
                || block.getType() == Material.TALL_GRASS
                || block.getType() == Material.SNOW;
    }

    private ItemFrame spawnFloatingMapFrame(World world, Block planeBlock, BlockFace front) {
        Location location = planeBlock.getRelative(front).getLocation().add(0.5D, 0.5D, 0.5D);
        ItemFrame frame = world.spawn(location, ItemFrame.class);
        frame.setFacingDirection(front, true);
        frame.setFixed(true);
        frame.setVisible(false);
        return frame;
    }

    private void makeEntityVisibleOnlyToOwner(Entity entity, UUID ownerId) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(ownerId)) {
                viewer.showEntity(plugin, entity);
            } else {
                viewer.hideEntity(plugin, entity);
            }
        }
    }

    private ItemStack createMapItem(World world, PaletteBoard board, int tileX, int tileY) {
        MapView mapView = plugin.getServer().createMap(world);
        board.mapTiles().add(new CanvasMapTile(mapView, tileX, tileY));
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new PaletteMapRenderer(board, tileX, tileY, shaderRgbSupplier));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private PaletteLook createLook(PaletteBoard board, double u, double v, double distance) {
        double clampedU = Math.max(0.0D, Math.min(PALETTE_BLOCK_WIDTH - 0.000001D, u));
        double clampedV = Math.max(0.0D, Math.min(PALETTE_BLOCK_HEIGHT - 0.000001D, v));
        int x = Math.min(PALETTE_PIXEL_WIDTH - 1, (int) Math.floor(clampedU / PALETTE_BLOCK_WIDTH * PALETTE_PIXEL_WIDTH));
        int y = Math.min(PALETTE_PIXEL_HEIGHT - 1, (int) Math.floor((PALETTE_BLOCK_HEIGHT - clampedV) / PALETTE_BLOCK_HEIGHT * PALETTE_PIXEL_HEIGHT));
        return new PaletteLook(board, x, y, distance);
    }

    private void rememberAndClearTransientBlock(PaletteBoard board, Block block) {
        if (block.getType() == Material.LIGHT) {
            return;
        }
        if (block.getType() == Material.SHORT_GRASS
                || block.getType() == Material.TALL_GRASS
                || block.getType() == Material.SNOW) {
            board.replacedLightBlocks().add(block.getState());
            block.setType(Material.AIR, false);
        }
    }

    private void restoreTransientBlocks(PaletteBoard board) {
        board.replacedLightBlocks().forEach(state -> state.update(true, false));
        board.replacedLightBlocks().clear();
    }

    private BlockFace cardinalFace(Player player) {
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

    private BlockFace rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            case EAST -> BlockFace.SOUTH;
            default -> BlockFace.EAST;
        };
    }

    private BlockFace leftOf(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.WEST;
        };
    }

    private boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private Vector vectorOf(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    public record PaletteLook(PaletteBoard board, int x, int y, double distance) {
    }

    private record PalettePlacement(World world, Block origin, BlockFace facing, BlockFace right, BlockFace front, boolean wallBacked, boolean valid) {
    }
}
