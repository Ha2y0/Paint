package org.ha2yo.paint.workflow;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.session.ArtworkPreviewSession;
import org.ha2yo.paint.renderer.GalleryImageMapRenderer;
import org.ha2yo.paint.service.ArtworkGalleryService;
import org.ha2yo.paint.service.ArtworkImageService;
import org.ha2yo.paint.service.ArtworkPreviewService;
import org.ha2yo.paint.service.ArtworkStorageService;
import org.ha2yo.paint.service.PaintMenuService;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ArtworkGalleryWorkflowService {
    private final Paint plugin;
    private final ArtworkGalleryService galleries;
    private final ArtworkStorageService storage;
    private final ArtworkImageService images;
    private final PaintMenuService menus;
    private final int mapSize;
    private final int canvasPixelsPerBlock;
    private final int maxCanvasBlockSize;
    private final String searchInputKey;
    private final Color backgroundColor;
    private final Function<Player, Location> anchorResolver;
    private final Function<Player, BlockFace> facingResolver;
    private final Consumer<Player> paintPanelStarter;
    private final Consumer<Player> paintPanelEnder;
    private final ArtworkPlacementStarter artworkPlacementStarter;
    private final FrameSelectionStarter frameSelectionStarter;
    private final CanvasPlacementStarter canvasPlacementStarter;
    private final Function<UUID, PlayerCanvas> canvasResolver;
    private final BiConsumer<UUID, UUID> editingArtworkSetter;
    private final Consumer<PlayerCanvas> canvasMapSender;
    private final Consumer<PlayerCanvas> layerPanelUpdater;
    private final BooleanSupplier shaderRgbEnabled;
    private final Map<UUID, ItemStack> previewItems = new HashMap<>();
    private final Map<UUID, ExhibitFrameStyle> selectedFrameStyles = new HashMap<>();

    public ArtworkGalleryWorkflowService(
            Paint plugin,
            ArtworkGalleryService galleries,
            ArtworkStorageService storage,
            ArtworkImageService images,
            PaintMenuService menus,
            int mapSize,
            int canvasPixelsPerBlock,
            int maxCanvasBlockSize,
            String searchInputKey,
            Color backgroundColor,
            Function<Player, Location> anchorResolver,
            Function<Player, BlockFace> facingResolver,
            Consumer<Player> paintPanelStarter,
            Consumer<Player> paintPanelEnder,
            ArtworkPlacementStarter artworkPlacementStarter,
            FrameSelectionStarter frameSelectionStarter,
            CanvasPlacementStarter canvasPlacementStarter,
            Function<UUID, PlayerCanvas> canvasResolver,
            BiConsumer<UUID, UUID> editingArtworkSetter,
            Consumer<PlayerCanvas> canvasMapSender,
            Consumer<PlayerCanvas> layerPanelUpdater,
            BooleanSupplier shaderRgbEnabled
    ) {
        this.plugin = plugin;
        this.galleries = galleries;
        this.storage = storage;
        this.images = images;
        this.menus = menus;
        this.mapSize = mapSize;
        this.canvasPixelsPerBlock = canvasPixelsPerBlock;
        this.maxCanvasBlockSize = maxCanvasBlockSize;
        this.searchInputKey = searchInputKey;
        this.backgroundColor = backgroundColor;
        this.anchorResolver = anchorResolver;
        this.facingResolver = facingResolver;
        this.paintPanelStarter = paintPanelStarter;
        this.paintPanelEnder = paintPanelEnder;
        this.artworkPlacementStarter = artworkPlacementStarter;
        this.frameSelectionStarter = frameSelectionStarter;
        this.canvasPlacementStarter = canvasPlacementStarter;
        this.canvasResolver = canvasResolver;
        this.editingArtworkSetter = editingArtworkSetter;
        this.canvasMapSender = canvasMapSender;
        this.layerPanelUpdater = layerPanelUpdater;
        this.shaderRgbEnabled = shaderRgbEnabled;
    }

    public void start(Player player, boolean exhibitMode) {
        paintPanelStarter.accept(player);
        galleries.start(player, exhibitMode, anchorResolver.apply(player), facingResolver.apply(player));
    }

    public void startAt(Player player, boolean exhibitMode, Location location, BlockFace facing) {
        paintPanelStarter.accept(player);
        galleries.start(player, exhibitMode, location, facing);
    }

    public void startAtWithoutPanelMode(Player player, boolean exhibitMode, Location location, BlockFace facing) {
        galleries.start(player, exhibitMode, location, facing);
    }

    public void startManualStationGallery(Player player, Location location, BlockFace facing) {
        galleries.startManualStation(player, location, facing);
    }

    public void openForOwnerName(Player player, String ownerName) {
        paintPanelStarter.accept(player);
        galleries.openForOwnerName(player, ownerName, anchorResolver.apply(player), facingResolver.apply(player));
    }

    public void showCurrent(Player player) {
        galleries.show(player);
    }

    public void applySearch(Player player, String rawQuery) {
        galleries.applySearch(player, rawQuery);
    }

    public void clearSearch(Player player) {
        galleries.clearSearch(player);
    }

    public boolean hasSession(UUID playerId) {
        return galleries.hasSession(playerId);
    }

    public boolean handleLookedClick(Player player) {
        return handleLookedClick(player, false);
    }

    public boolean handleLookedLeftClick(Player player) {
        return handleLookedClick(player, true);
    }

    private boolean handleLookedClick(Player player, boolean leftClick) {
        if (!galleries.hasSession(player.getUniqueId())) {
            return false;
        }
        ArtworkGalleryService.ClickResult result = galleries.handleLookedClick(player, leftClick);
        if (result.action() == ArtworkGalleryService.ClickAction.NONE) {
            return false;
        }
        handleResult(player, result);
        return true;
    }

    public boolean isLooking(Player player) {
        return galleries.isLooking(player);
    }

    public void updateTooltip(Player player) {
        galleries.updateTooltip(player);
    }

    public boolean handleFrameClick(Player player, UUID frameId) {
        ArtworkGalleryService.ClickResult result = galleries.handleFrameClick(player, frameId);
        if (result.action() == ArtworkGalleryService.ClickAction.NONE) {
            return false;
        }
        handleResult(player, result);
        return true;
    }

    public boolean handleFrameLeftClick(Player player, UUID frameId) {
        ArtworkGalleryService.ClickResult result = galleries.handleFrameClick(player, frameId, true);
        if (result.action() == ArtworkGalleryService.ClickAction.NONE) {
            return false;
        }
        handleResult(player, result);
        return true;
    }

    public void end(Player player) {
        selectedFrameStyles.remove(player.getUniqueId());
        galleries.end(player);
    }

    public void clearAll() {
        previewItems.clear();
        selectedFrameStyles.clear();
        galleries.clearAll();
    }

    public void clearCachedPreview(UUID artworkId) {
        previewItems.remove(artworkId);
    }

    public void openInventoryList(Player player, boolean exhibitMode) {
        List<PaintArtwork> artworks = storage.listByOwner(player.getUniqueId());
        if (artworks.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "아직 저장된 그림이 없습니다.");
        }
        menus.openArtworkList(player, artworks, exhibitMode, artwork -> createPreviewItem(player.getWorld(), artwork));
    }

    public void handleInventoryClick(Player player, ItemStack item) {
        PaintMenuService.MenuAction action = menus.actionFrom(item);
        if (action == null) {
            return;
        }

        switch (action) {
            case BACK -> menus.openMainMenu(player);
            case ARTWORK_INFO -> {
                UUID artworkId = menus.artworkIdFrom(item);
                if (artworkId == null) {
                    return;
                }
                storage.find(artworkId).ifPresent(artwork -> player.sendMessage(ChatColor.GRAY
                        + "저장 파일: " + ChatColor.WHITE + artwork.imagePath()));
            }
            case ARTWORK_SHOW -> showArtwork(player, item);
            default -> {
            }
        }
    }

    public void showSearchDialog(Player player) {
        UUID playerId = player.getUniqueId();
        ArtworkPreviewSession session = galleries.session(playerId);
        if (session == null) {
            return;
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("그림 검색"))
                        .externalTitle(Component.text("그림 검색"))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .inputs(List.of(DialogInput.text(searchInputKey, Component.text("검색어"))
                                .initial(session.searchQuery())
                                .maxLength(32)
                                .width(260)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("검색"))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) -> {
                                    String query = response.getText(searchInputKey);
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        Player current = plugin.getServer().getPlayer(playerId);
                                        if (current != null) {
                                            applySearch(current, query);
                                        }
                                    });
                                }, ClickCallback.Options.builder().build()))
                                .build(),
                        ActionButton.builder(Component.text("취소"))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) -> {
                                }, ClickCallback.Options.builder().build()))
                                .build())));
        player.showDialog(dialog);
    }

    private void handleResult(Player player, ArtworkGalleryService.ClickResult result) {
        switch (result.action()) {
            case NONE, HANDLED -> {
            }
            case SEARCH -> showSearchDialog(player);
            case CLOSE -> paintPanelEnder.accept(player);
            case EDIT -> editCurrent(player);
            case FRAME_SELECT -> selectFrameStyle(player);
            case EXHIBIT -> exhibitCurrent(player);
            case DELETE -> deleteCurrent(player);
        }
    }

    private void selectFrameStyle(Player player) {
        UUID playerId = player.getUniqueId();
        ArtworkPreviewSession session = galleries.session(playerId);
        if (session == null || session.artworks().isEmpty()) {
            return;
        }
        selectedFrameStyles.remove(playerId);
        galleries.end(player);
        paintPanelEnder.accept(player);
        frameSelectionStarter.start(
                player,
                session.selectedArtwork(),
                ExhibitFrameStyle.NONE,
                style -> {
                },
                player1 -> {
                }
        );
    }

    private void resumeGalleryAfterFrameSelection(Player player) {
        if (galleries.session(player.getUniqueId()) == null) {
            return;
        }
        paintPanelStarter.accept(player);
        galleries.show(player);
    }

    private void exhibitCurrent(Player player) {
        UUID playerId = player.getUniqueId();
        ArtworkPreviewSession session = galleries.session(playerId);
        if (session == null || session.artworks().isEmpty()) {
            return;
        }

        paintPanelEnder.accept(player);
        artworkPlacementStarter.start(player, session.selectedArtwork(), selectedFrameStyles.getOrDefault(playerId, session.exhibitFrameStyle()));
    }

    private void editCurrent(Player player) {
        UUID playerId = player.getUniqueId();
        ArtworkPreviewSession session = galleries.session(playerId);
        if (session == null || session.artworks().isEmpty()) {
            return;
        }
        if (session.remoteGallery()) {
            player.sendMessage(ChatColor.RED + "다른 플레이어 갤러리에서는 그림을 편집할 수 없습니다.");
            return;
        }

        PaintArtwork artwork = session.selectedArtwork();
        PlayerCanvas currentCanvas = canvasResolver.apply(playerId);
        if (!canEditIntoCanvas(player, artwork, currentCanvas)) {
            return;
        }

        if (session.manualStationGallery()) {
            try {
                if (currentCanvas == null) {
                    player.sendMessage(ChatColor.RED + "불러올 수동 캔버스를 찾을 수 없습니다.");
                    return;
                }
                loadArtworkIntoCanvas(player, artwork, currentCanvas);
                player.sendMessage(ChatColor.GREEN + "선택한 그림을 수동 캔버스로 불러왔습니다.");
            } catch (IOException e) {
                plugin.getLogger().warning("Could not load Paint artwork into manual canvas: " + e.getMessage());
                player.sendMessage(ChatColor.RED + "그림을 수동 캔버스로 불러오지 못했습니다. 파일과 콘솔 로그를 확인해 주세요.");
            }
            return;
        }

        int blockWidth = artworkBlockWidth(artwork);
        int blockHeight = artworkBlockHeight(artwork);
        paintPanelEnder.accept(player);
        end(player);
        canvasPlacementStarter.start(player, blockWidth, blockHeight, current -> {
            paintPanelEnder.accept(player);
            PlayerCanvas canvas = canvasResolver.apply(current.getUniqueId());
            if (canvas == null) {
                current.sendMessage(ChatColor.RED + "설치된 캔버스를 찾을 수 없어 그림을 불러오지 못했습니다.");
                return;
            }
            if (!canEditIntoCanvas(current, artwork, canvas)) {
                return;
            }
            try {
                loadArtworkIntoCanvas(current, artwork, canvas);
                current.sendMessage(ChatColor.GREEN + "저장된 그림을 캔버스로 불러왔습니다.");
            } catch (IOException e) {
                plugin.getLogger().warning("Could not edit Paint artwork: " + e.getMessage());
                current.sendMessage(ChatColor.RED + "그림을 편집할 캔버스로 불러오지 못했습니다. 파일과 콘솔 로그를 확인해 주세요.");
            }
        });
    }

    private boolean canEditIntoCanvas(Player player, PaintArtwork artwork, PlayerCanvas canvas) {
        if (canvas == null) {
            return true;
        }
        int canvasBlockWidth = canvas.pixelCanvas().blockWidth();
        int canvasBlockHeight = canvas.pixelCanvas().blockHeight();
        int artworkBlockWidth = artworkBlockWidth(artwork);
        int artworkBlockHeight = artworkBlockHeight(artwork);
        if (canvasBlockWidth * artworkBlockHeight != canvasBlockHeight * artworkBlockWidth) {
            player.sendMessage(ChatColor.RED + "캔버스 비율과 그림 비율이 달라 불러올 수 없습니다. "
                    + ChatColor.GRAY + "(캔버스: " + canvasBlockWidth + "x" + canvasBlockHeight
                    + ", 그림: " + artworkBlockWidth + "x" + artworkBlockHeight + ")");
            return false;
        }
        if (!canvas.pixelCanvas().hasPaintedPixels()) {
            return true;
        }
        player.sendMessage(ChatColor.RED + "캔버스가 비어 있을 때만 편집할 수 있습니다.");
        return false;
    }

    private void loadArtworkIntoCanvas(Player player, PaintArtwork artwork, PlayerCanvas canvas) throws IOException {
        editingArtworkSetter.accept(player.getUniqueId(), artwork.id());

        Optional<PixelCanvas.LayerSnapshot> layerSnapshot;
        try {
            layerSnapshot = storage.loadLayerSnapshot(artwork);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load Paint artwork layers: " + e.getMessage());
            layerSnapshot = Optional.empty();
        }
        if (canvas.pixelCanvas().width() != artwork.width() || canvas.pixelCanvas().height() != artwork.height()) {
            layerSnapshot = Optional.empty();
        }
        images.restoreToCanvas(artwork, storage.imageFile(artwork), canvas, layerSnapshot);
        canvasMapSender.accept(canvas);
        layerPanelUpdater.accept(canvas);
    }

    private void deleteCurrent(Player player) {
        UUID playerId = player.getUniqueId();
        ArtworkPreviewSession session = galleries.session(playerId);
        if (session == null || session.artworks().isEmpty()) {
            return;
        }
        if (session.remoteGallery()) {
            player.sendMessage(ChatColor.RED + "다른 플레이어 갤러리에서는 그림을 삭제할 수 없습니다.");
            return;
        }

        PaintArtwork selected = session.selectedArtwork();
        if (!session.deleteConfirmActiveFor(selected.id())) {
            galleries.replaceAndShow(player, session.withDeleteConfirm(selected.id()));
            player.sendMessage(ChatColor.RED + "삭제하려면 버튼을 한 번 더 눌러 주세요.");
            return;
        }

        try {
            storage.delete(player.getUniqueId(), selected.id());
            clearCachedPreview(selected.id());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete Paint artwork: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "그림 삭제에 실패했습니다. 콘솔 로그를 확인해 주세요.");
            return;
        }

        List<PaintArtwork> refreshed = storage.listByOwner(playerId);
        if (refreshed.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "그림을 삭제했습니다. 이제 저장된 그림이 없습니다.");
            end(player);
            return;
        }

        String query = session.searchQuery();
        List<PaintArtwork> visible = galleries.filterArtworks(refreshed, query);
        if (visible.isEmpty()) {
            query = "";
            visible = refreshed;
            player.sendMessage(ChatColor.YELLOW + "검색 결과가 없어 전체 목록으로 돌아갑니다.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "그림을 삭제했습니다.");
        }

        int page = Math.min(session.page(), ArtworkPreviewService.pageCount(visible) - 1);
        int selectedIndex = Math.min(Math.max(ArtworkPreviewService.firstArtworkIndexOnPage(visible, page), session.selectedIndex()), visible.size() - 1);
        galleries.replaceAndShow(player, new ArtworkPreviewSession(
                        refreshed,
                        visible,
                        page,
                        selectedIndex,
                        session.exhibitMode(),
                        session.anchor(),
                        session.facing(),
                        null,
                        query,
                        session.galleryOwnerId(),
                        session.galleryOwnerName(),
                        session.remoteGallery(),
                        session.manualStationGallery(),
                        session.exhibitFrameStyle()
                )
        );
    }

    private ItemStack createPreviewItem(World world, PaintArtwork artwork) {
        ItemStack cached = previewItems.get(artwork.id());
        if (cached != null) {
            return cached.clone();
        }

        try {
            BufferedImage preview = images.thumbnail(storage.imageFile(artwork));
            MapView mapView = plugin.getServer().createMap(world);
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);
            for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
                mapView.removeRenderer(renderer);
            }
            mapView.addRenderer(new GalleryImageMapRenderer(preview, 0, 0, mapSize, backgroundColor, shaderRgbEnabled.getAsBoolean()));

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) item.getItemMeta();
            meta.setMapView(mapView);
            item.setItemMeta(meta);
            previewItems.put(artwork.id(), item.clone());
            return item;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create Paint artwork preview: " + e.getMessage());
            return null;
        }
    }

    private void showArtwork(Player player, ItemStack item) {
        UUID artworkId = menus.artworkIdFrom(item);
        if (artworkId == null) {
            return;
        }
        Optional<PaintArtwork> found = storage.find(artworkId);
        if (found.isEmpty() || !found.get().ownerId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "전시할 그림을 찾을 수 없습니다.");
            return;
        }

        player.closeInventory();
        artworkPlacementStarter.start(player, found.get(), ExhibitFrameStyle.NONE);
    }

    private int artworkBlockWidth(PaintArtwork artwork) {
        return Math.max(1, Math.min(maxCanvasBlockSize, (int) Math.ceil(artwork.width() / (double) canvasPixelsPerBlock)));
    }

    private int artworkBlockHeight(PaintArtwork artwork) {
        return Math.max(1, Math.min(maxCanvasBlockSize, (int) Math.ceil(artwork.height() / (double) canvasPixelsPerBlock)));
    }

    @FunctionalInterface
    public interface CanvasPlacementStarter {
        void start(Player player, int blockWidth, int blockHeight, Consumer<Player> placementCompletion);
    }

    @FunctionalInterface
    public interface FrameSelectionStarter {
        void start(
                Player player,
                PaintArtwork artwork,
                ExhibitFrameStyle frameStyle,
                Consumer<ExhibitFrameStyle> frameStyleHandler,
                Consumer<Player> frameSelectionEndHandler
        );
    }

    @FunctionalInterface
    public interface ArtworkPlacementStarter {
        void start(Player player, PaintArtwork artwork, ExhibitFrameStyle frameStyle);
    }
}
