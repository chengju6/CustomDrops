package com.chengju.customdrops;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.blockmeta.ChunkManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginManager implements Listener {
    // 插件主类引用
    private final JavaPlugin plugin;
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
    // mcMMO检测开关
    private boolean checkMcMMO = true;

    public PluginManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 初始化方法（由主插件调用）
    public void initialize() {
        // 加载配置
        reloadConfiguration();
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 设置mcMMO集成
        setupMcMMO();
    }

    // 关闭方法（由主插件调用）
    public void shutdown() {
        // 清理资源
        this.currentConfig = null;
        this.mcMMOPlaceStore = null;
        this.cooldownMap.clear();
    }

    // 配置mcMMO集成
    private void setupMcMMO() {
        // 检查配置是否需要mcMMO检测
        if (!this.checkMcMMO) {
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

    // 重新加载配置
    public void reloadConfiguration() {
        // 获取配置文件
        FileConfiguration config = this.plugin.getConfig();
        // 加载mcMMO检测开关
        this.checkMcMMO = config.getBoolean("check-mcmmo", true);
        // 加载消息模板
        String messageTitle = config.getString("message.title", "开启全局概率翻倍 x%amount%");
        String messageSubTitle = config.getString("message.sub-title", "您当前概率翻倍倍数：%now% (权限基础倍数: %basic% 全局倍数: %total%)");

        // 准备存储工具配置的映射表
        Map<String, ToolConfig> newToolConfigs = new HashMap<>();
        // 获取dig（挖矿）配置节
        ConfigurationSection digSection = config.getConfigurationSection("dig");

        if (digSection == null) {
            this.plugin.getLogger().warning("配置中没有找到 'dig' 部分");
        } else {
            // 遍历所有工具配置
            for (String toolKey : digSection.getKeys(false)) {
                ConfigurationSection toolSection = digSection.getConfigurationSection(toolKey);
                if (toolSection == null) continue;

                // 创建新的工具配置对象
                ToolConfig toolConfig = new ToolConfig();

                // 加载工具匹配正则表达式
                String patternStr = toolSection.getString("pattern");
                if (patternStr == null || patternStr.isEmpty()) {
                    this.plugin.getLogger().warning("工具 " + toolKey + " 缺少 pattern 配置");
                    continue;
                }

                try {
                    // 编译正则表达式
                    toolConfig.pattern = Pattern.compile(patternStr);
                } catch (Exception e) {
                    this.plugin.getLogger().warning("无效的正则表达式: " + patternStr);
                    continue;
                }

                // 加载世界高度限制配置
                ConfigurationSection ySection = toolSection.getConfigurationSection("y");
                if (ySection != null) {
                    for (String worldName : ySection.getKeys(false)) {
                        // 存储世界名->最大高度的映射
                        toolConfig.yMap.put(worldName, ySection.getInt(worldName));
                    }
                }

                // 加载方块掉落配置
                ConfigurationSection blockSection = toolSection.getConfigurationSection("dig-type");
                if (blockSection != null) {
                    for (String blockType : blockSection.getKeys(false)) {
                        // 将字符串转换为方块材质
                        Material material = Material.matchMaterial(blockType);
                        if (material == null) {
                            this.plugin.getLogger().warning("无效的方块类型: " + blockType);
                            continue;
                        }

                        // 获取该方块类型的掉落配置节
                        ConfigurationSection dropsSection = blockSection.getConfigurationSection(blockType);
                        if (dropsSection == null) continue;

                        // 遍历所有掉落配置
                        for (String dropName : dropsSection.getKeys(false)) {
                            ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropName);
                            if (dropSection == null) continue;

                            // 创建掉落配置对象
                            DropConfig dropConfig = new DropConfig();
                            // 获取基础概率
                            double chanceValue = dropSection.getDouble("chance", 0.0D);
                            dropConfig.chance = chanceValue / 100.0D;
                            // 获取动作指令列表
                            dropConfig.actions = new ArrayList<>(dropSection.getStringList("string-action"));

                            // 存储到工具配置中（线程安全）
                            toolConfig.blockDrops
                                    .computeIfAbsent(material, k -> new ConcurrentHashMap<>())
                                    .put(dropName, dropConfig);
                        }
                    }
                }

                // 将工具配置存入映射表
                newToolConfigs.put(toolKey, toolConfig);
            }
        }

        // 保留当前的全局倍率（热重载时不重置）
        double multiplier = (this.currentConfig != null) ? this.currentConfig.globalMultiplier : 1.0D;
        // 创建新的配置对象
        PluginConfiguration newConfig = new PluginConfiguration(
                newToolConfigs,
                multiplier,
                messageTitle,
                messageSubTitle
        );

        // 更新当前配置
        this.currentConfig = newConfig;
        // 重新设置mcMMO
        setupMcMMO();

        this.plugin.getLogger().info("配置已重新加载!");
    }

    // 更新全局倍率
    public void updateGlobalMultiplier(double multiplier) {
        if (this.currentConfig == null) return;

        // 创建新的配置对象（仅更新倍率）
        PluginConfiguration newConfig = new PluginConfiguration(
                this.currentConfig.toolConfigs,
                multiplier,
                this.currentConfig.messageTitle,
                this.currentConfig.messageSubTitle
        );

        this.currentConfig = newConfig;
    }

    // 获取消息标题模板
    public String getMessageTitle() {
        return this.currentConfig != null ? this.currentConfig.messageTitle : "";
    }

    // 获取消息副标题模板
    public String getMessageSubTitle() {
        return this.currentConfig != null ? this.currentConfig.messageSubTitle : "";
    }

    // 处理方块破坏事件
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // 事件已被其他插件取消则直接返回
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 冷却检测逻辑
        long currentTime = System.currentTimeMillis();
        Long lastTime = this.cooldownMap.get(playerId);
        if (lastTime != null && currentTime - lastTime < COOLDOWN_TIME) {
            return;
        }
        this.cooldownMap.put(playerId, currentTime);

        ItemStack tool = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // 配置安全检查
        PluginConfiguration config = this.currentConfig;
        if (config == null) return;

        // 跳过mcMMO技能放置的方块（如技能树）
        if (this.checkMcMMO && this.mcMMOPlaceStore != null && this.mcMMOPlaceStore.isTrue(block)) {
            return;
        }

        // 跳过时运III效果触发（保留原版掉落）
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        // 遍历所有工具配置
        for (ToolConfig toolConfig : config.toolConfigs.values()) {
            // 正则匹配工具类型（如DIAMOND_PICKAXE）
            Matcher matcher = toolConfig.pattern.matcher(tool.getType().name());
            if (!matcher.matches()) continue;

            // 世界高度限制检测
            Location loc = block.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;

            String worldName = world.getName();
            Integer maxY = toolConfig.yMap.get(worldName);
            if (maxY != null && loc.getBlockY() > maxY) {
                continue;
            }

            // 获取该方块类型的掉落配置
            Map<String, DropConfig> drops = toolConfig.blockDrops.get(blockType);
            if (drops == null || drops.isEmpty()) continue;

            // 遍历该方块的所有可能掉落
            for (DropConfig dropConfig : drops.values()) {
                // 计算实际概率 = 基础概率 × 全局倍率
                double chance = dropConfig.chance * config.globalMultiplier;

                // 概率判定
                if (this.random.nextDouble() < chance) {
                    // 成功触发：执行掉落动作
                    executeActions(dropConfig.actions, player, "挖矿");
                    return; // 每次破坏只触发一个掉落
                }
            }
        }
    }

    // 处理食物消耗事件
    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack foodItem = event.getItem();
        Material foodType = foodItem.getType();

        // 获取配置文件
        FileConfiguration config = this.plugin.getConfig();
        ConfigurationSection consumeSection = config.getConfigurationSection("consume");
        if (consumeSection == null) return;

        // 获取特定食物类型的配置
        ConfigurationSection foodSection = consumeSection.getConfigurationSection(foodType.name());
        if (foodSection == null) return;

        // 遍历所有掉落配置
        for (String dropName : foodSection.getKeys(false)) {
            ConfigurationSection dropSection = foodSection.getConfigurationSection(dropName);
            if (dropSection == null) continue;

            // 计算基础概率
            double chanceValue = dropSection.getDouble("chance", 0.0D);
            double chance = chanceValue / 100.0D;

            // 计算实际概率 = 基础概率 × 全局倍率
            double actualChance = chance * (this.currentConfig != null ? this.currentConfig.globalMultiplier : 1.0D);
            if (actualChance > 1.0D) actualChance = 1.0D;

            // 概率判定
            if (this.random.nextDouble() < actualChance) {
                List<String> actions = dropSection.getStringList("string-action");
                executeActions(actions, player, "食用");
                return; // 每次食用只触发一个掉落
            }
        }
    }

    // 处理钓鱼事件
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        // 仅处理成功钓到鱼的场景
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        // 获取配置文件
        FileConfiguration config = this.plugin.getConfig();
        ConfigurationSection fishingSection = config.getConfigurationSection("fishing");
        if (fishingSection == null) return;

        // 遍历所有钓鱼掉落配置
        for (String dropName : fishingSection.getKeys(false)) {
            ConfigurationSection dropSection = fishingSection.getConfigurationSection(dropName);
            if (dropSection == null) continue;

            // 计算基础概率
            double chanceValue = dropSection.getDouble("chance", 0.0D);
            double chance = chanceValue / 100.0D;

            // 计算实际概率 = 基础概率 × 全局倍率
            double actualChance = chance * (this.currentConfig != null ? this.currentConfig.globalMultiplier : 1.0D);
            if (actualChance > 1.0D) actualChance = 1.0D;

            // 概率判定
            if (this.random.nextDouble() < actualChance) {
                List<String> actions = dropSection.getStringList("string-action");
                executeActions(actions, player, "钓鱼");
                return; // 每次钓鱼只触发一个掉落
            }
        }
    }

    // 处理附魔事件
    @EventHandler
    public void onPlayerEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        int enchantLevel = event.getExpLevelCost();

        // 获取配置文件
        FileConfiguration config = this.plugin.getConfig();
        ConfigurationSection enchantSection = config.getConfigurationSection("enchant");
        if (enchantSection == null) return;

        // 遍历所有附魔配置
        for (String configName : enchantSection.getKeys(false)) {
            ConfigurationSection configSection = enchantSection.getConfigurationSection(configName);
            if (configSection == null) continue;

            // 检查物品类型是否匹配
            String itemPattern = configSection.getString("item-pattern", "");
            if (!itemPattern.isEmpty() && !Pattern.matches(itemPattern, item.getType().name())) {
                continue;
            }

            // 检查附魔类型是否匹配
            boolean enchantMatch = false;
            String enchantPattern = configSection.getString("enchant-pattern", "");
            if (!enchantPattern.isEmpty()) {
                for (Map.Entry<Enchantment, Integer> enchantEntry : event.getEnchantsToAdd().entrySet()) {
                    String enchantName = enchantEntry.getKey().getKey().getKey();
                    if (Pattern.matches(enchantPattern, enchantName)) {
                        enchantMatch = true;
                        break;
                    }
                }
            }

            // 需要附魔匹配但未找到匹配项
            if (!enchantMatch && !enchantPattern.isEmpty()) {
                continue;
            }

            // 检查附魔等级是否在有效范围内
            int minLevel = configSection.getInt("min-level", 1);
            int maxLevel = configSection.getInt("max-level", 30);
            if (enchantLevel < minLevel || enchantLevel > maxLevel) {
                continue;
            }

            // 计算基础概率
            double chanceValue = configSection.getDouble("chance", 0.0D);
            double chance = chanceValue / 100.0D;

            // 计算实际概率 = 基础概率 × 全局倍率
            double actualChance = chance * (this.currentConfig != null ? this.currentConfig.globalMultiplier : 1.0D);
            if (actualChance > 1.0D) actualChance = 1.0D;

            // 概率判定
            if (this.random.nextDouble() < actualChance) {
                List<String> actions = configSection.getStringList("string-action");
                executeActions(actions, player, "附魔");
                return; // 每次附魔只触发一个掉落
            }
        }
    }

    // 执行配置的动作指令
    private void executeActions(List<String> actions, Player player, String source) {
        String itemName = "未知物品";
        // 初始化物品名称用于日志
        for (String action : actions) {
            if (action.startsWith("[") || action.isEmpty()) continue;
            itemName = ChatColor.stripColor(action); // 获取非指令动作作为物品名
            break;
        }

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
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("执行动作时出错: " + action);
                e.printStackTrace();
            }
        }
    }

    // 内部类：插件配置数据结构
    private static class PluginConfiguration {
        final Map<String, ToolConfig> toolConfigs; // 工具配置映射
        final double globalMultiplier;             // 全局倍率
        final String messageTitle;                 // 消息标题模板
        final String messageSubTitle;              // 消息副标题模板

        PluginConfiguration(Map<String, ToolConfig> toolConfigs, double globalMultiplier,
                            String messageTitle, String messageSubTitle) {
            this.toolConfigs = Collections.unmodifiableMap(new HashMap<>(toolConfigs));
            this.globalMultiplier = globalMultiplier;
            this.messageTitle = messageTitle;
            this.messageSubTitle = messageSubTitle;
        }
    }

    // 内部类：工具配置数据结构
    private static class ToolConfig {
        Pattern pattern; // 工具匹配正则
        Map<String, Integer> yMap = new HashMap<>(); // 世界高度限制
        Map<Material, Map<String, DropConfig>> blockDrops = new ConcurrentHashMap<>(); // 方块掉落配置
    }

    // 内部类：掉落配置数据结构
    private static class DropConfig {
        double chance;           // 基础概率（0-1）
        List<String> actions;     // 触发时的动作列表
    }
}