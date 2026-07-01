package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.renderer.GalleryImageMapRenderer;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import javax.imageio.ImageIO;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class PaintPanelService {
    private static final String PANEL_TAG = "paint_menu_panel";
    private static final int MAP_SIZE = 128;
    private static final double TILE_STEP = 1.0D;
    private static final Color NEW_BUTTON_COLOR = new Color(58, 132, 112);
    private static final Color SAVE_BUTTON_COLOR = new Color(160, 128, 48);
    private static final Color GALLERY_BUTTON_COLOR = new Color(76, 96, 148);
    private static final Color SHOW_BUTTON_COLOR = new Color(62, 128, 122);
    private static final Color DELETE_BUTTON_COLOR = new Color(132, 78, 60);
    private static final Color EXIT_BUTTON_COLOR = new Color(136, 72, 66);
    private static final Color DELETE_CONFIRM_BUTTON_COLOR = new Color(172, 66, 72);

    private final Paint plugin;
    private final Color backgroundColor;
    private final BooleanSupplier shaderRgbEnabled;
    private final Map<UUID, PanelBoard> boards = new HashMap<>();
    private final Map<UUID, PanelClick> clicks = new HashMap<>();
    private final Map<UUID, TooltipState> tooltips = new HashMap<>();
    private final Map<String, BufferedImage> iconCache = new HashMap<>();
    private final Set<UUID> displayEntityIds = new java.util.HashSet<>();

    public PaintPanelService(Paint plugin, Color backgroundColor, BooleanSupplier shaderRgbEnabled) {
        this.plugin = plugin;
        this.backgroundColor = backgroundColor;
        this.shaderRgbEnabled = shaderRgbEnabled;
    }

    public void showMainMenu(UUID playerId, Location centerLocation, BlockFace facing, boolean confirmRemove) {
        clear(playerId);
        World world = centerLocation.getWorld();
        if (world == null) {
            return;
        }
        BlockFace front = facing.getOppositeFace();
        BlockFace right = rightOf(facing);
        Vector center = centerLocation.toVector();
        List<UUID> entityIds = new ArrayList<>();
        PanelButton[] buttons = {
                new PanelButton("NEW", "CANVAS", PaintMenuService.MenuAction.NEW, NEW_BUTTON_COLOR),
                new PanelButton("SAVE", "ART", PaintMenuService.MenuAction.SAVE, SAVE_BUTTON_COLOR),
                new PanelButton("MY", "ART", PaintMenuService.MenuAction.LIST, GALLERY_BUTTON_COLOR),
                new PanelButton("SHOW", "ART", PaintMenuService.MenuAction.SHOW, SHOW_BUTTON_COLOR),
                new PanelButton(confirmRemove ? "OK?" : "DEL", "CANVAS", PaintMenuService.MenuAction.REMOVE, confirmRemove ? DELETE_CONFIRM_BUTTON_COLOR : DELETE_BUTTON_COLOR),
                new PanelButton("EXIT", "PANEL", PaintMenuService.MenuAction.CANCEL, EXIT_BUTTON_COLOR)
        };
        for (int col = 0; col < buttons.length; col++) {
            PanelButton button = buttons[col];
            ItemFrame frame = spawnPanelFrame(world, center, right, front, col, 0, buttons.length);
            frame.setItem(createMapItem(world, buttonTile(button)), false);
            register(entityIds, frame, clickFor(button));
        }
        boards.put(playerId, new PanelBoard(entityIds, centerLocation.clone(), facing));
    }

    public boolean showMainMenuAtCurrentLocation(UUID playerId, boolean confirmRemove) {
        PanelBoard board = boards.get(playerId);
        if (board == null) {
            return false;
        }
        showMainMenu(playerId, board.centerLocation(), board.facing(), confirmRemove);
        return true;
    }
    public void showCanvasSizeMenu(UUID playerId, Location centerLocation, BlockFace facing, int width, int height, int maxSize) {
        clear(playerId);
        World world = centerLocation.getWorld();
        if (world == null) {
            return;
        }
        BlockFace front = facing.getOppositeFace();
        BlockFace right = rightOf(facing);
        Vector center = centerLocation.toVector();
        List<UUID> entityIds = new ArrayList<>();
        PanelButton[] buttons = {
                new PanelButton("W-", String.valueOf(width), PaintMenuService.MenuAction.WIDTH_DOWN, new Color(112, 74, 128)),
                new PanelButton("W+", String.valueOf(width), PaintMenuService.MenuAction.WIDTH_UP, new Color(72, 128, 104)),
                new PanelButton("H-", String.valueOf(height), PaintMenuService.MenuAction.HEIGHT_DOWN, new Color(112, 74, 128)),
                new PanelButton("H+", String.valueOf(height), PaintMenuService.MenuAction.HEIGHT_UP, new Color(72, 128, 104)),
                new PanelButton("MAKE", width + "x" + height, PaintMenuService.MenuAction.CREATE_CANVAS, new Color(68, 116, 154)),
                new PanelButton("BACK", "MENU", PaintMenuService.MenuAction.BACK, new Color(126, 82, 62))
        };
        for (int col = 0; col < buttons.length; col++) {
            PanelButton button = buttons[col];
            ItemFrame frame = spawnPanelFrame(world, center, right, front, col, 0, buttons.length);
            frame.setItem(createMapItem(world, buttonTile(button)), false);
            register(entityIds, frame, clickFor(button));
        }
        boards.put(playerId, new PanelBoard(entityIds, centerLocation.clone(), facing));
    }

    public boolean showCanvasSizeMenuAtCurrentLocation(UUID playerId, int width, int height, int maxSize) {
        PanelBoard board = boards.get(playerId);
        if (board == null) {
            return false;
        }
        showCanvasSizeMenu(playerId, board.centerLocation(), board.facing(), width, height, maxSize);
        return true;
    }

    public void showManualControlPanel(UUID playerId, Location centerLocation, BlockFace facing, boolean vertical, boolean confirmRemove) {
        clear(playerId);
        World world = centerLocation.getWorld();
        if (world == null) {
            return;
        }
        BlockFace front = facing.getOppositeFace();
        BlockFace right = rightOf(facing);
        Vector center = centerLocation.toVector();
        List<UUID> entityIds = new ArrayList<>();
        PanelButton[] buttons = {
                new PanelButton("SAVE", "ART", PaintMenuService.MenuAction.SAVE, SAVE_BUTTON_COLOR),
                new PanelButton(confirmRemove ? "OK?" : "END", "CANVAS", PaintMenuService.MenuAction.REMOVE, confirmRemove ? DELETE_CONFIRM_BUTTON_COLOR : DELETE_BUTTON_COLOR)
        };
        int columns = vertical ? 1 : buttons.length;
        int rows = vertical ? buttons.length : 1;
        for (int index = 0; index < buttons.length; index++) {
            PanelButton button = buttons[index];
            int col = vertical ? 0 : index;
            int row = vertical ? index : 0;
            ItemFrame frame = spawnPanelFrame(world, center, right, front, col, row, columns, rows);
            frame.setItem(createMapItem(world, buttonTile(button)), false);
            register(entityIds, frame, clickFor(button));
        }
        boards.put(playerId, new PanelBoard(entityIds, centerLocation.clone(), facing));
    }

    public PanelClick click(UUID entityId) {
        return clicks.get(entityId);
    }

    public PanelClick lookedClick(Player player, double maxDistance) {
        HoverTarget target = lookedTarget(player, maxDistance);
        return target == null ? null : target.click();
    }

    public void updateTooltip(Player player, double maxDistance) {
        UUID playerId = player.getUniqueId();
        if (!boards.containsKey(playerId)) {
            clearTooltip(playerId);
            return;
        }
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
        if (display == null) {
            display = spawnTooltip(player, location, target.click().tooltip());
            tooltips.put(playerId, new TooltipState(display.getUniqueId(), target.click().tooltip()));
            return;
        }
        display.teleport(location);
        if (!state.text().equals(target.click().tooltip())) {
            display.setText(target.click().tooltip());
            tooltips.put(playerId, new TooltipState(display.getUniqueId(), target.click().tooltip()));
        }
    }

    private HoverTarget lookedTarget(Player player, double maxDistance) {
        PanelBoard board = boards.get(player.getUniqueId());
        if (board == null || board.centerLocation().getWorld() == null
                || !player.getWorld().getUID().equals(board.centerLocation().getWorld().getUID())) {
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
        PanelClick click = clicks.get(entity.getUniqueId());
        return click == null ? null : new HoverTarget(entity.getUniqueId(), click);
    }

    public boolean isDisplayEntity(UUID entityId) {
        return displayEntityIds.contains(entityId);
    }

    public boolean isShowing(UUID playerId) {
        return boards.containsKey(playerId);
    }

    public void clear(UUID playerId) {
        clearTooltip(playerId);
        PanelBoard board = boards.remove(playerId);
        if (board == null) {
            return;
        }
        for (UUID entityId : board.entityIds()) {
            clicks.remove(entityId);
            displayEntityIds.remove(entityId);
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void clearAll() {
        for (UUID playerId : new ArrayList<>(boards.keySet())) {
            clear(playerId);
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getScoreboardTags().contains(PANEL_TAG)) {
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

    private PanelClick clickFor(PanelButton button) {
        return new PanelClick(button.action(), tooltipFor(button));
    }

    private String tooltipFor(PanelButton button) {
        return switch (button.action()) {
            case NEW -> "새 캔버스";
            case SAVE -> "그림 저장";
            case LIST -> "내 그림";
            case SHOW -> "전시 제거";
            case REMOVE -> button.label().equals("OK?") ? "삭제 확인" : "캔버스 삭제";
            case CANCEL -> "나가기";
            case WIDTH_DOWN -> "Length 감소: " + button.subLabel();
            case WIDTH_UP -> "Length 증가: " + button.subLabel();
            case HEIGHT_DOWN -> "Height 감소: " + button.subLabel();
            case HEIGHT_UP -> "Height 증가: " + button.subLabel();
            case CREATE_CANVAS -> "생성: " + button.subLabel();
            case BACK -> "뒤로";
            default -> "";
        };
    }
    private void register(List<UUID> entityIds, ItemFrame frame, PanelClick click) {
        frame.addScoreboardTag(PANEL_TAG);
        entityIds.add(frame.getUniqueId());
        displayEntityIds.add(frame.getUniqueId());
        clicks.put(frame.getUniqueId(), click);
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
        TextDisplay display = owner.getWorld().spawn(location, TextDisplay.class);
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
        display.addScoreboardTag(PANEL_TAG);
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

    private ItemFrame spawnPanelFrame(World world, Vector center, BlockFace right, BlockFace front, int col, int row, int columns) {
        return spawnPanelFrame(world, center, right, front, col, row, columns, 1);
    }

    private ItemFrame spawnPanelFrame(World world, Vector center, BlockFace right, BlockFace front, int col, int row, int columns, int rows) {
        double rightOffset = (col - (columns - 1) / 2.0D) * TILE_STEP;
        double upOffset = ((rows - 1) / 2.0D - row) * TILE_STEP;
        Vector locationVector = center.clone()
                .add(vectorOf(right).multiply(rightOffset))
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

    private ItemStack createMapItem(World world, BufferedImage image) {
        MapView mapView = plugin.getServer().createMap(world);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new GalleryImageMapRenderer(image, 0, 0, MAP_SIZE, backgroundColor, shaderRgbEnabled.getAsBoolean()));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private BufferedImage buttonTile(PanelButton button) {
        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(42, 52, 58));
        graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
        graphics.setColor(button.color());
        graphics.fillRoundRect(6, 6, MAP_SIZE - 12, MAP_SIZE - 12, 10, 10);
        graphics.setColor(new Color(232, 238, 236));
        graphics.setStroke(new BasicStroke(5.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawRoundRect(9, 9, MAP_SIZE - 18, MAP_SIZE - 18, 8, 8);

        if (isSizeControl(button.action())) {
            drawButtonOverlay(graphics, button);
        } else {
            BufferedImage icon = iconFor(button);
            if (icon != null) {
                int size = button.action() == PaintMenuService.MenuAction.CREATE_CANVAS ? 64 : 76;
                int x = (MAP_SIZE - size) / 2;
                int y = button.action() == PaintMenuService.MenuAction.CREATE_CANVAS ? 18 : (MAP_SIZE - size) / 2;
                graphics.drawImage(icon, x, y, size, size, null);
            } else {
                drawFallbackIcon(graphics);
            }
            drawButtonOverlay(graphics, button);
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage iconFor(PanelButton button) {
        return switch (button.action()) {
            case NEW -> loadIcon("new.png");
            case SAVE -> loadIcon("save.png");
            case LIST -> loadIcon("gallery.png");
            case SHOW -> loadIcon("exhibit_remove.png");
            case REMOVE -> loadIcon("remove.png");
            case CREATE_CANVAS -> loadIcon("create.png");
            case WIDTH_DOWN, WIDTH_UP, HEIGHT_DOWN, HEIGHT_UP -> null;
            case BACK -> loadIcon("back.png");
            case CANCEL -> loadIcon("back.png");
            default -> null;
        };
    }

    private void drawButtonOverlay(Graphics2D graphics, PanelButton button) {
        PaintMenuService.MenuAction action = button.action();
        if (isSizeControl(action)) {
            drawSizeControl(
                    graphics,
                    isWidthControl(action) ? "L" : "H",
                    button.subLabel(),
                    action == PaintMenuService.MenuAction.WIDTH_UP
                            || action == PaintMenuService.MenuAction.HEIGHT_UP
            );
            return;
        }
        if (action == PaintMenuService.MenuAction.CREATE_CANVAS) {
            drawCreateSize(graphics, button.subLabel());
        }
    }

    private boolean isSizeControl(PaintMenuService.MenuAction action) {
        return action == PaintMenuService.MenuAction.WIDTH_DOWN
                || action == PaintMenuService.MenuAction.WIDTH_UP
                || action == PaintMenuService.MenuAction.HEIGHT_DOWN
                || action == PaintMenuService.MenuAction.HEIGHT_UP;
    }

    private boolean isWidthControl(PaintMenuService.MenuAction action) {
        return action == PaintMenuService.MenuAction.WIDTH_DOWN
                || action == PaintMenuService.MenuAction.WIDTH_UP;
    }

    private void drawSizeControl(Graphics2D graphics, String axis, String value, boolean increase) {
        drawCenteredText(graphics, axis, 50, new Font(Font.MONOSPACED, Font.BOLD, 42), new Color(248, 244, 226));
        drawCenteredText(graphics, value, 100, new Font(Font.MONOSPACED, Font.BOLD, 34), new Color(248, 244, 226));
        drawStepBadge(graphics, increase);
    }

    private void drawCreateSize(Graphics2D graphics, String value) {
        graphics.setColor(new Color(42, 52, 58, 210));
        graphics.fillRoundRect(22, 82, 84, 28, 8, 8);
        drawCenteredText(graphics, value, 104, new Font(Font.MONOSPACED, Font.BOLD, 22), new Color(248, 244, 226));
    }

    private void drawStepBadge(Graphics2D graphics, boolean increase) {
        graphics.setColor(increase ? new Color(70, 170, 96) : new Color(172, 66, 72));
        graphics.fillOval(78, 72, 34, 34);
        graphics.setColor(new Color(248, 244, 226));
        graphics.setStroke(new BasicStroke(5.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(87, 89, 103, 89);
        if (increase) {
            graphics.drawLine(95, 81, 95, 97);
        }
    }

    private void drawCenteredText(Graphics2D graphics, String text, int baseline, Font font, Color color) {
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        int x = (MAP_SIZE - metrics.stringWidth(text)) / 2;
        graphics.setColor(color);
        graphics.drawString(text, x, baseline);
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
            plugin.getLogger().warning("Could not load Paint panel icon " + fileName + ": " + e.getMessage());
            iconCache.put(fileName, null);
            return null;
        }
    }

    private void drawFallbackIcon(Graphics2D graphics) {
        graphics.setColor(new Color(248, 244, 226));
        graphics.fillOval(54, 54, 20, 20);
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

    public record PanelClick(PaintMenuService.MenuAction action, String tooltip) {
    }

    private record HoverTarget(UUID entityId, PanelClick click) {
    }

    private record TooltipState(UUID entityId, String text) {
    }

    private record PanelButton(String label, String subLabel, PaintMenuService.MenuAction action, Color color) {
    }

    private record PanelBoard(List<UUID> entityIds, Location centerLocation, BlockFace facing) {
    }
}
