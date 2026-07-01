package org.ha2yo.paint.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.api.PaintCanvas;
import org.ha2yo.paint.model.PaintArtwork;
import org.ha2yo.paint.model.PixelCanvas;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public final class ArtworkStorageService {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_TITLE_LENGTH = 32;
    private static final String LEGACY_METADATA_FILE = "artworks.yml";
    private static final String USER_METADATA_DIRECTORY = "artworks";
    private static final String METADATA_DIRECTORY_NAME = "metadata";
    private static final String IMAGE_DIRECTORY_NAME = "images";
    private static final String LAYER_DIRECTORY_NAME = "layers";
    private static final String LAYER_SNAPSHOT_FILE_SUFFIX = ".layers.dat";
    private static final String LAYER_SNAPSHOT_MAGIC = "PPL1";

    private final Paint plugin;
    private final int imageScale;
    private final Color backgroundColor;
    private final Map<UUID, PaintArtwork> artworks = new LinkedHashMap<>();
    private final Map<UUID, String> legacyMetadataLayerSnapshots = new HashMap<>();
    private File artworksDirectory;

    public ArtworkStorageService(Paint plugin, int imageScale, Color backgroundColor) {
        this.plugin = plugin;
        this.imageScale = imageScale;
        this.backgroundColor = backgroundColor;
    }

    public void load() {
        artworks.clear();
        legacyMetadataLayerSnapshots.clear();
        artworksDirectory = new File(plugin.getDataFolder(), USER_METADATA_DIRECTORY);
        if (hasUserMetadataFiles()) {
            loadUserMetadataFiles();
            return;
        }

        File legacyMetadataFile = legacyMetadataFile();
        boolean loadedLegacy = loadMetadataFile(legacyMetadataFile);
        if (loadedLegacy) {
            try {
                saveMetadata();
                backupLegacyMetadataFile(legacyMetadataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not migrate Paint artwork metadata: " + e.getMessage());
            }
        }
    }

    private void loadUserMetadataFiles() {
        File metadataDirectory = metadataDirectory();
        File[] files = metadataDirectory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            files = artworksDirectory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        }
        if (files == null) {
            return;
        }
        for (File file : files) {
            loadMetadataFile(file);
        }
    }

    private boolean loadMetadataFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("artworks");
        if (section == null) {
            return false;
        }

        for (String key : section.getKeys(false)) {
            readArtwork(config, key).ifPresent(artwork -> artworks.put(artwork.id(), artwork));
        }
        return true;
    }

    private Optional<PaintArtwork> readArtwork(YamlConfiguration config, String key) {
        try {
            UUID id = UUID.fromString(key);
            String path = "artworks." + key;
            UUID ownerId = UUID.fromString(config.getString(path + ".owner-id", ""));
            String legacyLayerSnapshot = config.getString(path + ".layer-snapshot", "");
            if (legacyLayerSnapshot != null && !legacyLayerSnapshot.isBlank()) {
                legacyMetadataLayerSnapshots.put(id, legacyLayerSnapshot);
            }
            return Optional.of(new PaintArtwork(
                    id,
                    ownerId,
                    config.getString(path + ".owner-name", ""),
                    config.getString(path + ".title", legacyTitle(config.getString(path + ".image", ""))),
                    config.getInt(path + ".width"),
                    config.getInt(path + ".height"),
                    LocalDateTime.parse(config.getString(path + ".created-at", LocalDateTime.now().toString())),
                    config.getString(path + ".image", "")
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public PaintArtwork save(Player player, PaintCanvas canvas) throws IOException {
        return save(player, canvas, player.getName());
    }

    public PaintArtwork save(Player player, PaintCanvas canvas, String title) throws IOException {
        return save(player, canvas, title, null);
    }

    public PaintArtwork save(Player player, PaintCanvas canvas, String title, PixelCanvas.LayerSnapshot layerSnapshot) throws IOException {
        return save(player.getUniqueId(), player.getName(), canvas, title, layerSnapshot);
    }

    public PaintArtwork save(UUID ownerId, String ownerName, PaintCanvas canvas, String title) throws IOException {
        return save(ownerId, ownerName, canvas, title, null);
    }

    private PaintArtwork save(UUID ownerId, String ownerName, PaintCanvas canvas, String title, PixelCanvas.LayerSnapshot layerSnapshot) throws IOException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Could not create " + plugin.getDataFolder().getPath());
        }

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        String normalizedOwnerName = ownerName == null || ownerName.isBlank() ? ownerId.toString() : ownerName;
        String artworkTitle = normalizeTitle(title, normalizedOwnerName);
        String ownerDirectoryName = ownerDirectoryName(normalizedOwnerName, ownerId);
        File playerDirectory = ownerArtworkDirectory(ownerDirectoryName);
        if (!playerDirectory.mkdirs() && !playerDirectory.isDirectory()) {
            throw new IOException("Could not create " + playerDirectory.getPath());
        }

        String baseName = FILE_DATE_FORMAT.format(createdAt) + "-" + safeFileName(artworkTitle);
        File outputFile = uniqueImageFile(playerDirectory, baseName);
        ImageIO.write(toImage(canvas), "png", outputFile);
        saveLayerSnapshot(outputFile, layerSnapshot);

        PaintArtwork artwork = new PaintArtwork(
                id,
                ownerId,
                normalizedOwnerName,
                artworkTitle,
                canvas.width(),
                canvas.height(),
                createdAt,
                relativePath(outputFile)
        );
        artworks.put(id, artwork);
        saveMetadata();
        return artwork;
    }

    public PaintArtwork overwrite(Player player, PaintCanvas canvas, String title, UUID artworkId) throws IOException {
        return overwrite(player, canvas, title, artworkId, null);
    }

    public PaintArtwork overwrite(Player player, PaintCanvas canvas, String title, UUID artworkId, PixelCanvas.LayerSnapshot layerSnapshot) throws IOException {
        PaintArtwork current = artworks.get(artworkId);
        if (current == null || !current.ownerId().equals(player.getUniqueId())) {
            throw new IOException("Artwork not found: " + artworkId);
        }

        String artworkTitle = normalizeTitle(title, current.title());
        File outputFile = imageFile(current);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent.getPath());
        }
        ImageIO.write(toImage(canvas), "png", outputFile);
        saveLayerSnapshot(outputFile, layerSnapshot);

        PaintArtwork updated = new PaintArtwork(
                current.id(),
                current.ownerId(),
                current.ownerName(),
                artworkTitle,
                canvas.width(),
                canvas.height(),
                current.createdAt(),
                current.imagePath()
        );
        artworks.put(updated.id(), updated);
        saveMetadata();
        return updated;
    }

    public List<PaintArtwork> listByOwner(UUID ownerId) {
        List<PaintArtwork> result = new ArrayList<>();
        for (PaintArtwork artwork : artworks.values()) {
            if (artwork.ownerId().equals(ownerId)) {
                result.add(artwork);
            }
        }
        result.sort(Comparator.comparing(PaintArtwork::createdAt).reversed());
        return result;
    }

    public List<PaintArtwork> listByOwnerName(String ownerName) {
        String normalized = ownerName == null ? "" : ownerName.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<PaintArtwork> result = new ArrayList<>();
        for (PaintArtwork artwork : artworks.values()) {
            if (artwork.ownerName() != null
                    && artwork.ownerName().trim().toLowerCase(java.util.Locale.ROOT).equals(normalized)) {
                result.add(artwork);
            }
        }
        result.sort(Comparator.comparing(PaintArtwork::createdAt).reversed());
        return result;
    }

    public List<String> ownerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (PaintArtwork artwork : artworks.values()) {
            String ownerName = artwork.ownerName();
            if (ownerName != null && !ownerName.isBlank()) {
                names.add(ownerName);
            }
        }
        List<String> result = new ArrayList<>(names);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public Optional<PaintArtwork> find(UUID id) {
        return Optional.ofNullable(artworks.get(id));
    }

    public Optional<PaintArtwork> findByTitle(UUID ownerId, String title, UUID excludedArtworkId) {
        String normalized = normalizeTitle(title, "").toLowerCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        for (PaintArtwork artwork : artworks.values()) {
            if (excludedArtworkId != null && artwork.id().equals(excludedArtworkId)) {
                continue;
            }
            if (artwork.ownerId().equals(ownerId)
                    && normalizeTitle(artwork.title(), "").toLowerCase(java.util.Locale.ROOT).equals(normalized)) {
                return Optional.of(artwork);
            }
        }
        return Optional.empty();
    }

    public String uniqueTitle(UUID ownerId, String title, UUID excludedArtworkId) {
        String baseTitle = normalizeTitle(title, "");
        String candidate = clampTitle(baseTitle);
        for (int suffix = 1; titleExists(ownerId, candidate, excludedArtworkId); suffix++) {
            candidate = titleWithSuffix(baseTitle, suffix);
        }
        return candidate;
    }

    public boolean titleExists(UUID ownerId, String title) {
        return titleExists(ownerId, title, null);
    }

    public boolean titleExists(UUID ownerId, String title, UUID excludedArtworkId) {
        return findByTitle(ownerId, title, excludedArtworkId).isPresent();
    }
    public boolean delete(UUID ownerId, UUID artworkId) throws IOException {
        PaintArtwork artwork = artworks.get(artworkId);
        if (artwork == null || !artwork.ownerId().equals(ownerId)) {
            return false;
        }

        artworks.remove(artworkId);
        File imageFile = imageFile(artwork);
        if (imageFile.isFile() && !imageFile.delete()) {
            plugin.getLogger().warning("Could not delete Paint artwork image: " + imageFile.getPath());
        }
        deleteLayerSnapshot(imageFile);
        deleteEmptyDirectory(imageFile.getParentFile());
        saveMetadata();
        return true;
    }

    public Optional<PixelCanvas.LayerSnapshot> loadLayerSnapshot(PaintArtwork artwork) throws IOException {
        File file = existingLayerSnapshotFile(imageFile(artwork));
        if (!file.isFile()) {
            return Optional.empty();
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return Optional.of(readLayerSnapshot(input, file.getPath()));
        }
    }

    public File imageFile(PaintArtwork artwork) {
        File imageFile = new File(artwork.imagePath());
        if (imageFile.isAbsolute()) {
            return imageFile;
        }

        File resolved = new File(plugin.getDataFolder(), artwork.imagePath());
        if (resolved.isFile()) {
            return resolved;
        }

        File fallback = fallbackImageFile(artwork, imageFile.getName(), resolved.getParentFile());
        return fallback != null ? fallback : resolved;
    }

    private File fallbackImageFile(PaintArtwork artwork, String fileName, File recordedParent) {
        File artworkDirectory = ownerArtworkDirectory(artwork.ownerName(), artwork.ownerId());
        File artworkFile = new File(artworkDirectory, fileName);
        if (artworkFile.isFile()) {
            return artworkFile;
        }

        File previousArtworkDirectory = new File(artworksDirectory, ownerDirectoryName(artwork.ownerName(), artwork.ownerId()));
        File previousArtworkFile = new File(previousArtworkDirectory, fileName);
        if (previousArtworkFile.isFile()) {
            return previousArtworkFile;
        }

        File imagesDirectory = new File(plugin.getDataFolder(), "images");

        File namedDirectory = new File(imagesDirectory, ownerDirectoryName(artwork.ownerName(), artwork.ownerId()));
        File namedFile = new File(namedDirectory, fileName);
        if (namedFile.isFile()) {
            return namedFile;
        }

        File legacyDirectory = new File(imagesDirectory, artwork.ownerId().toString());
        File legacyFile = new File(legacyDirectory, fileName);
        if (legacyFile.isFile()) {
            return legacyFile;
        }

        if (recordedParent != null) {
            String uuidDirectoryName = uuidDirectoryName(recordedParent.getName());
            if (!uuidDirectoryName.equals(recordedParent.getName())) {
                File uuidDirectory = new File(imagesDirectory, uuidDirectoryName);
                File uuidFile = new File(uuidDirectory, fileName);
                if (uuidFile.isFile()) {
                    return uuidFile;
                }
            }
        }

        return null;
    }

    private File ownerArtworkDirectory(String ownerName, UUID ownerId) {
        return ownerArtworkDirectory(ownerDirectoryName(ownerName, ownerId));
    }

    private File ownerArtworkDirectory(String ownerDirectoryName) {
        if (artworksDirectory == null) {
            artworksDirectory = new File(plugin.getDataFolder(), USER_METADATA_DIRECTORY);
        }
        return new File(new File(artworksDirectory, IMAGE_DIRECTORY_NAME), ownerDirectoryName);
    }

    private static String ownerDirectoryName(String ownerName, UUID ownerId) {
        String safeOwnerName = safeOwnerDirectoryName(ownerName);
        return safeOwnerName + "-" + ownerId;
    }

    private static String safeOwnerDirectoryName(String ownerName) {
        String sanitized = ownerName == null ? "" : ownerName.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_");
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        return sanitized.isBlank() ? "player" : sanitized;
    }

    private static String uuidDirectoryName(String directoryName) {
        int separatorIndex = directoryName.indexOf('-');
        if (separatorIndex < 0 || separatorIndex + 1 >= directoryName.length()) {
            return directoryName;
        }
        String candidate = directoryName.substring(separatorIndex + 1);
        try {
            UUID.fromString(candidate);
            return candidate;
        } catch (IllegalArgumentException ignored) {
            return directoryName;
        }
    }

    private void saveMetadata() throws IOException {
        if (artworksDirectory == null) {
            artworksDirectory = new File(plugin.getDataFolder(), USER_METADATA_DIRECTORY);
        }
        if (!artworksDirectory.mkdirs() && !artworksDirectory.isDirectory()) {
            throw new IOException("Could not create " + artworksDirectory.getPath());
        }

        migrateArtworkFilesToUserDirectories();

        Map<File, List<PaintArtwork>> artworksByFile = new LinkedHashMap<>();
        for (PaintArtwork artwork : artworks.values()) {
            File file = ownerMetadataFile(artwork);
            artworksByFile.computeIfAbsent(file, ignored -> new ArrayList<>()).add(artwork);
        }

        Set<File> writtenFiles = new LinkedHashSet<>();
        for (Map.Entry<File, List<PaintArtwork>> entry : artworksByFile.entrySet()) {
            File file = entry.getKey();
            File parent = file.getParentFile();
            if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Could not create " + parent.getPath());
            }
            YamlConfiguration config = new YamlConfiguration();
            for (PaintArtwork artwork : entry.getValue()) {
                writeArtwork(config, artwork);
            }
            config.save(file);
            writtenFiles.add(file.getCanonicalFile());
        }

        deleteStaleUserMetadataFiles(writtenFiles);
        backupFlatUserMetadataFiles(writtenFiles);
    }

    private void writeArtwork(YamlConfiguration config, PaintArtwork artwork) {
        String path = "artworks." + artwork.id();
        config.set(path + ".owner-id", artwork.ownerId().toString());
        config.set(path + ".owner-name", artwork.ownerName());
        config.set(path + ".title", artwork.title());
        config.set(path + ".width", artwork.width());
        config.set(path + ".height", artwork.height());
        config.set(path + ".created-at", artwork.createdAt().toString());
        config.set(path + ".image", artwork.imagePath());
    }

    private void migrateArtworkFilesToUserDirectories() throws IOException {
        for (PaintArtwork artwork : new ArrayList<>(artworks.values())) {
            PaintArtwork migrated = migrateArtworkFileToUserDirectory(artwork);
            if (!migrated.imagePath().equals(artwork.imagePath())) {
                artworks.put(migrated.id(), migrated);
            }
        }
    }

    private PaintArtwork migrateArtworkFileToUserDirectory(PaintArtwork artwork) throws IOException {
        File imageFile = imageFile(artwork);
        if (!imageFile.isFile()) {
            return artwork;
        }

        File targetDirectory = ownerArtworkDirectory(artwork.ownerName(), artwork.ownerId());
        if (!targetDirectory.mkdirs() && !targetDirectory.isDirectory()) {
            throw new IOException("Could not create " + targetDirectory.getPath());
        }
        if (sameFile(imageFile.getParentFile(), targetDirectory)) {
            migrateLayerSnapshot(artwork, imageFile, imageFile);
            String relativePath = relativePath(imageFile);
            if (relativePath.equals(artwork.imagePath())) {
                return artwork;
            }
            return artworkWithImagePath(artwork, relativePath);
        }

        String baseName = fileNameWithoutExtension(imageFile.getName());
        File targetFile = uniqueImageFile(targetDirectory, baseName);
        Files.move(imageFile.toPath(), targetFile.toPath());
        migrateLayerSnapshot(artwork, imageFile, targetFile);

        deleteEmptyDirectory(imageFile.getParentFile());
        return artworkWithImagePath(artwork, relativePath(targetFile));
    }

    private void migrateLayerSnapshot(PaintArtwork artwork, File sourceImageFile, File targetImageFile) throws IOException {
        String legacySnapshot = legacyMetadataLayerSnapshots.remove(artwork.id());
        if (legacySnapshot != null && !legacySnapshot.isBlank()) {
            Optional<PixelCanvas.LayerSnapshot> snapshot = decodeLegacyMetadataLayerSnapshot(legacySnapshot);
            if (snapshot.isPresent()) {
                saveLayerSnapshot(targetImageFile, snapshot.get());
                return;
            }
        }

        File layerFile = existingLayerSnapshotFile(sourceImageFile);
        if (!layerFile.isFile()) {
            return;
        }

        File targetLayerFile = layerSnapshotFile(targetImageFile);
        File parent = targetLayerFile.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent.getPath());
        }
        if (!sameFile(layerFile, targetLayerFile)) {
            Files.move(layerFile.toPath(), targetLayerFile.toPath());
        }
    }

    private PaintArtwork artworkWithImagePath(PaintArtwork artwork, String imagePath) {
        return new PaintArtwork(
                artwork.id(),
                artwork.ownerId(),
                artwork.ownerName(),
                artwork.title(),
                artwork.width(),
                artwork.height(),
                artwork.createdAt(),
                imagePath
        );
    }

    private boolean hasUserMetadataFiles() {
        if (artworksDirectory == null || !artworksDirectory.isDirectory()) {
            return false;
        }
        File[] metadataFiles = metadataDirectory().listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (metadataFiles != null && metadataFiles.length > 0) {
            return true;
        }
        File[] flatFiles = artworksDirectory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        return flatFiles != null && flatFiles.length > 0;
    }

    private File ownerMetadataFile(PaintArtwork artwork) {
        return new File(metadataDirectory(), ownerDirectoryName(artwork.ownerName(), artwork.ownerId()) + ".yml");
    }

    private File metadataDirectory() {
        if (artworksDirectory == null) {
            artworksDirectory = new File(plugin.getDataFolder(), USER_METADATA_DIRECTORY);
        }
        return new File(artworksDirectory, METADATA_DIRECTORY_NAME);
    }

    private void deleteStaleUserMetadataFiles(Set<File> writtenFiles) throws IOException {
        File[] files = metadataDirectory().listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (writtenFiles.contains(file.getCanonicalFile())) {
                continue;
            }
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete stale Paint artwork metadata: " + file.getPath());
            }
        }
    }

    private void backupFlatUserMetadataFiles(Set<File> writtenFiles) throws IOException {
        File[] files = artworksDirectory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (writtenFiles.contains(file.getCanonicalFile())) {
                continue;
            }
            File backup = uniqueBackupFile(new File(file.getParentFile(), file.getName() + ".legacy"));
            if (!file.renameTo(backup)) {
                plugin.getLogger().warning("Could not rename legacy Paint user metadata file: " + file.getPath());
            }
        }
    }

    private File legacyMetadataFile() {
        return new File(plugin.getDataFolder(), LEGACY_METADATA_FILE);
    }

    private void backupLegacyMetadataFile(File legacyMetadataFile) {
        if (legacyMetadataFile == null || !legacyMetadataFile.isFile()) {
            return;
        }
        File backup = uniqueBackupFile(new File(legacyMetadataFile.getParentFile(), legacyMetadataFile.getName() + ".legacy"));
        if (!legacyMetadataFile.renameTo(backup)) {
            plugin.getLogger().warning("Could not rename legacy Paint metadata file: " + legacyMetadataFile.getPath());
        }
    }

    private File uniqueBackupFile(File baseFile) {
        File file = baseFile;
        String name = baseFile.getName();
        File parent = baseFile.getParentFile();
        for (int suffix = 2; file.exists(); suffix++) {
            file = new File(parent, name + "-" + suffix);
        }
        return file;
    }

    private BufferedImage toImage(PaintCanvas canvas) {
        int imageWidth = canvas.width() * imageScale;
        int imageHeight = canvas.height() * imageScale;
        Color[] pixels = canvas.snapshot();
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int sourceX = x / imageScale;
                int sourceY = y / imageScale;
                Color color = pixels[sourceY * canvas.width() + sourceX];
                image.setRGB(x, y, (color == null ? backgroundColor : color).getRGB());
            }
        }
        return image;
    }

    private Optional<PixelCanvas.LayerSnapshot> decodeLegacyMetadataLayerSnapshot(String encoded) throws IOException {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            return Optional.of(readLayerSnapshot(input, "metadata layer-snapshot"));
        }
    }

    private void saveLayerSnapshot(File imageFile, PixelCanvas.LayerSnapshot layerSnapshot) throws IOException {
        File layerFile = layerSnapshotFile(imageFile);
        if (layerSnapshot == null) {
            deleteLayerSnapshot(imageFile);
            return;
        }

        File parent = layerFile.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent.getPath());
        }

        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(layerFile)))) {
            writeLayerSnapshot(output, layerSnapshot);
        }
    }

    private void writeLayerSnapshot(DataOutputStream output, PixelCanvas.LayerSnapshot layerSnapshot) throws IOException {
        output.writeUTF(LAYER_SNAPSHOT_MAGIC);
        output.writeInt(layerSnapshot.layers().length);
        output.writeInt(layerSnapshot.layers().length == 0 ? 0 : layerSnapshot.layers()[0].length);
        output.writeInt(layerSnapshot.activeLayerCount());
        output.writeInt(layerSnapshot.activeLayerIndex());

        for (int layer = 0; layer < layerSnapshot.layers().length; layer++) {
            output.writeBoolean(layerSnapshot.visible()[layer]);
            output.writeInt(layerSnapshot.opacityPercent()[layer]);
            output.writeInt(layerSnapshot.labels()[layer]);
            for (Color color : layerSnapshot.layers()[layer]) {
                output.writeBoolean(color != null);
                if (color != null) {
                    output.writeInt(color.getRGB());
                }
            }
        }
    }

    private PixelCanvas.LayerSnapshot readLayerSnapshot(DataInputStream input, String source) throws IOException {
        String magic = input.readUTF();
        if (!LAYER_SNAPSHOT_MAGIC.equals(magic)) {
            throw new IOException("Unsupported layer snapshot format: " + source);
        }

        int layerCount = input.readInt();
        int pixelCount = input.readInt();
        int activeLayerCount = input.readInt();
        int activeLayerIndex = input.readInt();

        if (layerCount <= 0 || pixelCount < 0) {
            throw new IOException("Invalid layer snapshot header: " + source);
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

    private void deleteLayerSnapshot(File imageFile) {
        File layerFile = layerSnapshotFile(imageFile);
        if (layerFile.isFile() && !layerFile.delete()) {
            plugin.getLogger().warning("Could not delete Paint artwork layer snapshot: " + layerFile.getPath());
        }
        File legacyLayerFile = legacyLayerSnapshotFile(imageFile);
        if (legacyLayerFile.isFile() && !legacyLayerFile.delete()) {
            plugin.getLogger().warning("Could not delete legacy Paint artwork layer snapshot: " + legacyLayerFile.getPath());
        }
        File previousLayerFile = previousCentralLayerSnapshotFile(imageFile);
        if (previousLayerFile.isFile() && !previousLayerFile.delete()) {
            plugin.getLogger().warning("Could not delete previous Paint artwork layer snapshot: " + previousLayerFile.getPath());
        }
    }

    private File layerSnapshotFile(File imageFile) {
        File imageDirectory = imageFile.getParentFile();
        File layerDirectory = imageDirectory == null
                ? new File(plugin.getDataFolder(), LAYER_DIRECTORY_NAME)
                : new File(imageDirectory, LAYER_DIRECTORY_NAME);
        return new File(layerDirectory, layerSnapshotFileName(imageFile));
    }

    private File existingLayerSnapshotFile(File imageFile) {
        File current = layerSnapshotFile(imageFile);
        if (current.isFile()) {
            return current;
        }
        File previousCentral = previousCentralLayerSnapshotFile(imageFile);
        if (previousCentral.isFile()) {
            return previousCentral;
        }
        return legacyLayerSnapshotFile(imageFile);
    }

    private File previousCentralLayerSnapshotFile(File imageFile) {
        if (artworksDirectory == null) {
            artworksDirectory = new File(plugin.getDataFolder(), USER_METADATA_DIRECTORY);
        }
        String ownerDirectoryName = imageFile.getParentFile() == null ? "unknown" : imageFile.getParentFile().getName();
        File layerDirectory = new File(new File(new File(artworksDirectory, IMAGE_DIRECTORY_NAME), LAYER_DIRECTORY_NAME), ownerDirectoryName);
        return new File(layerDirectory, layerSnapshotFileName(imageFile));
    }

    private File legacyLayerSnapshotFile(File imageFile) {
        return new File(imageFile.getParentFile(), layerSnapshotFileName(imageFile));
    }

    private String layerSnapshotFileName(File imageFile) {
        String name = imageFile.getName();
        int dot = name.lastIndexOf('.');
        String baseName = dot > 0 ? name.substring(0, dot) : name;
        return baseName + LAYER_SNAPSHOT_FILE_SUFFIX;
    }

    private boolean sameFile(File first, File second) throws IOException {
        if (first == null || second == null) {
            return false;
        }
        return first.getCanonicalFile().equals(second.getCanonicalFile());
    }

    private void deleteEmptyDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null && files.length == 0 && !directory.delete()) {
            plugin.getLogger().warning("Could not delete empty Paint artwork directory: " + directory.getPath());
        }
    }

    private static String fileNameWithoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeTitle(String title, String fallback) {
        String normalized = title == null ? "" : title.trim().replaceAll("\\p{Cntrl}", "");
        if (normalized.isBlank()) {
            normalized = fallback == null ? "" : fallback.trim();
        }
        return normalized.isBlank() ? "artwork" : normalized;
    }

    private static String titleWithSuffix(String baseTitle, int suffix) {
        String marker = " (" + suffix + ")";
        int maxBaseLength = Math.max(0, MAX_TITLE_LENGTH - marker.length());
        String trimmedBase = baseTitle.length() > maxBaseLength
                ? baseTitle.substring(0, maxBaseLength).trim()
                : baseTitle;
        if (trimmedBase.isBlank()) {
            trimmedBase = "artwork";
        }
        return trimmedBase + marker;
    }

    private static String clampTitle(String title) {
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH).trim() : title;
    }

    private static String legacyTitle(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return "";
        }
        String fileName = new File(imagePath).getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String relativePath(File file) {
        return plugin.getDataFolder().toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static String safeFileName(String value) {
        String sanitized = value == null ? "" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_");
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        return sanitized.isBlank() ? "artwork" : sanitized;
    }

    private static File uniqueImageFile(File directory, String fileNameWithoutExtension) {
        File file = new File(directory, fileNameWithoutExtension + ".png");
        for (int suffix = 2; file.exists(); suffix++) {
            file = new File(directory, fileNameWithoutExtension + "-" + suffix + ".png");
        }
        return file;
    }
}
