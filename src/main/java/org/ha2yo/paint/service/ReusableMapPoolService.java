package org.ha2yo.paint.service;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.renderer.GalleryImageMapRenderer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReusableMapPoolService {
    private static final String FILE_NAME = "transient-map-pool.yml";

    private final Paint plugin;
    private final int mapSize;
    private final Color backgroundColor;
    private final File file;
    private final Map<Integer, MapSlot> slots = new LinkedHashMap<>();
    private final Map<String, MapSlot> persistentSlots = new HashMap<>();

    public ReusableMapPoolService(Paint plugin, int mapSize, Color backgroundColor) {
        this.plugin = plugin;
        this.mapSize = mapSize;
        this.backgroundColor = backgroundColor;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    public synchronized MapLease acquire(World world, int count) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count");
        }
        if (count == 0) {
            return new MapLease(List.of());
        }

        List<MapSlot> acquired = new ArrayList<>(count);
        for (MapSlot slot : slots.values()) {
            if (!slot.leased && slot.persistentKey == null) {
                slot.leased = true;
                acquired.add(slot);
                if (acquired.size() >= count) {
                    return new MapLease(acquired);
                }
            }
        }
        while (acquired.size() < count) {
            MapSlot slot = createSlot(world);
            slot.leased = true;
            acquired.add(slot);
        }
        save();
        return new MapLease(acquired);
    }

    public synchronized ItemStack mapItem(MapLease lease, int index, BufferedImage image, boolean shaderRgb) {
        MapSlot slot = leasedSlot(lease, index);
        prepare(slot, new GalleryImageMapRenderer(image, 0, 0, mapSize, backgroundColor, shaderRgb));
        return mapItem(slot.mapView);
    }

    public synchronized PreparedMap prepareMap(MapLease lease, int index, MapRenderer renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("renderer");
        }
        MapSlot slot = leasedSlot(lease, index);
        prepare(slot, renderer);
        return new PreparedMap(slot.mapView, mapItem(slot.mapView));
    }

    public synchronized ItemStack persistentMapItem(World world, String key, BufferedImage image, boolean shaderRgb) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key");
        }

        MapSlot slot = persistentSlots.get(key);
        if (slot == null) {
            slot = availableSlot();
            if (slot == null) {
                slot = createSlot(world);
            }
            slot.persistentKey = key;
            persistentSlots.put(key, slot);
            save();
        }
        prepare(slot, new GalleryImageMapRenderer(image, 0, 0, mapSize, backgroundColor, shaderRgb));
        return mapItem(slot.mapView);
    }

    public synchronized void release(MapLease lease) {
        if (lease == null || lease.released) {
            return;
        }
        for (MapSlot slot : lease.slots) {
            slot.leased = false;
        }
        lease.released = true;
    }

    public synchronized void releasePersistent(String key) {
        MapSlot slot = persistentSlots.remove(key);
        if (slot == null) {
            return;
        }
        slot.persistentKey = null;
        save();
    }

    private void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (Integer mapId : config.getIntegerList("map-ids")) {
            loadSlot(mapId);
        }

        ConfigurationSection persistent = config.getConfigurationSection("persistent");
        if (persistent == null) {
            return;
        }
        for (String key : persistent.getKeys(false)) {
            int mapId = persistent.getInt(key, -1);
            MapSlot slot = slots.get(mapId);
            if (slot == null) {
                slot = loadSlot(mapId);
            }
            if (slot == null || slot.persistentKey != null) {
                continue;
            }
            slot.persistentKey = key;
            persistentSlots.put(key, slot);
        }
    }

    private MapSlot loadSlot(int mapId) {
        if (mapId < 0) {
            return null;
        }
        MapSlot existing = slots.get(mapId);
        if (existing != null) {
            return existing;
        }
        MapView mapView = plugin.getServer().getMap(mapId);
        if (mapView == null) {
            return null;
        }
        configure(mapView);
        MapSlot slot = new MapSlot(mapView);
        slots.put(mapId, slot);
        return slot;
    }

    private MapSlot createSlot(World world) {
        MapView mapView = plugin.getServer().createMap(world);
        configure(mapView);
        MapSlot slot = new MapSlot(mapView);
        slots.put(mapView.getId(), slot);
        return slot;
    }

    private MapSlot availableSlot() {
        for (MapSlot slot : slots.values()) {
            if (!slot.leased && slot.persistentKey == null) {
                return slot;
            }
        }
        return null;
    }

    private void configure(MapView mapView) {
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
    }

    private void prepare(MapSlot slot, MapRenderer renderer) {
        for (MapRenderer existingRenderer : new ArrayList<>(slot.mapView.getRenderers())) {
            slot.mapView.removeRenderer(existingRenderer);
        }
        slot.mapView.addRenderer(renderer);
    }

    private MapSlot leasedSlot(MapLease lease, int index) {
        if (lease == null || lease.released) {
            throw new IllegalStateException("Map lease has already been released");
        }
        if (index < 0 || index >= lease.slots.size()) {
            throw new IndexOutOfBoundsException(index);
        }
        MapSlot slot = lease.slots.get(index);
        if (!slot.leased || slot.persistentKey != null) {
            throw new IllegalStateException("Map slot is not leased by a transient board");
        }
        return slot;
    }

    private ItemStack mapItem(MapView mapView) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("map-ids", slots.keySet().stream().sorted(Comparator.naturalOrder()).toList());
        for (Map.Entry<String, MapSlot> entry : persistentSlots.entrySet()) {
            config.set("persistent." + entry.getKey(), entry.getValue().mapView.getId());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data directory");
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save reusable Paint map pool: " + e.getMessage());
        }
    }

    public static final class MapLease {
        private final List<MapSlot> slots;
        private boolean released;

        private MapLease(List<MapSlot> slots) {
            this.slots = List.copyOf(slots);
        }
    }

    public record PreparedMap(MapView mapView, ItemStack itemStack) {
    }

    private static final class MapSlot {
        private final MapView mapView;
        private boolean leased;
        private String persistentKey;

        private MapSlot(MapView mapView) {
            this.mapView = mapView;
        }
    }
}
