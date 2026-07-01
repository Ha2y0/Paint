package org.ha2yo.paint.service;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.session.ArtworkPreviewClickStamp;
import org.ha2yo.paint.model.session.ArtworkPreviewSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ArtworkGalleryService {
    private final Paint plugin;
    private final ArtworkStorageService artworkStorage;
    private final ArtworkPreviewService previews;
    private final double interactionDistance;
    private final long openGraceMillis;
    private final long clickCooldownMillis;
    private final Map<UUID, ArtworkPreviewSession> sessions = new HashMap<>();
    private final Map<UUID, ArtworkPreviewClickStamp> lastClicks = new HashMap<>();
    private final Map<UUID, Long> interactionReadyTimes = new HashMap<>();

    public ArtworkGalleryService(
            Paint plugin,
            ArtworkStorageService artworkStorage,
            ArtworkPreviewService previews,
            double interactionDistance,
            long openGraceMillis,
            long clickCooldownMillis
    ) {
        this.plugin = plugin;
        this.artworkStorage = artworkStorage;
        this.previews = previews;
        this.interactionDistance = interactionDistance;
        this.openGraceMillis = openGraceMillis;
        this.clickCooldownMillis = clickCooldownMillis;
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ArtworkPreviewSession session(UUID playerId) {
        return sessions.get(playerId);
    }

    public void clearAll() {
        sessions.clear();
        lastClicks.clear();
        interactionReadyTimes.clear();
    }

    public boolean start(Player player, boolean exhibitMode, Location anchor, BlockFace facing) {
        List<PaintArtwork> artworks = artworkStorage.listByOwner(player.getUniqueId());
        return open(player, artworks, exhibitMode, player.getUniqueId(), player.getName(), false, false, anchor, facing);
    }

    public boolean startManualStation(Player player, Location anchor, BlockFace facing) {
        List<PaintArtwork> artworks = artworkStorage.listByOwner(player.getUniqueId());
        return open(player, artworks, false, player.getUniqueId(), player.getName(), false, true, anchor, facing);
    }

    public boolean openForOwnerName(Player player, String ownerName, Location anchor, BlockFace facing) {
        List<PaintArtwork> artworks = artworkStorage.listByOwnerName(ownerName);
        if (artworks.isEmpty()) {
            player.sendMessage(ChatColor.RED + "해당 플레이어가 저장한 그림이 없습니다.");
            return false;
        }

        PaintArtwork first = artworks.get(0);
        return open(player, artworks, false, first.ownerId(), first.ownerName(), !first.ownerId().equals(player.getUniqueId()), false, anchor, facing);
    }

    public boolean open(
            Player player,
            List<PaintArtwork> artworks,
            boolean exhibitMode,
            UUID galleryOwnerId,
            String galleryOwnerName,
            boolean remoteGallery,
            boolean manualStationGallery,
            Location anchor,
            BlockFace facing
    ) {
        if (artworks.isEmpty()) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "저장된 그림이 없습니다.");
            return false;
        }

        UUID playerId = player.getUniqueId();
        player.closeInventory();
        sessions.put(
                playerId,
                new ArtworkPreviewSession(artworks, artworks, 0, 0, exhibitMode, anchor, facing, null, "", galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery)
        );
        interactionReadyTimes.put(playerId, System.currentTimeMillis() + openGraceMillis);
        show(player);
        return true;
    }

    public void show(Player player) {
        ArtworkPreviewSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        try {
            previews.showGallery(
                    player,
                    session.anchor(),
                    session.facing(),
                    session.artworks(),
                    session.page(),
                    session.selectedIndex(),
                    session.deleteConfirmActive(),
                    session.searchQuery(),
                    session.manualStationGallery(),
                    artworkStorage::imageFile
            );
        } catch (IOException e) {
            plugin.getLogger().warning("Could not preview Paint artwork: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "그림 미리보기에 실패했습니다. 콘솔 로그를 확인해 주세요.");
        }
    }

    public void end(Player player) {
        UUID playerId = player.getUniqueId();
        sessions.remove(playerId);
        lastClicks.remove(playerId);
        interactionReadyTimes.remove(playerId);
        previews.clear(playerId);
    }

    public void hide(Player player) {
        UUID playerId = player.getUniqueId();
        lastClicks.remove(playerId);
        interactionReadyTimes.remove(playerId);
        previews.clear(playerId);
    }

    public void updateTooltip(Player player) {
        previews.updateTooltip(player, interactionDistance);
    }

    public boolean isLooking(Player player) {
        return hasSession(player.getUniqueId()) && previews.lookedClick(player, interactionDistance) != null;
    }

    public ClickResult handleLookedClick(Player player) {
        return handleLookedClick(player, false);
    }

    public ClickResult handleLookedClick(Player player, boolean leftClick) {
        if (!hasSession(player.getUniqueId())) {
            return ClickResult.none();
        }
        ArtworkPreviewService.PreviewClick click = previews.lookedClick(player, interactionDistance);
        if (click == null) {
            return ClickResult.none();
        }
        ClickResult result = handleClick(player, click, leftClick);
        return result.action() == ClickAction.NONE ? ClickResult.handled() : result;
    }

    public ClickResult handleFrameClick(Player player, UUID frameId) {
        return handleFrameClick(player, frameId, false);
    }

    public ClickResult handleFrameClick(Player player, UUID frameId, boolean leftClick) {
        ArtworkPreviewService.PreviewClick click = previews.click(frameId);
        if (click == null) {
            return ClickResult.none();
        }
        ClickResult result = handleClick(player, click, leftClick);
        return result.action() == ClickAction.NONE ? ClickResult.handled() : result;
    }

    public void applySearch(Player player, String rawQuery) {
        ArtworkPreviewSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        String query = sanitizeSearchQuery(rawQuery);
        if (query.isBlank()) {
            clearSearch(player);
            return;
        }

        replaceAndShow(player, new ArtworkPreviewSession(
                session.allArtworks(),
                filterArtworks(session.allArtworks(), query),
                0,
                0,
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
        ));
    }

    public void clearSearch(Player player) {
        ArtworkPreviewSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        replaceAndShow(player, new ArtworkPreviewSession(
                session.allArtworks(),
                session.allArtworks(),
                0,
                0,
                session.exhibitMode(),
                session.anchor(),
                session.facing(),
                null,
                "",
                session.galleryOwnerId(),
                session.galleryOwnerName(),
                session.remoteGallery(),
                session.manualStationGallery(),
                session.exhibitFrameStyle()
        ));
    }

    public void replaceAndShow(Player player, ArtworkPreviewSession session) {
        sessions.put(player.getUniqueId(), session);
        show(player);
    }

    public void updateExhibitFrameStyle(Player player, ExhibitFrameStyle frameStyle) {
        ArtworkPreviewSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        sessions.put(player.getUniqueId(), session.withExhibitFrameStyle(frameStyle));
    }

    public List<PaintArtwork> filterArtworks(List<PaintArtwork> artworks, String rawQuery) {
        String query = sanitizeSearchQuery(rawQuery).toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return artworks;
        }
        return artworks.stream()
                .filter(artwork -> artwork.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || artwork.imagePath().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private ClickResult handleClick(Player player, ArtworkPreviewService.PreviewClick click) {
        return handleClick(player, click, false);
    }

    private ClickResult handleClick(Player player, ArtworkPreviewService.PreviewClick click, boolean leftClick) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Long readyAt = interactionReadyTimes.get(playerId);
        if (readyAt != null && now < readyAt) {
            return ClickResult.none();
        }
        ArtworkPreviewClickStamp lastClick = lastClicks.get(playerId);
        if (lastClick != null
                && lastClick.matches(click)
                && now - lastClick.timeMillis() < clickCooldownMillis) {
            return ClickResult.none();
        }
        lastClicks.put(playerId, new ArtworkPreviewClickStamp(click.action(), click.artworkIndex(), now));

        ArtworkPreviewSession session = sessions.get(playerId);
        if (session == null) {
            return ClickResult.none();
        }

        return switch (click.action()) {
            case ARTWORK -> {
                if (click.artworkIndex() < 0 || click.artworkIndex() >= session.artworks().size()) {
                    yield ClickResult.none();
                }
                replaceAndShow(player, session.withSelectedIndex(click.artworkIndex()).clearDeleteConfirm());
                yield ClickResult.handled();
            }
            case PREVIOUS -> {
                int page = Math.floorMod(session.page() - 1, ArtworkPreviewService.pageCount(session.artworks()));
                int selected = ArtworkPreviewService.firstArtworkIndexOnPage(session.artworks(), page);
                replaceAndShow(player, session.withPageAndSelected(page, selected).clearDeleteConfirm());
                yield ClickResult.handled();
            }
            case NEXT -> {
                int page = Math.floorMod(session.page() + 1, ArtworkPreviewService.pageCount(session.artworks()));
                int selected = ArtworkPreviewService.firstArtworkIndexOnPage(session.artworks(), page);
                replaceAndShow(player, session.withPageAndSelected(page, selected).clearDeleteConfirm());
                yield ClickResult.handled();
            }
            case CLEAR_SEARCH -> {
                clearSearch(player);
                yield ClickResult.handled();
            }
            case SEARCH -> {
                session = clearDeleteConfirmIfActive(player, session);
                yield new ClickResult(ClickAction.SEARCH, session.selectedArtwork());
            }
            case CLOSE -> {
                if (session.manualStationGallery()) {
                    yield ClickResult.handled();
                }
                end(player);
                yield new ClickResult(ClickAction.CLOSE, null);
            }
            case EDIT -> {
                session = clearDeleteConfirmIfActive(player, session);
                yield new ClickResult(ClickAction.EDIT, session.selectedArtwork());
            }
            case EXHIBIT -> {
                if (session.manualStationGallery()) {
                    yield ClickResult.handled();
                }
                session = clearDeleteConfirmIfActive(player, session);
                yield new ClickResult(leftClick ? ClickAction.FRAME_SELECT : ClickAction.EXHIBIT, session.selectedArtwork());
            }
            case DELETE -> new ClickResult(ClickAction.DELETE, session.selectedArtwork());
        };
    }

    private ArtworkPreviewSession clearDeleteConfirmIfActive(Player player, ArtworkPreviewSession session) {
        if (!session.deleteConfirmActive()) {
            return session;
        }
        ArtworkPreviewSession cleared = session.clearDeleteConfirm();
        replaceAndShow(player, cleared);
        return cleared;
    }

    private String sanitizeSearchQuery(String rawQuery) {
        return rawQuery == null ? "" : rawQuery.trim().replaceAll("\\p{Cntrl}", "");
    }

    public enum ClickAction {
        NONE,
        HANDLED,
        SEARCH,
        CLOSE,
        EDIT,
        FRAME_SELECT,
        EXHIBIT,
        DELETE
    }

    public record ClickResult(ClickAction action, PaintArtwork artwork) {
        public static ClickResult none() {
            return new ClickResult(ClickAction.NONE, null);
        }

        public static ClickResult handled() {
            return new ClickResult(ClickAction.HANDLED, null);
        }
    }
}
