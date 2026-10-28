package cn.dancingsnow.neoecoae.compat.gto;

import net.minecraftforge.fml.ModList;

/** GTO keeps ECO's integrated workstations; its recipes come from the gt_gto profile pack. */
public final class ECOGtoWorkstations {
    private ECOGtoWorkstations() {}

    public static boolean active() {
        return ModList.get() != null && ModList.get().isLoaded("gtocore");
    }
}
