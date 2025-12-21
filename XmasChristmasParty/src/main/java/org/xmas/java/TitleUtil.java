package org.xmas.java;

import org.bukkit.entity.Player;

public class TitleUtil {
    /**
     * 发送标题给玩家（1.8.8兼容）
     * @param player 玩家
     * @param title 主标题
     * @param subtitle 副标题
     * @param fadeIn 淡入时间（tick）
     * @param stay 停留时间（tick）
     * @param fadeOut 淡出时间（tick）
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            // 1.8.8反射调用标题API
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Object playerConnection = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);

            // 主标题
            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent");
            Class<?> chatSerializerClass = Class.forName("net.minecraft.server.v1_8_R3.ChatSerializer");
            Object titleComponent = chatSerializerClass.getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
            Object subtitleComponent = chatSerializerClass.getMethod("a", String.class).invoke(null, "{\"text\":\"" + subtitle + "\"}");

            Class<?> packetPlayOutTitleClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutTitle");
            Class<?> packetPlayOutTitleEnumClass = packetPlayOutTitleClass.getDeclaredClasses()[0];
            Object titleEnum = packetPlayOutTitleEnumClass.getField("TITLE").get(null);
            Object subtitleEnum = packetPlayOutTitleEnumClass.getField("SUBTITLE").get(null);

            // 发送主标题
            Object titlePacket = packetPlayOutTitleClass.getConstructor(packetPlayOutTitleEnumClass, iChatBaseComponentClass, int.class, int.class, int.class)
                    .newInstance(titleEnum, titleComponent, fadeIn, stay, fadeOut);
            playerConnection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server.v1_8_R3.Packet")).invoke(playerConnection, titlePacket);

            // 发送副标题
            Object subtitlePacket = packetPlayOutTitleClass.getConstructor(packetPlayOutTitleEnumClass, iChatBaseComponentClass)
                    .newInstance(subtitleEnum, subtitleComponent);
            playerConnection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server.v1_8_R3.Packet")).invoke(playerConnection, subtitlePacket);
        } catch (Exception e) {
            // 反射失败时发送聊天消息
            player.sendMessage(title);
            player.sendMessage(subtitle);
        }
    }
}
