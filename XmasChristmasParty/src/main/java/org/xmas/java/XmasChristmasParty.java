package org.xmas.java;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class XmasChristmasParty extends JavaPlugin {
    private KillScoreManager killScoreManager;
    private XmasSnowballFight snowballFight;

    @Override
    public void onEnable() {
        // 初始化管理器（只初始化一次，避免重复创建）
        killScoreManager = new KillScoreManager();
        snowballFight = new XmasSnowballFight(this, killScoreManager);

        // 注册事件（删除重复的注册代码，统一传入KillScoreManager实例）
        getServer().getPluginManager().registerEvents(snowballFight, this);
        getServer().getPluginManager().registerEvents(new XmasBlockProtect(killScoreManager), this);
        getServer().getPluginManager().registerEvents(new XmasExplosionProtect(), this);

        // 注册命令
        getCommand("startxmas").setExecutor((sender, command, label, args) -> {
            startGame(sender);
            return true;
        });

        getLogger().info(ChatColor.GREEN + "雪球大战插件已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "雪球大战插件已禁用！");
    }

    /**
     * 启动雪球大战游戏
     */
    private void startGame(org.bukkit.command.CommandSender sender) {
        if (killScoreManager.isGameRunning()) {
            sender.sendMessage(ChatColor.RED + "游戏已经开始了！");
            return;
        }

        // 初始化玩家积分
        for (Player p : Bukkit.getOnlinePlayers()) {
            killScoreManager.initPlayerScore(p);
        }

        // 启动游戏
        killScoreManager.startGame();
        // 刷新计分板
        snowballFight.refreshGlobalScoreboard();

        sender.sendMessage(ChatColor.GREEN + "雪球大战游戏开始！20分钟后结束，或有人击杀10人立即结束！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "【雪球大战】游戏开始！击杀10人或20分钟后结束，积分可兑换点券（1分=100点券）！");

        // 启动计时任务
        startGameTimer();
    }

    /**
     * 游戏计时任务
     */
    private void startGameTimer() {
        new BukkitRunnable() {
            int timeLeft = 20 * 60; // 20分钟

            @Override
            public void run() {
                if (!killScoreManager.isGameRunning()) {
                    this.cancel();
                    return;
                }

                timeLeft--;

                // 倒计时提示
                if (timeLeft == 600 || timeLeft == 300 || timeLeft == 60) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "【雪球大战】剩余" + (timeLeft / 60) + "分钟结束！");
                }

                // 时间到结束游戏
                if (timeLeft <= 0) {
                    endGame();
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    /**
     * 结束游戏
     */
    public void endGame() {
        if (!killScoreManager.isGameRunning()) return;

        killScoreManager.endGame();
        Bukkit.broadcastMessage(ChatColor.RED + "【雪球大战】游戏结束！");

        // 显示获胜者
        Player winner = getWinner();
        if (winner != null) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "获胜者：" + winner.getName() + "，击杀积分：" + killScoreManager.getKillScore(winner));
            TitleUtil.sendTitle(winner, ChatColor.GOLD + "恭喜获胜！", ChatColor.WHITE + "击杀积分：" + killScoreManager.getKillScore(winner), 10, 70, 20);
        }
    }

    /**
     * 获取获胜者
     */
    private Player getWinner() {
        Player winner = null;
        int maxScore = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            int score = killScoreManager.getKillScore(p);
            if (score > maxScore) {
                maxScore = score;
                winner = p;
            }
        }

        return winner;
    }
}
