package org.ha2yo.paint.service;

import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.PixelCanvas;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

public final class ManualStationDraftStorageService {
    private static final String DIRECTORY_NAME = "manual-drafts";
    private static final String FILE_SUFFIX = ".draft.dat";
    private static final String MAGIC = "PMD1";

    private final Paint plugin;

    public ManualStationDraftStorageService(Paint plugin) {
        this.plugin = plugin;
    }

    public void save(
            String stationId,
            UUID playerId,
            int width,
            int height,
            PixelCanvas.LayerSnapshot snapshot,
            UUID editingArtworkId
    ) throws IOException {
        File target = draftFile(stationId, playerId);
        File directory = target.getParentFile();
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create " + directory.getPath());
        }

        File temporary = new File(directory, target.getName() + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
                output.writeUTF(MAGIC);
                output.writeUTF(stationId);
                output.writeLong(playerId.getMostSignificantBits());
                output.writeLong(playerId.getLeastSignificantBits());
                output.writeInt(width);
                output.writeInt(height);
                output.writeBoolean(editingArtworkId != null);
                if (editingArtworkId != null) {
                    output.writeLong(editingArtworkId.getMostSignificantBits());
                    output.writeLong(editingArtworkId.getLeastSignificantBits());
                }
                writeLayerSnapshot(output, snapshot);
            }
            moveIntoPlace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    public Optional<Draft> load(String stationId, UUID playerId, int expectedWidth, int expectedHeight) throws IOException {
        File file = draftFile(stationId, playerId);
        if (!file.isFile()) {
            return Optional.empty();
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (!MAGIC.equals(input.readUTF())) {
                throw new IOException("Unsupported manual draft format: " + file.getPath());
            }
            String storedStationId = input.readUTF();
            UUID storedPlayerId = new UUID(input.readLong(), input.readLong());
            int width = input.readInt();
            int height = input.readInt();
            if (!stationId.equals(storedStationId) || !playerId.equals(storedPlayerId)) {
                throw new IOException("Manual draft identity mismatch: " + file.getPath());
            }
            if (width != expectedWidth || height != expectedHeight) {
                throw new IOException("Manual draft canvas size mismatch: " + width + "x" + height
                        + " != " + expectedWidth + "x" + expectedHeight);
            }

            UUID editingArtworkId = null;
            if (input.readBoolean()) {
                editingArtworkId = new UUID(input.readLong(), input.readLong());
            }
            return Optional.of(new Draft(readLayerSnapshot(input, width, height, file.getPath()), editingArtworkId));
        }
    }

    public boolean delete(String stationId, UUID playerId) throws IOException {
        return Files.deleteIfExists(draftFile(stationId, playerId).toPath());
    }

    private void writeLayerSnapshot(DataOutputStream output, PixelCanvas.LayerSnapshot snapshot) throws IOException {
        output.writeInt(snapshot.layers().length);
        output.writeInt(snapshot.layers().length == 0 ? 0 : snapshot.layers()[0].length);
        output.writeInt(snapshot.activeLayerCount());
        output.writeInt(snapshot.activeLayerIndex());
        for (int layer = 0; layer < snapshot.layers().length; layer++) {
            output.writeBoolean(snapshot.visible()[layer]);
            output.writeInt(snapshot.opacityPercent()[layer]);
            output.writeInt(snapshot.labels()[layer]);
            for (Color color : snapshot.layers()[layer]) {
                output.writeBoolean(color != null);
                if (color != null) {
                    output.writeInt(color.getRGB());
                }
            }
        }
    }

    private PixelCanvas.LayerSnapshot readLayerSnapshot(
            DataInputStream input,
            int width,
            int height,
            String source
    ) throws IOException {
        int expectedPixelCount;
        try {
            expectedPixelCount = Math.multiplyExact(width, height);
        } catch (ArithmeticException e) {
            throw new IOException("Invalid manual draft dimensions: " + source, e);
        }

        int layerCount = input.readInt();
        int pixelCount = input.readInt();
        int activeLayerCount = input.readInt();
        int activeLayerIndex = input.readInt();
        if (layerCount != PixelCanvas.LAYER_COUNT || pixelCount != expectedPixelCount) {
            throw new IOException("Invalid manual draft layer dimensions: " + source);
        }

        Color[][] layers = new Color[layerCount][pixelCount];
        boolean[] visible = new boolean[layerCount];
        int[] opacityPercent = new int[layerCount];
        int[] labels = new int[layerCount];
        for (int layer = 0; layer < layerCount; layer++) {
            visible[layer] = input.readBoolean();
            opacityPercent[layer] = input.readInt();
            labels[layer] = input.readInt();
            for (int index = 0; index < pixelCount; index++) {
                if (input.readBoolean()) {
                    layers[layer][index] = new Color(input.readInt(), true);
                }
            }
        }
        return new PixelCanvas.LayerSnapshot(
                layers,
                visible,
                opacityPercent,
                labels,
                activeLayerCount,
                activeLayerIndex
        );
    }

    private File draftFile(String stationId, UUID playerId) {
        UUID stationKey = UUID.nameUUIDFromBytes(
                ("paint-manual-draft:" + stationId).getBytes(StandardCharsets.UTF_8)
        );
        return new File(new File(plugin.getDataFolder(), DIRECTORY_NAME), stationKey + "-" + playerId + FILE_SUFFIX);
    }

    private void moveIntoPlace(File source, File target) throws IOException {
        try {
            Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Draft(PixelCanvas.LayerSnapshot snapshot, UUID editingArtworkId) {
    }
}
