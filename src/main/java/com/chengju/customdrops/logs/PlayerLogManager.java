package com.chengju.customdrops.logs;

import com.chengju.customdrops.CustomDropsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PlayerLogManager {

    private final CustomDropsPlugin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public PlayerLogManager(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 记录玩家获取物品
     * @param player 玩家对象
     * @param itemName 物品名称
     * @param source 获取来源
     */
    public void logItemObtained(Player player, String itemName, String source) {
        String dateStr = dateFormat.format(new Date());
        String timeStr = timeFormat.format(new Date());

        // 获取日志文件
        File logFile = getPlayerLogFile(player, dateStr);
        if (logFile == null) {
            plugin.getLogger().warning("无法创建日志文件: " + dateStr);
            return;
        }

        // 创建或加载YAML配置
        YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);

        // 获取玩家日志节点
        String playerName = player.getName();
        ConfigurationSection playerSection = config.getConfigurationSection(playerName);
        if (playerSection == null) {
            playerSection = config.createSection(playerName);
        }

        // 添加新记录（格式：物品名称(来源)
        String logEntry = itemName + "(" + source + ")";
        playerSection.set(timeStr, logEntry);

        // 保存文件
        try {
            config.save(logFile);
            plugin.getLogger().info("已记录: " + playerName + " 获得了 " + itemName + " (" + source + ")");
        } catch (IOException e) {
            plugin.getLogger().warning("保存玩家日志时出错: " + playerName);
            e.printStackTrace();
        }
    }

    /**
     * 获取玩家日志文件
     * @param player 玩家对象
     * @param dateStr 日期字符串 (格式: yyyy-MM-dd)
     * @return 日志文件对象
     */
    private File getPlayerLogFile(Player player, String dateStr) {
        // 解析日期
        String[] dateParts = dateStr.split("-");
        if (dateParts.length != 3) {
            plugin.getLogger().warning("无效的日期格式: " + dateStr);
            return null;
        }

        // 创建目录结构
        File logDir = new File(plugin.getDataFolder(), "player_logs");
        File yearDir = new File(logDir, dateParts[0]);
        File monthDir = new File(yearDir, dateParts[1]);
        File dayDir = new File(monthDir, dateParts[2]);

        if (!dayDir.exists() && !dayDir.mkdirs()) {
            plugin.getLogger().warning("无法创建日志目录: " + dayDir.getAbsolutePath());
            return null;
        }

        // 创建日志文件
        return new File(dayDir, player.getName() + ".yml");
    }

    /**
     * 获取玩家最近日志
     * @param player 玩家对象
     * @param days 天数
     * @return 日志记录 (时间 -> 日志内容)
     */
    public Map<String, String> getRecentPlayerLogs(Player player, int days) {
        Map<String, String> logs = new LinkedHashMap<>();

        // 获取当前日期
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 遍历指定天数内的日志
        for (int i = 0; i < days; i++) {
            String dateStr = dateFormat.format(calendar.getTime());

            // 获取日志文件
            File logFile = getPlayerLogFile(player, dateStr);
            if (logFile == null || !logFile.exists()) {
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                continue;
            }

            // 加载日志
            YamlConfiguration config = YamlConfiguration.loadConfiguration(logFile);
            ConfigurationSection playerSection = config.getConfigurationSection(player.getName());
            if (playerSection != null) {
                for (String time : playerSection.getKeys(false)) {
                    logs.put(dateStr + " " + time, playerSection.getString(time));
                }
            }

            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }

        return logs;
    }

    /**
     * 关闭日志管理器（清理资源）
     */
    public void close() {
        // 在实际项目中，这里会关闭文件流或数据库连接
        plugin.getLogger().info("日志管理器资源已释放");
    }
}