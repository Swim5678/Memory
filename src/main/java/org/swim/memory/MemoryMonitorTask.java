package org.swim.memory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 每隔固定秒數自動偵測記憶體使用率：
 *  - 達到 warn 閾值（預設 90%）：透過 Discord Webhook 在指定頻道發送 @here 警告
 *  - 達到 shutdown 閾值（預設 95%）：發送關機通知後執行 /stop
 *  - 未達任何閾值：靜默，不做任何事
 *
 * 使用「有效使用量」（扣除 inactive_file page cache）作為判斷依據，
 * 避免 page cache 造成誤報。
 */
public class MemoryMonitorTask extends BukkitRunnable {

    private final Memory plugin;
    private final Logger log;

    /** 上一輪是否已發出過 warn 通知，避免同一次持續超標時重複轟炸 */
    private boolean warnSent = false;

    public MemoryMonitorTask(Memory plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    @Override
    public void run() {
        PterodactylMemoryMonitor.MemoryInfo info =
                PterodactylMemoryMonitor.getMemoryInfo();

        long ratio = info.getEffectiveRatio();

        log.info("[MemoryMonitor DEBUG] containerUsed=" + info.containerUsed
                + " containerEffective=" + info.containerEffective
                + " containerLimit=" + info.containerLimit
                + " ratio=" + ratio);

        // 無法取得記憶體資訊（非 Linux 容器環境）→ 靜默略過
        if (ratio < 0) return;

        int warnThreshold     = plugin.getConfig().getInt("monitor.thresholds.warn",     90);
        int shutdownThreshold = plugin.getConfig().getInt("monitor.thresholds.shutdown", 95);

        // ── 達到關機閾值 ──────────────────────────────────────────────────────
        if (ratio >= shutdownThreshold) {
            String discordMsg = buildMessage(
                    plugin.getConfig().getString(
                            "discord.shutdown-message",
                            "🔴 伺服器記憶體使用率已達 {percent}%，即將自動關閉伺服器！"),
                    ratio);
            sendWebhook(discordMsg);
            log.severe("記憶體使用率已達 " + ratio + "%（關機閾值 " + shutdownThreshold + "%），踢出所有玩家並執行關機。");

            String kickRaw = buildMessage(
                    plugin.getConfig().getString(
                            "monitor.kick-message",
                            "<red><bold>伺服器記憶體即將溢出</bold></red>\n<gray>記憶體使用率已達 {percent}%，伺服器緊急關閉中，請稍後再試。</gray>"),
                    ratio);

            // 踢出所有玩家 + 關機必須在主執行緒執行
            Bukkit.getScheduler().runTask(plugin, () -> {
                Component kickMessage = MiniMessage.miniMessage().deserialize(kickRaw);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.kick(kickMessage);
                }
                Bukkit.shutdown();
            });
            return;
        }

        // ── 達到警告閾值 ──────────────────────────────────────────────────────
        if (ratio >= warnThreshold) {
            if (!warnSent) {
                String msg = buildMessage(
                        plugin.getConfig().getString(
                                "discord.warn-message",
                                "⚠️ 伺服器記憶體使用率已達 {percent}%，請注意！"),
                        ratio);
                sendWebhook(msg);
                log.warning("記憶體使用率已達 " + ratio + "%（警告閾值 " + warnThreshold + "%），已發送 Discord 通知。");
                warnSent = true;
            }
            return;
        }

        // ── 低於所有閾值：靜默，重置 warnSent 以便下次再達標時重新通知 ────────
        warnSent = false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私用輔助方法
    // ─────────────────────────────────────────────────────────────────────────

    /** 將訊息樣板中的 {percent} 取代為實際百分比 */
    private String buildMessage(String template, long percent) {
        return template.replace("{percent}", String.valueOf(percent));
    }

    /**
     * 透過 Discord Webhook 發送訊息。
     * 若 webhook-url 未設定則靜默略過。
     * 若 mention-here 為 true 則在訊息前加上 @here。
     * 此方法在非同步執行緒（BukkitRunnable）中呼叫，直接使用阻塞式 HTTP 是安全的。
     */
    private void sendWebhook(String message) {
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isBlank()) {
            log.warning("[MemoryMonitor] discord.webhook-url 未設定，略過 Discord 通知。");
            return;
        }

        boolean mentionHere = plugin.getConfig().getBoolean("discord.mention-here", true);
        String content = mentionHere ? "@here " + message : message;

        // Discord Webhook JSON payload
        String json = "{\"content\":" + toJsonString(content) + "}";

        try {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                log.warning("[MemoryMonitor] Discord Webhook 回傳非成功狀態碼：" + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.warning("[MemoryMonitor] 發送 Discord Webhook 失敗：" + e.getMessage());
        }
    }

    /**
     * 將字串轉為合法 JSON 字串（含引號），對特殊字元進行跳脫。
     * 刻意不引入外部 JSON 函式庫，保持零依賴。
     */
    private static String toJsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
