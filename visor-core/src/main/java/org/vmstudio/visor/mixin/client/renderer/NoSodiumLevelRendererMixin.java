package org.vmstudio.visor.mixin.client.renderer;

import org.vmstudio.visor.core.client.VisorState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(LevelRenderer.class)
public class NoSodiumLevelRendererMixin {

    @Shadow
    @Final
    private SectionOcclusionGraph sectionOcclusionGraph;

    @Inject(method = "setupRender", at = @At("HEAD"))
    private void visor$alwaysUpdateCull(CallbackInfo ci) {
        if (VisorState.get().isActive()) {
            // fixes chunks cull frustum between displays
            this.sectionOcclusionGraph.invalidate();
            ((SectionOcclusionGraphAccessor) this.sectionOcclusionGraph)
                    .visor$getNeedsFrustumUpdate().set(true);
        }
    }

    @Mixin(SectionOcclusionGraph.class)
    public interface SectionOcclusionGraphAccessor {
        @Accessor("needsFrustumUpdate")
        AtomicBoolean visor$getNeedsFrustumUpdate();
    }
}
