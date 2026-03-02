package org.swim.memory;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Memory extends JavaPlugin {

    @Override
    public void onEnable() {
        MemoryCommand handler = new MemoryCommand();
        Objects.requireNonNull(getCommand("mem")).setExecutor(handler);
        Objects.requireNonNull(getCommand("mem")).setTabCompleter(handler);

        getLogger().info("Memory Monitor 已啟動。輸入 /mem 查看記憶體狀態。");
    }

    @Override
    public void onDisable() {
        getLogger().info("Memory Monitor 已停止。");
    }
}
