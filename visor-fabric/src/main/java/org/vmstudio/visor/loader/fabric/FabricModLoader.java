package org.vmstudio.visor.loader.fabric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.client.render.RenderPipelineCallback;
import org.vmstudio.visor.api.client.render.RenderPipelineStage;
import org.vmstudio.visor.api.common.VRException;
import org.vmstudio.visor.api.common.network.VisorChannel;
import org.vmstudio.visor.api.common.network.VisorPayloadToClient;
import org.vmstudio.visor.api.common.network.VisorPayloadToServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public class FabricModLoader implements ModLoader {
    private final File configFolder = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().toFile();

    private final Map<RenderPipelineStage, List<RenderPipelineCallback>> pipelineCallbacks
            = new EnumMap<>(RenderPipelineStage.class);
    private final Map<ResourceLocation, VisorChannel> networkChannels = new ConcurrentHashMap<>();
    private boolean serverReceiverRegistered;
    private boolean clientReceiverRegistered;

    private boolean worldEventsRegistered = false;

    @Override
    public File getConfigFolder() {
        return configFolder;
    }


    @Override
    public boolean isModLoaded(@NotNull String id) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public @NotNull String getModVersion(@NotNull String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("no version");
    }

    @Override
    public boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType().equals(EnvType.SERVER);
    }


    @Override
    public void addToRenderPipeline(@NotNull RenderPipelineStage stage,
                                    @NotNull RenderPipelineCallback callback) {
        pipelineCallbacks
                .computeIfAbsent(stage, k -> new CopyOnWriteArrayList<>())
                .add(callback);

        if (!worldEventsRegistered) {
            // BEFORE_ENTITIES is the closest equivalent to AFTER_SOLID, but
            // Fabric has not created its world PoseStack yet. Use an identity
            // stack so Visor can apply its own VR camera transform once.
            WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
                fireCallbacks(RenderPipelineStage.AFTER_SOLID, new PoseStack(), context.tickCounter().getGameTimeDeltaPartialTick(false));
            });

            // AFTER_TRANSLUCENT
            WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
                fireCallbacks(RenderPipelineStage.AFTER_TRANSLUCENT, context.matrixStack(), context.tickCounter().getGameTimeDeltaPartialTick(false));
            });

            // AFTER_WORLD
            WorldRenderEvents.END.register(context -> {
                fireCallbacks(RenderPipelineStage.AFTER_WORLD, context.matrixStack(), context.tickCounter().getGameTimeDeltaPartialTick(false));
            });

            worldEventsRegistered = true;
        }
    }

    private void fireCallbacks(RenderPipelineStage stage, PoseStack poseStack, float partialTicks) {
        List<RenderPipelineCallback> callbacks = pipelineCallbacks.get(stage);
        if (callbacks == null || callbacks.isEmpty()) return;
        for (RenderPipelineCallback cb : callbacks) {
            cb.render(poseStack, partialTicks);
        }
    }


    @Override
    public boolean enableRenderTargetStencil(@NotNull RenderTarget renderTarget) {
        return false;
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


    public @NotNull List<Class<?>> getClassesAnnotated(
            @NotNull Class<? extends Annotation> annotation,
            @NotNull String modId,
            @NotNull String packagePath
    ) {
        try {
            List<Class<?>> result = new ArrayList<>();

            ModContainer container = FabricLoader.getInstance()
                    .getModContainer(modId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mod: " + modId));

            String pkgPath = packagePath.replace('.', '/');

            // Prefer the requested mod container.
            scanPackageInContainer(container, annotation, packagePath, pkgPath, result);

            // Architectury's development launch (TransformerRuntime) splits common
            // code into generated mod containers, so the requested mod container's
            // root paths do not contain visor-core's classes. Fall back to scanning
            // every loaded container when nothing was found.
            if (result.isEmpty()
                    && FabricLoader.getInstance().isDevelopmentEnvironment()) {
                for (ModContainer candidate : FabricLoader.getInstance().getAllMods()) {
                    if (candidate.getMetadata().getId().equals(modId)) {
                        continue;
                    }
                    scanPackageInContainer(candidate, annotation, packagePath, pkgPath, result);
                }
            }
            return result;
        } catch (Exception e) {
            throw new VRException(e);
        }
    }

    private void scanPackageInContainer(
            @NotNull ModContainer container,
            @NotNull Class<? extends Annotation> annotation,
            @NotNull String packagePath,
            @NotNull String pkgPath,
            @NotNull List<Class<?>> result
    ) {
        for (Path root : container.getRootPaths()) {
            Path pkgRoot = root.resolve(pkgPath);
            if (!Files.exists(pkgRoot)) continue;

            try (Stream<Path> stream = Files.walk(pkgRoot)) {
                stream
                        .filter(p -> p.getFileName().toString().endsWith(".class"))
                        .forEach(classFile -> {
                            try (InputStream in = Files.newInputStream(classFile)) {
                                ClassReader reader = new ClassReader(in);
                                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        String found = Type.getType(desc).getClassName();
                                        if (found.equals(annotation.getName())) {
                                            Path rel = pkgRoot.relativize(classFile);
                                            String className = packagePath + "."
                                                    + rel.toString()
                                                    .replace('/', '.')
                                                    .replace('\\', '.')
                                                    .replaceAll("\\.class$", "");
                                            try {
                                                result.add(
                                                        Class.forName(
                                                                className,
                                                                false,
                                                                Thread.currentThread().getContextClassLoader()
                                                        )
                                                );
                                            } catch (ClassNotFoundException e) {
                                                throw new VRException(e);
                                            }
                                        }
                                        return super.visitAnnotation(desc, visible);
                                    }
                                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


    @Override
    public void registerNetworkChannel(@NotNull VisorChannel channel) {
        networkChannels.put(channel.getChannelId(), channel);

        if (channel.hasPacketsToServer() && !serverReceiverRegistered) {
            ServerPlayNetworking.registerGlobalReceiver(VisorPayload.TYPE, (payload, context) -> {
                VisorChannel registeredChannel = networkChannels.get(payload.channelId());
                if (registeredChannel == null || !registeredChannel.hasPacketsToServer()) {
                    return;
                }

                context.server().execute(() -> {
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                    try {
                        registeredChannel.handleToServer(buffer, context.player(), response ->
                                ServerPlayNetworking.send(context.player(),
                                        createPayload(registeredChannel.getChannelId(), response)));
                    } finally {
                        buffer.release();
                    }
                });
            });
            serverReceiverRegistered = true;
        }

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                && channel.hasPacketsToClient()
                && !clientReceiverRegistered) {
            ClientPlayNetworking.registerGlobalReceiver(VisorPayload.TYPE, (payload, context) -> {
                VisorChannel registeredChannel = networkChannels.get(payload.channelId());
                if (registeredChannel == null || !registeredChannel.hasPacketsToClient()) {
                    return;
                }

                context.client().execute(() -> {
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
                    try {
                        registeredChannel.handleToClient(buffer);
                    } finally {
                        buffer.release();
                    }
                });
            });
            clientReceiverRegistered = true;
        }
    }

    private static VisorPayload createPayload(
            ResourceLocation channelId,
            org.vmstudio.visor.api.common.network.VisorPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new VisorPayload(channelId, data);
        } finally {
            buffer.release();
        }
    }

    @Override
    public @NotNull Packet<?> createPacketToClient(@NotNull ResourceLocation channelId,
                                                   @NotNull VisorPayloadToClient payload) {
        return ServerPlayNetworking.createS2CPacket(createPayload(channelId, payload));
    }

    @Override
    public @NotNull Packet<?> createPacketToServer(@NotNull ResourceLocation channelId,
                                                   @NotNull VisorPayloadToServer payload) {
        return ClientPlayNetworking.createC2SPacket(createPayload(channelId, payload));
    }


    @Override
    public boolean renderWaterOverlay(Player player, PoseStack mat) {
        return false;
    }

    @Override
    public boolean renderFireOverlay(Player player, PoseStack mat) {
        return false;
    }

    @Override
    public @NotNull LoaderType getType() {
        return LoaderType.FABRIC;
    }

    public record VisorPayload(ResourceLocation channelId, byte[] data) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<VisorPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("visor", "tunnel"));

        public static final StreamCodec<ByteBuf, VisorPayload> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, VisorPayload::channelId,
                ByteBufCodecs.BYTE_ARRAY, VisorPayload::data,
                VisorPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}