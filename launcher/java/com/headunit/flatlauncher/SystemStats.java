package com.headunit.flatlauncher;

import android.app.ActivityManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * RAM via ActivityManager (system-computed, matches Settings); CPU prefers
 * /proc/stat deltas, falling back to /proc/loadavg normalized by cores.
 * Some firmwares deny apps both /proc sources; cpuPercent stays -1 then.
 */
final class SystemStats {

    static class Snapshot {
        long ramUsedBytes;
        long ramTotalBytes;
        int cpuPercent = -1;
    }

    private boolean haveLastCpu;
    private long lastCpuTotal;
    private long lastCpuIdle;

    Snapshot sample(ActivityManager am, long nowMs) {
        Snapshot s = new Snapshot();
        readMem(am, s);
        s.cpuPercent = readCpu(nowMs);
        return s;
    }

    private void readMem(ActivityManager am, Snapshot s) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        if (mi.totalMem > 0) {
            s.ramTotalBytes = mi.totalMem;
            s.ramUsedBytes = Math.max(0L, mi.totalMem - mi.availMem);
        }
    }

    private int readCpu(long nowMs) {
        long[] ti = readProcStat();
        if (ti != null) {
            if (haveLastCpu) {
                long dTotal = ti[0] - lastCpuTotal;
                long dIdle = ti[1] - lastCpuIdle;
                if (dTotal > 0) {
                    int pct = (int) ((dTotal - dIdle) * 100L / dTotal);
                    lastCpuTotal = ti[0];
                    lastCpuIdle = ti[1];
                    return Math.max(0, Math.min(100, pct));
                }
            }
            lastCpuTotal = ti[0];
            lastCpuIdle = ti[1];
            haveLastCpu = true;
            return -1;
        }
        Float load1 = readLoadavg();
        if (load1 != null) {
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            return Math.max(0, Math.min(100, (int) (load1 * 100f / cores)));
        }
        return -1;
    }

    /** Returns {totalJiffies, idleJiffies} across all cores, or null if unreadable. */
    private long[] readProcStat() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/stat"), 8192);
            String line = r.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            // cpu user nice system idle iowait irq softirq steal ...
            long total = 0;
            long idle = 0;
            for (int i = 1; i < parts.length; i++) {
                long v = Long.parseLong(parts[i]);
                total += v;
                if (i == 4 || i == 5) {
                    idle += v;
                }
            }
            return new long[]{total, idle};
        } catch (IOException | NumberFormatException e) {
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Float readLoadavg() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/loadavg"), 256);
            String line = r.readLine();
            if (line == null) {
                return null;
            }
            String first = line.trim().split("\\s+")[0];
            return Float.parseFloat(first);
        } catch (IOException | NumberFormatException e) {
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
