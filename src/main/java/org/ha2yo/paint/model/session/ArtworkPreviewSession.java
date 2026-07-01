package org.ha2yo.paint.model.session;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.ha2yo.paint.model.ExhibitFrameStyle;
import org.ha2yo.paint.model.PaintArtwork;

import java.util.List;
import java.util.UUID;

public record ArtworkPreviewSession(
        List<PaintArtwork> allArtworks,
        List<PaintArtwork> artworks,
        int page,
        int selectedIndex,
        boolean exhibitMode,
        Location anchor,
        BlockFace facing,
        UUID deleteConfirmArtworkId,
        String searchQuery,
        UUID galleryOwnerId,
        String galleryOwnerName,
        boolean remoteGallery,
        boolean manualStationGallery,
        ExhibitFrameStyle exhibitFrameStyle
) {
    public ArtworkPreviewSession(
            List<PaintArtwork> allArtworks,
            List<PaintArtwork> artworks,
            int page,
            int selectedIndex,
            boolean exhibitMode,
            Location anchor,
            BlockFace facing,
            UUID deleteConfirmArtworkId,
            String searchQuery,
            UUID galleryOwnerId,
            String galleryOwnerName,
            boolean remoteGallery
    ) {
        this(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, deleteConfirmArtworkId, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, false, ExhibitFrameStyle.NONE);
    }

    public ArtworkPreviewSession(
            List<PaintArtwork> allArtworks,
            List<PaintArtwork> artworks,
            int page,
            int selectedIndex,
            boolean exhibitMode,
            Location anchor,
            BlockFace facing,
            UUID deleteConfirmArtworkId,
            String searchQuery,
            UUID galleryOwnerId,
            String galleryOwnerName,
            boolean remoteGallery,
            boolean manualStationGallery
    ) {
        this(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, deleteConfirmArtworkId, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery, ExhibitFrameStyle.NONE);
    }

    public ArtworkPreviewSession {
        if (exhibitFrameStyle == null) {
            exhibitFrameStyle = ExhibitFrameStyle.NONE;
        }
    }

    public PaintArtwork selectedArtwork() {
        return artworks.get(Math.max(0, Math.min(selectedIndex, artworks.size() - 1)));
    }

    public ArtworkPreviewSession withSelectedIndex(int selectedIndex) {
        return new ArtworkPreviewSession(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, deleteConfirmArtworkId, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery, exhibitFrameStyle);
    }

    public ArtworkPreviewSession withPageAndSelected(int page, int selectedIndex) {
        return new ArtworkPreviewSession(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, deleteConfirmArtworkId, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery, exhibitFrameStyle);
    }

    public ArtworkPreviewSession withDeleteConfirm(UUID artworkId) {
        return new ArtworkPreviewSession(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, artworkId, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery, exhibitFrameStyle);
    }

    public ArtworkPreviewSession clearDeleteConfirm() {
        return new ArtworkPreviewSession(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, null, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery, exhibitFrameStyle);
    }

    public ArtworkPreviewSession withExhibitFrameStyle(ExhibitFrameStyle frameStyle) {
        return new ArtworkPreviewSession(allArtworks, artworks, page, selectedIndex, exhibitMode, anchor, facing, deleteConfirmArtworkId, searchQuery, galleryOwnerId, galleryOwnerName, remoteGallery, manualStationGallery, frameStyle);
    }

    public boolean deleteConfirmActive() {
        return deleteConfirmArtworkId != null;
    }

    public boolean deleteConfirmActiveFor(UUID artworkId) {
        return deleteConfirmArtworkId != null && deleteConfirmArtworkId.equals(artworkId);
    }
}
