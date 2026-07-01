package org.ha2yo.paint.workflow;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PaintExhibit;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.session.LookedExhibit;
import org.ha2yo.paint.service.ArtworkDisplayService;
import org.ha2yo.paint.service.ArtworkStorageService;

import java.io.IOException;
import java.util.Optional;

public final class ExhibitRemovalWorkflowService {
    private final ArtworkDisplayService artworkDisplays;
    private final ArtworkStorageService artworkStorage;
    private final double interactionDistance;

    public ExhibitRemovalWorkflowService(
            ArtworkDisplayService artworkDisplays,
            ArtworkStorageService artworkStorage,
            double interactionDistance
    ) {
        this.artworkDisplays = artworkDisplays;
        this.artworkStorage = artworkStorage;
        this.interactionDistance = interactionDistance;
    }

    public LookedExhibit lookedExhibit(Player player) {
        if (artworkDisplays == null) {
            return null;
        }
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                interactionDistance,
                entity -> artworkDisplays.isDisplayEntity(entity.getUniqueId())
        );
        if (hit == null || hit.getHitEntity() == null) {
            return null;
        }
        Optional<PaintExhibit> exhibit = artworkDisplays.findByEntity(hit.getHitEntity().getUniqueId());
        if (exhibit.isEmpty()) {
            return null;
        }
        PaintArtwork artwork = artworkStorage.find(exhibit.get().artworkId()).orElse(null);
        return new LookedExhibit(hit.getHitEntity().getUniqueId(), exhibit.get(), artwork);
    }

    public boolean canRemoveExhibit(Player player, LookedExhibit looked) {
        return player.isOp()
                || player.hasPermission("paint.admin")
                || (looked.artwork() != null && looked.artwork().ownerId().equals(player.getUniqueId()));
    }

    public boolean canRemoveCanvas(Player player) {
        return player.isOp() || player.hasPermission("paint.admin");
    }

    public boolean removeExhibit(LookedExhibit looked) throws IOException {
        return artworkDisplays.removeById(looked.exhibit().id());
    }

    public String exhibitInfoText(LookedExhibit looked) {
        return "그림: " + exhibitTitle(looked) + " / 작가: " + exhibitOwnerName(looked);
    }

    public String canvasRemovalInfoText(PlayerCanvas canvas) {
        return "캔버스: " + canvasOwnerName(canvas.ownerId());
    }

    private String exhibitTitle(LookedExhibit looked) {
        return looked.artwork() == null ? looked.exhibit().imagePath() : looked.artwork().displayName();
    }

    private String exhibitOwnerName(LookedExhibit looked) {
        return looked.artwork() == null ? "알 수 없음" : looked.artwork().ownerName();
    }

    private String canvasOwnerName(java.util.UUID ownerId) {
        Player player = Bukkit.getPlayer(ownerId);
        if (player != null && !player.getName().isBlank()) {
            return player.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(ownerId).getName();
        return offlineName == null || offlineName.isBlank() ? "알 수 없음" : offlineName;
    }
}
