package com.chengju.customdrops.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CustomDropTabCompleter implements TabCompleter {

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "reload", "ip", "mylogs", "mybonus"
    );

    private static final List<String> EMPTY_LIST = Collections.emptyList();

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        // 处理 customdrop 和 cd 命令
        if (cmd.getName().equalsIgnoreCase("customdrop") ||
                cmd.getName().equalsIgnoreCase("cd")) {

            // 第一级参数补全
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], MAIN_COMMANDS, new ArrayList<>());
            }

            // 第二级参数补全
            if (args.length == 2) {
                String subCommand = args[0].toLowerCase();

                // /customdrop ip 的参数补全
                if ("ip".equalsIgnoreCase(subCommand)) {
                    return StringUtil.copyPartialMatches(args[1], Arrays.asList("1.0", "1.5", "2.0", "3.0"), new ArrayList<>());
                }

                // /customdrop mylogs 的参数补全
                if ("mylogs".equalsIgnoreCase(subCommand)) {
                    return StringUtil.copyPartialMatches(args[1], Arrays.asList("1", "3", "7", "14", "30"), new ArrayList<>());
                }
            }
        }

        // 处理 ip 命令
        if (cmd.getName().equalsIgnoreCase("ip")) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], Arrays.asList("1.0", "1.5", "2.0", "3.0"), new ArrayList<>());
            }
        }

        // 处理 mylogs 命令
        if (cmd.getName().equalsIgnoreCase("mylogs")) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], Arrays.asList("1", "3", "7", "14", "30"), new ArrayList<>());
            }
        }

        return EMPTY_LIST;
    }
}