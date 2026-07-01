package org.ha2yo.paint.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.ha2yo.paint.api.PaintService;
import org.ha2yo.paint.service.PaintMenuService;
import org.ha2yo.paint.service.PaintTransientCleanupService;
import org.ha2yo.paint.service.ToolItemService;
import org.ha2yo.paint.workflow.PaintControllerFeatureService;

public final class CoreRuntime {
    public PaintService paintService;
    public NamespacedKey paletteColorKey;
    public NamespacedKey brushSizeDeltaKey;
    public NamespacedKey brushSizeValueKey;
    public NamespacedKey toolKey;
    public NamespacedKey menuActionKey;
    public NamespacedKey artworkIdKey;
    public NamespacedKey previewActionKey;
    public ToolItemService toolItems;
    public PaintMenuService paintMenus;
    public PaintTransientCleanupService transientCleanup;
    public PaintControllerFeatureService featureService;
    public BukkitTask paintTask;
}