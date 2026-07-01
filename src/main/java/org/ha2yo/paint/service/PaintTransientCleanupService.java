package org.ha2yo.paint.service;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.Set;

public final class PaintTransientCleanupService {
    private final Set<String> transientTags;
    private final String canvasFrameTag;
    private final String exhibitDisplayTag;

    public PaintTransientCleanupService(Set<String> transientTags, String canvasFrameTag, String exhibitDisplayTag) {
        this.transientTags = transientTags;
        this.canvasFrameTag = canvasFrameTag;
        this.exhibitDisplayTag = exhibitDisplayTag;
    }

    public void clearWorldState(Iterable<World> worlds) {
        for (World world : worlds) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isPaintTransientEntity(entity) || isLegacyCanvasFrame(entity)) {
                    clearCanvasBackingBlock(entity);
                    entity.remove();
                }
            }
        }
    }

    private boolean isPaintTransientEntity(Entity entity) {
        Set<String> tags = entity.getScoreboardTags();
        for (String tag : transientTags) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLegacyCanvasFrame(Entity entity) {
        if (!(entity instanceof ItemFrame frame)
                || entity.getScoreboardTags().contains(exhibitDisplayTag)
                || !frame.isFixed()
                || frame.isVisible()
                || frame.getItem().getType() != Material.FILLED_MAP) {
            return false;
        }
        Block backingBlock = attachedBlock(frame);
        return backingBlock != null && backingBlock.getType() == Material.BLACK_CONCRETE;
    }

    private void clearCanvasBackingBlock(Entity entity) {
        if (!(entity instanceof ItemFrame frame)
                || (!entity.getScoreboardTags().contains(canvasFrameTag) && !isLegacyCanvasFrame(entity))) {
            return;
        }
        Block backingBlock = attachedBlock(frame);
        if (backingBlock != null && backingBlock.getType() == Material.BLACK_CONCRETE) {
            backingBlock.setType(Material.AIR, false);
        }
    }

    private Block attachedBlock(ItemFrame frame) {
        try {
            return frame.getLocation().getBlock().getRelative(frame.getFacing().getOppositeFace());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
