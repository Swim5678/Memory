package org.swim.memory;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancedMonitor extends JavaPlugin {

    private MemoryCommand handler;

    @Override
    public void onLoad() {
        handler = new MemoryCommand(this);

        // COMMANDS 事件在 onEnable() 之前觸發，必須在 onLoad() 裡註冊
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            // /mem
            commands.register(
                    Commands.literal("mem")
                            .requires(src -> src.getSender().hasPermission("memory.check"))
                            .executes(ctx -> {
                                handler.sendMemoryReport(ctx.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "查看伺服器記憶體使用情況"
            );

            // /am
            commands.register(
                    Commands.literal("am")
                            .requires(src -> src.getSender().hasPermission("memory.check"))
                            .executes(ctx -> {
                                handler.sendOverview(ctx.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("info")
                                    .executes(ctx -> {
                                        handler.sendOverview(ctx.getSource().getSender());
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .then(Commands.literal("reload")
                                    .executes(ctx -> {
                                        handler.executeReload(ctx.getSource().getSender());
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    "AdvancedMonitor 管理指令"
            );
        });
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 啟動自動監測排程（非同步，避免阻塞主執行緒）
        int intervalSeconds = getConfig().getInt("monitor.interval-seconds", 30);
        long intervalTicks = intervalSeconds * 20L;
        new MemoryMonitorTask(this).runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);

        getLogger().info("AdvancedMonitor 已啟動。每 " + intervalSeconds + " 秒自動監測一次。輸入 /mem 查看記憶體狀態。");
    }

    @Override
    public void onDisable() {
        getLogger().info("AdvancedMonitor 已停止。");
    }
}
