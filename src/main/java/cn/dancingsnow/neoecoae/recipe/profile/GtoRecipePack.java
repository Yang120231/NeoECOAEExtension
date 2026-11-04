package cn.dancingsnow.neoecoae.recipe.profile;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

/** The GTO edition always loads its single progression pack. */
public final class GtoRecipePack {
    private GtoRecipePack() {}

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        var modFile = ModList.get().getModFileById(NeoECOAE.MOD_ID).getFile();
        event.addRepositorySource(consumer -> {
            var root = modFile.findResource("recipe_profiles", "gt_gto");
            var pack = Pack.readMetaAndCreate("neoecoae:recipe_profile/gt_gto",
                Component.literal("NeoECOAE GTO recipes"), true,
                id -> new PathPackResources(id, true, root), PackType.SERVER_DATA,
                Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack == null) throw new IllegalStateException("Missing NeoECOAE GTO recipe pack");
            consumer.accept(pack);
        });
    }
}
