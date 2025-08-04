package com.chengju.customdrops.listeners;

import com.chengju.customdrops.CustomDropsPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class EnchantListener implements Listener {
    // 随机数生成器，用于概率判定
    private final Random random = new Random();
    // 插件主类引用
    private final CustomDropsPlugin plugin;
    // 存储所有附魔掉落配置（线程安全）
    private final Map<String, EnchantDropConfig> enchantDrops = new ConcurrentHashMap<>();
    // 全局概率倍率（可动态调整）
    private double globalMultiplier = 1.0D;

    public EnchantListener(CustomDropsPlugin plugin) {
        this.plugin = plugin;
        // 初始化时加载配置
        loadEnchantConfig();
    }

    // 从配置文件加载附魔掉落配置
    public void loadEnchantConfig() {
        this.enchantDrops.clear(); // 清空现有配置
        FileConfiguration config = this.plugin.getConfig();
        // 获取配置文件中'enchant'部分
        ConfigurationSection enchantSection = config.getConfigurationSection("enchant");

        if (enchantSection == null) {
            this.plugin.getLogger().warning("配置中没有找到 'enchant' 部分");
            return;
        }

        // 遍历所有附魔掉落配置项
        for (String enchantKey : enchantSection.getKeys(false)) {
            ConfigurationSection dropSection = enchantSection.getConfigurationSection(enchantKey);
            if (dropSection == null)
                continue;
            // 创建新的附魔掉落配置对象
            EnchantDropConfig dropConfig = new EnchantDropConfig();

            // 加载物品匹配正则（可为空）
            String itemPattern = dropSection.getString("item-pattern", "");
            if (!itemPattern.isEmpty()) {
                dropConfig.itemPattern = Pattern.compile(itemPattern);
            }

            // 加载附魔匹配正则（可为空）
            String enchantPattern = dropSection.getString("enchant-pattern", "");
            if (!enchantPattern.isEmpty()) {
                dropConfig.enchantPattern = Pattern.compile(enchantPattern);
            }

            // 加载生效的附魔等级范围
            dropConfig.minLevel = dropSection.getInt("min-level", 1);
            dropConfig.maxLevel = dropSection.getInt("max-level", 30);

            // 加载基础概率（配置文件中的百分比值转换为小数）
            double chanceValue = dropSection.getDouble("chance", 0.0D);
            dropConfig.chance = chanceValue / 100.0D;

            // 加载触发时的动作指令列表
            dropConfig.actions = new ArrayList<>(dropSection.getStringList("string-action"));

            // 存入配置映射表
            this.enchantDrops.put(enchantKey, dropConfig);
        }

        this.plugin.getLogger().info("已加载 " + this.enchantDrops.size() + " 个附魔掉落配置");
    }

    // 更新全局倍率（供外部调用）
    public void updateGlobalMultiplier(double multiplier) {
        this.globalMultiplier = multiplier;
    }

    // 处理玩家附魔事件
    @EventHandler
    public void onPlayerEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();    // 被附魔的物品
        int enchantLevel = event.getExpLevelCost();  // 消耗的附魔等级

        // 获取玩家专属倍率（如权限组倍率）
        double playerBonusMultiplier = this.plugin.getPlayerBonusMultiplier(player);

        // 遍历所有附魔掉落配置
        for (Map.Entry<String, EnchantDropConfig> entry : this.enchantDrops.entrySet()) {
            String dropName = entry.getKey();
            EnchantDropConfig dropConfig = entry.getValue();

            // 检查物品类型是否匹配
            if (dropConfig.itemPattern != null &&
                    !dropConfig.itemPattern.matcher(item.getType().name()).matches()) {
                continue; // 不匹配则跳过
            }

            // 检查附魔类型是否匹配
            boolean enchantMatch = false;
            // 遍历本次添加的所有附魔
            for (Map.Entry<Enchantment, Integer> enchantEntry : event.getEnchantsToAdd().entrySet()) {
                String enchantName = enchantEntry.getKey().getKey().getKey(); // 获取附魔名称
                if (dropConfig.enchantPattern != null &&
                        dropConfig.enchantPattern.matcher(enchantName).matches()) {
                    enchantMatch = true; // 找到匹配的附魔
                    break;
                }
            }
            // 需要附魔匹配但未找到匹配项
            if (!enchantMatch && dropConfig.enchantPattern != null) {
                continue;
            }

            // 检查附魔等级是否在有效范围内
            if (enchantLevel < dropConfig.minLevel || enchantLevel > dropConfig.maxLevel) {
                continue;
            }

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

            // 概率判定
            if (ThreadLocalRandom.current().nextDouble() < actualChance) {
                // 触发成功：执行配置动作
                executeActions(dropConfig.actions, player, dropName);
                plugin.setCooldown(player);
                return; // 每次附魔只触发一个掉落
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
        this.plugin.getLogManager().logItemObtained(player, dropName, "附魔");

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

    // 内部类：附魔掉落配置数据结构
    private static class EnchantDropConfig {
        Pattern itemPattern;     // 物品匹配正则
        Pattern enchantPattern;  // 附魔匹配正则
        int minLevel;            // 最低生效等级
        int maxLevel;            // 最高生效等级
        double chance;           // 基础概率（0-1）
        List<String> actions;    // 触发时的动作列表
    }
}