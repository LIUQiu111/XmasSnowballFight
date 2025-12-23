package org.xmas.java;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 击杀积分管理核心类，修复积分不累计、游戏状态判断错误
public class KillScoreManager {
    private final Map<UUID, Integer> killScores = new HashMap<>();
    private boolean gameRunning = false;
    private final int WIN_KILL_COUNT = 10; // 获胜击杀数

    /**
     * 初始化玩家积分
     */
    public void initPlayerScore(Player player) {
        killScores.put(player.getUniqueId(), 0);
    }

    /**
     * 增加玩家击杀积分
     */
    public int addKillScore(Player player) {
        UUID uuid = player.getUniqueId();
        int currentScore = killScores.getOrDefault(uuid, 0);
        currentScore++; // 积分+1，修复累计问题
        killScores.put(uuid, currentScore);
        return currentScore; // 返回更新后的积分
    }

    /**
     * 获取玩家击杀积分
     */
    public int getKillScore(Player player) {
        return killScores.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 启动游戏
     */
    public void startGame() {
        gameRunning = true;
        killScores.clear(); // 清空历史积分
    }

    /**
     * 结束游戏
     */
    public void endGame() {
        gameRunning = false;
    }

    /**
     * 判断游戏是否运行
     */
    public boolean isGameRunning() {
        return gameRunning;
    }

    /**
     * 获取获胜所需击杀数
     */
    public int getWinKillCount() {
        return WIN_KILL_COUNT;
    }
}
