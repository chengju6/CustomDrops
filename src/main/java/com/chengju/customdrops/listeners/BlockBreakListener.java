package com.chengju.customdrops.listeners;

import com.chengju.customdrops.CustomDropsPlugin;
import com.chengju.customdrops.PluginConfiguration;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.blockmeta.ChunkManager;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class BlockBreakListener implements Listener {
    // 插件主类实例
    private final CustomDropsPlugin plugin;
    // 当前生效的插件配置（支持热重载）
    private volatile PluginConfiguration currentConfig;
    // 随机数生成器，用于概率计算
    private final Random random = new Random();

    // 玩家操作冷却时间记录（防止高频操作）
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    // 冷却时间阈值（200毫秒）
    private static final long COOLDOWN_TIME = 200L;

    // mcMMO相关区块管理器（用于检测技能放置的方块）
    private ChunkManager mcMMOPlaceStore;

    public BlockBreakListener(CustomDropsPlugin plugin) {
        this.plugin = plugin;
        // 初始化时加载配置
        reloadConfiguration();
    }

    // 重新加载配置（可从外部调用）
    public void reloadConfiguration() {
        this.plugin.getLogger().info("重新加载挖矿配置...");
        // 从插件配置管理器获取最新配置
        this.currentConfig = this.plugin.getConfigManager().getCurrentConfig();
        // 设置mcMMO集成
        setupMcMMO();
        this.plugin.getLogger().info("挖矿配置已重新加载!");
    }

    // 配置mcMMO集成
    private void setupMcMMO() {
        // 检查配置是否需要mcMMO检测
        if (this.currentConfig == null || !this.currentConfig.isCheckMcMMO()) {
            this.plugin.getLogger().info("配置中已禁用mcMMO检测");
            return;
        }
        // 检测mcMMO插件是否存在
        if (this.plugin.getServer().getPluginManager().getPlugin("mcMMO") != null) {
            // 获取mcMMO的方块放置记录管理器
            this.mcMMOPlaceStore = mcMMO.getPlaceStore();
            this.plugin.getLogger().info("已检测到mcMMO，启用技能方块检测");
        } else {
            this.plugin.getLogger().info("未检测到mcMMO，跳过技能方块检测");
        }
    }

    // 更新全局概率倍率（动态调整）
    public void updateGlobalMultiplier(double multiplier) {
        if (this.currentConfig == null) {
            return;
        }
        // 创建新的配置对象（仅更新倍率）
        PluginConfiguration newConfig = new PluginConfiguration(
                this.currentConfig.getToolConfigs(),
                multiplier,
                this.currentConfig.getMessageTitle(),
                this.currentConfig.getMessageSubTitle(),
                this.currentConfig.isCheckMcMMO()
        );
        this.currentConfig = newConfig;
    }

    // 处理方块破坏事件
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // 获取玩家专属倍率（如权限组倍率）
        double playerBonusMultiplier = this.plugin.getPlayerBonusMultiplier(player);

        // 事件已被其他插件取消则直接返回
        if (event.isCancelled())
            return;

        // 冷却检测逻辑
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = this.cooldownMap.get(playerId);
        // 检查200毫秒内的操作冷却
        if (lastTime != null && currentTime - lastTime.longValue() < COOLDOWN_TIME) {
            return;
        }
        this.cooldownMap.put(playerId, Long.valueOf(currentTime));

        // 获取玩家主手工具
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // 配置安全检查
        PluginConfiguration config = this.currentConfig;
        if (config == null) {
            return;
        }
        // 跳过mcMMO技能放置的方块（如技能树）
        if (config.isCheckMcMMO() && this.mcMMOPlaceStore != null && this.mcMMOPlaceStore.isTrue(block)) {
            return;
        }
        // 跳过时运III效果触发（保留原版掉落）
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        // 遍历所有工具配置
        for (PluginConfiguration.ToolConfig toolConfig : config.getToolConfigs().values()) {
            // 正则匹配工具类型（如DIAMOND_PICKAXE）
            Matcher matcher = toolConfig.pattern.matcher(tool.getType().name());
            if (!matcher.matches()) {
                continue;
            }

            // 世界高度限制检测
            Location loc = block.getLocation();
            World world = loc.getWorld();
            if (world == null)
                continue;
            String worldName = world.getName();
            Integer maxY = toolConfig.yMap.get(worldName);
            if (maxY != null && loc.getBlockY() > maxY.intValue()) {
                continue;
            }

            // 获取该方块类型的掉落配置
            Map<String, PluginConfiguration.DropConfig> drops = toolConfig.blockDrops.get(blockType);
            if (drops == null || drops.isEmpty()) {
                continue;
            }

            // 遍历该方块的所有可能掉落
            for (Map.Entry<String, PluginConfiguration.DropConfig> entry : drops.entrySet()) {
                String dropName = entry.getKey();
                PluginConfiguration.DropConfig dropConfig = entry.getValue();

                // 计算实际概率 = 基础概率 * 玩家倍率 * 全局倍率
                double actualChance = dropConfig.chance * config.getGlobalMultiplier() + playerBonusMultiplier;
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
                    // 成功触发：执行掉落动作
                    executeActions(dropConfig.actions, player, dropName);
                    plugin.setCooldown(player); // 设置冷却时间
                    return; // 每次破坏只触发一个掉落（优先顺序）
                }
            }
        }
    }

    // 执行掉落动作指令
    private void executeActions(List<String> actions, Player player, String dropName) {
        String itemName = "未知物品";
        // 初始化物品名称用于日志
        for (String action : actions) {
            if (action.startsWith("[") || action.isEmpty())
                continue;
            itemName = ChatColor.stripColor(action); // 获取第一个非指令动作作为物品名
        }

        // 记录日志系统（玩家、物品、来源）
        this.plugin.getLogManager().logItemObtained(player, dropName, "挖矿");

        // 执行所有配置的动作
        for (String action : actions) {
            try {
                // 执行控制台指令
                if (action.startsWith("[CMD]")) {
                    String command = action.substring(5)
                            .replace("%player%", player.getName())
                            .replace("%world%", player.getWorld().getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    continue;
                }
                // 全服广播
                if (action.startsWith("[BD]")) {
                    String message = action.substring(4)
                            .replace("%player%", player.getName())
                            .replace("%world%", player.getWorld().getName());
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
                    continue;
                }
                // 标题动画（Title）
                if (action.startsWith("[TITLE]")) {
                    String[] parts = action.substring(7).split(";", 5);
                    if (parts.length == 5) {
                        int fadeIn = Integer.parseInt(parts[0]);
                        int stay = Integer.parseInt(parts[1]);
                        int fadeOut = Integer.parseInt(parts[2]);
                        String title = ChatColor.translateAlternateColorCodes('&', parts[3]);
                        String subtitle = ChatColor.translateAlternateColorCodes('&', parts[4]);
                        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                    }
                    continue;
                }
                // 动作栏消息
                if (action.startsWith("[ACTION]")) {
                    String message = ChatColor.translateAlternateColorCodes('&',
                            action.substring(8)
                                    .replace("%player%", player.getName())
                                    .replace("%world%", player.getWorld().getName()));
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(message));
                    continue;
                }
                // 普通聊天消息（默认动作）
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        action.replace("%player%", player.getName())));
            } catch (Exception e) {
                this.plugin.getLogger().warning("执行动作时出错: " + action);
                e.printStackTrace();
            }
        }
    }
}