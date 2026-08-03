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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

public final class FreeModeDraftStorageService {
    private static final String DIRECTORY_NAME = "free-drafts";
    private static final String FILE_SUFFIX = ".draft.dat";
    private static final String MAGIC = "PFD1";

    private final Paint plugin;
    private final int pixelsPerBlock;
    private final int maxCanvasBlockSize;

    public FreeModeDraftStorageService(Paint plugin, int pixelsPerBlock, int maxCanvasBlockSize) {
        this.plugin = plugin;
        this.pixelsPerBlock = pixelsPerBlock;
        this.maxCanvasBlockSize = maxCanvasBlockSize;
    }

    public boolean exists(UUID playerId) {
        return draftFile(playerId).isFile();
    }

    public void save(
            UUID playerId,
            int blockWidth,
            int blockHeight,
            int pixelWidth,
            int pixelHeight,
            PixelCanvas.LayerSnapshot snapshot,
            UUID editingArtworkId
    ) throws IOException {
        validateDimensions(blockWidth, blockHeight, pixelWidth, pixelHeight, playerId.toString());
        File target = draftFile(playerId);
        File directory = target.getParentFile();
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create " + directory.getPath());
        }

        File temporary = new File(directory, target.getName() + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
                output.writeUTF(MAGIC);
                output.writeLong(playerId.getMostSignificantBits());
                output.writeLong(playerId.getLeastSignificantBits());
                output.writeInt(blockWidth);
                output.writeInt(blockHeight);
                output.writeInt(pixelWidth);
                output.writeInt(pixelHeight);
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

    public Optional<Draft> load(UUID playerId) throws IOException {
        File file = draftFile(playerId);
        if (!file.isFile()) {
            return Optional.empty();
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (!MAGIC.equals(input.readUTF())) {
                throw new IOException("Unsupported free-mode draft format: " + file.getPath());
            }
            UUID storedPlayerId = new UUID(input.readLong(), input.readLong());
            if (!playerId.equals(storedPlayerId)) {
                throw new IOException("Free-mode draft identity mismatch: " + file.getPath());
            }

            int blockWidth = input.readInt();
            int blockHeight = input.readInt();
            int pixelWidth = input.readInt();
            int pixelHeight = input.readInt();
            validateDimensions(blockWidth, blockHeight, pixelWidth, pixelHeight, file.getPath());

            UUID editingArtworkId = null;
            if (input.readBoolean()) {
                editingArtworkId = new UUID(input.readLong(), input.readLong());
            }
            PixelCanvas.LayerSnapshot snapshot = readLayerSnapshot(input, pixelWidth, pixelHeight, file.getPath());
            return Optional.of(new Draft(
                    blockWidth,
                    blockHeight,
                    pixelWidth,
                    pixelHeight,
                    snapshot,
                    editingArtworkId
            ));
        }
    }

    public boolean delete(UUID playerId) throws IOException {
        return Files.deleteIfExists(draftFile(playerId).toPath());
    }

    private void validateDimensions(
            int blockWidth,
            int blockHeight,
            int pixelWidth,
            int pixelHeight,
            String source
    ) throws IOException {
        if (blockWidth < 1 || blockWidth > maxCanvasBlockSize
                || blockHeight < 1 || blockHeight > maxCanvasBlockSize
                || pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IOException("Invalid free-mode draft dimensions: " + source);
        }
        try {
            int expectedPixelWidth = Math.multiplyExact(blockWidth, pixelsPerBlock);
            int expectedPixelHeight = Math.multiplyExact(blockHeight, pixelsPerBlock);
            Math.multiplyExact(pixelWidth, pixelHeight);
            if (pixelWidth != expectedPixelWidth || pixelHeight != expectedPixelHeight) {
                throw new IOException("Free-mode draft pixel dimensions do not match block dimensions: " + source);
            }
        } catch (ArithmeticException e) {
            throw new IOException("Invalid free-mode draft dimensions: " + source, e);
        }
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
            int pixelWidth,
            int pixelHeight,
            String source
    ) throws IOException {
        int expectedPixelCount;
        try {
            expectedPixelCount = Math.multiplyExact(pixelWidth, pixelHeight);
        } catch (ArithmeticException e) {
            throw new IOException("Invalid free-mode draft dimensions: " + source, e);
        }

        int layerCount = input.readInt();
        int pixelCount = input.readInt();
        int activeLayerCount = input.readInt();
        int activeLayerIndex = input.readInt();
        if (layerCount != PixelCanvas.LAYER_COUNT
                || pixelCount != expectedPixelCount
                || activeLayerCount < 1
                || activeLayerCount > PixelCanvas.LAYER_COUNT
                || activeLayerIndex < 0
                || activeLayerIndex >= activeLayerCount) {
            throw new IOException("Invalid free-mode draft layer dimensions: " + source);
        }

        Color[][] layers = new Color[layerCount][pixelCount];
        boolean[] visible = new boolean[layerCount];
        int[] opacityPercent = new int[layerCount];
        int[] labels = new int[layerCount];
        for (int layer = 0; layer < layerCount; layer++) {
            visible[layer] = input.readBoolean();
            opacityPercent[layer] = input.readInt();
            if (opacityPercent[layer] < 0 || opacityPercent[layer] > 100) {
                throw new IOException("Invalid free-mode draft layer opacity: " + source);
            }
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

    private File draftFile(UUID playerId) {
        return new File(new File(plugin.getDataFolder(), DIRECTORY_NAME), playerId + FILE_SUFFIX);
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

    public record Draft(
            int blockWidth,
            int blockHeight,
            int pixelWidth,
            int pixelHeight,
            PixelCanvas.LayerSnapshot snapshot,
            UUID editingArtworkId
    ) {
    }
}
