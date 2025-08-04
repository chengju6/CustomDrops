package com.chengju.customdrops;

import org.bukkit.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PluginConfiguration {

    private final Map<String, ToolConfig> toolConfigs;
    private final double globalMultiplier;
    private final String messageTitle;
    private final String messageSubTitle;
    private final boolean checkMcMMO;

    public PluginConfiguration(Map<String, ToolConfig> toolConfigs,
                               double globalMultiplier,
                               String messageTitle,
                               String messageSubTitle,
                               boolean checkMcMMO) {
        // 深度拷贝以确保线程安全
        this.toolConfigs = Collections.unmodifiableMap(new HashMap<>(toolConfigs));
        this.globalMultiplier = globalMultiplier;
        this.messageTitle = messageTitle;
        this.messageSubTitle = messageSubTitle;
        this.checkMcMMO = checkMcMMO;
    }

    public Map<String, ToolConfig> getToolConfigs() {
        return toolConfigs;
    }

    public double getGlobalMultiplier() {
        return globalMultiplier;
    }

    public String getMessageTitle() {
        return messageTitle;
    }

    public String getMessageSubTitle() {
        return messageSubTitle;
    }

    public boolean isCheckMcMMO() {
        return checkMcMMO;
    }

    // 工具配置数据结构
    public static class ToolConfig {
        public Pattern pattern;
        public Map<String, Integer> yMap = new HashMap<>();
        public Map<Material, Map<String, DropConfig>> blockDrops = new ConcurrentHashMap<>();
    }

    // 掉落配置数据结构
    public static class DropConfig {
        public double chance; // 概率（0.0-1.0）
        public String permission; // 权限节点（可选）
        public List<String> actions;
        public String dropName; // 添加掉落物名称字段
    }
}