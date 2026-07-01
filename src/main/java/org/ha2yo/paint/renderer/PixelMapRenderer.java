package org.ha2yo.paint.renderer;

import org.ha2yo.paint.model.PixelCanvas;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

public final class PixelMapRenderer extends MapRenderer {
    private static final Color FRAME_COLOR = new Color(46, 52, 52);
    private static final LongAdder PERF_PREPARE_CALLS = new LongAdder();
    private static final LongAdder PERF_READY_HITS = new LongAdder();
    private static final LongAdder PERF_DUPLICATE_WAITS = new LongAdder();
    private static final LongAdder PERF_RENDER_SCHEDULED = new LongAdder();
    private static final LongAdder PERF_RENDER_COMPLETED = new LongAdder();
    private static final LongAdder PERF_RENDER_FAILED = new LongAdder();
    private static final LongAdder PERF_SNAPSHOT_NANOS = new LongAdder();
    private static final LongAdder PERF_RENDER_NANOS = new LongAdder();
    private static final AtomicLong PERF_MAX_SNAPSHOT_NANOS = new AtomicLong();
    private static final AtomicLong PERF_MAX_RENDER_NANOS = new AtomicLong();

    private final PixelCanvas canvas;
    private final UUID canvasId;
    private final UUID ownerId;
    private final int tileX;
    private final int tileY;
    private final int mapSize;
    private final int mapMarginX;
    private final int mapMarginY;
    private final int drawScale;
    private final int canvasPixelWidth;
    private final int canvasPixelHeight;
    private final Color backgroundColor;
    private final String ownerName;
    private final String themeTitle;
    private final boolean throttleNonOwner;
    private final BooleanSupplier shaderRgbSupplier;
    private final Function<Player, PreviewOverlay> previewOverlayProvider;
    private final Map<UUID, Integer> renderedVersions = new HashMap<>();
    private final Map<UUID, Integer> renderedVoteCounts = new HashMap<>();
    private final Map<UUID, Boolean> renderedShaderRgbStates = new HashMap<>();
    private final Map<UUID, Integer> renderedOverlayVersions = new HashMap<>();
    private final IntSupplier voteCountSupplier;
    private final Object cacheLock = new Object();
    private volatile byte[] cachedVanillaIndexes;
    private volatile int cachedVersion = -1;
    private volatile int cachedVoteCount = -1;
    private volatile boolean cachedShaderRgb;
    private int preparingVersion = -1;
    private int preparingVoteCount = -1;
    private boolean preparingShaderRgb;

    public PixelMapRenderer(
            PixelCanvas canvas,
            UUID canvasId,
            UUID ownerId,
            int tileX,
            int tileY,
            int mapSize,
            int mapMarginX,
            int mapMarginY,
            int drawScale,
            int canvasPixelWidth,
            int canvasPixelHeight,
            Color backgroundColor,
            String ownerName,
            String themeTitle,
            IntSupplier voteCountSupplier,
            boolean throttleNonOwner,
            BooleanSupplier shaderRgbSupplier,
            Function<Player, PreviewOverlay> previewOverlayProvider
    ) {
        super(true);
        this.canvas = canvas;
        this.canvasId = canvasId;
        this.ownerId = ownerId;
        this.tileX = tileX;
        this.tileY = tileY;
        this.mapSize = mapSize;
        this.mapMarginX = mapMarginX;
        this.mapMarginY = mapMarginY;
        this.drawScale = drawScale;
        this.canvasPixelWidth = canvasPixelWidth;
        this.canvasPixelHeight = canvasPixelHeight;
        this.backgroundColor = backgroundColor;
        this.ownerName = ownerName;
        this.themeTitle = themeTitle;
        this.voteCountSupplier = voteCountSupplier;
        this.throttleNonOwner = throttleNonOwner;
        this.shaderRgbSupplier = shaderRgbSupplier;
        this.previewOverlayProvider = previewOverlayProvider;
    }

