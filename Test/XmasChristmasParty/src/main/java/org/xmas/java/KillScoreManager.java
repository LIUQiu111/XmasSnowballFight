package org.xmas.java;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillScoreManager {
    private final Map<UUID, Integer> killScores = new HashMap<>();
    private boolean gameRunning = false;
    private static final int WIN_KILL_COUNT = 10;
    private static final long GAME_DURATION = 20 * 60 * 20; // 20分钟（tick）

    public void initPlayerScore(Player player) {
        if (!killScores.containsKey(player.getUniqueId())) {
            killScores.put(player.getUniqueId(), 0);
        }
    }

    public int addKillScore(Player player) {
        UUID uuid = player.getUniqueId();
        int currentScore = killScores.getOrDefault(uuid, 0);
        killScores.put(uuid, currentScore);
        return currentScore;
    }

    public int getKillScore(Player player) {
        return killScores.getOrDefault(player.getUniqueId(), 0);
    }

    public void resetAllScores() {
        killScores.clear();
        gameRunning = false;
    }

    public void startGame() {
        gameRunning = true;
    }

    public void endGame() {
        gameRunning = false;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public int getWinKillCount() {
        return WIN_KILL_COUNT;
    }

    public long getGameDuration() {
        return GAME_DURATION;
    }
}
