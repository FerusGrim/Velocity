package com.velocitypowered.proxy.protocol.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.proxy.connection.VelocityConstants;
import com.velocitypowered.proxy.protocol.ProtocolConstants;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class PluginMessageUtil {
    public static final String BRAND_CHANNEL_LEGACY = "MC|Brand";
    public static final String BRAND_CHANNEL = "minecraft:brand";
    public static final String REGISTER_CHANNEL_LEGACY = "REGISTER";
    public static final String REGISTER_CHANNEL = "minecraft:register";
    public static final String UNREGISTER_CHANNEL_LEGACY = "UNREGISTER";
    public static final String UNREGISTER_CHANNEL = "minecraft:unregister";

    private PluginMessageUtil() {
        throw new AssertionError();
    }

    public static boolean isMCBrand(PluginMessage message) {
        Preconditions.checkNotNull(message, "message");
        return message.getChannel().equals(BRAND_CHANNEL_LEGACY) || message.getChannel().equals(BRAND_CHANNEL);
    }

    public static boolean isMCRegister(PluginMessage message) {
        Preconditions.checkNotNull(message, "message");
        return message.getChannel().equals(REGISTER_CHANNEL_LEGACY) || message.getChannel().equals(REGISTER_CHANNEL);
    }

    public static boolean isMCUnregister(PluginMessage message) {
        Preconditions.checkNotNull(message, "message");
        return message.getChannel().equals(UNREGISTER_CHANNEL_LEGACY) || message.getChannel().equals(UNREGISTER_CHANNEL);
    }

    public static List<String> getChannels(PluginMessage message) {
        Preconditions.checkNotNull(message, "message");
        Preconditions.checkArgument(isMCRegister(message) || isMCUnregister(message),"Unknown channel type %s",
                message.getChannel());
        String channels = new String(message.getData(), StandardCharsets.UTF_8);
        return ImmutableList.copyOf(channels.split("\0"));
    }

    public static PluginMessage constructChannelsPacket(int protocolVersion, Collection<String> channels) {
        Preconditions.checkNotNull(channels, "channels");
        String channelName = protocolVersion >= ProtocolConstants.MINECRAFT_1_13 ? REGISTER_CHANNEL : REGISTER_CHANNEL_LEGACY;
        PluginMessage message = new PluginMessage();
        message.setChannel(channelName);
        message.setData(String.join("\0", channels).getBytes(StandardCharsets.UTF_8));
        return message;
    }

    public static PluginMessage rewriteMCBrand(PluginMessage message) {
        Preconditions.checkNotNull(message, "message");
        Preconditions.checkArgument(isMCBrand(message), "message is not a MC Brand plugin message");

        byte[] rewrittenData;
        ByteBuf rewrittenBuf = Unpooled.buffer();
        try {
            String currentBrand = ProtocolUtils.readString(Unpooled.wrappedBuffer(message.getData()));
            ProtocolUtils.writeString(rewrittenBuf, currentBrand + " (Velocity)");
            rewrittenData = new byte[rewrittenBuf.readableBytes()];
            rewrittenBuf.readBytes(rewrittenData);
        } finally {
            rewrittenBuf.release();
        }

        PluginMessage newMsg = new PluginMessage();
        newMsg.setChannel(message.getChannel());
        newMsg.setData(rewrittenData);
        return newMsg;
    }
    
    public static Optional<List<ModInfo.Mod>> readModList(PluginMessage message) {
        Preconditions.checkNotNull(message, "message");
        Preconditions.checkArgument(message.getChannel().equals(VelocityConstants.FORGE_LEGACY_HANDSHAKE_CHANNEL),
                "message is not a FML HS plugin message");
        
        ByteBuf byteBuf = Unpooled.wrappedBuffer(message.getData());
        try {
            byte discriminator = byteBuf.readByte();
            
            if (discriminator == 2) {
                ImmutableList.Builder<ModInfo.Mod> mods = ImmutableList.builder();
                int modCount = ProtocolUtils.readVarInt(byteBuf);
                
                for (int index = 0; index < modCount; index++) {
                    String id = ProtocolUtils.readString(byteBuf);
                    String version = ProtocolUtils.readString(byteBuf);
                    mods.add(new ModInfo.Mod(id, version));
                }
                
                return Optional.of(mods.build());
            }
            
            return Optional.empty();
        } finally {
            byteBuf.release();
        }
    }
}
