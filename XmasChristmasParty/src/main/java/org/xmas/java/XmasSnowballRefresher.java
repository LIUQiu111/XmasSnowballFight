package org.xmas.java;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class XmasSnowballRefresher {
    private final XmasChristmasParty plugin;
    private final List<Location> allChestLocations = new ArrayList<>();
    private static final int REFRESH_INTERVAL = 600;
    private static final double REFRESH_RATIO = 0.3;

    public XmasSnowballRefresher(XmasChristmasParty plugin) {
        this.plugin = plugin;
        scanAllChestsInWorld();
        startSnowballRefreshTask();
        plugin.getLogger().info("已扫描到全地图共" + allChestLocations.size() + "个箱子，雪球刷新任务已启动！");
    }

    private void scanAllChestsInWorld() {
        allChestLocations.clear();
        for (World world : plugin.getServer().getWorlds()) {
            Arrays.asList(world.getLoadedChunks()).forEach(chunk -> {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 256; y++) {
                            Block block = chunk.getBlock(x, y, z);
                            if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                                allChestLocations.add(block.getLocation());
                            }
                        }
                    }
                }
            });
        }
    }

    private void startSnowballRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (allChestLocations.isEmpty()) {
                    plugin.getLogger().warning("未检测到地图内的箱子，跳过本次雪球刷新！");
                    scanAllChestsInWorld();
                    return;
                }
                List<Location> selectedChests = getRandomChests();
                selectedChests.forEach(this::refreshChestSnowballs);
                plugin.getLogger().info("本次刷新了" + selectedChests.size() + "个箱子的雪球，剩余未刷新：" + (allChestLocations.size() - selectedChests.size()));
            }

            private List<Location> getRandomChests() {
                List<Location> temp = new ArrayList<>(allChestLocations);
                Collections.shuffle(temp);
                int selectCount = (int) (temp.size() * REFRESH_RATIO);
                selectCount = Math.max(1, selectCount);
                return temp.subList(0, selectCount);
            }

            private void refreshChestSnowballs(Location loc) {
                Block block = loc.getBlock();
                if (!(block.getState() instanceof Chest)) {
                    return;
                }
                Chest chest = (Chest) block.getState();
                clearSnowballsInChest(chest);
                int snowballCount = getRandomSnowballCount();
                if (snowballCount > 0) {
                    ItemStack snowballs = new ItemStack(Material.SNOW_BALL, snowballCount);
                    Random random = new Random();
                    int slot = random.nextInt(chest.getInventory().getSize());
                    chest.getInventory().setItem(slot, snowballs);
                }
                chest.update();
            }

            private void clearSnowballsInChest(Chest chest) {
                chest.getInventory().remove(Material.SNOW_BALL);
            }

            private int getRandomSnowballCount() {
                Random random = new Random();
                int randomValue = random.nextInt(100);

                if (randomValue < 30) {
                    return 0;
                } else if (randomValue < 45) {
                    return random.nextInt(5) + 1;
                } else if (randomValue < 60) {
                    return random.nextInt(5) + 6;
                } else if (randomValue < 75) {
                    return random.nextInt(5) + 11;
                } else if (randomValue < 90) {
                    return random.nextInt(4) + 16;
                } else {
                    return 20;
                }
            }
        }.runTaskTimer(plugin, 0, REFRESH_INTERVAL);
    }

    public void rescanChests() {
        scanAllChestsInWorld();
        plugin.getLogger().info("手动重新扫描箱子完成，当前共检测到" + allChestLocations.size() + "个箱子！");
    }
}
