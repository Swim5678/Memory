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
    private static final TextColor GOLD = TextColor.color(0xFFAA00);
    private static final TextColor GRAY = NamedTextColor.GRAY;
    private static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
    private static final TextColor WHITE = NamedTextColor.WHITE;
    private static final TextColor GREEN = NamedTextColor.GREEN;
    private static final TextColor YELLOW = NamedTextColor.YELLOW;
    private static final TextColor RED = NamedTextColor.RED;
    private final Memory plugin;
    public MemoryCommand(Memory plugin) {
        this.plugin = plugin;
    }

    // 格式化：自動選擇 MiB 或 GiB，保留兩位小數
    private static String formatBytes(long bytes) {
        long mib100 = bytes * 100 / MB;           // MiB × 100（兩位小數）
        if (mib100 >= 1024 * 100) {
            // GiB
            long gib100 = bytes * 100 / (MB * 1024);
            return (gib100 / 100) + "." + String.format("%02d", gib100 % 100) + " GiB";
        }
        return (mib100 / 100) + "." + String.format("%02d", mib100 % 100) + " MiB";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String[] args) {

        // ── /mem reload ──────────────────────────────────
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Component.text("Memory Monitor 配置已重新載入。").color(GOLD));
            return true;
        }

        PterodactylMemoryMonitor.MemoryInfo info =
                PterodactylMemoryMonitor.getMemoryInfo();

        // ── 標題 ─────────────────────────────────────────
        sender.sendMessage(Component.text("══ 記憶體報告 ══")
                .color(GOLD)
                .decorate(TextDecoration.BOLD));

        // ── 容器層級（最重要）────────────────────────────
        if (info.containerUsed >= 0 && info.containerLimit > 0) {

            // 有效值（扣除 inactive page cache）
            boolean hasEffective = info.containerEffective >= 0;

            // 分母：優先用 -Xmx（heapMax），fallback 到 cgroup limit
            long denominator = (info.heapMax > 0) ? info.heapMax : info.containerLimit;

            // 顏色與進度條依有效值判斷（避免 cache 誤報）
            long ratio = info.getEffectiveRatio();
            TextColor barColor;
            if (info.isDangerous()) {
                barColor = RED;
            } else if (ratio > 70) {
                barColor = YELLOW;
            } else {
                barColor = GREEN;
            }

            // 有效值（扣除 page cache）
            long displayUsed = hasEffective ? info.containerEffective : info.containerUsed;
            long pct = displayUsed * 100 / denominator;
            if (hasEffective) {
                long cacheMiB = (info.containerUsed - info.containerEffective) / MB;
                sender.sendMessage(
                        Component.text("容器: ").color(barColor)
                                .append(Component.text(
                                        formatBytes(info.containerEffective) + " / " + formatBytes(denominator)
                                                + "  (" + pct + "%)"
                                                + (cacheMiB > 0 ? "  [-" + cacheMiB + " MiB cache]" : "")).color(WHITE))
                );
            } else {
                sender.sendMessage(
                        Component.text("容器: ").color(barColor)
                                .append(Component.text(
                                        formatBytes(displayUsed) + " / " + formatBytes(denominator)
                                                + "  (" + pct + ")").color(WHITE))
                );
            }
            int bars = 20;
            int filled = (int) Math.max(0, Math.min(bars, displayUsed * bars / denominator));

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
                formatBytes(info.heapUsed) + " / " + formatBytes(info.heapMax));
        sendDetail(sender, "Non-Heap",
                formatBytes(info.nonHeapUsed));
        sendDetail(sender, "Direct (Netty)",
                formatBytes(info.directUsed));
        sendDetail(sender, "Threads",
                String.valueOf(info.threadCount));

        if (info.containerUsed >= 0) {
            long tracked = info.heapUsed + info.nonHeapUsed + info.directUsed;
            // 優先用有效值計算差值，排除 page cache 的干擾
            long baseline = info.containerEffective >= 0 ? info.containerEffective : info.containerUsed;
            long untracked = baseline - tracked;
            sendDetail(sender, "其他 (GC/Thread Stack/Native)",
                    "~" + formatBytes(Math.max(0, untracked)));
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
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input)) {
                return List.of("reload");
            }
        }
        return Collections.emptyList();
    }
}
