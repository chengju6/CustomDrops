package com.chengju.customdrops.commands;

import com.chengju.customdrops.CustomDropsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MainCommandExecutor implements CommandExecutor {

    private final CustomDropsPlugin plugin;

    public MainCommandExecutor(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "===== CustomDrops 帮助 =====");
        sender.sendMessage(ChatColor.YELLOW + "/customdrop reload " + ChatColor.GRAY + "- 重新加载配置");
        sender.sendMessage(ChatColor.YELLOW + "/customdrop ip <倍率> " + ChatColor.GRAY + "- 设置全局概率倍率");
        sender.sendMessage(ChatColor.YELLOW + "/customdrop mylogs [天数] " + ChatColor.GRAY + "- 查看物品获取记录");
        sender.sendMessage(ChatColor.YELLOW + "/customdrop mybonus " + ChatColor.GRAY + "- 查看概率加成信息");
        sender.sendMessage(ChatColor.GOLD + "========================");
        sender.sendMessage(ChatColor.GRAY + "快捷命令:");
        sender.sendMessage(ChatColor.YELLOW + "/cdreload " + ChatColor.GRAY + "- 重新加载配置");
        sender.sendMessage(ChatColor.YELLOW + "/ip <倍率> " + ChatColor.GRAY + "- 设置全局概率倍率");
        sender.sendMessage(ChatColor.YELLOW + "/mylogs [天数] " + ChatColor.GRAY + "- 查看物品获取记录");
        sender.sendMessage(ChatColor.YELLOW + "/mybonus " + ChatColor.GRAY + "- 查看概率加成信息");
        return true;
    }
}