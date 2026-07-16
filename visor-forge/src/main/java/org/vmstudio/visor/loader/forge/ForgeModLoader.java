package org.vmstudio.visor.loader.forge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.common.VRException;
import org.vmstudio.visor.api.client.render.RenderPipelineCallback;
import org.vmstudio.visor.api.client.render.RenderPipelineStage;
import org.vmstudio.visor.api.common.network.VisorChannel;
import org.vmstudio.visor.api.common.network.VisorPayload;
import org.vmstudio.visor.api.common.network.VisorPayloadToClient;
import org.vmstudio.visor.api.common.network.VisorPayloadToServer;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ForgeModLoader implements ModLoader {
    private final File configFolder = FMLPaths.CONFIGDIR.get().toFile();

    private final Map<RenderPipelineStage, List<RenderPipelineCallback>> pipelineCallbacks =
            new EnumMap<>(RenderPipelineStage.class);
    private final Map<ResourceLocation, VisorChannel> networkChannels = new LinkedHashMap<>();
    private final Map<ResourceLocation, CustomPacketPayload.Type<NeoForgePayload>> payloadTypes =
            new LinkedHashMap<>();

    private boolean levelStageListenerRegistered;
    private boolean payloadRegistrationFinished;

    @Override
    public File getConfigFolder() {
        return configFolder;
    }

    @Override
    public boolean isModLoaded(@NotNull String id) {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.isLoaded(id);
        }

        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        return loadingModList != null && loadingModList.getModFileById(id) != null;
    }

    @Override
    public @NotNull String getModVersion(@NotNull String id) {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModContainerById(id)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("no version");
        }

        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        ModFileInfo modFile = loadingModList == null ? null : loadingModList.getModFileById(id);
        if (modFile == null) {
            return "no version";
        }
        return modFile.getMods().stream()
                .filter(modInfo -> id.equals(modInfo.getModId()))
                .map(modInfo -> modInfo.getVersion().toString())
                .findFirst()
                .orElse("no version");
    }

    @Override
    public boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    @Override
    public void addToRenderPipeline(@NotNull RenderPipelineStage stage,
                                    @NotNull RenderPipelineCallback callback) {
        pipelineCallbacks
                .computeIfAbsent(stage, ignored -> new CopyOnWriteArrayList<>())
                .add(callback);

        if (!levelStageListenerRegistered) {
            NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
            levelStageListenerRegistered = true;
        }
    }

    @Override
    public boolean enableRenderTargetStencil(@NotNull RenderTarget renderTarget) {
        renderTarget.enableStencil();
        return true;
    }

    @Override
    public double getItemEntityReach(double baseRange, ItemStack itemStack, EquipmentSlot slot) {
        List<AttributeModifier> modifiers = new ArrayList<>();
        itemStack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ENTITY_INTERACTION_RANGE)) {
                modifiers.add(modifier);
            }
        });

        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                baseRange += modifier.amount();
            }
        }

        double totalRange = baseRange;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                totalRange += baseRange * modifier.amount();
            }
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                totalRange *= 1.0D + modifier.amount();
            }
        }
        return totalRange;
    }

    @Override
    public @NotNull List<Class<?>> getClassesAnnotated(@NotNull Class<? extends Annotation> annotation,
                                                       @NotNull String modId,
                                                       @NotNull String packagePath) {
        List<Class<?>> result = new ArrayList<>();
        List<ModFileScanData> scanData = new ArrayList<>();
        ModList modList = ModList.get();
        IModFileInfo info = modList.getModFileById(modId);
        if (info instanceof ModFileInfo modFileInfo) {
            scanData.add(modFileInfo.getFile().getScanResult());
        }

        // Architectury's development launch splits common code into generated
        // mod files, so the loader module's scan data does not contain it.
        if (!FMLLoader.isProduction()) {
            for (ModFileScanData candidate : modList.getAllScanData()) {
                if (!scanData.contains(candidate)) {
                    scanData.add(candidate);
                }
            }
        }

        String annotationName = annotation.getName();
        Set<String> classNames = new LinkedHashSet<>();

        for (ModFileScanData data : scanData) {
            for (var annotationData : data.getAnnotations()) {
                String className = annotationData.clazz().getClassName();
                if (className.startsWith(packagePath)
                        && annotationData.annotationType().getClassName().equals(annotationName)) {
                    classNames.add(className);
                }
            }
        }

        for (String className : classNames) {
            try {
                result.add(Class.forName(className, false,
                        Thread.currentThread().getContextClassLoader()));
            } catch (ClassNotFoundException exception) {
                throw new VRException(exception);
            }
        }
        return result;
    }

    @Override
    public void registerNetworkChannel(@NotNull VisorChannel channel) {
        if (payloadRegistrationFinished) {
            throw new IllegalStateException(
                    "NeoForge payload channels must be registered during addon registration: "
                            + channel.getChannelId());
        }

        ResourceLocation id = channel.getChannelId();
        networkChannels.put(id, channel);
        payloadTypes.put(id, new CustomPacketPayload.Type<>(id));
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        ForgeModLoader loader = (ForgeModLoader) ModLoader.get();
        loader.registerPayloadHandlers(event);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        for (VisorChannel channel : networkChannels.values()) {
            CustomPacketPayload.Type<NeoForgePayload> type = payloadTypes.get(channel.getChannelId());
            StreamCodec<RegistryFriendlyByteBuf, NeoForgePayload> codec = payloadCodec(type);

            event.registrar(Integer.toString(channel.getNetworkVersion()))
                    .optional()
                    .playBidirectional(type, codec,
                            (payload, context) -> handlePayload(channel, payload, context));
        }
        payloadRegistrationFinished = true;
    }

    private static void handlePayload(VisorChannel channel,
                                      NeoForgePayload payload,
                                      net.neoforged.neoforge.network.handling.IPayloadContext context) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        try {
            if (context.flow() == PacketFlow.SERVERBOUND) {
                if (channel.hasPacketsToServer() && context.player() instanceof ServerPlayer sender) {
                    channel.handleToServer(buffer, sender,
                            response -> context.reply(createPayload(channel.getChannelId(), response)));
                }
            } else if (channel.hasPacketsToClient()) {
                channel.handleToClient(buffer);
            }
        } finally {
            buffer.release();
        }
    }

    private static StreamCodec<RegistryFriendlyByteBuf, NeoForgePayload> payloadCodec(
            CustomPacketPayload.Type<NeoForgePayload> type) {
        return new StreamCodec<>() {
            @Override
            public @NotNull NeoForgePayload decode(RegistryFriendlyByteBuf buffer) {
                return new NeoForgePayload(type, buffer.readByteArray());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, NeoForgePayload payload) {
                buffer.writeByteArray(payload.data());
            }
        };
    }

    private static NeoForgePayload createPayload(ResourceLocation channelId, VisorPayload payload) {
        ForgeModLoader loader = (ForgeModLoader) ModLoader.get();
        CustomPacketPayload.Type<NeoForgePayload> type = loader.payloadTypes.get(channelId);
        if (type == null) {
            throw new IllegalStateException("Unregistered Visor payload channel: " + channelId);
        }

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new NeoForgePayload(type, data);
        } finally {
            buffer.release();
        }
    }

    @Override
    public @NotNull Packet<?> createPacketToClient(@NotNull ResourceLocation channelId,
                                                   @NotNull VisorPayloadToClient payload) {
        return new ClientboundCustomPayloadPacket(createPayload(channelId, payload));
    }

    @Override
    public @NotNull Packet<?> createPacketToServer(@NotNull ResourceLocation channelId,
                                                   @NotNull VisorPayloadToServer payload) {
        return new ServerboundCustomPayloadPacket(createPayload(channelId, payload));
    }

    @Override
    public boolean renderWaterOverlay(Player player, PoseStack poseStack) {
        return ClientHooks.renderWaterOverlay(player, poseStack);
    }

    @Override
    public boolean renderFireOverlay(Player player, PoseStack poseStack) {
        return ClientHooks.renderFireOverlay(player, poseStack);
    }

    @Override
    public @NotNull LoaderType getType() {
        return LoaderType.NEOFORGE;
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderPipelineStage stage = mapNeoForgeStage(event.getStage());
        if (stage == null) {
            return;
        }

        Collection<RenderPipelineCallback> callbacks = pipelineCallbacks.get(stage);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (RenderPipelineCallback callback : callbacks) {
            callback.render(poseStack, partialTicks);
        }
    }

    private static RenderPipelineStage mapNeoForgeStage(RenderLevelStageEvent.Stage stage) {
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return RenderPipelineStage.AFTER_SOLID;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return RenderPipelineStage.AFTER_TRANSLUCENT;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return RenderPipelineStage.AFTER_WORLD;
        }
        return null;
    }

    private record NeoForgePayload(CustomPacketPayload.Type<NeoForgePayload> type,
                                   byte[] data) implements CustomPacketPayload {
    }
}
