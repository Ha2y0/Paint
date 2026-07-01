package org.ha2yo.paint.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.ha2yo.paint.model.session.CanvasSize;
import org.ha2yo.paint.model.station.StationPanelSlot;
import org.ha2yo.paint.workflow.PaintCommandWorkflowService;

import java.util.List;
import java.util.Locale;

public final class PaintCommand implements TabExecutor {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "gallery",
            "new",
            "clear",
            "remove",
            "save",
            "list",
            "show",
            "exhibits",
            "brush",
            "color",
            "station"
    );
    private static final List<String> EXHIBIT_SUBCOMMANDS = List.of("reload", "remove");
    private static final List<String> STATION_SUBCOMMANDS = List.of("canvas", "gallery", "control", "list", "remove");
    private static final List<String> CANVAS_SIZE_SUGGESTIONS = List.of("5", "7", "10");
    private static final List<String> CONTROL_LAYOUT_SUGGESTIONS = List.of("horizontal", "vertical");

    private final PaintCommandWorkflowService controller;

    public PaintCommand(PaintCommandWorkflowService controller) {
        this.controller = controller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 이 명령어를 사용할 수 있습니다.");
            return true;
        }
        if (!player.hasPermission("paint.use")) {
            player.sendMessage(ChatColor.RED + "Paint 명령어를 사용할 수 없습니다.");
            return true;
        }
        if (!controller.isFreeMode(player) && !controller.canUseAdminPaintCommands(player) && !isStationCommand(args)) {
            player.sendMessage(ChatColor.YELLOW + "현재 모드에서는 사용 불가한 명령어입니다.");
            return true;
        }
        if (controller.isPaintUiOpen(player.getUniqueId())) {
            if (args.length == 0) {
                controller.repositionPaintUi(player);
                return true;
            }
            if (!controller.canUseAdminPaintCommands(player)) {
                player.sendMessage(ChatColor.YELLOW + "이미 Paint 창이 열려 있습니다. /paint 만 입력하면 현재 위치로 다시 소환됩니다.");
                return true;
            }
            controller.resetPaintUiForSubcommand(player);
        }
        if (args.length == 0) {
            controller.openPaintMainPanel(player, false);
            return true;
        }
        if (!controller.canUseAdminPaintCommands(player)) {
            player.sendMessage(ChatColor.RED + "일반 유저는 /paint 명령어만 사용할 수 있습니다.");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gallery" -> handleGalleryCommand(player, label, args);
            case "new" -> handleNewCommand(player, label, args);
            case "clear" -> handleClearCommand(player);
            case "remove" -> handleRemoveCommand(player);
            case "save" -> handleSaveArtworkCommand(player, args);
            case "list" -> controller.startArtworkPreview(player, false);
            case "show" -> controller.startArtworkPreview(player, true);
            case "exhibits" -> handleExhibitsCommand(player, label, args);
            case "brush" -> handleBrushCommand(player, label, args);
            case "color" -> handleColorCommand(player, label, args);
            case "station" -> handleStationCommand(player, label, args);
            default -> sendHelp(player, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player player && !controller.canUseAdminPaintCommands(player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "gallery".equalsIgnoreCase(args[0])) {
            return filter(controller.artworkOwnerNames(), args[1]);
        }
        if (args.length == 2 && "exhibits".equalsIgnoreCase(args[0])) {
            return filter(EXHIBIT_SUBCOMMANDS, args[1]);
        }
        if (args.length == 2 && "station".equalsIgnoreCase(args[0])) {
            return filter(STATION_SUBCOMMANDS, args[1]);
        }
        if (args.length == 4 && "station".equalsIgnoreCase(args[0]) && "control".equalsIgnoreCase(args[1])) {
            return filter(CONTROL_LAYOUT_SUGGESTIONS, args[3]);
        }
        if (args.length == 2 && "color".equalsIgnoreCase(args[0])) {
            return filter(controller.paletteNames(), args[1]);
        }
        if ("new".equalsIgnoreCase(args[0]) && (args.length == 2 || args.length == 3)) {
            return filter(CANVAS_SIZE_SUGGESTIONS, args[args.length - 1]);
        }
        return List.of();
    }

    private boolean isStationCommand(String[] args) {
        return args.length > 0 && "station".equalsIgnoreCase(args[0]);
    }

    private void handleNewCommand(Player player, String label, String[] args) {
        if (!controller.canCreateNewCanvas(player)) {
            return;
        }
        CanvasSize size = parseCanvasSize(player, label, args);
        if (size == null) {
            return;
        }
        controller.startCanvasPlacementPreview(player, size.width(), size.height());
    }

    private CanvasSize parseCanvasSize(Player player, String label, String[] args) {
        if (args.length == 1) {
            return new CanvasSize(controller.defaultCanvasBlockWidth(), controller.defaultCanvasBlockHeight());
        }
        if (args.length != 3) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " " + args[0].toLowerCase(Locale.ROOT) + " [너비] [높이]");
            player.sendMessage(ChatColor.GRAY + "크기 범위는 1-" + controller.maxCanvasBlockSize() + ", 기본값은 "
                    + controller.defaultCanvasBlockWidth() + "x" + controller.defaultCanvasBlockHeight() + " 입니다.");
            return null;
        }

        try {
            int width = Integer.parseInt(args[1]);
            int height = Integer.parseInt(args[2]);
            if (!controller.isValidCanvasBlockSizeForCommand(width) || !controller.isValidCanvasBlockSizeForCommand(height)) {
                player.sendMessage(ChatColor.RED + "크기는 1-" + controller.maxCanvasBlockSize() + " 사이로 입력해 주세요.");
                return null;
            }
            return new CanvasSize(width, height);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "숫자로 입력해 주세요.");
            return null;
        }
    }

    private void handleClearCommand(Player player) {
        if (controller.clearCanvas(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Paint 캔버스를 비웠습니다.");
        }
    }

    private void handleRemoveCommand(Player player) {
        if (controller.removeCanvas(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Paint 캔버스를 제거했습니다.");
            return;
        }
        player.sendMessage(ChatColor.RED + "제거할 캔버스가 없습니다.");
    }

    private void handleSaveArtworkCommand(Player player, String[] args) {
        if (args.length > 1) {
            controller.saveArtworkWithName(player, joinedArtworkName(args));
            return;
        }
        controller.beginArtworkNameInput(player);
    }

    private String joinedArtworkName(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void handleGalleryCommand(Player player, String label, String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " gallery <플레이어명>");
            return;
        }
        controller.openArtworkPreviewForOwnerName(player, args[1].trim());
    }

    private void handleExhibitsCommand(Player player, String label, String[] args) {
        if (args.length >= 2 && "reload".equalsIgnoreCase(args[1])) {
            controller.reloadArtworkDisplays(player);
            return;
        }
        if (args.length >= 2 && ("remove".equalsIgnoreCase(args[1]) || "delete".equalsIgnoreCase(args[1]))) {
            controller.startExhibitRemovalMode(player);
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "/" + label + " exhibits reload" + ChatColor.GRAY + " - 전시품 데이터를 다시 불러옵니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " exhibits remove" + ChatColor.GRAY + " - 바라보고 있는 전시품을 제거 모드로 삭제합니다.");
    }

    private void handleBrushCommand(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " brush <1-" + controller.maxBrushRadius() + ">");
            return;
        }
        try {
            controller.setBrushRadius(player, Integer.parseInt(args[1]));
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "숫자로 입력해 주세요.");
        }
    }

    private void handleColorCommand(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " color <색상>");
            player.sendMessage(ChatColor.GRAY + "색상: " + String.join(", ", controller.paletteNames()));
            return;
        }
        controller.selectPaletteColor(player, args[1]);
    }

    private void handleStationCommand(Player player, String label, String[] args) {
        if (args.length < 2) {
            sendStationHelp(player, label);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "canvas" -> handleStationCanvasCommand(player, label, args);
            case "gallery" -> handleStationGalleryCommand(player, label, args);
            case "control" -> handleStationControlCommand(player, label, args);
            case "list" -> controller.listManualStations(player);
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "사용법: /" + label + " station remove <번호>");
                    return;
                }
                controller.removeManualStation(player, args[2]);
            }
            default -> sendStationHelp(player, label);
        }
    }

    private void handleStationCanvasCommand(Player player, String label, String[] args) {
        if (args.length != 5) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " station canvas <번호> <너비> <높이>");
            return;
        }
        try {
            int width = Integer.parseInt(args[3]);
            int height = Integer.parseInt(args[4]);
            if (!controller.isValidCanvasBlockSizeForCommand(width) || !controller.isValidCanvasBlockSizeForCommand(height)) {
                player.sendMessage(ChatColor.RED + "크기는 1-" + controller.maxCanvasBlockSize() + " 사이로 입력해 주세요.");
                return;
            }
            controller.setManualStationCanvas(player, args[2], width, height);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "숫자로 입력해 주세요.");
        }
    }

    private void handleStationGalleryCommand(Player player, String label, String[] args) {
        if (args.length != 3) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " station gallery <번호>");
            return;
        }
        controller.setManualStationGallery(player, args[2]);
    }

    private void handleStationControlCommand(Player player, String label, String[] args) {
        if (args.length != 3 && args.length != 4) {
            player.sendMessage(ChatColor.RED + "사용법: /" + label + " station control <번호> [horizontal|vertical]");
            return;
        }
        StationPanelSlot.Layout layout = parseControlLayout(player, label, args);
        if (layout == null) {
            return;
        }
        controller.setManualStationControl(player, args[2], layout);
    }

    private StationPanelSlot.Layout parseControlLayout(Player player, String label, String[] args) {
        if (args.length == 3) {
            return StationPanelSlot.Layout.HORIZONTAL;
        }
        String value = args[3].toLowerCase(Locale.ROOT);
        if (value.equals("horizontal")) {
            return StationPanelSlot.Layout.HORIZONTAL;
        }
        if (value.equals("vertical")) {
            return StationPanelSlot.Layout.VERTICAL;
        }
        player.sendMessage(ChatColor.RED + "사용법: /" + label + " station control <번호> [horizontal|vertical]");
        return null;
    }

    private void sendStationHelp(Player player, String label) {
        player.sendMessage(ChatColor.GOLD + "Paint 수동 자리 명령어");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " station canvas <번호> <너비> <높이>" + ChatColor.GRAY + " - 바라보는 블록을 캔버스 위치로 저장합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " station gallery <번호>" + ChatColor.GRAY + " - 현재 위치 기준으로 갤러리 위치를 저장합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " station control <번호> [horizontal|vertical]" + ChatColor.GRAY + " - 바라보는 벽에 조작판 위치와 배치를 저장합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " station list" + ChatColor.GRAY + " - 수동 자리 목록을 봅니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " station remove <번호>" + ChatColor.GRAY + " - 수동 자리를 삭제합니다.");
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(ChatColor.GOLD + "Paint 명령어");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " new [너비] [높이]" + ChatColor.GRAY
                + " - 새 캔버스를 만듭니다. 기본값은 " + controller.defaultCanvasBlockWidth() + "x" + controller.defaultCanvasBlockHeight()
                + ", 최대 크기는 " + controller.maxCanvasBlockSize() + "x" + controller.maxCanvasBlockSize() + " 입니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " clear" + ChatColor.GRAY + " - 현재 캔버스를 비웁니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " remove" + ChatColor.GRAY + " - 현재 캔버스를 제거합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " save [이름]" + ChatColor.GRAY + " - 현재 캔버스를 PNG 그림으로 저장합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " list" + ChatColor.GRAY + " - 저장한 그림 목록을 엽니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " show" + ChatColor.GRAY + " - 저장한 그림을 전시합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " exhibits remove" + ChatColor.GRAY + " - 바라보고 있는 전시품을 제거 모드로 삭제합니다.");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " station" + ChatColor.GRAY + " - 수동 자리 위치를 관리합니다.");
    }

    private List<String> filter(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .sorted()
                .toList();
    }
}