    @Override
    public void render(MapView map, MapCanvas mapCanvas, Player player) {
        int tileVersion = canvas.tileVersion(tileX, tileY);
        UUID playerId = player.getUniqueId();
        PreviewOverlay overlay = previewOverlay(player);
        if (overlay == null && throttleNonOwner && !ownerId.equals(playerId)) {
            return;
        }

        int renderedVersion = renderedVersions.getOrDefault(playerId, -1);
        int voteCount = Math.max(0, voteCountSupplier.getAsInt());
        int renderedVoteCount = renderedVoteCounts.getOrDefault(playerId, -1);
        boolean shaderRgb = shaderRgbSupplier.getAsBoolean();
        boolean renderedShaderRgb = renderedShaderRgbStates.getOrDefault(playerId, false);
        int overlayVersion = overlay == null ? 0 : overlay.version();
        int renderedOverlayVersion = renderedOverlayVersions.getOrDefault(playerId, -1);
        if (renderedVersion == tileVersion
                && renderedVoteCount == voteCount
                && renderedShaderRgb == shaderRgb
                && renderedOverlayVersion == overlayVersion) {
            return;
        }

        RenderedMap renderedMap = cachedMap(tileVersion, voteCount, shaderRgb);
        if (renderedMap == null) {
            return;
        }
        byte[] vanillaIndexes = renderedMap.vanillaIndexes();
        if (overlay != null) {
            vanillaIndexes = vanillaIndexes.clone();
            applyOverlay(vanillaIndexes, overlay, shaderRgb);
        }
        VanillaMapEncoder.paint(mapCanvas, vanillaIndexes, mapSize, mapSize);
        renderedVersions.put(playerId, renderedMap.version());
        renderedVoteCounts.put(playerId, renderedMap.voteCount());
        renderedShaderRgbStates.put(playerId, shaderRgb);
        renderedOverlayVersions.put(playerId, overlayVersion);
    }

    private PreviewOverlay previewOverlay(Player player) {
        if (previewOverlayProvider == null) {
            return null;
        }
        PreviewOverlay overlay = previewOverlayProvider.apply(player);
        if (overlay == null || !canvasId.equals(overlay.canvasId())) {
            return null;
        }
        return overlay;
    }

    private void applyOverlay(byte[] vanillaIndexes, PreviewOverlay overlay, boolean shaderRgb) {
        int tileLeft = tileX * mapSize;
        int tileTop = tileY * mapSize;
        int tileRight = tileLeft + mapSize;
        int tileBottom = tileTop + mapSize;
        for (PreviewPixel pixel : overlay.pixels()) {
            int panelX = mapMarginX + pixel.x() * drawScale;
            int panelY = mapMarginY + pixel.y() * drawScale;
            Color color = pixel.clearActiveLayer()
                    ? canvas.getPixelWithoutActiveLayer(pixel.x(), pixel.y())
                    : pixel.color();
            if (shaderRgb) {
                RgbMapEncoder.writePixel(
                        vanillaIndexes,
                        mapSize,
                        mapSize,
                        panelX - tileLeft,
                        panelY - tileTop,
                        color
                );
                continue;
            }
            byte mapColor = VanillaMapEncoder.match(color);
            for (int y = panelY; y < panelY + drawScale; y++) {
                if (y < tileTop || y >= tileBottom) {
                    continue;
                }
                for (int x = panelX; x < panelX + drawScale; x++) {
                    if (x < tileLeft || x >= tileRight) {
                        continue;
                    }
                    vanillaIndexes[(y - tileTop) * mapSize + (x - tileLeft)] = mapColor;
                }
            }
        }
    }

    private RenderedMap cachedMap(int tileVersion, int voteCount, boolean shaderRgb) {
        synchronized (cacheLock) {
            if (cachedVanillaIndexes != null
                    && cachedVersion == tileVersion
                    && cachedVoteCount == voteCount
                    && cachedShaderRgb == shaderRgb) {
                return new RenderedMap(cachedVanillaIndexes, cachedVersion, cachedVoteCount);
            }
        }

        prepareAsync(tileVersion, voteCount, shaderRgb, null);
        synchronized (cacheLock) {
            if (cachedVanillaIndexes != null && cachedShaderRgb == shaderRgb) {
                return new RenderedMap(cachedVanillaIndexes, cachedVersion, cachedVoteCount);
            }
        }
        return prepareSyncMap(tileVersion, voteCount, shaderRgb);
    }

