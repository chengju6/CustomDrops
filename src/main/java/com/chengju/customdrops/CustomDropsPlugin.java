package com.chengju.customdrops;

import com.chengju.customdrops.commands.BonusCommand;
import com.chengju.customdrops.commands.LogCommand;
import com.chengju.customdrops.commands.MainCommandExecutor;
import com.chengju.customdrops.listeners.BlockBreakListener;
import com.chengju.customdrops.listeners.EnchantListener;
import com.chengju.customdrops.listeners.FishingListener;
import com.chengju.customdrops.listeners.FoodConsumeListener;
import com.chengju.customdrops.logs.PlayerLogManager;
import com.chengju.customdrops.tasks.LogCleanupTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomDropsPlugin
        extends JavaPlugin {
    // 日志管理器（记录玩家获得的物品）
    private PlayerLogManager logManager;
    // 配置管理器（处理插件配置）
    private ConfigManager configManager;
    // 各功能监听器
    private BlockBreakListener blockBreakListener;
    private FoodConsumeListener foodListener;
    private FishingListener fishingListener;
    // 防刷冷却时间（毫秒）
    private static final long COOLDOWN_TIME = 5000; // 5秒
    // 玩家冷却时间记录
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    private EnchantListener enchantListener;

    // 玩家倍率缓存（UUID -> 倍率值），提升权限检查效率
    private final Map<UUID, Double> bonusCache = new ConcurrentHashMap<>();

    /**
     * 检查玩家是否在冷却时间内
     * @param player 玩家对象
     * @return 是否在冷却中
     */
    public boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = cooldownMap.get(playerId);

        // 如果上次触发时间为空，不在冷却中
        if (lastTime == null) {
            return false;
        }

        // 检查是否在冷却时间内
        return (currentTime - lastTime) < COOLDOWN_TIME;
    }

    /**
     * 设置玩家冷却时间
     * @param player 玩家对象
     */
    public void setCooldown(Player player) {
        cooldownMap.put(player.getUniqueId(), System.currentTimeMillis());
    }
    // 插件加载阶段
    public void onLoad() {
        // 初始化日志管理器
        this.logManager = new PlayerLogManager(this);
    }

    // 插件启用阶段
    public void onEnable() {
        // 保存默认配置（如果不存在）
        saveDefaultConfig();

        // 初始化配置管理器并确保配置完整
        this.configManager = new ConfigManager(this);
        this.configManager.ensureConfigComplete();

        // 确保日志管理器已初始化
        if (this.logManager == null) {
            this.logManager = new PlayerLogManager(this);
        }

        // 注册所有事件监听器
        initializeListeners();

        // 注册命令处理器
        registerCommands();

        // 启动自动清理日志任务（每24小时一次）
        startCleanupTask();

        getLogger().info("CustomDropsPlugin 已启用!");
    }

    // 初始化所有事件监听器
    private void initializeListeners() {
        // 方块破坏监听（挖矿掉落）
        this.blockBreakListener = new BlockBreakListener(this);
        getServer().getPluginManager().registerEvents(this.blockBreakListener, this);

        // 食物消耗监听（食用特定食物触发事件）
        this.foodListener = new FoodConsumeListener(this);
        getServer().getPluginManager().registerEvents(this.foodListener, this);

        // 钓鱼事件监听
        this.fishingListener = new FishingListener(this);
        getServer().getPluginManager().registerEvents(this.fishingListener, this);

        // 附魔事件监听
        this.enchantListener = new EnchantListener(this);
        getServer().getPluginManager().registerEvents(this.enchantListener, this);
    }

    // 注册所有命令处理器
    private void registerCommands() {
        // 注册主命令（/customdrop 和 /cd）
        registerMainCommand("customdrop");
        registerMainCommand("cd");

        // 注册/cdreload命令（配置重载）
        PluginCommand cdReloadCmd = getCommand("cdreload");
        if (cdReloadCmd != null) {
            cdReloadCmd.setExecutor(this); // 使用插件自身作为执行器
        }

        // 注册/ip命令（设置全局倍率）
        PluginCommand ipCmd = getCommand("ip");
        if (ipCmd != null) {
            ipCmd.setExecutor(this);
        }

        // 注册/mylogs命令（查看个人日志）
        LogCommand logCommand = new LogCommand(this);
        PluginCommand myLogsCmd = getCommand("mylogs");
        if (myLogsCmd != null) {
            myLogsCmd.setExecutor(logCommand);
        }

        // 注册/mybonus命令（查看个人倍率）
        PluginCommand myBonusCmd = getCommand("mybonus");
        if (myBonusCmd != null) {
            myBonusCmd.setExecutor(new BonusCommand(this));
        }
    }

    // 注册主命令处理器
    private void registerMainCommand(String commandName) {
        PluginCommand cmd = getCommand(commandName);
        if (cmd != null) {
            cmd.setExecutor(new MainCommandExecutor(this));
        }
    }

    // 启动日志清理定时任务（每24小时一次）
    private void startCleanupTask() {
        try {
            // 1728000 ticks = 24小时 (20 ticks/秒 * 60秒 * 60分钟 * 24小时)
            new LogCleanupTask(this).runTaskTimerAsynchronously(this, 0L, 1728000L);
            getLogger().info("已启动日志清理任务");
        } catch (Exception e) {
            getLogger().warning("启动日志清理任务失败: " + e.getMessage());
        }
    }

    // 插件禁用阶段
    public void onDisable() {
        getLogger().info("===== 插件禁用开始 =====");

        // 清空倍率缓存
        this.bonusCache.clear();

        // 解除监听器引用，便于垃圾回收
        this.blockBreakListener = null;
        this.foodListener = null;
        this.fishingListener = null;
        this.enchantListener = null;

        // 解除管理器引用
        this.logManager = null;
        this.configManager = null;

        getLogger().info("CustomDropsPlugin 已成功禁用!");
    }

    // 获取日志管理器
    public PlayerLogManager getLogManager() {
        return this.logManager;
    }

    // 获取配置管理器
    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    // 获取玩家倍率（带缓存机制）
    public double getPlayerBonusMultiplier(Player player) {
        UUID playerId = player.getUniqueId();

        // 优先从缓存读取
        if (this.bonusCache.containsKey(playerId)) {
            return this.bonusCache.get(playerId);
        }

        // 计算倍率并存入缓存
        double multiplier = calculateBonusMultiplier(player);
        this.bonusCache.put(playerId, multiplier);
        return multiplier;
    }

    // 计算玩家权限倍率
    private double calculateBonusMultiplier(Player player) {
        double maxMultiplier = 0.0D; // 默认倍率
        Pattern pattern = Pattern.compile("cu\\.drop\\.(\\d+)"); // 权限格式：cu.drop.XXX

        // 遍历玩家所有有效权限
        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            String permission = permissionInfo.getPermission();
            Matcher matcher = pattern.matcher(permission);
            if (matcher.matches()) {
                try {
                    int bonusPercent = Integer.parseInt(matcher.group(1)); // 提取百分比值
                    double multiplier = bonusPercent / 100.0D; // 转换为倍率
                    if (multiplier > maxMultiplier) {
                        maxMultiplier = multiplier; // 保留最高倍率
                    }
                } catch (NumberFormatException e) {
                    // 忽略格式错误的权限
                }
            }
        }

        return maxMultiplier;
    }

    // 清除特定玩家的倍率缓存（权限变更时调用）
    public void clearBonusCache(UUID playerId) {
        this.bonusCache.remove(playerId);
    }

    // 命令处理主入口（处理/cdreload和/ip命令）
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cdreload")) {
            return handleReloadCommand(sender);
        }
        if (cmd.getName().equalsIgnoreCase("ip")) {
            return handleIpCommand(sender, args);
        }
        return false;
    }

    // 处理/cdreload命令（配置重载）
    private boolean handleReloadCommand(CommandSender sender) {
        try {
            getLogger().info("开始重载配置...");

            // 重新加载配置文件
            reloadConfig();
            getLogger().info("配置文件已重新加载");

            // 重新初始化配置管理器
            this.configManager = new ConfigManager(this);
            this.configManager.ensureConfigComplete();
            getLogger().info("配置管理器已重新初始化");

            // 重载各监听器的配置
            if (this.blockBreakListener != null) {
                this.blockBreakListener.reloadConfiguration();
                getLogger().info("方块破坏监听器配置已重载");
            }
            if (this.foodListener != null) {
                this.foodListener.loadFoodConfig();
                getLogger().info("食物消耗监听器配置已重载");
            }
            if (this.fishingListener != null) {
                this.fishingListener.loadFishingConfig();
                getLogger().info("钓鱼监听器配置已重载");
            }
            if (this.enchantListener != null) {
                this.enchantListener.loadEnchantConfig();
                getLogger().info("附魔监听器配置已重载");
            }

            // 清空玩家倍率缓存
            this.bonusCache.clear();
            getLogger().info("加成缓存已清除");

            // 通知发送者
            sender.sendMessage(ChatColor.GREEN + "配置已重新加载!");
            getLogger().info("配置重载完成");
            return true;
        } catch (Exception e) {
            getLogger().severe("重载配置时发生错误: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "重载配置失败: " + e.getMessage());
            return false;
        }
    }

    // 处理/ip命令（设置全局倍率）
    private boolean handleIpCommand(CommandSender sender, String[] args) {
        // 参数检查
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "用法: /ip <倍率>");
            return true;
        }

        try {
            // 解析倍率值
            double multiplier = Double.parseDouble(args[0]);
            getLogger().info("设置全局倍率为: " + multiplier);

            // 更新各监听器的全局倍率
            if (this.blockBreakListener != null) {
                this.blockBreakListener.updateGlobalMultiplier(multiplier);
                getLogger().info("方块破坏监听器倍率已更新");
            }
            if (this.foodListener != null) {
                this.foodListener.updateGlobalMultiplier(multiplier);
                getLogger().info("食物消耗监听器倍率已更新");
            }
            if (this.fishingListener != null) {
                this.fishingListener.updateGlobalMultiplier(multiplier);
                getLogger().info("钓鱼监听器倍率已更新");
            }
            if (this.enchantListener != null) {
                this.enchantListener.updateGlobalMultiplier(multiplier);
                getLogger().info("附魔监听器倍率已更新");
            }

            // 显示变更通知
            if (this.configManager != null) {
                PluginConfiguration config = this.configManager.getCurrentConfig();

                // 构建标题消息
                String title = config.getMessageTitle().replace("%amount%", String.valueOf(multiplier));

                // 构建副标题消息（其中%basic%暂时设为1.0）
                String subTitle = config.getMessageSubTitle()
                        .replace("%now%", String.valueOf(multiplier))
                        .replace("%basic%", "1.0")
                        .replace("%total%", String.valueOf(multiplier));

                // 广播消息
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', title));
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', subTitle));
                getLogger().info("全局倍率变更消息已广播");
            }

            sender.sendMessage(ChatColor.GREEN + "全局概率倍率已设置为: " + multiplier);
            return true;
        } catch (NumberFormatException e) {
            // 数字格式错误
            sender.sendMessage(ChatColor.RED + "无效的概率值: " + args[0]);
            return false;
        } catch (Exception e) {
            // 其他错误
            getLogger().severe("设置全局倍率时发生错误: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "设置全局倍率失败: " + e.getMessage());
            return false;
        }
    }
}