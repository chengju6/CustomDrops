package com.chengju.customdrops.listeners;
import com.chengju.customdrops.CustomDropsPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class FoodConsumeListener implements Listener {
    private final Random random = new Random();
    // 特定食物配置
    private final Map<Material, Map<String, DropConfig>> foodDrops = new ConcurrentHashMap<>();
    // 全局配置（通配符*）
    private Map<String, DropConfig> globalDrops = new ConcurrentHashMap<>();
    private final CustomDropsPlugin plugin;


    private double globalMultiplier = 1.0D;

    public FoodConsumeListener(CustomDropsPlugin plugin) {
        this.plugin = plugin;
        loadFoodConfig();
    }


    public void loadFoodConfig() {
        this.foodDrops.clear();
        this.globalDrops.clear(); // 清空全局配置

        FileConfiguration config = this.plugin.getConfig();
        ConfigurationSection consumeSection = config.getConfigurationSection("consume");

        if (consumeSection == null) {
            this.plugin.getLogger().warning("配置中没有找到 'consume' 部分");
            return;
        }

        for (String foodKey : consumeSection.getKeys(false)) {
            // 处理通配符配置
            if ("*".equals(foodKey)) {
                ConfigurationSection dropsSection = consumeSection.getConfigurationSection(foodKey);
                if (dropsSection == null) continue;

                for (String dropName : dropsSection.getKeys(false)) {
                    ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropName);
                    if (dropSection == null) continue;

                    DropConfig dropConfig = new DropConfig();
                    double chanceValue = dropSection.getDouble("chance", 0.0D);
                    dropConfig.chance = chanceValue / 100.0D;
                    dropConfig.actions = new ArrayList<>(dropSection.getStringList("string-action"));

                    this.globalDrops.put(dropName, dropConfig);
                }
                continue;
            }

            // 处理特定食物配置
            Material foodType = Material.matchMaterial(foodKey);
            if (foodType == null) {
                this.plugin.getLogger().warning("无效的食物类型: " + foodKey);
                continue;
            }

            ConfigurationSection dropsSection = consumeSection.getConfigurationSection(foodKey);
            if (dropsSection == null) continue;

            Map<String, DropConfig> drops = new ConcurrentHashMap<>();
            for (String dropName : dropsSection.getKeys(false)) {
                ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropName);
                if (dropSection == null) continue;

                DropConfig dropConfig = new DropConfig();
                double chanceValue = dropSection.getDouble("chance", 0.0D);
                dropConfig.chance = chanceValue / 100.0D;
                dropConfig.actions = new ArrayList<>(dropSection.getStringList("string-action"));

                drops.put(dropName, dropConfig);
            }
            this.foodDrops.put(foodType, drops);
        }

        this.plugin.getLogger().info("已加载 " + this.foodDrops.size() + " 种特定食物的配置和 "
                + this.globalDrops.size() + " 个全局掉落");
    }

    public void updateGlobalMultiplier(double multiplier) {
        this.globalMultiplier = multiplier;
    }


    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack foodItem = event.getItem();
        Material foodType = foodItem.getType();
        double playerBonusMultiplier = this.plugin.getPlayerBonusMultiplier(player);

        // 处理特定食物配置
        Map<String, DropConfig> specificDrops = this.foodDrops.get(foodType);
        if (specificDrops != null && !specificDrops.isEmpty()) {
            for (Map.Entry<String, DropConfig> entry : specificDrops.entrySet()) {
                DropConfig dropConfig = entry.getValue();
                double actualChance = dropConfig.chance * playerBonusMultiplier * this.globalMultiplier;
                actualChance = Math.min(actualChance, 1.0); // 上限100%

                if (this.random.nextDouble() < actualChance) {
                    executeActions(dropConfig.actions, player, entry.getKey());
                    break; // 特定配置只触发一个
                }
            }
        }

        // 处理全局配置（通配符*）
        if (!this.globalDrops.isEmpty()) {
            for (Map.Entry<String, DropConfig> entry : this.globalDrops.entrySet()) {
                DropConfig dropConfig = entry.getValue();
                double actualChance = dropConfig.chance * playerBonusMultiplier * this.globalMultiplier;
                actualChance = Math.min(actualChance, 1.0); // 上限100%

                // 防刷检查
                if (plugin.isOnCooldown(player)) {
                    return;
                }

                if (ThreadLocalRandom.current().nextDouble() < actualChance) {
                    executeActions(dropConfig.actions, player, entry.getKey());
                    plugin.setCooldown(player);
                    break; // 全局配置只触发一个
                }
            }
        }
    }



    private void executeActions(List<String> actions, Player player, String dropName) {
        String itemName = "未知物品";
        for (String action : actions) {
            if (action.startsWith("[") || action.isEmpty())
                continue;  itemName = ChatColor.stripColor(action);
        }



        this.plugin.getLogManager().logItemObtained(player, dropName, "食用");

        for (String action : actions) {
            try {
                if (action.startsWith("[CMD]")) {



                    String command = action.substring(5).replace("%player%", player.getName()).replace("%world%", player.getWorld().getName());
                    Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), command); continue;
                }
                if (action.startsWith("[BD]")) {



                    String message = action.substring(4).replace("%player%", player.getName()).replace("%world%", player.getWorld().getName());
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message)); continue;
                }
                if (action.startsWith("[TITLE]")) {

                    String[] parts = action.substring(7).split(";", 5);
                    if (parts.length == 5) {
                        int fadeIn = Integer.parseInt(parts[0]);
                        int stay = Integer.parseInt(parts[1]);
                        int fadeOut = Integer.parseInt(parts[2]);
                        String title = ChatColor.translateAlternateColorCodes('&', parts[3]);
                        String subtitle = ChatColor.translateAlternateColorCodes('&', parts[4]);
                        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                    }  continue;
                }
                if (action.startsWith("[ACTION]")) {

                    String message = ChatColor.translateAlternateColorCodes('&', action
                            .substring(8)
                            .replace("%player%", player.getName())
                            .replace("%world%", player.getWorld().getName()));

                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,

                            TextComponent.fromLegacyText(message));

                    continue;
                }

                player.sendMessage(ChatColor.translateAlternateColorCodes('&', action
                        .replace("%player%", player.getName())));

            }
            catch (Exception e) {
                this.plugin.getLogger().warning("执行动作时出错: " + action);
                e.printStackTrace();
            }
        }
    }

    private static class DropConfig {
        double chance;
        List<String> actions;

        private DropConfig() {}
    }
}

