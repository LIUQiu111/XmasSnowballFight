package org.xmas.java;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 圣诞雪球大战核心类 - 1.8.8兼容最终版
 * 功能：自动启动、10杀胜利、无范围挖雪、随机雪球刷新、防刷分
 * 作者：初开
 */
public class XmasSnowballFight implements Listener, CommandExecutor {
    private XmasChristmasParty mainPlugin;
    private Map<UUID, Integer> killScoreManager;
    private boolean gameRunning = false;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Integer> playerGameHealth = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();
    private final List<Location> chestLocations = new ArrayList<>();
    private final Map<UUID, Integer> selfKillCount = new HashMap<>();

    // 自动启动相关变量
    private boolean autoStartPending = false; // 是否有自动启动倒计时
    private int minPlayersForAutoStart = 2;   // 自动启动最小人数
    private long autoStartDelay = 30 * 20L;   // 自动启动延迟（30秒=600ticks）

    // 核心配置常量
    private static final int MAX_GAME_HEALTH = 200;
    private static final double SNOWBALL_REAL_DAMAGE = 0.01;
    private static final int SNOWBALL_GAME_DAMAGE = 20;
    private static final String SCOREBOARD_TITLE = ChatColor.RED + "❄圣诞雪球大战❄";
    private static final int WIN_KILLS = 10; // 胜利目标改为10杀

    // 雪球补给常量
    private static final int SNOWBALL_SUPPLY_AMOUNT = 16;
    private static final long SNOWBALL_SUPPLY_INTERVAL = 200;
    private static final long CHEST_REFRESH_INTERVAL = 600;
    private static final int CHEST_SNOWBALL_MIN = 0;
    private static final int CHEST_SNOWBALL_MAX = 50;
    private static final int GAME_REGION_RADIUS = 50; // 仅用于箱子扫描，不再限制挖雪

    // 雪层挖掘常量
    private static final int SNOW_BLOCK_DROP = 4;
    private static final int SNOW_LAYER_DROP = 1;
    private static final int MAX_SELF_KILL = 2;

    // 颜色常量
    private static final String C_GRAY = ChatColor.GRAY.toString();
    private static final String C_WHITE = ChatColor.WHITE.toString();
    private static final String C_RED = ChatColor.RED.toString();
    private static final String C_GREEN = ChatColor.GREEN.toString();
    private static final String C_YELLOW = ChatColor.YELLOW.toString();
    private static final String C_AQUA = ChatColor.AQUA.toString();
    private static final String C_GOLD = ChatColor.GOLD.toString();
    private static final String C_BOLD = ChatColor.BOLD.toString();

    // OP白名单
    private static final String[] OP_WHITELIST = {"YourGameID", "AdminID"};

    public XmasSnowballFight() {
    }

    public void init(XmasChristmasParty mainPlugin, Map<UUID, Integer> killScoreManager) {
        this.mainPlugin = mainPlugin;
        this.killScoreManager = killScoreManager;

        for (Player player : Bukkit.getOnlinePlayers()) {
            initPlayerData(player);
        }
        scanChestLocations();
        startSnowballSupplyTask();
        startChestRefreshTask();
        checkAutoStartConditions(); // 初始化时检查自动启动条件
    }

