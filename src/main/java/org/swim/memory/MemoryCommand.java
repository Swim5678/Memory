package org.swim.memory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public class MemoryCommand {

    private static final long MB = 1024L * 1024L;
    private static final TextColor GOLD = TextColor.color(0xFFAA00);
    private static final TextColor GRAY = NamedTextColor.GRAY;
    private static final TextColor DARK_GRAY = NamedTextColor.DARK_GRAY;
    private static final TextColor WHITE = NamedTextColor.WHITE;
    private static final TextColor GREEN = NamedTextColor.GREEN;
    private static final TextColor YELLOW = NamedTextColor.YELLOW;
    private static final TextColor RED = NamedTextColor.RED;
    private final AdvancedMonitor plugin;

    public MemoryCommand(AdvancedMonitor plugin) {
        this.plugin = plugin;
    }

    // 格式化：自動選擇 MiB 或 GiB，保留兩位小數
    private static String formatBytes(long bytes) {
        long mib100 = bytes * 100 / MB;
        if (mib100 >= 1024 * 100) {
            long gib100 = bytes * 100 / (MB * 1024);
            return (gib100 / 100) + "." + String.format("%02d", gib100 % 100) + " GiB";
        }
        return (mib100 / 100) + "." + String.format("%02d", mib100 % 100) + " MiB";
    }

    // ── /am reload ────────────────────────────────────────
    public void executeReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(Component.text("AdvancedMonitor 配置已重新載入。").color(GOLD));
    }

    // ── 整體概況（TPS + 記憶體）────────────────────────────
    public void sendOverview(CommandSender sender) {
        sender.sendMessage(Component.text("══ 伺服器概況 ══")
                .color(GOLD)
                .decorate(TextDecoration.BOLD));

        // TPS
        double[] tps = plugin.getServer().getTPS();
        sender.sendMessage(Component.text("── TPS ──").color(GRAY));
        String[] labels = {"1m", "5m", "15m"};
        for (int t = 0; t < 3; t++) {
            double val = Math.min(tps[t], 20.0);
            TextColor tpsColor = val >= 19.0 ? GREEN : val >= 15.0 ? YELLOW : RED;
            int filled = (int) Math.round(val * 20 / 20.0);

            Component tpsBar = Component.text("[").color(DARK_GRAY);
            for (int i = 0; i < 20; i++) {
                tpsBar = tpsBar.append(Component.text("█").color(i < filled ? tpsColor : GRAY));
            }
            tpsBar = tpsBar.append(Component.text("]").color(DARK_GRAY));

            sender.sendMessage(
                    Component.text("  " + labels[t] + ": ").color(GRAY)
                            .append(tpsComponent(tps[t]))
                            .append(Component.text("  ").color(GRAY))
                            .append(tpsBar)
            );
        }

        // 記憶體（不顯示標題，已在概況標題下）
        sendMemoryReport(sender, false);
    }

    // ── 記憶體報告（/mem 原版邏輯，完全不變）────────────────
    public void sendMemoryReport(CommandSender sender) {
        sendMemoryReport(sender, true);
    }

    private void sendMemoryReport(CommandSender sender, boolean showTitle) {
        PterodactylMemoryMonitor.MemoryInfo info =
                PterodactylMemoryMonitor.getMemoryInfo();

        // ── 標題 ─────────────────────────────────────────
        if (showTitle) {
            sender.sendMessage(Component.text("══ 記憶體報告 ══")
                    .color(GOLD)
                    .decorate(TextDecoration.BOLD));
        }

        // ── 容器層級（最重要）────────────────────────────
        if (info.containerUsed >= 0 && info.containerLimit > 0) {

            boolean hasEffective = info.containerEffective >= 0;
            long denominator = (info.heapMax > 0) ? info.heapMax : info.containerLimit;

            long ratio = info.getEffectiveRatio();
            TextColor barColor;
            if (info.isDangerous()) {
                barColor = RED;
            } else if (ratio > 70) {
                barColor = YELLOW;
            } else {
                barColor = GREEN;
            }

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
    }

    private Component tpsComponent(double tps) {
        double capped = Math.min(tps, 20.0);
        String formatted = String.format("%.1f", capped);
        TextColor color = capped >= 19.0 ? GREEN : capped >= 15.0 ? YELLOW : RED;
        return Component.text(formatted).color(color);
    }

    private void sendDetail(CommandSender sender, String label, String value) {
        sender.sendMessage(
                Component.text("  " + label + ": ").color(GRAY)
                        .append(Component.text(value).color(WHITE))
        );
    }
}

