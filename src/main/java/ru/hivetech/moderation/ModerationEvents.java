package ru.hivetech.moderation;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public final class ModerationEvents {
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        ModerationBridge bridge = HiveTechModeration.getBridge();
        if (bridge != null) {
            bridge.onPlayerLogin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        ModerationBridge bridge = HiveTechModeration.getBridge();
        if (bridge == null) {
            return;
        }

        EntityPlayerMP player = event.getPlayer();
        ModerationAction mute = bridge.getMute(player);
        if (mute == null) {
            return;
        }

        event.setCanceled(true);
        player.sendMessage(new TextComponentString(bridge.formatMuteMessage(mute)));
    }
}
