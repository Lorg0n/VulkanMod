package net.vulkanmod.render.chunk;

import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

public class VoxelVolume {
    // Extends volume range to 512x512x512 (32x32 chunks without looping)
    public static final int WIDTH = 512;
    public static final int HEIGHT = 512;
    public static final int DEPTH = 512;

    private static VulkanImage volumeImage;

    public static void init() {
        if (volumeImage != null) {
            volumeImage.free();
        }
        volumeImage = VulkanImage.builder(WIDTH, HEIGHT)
                .setDepth(DEPTH)
                .setFormat(VK_FORMAT_R8_UNORM)
                .setUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .setClamp(false)
                .setLinearFiltering(true)
                .createVulkanImage();

        VTextureSelector.bindTexture(4, volumeImage);
    }

    public static void updateRegion(int chunkX, int chunkY, int chunkZ, byte[] data) {
        if (volumeImage == null) return;

        int xOffset = (chunkX * 16) & (WIDTH - 1);
        int yOffset = (chunkY * 16) & (HEIGHT - 1);
        int zOffset = (chunkZ * 16) & (DEPTH - 1);

        if (xOffset < 0) xOffset += WIDTH;
        if (yOffset < 0) yOffset += HEIGHT;
        if (zOffset < 0) zOffset += DEPTH;

        ByteBuffer buffer = MemoryUtil.memAlloc(16 * 16 * 16);
        buffer.put(data);
        buffer.flip();

        volumeImage.uploadSubTexture3DAsync(0, 0, 16, 16, 16, xOffset, yOffset, zOffset, 0, 0, 16, MemoryUtil.memAddress(buffer));
        MemoryUtil.memFree(buffer);
    }

    public static VulkanImage getVolumeImage() {
        return volumeImage;
    }
}