    private record RenderedMap(byte[] vanillaIndexes, int version, int voteCount) {
    }

    public void invalidateCache() {
        synchronized (cacheLock) {
            cachedVanillaIndexes = null;
            cachedVersion = -1;
            cachedVoteCount = -1;
            cachedShaderRgb = false;
            preparingVersion = -1;
            preparingVoteCount = -1;
            preparingShaderRgb = false;
        }
    }

    public void prepareSync(int tileVersion, int voteCount) {
        prepareSyncMap(tileVersion, voteCount, shaderRgbSupplier.getAsBoolean());
    }

    private RenderedMap prepareSyncMap(int tileVersion, int voteCount, boolean shaderRgb) {
        RenderedColors rendered = buildRenderedColors(canvas.snapshotMapTile(tileX, tileY), voteCount, shaderRgb);
        synchronized (cacheLock) {
            cachedVanillaIndexes = rendered.vanillaIndexes();
            cachedVersion = tileVersion;
            cachedVoteCount = voteCount;
            cachedShaderRgb = shaderRgb;
            if (preparingVersion == tileVersion
                    && preparingVoteCount == voteCount
                    && preparingShaderRgb == shaderRgb) {
                preparingVersion = -1;
                preparingVoteCount = -1;
            }
            return new RenderedMap(cachedVanillaIndexes, cachedVersion, cachedVoteCount);
        }
    }

    public boolean prepareAsync(int tileVersion, int voteCount, Runnable onReady) {
        return prepareAsync(tileVersion, voteCount, shaderRgbSupplier.getAsBoolean(), onReady);
    }

    private boolean prepareAsync(int tileVersion, int voteCount, boolean shaderRgb, Runnable onReady) {
        PERF_PREPARE_CALLS.increment();
        synchronized (cacheLock) {
            if (cachedVanillaIndexes != null
                    && cachedVersion == tileVersion
                    && cachedVoteCount == voteCount
                    && cachedShaderRgb == shaderRgb) {
                PERF_READY_HITS.increment();
                return true;
            }
            if (preparingVersion == tileVersion
                    && preparingVoteCount == voteCount
                    && preparingShaderRgb == shaderRgb) {
                PERF_DUPLICATE_WAITS.increment();
                return false;
            }
            preparingVersion = tileVersion;
            preparingVoteCount = voteCount;
            preparingShaderRgb = shaderRgb;
        }

        long snapshotStart = System.nanoTime();
        Color[] snapshot = canvas.snapshotMapTile(tileX, tileY);
        recordSnapshotNanos(System.nanoTime() - snapshotStart);
        PERF_RENDER_SCHEDULED.increment();
        CompletableFuture
                .supplyAsync(() -> {
                    long renderStart = System.nanoTime();
                    try {
                        return buildRenderedColors(snapshot, voteCount, shaderRgb);
                    } finally {
                        recordRenderNanos(System.nanoTime() - renderStart);
                    }
                })
                .whenComplete((rendered, throwable) -> {
                    boolean ready = false;
                    synchronized (cacheLock) {
                        if (preparingVersion == tileVersion
                                && preparingVoteCount == voteCount
                                && preparingShaderRgb == shaderRgb) {
                            preparingVersion = -1;
                            preparingVoteCount = -1;
                        }
                        if (throwable == null && rendered != null && tileVersion >= cachedVersion) {
                            cachedVanillaIndexes = rendered.vanillaIndexes();
                            cachedVersion = tileVersion;
                            cachedVoteCount = voteCount;
                            cachedShaderRgb = shaderRgb;
                            ready = true;
                        }
                    }
                    if (throwable == null && rendered != null) {
                        PERF_RENDER_COMPLETED.increment();
                    } else {
                        PERF_RENDER_FAILED.increment();
                    }
                    if (ready && onReady != null) {
                        onReady.run();
                    }
                });
        return false;
    }

