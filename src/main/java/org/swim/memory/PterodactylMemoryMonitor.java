package org.swim.memory;

import java.lang.management.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class PterodactylMemoryMonitor {

    /**
     * 取得容器的記憶體使用量（Pterodactyl 面板上看到的那個數字）
     */
    public static long getContainerUsedBytes() {
        // cgroup v2（較新）
        try {
            Path path = Path.of("/sys/fs/cgroup/memory.current");
            if (Files.exists(path)) {
                return Long.parseLong(Files.readString(path).trim());
            }
        } catch (Exception ignored) {}

        // cgroup v1（較舊）
        try {
            Path path = Path.of("/sys/fs/cgroup/memory/memory.usage_in_bytes");
            if (Files.exists(path)) {
                return Long.parseLong(Files.readString(path).trim());
            }
        } catch (Exception ignored) {}

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
        } catch (Exception ignored) {}

        // cgroup v1
        try {
            Path path = Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes");
            if (Files.exists(path)) {
                long val = Long.parseLong(Files.readString(path).trim());
                if (val < Long.MAX_VALUE / 2) return val;
            }
        } catch (Exception ignored) {}

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

        // JVM 層級
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        info.heapUsed    = memBean.getHeapMemoryUsage().getUsed();
        info.heapMax     = memBean.getHeapMemoryUsage().getMax();
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
        public long containerUsed  = -1;  // cgroup 使用量
        public long containerLimit = -1;  // cgroup 上限
        public long heapUsed;
        public long heapMax;
        public long nonHeapUsed;
        public long directUsed;
        public int  threadCount;

        public String getUsagePercentage() {
            if (containerUsed > 0 && containerLimit > 0) {
                return (containerUsed * 100 / containerLimit) + "%";
            }
            return "未知";
        }

        public boolean isDangerous() {
            if (containerUsed > 0 && containerLimit > 0) {
                return containerUsed * 100 / containerLimit > 85;
            }
            return false;
        }
    }
}
