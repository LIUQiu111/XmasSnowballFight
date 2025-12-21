package org.xmas.java;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 1.8.8完全兼容，无任何编译错误和警告
public class XmasSnowballFight implements Listener {
    private final XmasChristmasParty plugin;
    private final KillScoreManager killScoreManager;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Integer> playerHealthCache = new HashMap<>();
    private final Map<UUID, Integer> playerScoreCache = new HashMap<>();

    private static final int MAX_HEALTH = 300;
    private static final int SNOWBALL_DAMAGE = 20;
    private final Map<UUID, String> objectiveIds = new HashMap<>();

    // 预定义颜色常量（提前转为字符串，避免拼接错误）
    private static final String COLOR_GRAY = ChatColor.GRAY.toString();
    private static final String COLOR_WHITE = ChatColor.WHITE.toString();
    private static final String COLOR_BLACK = ChatColor.BLACK.toString();
    private static final String COLOR_RED = ChatColor.RED.toString();
    private static final String COLOR_GREEN = ChatColor.GREEN.toString();
    private static final String COLOR_YELLOW = ChatColor.YELLOW.toString();
    private static final String COLOR_BLUE = ChatColor.BLUE.toString();

    // 计分板固定文本（提前拼接好，无类型问题）
    private static final String SEP_LINE = COLOR_GRAY + "——————";
    private static final String SCORE_TITLE = COLOR_WHITE + "击杀积分:";
    private static final String EMPTY_LINE = COLOR_BLACK + " ";
    private static final String HEALTH_TITLE = COLOR_RED + "当前血量:";
    private static final String TIP_LINE = COLOR_BLUE + "1分=100点券";
    private static final String FULL_HEALTH_TEXT = COLOR_GREEN + "300/300";
    private static final String ZERO_SCORE_TEXT = COLOR_YELLOW + "0";

