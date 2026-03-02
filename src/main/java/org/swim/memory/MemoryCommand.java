package org.swim.memory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MemoryCommand implements CommandExecutor, TabCompleter {

    private static final long MB = 1024L * 1024L;

    // 自訂 RGB 顏色
    private static final TextColor GOLD     = TextColor.color(0xFFAA00);
    private static final TextColor GRAY     = NamedTextColor.GRAY;
    private static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
    private static final TextColor WHITE    = NamedTextColor.WHITE;
    private static final TextColor GREEN    = NamedTextColor.GREEN;
    private static final TextColor YELLOW   = NamedTextColor.YELLOW;
    private static final TextColor RED      = NamedTextColor.RED;

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String[] args) {

        PterodactylMemoryMonitor.MemoryInfo info =
                PterodactylMemoryMonitor.getMemoryInfo();

        // ── 標題 ─────────────────────────────────────────
        sender.sendMessage(Component.text("══ 記憶體報告 ══")
                .color(GOLD)
                .decorate(TextDecoration.BOLD));

        // ── 容器層級（最重要）────────────────────────────
        if (info.containerUsed > 0 && info.containerLimit > 0) {
            long usedMB  = info.containerUsed  / MB;
            long limitMB = info.containerLimit / MB;
            String pct   = info.getUsagePercentage();

            long ratio = info.containerUsed * 100 / info.containerLimit;
            TextColor barColor;
            if (info.isDangerous()) {
                barColor = RED;
            } else if (ratio > 70) {
                barColor = YELLOW;
            } else {
                barColor = GREEN;
            }

            sender.sendMessage(
                Component.text("容器總使用: ").color(barColor)
                    .append(Component.text(usedMB + " / " + limitMB + " MB (" + pct + ")").color(WHITE))
            );

            // 進度條
            int bars   = 20;
            int filled = (int) (info.containerUsed * bars / info.containerLimit);

            Component bar = Component.text("[").color(DARK_GRAY);
            for (int i = 0; i < bars; i++) {
                bar = bar.append(Component.text("█").color(i < filled ? barColor : GRAY));
            }
            bar = bar.append(Component.text("]").color(DARK_GRAY));
            sender.sendMessage(bar);

        } else {
            sender.sendMessage(Component.text("無法讀取容器記憶體（cgroup）").color(RED));
            sender.sendMessage(Component.text("提示：此功能僅在 Linux 容器（Pterodactyl）環境下有效")
                    .color(GRAY));
        }

        // ── JVM 細節 ─────────────────────────────────────
        sender.sendMessage(Component.text("── JVM 細節 ──").color(GRAY));

        sendDetail(sender, "Heap",
                (info.heapUsed / MB) + " / " + (info.heapMax / MB) + " MB");
        sendDetail(sender, "Non-Heap",
                (info.nonHeapUsed / MB) + " MB");
        sendDetail(sender, "Direct (Netty)",
                (info.directUsed / MB) + " MB");
        sendDetail(sender, "Threads",
                String.valueOf(info.threadCount));

        if (info.containerUsed > 0) {
            long tracked   = info.heapUsed + info.nonHeapUsed + info.directUsed;
            long untracked = info.containerUsed - tracked;
            sendDetail(sender, "其他 (GC/Thread Stack/Native)",
                    "~" + (untracked / MB) + " MB");
        }

        // ── 警告 ──────────────────────────────────────────
        if (info.isDangerous()) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("⚠ 記憶體使用超過 85%！")
                    .color(RED).decorate(TextDecoration.BOLD));
            sender.sendMessage(Component.text("  可能隨時被 OOM Kill！")
                    .color(RED).decorate(TextDecoration.BOLD));
        }

        return true;
    }

    private void sendDetail(CommandSender sender, String label, String value) {
        sender.sendMessage(
            Component.text("  " + label + ": ").color(GRAY)
                .append(Component.text(value).color(WHITE))
        );
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String alias,
                                      String[] args) {
        return Collections.emptyList();
    }
}
