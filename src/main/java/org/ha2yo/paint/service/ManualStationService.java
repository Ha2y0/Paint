package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.station.ManualStation;
import org.ha2yo.paint.model.station.StationCanvasSlot;
import org.ha2yo.paint.model.station.StationPanelSlot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ManualStationService {
    private static final String FILE_NAME = "manual-stations.yml";
    private final Paint plugin;
    private final File file;
    private final Map<String, ManualStation> stations = new HashMap<>();

    public ManualStationService(Paint plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    public void load() {
        stations.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            stations.put(id, new ManualStation(
                    id,
                    readCanvas(section.getConfigurationSection("canvas")),
                    readPanel(section.getConfigurationSection("gallery")),
                    readPanel(section.getConfigurationSection("control")),
                    null
            ));
        }
    }

    public void save() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        for (ManualStation station : sortedStations()) {
            String path = station.id();
            writeCanvas(config.createSection(path + ".canvas"), station.canvas());
            writePanel(config.createSection(path + ".gallery"), station.gallery());
            writePanel(config.createSection(path + ".control"), station.control());
        }
        config.save(file);
    }

    public Collection<ManualStation> stations() {
        return sortedStations();
    }

    public Optional<ManualStation> station(String id) {
        return Optional.ofNullable(stations.get(normalizeId(id)));
    }

    public ManualStation putCanvas(String id, StationCanvasSlot canvas) throws IOException {
        ManualStation station = baseStation(id).withCanvas(canvas);
        stations.put(station.id(), station);
        save();
        return station;
    }

    public ManualStation putGallery(String id, StationPanelSlot gallery) throws IOException {
        ManualStation station = baseStation(id).withGallery(gallery);
        stations.put(station.id(), station);
        save();
        return station;
    }

    public ManualStation putControl(String id, StationPanelSlot control) throws IOException {
        ManualStation station = baseStation(id).withControl(control);
        stations.put(station.id(), station);
        save();
        return station;
    }

    public boolean remove(String id) throws IOException {
        ManualStation removed = stations.remove(normalizeId(id));
        if (removed == null) {
            return false;
        }
        save();
        return true;
    }

    public void occupy(String id, UUID playerId) {
        ManualStation station = stations.get(normalizeId(id));
        if (station != null) {
            stations.put(station.id(), station.withOccupant(playerId));
        }
    }

    public void release(String id) {
        ManualStation station = stations.get(normalizeId(id));
        if (station != null) {
            stations.put(station.id(), station.withOccupant(null));
        }
    }

    public Optional<ManualStation> occupiedBy(UUID playerId) {
        return stations.values().stream()
                .filter(station -> playerId.equals(station.occupantId()))
                .findFirst();
    }

    public UUID blankOwnerId(String stationId) {
        return UUID.nameUUIDFromBytes(("paint-manual-station:" + normalizeId(stationId)).getBytes(StandardCharsets.UTF_8));
    }

    public Location canvasLocation(StationCanvasSlot slot) {
        World world = Bukkit.getWorld(slot.worldId());
        return world == null ? null : new Location(world, slot.x(), slot.y(), slot.z());
    }

    public Location panelLocation(StationPanelSlot slot) {
        World world = Bukkit.getWorld(slot.worldId());
        if (world == null) {
            return null;
        }
        Location location = new Location(world, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
        return location;
    }

    private ManualStation baseStation(String id) {
        String normalized = normalizeId(id);
        ManualStation current = stations.get(normalized);
        return current == null ? new ManualStation(normalized, null, null, null, null) : current;
    }

    private ArrayList<ManualStation> sortedStations() {
        ArrayList<ManualStation> sorted = new ArrayList<>(stations.values());
        sorted.sort(Comparator.comparing(ManualStation::id));
        return sorted;
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim();
    }

    private StationCanvasSlot readCanvas(ConfigurationSection section) {
        if (section == null || !section.contains("world")) {
            return null;
        }
        return new StationCanvasSlot(
                UUID.fromString(section.getString("world")),
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                BlockFace.valueOf(section.getString("facing", BlockFace.NORTH.name())),
                BlockFace.valueOf(section.getString("right", BlockFace.EAST.name())),
                section.getInt("width", 5),
                section.getInt("height", 5)
        );
    }

    private StationPanelSlot readPanel(ConfigurationSection section) {
        if (section == null || !section.contains("world")) {
            return null;
        }
        StationPanelSlot.Layout layout = readPanelLayout(section);
        return new StationPanelSlot(
                UUID.fromString(section.getString("world")),
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"),
                BlockFace.valueOf(section.getString("facing", BlockFace.NORTH.name())),
                layout
        );
    }

    private StationPanelSlot.Layout readPanelLayout(ConfigurationSection section) {
        String value = section.getString("layout", StationPanelSlot.Layout.HORIZONTAL.name());
        try {
            return StationPanelSlot.Layout.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return StationPanelSlot.Layout.HORIZONTAL;
        }
    }

    private void writeCanvas(ConfigurationSection section, StationCanvasSlot slot) {
        if (slot == null) {
            return;
        }
        section.set("world", slot.worldId().toString());
        section.set("x", slot.x());
        section.set("y", slot.y());
        section.set("z", slot.z());
        section.set("facing", slot.facing().name());
        section.set("right", slot.right().name());
        section.set("width", slot.width());
        section.set("height", slot.height());
    }

    private void writePanel(ConfigurationSection section, StationPanelSlot slot) {
        if (slot == null) {
            return;
        }
        section.set("world", slot.worldId().toString());
        section.set("x", slot.x());
        section.set("y", slot.y());
        section.set("z", slot.z());
        section.set("yaw", slot.yaw());
        section.set("pitch", slot.pitch());
        section.set("facing", slot.facing().name());
        section.set("layout", slot.layout().name());
    }
}
