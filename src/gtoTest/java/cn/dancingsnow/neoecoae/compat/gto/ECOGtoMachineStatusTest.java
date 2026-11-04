package cn.dancingsnow.neoecoae.compat.gto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ECOGtoMachineStatusTest {
    @Test void nativeOutputListsExposeCycleProgressParallelAndRecipeId() {
        var output = mock(AEKey.class);
        var logic = new Logic(output);
        var result = ECOGtoMachineStatus.sampleMachine(logic, Component.literal("GTO machine"), "0,0,0", output);
        assertEquals(State.RUNNING, result.state());
        assertEquals(25, result.progressTicks());
        assertEquals(100, result.durationTicks());
        assertEquals(4096, result.parallel());
        assertEquals("gtocore:test_recipe", result.recipe());
        assertTrue(result.reason().getString().isEmpty());
        assertEquals(State.QUEUED, ECOGtoMachineStatus.sampleMachine(logic,
            Component.literal("GTO machine"), "0,0,0", mock(AEKey.class)).state());
    }

    @Test void nativeWaitingAndSuspendUseTheActualIdleReason() {
        var output = mock(AEKey.class);
        var logic = new Logic(output);
        logic.working = false; logic.waiting = true;
        logic.reason = Component.literal("Native voltage requirement");
        var result = ECOGtoMachineStatus.sampleMachine(logic, Component.literal("GTO machine"), "0,0,0", output);
        assertEquals(State.WAITING, result.state());
        assertEquals("Native voltage requirement", result.reason().getString());
        logic.paused = true;
        assertEquals(State.PAUSED, ECOGtoMachineStatus.sampleMachine(logic,
            Component.literal("GTO machine"), "0,0,0", output).state());
    }

    public static final class Outputs {
        final AEKey output;
        Outputs(AEKey output) { this.output = output; }
        public int size() { return output == null ? 0 : 1; }
        public AEKey outputKey(int index) { return output; }
    }
    public static final class Definition {
        public final ResourceLocation id = new ResourceLocation("gtocore", "test_recipe");
    }
    public static final class Recipe {
        public final Outputs itemOutputs;
        public final Outputs fluidOutputs = new Outputs(null);
        public final Definition definition = new Definition();
        public final long parallels = 4096;
        Recipe(AEKey output) { itemOutputs = new Outputs(output); }
    }
    public static final class Logic {
        final Recipe recipe;
        boolean working = true, waiting, paused;
        Component reason = Component.empty();
        Logic(AEKey output) { recipe = new Recipe(output); }
        public Recipe getLastRecipe() { return recipe; }
        public int getMaxProgress() { return 100; }
        public int getProgress() { return 25; }
        public boolean isWorking() { return working; }
        public boolean isWaiting() { return waiting; }
        public boolean isSuspend() { return paused; }
        public Component getIdleReason() { return reason; }
    }
}
