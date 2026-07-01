package org.ha2yo.paint.renderer;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SolidColorMapRenderer extends MapRenderer {
    private final Color color;
    private final int mapSize;
    private final Set<UUID> renderedPlayers = new HashSet<>();

    public SolidColorMapRenderer(Color color, int mapSize) {
        super(false);
        this.color = color;
        this.mapSize = mapSize;
    }

    @Override
    public void render(MapView map, MapCanvas mapCanvas, Player player) {
        if (!renderedPlayers.add(player.getUniqueId())) {
            return;
        }

        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                mapCanvas.setPixelColor(x, y, color);
            }
        }
    }
}
