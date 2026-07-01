package org.ha2yo.paint.workflow;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.station.ManualStation;
import org.ha2yo.paint.model.station.StationCanvasSlot;
import org.ha2yo.paint.model.station.StationPanelSlot;
import org.ha2yo.paint.service.CanvasLifecycleService;
import org.ha2yo.paint.service.CanvasMapRenderService;
import org.ha2yo.paint.service.ManualStationService;
import org.ha2yo.paint.service.PaintMenuService;
import org.ha2yo.paint.service.PaintPanelModeService;
import org.ha2yo.paint.service.PaintPanelService;
import org.ha2yo.paint.service.PaintWindowPlacementService;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

public final class ManualStationWorkflowService {
    private static final DateTimeFormatter AUTO_SAVE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final double MANUAL_GALLERY_CENTER_OFFSET = 2.5D;

    private final Paint plugin;
    private final ManualStationService stations;
    private final CanvasLifecycleService canvasLifecycle;
    private final CanvasWorkflowService canvasWorkflow;
    private final CanvasMapRenderService canvasMapRenderer;
    private final PaintWindowPlacementService paintWindows;
    private final PaintPanelService paintPanels;
    private final PaintPanelModeService paintPanelModes;
    private final ArtworkGalleryWorkflowService artworkGalleryWorkflow;
    private final ArtworkSaveWorkflowService artworkSaveWorkflow;
    private final Function<UUID, Optional<PaintCanvas>> canvasResolver;
    private final Predicate<Player> freeModeChecker;
    private final Consumer<Player> toolGiver;
    private final int pixelsPerBlock;
    private final int mapSize;
    private final int drawScale;
    private final Color backgroundColor;
    private final IntUnaryOperator blockSizeClamp;
    private final Set<UUID> pendingControlRemoveConfirms = new HashSet<>();

    public ManualStationWorkflowService(
            Paint plugin,
            ManualStationService stations,
            CanvasLifecycleService canvasLifecycle,
            CanvasWorkflowService canvasWorkflow,
            CanvasMapRenderService canvasMapRenderer,
            PaintWindowPlacementService paintWindows,
            PaintPanelService paintPanels,
            PaintPanelModeService paintPanelModes,
            ArtworkGalleryWorkflowService artworkGalleryWorkflow,
            ArtworkSaveWorkflowService artworkSaveWorkflow,
            Function<UUID, Optional<PaintCanvas>> canvasResolver,
            Predicate<Player> freeModeChecker,
            Consumer<Player> toolGiver,
            int pixelsPerBlock,
            int mapSize,
            int drawScale,
            Color backgroundColor,
            IntUnaryOperator blockSizeClamp
    ) {
        this.plugin = plugin;
        this.stations = stations;
        this.canvasLifecycle = canvasLifecycle;
        this.canvasWorkflow = canvasWorkflow;
        this.canvasMapRenderer = canvasMapRenderer;
        this.paintWindows = paintWindows;
        this.paintPanels = paintPanels;
        this.paintPanelModes = paintPanelModes;
        this.artworkGalleryWorkflow = artworkGalleryWorkflow;
        this.artworkSaveWorkflow = artworkSaveWorkflow;
        this.canvasResolver = canvasResolver;
        this.freeModeChecker = freeModeChecker;
        this.toolGiver = toolGiver;
        this.pixelsPerBlock = pixelsPerBlock;
        this.mapSize = mapSize;
        this.drawScale = drawScale;
        this.backgroundColor = backgroundColor;
        this.blockSizeClamp = blockSizeClamp;
    }

    public void loadAndSpawn() {
        stations.load();
        spawnBlankCanvases();
    }

    public boolean isFreeMode(Player player) {
        return freeModeChecker.test(player);
    }

    public boolean handleCanvasFrameClick(Player player, UUID frameId) {
        if (isFreeMode(player)) {
            return false;
        }
        PlayerCanvas clicked = canvasLifecycle.canvasByFrame(frameId);
        if (clicked == null) {
            return false;
        }
        for (ManualStation station : stations.stations()) {
            if (stations.blankOwnerId(station.id()).equals(clicked.ownerId())) {
                startStation(player, station);
                return true;
            }
        }
        return false;
    }

