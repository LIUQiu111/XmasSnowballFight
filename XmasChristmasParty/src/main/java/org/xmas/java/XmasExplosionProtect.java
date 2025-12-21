package org.xmas.java;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class XmasExplosionProtect implements Listener {
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        // 游戏期间禁止爆炸破坏
        if (new KillScoreManager().isGameRunning()) {
            e.blockList().clear();
            e.setCancelled(true);
        }
    }
}
