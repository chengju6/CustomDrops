package com.chengju.customdrops.listeners;

import com.chengju.customdrops.CustomDropsPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishingListener implements Listener {
    // 随机数生成器，用于概率判定
    private final Random random = new Random();

    // 插件主类引用
    private final CustomDropsPlugin plugin;

    // 存储钓鱼掉落配置（线程安全）
    private final Map<String, DropConfig> fishingDrops = new ConcurrentHashMap<>();

    // 全局概率倍率（可动态调整）
    private double globalMultiplier = 1.0D;

    public FishingListener(CustomDropsPlugin plugin) {
        this.plugin = plugin;
        // 初始化时加载配置
        loadFishingConfig();
    }

    // 从配置文件加载钓鱼掉落配置
    public void loadFishingConfig() {
        this.fishingDrops.clear(); // 清空现有配置
        FileConfiguration config = this.plugin.getConfig();
        // 获取配置文件中'fishing'部分
        ConfigurationSection fishingSection = config.getConfigurationSection("fishing");

        if (fishingSection == null) {
            this.plugin.getLogger().warning("配置中没有找到 'fishing' 部分");
            return;
        }

        // 遍历所有钓鱼掉落配置项
        for (String dropName : fishingSection.getKeys(false)) {
            ConfigurationSection dropSection = fishingSection.getConfigurationSection(dropName);
            if (dropSection == null)
                continue;

            // 创建新的掉落配置对象
            DropConfig dropConfig = new DropConfig();

            // 加载基础概率（配置文件中的百分比值转换为小数）
            double chanceValue = dropSection.getDouble("chance", 0.0D);
            dropConfig.chance = chanceValue / 100.0D;

            // 加载触发时的动作列表
            dropConfig.actions = new ArrayList<>(dropSection.getStringList("string-action"));

            // 存入配置映射表
            this.fishingDrops.put(dropName, dropConfig);
        }

        this.plugin.getLogger().info("已加载 " + this.fishingDrops.size() + " 个钓鱼掉落配置");
    }

    // 更新全局倍率（供外部调用）
    public void updateGlobalMultiplier(double multiplier) {
        this.globalMultiplier = multiplier;
    }

    // 处理玩家钓鱼事件
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        // 仅处理成功钓到鱼的场景
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();

        // 获取玩家专属倍率（如权限组倍率）
        double playerBonusMultiplier = this.plugin.getPlayerBonusMultiplier(player);

        // 遍历所有钓鱼掉落配置
        for (Map.Entry<String, DropConfig> entry : this.fishingDrops.entrySet()) {
            String dropName = entry.getKey();
            DropConfig dropConfig = entry.getValue();

            // 计算实际触发概率 = 基础概率 × 玩家倍率 × 全局倍率
            double actualChance = dropConfig.chance * playerBonusMultiplier * this.globalMultiplier;

            // 概率上限100%
            if (actualChance > 1.0D) {
                actualChance = 1.0D;
            }
// 防刷检查
            if (plugin.isOnCooldown(player)) {
                return;
            }
            // 概率判定（直接使用nextDouble()与[0,1)范围比较）
            if (ThreadLocalRandom.current().nextDouble() < actualChance) {
                // 触发成功：执行配置动作
                executeActions(dropConfig.actions, player, dropName);
                plugin.setCooldown(player);
                return; // 每次钓鱼只触发一个掉落
            }
        }
    }

    // 执行配置的动作指令
    private void executeActions(List<String> actions, Player player, String dropName) {
        String itemName = "未知物品";
        // 初始化物品名称用于日志
        for (String action : actions) {
            if (action.startsWith("[") || action.isEmpty())
                continue;
            itemName = ChatColor.stripColor(action); // 获取非指令动作作为物品名
        }

        // 记录日志系统（玩家、物品、来源）
        this.plugin.getLogManager().logItemObtained(player, dropName, "钓鱼");

        // 执行所有配置的动作
        for (String action : actions) {
            try {
                // [CMD]开头的为控制台命令
                if (action.startsWith("[CMD]")) {
                    String command = action.substring(5)
                            .replace("%player%", player.getName())
                            .replace("%world%", player.getWorld().getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    continue;
                }
                // [BD]开头的为全服广播
                if (action.startsWith("[BD]")) {
                    String message = action.substring(4)
                            .replace("%player%", player.getName())
                            .replace("%world%", player.getWorld().getName());
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
                    continue;
                }
                // [TITLE]开头的为标题动画
                if (action.startsWith("[TITLE]")) {
                    // 格式：[TITLE]fadeIn;stay;fadeOut;标题;副标题
                    String[] parts = action.substring(7).split(";", 5);
                    if (parts.length == 5) {
                        int fadeIn = Integer.parseInt(parts[0]);   // 淡入时间
                        int stay = Integer.parseInt(parts[1]);      // 停留时间
                        int fadeOut = Integer.parseInt(parts[2]);   // 淡出时间
                        String title = ChatColor.translateAlternateColorCodes('&', parts[3]);
                        String subtitle = ChatColor.translateAlternateColorCodes('&', parts[4]);
                        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                    }
                    continue;
                }
                // [ACTION]开头的为动作栏消息
                if (action.startsWith("[ACTION]")) {
                    String message = ChatColor.translateAlternateColorCodes('&',
                            action.substring(8)
                                    .replace("%player%", player.getName())
                                    .replace("%world%", player.getWorld().getName()));
                    player.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(message)
                    );
                    continue;
                }
                // 默认处理：发送聊天消息
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        action.replace("%player%", player.getName())));
            } catch (Exception e) {
                this.plugin.getLogger().warning("执行动作时出错: " + action);
                e.printStackTrace();
            }
        }
    }

    // 内部类：钓鱼掉落配置数据结构
    private static class DropConfig {
        double chance;           // 基础概率（0-1）
        List<String> actions;    // 触发时的动作列表
    }
}