package org.ha2yo.paint.service;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.ha2yo.paint.Paint;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class ArtworkSaveDialogService {
    private final Paint plugin;
    private final String inputKey;

    public ArtworkSaveDialogService(Paint plugin, String inputKey) {
        this.plugin = plugin;
        this.inputKey = inputKey;
    }

    public void showNameDialog(Player player, String initialName, BiConsumer<Player, String> saveHandler) {
        UUID playerId = player.getUniqueId();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("그림 저장"))
                        .externalTitle(Component.text("그림 저장"))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .inputs(List.of(DialogInput.text(inputKey, Component.text("그림 이름"))
                                .initial(initialName)
                                .maxLength(32)
                                .width(260)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("저장"))
                                .tooltip(Component.text("입력한 이름으로 저장합니다."))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) -> {
                                    String title = response.getText(inputKey);
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        Player current = Bukkit.getPlayer(playerId);
                                        if (current != null) {
                                            saveHandler.accept(current, title);
                                        }
                                    });
                                }, ClickCallback.Options.builder().build()))
                                .build(),
                        ActionButton.builder(Component.text("취소"))
                                .tooltip(Component.text("저장을 취소합니다."))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            Player current = Bukkit.getPlayer(playerId);
                                            if (current != null) {
                                                current.sendMessage(ChatColor.YELLOW + "그림 저장을 취소했습니다.");
                                            }
                                        }), ClickCallback.Options.builder().build()))
                                .build())));
        player.showDialog(dialog);
    }

    public void showConflictDialog(
            Player player,
            String title,
            String proposedTitle,
            BiConsumer<Player, String> acceptHandler,
            BiConsumer<Player, String> rejectHandler
    ) {
        UUID playerId = player.getUniqueId();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("그림 이름 바꾸기"))
                        .externalTitle(Component.text("그림 이름 바꾸기"))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text(String.format("\"%s\"에서 \"%s\"(으)로 이름을 변경하시겠습니까?", title, proposedTitle))),
                                DialogBody.plainMessage(Component.text("이 위치에 이름이 같은 그림이 있습니다."))
                        ))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("예"))
                                .tooltip(Component.text("제안된 이름으로 저장합니다."))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            Player current = Bukkit.getPlayer(playerId);
                                            if (current != null) {
                                                acceptHandler.accept(current, proposedTitle);
                                            }
                                        }), ClickCallback.Options.builder().build()))
                                .build(),
                        ActionButton.builder(Component.text("아니요"))
                                .tooltip(Component.text("이름 입력으로 돌아갑니다."))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            Player current = Bukkit.getPlayer(playerId);
                                            if (current != null) {
                                                rejectHandler.accept(current, title);
                                            }
                                        }), ClickCallback.Options.builder().build()))
                                .build())));
        player.showDialog(dialog);
    }
}
