package org.xmas.java;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XmasChristmasParty extends JavaPlugin {
    private XmasSnowballFight snowballFight;
    private final Map<UUID, Integer> killScoreManager = new HashMap<>();

    @Override
    public void onEnable() {
        snowballFight = new XmasSnowballFight();
        snowballFight.init(this, killScoreManager);

        getServer().getPluginManager().registerEvents(snowballFight, this);
        getCommand("snowballfight").setExecutor(snowballFight);

        snowballFight.refreshGlobalScoreboard();

        getLogger().info("圣诞插件已启用！雪球伤害已调整为20点");
    }

    @Override
    public void onDisable() {
        getLogger().info("圣诞插件已禁用！");
    }

    public Map<UUID, Integer> getKillScoreManager() {
        return killScoreManager;
    }
}
