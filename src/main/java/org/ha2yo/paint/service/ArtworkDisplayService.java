package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.CanvasPlane;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PaintExhibit;
import org.ha2yo.paint.renderer.GalleryImageMapRenderer;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class ArtworkDisplayService {
    private static final String DISPLAY_TAG = "paint_exhibit_display";
    private static final Material DEFAULT_FRAME_MATERIAL = Material.DARK_OAK_PLANKS;
    private static final double FRAME_THICKNESS = 0.20D;
    private static final double FRAME_TILE_LENGTH = 0.25D;
    private static final double FRAME_INNER_OVERLAP = 0.010D;
    private static final double FRAME_DEPTH = 0.06D;
    private static final double FRAME_OFFSET = 0.06D;

    private final Paint plugin;
    private final int mapSize;
    private final Color backgroundColor;
    private final BooleanSupplier shaderRgbEnabled;
    private final Map<UUID, PaintExhibit> exhibits = new HashMap<>();
    private final Map<UUID, List<UUID>> entityIdsByExhibit = new HashMap<>();
    private final Set<UUID> displayEntityIds = new HashSet<>();
    private final Set<BlockKey> protectedBlocks = new HashSet<>();
    private File exhibitsFile;

    public ArtworkDisplayService(
            Paint plugin,
            int mapSize,
            Color backgroundColor,
            BooleanSupplier shaderRgbEnabled
    ) {
        this.plugin = plugin;
        this.mapSize = mapSize;
        this.backgroundColor = backgroundColor;
        this.shaderRgbEnabled = shaderRgbEnabled;
    }

    public void load() {
        exhibits.clear();
        exhibitsFile = new File(plugin.getDataFolder(), "exhibits.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(exhibitsFile);
        ConfigurationSection section = config.getConfigurationSection("exhibits");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "exhibits." + key;
                BlockFace facing = parseFace(config.getString(path + ".facing", "NORTH"));
                PaintExhibit exhibit = new PaintExhibit(
                        id,
                        UUID.fromString(config.getString(path + ".artwork-id", "")),
                        config.getString(path + ".image", ""),
                        config.getString(path + ".world", ""),
                        config.getInt(path + ".x"),
                        config.getInt(path + ".y"),
                        config.getInt(path + ".z"),
                        facing,
                        parseFace(config.getString(path + ".right", rightOf(facing).name())),
                        parseFace(config.getString(path + ".up", BlockFace.UP.name())),
                        Math.max(1, config.getInt(path + ".width", 1)),
                        Math.max(1, config.getInt(path + ".height", 1)),
                        ExhibitFrameStyle.fromName(config.getString(path + ".frame-style", ExhibitFrameStyle.NONE.name())),
                        config.getString(path + ".frame-material", "")
                );
                exhibits.put(id, exhibit);
            } catch (IllegalArgumentException ignored) {
            }
        }
        reloadDisplays(true);
    }

    public PaintExhibit place(PaintArtwork artwork, File imageFile, Placement placement) throws IOException {
        BufferedImage source = ImageIO.read(imageFile);
        if (source == null) {
            throw new IOException("Could not decode " + imageFile.getPath());
        }

        PaintExhibit exhibit = new PaintExhibit(
                UUID.randomUUID(),
                artwork.id(),
                artwork.imagePath(),
                placement.world().getName(),
                placement.origin().getX(),
                placement.origin().getY(),
                placement.origin().getZ(),
                placement.facing(),
                placement.right(),
                placement.up(),
                Math.max(1, placement.width()),
                Math.max(1, placement.height()),
                placement.frameStyle(),
                placement.frameMaterial()
        );
        exhibits.put(exhibit.id(), exhibit);
        save();
        spawn(exhibit, source, true);
        return exhibit;
    }

    public void reloadDisplays(boolean logWarnings) {
        clearDisplays();
        for (PaintExhibit exhibit : new ArrayList<>(exhibits.values())) {
            File imageFile = imageFile(exhibit.imagePath());
            if (!imageFile.isFile()) {
                if (logWarnings) {
                    plugin.getLogger().warning("Exhibit image not found: " + imageFile.getPath());
                }
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(imageFile);
                if (image == null) {
                    if (logWarnings) {
                        plugin.getLogger().warning("Exhibit image could not be decoded: " + imageFile.getPath());
                    }
                    continue;
                }
                spawn(exhibit, image, false);
            } catch (IOException e) {
                if (logWarnings) {
                    plugin.getLogger().warning("Could not load exhibit image: " + e.getMessage());
                }
            }
        }
    }

    public void clearDisplays() {
        for (List<UUID> ids : entityIdsByExhibit.values()) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        entityIdsByExhibit.clear();
        displayEntityIds.clear();
        protectedBlocks.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getScoreboardTags().contains(DISPLAY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    public boolean isDisplayEntity(UUID entityId) {
        return displayEntityIds.contains(entityId);
    }

    public boolean isProtectedBlock(BlockKey blockKey) {
        return protectedBlocks.contains(blockKey);
    }

    public Optional<PaintExhibit> findByEntity(UUID entityId) {
        UUID exhibitId = exhibitIdByEntity(entityId);
        return exhibitId == null ? Optional.empty() : Optional.ofNullable(exhibits.get(exhibitId));
    }

    public List<PaintExhibit> exhibits() {
        return new ArrayList<>(exhibits.values());
    }

    public Optional<ExhibitFrameStyle> cycleFrameStyleByEntity(UUID entityId, Material frameMaterial) throws IOException {
        UUID exhibitId = exhibitIdByEntity(entityId);
        if (exhibitId == null) {
            return Optional.empty();
        }
        PaintExhibit exhibit = exhibits.get(exhibitId);
        if (exhibit == null) {
            return Optional.empty();
        }
        Material material = validFrameMaterial(frameMaterial);
        ExhibitFrameStyle currentStyle = exhibit.frameStyle() == null ? ExhibitFrameStyle.NONE : exhibit.frameStyle();
        boolean sameFrameMaterial = currentStyle == ExhibitFrameStyle.FRAME && material.name().equalsIgnoreCase(exhibit.frameMaterial());
        ExhibitFrameStyle nextStyle = sameFrameMaterial ? ExhibitFrameStyle.NONE : ExhibitFrameStyle.FRAME;
        PaintExhibit updated = new PaintExhibit(
                exhibit.id(),
                exhibit.artworkId(),
                exhibit.imagePath(),
                exhibit.worldName(),
                exhibit.x(),
                exhibit.y(),
                exhibit.z(),
                exhibit.facing(),
                exhibit.right(),
                exhibit.up(),
                exhibit.width(),
                exhibit.height(),
                nextStyle,
                nextStyle == ExhibitFrameStyle.NONE ? "" : material.name()
        );
        File imageFile = imageFile(updated.imagePath());
        BufferedImage source = ImageIO.read(imageFile);
        if (source == null) {
            throw new IOException("Could not decode " + imageFile.getPath());
        }
        exhibits.put(updated.id(), updated);
        save();
        spawn(updated, source, true);
        return Optional.of(nextStyle);
    }

    public boolean removeByEntity(UUID entityId) throws IOException {
        UUID exhibitId = exhibitIdByEntity(entityId);
        return exhibitId != null && removeById(exhibitId);
    }

    public boolean removeById(UUID exhibitId) throws IOException {
        PaintExhibit exhibit = exhibits.remove(exhibitId);
        if (exhibit == null) {
            return false;
        }
        clearExhibit(exhibitId);
        removeProtectedBlocks(exhibit);
        save();
        return true;
    }

    private UUID exhibitIdByEntity(UUID entityId) {
        for (Map.Entry<UUID, List<UUID>> entry : entityIdsByExhibit.entrySet()) {
            if (entry.getValue().contains(entityId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void spawn(PaintExhibit exhibit, BufferedImage source, boolean replaceExisting) {
        if (replaceExisting) {
            clearExhibit(exhibit.id());
        }
        World world = plugin.getServer().getWorld(exhibit.worldName());
        if (world == null) {
            return;
        }

        BufferedImage resized = resizedImage(source, exhibit.width() * mapSize, exhibit.height() * mapSize);
        boolean shaderRgb = shaderRgbEnabled.getAsBoolean();
        BufferedImage image = displayImage(resized, shaderRgb);
        Block origin = world.getBlockAt(exhibit.x(), exhibit.y(), exhibit.z());
        BlockFace front = exhibit.facing().getOppositeFace();
        List<UUID> entityIds = new ArrayList<>();
        for (int y = 0; y < exhibit.height(); y++) {
            for (int x = 0; x < exhibit.width(); x++) {
                Block block = origin.getRelative(exhibit.right(), x).getRelative(exhibit.up(), y);
                protectedBlocks.add(BlockKey.from(block));

                ItemFrame frame = spawnMapFrame(world, block, front);
                frame.addScoreboardTag(DISPLAY_TAG);
                entityIds.add(frame.getUniqueId());
                displayEntityIds.add(frame.getUniqueId());
                int tileY = isHorizontal(front) ? y : exhibit.height() - 1 - y;
                frame.setItem(createMapItem(world, image, x, tileY, front, exhibit.right(), exhibit.up(), shaderRgb), false);
            }
        }
        spawnFrameBorder(world, origin, exhibit, front, entityIds);
        entityIdsByExhibit.put(exhibit.id(), entityIds);
    }

    private BufferedImage displayImage(BufferedImage resized, boolean shaderRgb) {
        if (shaderRgb) {
            return resized;
        }
        return GalleryImageMapRenderer.oklabToVanillaMapColors(resized, backgroundColor);
    }

    private Set<BlockKey> protectedBlocksOf(PaintExhibit exhibit) {
        Set<BlockKey> keys = new HashSet<>();
        World world = plugin.getServer().getWorld(exhibit.worldName());
        if (world == null) {
            return keys;
        }
        Block origin = world.getBlockAt(exhibit.x(), exhibit.y(), exhibit.z());
        for (int y = 0; y < exhibit.height(); y++) {
            for (int x = 0; x < exhibit.width(); x++) {
                keys.add(BlockKey.from(origin.getRelative(exhibit.right(), x).getRelative(exhibit.up(), y)));
            }
        }
        return keys;
    }

    private void removeProtectedBlocks(PaintExhibit exhibit) {
        protectedBlocks.removeAll(protectedBlocksOf(exhibit));
    }

    private void clearExhibit(UUID exhibitId) {
        List<UUID> ids = entityIdsByExhibit.remove(exhibitId);
        if (ids == null) {
            return;
        }
        PaintExhibit exhibit = exhibits.get(exhibitId);
        if (exhibit != null) {
            removeProtectedBlocks(exhibit);
        }
        for (UUID id : ids) {
            displayEntityIds.remove(id);
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private ItemFrame spawnMapFrame(World world, Block backingBlock, BlockFace front) {
        Location location = backingBlock.getRelative(front).getLocation().add(0.5D, 0.5D, 0.5D);
        removeExistingItemFrames(world, location);
        ItemFrame frame = world.spawn(location, ItemFrame.class);
        frame.setFacingDirection(front, true);
        frame.setFixed(true);
        frame.setVisible(false);
        return frame;
    }

    private void removeExistingItemFrames(World world, Location location) {
        for (Entity entity : world.getNearbyEntities(location, 0.25D, 0.25D, 0.25D)) {
            if (entity instanceof ItemFrame) {
                entity.remove();
            }
        }
    }

    private ItemStack createMapItem(
            World world,
            BufferedImage image,
            int tileX,
            int tileY,
            BlockFace front,
            BlockFace right,
            BlockFace up,
            boolean shaderRgb
    ) {
        MapView mapView = plugin.getServer().createMap(world);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        if (isHorizontal(front)) {
            mapView.addRenderer(new GalleryImageMapRenderer(image, tileX, tileY, mapSize, backgroundColor, shaderRgb, front, right, up));
        } else {
            mapView.addRenderer(new GalleryImageMapRenderer(image, tileX, tileY, mapSize, backgroundColor, shaderRgb));
        }

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isHorizontal(BlockFace front) {
        return front == BlockFace.UP || front == BlockFace.DOWN;
    }

    private BufferedImage resizedImage(BufferedImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private void save() throws IOException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Could not create " + plugin.getDataFolder().getPath());
        }
        if (exhibitsFile == null) {
            exhibitsFile = new File(plugin.getDataFolder(), "exhibits.yml");
        }
        YamlConfiguration config = new YamlConfiguration();
        for (PaintExhibit exhibit : exhibits.values()) {
            String path = "exhibits." + exhibit.id();
            config.set(path + ".artwork-id", exhibit.artworkId().toString());
            config.set(path + ".image", exhibit.imagePath());
            config.set(path + ".world", exhibit.worldName());
            config.set(path + ".x", exhibit.x());
            config.set(path + ".y", exhibit.y());
            config.set(path + ".z", exhibit.z());
            config.set(path + ".facing", exhibit.facing().name());
            config.set(path + ".right", exhibit.right().name());
            config.set(path + ".up", exhibit.up().name());
            config.set(path + ".width", exhibit.width());
            config.set(path + ".height", exhibit.height());
            config.set(path + ".frame-style", exhibit.frameStyle().name());
            config.set(path + ".frame-material", exhibit.frameMaterial());
        }
        config.save(exhibitsFile);
    }

    private File imageFile(String imagePath) {
        File imageFile = new File(imagePath);
        if (imageFile.isAbsolute()) {
            return imageFile;
        }

        File resolved = new File(plugin.getDataFolder(), imagePath);
        if (resolved.isFile()) {
            return resolved;
        }

        File fallback = fallbackImageFile(imageFile.getName(), resolved.getParentFile());
        return fallback != null ? fallback : resolved;
    }

    private File fallbackImageFile(String fileName, File recordedParent) {
        File imagesDirectory = new File(plugin.getDataFolder(), "images");
        if (recordedParent == null || !imagesDirectory.isDirectory()) {
            return null;
        }

        String directoryName = recordedParent.getName();
        String uuidDirectoryName = uuidDirectoryName(directoryName);
        if (!uuidDirectoryName.equals(directoryName)) {
            File uuidFile = new File(new File(imagesDirectory, uuidDirectoryName), fileName);
            if (uuidFile.isFile()) {
                return uuidFile;
            }
        }

        File[] matchingDirectories = imagesDirectory.listFiles(file ->
                file.isDirectory() && file.getName().endsWith("-" + directoryName)
        );
        if (matchingDirectories != null) {
            for (File directory : matchingDirectories) {
                File candidate = new File(directory, fileName);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static String uuidDirectoryName(String directoryName) {
        int separatorIndex = directoryName.indexOf('-');
        if (separatorIndex < 0 || separatorIndex + 1 >= directoryName.length()) {
            return directoryName;
        }
        String candidate = directoryName.substring(separatorIndex + 1);
        try {
            UUID.fromString(candidate);
            return candidate;
        } catch (IllegalArgumentException ignored) {
            return directoryName;
        }
    }

    private static BlockFace rightOf(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case EAST -> BlockFace.SOUTH;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private static BlockFace parseFace(String value) {
        try {
            return BlockFace.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return BlockFace.NORTH;
        }
    }

    private void spawnFrameBorder(World world, Block origin, PaintExhibit exhibit, BlockFace front, List<UUID> entityIds) {
        ExhibitFrameStyle style = exhibit.frameStyle();
        if (style == null || style == ExhibitFrameStyle.NONE) {
            return;
        }
        Material frameMaterial = frameMaterial(exhibit);
        double fullWidth = exhibit.width() + FRAME_THICKNESS * 2.0D;
        double overlappedThickness = FRAME_THICKNESS + FRAME_INNER_OVERLAP;

        spawnFrameStrip(world, entityIds, frameMaterial, exhibit, -FRAME_THICKNESS, exhibit.height() - FRAME_INNER_OVERLAP, fullWidth, overlappedThickness, FRAME_DEPTH, FRAME_OFFSET);
        spawnFrameStrip(world, entityIds, frameMaterial, exhibit, -FRAME_THICKNESS, -FRAME_THICKNESS, fullWidth, overlappedThickness, FRAME_DEPTH, FRAME_OFFSET);
        spawnFrameStrip(world, entityIds, frameMaterial, exhibit, -FRAME_THICKNESS, 0.0D, overlappedThickness, exhibit.height(), FRAME_DEPTH, FRAME_OFFSET);
        spawnFrameStrip(world, entityIds, frameMaterial, exhibit, exhibit.width() - FRAME_INNER_OVERLAP, 0.0D, overlappedThickness, exhibit.height(), FRAME_DEPTH, FRAME_OFFSET);
    }

    private Material frameMaterial(PaintExhibit exhibit) {
        String configured = exhibit.frameMaterial();
        String normalized = configured == null || configured.isBlank() ? DEFAULT_FRAME_MATERIAL.name() : configured.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return validFrameMaterial(material);
    }

    private Material validFrameMaterial(Material material) {
        return material == null || !material.isBlock() ? DEFAULT_FRAME_MATERIAL : material;
    }

    private void spawnFrameStrip(
            World world,
            List<UUID> entityIds,
            Material material,
            PaintExhibit exhibit,
            double leftOffset,
            double bottomOffset,
            double width,
            double height,
            double depth,
            double frontOffset
    ) {
        if (width >= height) {
            double remaining = width;
            double cursor = leftOffset;
            while (remaining > 0.0001D) {
                double pieceWidth = Math.min(FRAME_TILE_LENGTH, remaining);
                spawnFramePiece(world, entityIds, material, exhibit, cursor, bottomOffset, pieceWidth, height, depth, frontOffset);
                cursor += pieceWidth;
                remaining -= pieceWidth;
            }
            return;
        }

        double remaining = height;
        double cursor = bottomOffset;
        while (remaining > 0.0001D) {
            double pieceHeight = Math.min(FRAME_TILE_LENGTH, remaining);
            spawnFramePiece(world, entityIds, material, exhibit, leftOffset, cursor, width, pieceHeight, depth, frontOffset);
            cursor += pieceHeight;
            remaining -= pieceHeight;
        }
    }

    private void spawnFramePiece(
            World world,
            List<UUID> entityIds,
            Material material,
            PaintExhibit exhibit,
            double leftOffset,
            double bottomOffset,
            double width,
            double height,
            double depth,
            double frontOffset
    ) {
        BlockFace front = exhibit.facing().getOppositeFace();
        Vector facePoint = new CanvasPlane(
                world.getUID(),
                exhibit.x(),
                exhibit.y(),
                exhibit.z(),
                exhibit.facing(),
                exhibit.right()
        ).facePoint();
        Vector locationVector = facePoint
                .add(vectorOf(exhibit.right()).multiply(leftOffset + width / 2.0D))
                .add(vectorOf(exhibit.up()).multiply(bottomOffset + height / 2.0D))
                .add(vectorOf(front).multiply(frontOffset));
        Location location = new Location(world, locationVector.getX(), locationVector.getY(), locationVector.getZ());
        location.setYaw(yawFor(front));
        location.setPitch(pitchFor(front));

        BlockDisplay display = world.spawn(location, BlockDisplay.class);
        display.addScoreboardTag(DISPLAY_TAG);
        display.setBlock(material.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(12, 12));
        display.setViewRange(64.0F);
        display.setInterpolationDuration(0);
        display.setTransformation(new Transformation(
                new Vector3f((float) (-width / 2.0D), (float) (-height / 2.0D), (float) (-depth / 2.0D)),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f((float) width, (float) height, (float) depth),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
        ));
        entityIds.add(display.getUniqueId());
        displayEntityIds.add(display.getUniqueId());
    }

    private Vector vectorOf(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    private static float yawFor(BlockFace front) {
        return switch (front) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    private static float pitchFor(BlockFace front) {
        return switch (front) {
            case UP -> -90.0F;
            case DOWN -> 90.0F;
            default -> 0.0F;
        };
    }

    public record Placement(
            World world,
            Block origin,
            BlockFace facing,
            BlockFace right,
            BlockFace up,
            int width,
            int height,
            ExhibitFrameStyle frameStyle,
            String frameMaterial
    ) {
        public Placement(World world, Block origin, BlockFace facing, BlockFace right, BlockFace up, int width, int height, ExhibitFrameStyle frameStyle) {
            this(world, origin, facing, right, up, width, height, frameStyle, "");
        }
    }
}
