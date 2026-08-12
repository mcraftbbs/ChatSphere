package cn.sarskin.ChatSphere.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

public class ItemSerialization {
    /** Cap on base64 item NBT from the network (~12 KiB). */
    private static final int MAX_BASE64 = 16 * 1024;

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            var conn = Minecraft.getInstance().getConnection();
            if (conn == null) return "";
            RegistryAccess registryAccess = conn.registryAccess();
            CompoundTag tag = (CompoundTag) stack.save(registryAccess, new CompoundTag());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                NbtIo.write(tag, dos);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty() || data.length() > MAX_BASE64) return ItemStack.EMPTY;
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try (DataInputStream dis = new DataInputStream(bais)) {
                CompoundTag tag = NbtIo.read(dis, NbtAccounter.create(1_000_000L));
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) return ItemStack.EMPTY;
                RegistryAccess registryAccess = conn.registryAccess();
                return ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
            }
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
