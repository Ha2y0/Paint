package org.ha2yo.paint.event;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.ha2yo.paint.Paint;

public final class PaintPlayerEventController implements Listener {
    private final Paint plugin;
    private final Consumer<Player> layerVisibilitySyncer;
    private final Consumer<Player> paletteBoardVisibilitySyncer;
    private final Consumer<Player> canvasPlacementVisibilitySyncer;
    private final Consumer<Player> artworkPlacementVisibilitySyncer;
    private final Predicate<UUID> canvasOwnerChecker;
    private final Consumer<Player> toolGiver;
    private final Consumer<Player> strayToolCleaner;
    private final Consumer<PlayerQuitEvent> quitHandler;
    public PaintPlayerEventController(
            Paint plugin,
            Consumer<Player> layerVisibilitySyncer,
            Consumer<Player> paletteBoardVisibilitySyncer,
            Consumer<Player> canvasPlacementVisibilitySyncer,
            Consumer<Player> artworkPlacementVisibilitySyncer,
            Predicate<UUID> canvasOwnerChecker,
            Consumer<Player> toolGiver,
            Consumer<Player> strayToolCleaner,
            Consumer<PlayerQuitEvent> quitHandler
    ) {
        this.plugin = plugin;
        this.layerVisibilitySyncer = layerVisibilitySyncer;
        this.paletteBoardVisibilitySyncer = paletteBoardVisibilitySyncer;
        this.canvasPlacementVisibilitySyncer = canvasPlacementVisibilitySyncer;
        this.artworkPlacementVisibilitySyncer = artworkPlacementVisibilitySyncer;
        this.canvasOwnerChecker = canvasOwnerChecker;
        this.toolGiver = toolGiver;
        this.strayToolCleaner = strayToolCleaner;
        this.quitHandler = quitHandler;
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        layerVisibilitySyncer.accept(player);
        paletteBoardVisibilitySyncer.accept(player);
        canvasPlacementVisibilitySyncer.accept(player);
        artworkPlacementVisibilitySyncer.accept(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (canvasOwnerChecker.test(player.getUniqueId())) {
                toolGiver.accept(player);
                return;
            }
            strayToolCleaner.accept(player);
        });
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        quitHandler.accept(event);
    }
}
