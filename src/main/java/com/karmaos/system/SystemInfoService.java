package com.karmaos.system;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

@Service
public class SystemInfoService {

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;

    public SystemInfoService() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    public double getCpuLoad() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                return Math.round(sunOsBean.getProcessCpuLoad() * 100 * 10.0) / 10.0;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public double getSystemCpuLoad() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                return Math.round(sunOsBean.getCpuLoad() * 100 * 10.0) / 10.0;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public long getTotalMemory() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        return heap.getMax() / (1024 * 1024);
    }

    public long getUsedMemory() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        return heap.getUsed() / (1024 * 1024);
    }

    public int getAvailableProcessors() {
        return osBean.getAvailableProcessors();
    }

    public String getOsName() {
        return osBean.getName();
    }
}
