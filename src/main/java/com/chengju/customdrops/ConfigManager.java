package com.chengju.customdrops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    // 插件主类引用
    private final JavaPlugin plugin;
    // 当前生效的配置（支持热重载）
    private volatile PluginConfiguration currentConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // 初始化时加载配置
        loadConfig();
    }

    // 加载配置文件（可被外部调用重载配置）
    public void loadConfig() {
        this.plugin.getLogger().info("加载配置...");
        // 获取Bukkit配置文件对象
        FileConfiguration config = this.plugin.getConfig();

        // 加载全局mcMMO检测开关
        boolean checkMcMMO = config.getBoolean("global.check-mcmmo", true);
        // 加载消息标题模板
        String messageTitle = config.getString("global.message.title", "开启全局概率翻倍 x%amount%");
        // 加载消息副标题模板
        String messageSubTitle = config.getString("global.message.sub-title", "您当前概率翻倍倍数：%now% (权限基础倍数: %basic% 全局倍数: %total%)");

        // 准备存储工具配置的映射表
        Map<String, PluginConfiguration.ToolConfig> newToolConfigs = new HashMap<>();
        // 获取dig（挖矿）配置节
        ConfigurationSection digSection = config.getConfigurationSection("dig");

        // 检查dig配置是否存在
        if (digSection == null) {
            this.plugin.getLogger().warning("配置中没有找到 'dig' 部分");
            return;
        }

        // 遍历所有工具配置
        for (String toolKey : digSection.getKeys(false)) {
            ConfigurationSection toolSection = digSection.getConfigurationSection(toolKey);
            if (toolSection == null)
                continue;

            // 创建新的工具配置对象
            PluginConfiguration.ToolConfig toolConfig = new PluginConfiguration.ToolConfig();
            // 获取工具匹配正则表达式
            String patternStr = toolSection.getString("pattern");

            // 正则表达式校验
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
                    toolConfig.yMap.put(worldName, Integer.valueOf(ySection.getInt(worldName)));
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
                    if (dropsSection == null)
                        continue;

                    // 遍历所有掉落配置
                    for (String dropName : dropsSection.getKeys(false)) {
                        ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropName);
                        if (dropSection == null)
                            continue;

                        // 创建掉落配置对象
                        PluginConfiguration.DropConfig dropConfig = new PluginConfiguration.DropConfig();
                        // 获取基础概率
                        dropConfig.chance = dropSection.getDouble("chance", 0.0D) / 100;
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

        // 保留当前的全局倍率（热重载时不重置）
        double multiplier = (this.currentConfig != null) ? this.currentConfig.getGlobalMultiplier() : 1.0D;
        // 创建新的配置对象
        PluginConfiguration newConfig = new PluginConfiguration(
                newToolConfigs,
                multiplier,
                messageTitle,
                messageSubTitle,
                checkMcMMO
        );

        // 更新当前配置
        this.currentConfig = newConfig;

        this.plugin.getLogger().info("配置已加载! 加载了 " + newToolConfigs.size() + " 个工具配置");
    }

    // 确保配置完整性（外部调用）
    public void ensureConfigComplete() {
        this.plugin.getLogger().info("确保配置完整...");
        loadConfig(); // 重新加载配置
    }

    // 获取当前生效的配置
    public PluginConfiguration getCurrentConfig() {
        return this.currentConfig;
    }
}