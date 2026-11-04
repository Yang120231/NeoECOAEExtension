package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.compat.ae2.ECORemoteProviderLocator;
import cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingMonitor;

/** Registers the GTO edition's native processing and remote-menu services once. */
public final class ECOGtoIntegration {
    private static boolean initialized;
    private ECOGtoIntegration() {}
    public static synchronized void initialize() {
        if (initialized) return;
        ECOProcessingMonitor.initialize();
        ECORemoteProviderLocator.initialize();
        initialized = true;
    }
}
