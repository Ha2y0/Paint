package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.CanvasPlane;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.PixelCanvas;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class LayerPanelService {
    private static final double WIDTH = 3.20D;
    private static final double CLOSED_WIDTH = 2.30D;
    private static final double CLOSED_HEIGHT = 0.72D;
    private static final double HEADER_HEIGHT = 0.68D;
    private static final double BUTTON_HEIGHT = 0.64D;
    private static final double ROW_HEIGHT = 0.78D;
    private static final double ROW_GAP = 0.08D;
    private static final double VISIBILITY_BUTTON_WIDTH = 0.58D;
    private static final double MOVE_BUTTON_WIDTH = 0.52D;
    private static final double SETTINGS_HEIGHT = 0.76D;
    private static final double SETTINGS_TOGGLE_WIDTH = 0.82D;
    private static final double SETTINGS_SLIDER_WIDTH = WIDTH - SETTINGS_TOGGLE_WIDTH;
    private static final double OPACITY_MARKER_WIDTH = 0.08D;
    private static final double ROW_SELECT_WIDTH = WIDTH - VISIBILITY_BUTTON_WIDTH - MOVE_BUTTON_WIDTH * 2.0D;
    private static final double BLOCK_DEPTH = 0.025D;
    private static final double BACKGROUND_DEPTH = 0.018D;
    private static final double BUTTON_DEPTH = 0.055D;
    private static final double SETTINGS_CONTROL_DEPTH = 0.085D;
    private static final double SETTINGS_FILL_DEPTH = 0.105D;
    private static final double SETTINGS_MARKER_DEPTH = 0.130D;
    private static final double TEXT_DEPTH = 0.095D;
    private static final double SETTINGS_TEXT_DEPTH = 0.155D;
    private static final double TEXT_Y_OFFSET_PER_SCALE = -0.105D;
    private static final Display.Brightness DISPLAY_BRIGHTNESS = new Display.Brightness(15, 15);
    private static final Material CLOSED_BUTTON_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material BACKGROUND_MATERIAL = Material.BLACK_CONCRETE;
    private static final Material HEADER_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material ADD_BUTTON_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material DELETE_BUTTON_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material ACTIVE_ROW_MATERIAL = Material.LIGHT_BLUE_CONCRETE;
    private static final Material INACTIVE_ROW_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material SETTINGS_BUTTON_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material SETTINGS_OPEN_BUTTON_MATERIAL = Material.CYAN_CONCRETE;
    private static final Material MOVE_BUTTON_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material SETTINGS_BACKGROUND_MATERIAL = Material.BLACK_CONCRETE;
    private static final Material TOGGLE_ON_MATERIAL = Material.LIME_CONCRETE;
    private static final Material TOGGLE_OFF_MATERIAL = Material.PINK_CONCRETE;
    private static final Material SLIDER_BASE_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material SLIDER_FILL_MATERIAL = Material.BLUE_CONCRETE;
    private static final Material SLIDER_MARKER_MATERIAL = Material.WHITE_CONCRETE;

    private final Paint plugin;
    private final String displayTag;
    private final long opacityInteractionLockMillis;
    private final PlaneHitResolver planeHitResolver;
    private final Map<UUID, Long> opacityInteractionLocks = new HashMap<>();
    private final Map<UUID, List<UUID>> displayIdsByOwner = new HashMap<>();

    public LayerPanelService(Paint plugin, String displayTag, long opacityInteractionLockMillis, PlaneHitResolver planeHitResolver) {
        this.plugin = plugin;
        this.displayTag = displayTag;
        this.opacityInteractionLockMillis = opacityInteractionLockMillis;
        this.planeHitResolver = planeHitResolver;
    }

    public void update(PlayerCanvas canvas) {
        remove(canvas.ownerId());
        if (!enabled()) {
            canvas.setLayerPanelOpen(false);
            canvas.setLayerSettingsIndex(-1);
            return;
        }

        World world = plugin.getServer().getWorld(canvas.plane().worldId());
        if (world == null) {
            return;
        }

        double panelMinU = panelMinU(canvas);
        double panelTopV = panelTopV(canvas);
        List<UUID> displayIds = new ArrayList<>();
        if (!canvas.layerPanelOpen()) {
            displayIds.add(spawnBlock(world, canvas, panelMinU, panelTopV - CLOSED_HEIGHT / 2.0D, CLOSED_WIDTH, CLOSED_HEIGHT, CLOSED_BUTTON_MATERIAL, BUTTON_DEPTH).getUniqueId());
            displayIds.add(spawnText(world, canvas, panelMinU + CLOSED_WIDTH / 2.0D, panelTopV - CLOSED_HEIGHT / 2.0D,
                    ChatColor.WHITE + "" + ChatColor.BOLD + "LAYERS", 1.20F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());
        } else {
            spawnOpenPanel(world, canvas, panelMinU, panelTopV, displayIds);
        }

        displayIdsByOwner.put(canvas.ownerId(), displayIds);
    }

    public void remove(UUID ownerId) {
        List<UUID> displayIds = displayIdsByOwner.remove(ownerId);
        if (displayIds != null) {
            displayIds.forEach(this::removeDisplayEntity);
        }
    }

    public void clearAll() {
        for (List<UUID> displayIds : displayIdsByOwner.values()) {
            displayIds.forEach(this::removeDisplayEntity);
        }
        displayIdsByOwner.clear();
        opacityInteractionLocks.clear();
    }

    public PlayerCanvas canvasByDisplay(UUID displayId, Function<UUID, PlayerCanvas> canvasResolver) {
        if (!enabled()) {
            return null;
        }

        for (Map.Entry<UUID, List<UUID>> entry : displayIdsByOwner.entrySet()) {
            if (entry.getValue().contains(displayId)) {
                return canvasResolver.apply(entry.getKey());
            }
        }
        return null;
    }

    public void syncVisibility(Player viewer, Function<UUID, PlayerCanvas> canvasResolver) {
        for (Map.Entry<UUID, List<UUID>> entry : displayIdsByOwner.entrySet()) {
            PlayerCanvas canvas = canvasResolver.apply(entry.getKey());
            for (UUID entityId : entry.getValue()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity == null) {
                    continue;
                }
                if (canvas != null && canvas.canEdit(viewer)) {
                    viewer.showEntity(plugin, entity);
                } else {
                    viewer.hideEntity(plugin, entity);
                }
            }
        }
    }

    public LayerControlLook looked(Player player, Collection<PlayerCanvas> canvases) {
        if (!enabled()) {
            return null;
        }

        LayerControlLook closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (PlayerCanvas canvas : canvases) {
            LayerControlLook look = looked(player, canvas);
            if (look == null || look.distance() >= closestDistance) {
                continue;
            }

            closest = look;
            closestDistance = look.distance();
        }
        return closest;
    }

    public LayerControlLook looked(Player player, PlayerCanvas canvas) {
        LayerPlaneHit hit = planeHitResolver.hit(player, canvas);
        if (hit == null) {
            return null;
        }

        double u = hit.u();
        double v = hit.v();
        double panelMinU = panelMinU(canvas);
        double panelTopV = panelTopV(canvas);
        double panelHeight = canvas.layerPanelOpen() ? panelHeight(canvas) : CLOSED_HEIGHT;
        boolean opacityLocked = hasOpacityInteractionLock(player);
        boolean outsidePanelWidth = u < panelMinU || u >= panelMinU + WIDTH;
        if ((!opacityLocked && outsidePanelWidth) || v > panelTopV || v <= panelTopV - panelHeight) {
            return null;
        }

        if (!canvas.layerPanelOpen()) {
            return new LayerControlLook(canvas, LayerControlAction.TOGGLE_PANEL, -1, -1, hit.distance());
        }

        double localU = u - panelMinU;
        double fromTop = panelTopV - v;
        if (fromTop < HEADER_HEIGHT) {
            return new LayerControlLook(canvas, LayerControlAction.TOGGLE_PANEL, -1, -1, hit.distance());
        }

        if (fromTop < HEADER_HEIGHT + BUTTON_HEIGHT) {
            if (localU < WIDTH * 0.50D) {
                return new LayerControlLook(canvas, LayerControlAction.ADD_LAYER, -1, -1, hit.distance());
            }
            return new LayerControlLook(canvas, LayerControlAction.DELETE_LAYER, canvas.pixelCanvas().activeLayerIndex(), -1, hit.distance());
        }

        for (int layerIndex = 0; layerIndex < canvas.pixelCanvas().activeLayerCount(); layerIndex++) {
            double rowTop = rowTopV(canvas, layerIndex);
            double rowBottom = rowTop - ROW_HEIGHT;
            if (v <= rowTop && v > rowBottom) {
                if (localU >= ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH + MOVE_BUTTON_WIDTH) {
                    return new LayerControlLook(canvas, LayerControlAction.MOVE_LAYER_DOWN, layerIndex, -1, hit.distance());
                }
                if (localU >= ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH) {
                    return new LayerControlLook(canvas, LayerControlAction.MOVE_LAYER_UP, layerIndex, -1, hit.distance());
                }
                if (localU >= ROW_SELECT_WIDTH) {
                    return new LayerControlLook(canvas, LayerControlAction.TOGGLE_LAYER_SETTINGS, layerIndex, -1, hit.distance());
                }
                return new LayerControlLook(canvas, LayerControlAction.SELECT_LAYER, layerIndex, -1, hit.distance());
            }

            if (openSettingsIndex(canvas) == layerIndex) {
                double settingsTop = rowBottom - ROW_GAP;
                double settingsBottom = settingsTop - SETTINGS_HEIGHT;
                if (v <= settingsTop && v > settingsBottom) {
                    if (!opacityLocked && localU < SETTINGS_TOGGLE_WIDTH) {
                        return new LayerControlLook(canvas, LayerControlAction.TOGGLE_VISIBILITY, layerIndex, -1, hit.distance());
                    }
                    double sliderU = Math.max(0.0D, Math.min(SETTINGS_SLIDER_WIDTH, localU - SETTINGS_TOGGLE_WIDTH));
                    int opacityPercent = (int) Math.round(sliderU / SETTINGS_SLIDER_WIDTH * 100.0D);
                    return new LayerControlLook(canvas, LayerControlAction.SET_LAYER_OPACITY, layerIndex, opacityPercent, hit.distance());
                }
            }
        }
        return null;
    }

    public void lockOpacityInteraction(UUID playerId) {
        opacityInteractionLocks.put(playerId, System.currentTimeMillis() + opacityInteractionLockMillis);
    }

    public boolean hasOpacityInteractionLock(Player player) {
        UUID playerId = player.getUniqueId();
        long untilMillis = opacityInteractionLocks.getOrDefault(playerId, 0L);
        if (untilMillis <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() <= untilMillis) {
            return true;
        }

        opacityInteractionLocks.remove(playerId);
        return false;
    }

    public void clearOpacityLock(UUID playerId) {
        opacityInteractionLocks.remove(playerId);
    }

    private void spawnOpenPanel(World world, PlayerCanvas canvas, double panelMinU, double panelTopV, List<UUID> displayIds) {
        double panelHeight = panelHeight(canvas);
        displayIds.add(spawnBlock(world, canvas, panelMinU - 0.06D, panelTopV - panelHeight / 2.0D, WIDTH + 0.12D, panelHeight, BACKGROUND_MATERIAL, BACKGROUND_DEPTH).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU, panelTopV - HEADER_HEIGHT / 2.0D, WIDTH, HEADER_HEIGHT, HEADER_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + WIDTH / 2.0D, panelTopV - HEADER_HEIGHT / 2.0D,
                ChatColor.AQUA + "" + ChatColor.BOLD + "[<] " + ChatColor.WHITE + "LAYERS", 1.20F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU, panelTopV - HEADER_HEIGHT - BUTTON_HEIGHT / 2.0D, WIDTH * 0.50D, BUTTON_HEIGHT, ADD_BUTTON_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + WIDTH * 0.25D, panelTopV - HEADER_HEIGHT - BUTTON_HEIGHT / 2.0D,
                ChatColor.GREEN + "" + ChatColor.BOLD + "ADD", 1.20F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU + WIDTH * 0.50D, panelTopV - HEADER_HEIGHT - BUTTON_HEIGHT / 2.0D, WIDTH * 0.50D, BUTTON_HEIGHT, DELETE_BUTTON_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + WIDTH * 0.75D, panelTopV - HEADER_HEIGHT - BUTTON_HEIGHT / 2.0D,
                ChatColor.RED + "" + ChatColor.BOLD + "DEL", 1.20F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());

        for (int layer = 0; layer < canvas.pixelCanvas().activeLayerCount(); layer++) {
            spawnLayerRow(world, canvas, panelMinU, layer, displayIds);
        }
    }

    private void spawnLayerRow(World world, PlayerCanvas canvas, double panelMinU, int layer, List<UUID> displayIds) {
        boolean active = canvas.pixelCanvas().activeLayerIndex() == layer;
        boolean visible = canvas.pixelCanvas().isLayerVisible(layer);
        boolean settingsOpen = openSettingsIndex(canvas) == layer;
        int opacityPercent = canvas.pixelCanvas().layerOpacityPercent(layer);
        ChatColor color = active ? ChatColor.WHITE : ChatColor.GRAY;
        double rowCenter = rowCenterV(canvas, layer);

        displayIds.add(spawnBlock(world, canvas, panelMinU, rowCenter, ROW_SELECT_WIDTH, ROW_HEIGHT,
                active ? ACTIVE_ROW_MATERIAL : INACTIVE_ROW_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + ROW_SELECT_WIDTH / 2.0D, rowCenter,
                color + "" + ChatColor.BOLD + activeLayerName(canvas.pixelCanvas(), layer), 1.45F,
                TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU + ROW_SELECT_WIDTH, rowCenter, VISIBILITY_BUTTON_WIDTH, ROW_HEIGHT,
                settingsOpen ? SETTINGS_OPEN_BUTTON_MATERIAL : SETTINGS_BUTTON_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH / 2.0D, rowCenter,
                settingsOpen ? ChatColor.WHITE + "" + ChatColor.BOLD + "SET" : ChatColor.GRAY + "" + ChatColor.BOLD + "SET",
                0.82F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU + ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH, rowCenter,
                MOVE_BUTTON_WIDTH, ROW_HEIGHT, MOVE_BUTTON_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH + MOVE_BUTTON_WIDTH / 2.0D,
                rowCenter, ChatColor.GRAY + "" + ChatColor.BOLD + "^", 1.55F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU + ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH + MOVE_BUTTON_WIDTH, rowCenter,
                MOVE_BUTTON_WIDTH, ROW_HEIGHT, MOVE_BUTTON_MATERIAL, BUTTON_DEPTH).getUniqueId());
        displayIds.add(spawnText(world, canvas, panelMinU + ROW_SELECT_WIDTH + VISIBILITY_BUTTON_WIDTH + MOVE_BUTTON_WIDTH + MOVE_BUTTON_WIDTH / 2.0D,
                rowCenter, ChatColor.GRAY + "" + ChatColor.BOLD + "v", 1.55F, TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0)).getUniqueId());

        if (settingsOpen) {
            spawnLayerSettings(world, canvas, panelMinU, layer, visible, opacityPercent, displayIds);
        }
    }

    private void spawnLayerSettings(World world, PlayerCanvas canvas, double panelMinU, int layer, boolean visible, int opacityPercent, List<UUID> displayIds) {
        double settingsCenter = settingsCenterV(canvas, layer);
        displayIds.add(spawnBlock(world, canvas, panelMinU, settingsCenter, WIDTH, SETTINGS_HEIGHT, SETTINGS_BACKGROUND_MATERIAL, BACKGROUND_DEPTH).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU, settingsCenter, SETTINGS_TOGGLE_WIDTH, SETTINGS_HEIGHT,
                visible ? TOGGLE_ON_MATERIAL : TOGGLE_OFF_MATERIAL, SETTINGS_CONTROL_DEPTH).getUniqueId());
        displayIds.add(spawnTextAtDepth(world, canvas, panelMinU + SETTINGS_TOGGLE_WIDTH / 2.0D, settingsCenter,
                ChatColor.WHITE + "" + ChatColor.BOLD + (visible ? "ON" : "OFF"), 1.18F,
                TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0), SETTINGS_TEXT_DEPTH).getUniqueId());
        displayIds.add(spawnBlock(world, canvas, panelMinU + SETTINGS_TOGGLE_WIDTH, settingsCenter, SETTINGS_SLIDER_WIDTH,
                SETTINGS_HEIGHT, SLIDER_BASE_MATERIAL, SETTINGS_CONTROL_DEPTH).getUniqueId());

        double fillWidth = SETTINGS_SLIDER_WIDTH * opacityPercent / 100.0D;
        if (fillWidth > 0.0D) {
            displayIds.add(spawnBlock(world, canvas, panelMinU + SETTINGS_TOGGLE_WIDTH, settingsCenter, fillWidth,
                    SETTINGS_HEIGHT, SLIDER_FILL_MATERIAL, SETTINGS_FILL_DEPTH).getUniqueId());
        }

        double markerLeft = panelMinU + SETTINGS_TOGGLE_WIDTH + Math.max(0.0D, fillWidth - OPACITY_MARKER_WIDTH / 2.0D);
        displayIds.add(spawnBlock(world, canvas, markerLeft, settingsCenter, OPACITY_MARKER_WIDTH,
                SETTINGS_HEIGHT, SLIDER_MARKER_MATERIAL, SETTINGS_MARKER_DEPTH).getUniqueId());
        displayIds.add(spawnTextAtDepth(world, canvas, panelMinU + SETTINGS_TOGGLE_WIDTH + SETTINGS_SLIDER_WIDTH / 2.0D,
                settingsCenter, ChatColor.WHITE + "" + ChatColor.BOLD + opacityPercent + "%", 0.96F,
                TextDisplay.TextAlignment.CENTER, org.bukkit.Color.fromARGB(0, 0, 0, 0), SETTINGS_TEXT_DEPTH).getUniqueId());
    }

    private TextDisplay spawnText(World world, PlayerCanvas canvas, double rightOffset, double yOffset, String text, float scale,
                                  TextDisplay.TextAlignment alignment, org.bukkit.Color background) {
        return spawnTextAtDepth(world, canvas, rightOffset, yOffset, text, scale, alignment, background, TEXT_DEPTH);
    }

    private TextDisplay spawnTextAtDepth(World world, PlayerCanvas canvas, double rightOffset, double yOffset, String text, float scale,
                                         TextDisplay.TextAlignment alignment, org.bukkit.Color background, double depth) {
        TextDisplay display = world.spawn(location(canvas, rightOffset, yOffset + scale * TEXT_Y_OFFSET_PER_SCALE, depth), TextDisplay.class);
        display.setText(text);
        configureTextDisplay(display, scale, alignment, DISPLAY_BRIGHTNESS);
        display.setDefaultBackground(false);
        display.setBackgroundColor(background);
        display.addScoreboardTag(displayTag);
        makeEntityVisibleToEditors(display, canvas);
        return display;
    }

    private BlockDisplay spawnBlock(World world, PlayerCanvas canvas, double leftOffset, double centerYOffset, double width,
                                    double height, Material material, double depth) {
        BlockDisplay display = world.spawn(location(canvas, leftOffset + width / 2.0D, centerYOffset, depth), BlockDisplay.class);
        display.setBlock(material.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(DISPLAY_BRIGHTNESS);
        display.setViewRange(64.0F);
        display.setInterpolationDuration(0);
        display.setTransformation(new Transformation(
                new Vector3f((float) (-width / 2.0D), (float) (-height / 2.0D), (float) (-BLOCK_DEPTH / 2.0D)),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f((float) width, (float) height, (float) BLOCK_DEPTH),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
        ));
        display.addScoreboardTag(displayTag);
        makeEntityVisibleToEditors(display, canvas);
        return display;
    }

    private void configureTextDisplay(TextDisplay display, float scale, TextDisplay.TextAlignment alignment, Display.Brightness brightness) {
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(brightness);
        display.setViewRange(64.0F);
        display.setAlignment(alignment);
        display.setShadowed(false);
        display.setDefaultBackground(false);
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f(scale, scale, 1.0F),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
        ));
    }

    private double panelMinU(PlayerCanvas canvas) {
        return canvas.pixelCanvas().blockWidth() + 0.35D;
    }

    private double panelTopV(PlayerCanvas canvas) {
        return canvas.pixelCanvas().blockHeight() - 0.30D;
    }

    private double panelHeight(PlayerCanvas canvas) {
        double height = HEADER_HEIGHT + BUTTON_HEIGHT + canvas.pixelCanvas().activeLayerCount() * (ROW_HEIGHT + ROW_GAP);
        if (openSettingsIndex(canvas) >= 0) {
            height += SETTINGS_HEIGHT + ROW_GAP;
        }
        return height;
    }

    private int openSettingsIndex(PlayerCanvas canvas) {
        int index = canvas.layerSettingsIndex();
        if (index < 0 || index >= canvas.pixelCanvas().activeLayerCount()) {
            return -1;
        }
        return index;
    }

    private double rowTopV(PlayerCanvas canvas, int row) {
        double top = panelTopV(canvas) - HEADER_HEIGHT - BUTTON_HEIGHT;
        int settingsIndex = openSettingsIndex(canvas);
        for (int index = 0; index < row; index++) {
            top -= ROW_HEIGHT + ROW_GAP;
            if (index == settingsIndex) {
                top -= SETTINGS_HEIGHT + ROW_GAP;
            }
        }
        return top;
    }

    private double rowCenterV(PlayerCanvas canvas, int row) {
        return rowTopV(canvas, row) - ROW_HEIGHT / 2.0D;
    }

    private double settingsCenterV(PlayerCanvas canvas, int row) {
        return rowTopV(canvas, row) - ROW_HEIGHT - ROW_GAP - SETTINGS_HEIGHT / 2.0D;
    }

    private Location location(PlayerCanvas canvas, double rightOffset, double yOffset, double depth) {
        CanvasPlane plane = canvas.plane();
        World world = plugin.getServer().getWorld(plane.worldId());
        Vector front = vectorOf(plane.facing().getOppositeFace());
        Vector location = plane.facePoint()
                .add(vectorOf(plane.right()).multiply(rightOffset))
                .add(new Vector(0.0D, yOffset, 0.0D))
                .add(front.multiply(depth));
        Location result = new Location(world, location.getX(), location.getY(), location.getZ());
        result.setYaw(yawFor(plane.facing().getOppositeFace()));
        result.setPitch(0.0F);
        return result;
    }

    private void removeDisplayEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private void makeEntityVisibleToEditors(Entity entity, PlayerCanvas canvas) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canvas.canEdit(viewer)) {
                viewer.showEntity(plugin, entity);
            } else {
                viewer.hideEntity(plugin, entity);
            }
        }
    }

    private String activeLayerName(PixelCanvas pixelCanvas, int layerIndex) {
        return Integer.toString(pixelCanvas.layerLabel(layerIndex));
    }

    private boolean enabled() {
        return true;
    }

    private Vector vectorOf(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    private float yawFor(BlockFace face) {
        return switch (face) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    @FunctionalInterface
    public interface PlaneHitResolver {
        LayerPlaneHit hit(Player player, PlayerCanvas canvas);
    }

    public enum LayerControlAction {
        TOGGLE_PANEL,
        ADD_LAYER,
        DELETE_LAYER,
        SELECT_LAYER,
        TOGGLE_LAYER_SETTINGS,
        TOGGLE_VISIBILITY,
        SET_LAYER_OPACITY,
        MOVE_LAYER_UP,
        MOVE_LAYER_DOWN
    }

    public record LayerControlLook(
            PlayerCanvas canvas,
            LayerControlAction action,
            int layerIndex,
            int opacityPercent,
            double distance
    ) {
    }

    public record LayerPlaneHit(double u, double v, double distance) {
    }
}
