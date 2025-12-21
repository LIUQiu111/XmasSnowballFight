package org.xmas.java;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

// 仅OP玩家可破坏/放置方块，非OP玩家全程禁止
public class XmasBlockProtect implements Listener {
    // 引入KillScoreManager（如果需要游戏状态提示，保留；不需要可删除）
    private final KillScoreManager killScoreManager;

    // 构造方法注入KillScoreManager（插件初始化时传入）
    public XmasBlockProtect(KillScoreManager killScoreManager) {
        this.killScoreManager = killScoreManager;
    }

    /**
     * 禁止非OP玩家放置方块
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        // 判断玩家是否为OP
        if (!e.getPlayer().isOp()) {
            e.setCancelled(true);
            // 根据游戏状态显示不同提示（保留原有逻辑，不需要可替换为固定提示）
            if (killScoreManager.isGameRunning()) {
                e.getPlayer().sendMessage(ChatColor.RED + "雪球大战期间禁止放置方块！你不是OP，无操作权限！");
            } else {
                e.getPlayer().sendMessage(ChatColor.RED + "你不是OP，禁止放置任何方块！");
            }
        }
        // OP玩家不做限制，可正常放置方块
    }

    /**
     * 禁止非OP玩家破坏方块
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        // 判断玩家是否为OP
        if (!e.getPlayer().isOp()) {
            e.setCancelled(true);
            // 根据游戏状态显示不同提示（保留原有逻辑，不需要可替换为固定提示）
            if (killScoreManager.isGameRunning()) {
                e.getPlayer().sendMessage(ChatColor.RED + "雪球大战期间禁止破坏方块！你不是OP，无操作权限！");
            } else {
                e.getPlayer().sendMessage(ChatColor.RED + "你不是OP，禁止破坏任何方块！");
            }
        }
        // OP玩家不做限制，可正常破坏方块
    }
}