    public boolean handleControlAction(Player player, PaintMenuService.MenuAction action) {
        if (isFreeMode(player)) {
            return false;
        }
        Optional<ManualStation> station = stations.occupiedBy(player.getUniqueId());
        if (station.isEmpty()) {
            return false;
        }
        if (action == PaintMenuService.MenuAction.SAVE) {
            pendingControlRemoveConfirms.remove(player.getUniqueId());
            artworkSaveWorkflow.beginNameInput(player);
            return true;
        }
        if (action == PaintMenuService.MenuAction.REMOVE || action == PaintMenuService.MenuAction.CANCEL) {
            if (!pendingControlRemoveConfirms.remove(player.getUniqueId())) {
                pendingControlRemoveConfirms.add(player.getUniqueId());
                showStationControlPanel(player, station.get(), true);
                player.sendMessage(ChatColor.YELLOW + "캔버스를 삭제하려면 한 번 더 눌러 주세요.");
                return true;
            }
            endStation(player, station.get(), true, true);
            player.sendMessage(ChatColor.YELLOW + "수동 캔버스 작업을 종료했습니다.");
            return true;
        }
        return false;
    }

    public void onCanvasSaved(Player player) {
        if (isFreeMode(player)) {
            return;
        }
        stations.occupiedBy(player.getUniqueId()).ifPresent(station -> endStation(player, station, false, true));
    }

    public void clearControlRemoveConfirm(Player player) {
        if (!pendingControlRemoveConfirms.remove(player.getUniqueId())) {
            return;
        }
        stations.occupiedBy(player.getUniqueId()).ifPresent(station -> showStationControlPanel(player, station, false));
    }

    public void onQuit(Player player) {
        if (isFreeMode(player)) {
            return;
        }
        stations.occupiedBy(player.getUniqueId()).ifPresent(station -> {
            Optional<PaintCanvas> canvas = canvasResolver.apply(player.getUniqueId());
            if (canvas.isPresent() && canvas.get().hasPaintedPixels()) {
                artworkSaveWorkflow.saveWithName(player, "자동 저장 " + LocalDateTime.now().format(AUTO_SAVE_FORMAT));
            }
            if (canvasWorkflow.hasCanvas(player.getUniqueId())) {
                canvasWorkflow.remove(player.getUniqueId());
            }
            endStation(player, station, false, false);
        });
    }

    public void spawnBlankCanvases() {
        for (ManualStation station : stations.stations()) {
            if (station.canvas() != null && !station.occupied()) {
                spawnBlankCanvas(station);
            }
        }
    }

