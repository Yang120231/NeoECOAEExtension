package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.gtolib.api.GTOApi;
import dev.emi.emi.registry.EmiPluginContainer;

public final class GTOCoreEmiCompat {
    private static boolean registered;

    private GTOCoreEmiCompat() {}

    public static void registerPluginEvent() {
        if (registered) {
            return;
        }
        registered = true;
        GTOApi.EMI_PLUGIN_EVENT.addListener(
                GTOCoreEmiCompat.class,
                plugins -> plugins.accept(new EmiPluginContainer(new NeoECOAEEmiPlugin(), NeoECOAE.MOD_ID)));
    }
}
