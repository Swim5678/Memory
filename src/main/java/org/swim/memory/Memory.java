package org.swim.memory;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Memory extends JavaPlugin {

    @Override
    public void onEnable() {
        // 儲存預設 config.yml（若玩家尚未建立）
        saveDefaultConfig();

        // 註冊 /mem 指令
        MemoryCommand handler = new MemoryCommand(this);
        Objects.requireNonNull(getCommand("mem")).setExecutor(handler);
        Objects.requireNonNull(getCommand("mem")).setTabCompleter(handler);

        // 啟動自動監測排程（非同步，避免阻塞主執行緒）
        int intervalSeconds = getConfig().getInt("monitor.interval-seconds", 30);
        long intervalTicks  = intervalSeconds * 20L; // 1 秒 = 20 ticks
        new MemoryMonitorTask(this).runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);

        getLogger().info("Memory Monitor 已啟動。每 " + intervalSeconds + " 秒自動監測一次。輸入 /mem 查看記憶體狀態。");
    }

    @Override
    public void onDisable() {
        getLogger().info("Memory Monitor 已停止。");
    }
}
