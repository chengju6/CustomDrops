//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.chengju.customdrops.tasks;

import com.chengju.customdrops.CustomDropsPlugin;
import java.io.File;
import java.util.Calendar;
import java.util.Date;
import org.bukkit.scheduler.BukkitRunnable;

public class LogCleanupTask extends BukkitRunnable {
    private final CustomDropsPlugin plugin;
    private final int retentionDays;

    public LogCleanupTask(CustomDropsPlugin plugin) {
        this.plugin = plugin;
        this.retentionDays = plugin.getConfig().getInt("log-retention-days", 30);
    }

    public void run() {
        if (this.retentionDays > 0) {
            File logDir = new File(this.plugin.getDataFolder(), "player_log");
            if (logDir.exists()) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(5, -this.retentionDays);
                Date cutoffDate = calendar.getTime();
                this.cleanupDirectory(logDir, cutoffDate);
            }
        }
    }

    private void cleanupDirectory(File dir, Date cutoffDate) {
        File[] files = dir.listFiles();
        if (files != null) {
            File[] var4 = files;
            int var5 = files.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                File file = var4[var6];
                if (file.isDirectory()) {
                    this.cleanupDirectory(file, cutoffDate);
                    if (file.listFiles() == null || file.listFiles().length == 0) {
                        file.delete();
                    }
                } else if (file.lastModified() < cutoffDate.getTime()) {
                    file.delete();
                }
            }

        }
    }
}
