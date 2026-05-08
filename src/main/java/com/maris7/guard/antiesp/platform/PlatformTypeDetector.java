package com.maris7.guard.antiesp.platform;

public final class PlatformTypeDetector {

    private PlatformTypeDetector() {
    }

    public static PlatformType detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return PlatformType.FOLIA;
        } catch (ClassNotFoundException ignored) {
            return PlatformType.BUKKIT;
        }
    }
}

