package org.ha2yo.paint;

import org.bukkit.plugin.java.JavaPlugin;
import org.ha2yo.paint.api.PaintService;

public final class Paint extends JavaPlugin {
    private PaintApplication application;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        application = new PaintApplication(this);
        application.enable();
    }

    @Override
    public void onDisable() {
        if (application == null) {
            return;
        }

        application.disable();
        application = null;
    }

    public PaintService paintService() {
        return application == null ? null : application.paintService();
    }
}