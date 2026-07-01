package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import org.joml.Vector3f;
import org.joml.AxisAngle4f;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.BlockKey;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.renderer.GalleryImageMapRenderer;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class ArtworkPreviewService {
    public static final int ARTWORKS_PER_PAGE = 24;

    private static final String PREVIEW_TAG = "paint_artwork_preview";
    private static final int BOARD_WIDTH = 6;
    private static final int BOARD_HEIGHT = 6;
    private static final int SEARCH_ROW = 0;
    private static final int ARTWORK_START_ROW = 1;
    private static final int ACTION_ROW = 5;
    private static final double TILE_STEP = 1.0D;
    private static final double TILE_HIT_HALF = 0.52D;
    private static final Color PREVIEW_BUTTON_COLOR = new Color(84, 108, 132);
    private static final Color DISABLED_BUTTON_COLOR = new Color(74, 82, 90);
    private static final Color DELETE_BUTTON_COLOR = new Color(132, 78, 60);
    private static final Color DELETE_CONFIRM_BUTTON_COLOR = new Color(172, 66, 72);

    private final Paint plugin;
    private final int mapSize;
    private final Color backgroundColor;
    private final BooleanSupplier shaderRgbEnabled;
    private final Map<UUID, PreviewBoard> previews = new HashMap<>();
    private final Map<UUID, PreviewClick> clicks = new HashMap<>();
    private final Map<String, BufferedImage> iconCache = new HashMap<>();
    private final Set<UUID> displayEntityIds = new HashSet<>();
    private final Map<UUID, TooltipState> tooltips = new HashMap<>();

    public ArtworkPreviewService(Paint plugin, int mapSize, Color backgroundColor, BooleanSupplier shaderRgbEnabled) {
        this.plugin = plugin;
        this.mapSize = mapSize;
        this.backgroundColor = backgroundColor;
        this.shaderRgbEnabled = shaderRgbEnabled;
    }

    public void showGallery(
            Player player,
            Location centerLocation,
            BlockFace facing,
            List<PaintArtwork> artworks,
            int page,
            int selectedIndex,
            boolean deleteArmed,
            String searchQuery,
            boolean manualStationGallery,
            Function<PaintArtwork, File> imageFile
    ) throws IOException {
        UUID playerId = player.getUniqueId();
        clear(playerId);

        int pageTotal = pageCount(artworks);
        int safePage = Math.max(0, Math.min(page, pageTotal - 1));
        BlockFace front = facing.getOppositeFace();
        BlockFace right = rightOf(facing);
        World world = centerLocation.getWorld();
        if (world == null) {
            world = player.getWorld();
        }
        Vector center = centerLocation.toVector();

        List<UUID> entityIds = new ArrayList<>();
        PreviewClick[][] gridClicks = new PreviewClick[BOARD_HEIGHT][BOARD_WIDTH];
        try {
            String searchText = searchBarText(searchQuery);
            PreviewClick searchClick = new PreviewClick(PreviewClickAction.SEARCH, -1);
            for (int col = 0; col < BOARD_WIDTH; col++) {
                ItemFrame frame = spawnFloatingMapFrame(world, center, right, front, col, SEARCH_ROW);
                frame.setItem(createMapItem(world, searchBarTile(searchText, col, BOARD_WIDTH)), false);
                gridClicks[SEARCH_ROW][col] = searchClick;
                register(entityIds, frame, searchClick);
            }

            int pageStart = safePage * ARTWORKS_PER_PAGE;
            for (int row = ARTWORK_START_ROW; row < ACTION_ROW; row++) {
                for (int col = 0; col < BOARD_WIDTH; col++) {
                    int slot = (row - ARTWORK_START_ROW) * BOARD_WIDTH + col;
                    int artworkIndex = pageStart + slot;
                    BufferedImage image = blankTile();
                    if (artworkIndex >= 0 && artworkIndex < artworks.size()) {
                        try {
                            image = thumbnail(imageFile.apply(artworks.get(artworkIndex)), artworkIndex == selectedIndex);
                        } catch (IOException e) {
                            plugin.getLogger().warning("Could not load Paint artwork preview tile: " + e.getMessage());
                        }
                    }
                    ItemFrame frame = spawnFloatingMapFrame(world, center, right, front, col, row);
                    frame.setItem(createMapItem(world, image), false);
                    if (artworkIndex >= 0 && artworkIndex < artworks.size()) {
                        PreviewClick click = new PreviewClick(PreviewClickAction.ARTWORK, artworkIndex, artworks.get(artworkIndex).displayName());
                        gridClicks[row][col] = click;
                        register(entityIds, frame, click);
                    } else {
                        registerDecoration(entityIds, frame);
                    }
                }
            }

            PreviewButton[] actionButtons = manualStationGallery
                    ? new PreviewButton[]{
                    new PreviewButton("이전 페이지", null, PreviewClickAction.PREVIOUS, PREVIEW_BUTTON_COLOR),
                    new PreviewButton("다음 페이지", null, PreviewClickAction.NEXT, PREVIEW_BUTTON_COLOR),
                    new PreviewButton("", (safePage + 1) + "/" + pageTotal, PreviewClickAction.CLOSE, DISABLED_BUTTON_COLOR, false, true),
                    new PreviewButton("", null, PreviewClickAction.CLOSE, DISABLED_BUTTON_COLOR, false),
                    new PreviewButton("편집", null, PreviewClickAction.EDIT, PREVIEW_BUTTON_COLOR),
                    new PreviewButton(deleteArmed ? "삭제 확인" : "삭제", null, PreviewClickAction.DELETE, deleteArmed ? DELETE_CONFIRM_BUTTON_COLOR : DELETE_BUTTON_COLOR)
            }
                    : new PreviewButton[]{
                    new PreviewButton("이전 페이지", null, PreviewClickAction.PREVIOUS, PREVIEW_BUTTON_COLOR),
                    new PreviewButton("다음 페이지", null, PreviewClickAction.NEXT, PREVIEW_BUTTON_COLOR),
                    new PreviewButton("닫기", (safePage + 1) + "/" + pageTotal, PreviewClickAction.CLOSE, PREVIEW_BUTTON_COLOR, true, true),
                    new PreviewButton("편집", null, PreviewClickAction.EDIT, PREVIEW_BUTTON_COLOR),
                    new PreviewButton("전시", null, PreviewClickAction.EXHIBIT, PREVIEW_BUTTON_COLOR),
                    new PreviewButton(deleteArmed ? "삭제 확인" : "삭제", null, PreviewClickAction.DELETE, deleteArmed ? DELETE_CONFIRM_BUTTON_COLOR : DELETE_BUTTON_COLOR)
            };
            for (int col = 0; col < actionButtons.length; col++) {
                ItemFrame frame = spawnFloatingMapFrame(world, center, right, front, col, ACTION_ROW);
                PreviewButton button = actionButtons[col];
                frame.setItem(createMapItem(world, buttonTile(button)), false);
                PreviewClick click = new PreviewClick(button.action(), -1, "");
                gridClicks[ACTION_ROW][col] = click;
                register(entityIds, frame, click);
            }

            previews.put(playerId, new PreviewBoard(entityIds, world.getUID(), center, front, right, gridClicks));
        } catch (RuntimeException e) {
            removeEntities(entityIds);
            throw e;
        }
    }
    public static int pageCount(List<PaintArtwork> artworks) {
        return Math.max(1, (int) Math.ceil(artworks.size() / (double) ARTWORKS_PER_PAGE));
    }

    public static int firstArtworkIndexOnPage(List<PaintArtwork> artworks, int page) {
        if (artworks.isEmpty()) {
            return 0;
        }
        int safePage = Math.max(0, Math.min(page, pageCount(artworks) - 1));
        return Math.min(safePage * ARTWORKS_PER_PAGE, artworks.size() - 1);
    }

    public PreviewClick click(UUID entityId) {
        return clicks.get(entityId);
    }

    public void updateTooltip(Player player, double maxDistance) {
        UUID playerId = player.getUniqueId();
        HoverTarget target = lookedTarget(player, maxDistance);
        if (target == null || target.click().tooltip().isBlank()) {
            clearTooltip(playerId);
            return;
        }

        Entity targetEntity = Bukkit.getEntity(target.entityId());
        if (targetEntity == null) {
            clearTooltip(playerId);
            return;
        }

        Location location = tooltipLocation(targetEntity);
        TooltipState state = tooltips.get(playerId);
        TextDisplay display = state == null ? null : textDisplay(state.entityId());
        if (display == null || !state.text().equals(target.click().tooltip())) {
            clearTooltip(playerId);
            display = spawnTooltip(player, location, target.click().tooltip());
            tooltips.put(playerId, new TooltipState(display.getUniqueId(), target.click().tooltip()));
        } else {
            display.teleport(location);
        }
    }

    private HoverTarget lookedTarget(Player player, double maxDistance) {
        PreviewBoard board = previews.get(player.getUniqueId());
        if (board == null || !player.getWorld().getUID().equals(board.worldId())) {
            return null;
        }
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxDistance,
                0.08D,
                entity -> clicks.containsKey(entity.getUniqueId())
        );
        Entity entity = hit == null ? null : hit.getHitEntity();
        if (entity == null) {
            return null;
        }
        PreviewClick click = clicks.get(entity.getUniqueId());
        return click == null ? null : new HoverTarget(entity.getUniqueId(), click);
    }
    public PreviewClick lookedClick(Player player, double maxDistance) {
        PreviewBoard board = previews.get(player.getUniqueId());
        if (board == null || !player.getWorld().getUID().equals(board.worldId())) {
            return null;
        }

        PreviewClick entityClick = lookedEntityClick(player, maxDistance);
        if (entityClick != null) {
            return entityClick;
        }

        Vector origin = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector normal = vectorOf(board.front());
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 1.0E-6D) {
            return null;
        }

        double distance = board.center().clone().subtract(origin).dot(normal) / denominator;
        if (distance < 0.0D || distance > maxDistance) {
            return null;
        }

        Vector hit = origin.add(direction.multiply(distance));
        Vector relative = hit.subtract(board.center());
        double rightOffset = relative.dot(vectorOf(board.right()));
        double upOffset = relative.getY();
        int col = nearestPreviewColumn(rightOffset);
        int row = nearestPreviewRow(upOffset);
        if (row < 0 || row >= BOARD_HEIGHT || col < 0 || col >= BOARD_WIDTH
                || Math.abs(rightOffset - previewRightOffset(col)) > TILE_HIT_HALF
                || Math.abs(upOffset - previewUpOffset(row)) > TILE_HIT_HALF) {
            return null;
        }
        return board.gridClicks()[row][col];
    }

    private PreviewClick lookedEntityClick(Player player, double maxDistance) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxDistance,
                0.08D,
                entity -> clicks.containsKey(entity.getUniqueId())
        );
        Entity entity = hit == null ? null : hit.getHitEntity();
        return entity == null ? null : clicks.get(entity.getUniqueId());
    }
    public void clear(UUID playerId) {
        clearTooltip(playerId);
        PreviewBoard board = previews.remove(playerId);
        if (board == null) {
            return;
        }
        removeEntities(board.entityIds());
    }

    private void removeEntities(List<UUID> entityIds) {
        for (UUID entityId : entityIds) {
            clicks.remove(entityId);
            displayEntityIds.remove(entityId);
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void clearAll() {
        for (UUID playerId : new ArrayList<>(previews.keySet())) {
            clear(playerId);
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getScoreboardTags().contains(PREVIEW_TAG)) {
                    clicks.remove(entity.getUniqueId());
                    displayEntityIds.remove(entity.getUniqueId());
                    entity.remove();
                }
            }
        }
        clicks.clear();
        displayEntityIds.clear();
        tooltips.clear();
    }

    public boolean isDisplayEntity(UUID entityId) {
        return displayEntityIds.contains(entityId);
    }

    public boolean isProtectedBlock(BlockKey blockKey) {
        return false;
    }

    private void register(List<UUID> entityIds, ItemFrame frame, PreviewClick click) {
        registerDecoration(entityIds, frame);
        clicks.put(frame.getUniqueId(), click);
    }

    private void registerDecoration(List<UUID> entityIds, ItemFrame frame) {
        frame.addScoreboardTag(PREVIEW_TAG);
        entityIds.add(frame.getUniqueId());
        displayEntityIds.add(frame.getUniqueId());
    }

    private Location tooltipLocation(Entity targetEntity) {
        Location location = targetEntity.getLocation().clone().add(0.0D, -0.62D, 0.0D);
        if (targetEntity instanceof ItemFrame frame) {
            location.add(vectorOf(frame.getFacing()).multiply(0.05D));
            Location frameLocation = frame.getLocation();
            location.setYaw(frameLocation.getYaw());
            location.setPitch(frameLocation.getPitch());
        }
        return location;
    }

    private TextDisplay spawnTooltip(Player owner, Location location, String text) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setPersistent(false);
        display.setText(text);
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(16.0F);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(190, 34, 42, 48));
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f(0.9F, 0.9F, 1.0F),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
        ));
        display.addScoreboardTag(PREVIEW_TAG);
        displayEntityIds.add(display.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(owner.getUniqueId())) {
                viewer.hideEntity(plugin, display);
            }
        }
        return display;
    }

    private TextDisplay textDisplay(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        return entity instanceof TextDisplay display ? display : null;
    }

    private void clearTooltip(UUID playerId) {
        TooltipState state = tooltips.remove(playerId);
        if (state == null) {
            return;
        }
        displayEntityIds.remove(state.entityId());
        Entity entity = Bukkit.getEntity(state.entityId());
        if (entity != null) {
            entity.remove();
        }
    }
    private ItemFrame spawnFloatingMapFrame(World world, Vector center, BlockFace right, BlockFace front, int col, int row) {
        double leftOffset = previewRightOffset(col);
        double upOffset = previewUpOffset(row);
        Vector locationVector = center.clone()
                .add(vectorOf(right).multiply(leftOffset))
                .add(new Vector(0.0D, upOffset, 0.0D));
        Location location = new Location(world, locationVector.getX(), locationVector.getY(), locationVector.getZ());
        location.setYaw(yawFor(front));
        location.setPitch(0.0F);

        ItemFrame frame = world.spawn(location, ItemFrame.class);
        frame.setFacingDirection(front, true);
        frame.setFixed(true);
        frame.setVisible(false);
        return frame;
    }
    private static int nearestPreviewColumn(double rightOffset) {
        return (int) Math.round((rightOffset + previewRightSpan() / 2.0D) / TILE_STEP);
    }

    private static int nearestPreviewRow(double upOffset) {
        return (int) Math.round((previewUpSpan() / 2.0D - upOffset) / TILE_STEP);
    }

    private static double previewRightOffset(int col) {
        return col * TILE_STEP - previewRightSpan() / 2.0D;
    }

    private static double previewUpOffset(int row) {
        return previewUpSpan() / 2.0D - row * TILE_STEP;
    }

    private static double previewRightSpan() {
        return (BOARD_WIDTH - 1) * TILE_STEP;
    }

    private static double previewUpSpan() {
        return (BOARD_HEIGHT - 1) * TILE_STEP;
    }
    private ItemStack createMapItem(World world, BufferedImage image) {
        MapView mapView = plugin.getServer().createMap(world);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new GalleryImageMapRenderer(image, 0, 0, mapSize, backgroundColor, shaderRgbEnabled.getAsBoolean()));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private BufferedImage thumbnail(File file, boolean selected) throws IOException {
        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IOException("Could not decode " + file.getPath());
        }
        BufferedImage image = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(84, 112, 132));
        graphics.fillRect(0, 0, mapSize, mapSize);
        graphics.setColor(new Color(210, 222, 226));
        graphics.fillRect(0, 0, mapSize, 5);
        graphics.fillRect(0, 0, 5, mapSize);
        graphics.setColor(new Color(34, 49, 55));
        graphics.fillRect(0, mapSize - 7, mapSize, 7);
        graphics.fillRect(mapSize - 7, 0, 7, mapSize);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double scale = Math.min(mapSize / (double) source.getWidth(), mapSize / (double) source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (mapSize - width) / 2;
        int y = (mapSize - height) / 2;
        graphics.drawImage(source, x, y, width, height, null);
        if (selected) {
            graphics.setColor(new Color(255, 215, 60));
            graphics.setStroke(new BasicStroke(8.0F));
            graphics.drawRect(4, 4, mapSize - 9, mapSize - 9);
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage blankTile() {
        BufferedImage image = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(72, 92, 106));
        graphics.fillRect(0, 0, mapSize, mapSize);
        graphics.setColor(new Color(180, 196, 202));
        graphics.fillRect(0, 0, mapSize, 4);
        graphics.fillRect(0, 0, 4, mapSize);
        graphics.setColor(new Color(34, 46, 52));
        graphics.fillRect(0, mapSize - 6, mapSize, 6);
        graphics.fillRect(mapSize - 6, 0, 6, mapSize);
        graphics.dispose();
        return image;
    }

    private String searchBarText(String searchQuery) {
        String normalized = searchQuery == null ? "" : searchQuery.trim();
        return normalized;
    }

    private BufferedImage searchBarTile(String text, int segment, int segments) {
        int fullWidth = mapSize * segments;
        BufferedImage full = new BufferedImage(fullWidth, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = full.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(68, 88, 104));
        graphics.fillRect(0, 0, fullWidth, mapSize);
        graphics.setColor(new Color(100, 121, 137));
        graphics.fillRect(0, 0, fullWidth, 4);
        graphics.fillRect(0, 0, 4, mapSize);
        graphics.setColor(new Color(39, 48, 57));
        graphics.fillRect(0, mapSize - 6, fullWidth, 6);
        graphics.fillRect(fullWidth - 6, 0, 6, mapSize);

        int barX = 16;
        int barY = 42;
        int barWidth = fullWidth - 32;
        int barHeight = 44;
        int labelWidth = 108;
        int inputWidth = barWidth - labelWidth;
        int labelX = barX + inputWidth;
        graphics.setColor(new Color(248, 248, 242));
        graphics.fillRect(barX, barY, inputWidth, barHeight);
        graphics.setColor(new Color(84, 108, 132));
        graphics.fillRect(labelX, barY, labelWidth, barHeight);
        graphics.setColor(new Color(28, 38, 46));
        graphics.drawRect(barX, barY, barWidth - 1, barHeight - 1);
        graphics.drawLine(labelX, barY, labelX, barY + barHeight - 1);

        String value = text == null ? "" : text;
        if (!value.isBlank()) {
            Font font = fitSingleLineFont(graphics, value, Font.BOLD, 30, 16, inputWidth - 18);
            FontMetrics metrics = graphics.getFontMetrics(font);
            int baseline = barY + (barHeight - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.setFont(font);
            graphics.setColor(new Color(28, 38, 46));
            graphics.drawString(value, barX + 9, baseline);
        }

        String label = "검색";
        Font labelFont = fitSingleLineFont(graphics, label, Font.BOLD, 30, 18, labelWidth - 18);
        FontMetrics labelMetrics = graphics.getFontMetrics(labelFont);
        int labelBaseline = barY + (barHeight - labelMetrics.getHeight()) / 2 + labelMetrics.getAscent();
        int labelTextX = labelX + (labelWidth - labelMetrics.stringWidth(label)) / 2;
        graphics.setFont(labelFont);
        graphics.setColor(new Color(248, 248, 242));
        graphics.drawString(label, labelTextX, labelBaseline);
        graphics.dispose();

        BufferedImage image = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D segmentGraphics = image.createGraphics();
        int sourceX = Math.max(0, Math.min(segments - 1, segment)) * mapSize;
        segmentGraphics.drawImage(full, 0, 0, mapSize, mapSize, sourceX, 0, sourceX + mapSize, mapSize, null);
        segmentGraphics.dispose();
        return image;
    }

    private Font fitSingleLineFont(Graphics2D graphics, String text, int style, int maxSize, int minSize, int maxWidth) {
        for (int size = maxSize; size >= minSize; size--) {
            Font font = new Font("Dialog", style, size);
            if (graphics.getFontMetrics(font).stringWidth(text) <= maxWidth) {
                return font;
            }
        }
        return new Font("Dialog", style, minSize);
    }
    private BufferedImage controlSpacerTile() {
        BufferedImage image = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(68, 88, 104));
        graphics.fillRect(0, 0, mapSize, mapSize);
        graphics.setColor(new Color(100, 121, 137));
        graphics.fillRect(0, 0, mapSize, 4);
        graphics.fillRect(0, 0, 4, mapSize);
        graphics.setColor(new Color(39, 48, 57));
        graphics.fillRect(0, mapSize - 6, mapSize, 6);
        graphics.fillRect(mapSize - 6, 0, 6, mapSize);
        graphics.dispose();
        return image;
    }
    private BufferedImage buttonTile(PreviewButton button) {
        BufferedImage image = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(button.color());
        graphics.fillRect(0, 0, mapSize, mapSize);
        graphics.setColor(new Color(226, 235, 239));
        graphics.fillRect(0, 0, mapSize, 5);
        graphics.fillRect(0, 0, 5, mapSize);
        graphics.setColor(new Color(39, 48, 57));
        graphics.fillRect(0, mapSize - 7, mapSize, 7);
        graphics.fillRect(mapSize - 7, 0, 7, mapSize);
        graphics.setColor(new Color(210, 223, 229));
        graphics.drawRect(10, 10, mapSize - 21, mapSize - 21);

        if (button.enabled() || button.showSubLabelWhenDisabled()) {
            drawButtonLabel(graphics, button.label(), button.subLabel());
        }
        graphics.dispose();
        return image;
    }

    private void drawButtonLabel(Graphics2D graphics, String label, String subLabel) {
        if ((label == null || label.isBlank()) && subLabel != null && !subLabel.isBlank()) {
            Font pageFont = fitSingleLineFont(graphics, subLabel, Font.BOLD, 36, 22, mapSize - 26);
            FontMetrics pageMetrics = graphics.getFontMetrics(pageFont);
            int baseline = (mapSize - pageMetrics.getHeight()) / 2 + pageMetrics.getAscent();
            drawCenteredText(graphics, subLabel, baseline + 2, pageFont, new Color(42, 51, 60));
            drawCenteredText(graphics, subLabel, baseline, pageFont, new Color(248, 244, 232));
            return;
        }
        String[] lines = label.split(" ", 2);
        int maxTextWidth = mapSize - 22;
        int maxLabelHeight = subLabel == null ? mapSize - 36 : mapSize - 58;
        Font labelFont = fitButtonFont(graphics, lines, Font.BOLD, 34, 20, maxTextWidth, maxLabelHeight);
        FontMetrics labelMetrics = graphics.getFontMetrics(labelFont);
        int labelLineHeight = labelMetrics.getHeight() - 1;
        Font subFont = subLabel == null ? null : new Font(Font.MONOSPACED, Font.BOLD, 20);
        FontMetrics subMetrics = subFont == null ? null : graphics.getFontMetrics(subFont);
        int subLabelHeight = subMetrics == null ? 0 : subMetrics.getHeight();
        int gap = subLabel == null ? 0 : 1;
        int labelBlockHeight = labelLineHeight * lines.length;
        int totalHeight = labelBlockHeight + gap + subLabelHeight;
        int top = (mapSize - totalHeight) / 2;
        int baseline = top + labelMetrics.getAscent();
        for (String line : lines) {
            drawCenteredText(graphics, line, baseline + 2, labelFont, new Color(42, 51, 60));
            drawCenteredText(graphics, line, baseline, labelFont, new Color(248, 244, 232));
            baseline += labelLineHeight;
        }
        if (subLabel != null) {
            int subBaseline = top + labelBlockHeight + gap + subMetrics.getAscent();
            drawCenteredText(graphics, subLabel, subBaseline + 2, subFont, new Color(42, 51, 60));
            drawCenteredText(graphics, subLabel, subBaseline, subFont, new Color(238, 233, 222));
        }
    }

    private Font fitButtonFont(
            Graphics2D graphics,
            String[] lines,
            int style,
            int maxSize,
            int minSize,
            int maxWidth,
            int maxHeight
    ) {
        for (int size = maxSize; size >= minSize; size--) {
            Font font = new Font("Dialog", style, size);
            FontMetrics metrics = graphics.getFontMetrics(font);
            boolean fits = (metrics.getHeight() - 1) * lines.length <= maxHeight;
            for (String line : lines) {
                if (metrics.stringWidth(line) > maxWidth) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                return font;
            }
        }
        return new Font("Dialog", style, minSize);
    }

    private String tooltipFor(PreviewButton button) {
        if (!button.enabled()) {
            return "";
        }
        return switch (button.action()) {
            case PREVIOUS -> "이전 페이지";
            case NEXT -> "다음 페이지";
            case CLOSE -> "닫기";
            case EDIT -> "편집";
            case EXHIBIT -> "전시";
            case SEARCH -> "검색";
            case CLEAR_SEARCH -> "전체 보기";
            case DELETE -> button.label().equals("삭제 확인") ? "삭제 확인" : "삭제";
            default -> "";
        };
    }

    private BufferedImage iconFor(PreviewClickAction action) {
        return switch (action) {
            case PREVIOUS -> loadIcon("decrease.png");
            case NEXT -> loadIcon("increase.png");
            case CLOSE -> loadIcon("back.png");
            case EDIT -> loadIcon("pencil.png");
            case EXHIBIT -> loadIcon("show.png");
            case SEARCH, CLEAR_SEARCH -> null;
            case DELETE -> loadIcon("remove.png");
            default -> null;
        };
    }

    private BufferedImage loadIcon(String fileName) {
        if (iconCache.containsKey(fileName)) {
            return iconCache.get(fileName);
        }
        try (InputStream input = plugin.getResource("paint-panel-icons/" + fileName)) {
            BufferedImage icon = input == null ? null : ImageIO.read(input);
            iconCache.put(fileName, icon);
            return icon;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load Paint preview icon " + fileName + ": " + e.getMessage());
            iconCache.put(fileName, null);
            return null;
        }
    }

    private void drawFallbackIcon(Graphics2D graphics) {
        graphics.setColor(new Color(246, 241, 228));
        graphics.fillOval(mapSize / 2 - 10, mapSize / 2 - 10, 20, 20);
    }

    private void drawCenteredText(Graphics2D graphics, String text, int baseline, Font font, Color color) {
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        int x = (mapSize - metrics.stringWidth(text)) / 2;
        graphics.setColor(color);
        graphics.drawString(text, x, baseline);
    }
    private static BlockFace cardinalFace(Player player) {
        float yaw = player.getLocation().getYaw();
        int index = Math.floorMod(Math.round(yaw / 90.0F), 4);
        return switch (index) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
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

    private static Vector vectorOf(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    private static float yawFor(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    public enum PreviewClickAction {
        ARTWORK,
        PREVIOUS,
        NEXT,
        CLOSE,
        EXHIBIT,
        EDIT,
        SEARCH,
        CLEAR_SEARCH,
        DELETE
    }

    public record PreviewClick(PreviewClickAction action, int artworkIndex, String tooltip) {
        public PreviewClick(PreviewClickAction action, int artworkIndex) {
            this(action, artworkIndex, "");
        }
    }

    private record HoverTarget(UUID entityId, PreviewClick click) {
    }

    private record TooltipState(UUID entityId, String text) {
    }

    private record PreviewButton(String label, String subLabel, PreviewClickAction action, Color color, boolean enabled, boolean showSubLabelWhenDisabled) {
        private PreviewButton(String label, String subLabel, PreviewClickAction action, Color color, boolean enabled) {
            this(label, subLabel, action, color, enabled, false);
        }

        private PreviewButton(String label, String subLabel, PreviewClickAction action, Color color) {
            this(label, subLabel, action, color, true, false);
        }
    }

    private record PreviewBoard(
            List<UUID> entityIds,
            UUID worldId,
            Vector center,
            BlockFace front,
            BlockFace right,
            PreviewClick[][] gridClicks
    ) {
    }

}