    public static RenderPerfSnapshot drainPerfSnapshot() {
        return new RenderPerfSnapshot(
                PERF_PREPARE_CALLS.sumThenReset(),
                PERF_READY_HITS.sumThenReset(),
                PERF_DUPLICATE_WAITS.sumThenReset(),
                PERF_RENDER_SCHEDULED.sumThenReset(),
                PERF_RENDER_COMPLETED.sumThenReset(),
                PERF_RENDER_FAILED.sumThenReset(),
                PERF_SNAPSHOT_NANOS.sumThenReset(),
                PERF_MAX_SNAPSHOT_NANOS.getAndSet(0L),
                PERF_RENDER_NANOS.sumThenReset(),
                PERF_MAX_RENDER_NANOS.getAndSet(0L)
        );
    }

    private static void recordSnapshotNanos(long nanos) {
        PERF_SNAPSHOT_NANOS.add(nanos);
        updateMax(PERF_MAX_SNAPSHOT_NANOS, nanos);
    }

    private static void recordRenderNanos(long nanos) {
        PERF_RENDER_NANOS.add(nanos);
        updateMax(PERF_MAX_RENDER_NANOS, nanos);
    }

    private static void updateMax(AtomicLong target, long value) {
        long current = target.get();
        while (value > current && !target.compareAndSet(current, value)) {
            current = target.get();
        }
    }

    private RenderedColors buildRenderedColors(Color[] snapshot, int voteCount, boolean shaderRgb) {
        Color[] colors = buildColors(snapshot, voteCount);
        byte[] indexes = shaderRgb
                ? RgbMapEncoder.encode(colors, mapSize, mapSize)
                : VanillaMapEncoder.encode(colors, mapSize, mapSize);
        return new RenderedColors(indexes);
    }

    private record RenderedColors(byte[] vanillaIndexes) {
    }

    public record RenderPerfSnapshot(
            long prepareCalls,
            long readyHits,
            long duplicateWaits,
            long renderScheduled,
            long renderCompleted,
            long renderFailed,
            long snapshotNanosTotal,
            long snapshotNanosMax,
            long renderNanosTotal,
            long renderNanosMax
    ) {
    }

    public record PreviewOverlay(UUID canvasId, int version, java.util.List<PreviewPixel> pixels) {
    }

    public record PreviewPixel(int x, int y, Color color, boolean clearActiveLayer) {
        public PreviewPixel(int x, int y, Color color) {
            this(x, y, color, false);
        }

        public static PreviewPixel clearActiveLayer(int x, int y) {
            return new PreviewPixel(x, y, null, true);
        }
    }