    public void setCanvasSlot(Player player, String stationId, int width, int height) {
        if (isStationInUse(player, stationId)) {
            return;
        }
        Block target = player.getTargetBlockExact(10);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "캔버스를 둘 기준 블록을 바라봐 주세요.");
            return;
        }
        BlockFace facing = paintWindows.cardinalFace(player);
        StationCanvasSlot slot = StationCanvasSlot.from(
                target.getLocation(),
                facing,
                paintWindows.rightOf(facing),
                blockSizeClamp.applyAsInt(width),
                blockSizeClamp.applyAsInt(height)
        );
        try {
            ManualStation station = stations.putCanvas(stationId, slot);
            spawnBlankCanvas(station);
            player.sendMessage(ChatColor.GREEN + "수동 캔버스 위치를 저장했습니다. " + ChatColor.WHITE + station.id());
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "수동 캔버스 위치 저장에 실패했습니다.");
        }
    }

    public void setGallerySlot(Player player, String stationId) {
        if (isStationInUse(player, stationId)) {
            return;
        }
        Block target = player.getTargetBlockExact(10);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "갤러리의 좌측하단이 될 블록을 바라봐 주세요.");
            return;
        }
        BlockFace facing = paintWindows.cardinalFace(player);
        BlockFace front = facing.getOppositeFace();
        BlockFace right = paintWindows.rightOf(facing);
        Vector lowerLeft = target.getRelative(front).getLocation().toVector()
                .add(new Vector(0.5D, 0.5D, 0.5D));
        Vector center = lowerLeft
                .add(new Vector(right.getModX(), right.getModY(), right.getModZ()).multiply(MANUAL_GALLERY_CENTER_OFFSET))
                .add(new Vector(0.0D, MANUAL_GALLERY_CENTER_OFFSET, 0.0D));
        Location anchor = new Location(player.getWorld(), center.getX(), center.getY(), center.getZ());
        anchor.setYaw(player.getLocation().getYaw());
        anchor.setPitch(0.0F);
        try {
            ManualStation station = stations.putGallery(stationId, StationPanelSlot.from(anchor, facing));
            player.sendMessage(ChatColor.GREEN + "수동 갤러리 위치를 저장했습니다. " + ChatColor.WHITE + station.id());
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "수동 갤러리 위치 저장에 실패했습니다.");
        }
    }

    public void setControlSlot(Player player, String stationId, StationPanelSlot.Layout layout) {
        if (isStationInUse(player, stationId)) {
            return;
        }
        RayTraceResult hit = player.rayTraceBlocks(10.0D);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            player.sendMessage(ChatColor.RED + "조작판을 붙일 벽을 바라봐 주세요.");
            return;
        }
        BlockFace front = hit.getHitBlockFace();
        BlockFace facing = front.getOppositeFace();
        if (facing.getModY() != 0) {
            player.sendMessage(ChatColor.RED + "조작판은 벽에만 설치할 수 있습니다.");
            return;
        }
        Vector center = hit.getHitBlock().getRelative(front).getLocation().toVector()
                .add(new Vector(0.5D, 0.5D, 0.5D));
        Location anchor = new Location(player.getWorld(), center.getX(), center.getY(), center.getZ());
        anchor.setYaw(player.getLocation().getYaw());
        anchor.setPitch(0.0F);
        try {
            ManualStation station = stations.putControl(stationId, StationPanelSlot.from(anchor, facing, layout));
            player.sendMessage(ChatColor.GREEN + "수동 조작판 위치를 저장했습니다. " + ChatColor.WHITE + station.id());
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "수동 조작판 위치 저장에 실패했습니다.");
        }
    }

    private boolean isStationInUse(Player player, String stationId) {
        Optional<ManualStation> station = stations.station(stationId);
        if (station.isEmpty() || !station.get().occupied()) {
            return false;
        }
        player.sendMessage(ChatColor.RED + "사용 중인 수동 캔버스의 위치는 변경할 수 없습니다.");
        return true;
    }

    public void listStations(Player player) {
        if (stations.stations().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "등록된 수동 자리가 없습니다.");
            return;
        }
        for (ManualStation station : stations.stations()) {
            player.sendMessage(ChatColor.GRAY + station.id()
                    + " canvas=" + yesNo(station.canvas() != null)
                    + " gallery=" + yesNo(station.gallery() != null)
                    + " control=" + yesNo(station.control() != null)
                    + " occupied=" + yesNo(station.occupied()));
        }
    }

    public void removeStation(Player player, String stationId) {
        stations.station(stationId).ifPresent(station -> {
            if (station.canvas() != null) {
                canvasLifecycle.remove(stations.blankOwnerId(station.id()));
            }
        });
        try {
            if (stations.remove(stationId)) {
                player.sendMessage(ChatColor.YELLOW + "수동 자리를 삭제했습니다. " + stationId);
            } else {
                player.sendMessage(ChatColor.RED + "해당 수동 자리가 없습니다. " + stationId);
            }
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "수동 자리 삭제에 실패했습니다.");
        }
    }

    private void startStation(Player player, ManualStation station) {
        if (!station.ready()) {
            player.sendMessage(ChatColor.RED + "이 자리는 캔버스, 갤러리, 조작판 위치가 모두 필요합니다.");
            return;
        }
        if (station.occupied()) {
            player.sendMessage(ChatColor.RED + "이미 다른 유저가 사용 중인 캔버스입니다.");
            return;
        }
        if (canvasWorkflow.hasCanvas(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "이미 캔버스가 있습니다. 먼저 기존 캔버스를 제거해 주세요.");
            return;
        }

        Location origin = stations.canvasLocation(station.canvas());
        if (origin == null) {
            player.sendMessage(ChatColor.RED + "캔버스 월드를 찾을 수 없습니다.");
            return;
        }
        canvasLifecycle.remove(stations.blankOwnerId(station.id()));
        canvasWorkflow.create(
                player.getUniqueId(),
                origin,
                station.canvas().facing(),
                station.canvas().right(),
                station.canvas().width(),
                station.canvas().height()
        );
        stations.occupy(station.id(), player.getUniqueId());
        pendingControlRemoveConfirms.remove(player.getUniqueId());
        if (paintPanelModes != null) {
            paintPanelModes.applyInteractionRangeOnly(player);
        }
        toolGiver.accept(player);
        showStationUi(player, station);
        player.sendMessage(ChatColor.GREEN + "수동 캔버스를 사용합니다.");
    }

    private void endStation(Player player, ManualStation station, boolean removeCanvas, boolean restoreBlank) {
        pendingControlRemoveConfirms.remove(player.getUniqueId());
        if (removeCanvas && canvasWorkflow.hasCanvas(player.getUniqueId())) {
            canvasWorkflow.remove(player.getUniqueId());
        }
        if (artworkGalleryWorkflow != null) {
            artworkGalleryWorkflow.end(player);
        }
        if (paintPanels != null) {
            paintPanels.clear(player.getUniqueId());
        }
        if (paintPanelModes != null && paintPanelModes.contains(player.getUniqueId())) {
            paintPanelModes.end(player, true);
        } else if (paintPanelModes != null) {
            paintPanelModes.restoreInteractionRangeOnly(player);
        }
        stations.release(station.id());
        if (restoreBlank) {
            stations.station(station.id()).ifPresent(this::spawnBlankCanvas);
        }
    }

    private void showStationUi(Player player, ManualStation station) {
        Location galleryLocation = stations.panelLocation(station.gallery());
        if (galleryLocation != null && artworkGalleryWorkflow != null) {
            artworkGalleryWorkflow.startManualStationGallery(player, galleryLocation, station.gallery().facing());
        }
        Location controlLocation = stations.panelLocation(station.control());
        if (controlLocation != null && paintPanels != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stations.occupiedBy(player.getUniqueId()).isPresent()) {
                    showStationControlPanel(player, station, pendingControlRemoveConfirms.contains(player.getUniqueId()));
                }
            });
        }
    }

    private void showStationControlPanel(Player player, ManualStation station, boolean confirmRemove) {
        Location controlLocation = stations.panelLocation(station.control());
        if (controlLocation == null || paintPanels == null) {
            return;
        }
        paintPanels.showManualControlPanel(
                player.getUniqueId(),
                controlLocation,
                station.control().facing(),
                station.control().vertical(),
                confirmRemove
        );
    }

    private void spawnBlankCanvas(ManualStation station) {
        StationCanvasSlot slot = station.canvas();
        if (slot == null) {
            return;
        }
        UUID blankOwnerId = stations.blankOwnerId(station.id());
        if (canvasLifecycle.hasCanvas(blankOwnerId)) {
            return;
        }
        Location origin = stations.canvasLocation(slot);
        if (origin == null) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        PixelCanvas pixelCanvas = new PixelCanvas(
                slot.width() * pixelsPerBlock,
                slot.height() * pixelsPerBlock,
                slot.width(),
                slot.height(),
                mapSize,
                drawScale,
                0,
                0,
                backgroundColor
        );
        canvasLifecycle.create(blankOwnerId, pixelCanvas, origin, slot.facing(), slot.right(), canvasMapRenderer::createMapItem);
    }

    private String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