    /**
     * 扫描游戏区域内的箱子
     */
    private void scanChestLocations() {
        chestLocations.clear();
        Location spawn = Bukkit.getWorld("world").getSpawnLocation();
        int radius = GAME_REGION_RADIUS;

        int spawnX = spawn.getBlockX();
        int spawnY = spawn.getBlockY();
        int spawnZ = spawn.getBlockZ();

        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= 255; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = spawn.getWorld().getBlockAt(spawnX + x, spawnY + y, spawnZ + z);
                    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                        chestLocations.add(block.getLocation());
                    }
                }
            }
        }
        mainPlugin.getLogger().info("扫描到游戏区域内箱子数量：" + chestLocations.size());
    }

    /**
     * 玩家物品栏雪球自动补给
     */
    private void startSnowballSupplyTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 移除区域限制，所有在线玩家都能获得补给
                    supplySnowballs(player);
                }
            }
        }.runTaskTimer(mainPlugin, 0, SNOWBALL_SUPPLY_INTERVAL);
        mainPlugin.getLogger().info("玩家雪球补给任务启动（每10秒一次）");
    }

    /**
     * 箱子雪球刷新任务
     */
    private void startChestRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) return;
                refreshChestSnowballs();
            }
        }.runTaskTimer(mainPlugin, 0, CHEST_REFRESH_INTERVAL);
        mainPlugin.getLogger().info("箱子雪球刷新任务启动（每30秒一次）");
    }

    /**
     * 为玩家补给雪球
     */
    private void supplySnowballs(Player player) {
        try {
            int snowballCount = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.SNOW_BALL) {
                    snowballCount += item.getAmount();
                }
            }

            if (snowballCount < 8) {
                player.getInventory().addItem(new ItemStack(Material.SNOW_BALL, SNOWBALL_SUPPLY_AMOUNT));
                player.playSound(player.getLocation(), Sound.ITEM_PICKUP, 0.5f, 1.0f);
                if (snowballCount == 0) {
                    player.sendMessage(C_AQUA + "【雪球补给】" + C_WHITE + "获得了 " + C_GREEN + SNOWBALL_SUPPLY_AMOUNT + C_WHITE + " 个雪球！");
                }
            }
        } catch (Exception e) {
            mainPlugin.getLogger().warning("玩家雪球补给失败：" + e.getMessage());
        }
    }

    /**
     * 刷新箱子中的雪球（随机0-50个）
     */
    private void refreshChestSnowballs() {
        try {
            Random random = new Random();
            for (Location loc : chestLocations) {
                Block block = loc.getBlock();
                if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
                    continue;
                }

                Chest chest = (Chest) block.getState();
                Inventory inv = chest.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() == Material.SNOW_BALL) {
                        inv.setItem(i, null);
                    }
                }

                int snowballAmount = random.nextInt(CHEST_SNOWBALL_MAX - CHEST_SNOWBALL_MIN + 1) + CHEST_SNOWBALL_MIN;
                if (snowballAmount > 0) {
                    inv.addItem(new ItemStack(Material.SNOW_BALL, snowballAmount));
                }

                Location effectLoc = loc.add(0.5, 0.5, 0.5);
                loc.getWorld().dropItemNaturally(effectLoc, new ItemStack(Material.SNOW_BALL, 1));
                loc.getWorld().playSound(effectLoc, Sound.CHEST_OPEN, 0.8f, 1.0f);
                loc.getWorld().playSound(effectLoc, Sound.STEP_SNOW, 0.8f, 1.0f);
            }
            mainPlugin.getLogger().info("箱子雪球刷新完成，每个箱子随机生成0-50个雪球");
        } catch (Exception e) {
            mainPlugin.getLogger().warning("箱子雪球刷新失败：" + e.getMessage());
        }
    }

    /**
     * 检查自动启动条件
     */
    private void checkAutoStartConditions() {
        // 如果游戏已运行或已有倒计时，直接返回
        if (gameRunning || autoStartPending) {
            return;
        }

        // 获取在线玩家数量
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // 满足最小人数要求，启动倒计时
        if (onlinePlayers >= minPlayersForAutoStart) {
            autoStartPending = true;
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(C_GOLD + "【雪球大战】满足自动启动条件（当前在线" + onlinePlayers + "人）！");
            Bukkit.broadcastMessage(C_YELLOW + "将在30秒后自动启动游戏，最少保持2人在线！");
            Bukkit.broadcastMessage("");

            // 启动倒计时任务
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 再次检查人数，防止倒计时期间玩家离开
                    int currentPlayers = Bukkit.getOnlinePlayers().size();
                    if (currentPlayers >= minPlayersForAutoStart) {
                        startGame(); // 自动启动游戏
                    } else {
                        Bukkit.broadcastMessage("");
                        Bukkit.broadcastMessage(C_RED + "【雪球大战】在线人数不足（当前" + currentPlayers + "人），取消自动启动！");
                        Bukkit.broadcastMessage("");
                    }
                    autoStartPending = false; // 重置倒计时状态
                }
            }.runTaskLater(mainPlugin, autoStartDelay);
        }
    }

    /**
     * 初始化玩家数据
     */
    private void initPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        if (!killScoreManager.containsKey(uuid)) {
            killScoreManager.put(uuid, 0);
        }
        if (!playerGameHealth.containsKey(uuid)) {
            playerGameHealth.put(uuid, MAX_GAME_HEALTH);
        }
        if (!selfKillCount.containsKey(uuid)) {
            selfKillCount.put(uuid, 0);
        }
        createPlayerScoreboard(player);
        applySaturationBuff(player);
        giveSnowShovel(player);

        mainPlugin.getLogger().info("初始化玩家数据：" + player.getName());
    }

    /**
     * 发放圣诞铲子
     */
    private void giveSnowShovel(Player player) {
        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.DIAMOND_SPADE && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta.hasDisplayName() && meta.getDisplayName().contains("圣诞铲子")) {
                        return;
                    }
                }
            }

            ItemStack shovel = new ItemStack(Material.DIAMOND_SPADE, 1);
            ItemMeta meta = shovel.getItemMeta();
            meta.setDisplayName(C_WHITE + C_BOLD + "圣诞铲子 " + C_GRAY + "(挖雪专用)");
            meta.addEnchant(Enchantment.DIG_SPEED, 3, true);
            shovel.setDurability((short) 0);
            shovel.setItemMeta(meta);

            player.getInventory().addItem(shovel);
            player.sendMessage(C_GREEN + "获得圣诞铲子！可挖掘雪层获取雪球（无范围限制）");
        } catch (Exception e) {
            mainPlugin.getLogger().warning("发放铲子失败：" + e.getMessage());
        }
    }

    /**
     * 检查OP白名单
     */
    private boolean isOpWhitelist(Player player) {
        for (String opId : OP_WHITELIST) {
            if (player.getName().equalsIgnoreCase(opId)) {
                return true;
            }
        }
        return player.isOp() || player.hasPermission("snowballfight.admin");
    }

    /**
     * 给玩家添加饱食Buff
     */
    private void applySaturationBuff(Player player) {
        try {
            if (player.hasPotionEffect(PotionEffectType.SATURATION)) {
                player.removePotionEffect(PotionEffectType.SATURATION);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 2, false));
            player.setFoodLevel(20);
            player.setSaturation(10.0F);
        } catch (Exception e) {
            mainPlugin.getLogger().warning("添加饱食Buff失败：" + e.getMessage());
        }
    }

    /**
     * 创建玩家计分板
     */
    private void createPlayerScoreboard(Player player) {
        try {
            UUID uuid = player.getUniqueId();
            ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();

            if (scoreboardManager == null) {
                mainPlugin.getLogger().severe("计分板管理器初始化失败！");
                return;
            }

            Scoreboard scoreboard = scoreboardManager.getNewScoreboard();
            String objectiveId = "xmas_sf_" + uuid.toString().substring(0, 8);

            if (scoreboard.getObjective(objectiveId) != null) {
                scoreboard.getObjective(objectiveId).unregister();
            }
            if (scoreboard.getObjective(DisplaySlot.SIDEBAR) != null) {
                scoreboard.getObjective(DisplaySlot.SIDEBAR).unregister();
            }

            Objective objective = scoreboard.registerNewObjective(objectiveId, "dummy");
            objective.setDisplayName(SCOREBOARD_TITLE);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            clearScoreboard(scoreboard);
            addScoreboardLine(objective, C_GRAY + "——————————", 7);
            addScoreboardLine(objective, C_WHITE + "击杀积分：", 6);
            addScoreboardLine(objective, C_YELLOW + String.valueOf(getKillScore(player)), 5);
            addScoreboardLine(objective, C_GRAY + "——————————", 4);
            addScoreboardLine(objective, C_WHITE + "游戏血量：", 3);
            addScoreboardLine(objective, C_GREEN + getPlayerHealth(player) + "/" + MAX_GAME_HEALTH, 2);
            addScoreboardLine(objective, C_BOLD + "胜利目标：" + WIN_KILLS + "杀", 1);

            playerScoreboards.put(uuid, scoreboard);
            playerObjectives.put(uuid, objective);
            player.setScoreboard(scoreboard);

        } catch (Exception e) {
            mainPlugin.getLogger().severe("创建计分板失败：" + e.getMessage());
            player.sendMessage(C_RED + "计分板加载失败，请重新加入游戏！");
        }
    }

    /**
     * 清空计分板
     */
    private void clearScoreboard(Scoreboard scoreboard) {
        try {
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }
        } catch (Exception e) {
            mainPlugin.getLogger().warning("清空计分板失败：" + e.getMessage());
        }
    }

    /**
     * 添加计分板行
     */
    private void addScoreboardLine(Objective objective, String text, int score) {
        try {
            Score line = objective.getScore(text);
            if (line.getScore() != 0) {
                objective.getScoreboard().resetScores(text);
            }
            line.setScore(score);
        } catch (Exception e) {
            mainPlugin.getLogger().warning("添加计分板行失败：" + e.getMessage());
        }
    }

    /**
     * 更新玩家计分板
     */
    private void updatePlayerScoreboard(Player player) {
        try {
            UUID uuid = player.getUniqueId();

            if (!killScoreManager.containsKey(uuid) || !playerGameHealth.containsKey(uuid)) {
                initPlayerData(player);
                return;
            }

            if (!playerScoreboards.containsKey(uuid) || !playerObjectives.containsKey(uuid)) {
                createPlayerScoreboard(player);
                return;
            }

            Scoreboard scoreboard = playerScoreboards.get(uuid);
            Objective objective = playerObjectives.get(uuid);

            for (String entry : scoreboard.getEntries()) {
                if (entry.startsWith(C_YELLOW) || entry.startsWith(C_GREEN)) {
                    scoreboard.resetScores(entry);
                }
            }

            addScoreboardLine(objective, C_YELLOW + String.valueOf(getKillScore(player)), 5);
            addScoreboardLine(objective, C_GREEN + getPlayerHealth(player) + "/" + MAX_GAME_HEALTH, 2);

        } catch (Exception e) {
            mainPlugin.getLogger().warning("更新计分板失败：" + e.getMessage());
        }
    }

    /**
     * 全局刷新计分板
     */
    public void refreshGlobalScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerScoreboard(player);
        }
    }

    /**
     * 获取玩家击杀数
     */
    private int getKillScore(Player player) {
        return killScoreManager.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 增加玩家击杀数（防刷分逻辑）
     */
    private void addKillScore(Player player, Player victim) {
        UUID killerUuid = player.getUniqueId();
        UUID victimUuid = victim.getUniqueId();

        if (killerUuid.equals(victimUuid)) {
            int count = selfKillCount.getOrDefault(killerUuid, 0) + 1;
            selfKillCount.put(killerUuid, count);

            if (count == 1) {
                player.sendMessage("");
                player.sendMessage(C_YELLOW + "⚠️ 警告：你击杀了自己！累计自杀1次，超过2次将清空积分！");
                player.sendMessage("");
            } else if (count == MAX_SELF_KILL) {
                player.sendMessage("");
                player.sendMessage(C_RED + "⚠️ 严重警告：你已累计自杀2次！再次自杀将清空所有击杀积分！");
                player.sendMessage("");
            } else if (count > MAX_SELF_KILL) {
                killScoreManager.put(killerUuid, 0);
                selfKillCount.put(killerUuid, 0);
                player.sendMessage("");
                player.sendMessage(C_RED + C_BOLD + "❌ 惩罚：你累计自杀超过2次，击杀积分已被清空！");
                player.sendMessage("");
                sendTitle(player, C_RED + "积分清空", C_WHITE + "禁止刷分！", 0, 30, 10);
            }
            updatePlayerScoreboard(player);
            return;
        }

        killScoreManager.put(killerUuid, getKillScore(player) + 1);
        updatePlayerScoreboard(player);
    }

    /**
     * 获取玩家血量
     */
    private int getPlayerHealth(Player player) {
        return playerGameHealth.getOrDefault(player.getUniqueId(), MAX_GAME_HEALTH);
    }

    /**
     * 发送标题信息
     */
    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> packetPlayOutTitleClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");
            Class<?> enumTitleActionClass = Class.forName("net.minecraft.server." + version + ".EnumTitleAction");
            Class<?> chatSerializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");

            Object craftPlayer = craftPlayerClass.cast(player);
            Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
            Object entityPlayer = getHandleMethod.invoke(craftPlayer);

            Method getPlayerConnectionMethod = entityPlayer.getClass().getMethod("playerConnection");
            Object playerConnection = getPlayerConnectionMethod.invoke(entityPlayer);
            Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", packetClass);

            String titleJson = "{\"text\":\"" + title.replace("§", "\\u00a7") + "\"}";
            String subtitleJson = "{\"text\":\"" + subtitle.replace("§", "\\u00a7") + "\"}";

            Method aMethod = chatSerializerClass.getMethod("a", String.class);
            Object titleComponent = aMethod.invoke(null, titleJson);
            Object subtitleComponent = aMethod.invoke(null, subtitleJson);

            Constructor<?> titlePacketConstructor = packetPlayOutTitleClass.getConstructor(enumTitleActionClass, chatSerializerClass.getDeclaringClass(), int.class, int.class, int.class);
            Object titlePacket = titlePacketConstructor.newInstance(
                    enumTitleActionClass.getEnumConstants()[0],
                    titleComponent,
                    fadeIn,
                    stay,
                    fadeOut
            );
            sendPacketMethod.invoke(playerConnection, titlePacket);

            Object subtitlePacket = titlePacketConstructor.newInstance(
                    enumTitleActionClass.getEnumConstants()[1],
                    subtitleComponent,
                    fadeIn,
                    stay,
                    fadeOut
            );
            sendPacketMethod.invoke(playerConnection, subtitlePacket);

        } catch (Exception e) {
            mainPlugin.getLogger().warning("发送标题失败：" + e.getMessage());
            player.sendMessage(C_BOLD + title + C_WHITE + " " + subtitle);
        }
    }

    /**
     * 扣除玩家血量
     */
    private void deductGameHealth(Player player, int damage, Player attacker) {
        try {
            UUID uuid = player.getUniqueId();
            int currentHealth = getPlayerHealth(player);
            int newHealth = Math.max(0, currentHealth - damage);

            playerGameHealth.put(uuid, newHealth);
            updatePlayerScoreboard(player);

            player.sendMessage("");
            player.sendMessage(C_RED + C_BOLD + "⚠️ 受到攻击！");
            player.sendMessage(C_WHITE + "被 " + C_GOLD + attacker.getName() + C_WHITE + " 造成 " + C_RED + damage + C_WHITE + " 点伤害");
            player.sendMessage(C_RED + "❤ 剩余血量：" + C_GREEN + newHealth + C_WHITE + "/" + C_GREEN + MAX_GAME_HEALTH);
            player.sendMessage("");

            sendTitle(player, C_RED + "-" + damage, "", 0, 10, 0);

            attacker.sendMessage("");
            attacker.sendMessage(C_GREEN + C_BOLD + "🎯 击中目标！");
            attacker.sendMessage(C_WHITE + "对 " + C_GOLD + player.getName() + C_WHITE + " 造成 " + C_RED + damage + C_WHITE + " 点伤害");
            attacker.sendMessage("");
            sendTitle(attacker, C_GREEN + "击中！", "", 0, 10, 0);

            if (newHealth <= 0) {
                eliminatePlayer(player, attacker);
            }
        } catch (Exception e) {
            mainPlugin.getLogger().warning("扣除血量失败：" + e.getMessage());
            player.sendMessage(C_RED + "受到攻击时发生错误！");
            attacker.sendMessage(C_YELLOW + "攻击目标时发生错误！");
        }
    }

    /**
     * 淘汰玩家
     */
    private void eliminatePlayer(Player victim, Player killer) {
        try {
            victim.sendMessage("");
            victim.sendMessage(C_RED + C_BOLD + "❌ 你被淘汰了！");
            victim.sendMessage(C_WHITE + "击杀者：" + C_GOLD + killer.getName());
            victim.sendMessage(C_YELLOW + "3秒后自动复活");
            victim.sendMessage("");
            sendTitle(victim, C_RED + "被淘汰！", C_WHITE + "3秒后复活", 0, 40, 10);

            victim.teleport(Bukkit.getWorld("world").getSpawnLocation());

            addKillScore(killer, victim);

            killer.sendMessage("");
            killer.sendMessage(C_AQUA + C_BOLD + "🎉 击杀成功！");
            killer.sendMessage(C_WHITE + "当前积分：" + C_GOLD + getKillScore(killer) + C_WHITE + "/" + WIN_KILLS);
            killer.sendMessage("");
            sendTitle(killer, C_GOLD + "击杀！", C_WHITE + "积分+1", 0, 20, 0);

            if (getKillScore(killer) % 5 == 0) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(C_GOLD + "【雪球大战】" + C_AQUA + killer.getName() + C_WHITE + " 已拿下 " + C_RED + getKillScore(killer) + C_WHITE + " 杀！");
                Bukkit.broadcastMessage("");
            }

            if (getKillScore(killer) >= WIN_KILLS) {
                endGame(killer);
            }

            Bukkit.getScheduler().runTaskLater(mainPlugin, () -> {
                if (victim.isOnline()) {
                    playerGameHealth.put(victim.getUniqueId(), MAX_GAME_HEALTH);
                    updatePlayerScoreboard(victim);
                    victim.sendMessage("");
                    victim.sendMessage(C_GREEN + C_BOLD + "✅ 复活成功！");
                    victim.sendMessage(C_WHITE + "继续战斗吧！");
                    victim.sendMessage("");
                    sendTitle(victim, C_GREEN + "复活成功！", "", 0, 20, 0);
                }
            }, 60);
        } catch (Exception e) {
            mainPlugin.getLogger().warning("淘汰玩家失败：" + e.getMessage());
        }
    }

    /**
     * 开始游戏
     */
    private void startGame() {
        gameRunning = true;
        scanChestLocations();

        for (Player player : Bukkit.getOnlinePlayers()) {
            killScoreManager.put(player.getUniqueId(), 0);
            playerGameHealth.put(player.getUniqueId(), MAX_GAME_HEALTH);
            selfKillCount.put(player.getUniqueId(), 0);
            updatePlayerScoreboard(player);
            player.sendMessage("");
            player.sendMessage(C_GOLD + C_BOLD + "🎄 圣诞雪球大战开始！");
            player.sendMessage(C_WHITE + "击杀 " + C_RED + WIN_KILLS + C_WHITE + " 名玩家即可获胜！");
            player.sendMessage(C_YELLOW + "雪球伤害：20点/次 | 总血量：200点 | 铲子挖雪无范围限制 | 箱子每30秒刷新0-50个雪球");
            player.sendMessage(C_RED + "注意：自杀超过2次将清空击杀积分！");
            player.sendMessage("");
            sendTitle(player, C_GOLD + "游戏开始！", C_WHITE + "击杀" + WIN_KILLS + "人获胜", 0, 60, 20);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(C_GOLD + "【雪球大战】游戏开始！箱子每30秒刷新0-50个雪球，10杀获胜！");
        Bukkit.broadcastMessage(C_RED + "防刷分机制：自杀超过2次将清空积分！");
        Bukkit.broadcastMessage("");
    }

    /**
     * 结束游戏
     */
    private void endGame(Player winner) {
        gameRunning = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage(C_GOLD + C_BOLD + "🏆 雪球大战结束！");
            player.sendMessage(C_WHITE + "获胜者：" + C_AQUA + winner.getName());
            player.sendMessage(C_WHITE + "最终击杀数：" + C_RED + getKillScore(winner));
            player.sendMessage("");
            sendTitle(player, C_GOLD + "游戏结束！", C_AQUA + winner.getName() + " 获胜！", 0, 60, 20);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(C_GOLD + "【雪球大战】" + C_AQUA + winner.getName() + C_WHITE + " 获得胜利！最终击杀数：" + C_RED + getKillScore(winner));
        Bukkit.broadcastMessage("");

        // 游戏结束后重新检查自动启动条件
        Bukkit.getScheduler().runTaskLater(mainPlugin, this::checkAutoStartConditions, 5 * 20L);
    }

    /**
     * 停止游戏
     */
    private void stopGame() {
        gameRunning = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage(C_RED + C_BOLD + "🛑 雪球大战已停止！");
            player.sendMessage("");
            sendTitle(player, C_RED + "游戏停止", "", 0, 30, 10);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(C_GOLD + "【雪球大战】游戏已被管理员停止！");
        Bukkit.broadcastMessage("");

        // 停止后重新检查自动启动条件
        checkAutoStartConditions();
    }

    /**
     * 命令处理
     */
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (!(sender instanceof Player) && !sender.isOp()) {
                sender.sendMessage(C_RED + "只有玩家或管理员可以使用此命令！");
                return true;
            }

            Player player = (sender instanceof Player) ? (Player) sender : null;

            if (args.length == 0) {
                sendCommandHelp(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "start":
                    if (gameRunning) {
                        sender.sendMessage(C_YELLOW + "雪球大战已经在运行中了！");
                        return true;
                    }
                    if (isOpWhitelist(player) || sender.isOp()) {
                        startGame();
                        sender.sendMessage(C_GREEN + "雪球大战已启动！");
                    } else {
                        sender.sendMessage(C_RED + "你没有权限执行此命令！");
                    }
                    break;

                case "stop":
                    if (!gameRunning) {
                        sender.sendMessage(C_YELLOW + "雪球大战尚未开始！");
                        return true;
                    }
                    if (isOpWhitelist(player) || sender.isOp()) {
                        stopGame();
                        sender.sendMessage(C_GREEN + "雪球大战已停止！");
                    } else {
                        sender.sendMessage(C_RED + "你没有权限执行此命令！");
                    }
                    break;

                case "status":
                    sender.sendMessage(C_GOLD + "=== 雪球大战状态 ===");
                    sender.sendMessage(C_WHITE + "运行状态：" + (gameRunning ? C_GREEN + "已启动" : C_RED + "未启动"));
                    sender.sendMessage(C_WHITE + "自动启动倒计时：" + (autoStartPending ? C_YELLOW + "30秒" : C_GRAY + "无"));
                    sender.sendMessage(C_WHITE + "雪球伤害：" + C_YELLOW + "20点/次");
                    sender.sendMessage(C_WHITE + "玩家血量：" + C_YELLOW + "200点");
                    sender.sendMessage(C_WHITE + "获胜条件：" + C_YELLOW + WIN_KILLS + "杀");
                    sender.sendMessage(C_WHITE + "箱子雪球范围：" + C_YELLOW + "0-50个");
                    sender.sendMessage(C_WHITE + "游戏区域箱子数：" + C_AQUA + chestLocations.size());
                    if (gameRunning && player != null) {
                        sender.sendMessage(C_WHITE + "你的击杀数：" + C_YELLOW + getKillScore(player));
                        sender.sendMessage(C_WHITE + "自杀次数：" + C_RED + selfKillCount.getOrDefault(player.getUniqueId(), 0));
                    }
                    break;

                case "help":
                    sendCommandHelp(sender);
                    break;

                default:
                    sender.sendMessage(C_RED + "未知命令！使用 /snowballfight help 查看帮助");
                    break;
            }
        } catch (Exception e) {
            mainPlugin.getLogger().severe("命令执行失败：" + e.getMessage());
            sender.sendMessage(C_RED + "命令执行出错：" + e.getMessage());
        }
        return true;
    }

    /**
     * 发送命令帮助
     */
    private void sendCommandHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(C_GOLD + C_BOLD + "雪球大战命令帮助");
        sender.sendMessage(C_WHITE + "/snowballfight start - 启动游戏（管理员）");
        sender.sendMessage(C_WHITE + "/snowballfight stop - 停止游戏（管理员）");
        sender.sendMessage(C_WHITE + "/snowballfight status - 查看游戏状态");
        sender.sendMessage(C_WHITE + "/snowballfight help - 查看此帮助");
        sender.sendMessage(C_RED + "注意：自杀超过2次将清空击杀积分，游戏会自动启动（至少2人）！");
        sender.sendMessage("");
    }

    /**
     * 玩家加入事件（触发自动启动检查）
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        initPlayerData(player);
        if (gameRunning) {
            player.sendMessage("");
            player.sendMessage(C_GOLD + "【雪球大战】欢迎加入正在进行的游戏！");
            player.sendMessage(C_YELLOW + "雪球伤害：20点/次 | 你的血量：200点 | 箱子每30秒刷新0-50个雪球");
            player.sendMessage(C_RED + "防刷分：自杀超过2次将清空积分！");
            player.sendMessage(C_WHITE + "当前击杀数：" + C_YELLOW + getKillScore(player));
            player.sendMessage("");
        }

        // 玩家加入后检查自动启动条件
        checkAutoStartConditions();
    }

    /**
     * 玩家退出事件（如果人数不足，取消自动启动）
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (gameRunning) {
            Bukkit.broadcastMessage(C_YELLOW + "【雪球大战】" + player.getName() + " 退出了游戏！");

            // 游戏中如果剩余玩家不足2人，结束游戏
            if (Bukkit.getOnlinePlayers().size() < 2) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(C_RED + "【雪球大战】在线玩家不足2人，游戏结束！");
                Bukkit.broadcastMessage("");
                gameRunning = false;

                // 结束后重新检查自动启动条件
                checkAutoStartConditions();
            }
        } else if (autoStartPending) {
            // 如果有自动启动倒计时，检查人数
            if (Bukkit.getOnlinePlayers().size() < minPlayersForAutoStart) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(C_RED + "【雪球大战】在线人数不足，取消自动启动倒计时！");
                Bukkit.broadcastMessage("");
                autoStartPending = false;
            }
        }
    }

    /**
     * 雪球击中事件
     */
    @EventHandler
    public void onSnowballHit(EntityDamageByEntityEvent e) {
        try {
            if (!gameRunning || !(e.getDamager() instanceof Snowball) || !(e.getEntity() instanceof Player)) {
                return;
            }

            Snowball snowball = (Snowball) e.getDamager();
            if (!(snowball.getShooter() instanceof Player)) {
                return;
            }

            Player shooter = (Player) snowball.getShooter();
            Player victim = (Player) e.getEntity();

            e.setCancelled(true);
            victim.damage(SNOWBALL_REAL_DAMAGE, shooter);
            deductGameHealth(victim, SNOWBALL_GAME_DAMAGE, shooter);
        } catch (Exception e1) {
            mainPlugin.getLogger().warning("雪球击中处理失败：" + e1.getMessage());
        }
    }

    /**
     * 方块挖掘事件（移除挖雪范围限制，禁止非OP挖掘基础方块）
     */
    @EventHandler
    public void onSnowBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        Block block = e.getBlock();
        Material blockType = block.getType();

        // 禁止非OP挖掘基础方块
        if (blockType == Material.GRASS || blockType == Material.DIRT || blockType == Material.STONE ||
                blockType == Material.WOOD || blockType == Material.LEAVES || blockType == Material.LOG) {
            if (!isOpWhitelist(player)) {
                e.setCancelled(true);
                player.sendMessage(C_RED + "雪球大战期间禁止挖掘基础方块（仅允许OP）！");
                return;
            }
        }

        // 非雪块处理
        if (blockType != Material.SNOW_BLOCK && blockType != Material.SNOW) {
            if (gameRunning && !isOpWhitelist(player)) {
                e.setCancelled(true);
                player.sendMessage(C_RED + "雪球大战期间禁止破坏非雪块！");
            }
            return;
        }

        // 游戏未运行禁止挖雪
        if (!gameRunning) {
            e.setCancelled(true);
            player.sendMessage(C_RED + "游戏未开始，禁止挖雪！");
            return;
        }

        // 检查工具
        ItemStack handItem = player.getItemInHand();
        if (handItem == null || handItem.getType() != Material.DIAMOND_SPADE || !handItem.hasItemMeta()) {
            e.setCancelled(true);
            player.sendMessage(C_RED + "只有使用圣诞铲子才能挖掘雪层！");
            return;
        }

        ItemMeta meta = handItem.getItemMeta();
        if (!meta.hasDisplayName() || !meta.getDisplayName().contains("圣诞铲子")) {
            e.setCancelled(true);
            player.sendMessage(C_RED + "只有使用圣诞铲子才能挖掘雪层！");
            return;
        }

        // 移除区域限制，允许在任何地方挖雪
        e.setCancelled(false);
        int dropAmount = (blockType == Material.SNOW_BLOCK) ? SNOW_BLOCK_DROP : SNOW_LAYER_DROP;
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SNOW_BALL, dropAmount));
        block.setType(Material.AIR);
        player.playSound(block.getLocation(), Sound.DIG_SNOW, 1.0f, 1.0f);
        player.sendMessage(C_GREEN + "挖掘" + (blockType == Material.SNOW_BLOCK ? "雪块" : "雪层") + "获得了" + dropAmount + "个雪球！");
    }

    /**
     * 方块放置事件
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (gameRunning && !isOpWhitelist(player)) {
            e.setCancelled(true);
            player.sendMessage(C_RED + "雪球大战期间禁止放置方块！");
        }
    }
}
