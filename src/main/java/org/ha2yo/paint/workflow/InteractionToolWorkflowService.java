package org.ha2yo.paint.workflow;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.ha2yo.paint.Paint;
import org.ha2yo.paint.model.PlayerCanvas;
import org.ha2yo.paint.model.tool.Tool;
import org.ha2yo.paint.service.AdvancedToolSessionService;
import org.ha2yo.paint.service.CanvasLookService.LookResult;
import org.ha2yo.paint.service.DrawingSessionService;

import java.util.Map;
import java.util.UUID;

public final class InteractionToolWorkflowService {
    private final Paint plugin;
    private final AdvancedToolWorkflowService advancedToolWorkflow;
    private final DrawingSessionService drawingSessions;
    private final Map<UUID, Tool> selectedTools;

    public InteractionToolWorkflowService(
            Paint plugin,
            AdvancedToolWorkflowService advancedToolWorkflow,
            DrawingSessionService drawingSessions,
            Map<UUID, Tool> selectedTools
    ) {
        this.plugin = plugin;
        this.advancedToolWorkflow = advancedToolWorkflow;
        this.drawingSessions = drawingSessions;
        this.selectedTools = selectedTools;
    }

    public boolean isPrimaryUseHand(EquipmentSlot hand, Player player) {
        if (hand == EquipmentSlot.HAND) {
            return true;
        }
        return hand == EquipmentSlot.OFF_HAND
                && player.getInventory().getItemInMainHand().getType() == Material.AIR;
    }

    public void beginAdvancedHold(Player player, LookResult look, Tool tool) {
        if (advancedToolWorkflow != null) {
            advancedToolWorkflow.beginHold(player, look, tool);
        }
    }

    public void beginPendingAdvancedHold(Player player, Tool tool) {
        if (advancedToolWorkflow != null) {
            advancedToolWorkflow.beginPendingHold(player, tool);
        }
    }

    public void stopDrawing(UUID playerId) {
        drawingSessions.stop(playerId);
        if (advancedToolWorkflow != null) {
            advancedToolWorkflow.cancelHold(playerId);
        }
    }

    public void forceShieldUse(Player player) {
        if (player.getInventory().getItemInMainHand().getType() != Material.SHIELD) {
            return;
        }

        player.startUsingItem(EquipmentSlot.HAND);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()
                    || !selectedTools.getOrDefault(player.getUniqueId(), Tool.PENCIL).isContinuous()
                    || player.getInventory().getItemInMainHand().getType() != Material.SHIELD) {
                return;
            }
            player.startUsingItem(EquipmentSlot.HAND);
        });
    }
}