    public XmasSnowballFight(XmasChristmasParty plugin, KillScoreManager killScoreManager) {
        this.plugin = plugin;
        this.killScoreManager = killScoreManager;

        // 初始化在线玩家
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            playerHealthCache.put(uuid, MAX_HEALTH);
            playerScoreCache.put(uuid, 0);
            objectiveIds.put(uuid, "xmas_" + uuid.toString().substring(0, 8));
            applySaturation(p); // 添加饱食Buff
        }
    }

    /**
     * 1.8.8原生饱食Buff设置（解决饥饿问题）
     */
    private void applySaturation(Player player) {
        // 强制设置饱食相关数值
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        player.setExhaustion(0.0F);

        // 清除冲突效果
        player.removePotionEffect(PotionEffectType.SATURATION);
        player.removePotionEffect(PotionEffectType.HUNGER);

        // 1.8.8 Buff必须显示粒子才能生效，定时刷新避免失效
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 72000, 2, true, false));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) applySaturation(player);
        }, 36000);
    }

    /**
     * 创建1.8.8原生计分板
     */
    private void createScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerScoreboards.containsKey(uuid)) return;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();
        // 创建唯一的Objective
        Objective obj = board.registerNewObjective(objectiveIds.get(uuid), "dummy");
        obj.setDisplayName(COLOR_RED + "❄雪球大战❄");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 使用预定义的文本常量，无拼接错误
        setScore(obj, SEP_LINE, 7);
        setScore(obj, SCORE_TITLE, 6);
        setScore(obj, ZERO_SCORE_TEXT, 5);
        setScore(obj, EMPTY_LINE, 4);
        setScore(obj, HEALTH_TITLE, 3);
        setScore(obj, FULL_HEALTH_TEXT, 2);
        setScore(obj, TIP_LINE, 1);

        playerScoreboards.put(uuid, board);
        player.setScoreboard(board);
    }

    /**
     * 1.8.8原生计分板行设置方法
     */
    private void setScore(Objective obj, String text, int scoreValue) {
        Score score = obj.getScore(text);
        score.setScore(scoreValue);
    }

    /**
     * 清除计分板中指定前缀的行
     */
    private void clearScoreboardLines(Scoreboard board, String prefix) {
        for (String entry : board.getEntries()) {
            if (entry.startsWith(prefix)) {
                board.resetScores(entry);
            }
        }
    }

    /**
     * 安全的字符串拼接工具方法（彻底解决类型问题）
     */
    private String formatText(String color, Object... args) {
        StringBuilder sb = new StringBuilder(color);
        for (Object arg : args) {
            sb.append(arg.toString());
        }
        return sb.toString();
    }

    /**
     * 更新计分板数值（使用工具方法拼接文本）
     */
    private void updateScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerScoreboards.containsKey(uuid)) {
            createScoreboard(player);
            return;
        }

        Scoreboard board = playerScoreboards.get(uuid);
        Objective obj = board.getObjective(objectiveIds.get(uuid));
        if (obj == null) return;

        int health = playerHealthCache.getOrDefault(uuid, MAX_HEALTH);
        int score = playerScoreCache.getOrDefault(uuid, 0);

        // 清除旧的数值行
        clearScoreboardLines(board, COLOR_YELLOW);
        clearScoreboardLines(board, COLOR_GREEN);

        // 使用工具方法安全拼接，无类型错误
        String scoreText = formatText(COLOR_YELLOW, score);
        String healthText = formatText(COLOR_GREEN, health, "/", MAX_HEALTH);

        // 更新新的数值行
        setScore(obj, scoreText, 5);
        setScore(obj, healthText, 2);
    }

    /**
     * 设置玩家血量并更新计分板
     */
    public void setPlayerHealth(Player p, int health) {
        UUID uuid = p.getUniqueId();
        int finalHealth = Math.max(0, Math.min(health, MAX_HEALTH));
        playerHealthCache.put(uuid, finalHealth);

        // 使用工具方法发送提示
        String healthMsg = formatText(COLOR_YELLOW, "当前血量：", COLOR_RED, finalHealth, "/", MAX_HEALTH);
        p.sendMessage(healthMsg);
        updateScoreboard(p);

        if (finalHealth <= 0) {
            eliminatePlayer(p);
        }
    }

    /**
     * 获取玩家血量（从缓存读取）
     */
    public int getPlayerHealth(Player p) {
        return playerHealthCache.getOrDefault(p.getUniqueId(), MAX_HEALTH);
    }

    /**
     * 增加击杀积分并更新计分板
     */
    public void addKillScore(Player p) {
        UUID uuid = p.getUniqueId();
        int newScore = killScoreManager.addKillScore(p) + 1;
        playerScoreCache.put(uuid, newScore);

        // 使用工具方法发送提示
        String scoreMsg = formatText(COLOR_GREEN, "击杀积分+1！当前积分：", newScore);
        p.sendMessage(scoreMsg);
        updateScoreboard(p);
    }

    /**
     * 玩家复活处理
     */
    private void eliminatePlayer(Player p) {
        p.sendMessage(formatText(COLOR_RED, "你被雪球砸倒了！已淘汰！"));
        p.teleport(Bukkit.getWorld("world").getSpawnLocation());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setPlayerHealth(p, MAX_HEALTH);
            p.sendMessage(formatText(COLOR_GREEN, "你已复活，重新加入战斗！"));
        }, 20);
    }

    // ===================== 事件监听 =====================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // 初始化玩家数据
        playerHealthCache.put(uuid, MAX_HEALTH);
        playerScoreCache.put(uuid, 0);
        objectiveIds.put(uuid, "xmas_" + uuid.toString().substring(0, 8));

        // 立即添加饱食Buff和创建计分板
        applySaturation(p);
        createScoreboard(p);

        if (killScoreManager.isGameRunning()) {
            p.sendMessage(formatText(COLOR_GREEN, "欢迎加入雪球大战！初始血量：", MAX_HEALTH));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // 清理玩家数据
        playerScoreboards.remove(uuid);
        playerHealthCache.remove(uuid);
        playerScoreCache.remove(uuid);
        objectiveIds.remove(uuid);

        // 恢复默认计分板
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    @EventHandler
    public void onSnowballHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Snowball) || !(e.getEntity() instanceof Player)) return;

        Snowball snowball = (Snowball) e.getDamager();
        if (!(snowball.getShooter() instanceof Player)) return;

        Player shooter = (Player) snowball.getShooter();
        Player victim = (Player) e.getEntity();
        e.setCancelled(true);

        if (!killScoreManager.isGameRunning()) {
            shooter.sendMessage(formatText(COLOR_RED, "雪球大战尚未开始！"));
            return;
        }

        // 计算伤害并更新状态
        int currentHealth = getPlayerHealth(victim);
        int newHealth = currentHealth - SNOWBALL_DAMAGE;
        setPlayerHealth(victim, newHealth);

        if (newHealth <= 0) {
            addKillScore(shooter);
            int shooterScore = playerScoreCache.get(shooter.getUniqueId());

            String shooterMsg = formatText(COLOR_GREEN, "你击杀了", victim.getName(), "！当前积分：", shooterScore);
            shooter.sendMessage(shooterMsg);

            String victimMsg = formatText(COLOR_RED, "你被", shooter.getName(), "击杀了！");
            victim.sendMessage(victimMsg);

            if (shooterScore >= killScoreManager.getWinKillCount()) {
                killScoreManager.endGame();
                plugin.endGame();
            }
        } else {
            String shooterHitMsg = formatText(COLOR_GREEN, "击中", victim.getName(), "，造成", SNOWBALL_DAMAGE, "点伤害！");
            shooter.sendMessage(shooterHitMsg);

            String victimHitMsg = formatText(COLOR_RED, "被", shooter.getName(), "击中，损失", SNOWBALL_DAMAGE, "点血量！");
            victim.sendMessage(victimHitMsg);
        }
    }

    /**
     * 刷新所有玩家计分板
     */
    public void refreshGlobalScoreboard() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateScoreboard(p);
        }
    }

    /**
     * 兼容旧方法名
     */
    public void updateAllPlayerScoreboards() {
        refreshGlobalScoreboard();
    }
}
