package com.chengju.customdrops.commands;

import com.chengju.customdrops.CustomDropsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class LogCommand implements CommandExecutor {

    private final CustomDropsPlugin plugin;

    public LogCommand(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令!");
            return true;
        }

        Player player = (Player) sender;

        // 默认显示最近1天日志
        int days = 1;

        // 解析天数参数
        if (args.length > 0) {
            try {
                days = Integer.parseInt(args[0]);
                if (days < 1 || days > 30) {
                    player.sendMessage(ChatColor.RED + "天数必须在1-30之间");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "无效的天数: " + args[0]);
                return true;
            }
        }

        // 获取日志
        Map<String, String> logs = plugin.getLogManager().getRecentPlayerLogs(player, days);

        if (logs.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "最近" + days + "天内没有获取物品的记录");
            return true;
        }

        // 显示日志
        player.sendMessage(ChatColor.GOLD + "===== 物品获取记录 (" + days + "天内) =====");
        for (Map.Entry<String, String> entry : logs.entrySet()) {
            player.sendMessage(ChatColor.GREEN + entry.getKey() + ChatColor.WHITE + ": " +
                    ChatColor.AQUA + entry.getValue());
        }
        player.sendMessage(ChatColor.GOLD + "===============================");
        player.sendMessage(ChatColor.GRAY + "共找到 " + logs.size() + " 条记录");

        return true;
    }
}