package com.chengju.customdrops.commands;

import com.chengju.customdrops.CustomDropsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.List;
import java.util.stream.Collectors;

public class BonusCommand implements CommandExecutor {

    private final CustomDropsPlugin plugin;

    public BonusCommand(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此命令!");
            return true;
        }

        Player player = (Player) sender;
        double bonusMultiplier = plugin.getPlayerBonusMultiplier(player);
        int bonusPercent = (int) ((bonusMultiplier - 1.0) * 100);

        // 获取玩家拥有的所有cu.drop权限
        List<String> bonusPermissions = player.getEffectivePermissions().stream()
                .map(perm -> perm.getPermission())
                .filter(p -> p.startsWith("cu.drop."))
                .collect(Collectors.toList());

        player.sendMessage(ChatColor.GOLD + "===== 您的概率加成信息 =====");
        player.sendMessage(ChatColor.GREEN + "加成权限: " + ChatColor.AQUA + String.join(", ", bonusPermissions));
        player.sendMessage(ChatColor.GREEN + "加成百分比: " + ChatColor.AQUA + bonusPercent + "%");
        player.sendMessage(ChatColor.GREEN + "加成倍率: " + ChatColor.AQUA + bonusMultiplier);
        player.sendMessage(ChatColor.GOLD + "========================");

        return true;
    }
}