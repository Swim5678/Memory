package org.swim.memory;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;

public class PterodactylMemoryMonitor {

    /**
     * 取得容器的記憶體使用量（Pterodactyl 面板上看到的那個數字）
     * 注意：此值包含 kernel page cache，可能比實際「不可回收」記憶體高。
     */
    public static long getContainerUsedBytes() {
        // cgroup v2（較新）
        try {
            Path path = Path.of("/sys/fs/cgroup/memory.current");
            if (Files.exists(path)) {
                return Long.parseLong(Files.readString(path).trim());
            }
        } catch (Exception ignored) {
        }

        // cgroup v1（較舊）
        try {
            Path path = Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes");
            if (Files.exists(path)) {
                return Long.parseLong(Files.readString(path).trim());
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    /**
     * 取得容器的有效記憶體使用量（扣除可回收的 page cache）。
     * 這個數字更能反映「是否快要 OOM」的真實壓力：
     * effective = memory.current - inactive_file
     * <p>
     * inactive_file 是核心可在記憶體壓力時自動回收的檔案 cache，
     * 不佔用 Java heap，也不會導致 OOM。
     */
    public static long getContainerEffectiveUsed() {
        // cgroup v2：memory.current - inactive_file（來自 memory.stat）
        try {
            Path currentPath = Path.of("/sys/fs/cgroup/memory.current");
            Path statPath = Path.of("/sys/fs/cgroup/memory.stat");
            if (Files.exists(currentPath) && Files.exists(statPath)) {
                long current = Long.parseLong(Files.readString(currentPath).trim());
                for (String line : Files.readAllLines(statPath)) {
                    if (line.startsWith("inactive_file ")) {
                        long inactiveFile = Long.parseLong(line.split(" ")[1]);
                        return Math.max(0, current - inactiveFile);
                    }
                }
                // 找不到 inactive_file 就回傳 current（保守估計）
                return current;
            }
        } catch (Exception ignored) {
        }

        // cgroup v1：usage_in_bytes - inactive_file（來自 memory.stat）
        try {
            Path usagePath = Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes");
            Path statPath = Path.of("/sys/fs/cgroup/memory/memory.stat");
            if (Files.exists(usagePath) && Files.exists(statPath)) {
                long usage = Long.parseLong(Files.readString(usagePath).trim());
                for (String line : Files.readAllLines(statPath)) {
                    if (line.startsWith("total_inactive_file ")) {
                        long inactiveFile = Long.parseLong(line.split(" ")[1]);
                        return Math.max(0, usage - inactiveFile);
                    }
                }
                return usage;
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    /**
     * 取得容器的記憶體上限（Pterodactyl 面板上設定的那個數字）
     */
    public static long getContainerLimitBytes() {
        // cgroup v2
        try {
            Path path = Path.of("/sys/fs/cgroup/memory.max");
            if (Files.exists(path)) {
                String val = Files.readString(path).trim();
                if (!"max".equals(val)) {
                    return Long.parseLong(val);
                }
            }
        } catch (Exception ignored) {
        }

        // cgroup v1
        try {
            Path path = Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes");
            if (Files.exists(path)) {
                long val = Long.parseLong(Files.readString(path).trim());
                if (val < Long.MAX_VALUE / 2) return val;
            }
        } catch (Exception ignored) {
        }

        return -1;
    }

    /**
     * 完整報告
     */
    public static MemoryInfo getMemoryInfo() {
        MemoryInfo info = new MemoryInfo();

        // Container 層級（最重要）
        info.containerUsed = getContainerUsedBytes();
        info.containerLimit = getContainerLimitBytes();
        info.containerEffective = getContainerEffectiveUsed();

        // JVM 層級
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        info.heapUsed = memBean.getHeapMemoryUsage().getUsed();
        info.heapMax = memBean.getHeapMemoryUsage().getMax();
        info.nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed();

        // Direct buffers（Netty）
        for (BufferPoolMXBean bp :
                ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            info.directUsed += bp.getMemoryUsed();
        }

        // Thread 數量
        info.threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

        return info;
    }

    public static class MemoryInfo {
        public long containerUsed = -1;  // cgroup 原始使用量（含 page cache）— 面板顯示值
        public long containerEffective = -1;  // cgroup 有效使用量（扣除 inactive_file）— OOM 判斷依據
        public long containerLimit = -1;  // cgroup 上限
        public long heapUsed;
        public long heapMax;
        public long nonHeapUsed;
        public long directUsed;
        public int threadCount;

        /**
         * 面板顯示的百分比（含 cache）
         */
        public String getRawPercentage() {
            if (containerUsed >= 0 && containerLimit > 0) {
                return (containerUsed * 100 / containerLimit) + "%";
            }
            return "未知";
        }

        /**
         * 有效使用百分比（扣除 cache，用於 OOM 判斷）
         */
        public String getEffectivePercentage() {
            if (containerEffective >= 0 && containerLimit > 0) {
                return (containerEffective * 100 / containerLimit) + "%";
            }
            return "未知";
        }

        /**
         * 以有效使用量判斷是否危險（>85%），避免 page cache 誤報
         */
        public boolean isDangerous() {
            if (containerLimit <= 0) return false;
            long check = containerEffective >= 0 ? containerEffective : containerUsed;
            if (check >= 0) {
                return check * 100 / containerLimit > 85;
            }
            return false;
        }

        /**
         * 有效使用量的比例（0-100），回傳 -1 表示無法取得
         */
        public long getEffectiveRatio() {
            // 分母：優先用 -Xmx（heapMax），fallback 到 cgroup limit（與 /mem 顯示一致）
            long denominator = (heapMax > 0) ? heapMax : containerLimit;
            if (denominator <= 0) return -1;
            // containerEffective >= 0 表示已成功從 cgroup 讀取（0 也是合法值）
            if (containerEffective >= 0) {
                return containerEffective * 100 / denominator;
            }
            // fallback：使用原始值
            if (containerUsed >= 0) {
                return containerUsed * 100 / denominator;
            }
            return -1;
        }
    }
}