    private Color[] buildColors(Color[] tileSnapshot, int voteCount) {
        Color[] colors = new Color[mapSize * mapSize];
        int panelOffsetX = tileX * mapSize;
        int panelOffsetY = tileY * mapSize;
        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                int panelY = panelOffsetY + y;
                int index = y * mapSize + x;
                Color color = tileSnapshot[index];
                colors[index] = color == null ? frameColor(panelY) : color;
            }
        }

        if (mapMarginY > 0) {
            drawThemeLabel(colors);
            drawLabel(colors, ownerName, mapMarginY + canvasPixelHeight * drawScale);
            drawVoteHearts(colors, voteCount, mapMarginY + canvasPixelHeight * drawScale);
        }
        return colors;
    }

    private Color frameColor(int panelY) {
        int imagePanelTop = mapMarginY;
        int imagePanelBottom = mapMarginY + canvasPixelHeight * drawScale;
        if (panelY < imagePanelTop || panelY >= imagePanelBottom) {
            return FRAME_COLOR;
        }

        return backgroundColor;
    }

    private void drawLabel(Color[] colors, String value, int frameTop) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return;
        }
        if (!isPixelGlyphText(text)) {
            drawRasterTitle(colors, text, frameTop);
            return;
        }

        text = text.toUpperCase(Locale.ROOT);
        if (text.length() > 20) {
            text = text.substring(0, 20);
        }

        int panelWidth = canvasPixelWidth * drawScale;
        int scale = 7;
        while (scale > 4 && textPixelWidth(text, scale) > panelWidth - 16) {
            scale--;
        }
        int glyphWidth = 5 * scale;
        int glyphHeight = 7 * scale;
        int spacing = Math.max(1, scale - 1);
        int textWidth = text.isEmpty() ? 0 : text.length() * glyphWidth + (text.length() - 1) * spacing;
        int textPanelX = Math.max(0, (panelWidth - textWidth) / 2);
        int frameHeight = mapMarginY;
        int textPanelY = frameTop + Math.max(0, (frameHeight - glyphHeight) / 2);

        for (int index = 0; index < text.length(); index++) {
            int glyphPanelX = textPanelX + index * (glyphWidth + spacing);
            drawGlyph(colors, text.charAt(index), glyphPanelX, textPanelY, scale, true, Color.WHITE);
        }
    }

    private void drawThemeLabel(Color[] colors) {
        String text = themeTitle == null ? "" : themeTitle.trim();
        if (text.isEmpty()) {
            return;
        }

        int labelSize = Math.max(12, mapMarginY / 7);
        int titleSize = Math.max(26, mapMarginY / 2);
        drawRasterText(colors, "테마", 0, labelSize, mapMarginY / 4, new Color(226, 214, 126), true);
        drawRasterText(colors, text, 0, titleSize, mapMarginY * 5 / 8, Color.WHITE, true);
    }

    private void drawRasterTitle(Color[] colors, String text, int frameTop) {
        drawRasterText(colors, text, frameTop, mapMarginY - 28, mapMarginY / 2, Color.WHITE, true);
    }

    private void drawVoteHearts(Color[] colors, int count, int frameTop) {
        if (count <= 0) {
            return;
        }

        String[] heart = {
                "0110110",
                "1111111",
                "1111111",
                "0111110",
                "0011100",
                "0001000"
        };
        int panelWidth = canvasPixelWidth * drawScale;
        int scale = 4;
        while (scale > 2 && count * heart[0].length() * scale + (count - 1) * scale > panelWidth - 16) {
            scale--;
        }

        int heartWidth = heart[0].length() * scale;
        int heartHeight = heart.length * scale;
        int spacing = scale;
        int totalWidth = count * heartWidth + (count - 1) * spacing;
        int startX = Math.max(0, (panelWidth - totalWidth) / 2);
        int startY = frameTop + mapMarginY - heartHeight - 10;
        for (int index = 0; index < count; index++) {
            drawHeart(colors, heart, startX + index * (heartWidth + spacing), startY, scale);
        }
    }

    private void drawHeart(Color[] colors, String[] heart, int panelX, int panelY, int scale) {
        int tileLeft = tileX * mapSize;
        int tileTop = tileY * mapSize;
        int tileRight = tileLeft + mapSize;
        int tileBottom = tileTop + mapSize;

        for (int row = 0; row < heart.length; row++) {
            String line = heart[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != '1') {
                    continue;
                }

                int pixelPanelX = panelX + column * scale;
                int pixelPanelY = panelY + row * scale;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int currentPanelX = pixelPanelX + dx;
                        int currentPanelY = pixelPanelY + dy;
                        if (currentPanelX < tileLeft || currentPanelX >= tileRight
                                || currentPanelY < tileTop || currentPanelY >= tileBottom) {
                            continue;
                        }
                        colors[(currentPanelY - tileTop) * mapSize + currentPanelX - tileLeft] = new Color(231, 75, 88);
                    }
                }
            }
        }
    }

    private void drawRasterText(Color[] colors, String text, int frameTop, int maxFontSize, int centerY, Color color, boolean bold) {
        int panelWidth = canvasPixelWidth * drawScale;
        int frameHeight = mapMarginY;
        drawRasterTextInArea(colors, text, frameTop, frameHeight, maxFontSize, centerY, color, bold);
    }

    private void drawRasterTextInArea(Color[] colors, String text, int areaTop, int areaHeight, int maxFontSize, int centerY, Color color, boolean bold) {
        int panelWidth = canvasPixelWidth * drawScale;
        if (areaHeight <= 0 || panelWidth <= 0) {
            return;
        }

        BufferedImage image = new BufferedImage(panelWidth, areaHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        int fontSize = Math.max(10, Math.min(maxFontSize, areaHeight - 8));
        Font font = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, fontSize);
        FontMetrics metrics = graphics.getFontMetrics(font);
        while (fontSize > 10 && metrics.stringWidth(text) > panelWidth - 24) {
            fontSize--;
            font = font.deriveFont((float) fontSize);
            metrics = graphics.getFontMetrics(font);
        }

        graphics.setFont(font);
        graphics.setColor(color);
        int textX = Math.max(0, (panelWidth - metrics.stringWidth(text)) / 2);
        int textY = Math.max(metrics.getAscent(), centerY - metrics.getHeight() / 2 + metrics.getAscent());
        graphics.drawString(text, textX, textY);
        graphics.dispose();

        int tileLeft = tileX * mapSize;
        int tileTop = tileY * mapSize;
        int tileRight = tileLeft + mapSize;
        int tileBottom = tileTop + mapSize;
        for (int y = 0; y < areaHeight; y++) {
            int panelY = areaTop + y;
            if (panelY < tileTop || panelY >= tileBottom) {
                continue;
            }
            for (int x = 0; x < panelWidth; x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }
                if (x < tileLeft || x >= tileRight) {
                    continue;
                }
                colors[(panelY - tileTop) * mapSize + x - tileLeft] = new Color(argb, true);
            }
        }
    }

    private boolean isPixelGlyphText(String text) {
        return text.toUpperCase(Locale.ROOT).chars()
                .allMatch(character -> character == ' '
                        || character == '-'
                        || character == '_'
                        || character == '.'
                        || (character >= '0' && character <= '9')
                        || (character >= 'A' && character <= 'Z'));
    }

    private int textPixelWidth(String text, int scale) {
        if (text.isEmpty()) {
            return 0;
        }

        int glyphWidth = 5 * scale;
        int spacing = Math.max(1, scale - 1);
        return text.length() * glyphWidth + (text.length() - 1) * spacing;
    }

    private void drawGlyph(Color[] colors, char character, int panelX, int panelY, int scale, boolean bold, Color color) {
        String[] glyph = glyph(character);
        int tileLeft = tileX * mapSize;
        int tileTop = tileY * mapSize;
        int tileRight = tileLeft + mapSize;
        int tileBottom = tileTop + mapSize;

        for (int row = 0; row < glyph.length; row++) {
            String line = glyph[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != '1') {
                    continue;
                }

                int pixelPanelX = panelX + column * scale;
                int pixelPanelY = panelY + row * scale;
                int strokeSize = bold ? scale + 1 : scale;
                for (int dy = 0; dy < strokeSize; dy++) {
                    for (int dx = 0; dx < strokeSize; dx++) {
                        int currentPanelX = pixelPanelX + dx;
                        int currentPanelY = pixelPanelY + dy;
                        if (currentPanelX < tileLeft || currentPanelX >= tileRight
                                || currentPanelY < tileTop || currentPanelY >= tileBottom) {
                            continue;
                        }

                        colors[(currentPanelY - tileTop) * mapSize + currentPanelX - tileLeft] = color;
                    }
                }
            }
        }
    }

    private String[] glyph(char character) {
        return switch (character) {
            case 'A' -> new String[]{"01110", "10001", "10001", "11111", "10001", "10001", "10001"};
            case 'B' -> new String[]{"11110", "10001", "10001", "11110", "10001", "10001", "11110"};
            case 'C' -> new String[]{"01111", "10000", "10000", "10000", "10000", "10000", "01111"};
            case 'D' -> new String[]{"11110", "10001", "10001", "10001", "10001", "10001", "11110"};
            case 'E' -> new String[]{"11111", "10000", "10000", "11110", "10000", "10000", "11111"};
            case 'F' -> new String[]{"11111", "10000", "10000", "11110", "10000", "10000", "10000"};
            case 'G' -> new String[]{"01111", "10000", "10000", "10011", "10001", "10001", "01111"};
            case 'H' -> new String[]{"10001", "10001", "10001", "11111", "10001", "10001", "10001"};
            case 'I' -> new String[]{"11111", "00100", "00100", "00100", "00100", "00100", "11111"};
            case 'J' -> new String[]{"00111", "00010", "00010", "00010", "10010", "10010", "01100"};
            case 'K' -> new String[]{"10001", "10010", "10100", "11000", "10100", "10010", "10001"};
            case 'L' -> new String[]{"10000", "10000", "10000", "10000", "10000", "10000", "11111"};
            case 'M' -> new String[]{"10001", "11011", "10101", "10101", "10001", "10001", "10001"};
            case 'N' -> new String[]{"10001", "11001", "10101", "10011", "10001", "10001", "10001"};
            case 'O' -> new String[]{"01110", "10001", "10001", "10001", "10001", "10001", "01110"};
            case 'P' -> new String[]{"11110", "10001", "10001", "11110", "10000", "10000", "10000"};
            case 'Q' -> new String[]{"01110", "10001", "10001", "10001", "10101", "10010", "01101"};
            case 'R' -> new String[]{"11110", "10001", "10001", "11110", "10100", "10010", "10001"};
            case 'S' -> new String[]{"01111", "10000", "10000", "01110", "00001", "00001", "11110"};
            case 'T' -> new String[]{"11111", "00100", "00100", "00100", "00100", "00100", "00100"};
            case 'U' -> new String[]{"10001", "10001", "10001", "10001", "10001", "10001", "01110"};
            case 'V' -> new String[]{"10001", "10001", "10001", "10001", "10001", "01010", "00100"};
            case 'W' -> new String[]{"10001", "10001", "10001", "10101", "10101", "10101", "01010"};
            case 'X' -> new String[]{"10001", "10001", "01010", "00100", "01010", "10001", "10001"};
            case 'Y' -> new String[]{"10001", "10001", "01010", "00100", "00100", "00100", "00100"};
            case 'Z' -> new String[]{"11111", "00001", "00010", "00100", "01000", "10000", "11111"};
            case '0' -> new String[]{"01110", "10001", "10011", "10101", "11001", "10001", "01110"};
            case '1' -> new String[]{"00100", "01100", "00100", "00100", "00100", "00100", "01110"};
            case '2' -> new String[]{"01110", "10001", "00001", "00010", "00100", "01000", "11111"};
            case '3' -> new String[]{"11110", "00001", "00001", "01110", "00001", "00001", "11110"};
            case '4' -> new String[]{"00010", "00110", "01010", "10010", "11111", "00010", "00010"};
            case '5' -> new String[]{"11111", "10000", "10000", "11110", "00001", "00001", "11110"};
            case '6' -> new String[]{"01110", "10000", "10000", "11110", "10001", "10001", "01110"};
            case '7' -> new String[]{"11111", "00001", "00010", "00100", "01000", "01000", "01000"};
            case '8' -> new String[]{"01110", "10001", "10001", "01110", "10001", "10001", "01110"};
            case '9' -> new String[]{"01110", "10001", "10001", "01111", "00001", "00001", "01110"};
            case ' ' -> new String[]{"00000", "00000", "00000", "00000", "00000", "00000", "00000"};
            case '-', '_' -> new String[]{"00000", "00000", "00000", "11111", "00000", "00000", "00000"};
            case '.' -> new String[]{"00000", "00000", "00000", "00000", "00000", "01100", "01100"};
            default -> new String[]{"00000", "00000", "00000", "00000", "00000", "00000", "00000"};
        };
    }
}
