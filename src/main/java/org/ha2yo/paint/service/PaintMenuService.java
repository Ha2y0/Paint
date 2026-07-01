package org.ha2yo.paint.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.ha2yo.paint.model.PaintArtwork;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class PaintMenuService {
    public static final String MAIN_MENU_TITLE = ChatColor.DARK_GREEN + "Paint";
    public static final String CANVAS_SIZE_MENU_TITLE = ChatColor.DARK_GREEN + "Paint - 새 그림판";
    public static final String ARTWORK_LIST_TITLE = ChatColor.DARK_GREEN + "Paint - My Art";
    public static final String ARTWORK_SHOW_TITLE = ChatColor.DARK_GREEN + "Paint - Exhibit";
    private static final String MENU_MODEL_PREFIX = "menu/";
    private static final DateTimeFormatter ARTWORK_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String MODEL_BACK = MENU_MODEL_PREFIX + "back";
    private static final String MODEL_CLEAR = MENU_MODEL_PREFIX + "clear";
    private static final String MODEL_CREATE = MENU_MODEL_PREFIX + "create";
    private static final String MODEL_DECREASE = MENU_MODEL_PREFIX + "decrease";
    private static final String MODEL_GALLERY = MENU_MODEL_PREFIX + "gallery";
    private static final String MODEL_HEIGHT = MENU_MODEL_PREFIX + "height";
    private static final String MODEL_INCREASE = MENU_MODEL_PREFIX + "increase";
    private static final String MODEL_NEW = MENU_MODEL_PREFIX + "new";
    private static final String MODEL_REMOVE = MENU_MODEL_PREFIX + "remove";
    private static final String MODEL_SAVE = MENU_MODEL_PREFIX + "save";
    private static final String MODEL_SHOW = MENU_MODEL_PREFIX + "show";
    private static final String MODEL_WIDTH = MENU_MODEL_PREFIX + "width";

    private final NamespacedKey menuActionKey;
    private final NamespacedKey artworkIdKey;

    public PaintMenuService(NamespacedKey menuActionKey, NamespacedKey artworkIdKey) {
        this.menuActionKey = menuActionKey;
        this.artworkIdKey = artworkIdKey;
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, false);
    }

    public void openMainMenu(Player player, boolean confirmRemove) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_MENU_TITLE);
        inventory.setItem(10, menuItem(Material.PAPER, ChatColor.GREEN + "새 그림판", List.of(
                ChatColor.GRAY + "그림을 그릴 캔버스를 만듭니다."
        ), MenuAction.NEW, MODEL_NEW));
        inventory.setItem(12, menuItem(Material.PAPER, ChatColor.GOLD + "저장하기", List.of(
                ChatColor.GRAY + "그림을 저장합니다."
        ), MenuAction.SAVE, MODEL_SAVE));
        inventory.setItem(14, menuItem(Material.PAPER, ChatColor.GOLD + "내 그림", List.of(
                ChatColor.GRAY + "내 그림을 확인합니다."
        ), MenuAction.LIST, MODEL_GALLERY));
        inventory.setItem(16, canvasRemoveItem(confirmRemove));
        player.openInventory(inventory);
    }

    public ItemStack canvasRemoveItem(boolean confirmRemove) {
        return menuItem(Material.PAPER, confirmRemove ? ChatColor.RED + "확인" : ChatColor.RED + "캔버스 제거", List.of(
                ChatColor.GRAY + (confirmRemove ? "한 번 더 눌러 캔버스를 제거하세요." : "설치된 캔버스를 제거합니다.")
        ), MenuAction.REMOVE, MODEL_REMOVE);
    }

    public void openCanvasSizeMenu(Player player, int width, int height, int maxSize) {
        Inventory inventory = Bukkit.createInventory(null, 27, CANVAS_SIZE_MENU_TITLE);
        inventory.setItem(2, menuItem(Material.PAPER, ChatColor.GREEN + "가로 +1", List.of(
                ChatColor.GRAY + "가로를 한 칸 늘립니다.",
                ChatColor.GRAY + "최소 1, 최대 " + maxSize
        ), MenuAction.WIDTH_UP, MODEL_INCREASE));
        inventory.setItem(11, displayItem(Material.PAPER, ChatColor.GOLD + "가로 " + width, List.of(
                ChatColor.GRAY + "캔버스의 가로 크기입니다."
        ), MODEL_WIDTH, width));
        inventory.setItem(20, menuItem(Material.PAPER, ChatColor.RED + "가로 -1", List.of(
                ChatColor.GRAY + "가로를 한 칸 줄입니다.",
                ChatColor.GRAY + "최소 1, 최대 " + maxSize
        ), MenuAction.WIDTH_DOWN, MODEL_DECREASE));
        inventory.setItem(6, menuItem(Material.PAPER, ChatColor.GREEN + "세로 +1", List.of(
                ChatColor.GRAY + "세로를 한 칸 늘립니다.",
                ChatColor.GRAY + "최소 1, 최대 " + maxSize
        ), MenuAction.HEIGHT_UP, MODEL_INCREASE));
        inventory.setItem(15, displayItem(Material.PAPER, ChatColor.GOLD + "세로 " + height, List.of(
                ChatColor.GRAY + "캔버스의 세로 크기입니다."
        ), MODEL_HEIGHT, height));
        inventory.setItem(24, menuItem(Material.PAPER, ChatColor.RED + "세로 -1", List.of(
                ChatColor.GRAY + "세로를 한 칸 줄입니다.",
                ChatColor.GRAY + "최소 1, 최대 " + maxSize
        ), MenuAction.HEIGHT_DOWN, MODEL_DECREASE));
        inventory.setItem(18, menuItem(Material.PAPER, ChatColor.YELLOW + "뒤로", List.of(
                ChatColor.GRAY + "Paint 메인 메뉴로 돌아갑니다."
        ), MenuAction.BACK, MODEL_BACK));
        inventory.setItem(13, menuItem(Material.PAPER, ChatColor.GREEN + "생성하기", List.of(
                ChatColor.GRAY + "" + width + " x " + height + " 캔버스를 만듭니다.",
                ChatColor.DARK_GRAY + "클릭하면 바로 월드에 생성됩니다."
        ), MenuAction.CREATE_CANVAS, MODEL_CREATE));
        player.openInventory(inventory);
    }

    public void openArtworkList(
            Player player,
            List<PaintArtwork> artworks,
            boolean exhibitMode,
            Function<PaintArtwork, ItemStack> previewFactory
    ) {
        Inventory inventory = Bukkit.createInventory(null, 54, exhibitMode ? ARTWORK_SHOW_TITLE : ARTWORK_LIST_TITLE);
        int limit = Math.min(45, artworks.size());
        for (int index = 0; index < limit; index++) {
            PaintArtwork artwork = artworks.get(index);
            inventory.setItem(index, artworkItem(artwork, exhibitMode, previewFactory.apply(artwork)));
        }
        inventory.setItem(49, menuItem(Material.PAPER, ChatColor.YELLOW + "뒤로", List.of(
                ChatColor.GRAY + "Paint 메인 메뉴로 돌아갑니다."
        ), MenuAction.BACK, MODEL_BACK));
        player.openInventory(inventory);
    }

    public MenuAction actionFrom(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String storedAction = item.getItemMeta().getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
        if (storedAction == null) {
            return null;
        }
        try {
            return MenuAction.valueOf(storedAction);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public UUID artworkIdFrom(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String storedId = item.getItemMeta().getPersistentDataContainer().get(artworkIdKey, PersistentDataType.STRING);
        if (storedId == null) {
            return null;
        }
        try {
            return UUID.fromString(storedId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, MenuAction action) {
        return menuItem(material, name, lore, action, null);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, MenuAction action, String itemModel) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        applyItemModel(meta, itemModel);
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack artworkItem(PaintArtwork artwork, boolean exhibitMode, ItemStack previewItem) {
        ItemStack item = previewItem == null ? new ItemStack(Material.PAPER) : previewItem.clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + artwork.displayName());
        meta.setLore(List.of(
                ChatColor.GRAY + artwork.ownerName() + " / " + artwork.width() + "x" + artwork.height() + " px",
                ChatColor.DARK_GRAY + artwork.createdAt().format(ARTWORK_DATE_FORMAT),
                ChatColor.DARK_GRAY + artwork.imagePath(),
                exhibitMode ? ChatColor.YELLOW + "클릭하면 이 그림을 전시합니다." : ChatColor.GRAY + "저장된 그림 파일입니다."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (previewItem == null) {
            applyItemModel(meta, exhibitMode ? MODEL_SHOW : MODEL_GALLERY);
        }
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING,
                exhibitMode ? MenuAction.ARTWORK_SHOW.name() : MenuAction.ARTWORK_INFO.name());
        meta.getPersistentDataContainer().set(artworkIdKey, PersistentDataType.STRING, artwork.id().toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack displayItem(Material material, String name, List<String> lore) {
        return displayItem(material, name, lore, null, 1);
    }

    private ItemStack displayItem(Material material, String name, List<String> lore, String itemModel) {
        return displayItem(material, name, lore, itemModel, 1);
    }

    private ItemStack displayItem(Material material, String name, List<String> lore, String itemModel, int amount) {
        ItemStack item = new ItemStack(material);
        item.setAmount(Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        applyItemModel(meta, itemModel);
        item.setItemMeta(meta);
        return item;
    }

    private void applyItemModel(ItemMeta meta, String itemModel) {
        if (itemModel != null) {
            meta.setItemModel(new NamespacedKey("paint", itemModel));
        }
    }

    public enum MenuAction {
        NEW,
        CLEAR,
        SAVE,
        LIST,
        SHOW,
        REMOVE,
        WIDTH_DOWN,
        WIDTH_UP,
        HEIGHT_DOWN,
        HEIGHT_UP,
        BACK,
        CREATE_CANVAS,
        CANCEL,
        ARTWORK_INFO,
        ARTWORK_SHOW
    }
}
