package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.CanvasPlane;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CanvasLifecycleService {
    private final Plugin plugin;
    private final String canvasFrameTag;
    private final Map<UUID, PlayerCanvas> canvases = new HashMap<>();
    private final Map<BlockKey, PlayerCanvas> blockCanvases = new HashMap<>();
    private final Map<UUID, PlayerCanvas> frameCanvases = new HashMap<>();
    private final Set<BlockKey> generatedCanvasBlocks = new HashSet<>();

    public CanvasLifecycleService(Plugin plugin, String canvasFrameTag) {
        this.plugin = plugin;
        this.canvasFrameTag = canvasFrameTag;
    }

    public PlayerCanvas create(UUID ownerId, PixelCanvas pixelCanvas, Location origin, BlockFace facing, BlockFace right, MapItemFactory mapItemFactory) {
        World world = origin.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("origin world is null");
        }
        BlockFace front = facing.getOppositeFace();
        PlayerCanvas canvas = new PlayerCanvas(
                ownerId,
                new CanvasPlane(world.getUID(), origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), facing, right),
                pixelCanvas,
                pixelCanvas.blockWidth() * pixelCanvas.blockHeight()
        );
        canvases.put(ownerId, canvas);

        for (int y = 0; y < pixelCanvas.blockHeight(); y++) {
            for (int x = 0; x < pixelCanvas.blockWidth(); x++) {
                Block block = world.getBlockAt(origin).getRelative(right, x).getRelative(BlockFace.UP, y);
                block.setType(Material.BLACK_CONCRETE, false);
                BlockKey blockKey = BlockKey.from(block);
                canvas.blocks().add(blockKey);
                generatedCanvasBlocks.add(blockKey);
                blockCanvases.put(blockKey, canvas);

                ItemFrame frame = spawnMapFrame(world, block, front);
                canvas.frameIds().add(frame.getUniqueId());
                frameCanvases.put(frame.getUniqueId(), canvas);
                frame.setItem(mapItemFactory.create(world, canvas, x, pixelCanvas.blockHeight() - 1 - y), false);
            }
        }
        return canvas;
    }

    public PlayerCanvas remove(UUID ownerId) {
        return remove(ownerId, true);
    }

    public PlayerCanvas remove(UUID ownerId, boolean clearBlocks) {
        PlayerCanvas canvas = canvases.remove(ownerId);
        if (canvas == null) {
            return null;
        }

        for (UUID frameId : canvas.frameIds()) {
            frameCanvases.remove(frameId);
            Entity entity = Bukkit.getEntity(frameId);
            if (entity != null) {
                entity.remove();
            }
        }
        for (BlockKey blockKey : canvas.blocks()) {
            blockCanvases.remove(blockKey);
            World world = Bukkit.getServer().getWorld(blockKey.worldId());
            if (world != null && generatedCanvasBlocks.remove(blockKey)) {
                if (clearBlocks) {
                    world.getBlockAt(blockKey.x(), blockKey.y(), blockKey.z()).setType(Material.AIR, false);
                }
            }
        }
        return canvas;
    }

    public PlayerCanvas canvas(UUID ownerId) {
        return canvases.get(ownerId);
    }

    public PlayerCanvas canvasByBlock(BlockKey key) {
        return blockCanvases.get(key);
    }

    public PlayerCanvas canvasByFrame(UUID frameId) {
        return frameCanvases.get(frameId);
    }

    public boolean hasCanvas(UUID ownerId) {
        return canvases.containsKey(ownerId);
    }

    public boolean grantEditAccess(UUID ownerId, UUID editorId) {
        PlayerCanvas canvas = canvases.get(ownerId);
        return canvas != null && canvas.grantEditor(editorId);
    }

    public boolean revokeEditAccess(UUID ownerId, UUID editorId) {
        PlayerCanvas canvas = canvases.get(ownerId);
        return canvas != null && canvas.revokeEditor(editorId);
    }

    public boolean setCanvasVisibleFor(UUID ownerId, Player viewer, boolean visible) {
        PlayerCanvas canvas = canvases.get(ownerId);
        if (canvas == null || viewer == null) {
            return false;
        }

        canvas.setVisibleFor(viewer.getUniqueId(), visible);
        for (UUID frameId : canvas.frameIds()) {
            Entity entity = Bukkit.getEntity(frameId);
            if (entity == null) {
                continue;
            }
            if (visible) {
                viewer.showEntity(plugin, entity);
            } else {
                viewer.hideEntity(plugin, entity);
            }
        }
        return true;
    }

    public boolean hasBlock(BlockKey key) {
        return blockCanvases.containsKey(key);
    }

    public boolean hasFrame(UUID frameId) {
        return frameCanvases.containsKey(frameId);
    }

    public boolean isEmpty() {
        return canvases.isEmpty();
    }

    public Collection<PlayerCanvas> canvases() {
        return canvases.values();
    }

    public Set<UUID> ownerIds() {
        return Set.copyOf(canvases.keySet());
    }

    private ItemFrame spawnMapFrame(World world, Block backingBlock, BlockFace front) {
        Location location = backingBlock.getRelative(front).getLocation().add(0.5D, 0.5D, 0.5D);
        ItemFrame frame = world.spawn(location, ItemFrame.class);
        frame.setFacingDirection(front, true);
        frame.setFixed(true);
        frame.setVisible(false);
        frame.addScoreboardTag(canvasFrameTag);
        return frame;
    }

    @FunctionalInterface
    public interface MapItemFactory {
        ItemStack create(World world, PlayerCanvas canvas, int tileX, int tileY);
    }
}
