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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 圣诞雪球大战核心类 - 1.8.8兼容最终版
 * 修复：1. EnumTitleAction标题发送失败 2. Particle粒子类缺失
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

    // 自动启动相关
    private boolean autoStartPending = false;
    private int minPlayersForAutoStart = 2;
    private long autoStartDelay = 30 * 20L;

    // 核心配置
    private static final int MAX_GAME_HEALTH = 200;
    private static final double SNOWBALL_REAL_DAMAGE = 0.01;
    private static final int SNOWBALL_GAME_DAMAGE = 20;
    private static final String SCOREBOARD_TITLE = ChatColor.RED + "❄圣诞雪球大战❄";
    private static final int WIN_KILLS = 10;

    // 补给常量
    private static final int SNOWBALL_SUPPLY_AMOUNT = 16;
    private static final long SNOWBALL_SUPPLY_INTERVAL = 200;
    private static final long CHEST_REFRESH_INTERVAL = 600;
    private static final int CHEST_SNOWBALL_MIN = 0;
    private static final int CHEST_SNOWBALL_MAX = 50;
    private static final int GAME_REGION_RADIUS = 50;

    // 挖掘常量
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
        checkAutoStartConditions();
    }

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
        mainPlugin.getLogger().info("扫描到箱子数量：" + chestLocations.size());
    }

    private void startSnowballSupplyTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    supplySnowballs(player);
                }
            }
        }.runTaskTimer(mainPlugin, 0, SNOWBALL_SUPPLY_INTERVAL);
        mainPlugin.getLogger().info("雪球补给任务启动");
    }

    private void startChestRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) return;
                refreshChestSnowballs();
            }
        }.runTaskTimer(mainPlugin, 0, CHEST_REFRESH_INTERVAL);
        mainPlugin.getLogger().info("箱子刷新任务启动");
    }

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
            mainPlugin.getLogger().warning("补给失败：" + e.getMessage());
        }
    }

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
            mainPlugin.getLogger().info("箱子刷新完成，随机生成0-50个雪球");
        } catch (Exception e) {
            mainPlugin.getLogger().warning("箱子刷新失败：" + e.getMessage());
        }
    }

    private void checkAutoStartConditions() {
        if (gameRunning || autoStartPending) {
            return;
        }

        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (onlinePlayers >= minPlayersForAutoStart) {
            autoStartPending = true;
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(C_GOLD + "【雪球大战】满足启动条件（在线" + onlinePlayers + "人）！");
            Bukkit.broadcastMessage(C_YELLOW + "30秒后自动启动，最少保持2人在线！");
            Bukkit.broadcastMessage("");

            new BukkitRunnable() {
                @Override
                public void run() {
                    int currentPlayers = Bukkit.getOnlinePlayers().size();
                    if (currentPlayers >= minPlayersForAutoStart) {
                        startGame();
                    } else {
                        Bukkit.broadcastMessage("");
                        Bukkit.broadcastMessage(C_RED + "【雪球大战】人数不足，取消自动启动！");
                        Bukkit.broadcastMessage("");
                    }
                    autoStartPending = false;
                }
            }.runTaskLater(mainPlugin, autoStartDelay);
        }
    }

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

        mainPlugin.getLogger().info("初始化玩家：" + player.getName());
    }

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
            player.sendMessage(C_GREEN + "获得圣诞铲子！挖雪无范围限制");
        } catch (Exception e) {
            mainPlugin.getLogger().warning("发铲子失败：" + e.getMessage());
        }
    }

    private boolean isOpWhitelist(Player player) {
        for (String opId : OP_WHITELIST) {
            if (player.getName().equalsIgnoreCase(opId)) {
                return true;
            }
        }
        return player.isOp() || player.hasPermission("snowballfight.admin");
    }

    private void applySaturationBuff(Player player) {
        try {
            if (player.hasPotionEffect(PotionEffectType.SATURATION)) {
                player.removePotionEffect(PotionEffectType.SATURATION);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 2, false));
            player.setFoodLevel(20);
            player.setSaturation(10.0F);
        } catch (Exception e) {
            mainPlugin.getLogger().warning("加Buff失败：" + e.getMessage());
        }
    }

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
            mainPlugin.getLogger().severe("创计分板失败：" + e.getMessage());
            player.sendMessage(C_RED + "计分板加载失败，请重进！");
        }
    }

    private void clearScoreboard(Scoreboard scoreboard) {
        try {
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }
        } catch (Exception e) {
            mainPlugin.getLogger().warning("清计分板失败：" + e.getMessage());
        }
    }

    private void addScoreboardLine(Objective objective, String text, int score) {
        try {
            Score line = objective.getScore(text);
            if (line.getScore() != 0) {
                objective.getScoreboard().resetScores(text);
            }
            line.setScore(score);
        } catch (Exception e) {
            mainPlugin.getLogger().warning("加计分板行失败：" + e.getMessage());
        }
    }

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
            mainPlugin.getLogger().warning("更计分板失败：" + e.getMessage());
        }
    }

    public void refreshGlobalScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerScoreboard(player);
        }
    }

    private int getKillScore(Player player) {
        return killScoreManager.getOrDefault(player.getUniqueId(), 0);
    }

    private void addKillScore(Player player, Player victim) {
        UUID killerUuid = player.getUniqueId();
        UUID victimUuid = victim.getUniqueId();

        if (killerUuid.equals(victimUuid)) {
            int count = selfKillCount.getOrDefault(killerUuid, 0) + 1;
            selfKillCount.put(killerUuid, count);

            if (count == 1) {
                player.sendMessage("");
                player.sendMessage(C_YELLOW + "⚠️ 警告：自杀1次，超2次清空积分！");
                player.sendMessage("");
            } else if (count == MAX_SELF_KILL) {
                player.sendMessage("");
                player.sendMessage(C_RED + "⚠️ 严重警告：自杀2次，再自杀清空积分！");
                player.sendMessage("");
            } else if (count > MAX_SELF_KILL) {
                killScoreManager.put(killerUuid, 0);
                selfKillCount.put(killerUuid, 0);
                player.sendMessage("");
                player.sendMessage(C_RED + C_BOLD + "❌ 惩罚：自杀超2次，积分清空！");
                player.sendMessage("");
                sendTitle(player, C_RED + "积分清空", C_WHITE + "禁止刷分！", 0, 30, 10);
            }
            updatePlayerScoreboard(player);
            return;
        }

        killScoreManager.put(killerUuid, getKillScore(player) + 1);
        updatePlayerScoreboard(player);
    }

    private int getPlayerHealth(Player player) {
        return playerGameHealth.getOrDefault(player.getUniqueId(), MAX_GAME_HEALTH);
    }

    // 修复1.8.8标题发送bug
    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> packetPlayOutTitleClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");
            Class<?> enumTitleActionClass = Class.forName("net.minecraft.server." + version + ".EnumTitleAction");
            Class<?> chatSerializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");

            // 反射获取枚举实例
            Field titleField = enumTitleActionClass.getField("TITLE");
            Field subtitleField = enumTitleActionClass.getField("SUBTITLE");
            Object titleAction = titleField.get(null);
            Object subtitleAction = subtitleField.get(null);

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
            Object titlePacket = titlePacketConstructor.newInstance(titleAction, titleComponent, fadeIn, stay, fadeOut);
            sendPacketMethod.invoke(playerConnection, titlePacket);

            Object subtitlePacket = titlePacketConstructor.newInstance(subtitleAction, subtitleComponent, fadeIn, stay, fadeOut);
            sendPacketMethod.invoke(playerConnection, subtitlePacket);

        } catch (Exception e) {
            mainPlugin.getLogger().warning("发标题失败：" + e.getMessage());
            player.sendMessage(C_BOLD + title + C_WHITE + " " + subtitle);
        }
    }

    // 1.8.8粒子效果工具方法（修复Particle类缺失）
    private void spawnParticle1_8_8(World world, String particleType, Location loc, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> enumParticle = Class.forName("net.minecraft.server." + version + ".EnumParticle");
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");
            Class<?> worldServer = Class.forName("net.minecraft.server." + version + ".WorldServer");

            // 获取粒子枚举
            Object particle = enumParticle.getField(particleType.toUpperCase()).get(null);
            Object nmsWorld = craftWorld.getMethod("getHandle").invoke(world);

            // 调用粒子发送方法
            Method spawnParticle = worldServer.getMethod("a", enumParticle, boolean.class, double.class, double.class, double.class, int.class, double.class, double.class, double.class, double.class);
            spawnParticle.invoke(nmsWorld, particle, true, loc.getX(), loc.getY(), loc.getZ(), count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
            // 失败则用雪花效果替代
            world.playEffect(loc, Effect.SNOWBALL_BREAK, 0);
        }
    }

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
            mainPlugin.getLogger().warning("扣血失败：" + e.getMessage());
            player.sendMessage(C_RED + "受攻击时出错！");
            attacker.sendMessage(C_YELLOW + "攻击时出错！");
        }
    }

    // 修复复活旁观者+粒子效果
    // 修复复活旁观者+粒子效果+复活点回传
    private void eliminatePlayer(Player victim, Player killer) {
        try {
            victim.sendMessage("");
            victim.sendMessage(C_RED + C_BOLD + "❌ 你被淘汰了！");
            victim.sendMessage(C_WHITE + "击杀者：" + C_GOLD + killer.getName());
            victim.sendMessage(C_YELLOW + "3秒后复活（旁观者模式免疫伤害）");
            victim.sendMessage("");
            sendTitle(victim, C_RED + "被淘汰！", C_WHITE + "3秒后复活", 0, 40, 10);

            // 关键：记录玩家的重生点（优先使用床的重生点，无则用世界出生点）
            Location spawnLoc = victim.getBedSpawnLocation() != null ? victim.getBedSpawnLocation() : Bukkit.getWorld("world").getSpawnLocation();
            // 如果需要强制固定世界出生点，直接用这行：
            // Location spawnLoc = Bukkit.getWorld("world").getSpawnLocation();

            // 设置旁观者模式防秒杀
            victim.setGameMode(GameMode.SPECTATOR);
            // 可选：旁观者模式也传送到重生点（如果需要）
            // victim.teleport(spawnLoc);

            addKillScore(killer, victim);

            killer.sendMessage("");
            killer.sendMessage(C_AQUA + C_BOLD + "🎉 击杀成功！");
            killer.sendMessage(C_WHITE + "当前积分：" + C_GOLD + getKillScore(killer) + C_WHITE + "/" + WIN_KILLS);
            killer.sendMessage("");
            sendTitle(killer, C_GOLD + "击杀！", C_WHITE + "积分+1", 0, 20, 0);

            if (getKillScore(killer) % 5 == 0) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(C_GOLD + "【雪球大战】" + C_AQUA + killer.getName() + C_WHITE + " 拿下 " + C_RED + getKillScore(killer) + C_WHITE + " 杀！");
                Bukkit.broadcastMessage("");
            }

            if (getKillScore(killer) >= WIN_KILLS) {
                endGame(killer);
            }

            Bukkit.getScheduler().runTaskLater(mainPlugin, () -> {
                if (victim.isOnline()) {
                    // 恢复生存模式+强制传送到记录的重生点
                    victim.setGameMode(GameMode.SURVIVAL);
                    victim.teleport(spawnLoc); // 核心修复：强制传送到重生点
                    playerGameHealth.put(victim.getUniqueId(), MAX_GAME_HEALTH);
                    updatePlayerScoreboard(victim);
                    victim.sendMessage("");
                    victim.sendMessage(C_GREEN + C_BOLD + "✅ 复活成功！");
                    victim.sendMessage(C_WHITE + "继续战斗吧！");
                    victim.sendMessage("");
                    sendTitle(victim, C_GREEN + "复活成功！", "", 0, 20, 0);
                    // 调用1.8.8粒子方法
                    spawnParticle1_8_8(victim.getWorld(), "HEART", victim.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.1);
                }
            }, 60);
        } catch (Exception e) {
            mainPlugin.getLogger().warning("淘汰玩家失败：" + e.getMessage());
        }
    }

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
            player.sendMessage(C_WHITE + "击杀 " + C_RED + WIN_KILLS + C_WHITE + " 人获胜！");
            player.sendMessage(C_YELLOW + "雪球伤害20点/次 | 血量200点 | 挖雪无范围限制 | 箱子30秒刷0-50雪球");
            player.sendMessage(C_RED + "注意：自杀超2次清空积分！复活时免疫伤害~");
            player.sendMessage("");
            sendTitle(player, C_GOLD + "游戏开始！", C_WHITE + "击杀" + WIN_KILLS + "人获胜", 0, 60, 20);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(C_GOLD + "【雪球大战】开始！10杀获胜，箱子30秒刷0-50雪球！");
        Bukkit.broadcastMessage(C_RED + "防刷分：自杀超2次清空积分，复活免疫伤害~");
        Bukkit.broadcastMessage("");
    }

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
        Bukkit.broadcastMessage(C_GOLD + "【雪球大战】" + C_AQUA + winner.getName() + C_WHITE + " 胜利！击杀数：" + C_RED + getKillScore(winner));
        Bukkit.broadcastMessage("");

        Bukkit.getScheduler().runTaskLater(mainPlugin, this::checkAutoStartConditions, 5 * 20L);
    }

    private void stopGame() {
        gameRunning = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage(C_RED + C_BOLD + "🛑 雪球大战已停止！");
            player.sendMessage("");
            sendTitle(player, C_RED + "游戏停止", "", 0, 30, 10);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(C_GOLD + "【雪球大战】已被管理员停止！");
        Bukkit.broadcastMessage("");

        checkAutoStartConditions();
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (!(sender instanceof Player) && !sender.isOp()) {
                sender.sendMessage(C_RED + "只有玩家/管理员可用此命令！");
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
                        sender.sendMessage(C_YELLOW + "游戏已在运行！");
                        return true;
                    }
                    if (isOpWhitelist(player) || sender.isOp()) {
                        startGame();
                        sender.sendMessage(C_GREEN + "游戏启动！");
                    } else {
                        sender.sendMessage(C_RED + "无权限！");
                    }
                    break;

                case "stop":
                    if (!gameRunning) {
                        sender.sendMessage(C_YELLOW + "游戏尚未开始！");
                        return true;
                    }
                    if (isOpWhitelist(player) || sender.isOp()) {
                        stopGame();
                        sender.sendMessage(C_GREEN + "游戏停止！");
                    } else {
                        sender.sendMessage(C_RED + "无权限！");
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
                    sender.sendMessage(C_RED + "未知命令！用 /snowballfight help 查看帮助");
                    break;
            }
        } catch (Exception e) {
            mainPlugin.getLogger().severe("命令执行失败：" + e.getMessage());
            sender.sendMessage(C_RED + "命令出错：" + e.getMessage());
        }
        return true;
    }

    private void sendCommandHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(C_GOLD + C_BOLD + "雪球大战命令帮助");
        sender.sendMessage(C_WHITE + "/snowballfight start - 启动游戏（管理员）");
        sender.sendMessage(C_WHITE + "/snowballfight stop - 停止游戏（管理员）");
        sender.sendMessage(C_WHITE + "/snowballfight status - 查看状态");
        sender.sendMessage(C_WHITE + "/snowballfight help - 查看帮助");
        sender.sendMessage(C_RED + "注意：自杀超2次清空积分，2人在线自动启动！复活免疫伤害~");
        sender.sendMessage("");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        initPlayerData(player);
        if (gameRunning) {
            player.sendMessage("");
            player.sendMessage(C_GOLD + "【雪球大战】欢迎加入正在进行的游戏！");
            player.sendMessage(C_YELLOW + "雪球伤害20点/次 | 血量200点 | 箱子30秒刷0-50雪球");
            player.sendMessage(C_RED + "防刷分：自杀超2次清空积分！复活时免疫伤害~");
            player.sendMessage(C_WHITE + "当前击杀数：" + C_YELLOW + getKillScore(player));
            player.sendMessage("");
        }

        checkAutoStartConditions();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (gameRunning) {
            Bukkit.broadcastMessage(C_YELLOW + "【雪球大战】" + player.getName() + " 退出游戏！");

            if (Bukkit.getOnlinePlayers().size() < 2) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(C_RED + "【雪球大战】人数不足2人，游戏结束！");
                Bukkit.broadcastMessage("");
                gameRunning = false;

                checkAutoStartConditions();
            }
        } else if (autoStartPending) {
            if (Bukkit.getOnlinePlayers().size() < minPlayersForAutoStart) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(C_RED + "【雪球大战】人数不足，取消自动启动！");
                Bukkit.broadcastMessage("");
                autoStartPending = false;
            }
        }
    }

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

    @EventHandler
    public void onSnowBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        Block block = e.getBlock();
        Material blockType = block.getType();

        // 禁止非OP挖基础方块
        if (blockType == Material.GRASS || blockType == Material.DIRT || blockType == Material.STONE ||
                blockType == Material.WOOD || blockType == Material.LEAVES || blockType == Material.LOG) {
            if (!isOpWhitelist(player)) {
                e.setCancelled(true);
                player.sendMessage(C_RED + "雪球大战期间禁止挖基础方块（仅OP可挖）！");
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
            player.sendMessage(C_RED + "只有圣诞铲子能挖雪！");
            return;
        }

        ItemMeta meta = handItem.getItemMeta();
        if (!meta.hasDisplayName() || !meta.getDisplayName().contains("圣诞铲子")) {
            e.setCancelled(true);
            player.sendMessage(C_RED + "只有圣诞铲子能挖雪！");
            return;
        }

        // 无范围限制挖雪
        e.setCancelled(false);
        int dropAmount = (blockType == Material.SNOW_BLOCK) ? SNOW_BLOCK_DROP : SNOW_LAYER_DROP;
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SNOW_BALL, dropAmount));
        block.setType(Material.AIR);
        player.playSound(block.getLocation(), Sound.DIG_SNOW, 1.0f, 1.0f);
        player.sendMessage(C_GREEN + "挖掘" + (blockType == Material.SNOW_BLOCK ? "雪块" : "雪层") + "获得" + dropAmount + "个雪球！");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (gameRunning && !isOpWhitelist(player)) {
            e.setCancelled(true);
            player.sendMessage(C_RED + "雪球大战期间禁止放置方块！");
        }
    }
}
