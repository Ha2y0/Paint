package org.ha2yo.paint.service;

import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PixelCanvas;
import org.ha2yo.paint.model.PlayerCanvas;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public final class ArtworkImageService {
    private final int mapSize;
    private final Color backgroundColor;

    public ArtworkImageService(int mapSize, Color backgroundColor) {
        this.mapSize = mapSize;
        this.backgroundColor = backgroundColor;
    }

    public BufferedImage thumbnail(File imageFile) throws IOException {
        BufferedImage source = ImageIO.read(imageFile);
        if (source == null) {
            throw new IOException("Could not decode " + imageFile.getName());
        }
        return thumbnailImage(source);
    }

    public void restoreToCanvas(
            PaintArtwork artwork,
            File imageFile,
            PlayerCanvas canvas,
            Optional<PixelCanvas.LayerSnapshot> layerSnapshot
    ) throws IOException {
        BufferedImage source = ImageIO.read(imageFile);
        if (source == null) {
            throw new IOException("Could not decode " + artwork.imagePath());
        }

        if (layerSnapshot.isPresent()) {
            canvas.pixelCanvas().restore(layerSnapshot.get());
        } else {
            Color[] pixels = artworkPixels(source, canvas.pixelCanvas().width(), canvas.pixelCanvas().height());
            canvas.pixelCanvas().restore(pixels);
        }
        canvas.undoSnapshots().clear();
        canvas.redoSnapshots().clear();
        canvas.resetSentTileVersions();
    }

    private Color[] artworkPixels(BufferedImage source, int width, int height) {
        Color[] pixels = new Color[width * height];
        double sourceScaleX = source.getWidth() / (double) width;
        double sourceScaleY = source.getHeight() / (double) height;
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(source.getHeight() - 1, (int) Math.floor((y + 0.5D) * sourceScaleY));
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(source.getWidth() - 1, (int) Math.floor((x + 0.5D) * sourceScaleX));
                int argb = source.getRGB(sourceX, sourceY);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 16) {
                    continue;
                }
                pixels[y * width + x] = new Color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
            }
        }
        return pixels;
    }

    private BufferedImage thumbnailImage(BufferedImage source) {
        BufferedImage preview = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.setColor(backgroundColor);
        graphics.fillRect(0, 0, mapSize, mapSize);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double scale = Math.min(mapSize / (double) source.getWidth(), mapSize / (double) source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (mapSize - width) / 2;
        int y = (mapSize - height) / 2;
        graphics.drawImage(source, x, y, width, height, null);
        graphics.dispose();
        return preview;
    }
}
