package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages temporal frame history for advanced effects
 * Provides ping-pong buffer management for TAA, motion blur, and reflections
 * 
 * Maintains previous frame data automatically through frame swapping
 */
public class FrameHistoryManager {

    private static final FrameHistoryManager INSTANCE = new FrameHistoryManager();

    /**
     * Ping-pong framebuffer: stores current and previous frame
     */
    private static class PingPongBuffer {
        VulkanImage[] textures;  // [0] = current, [1] = previous
        int width, height;
        int format;

        PingPongBuffer(int width, int height, int format) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.textures = new VulkanImage[2];

            createTextures();
        }

        private void createTextures() {
            for (int i = 0; i < 2; i++) {
                this.textures[i] = VulkanImage.builder(width, height)
                        .setFormat(format)
                        .setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .setLinearFiltering(true)
                        .setClamp(true)
                        .createVulkanImage();
            }
        }

        VulkanImage getCurrent() {
            return this.textures[0];
        }

        VulkanImage getPrevious() {
            return this.textures[1];
        }

        void swap() {
            VulkanImage temp = this.textures[0];
            this.textures[0] = this.textures[1];
            this.textures[1] = temp;
        }

        void resize(int newWidth, int newHeight) {
            this.width = newWidth;
            this.height = newHeight;

            for (VulkanImage texture : this.textures) {
                if (texture != null) {
                    texture.free();
                }
            }

            createTextures();
        }

        void cleanUp() {
            for (VulkanImage texture : this.textures) {
                if (texture != null) {
                    texture.free();
                }
            }
        }
    }

    // Registry of ping-pong buffers by name
    private final Map<String, PingPongBuffer> pingPongBuffers = new HashMap<>();

    private FrameHistoryManager() {
    }

    public static FrameHistoryManager getInstance() {
        return INSTANCE;
    }

    /**
     * Create or get a ping-pong buffer for temporal effects
     * 
     * @param name identifier for the buffer (e.g., "ColorHistory", "DepthHistory")
     * @param width framebuffer width
     * @param height framebuffer height
     * @param format Vulkan image format
     * @return current frame texture
     */
    public VulkanImage createPingPongBuffer(String name, int width, int height, int format) {
        if (!this.pingPongBuffers.containsKey(name)) {
            this.pingPongBuffers.put(name, new PingPongBuffer(width, height, format));
        }
        return this.pingPongBuffers.get(name).getCurrent();
    }

    /**
     * Get current frame texture from ping-pong buffer
     */
    public VulkanImage getCurrentFrame(String name) {
        PingPongBuffer buffer = this.pingPongBuffers.get(name);
        return buffer != null ? buffer.getCurrent() : null;
    }

    /**
     * Get previous frame texture from ping-pong buffer (for history sampling)
     */
    public VulkanImage getPreviousFrame(String name) {
        PingPongBuffer buffer = this.pingPongBuffers.get(name);
        return buffer != null ? buffer.getPrevious() : null;
    }

    /**
     * Swap all ping-pong buffers (call at end of frame render)
     */
    public void swapFrames() {
        for (PingPongBuffer buffer : this.pingPongBuffers.values()) {
            buffer.swap();
        }
    }

    /**
     * Copy texture data between frames (alternative to swap)
     * Useful for selective history preservation
     */
    public void copyToHistory(String name, VkCommandBuffer commandBuffer, MemoryStack stack) {
        PingPongBuffer buffer = this.pingPongBuffers.get(name);
        if (buffer != null) {
            // Transition images for copy
            buffer.getCurrent().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            buffer.getPrevious().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

            // Copy current to previous
            copyImageRegion(commandBuffer, buffer.getCurrent(), buffer.getPrevious(), 
                    buffer.width, buffer.height);

            // Restore layouts
            buffer.getCurrent().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            buffer.getPrevious().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
    }

    private void copyImageRegion(VkCommandBuffer commandBuffer, VulkanImage src, VulkanImage dst, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var region = org.lwjgl.vulkan.VkImageCopy.calloc(1, stack);
            region.srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.extent().set(width, height, 1);

            vkCmdCopyImage(commandBuffer, src.getImageView(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    dst.getImageView(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
        }
    }

    /**
     * Resize all history buffers
     */
    public void resizeAll(int newWidth, int newHeight) {
        for (PingPongBuffer buffer : this.pingPongBuffers.values()) {
            buffer.resize(newWidth, newHeight);
        }
    }

    /**
     * Remove a specific history buffer
     */
    public void removePingPongBuffer(String name) {
        PingPongBuffer buffer = this.pingPongBuffers.remove(name);
        if (buffer != null) {
            buffer.cleanUp();
        }
    }

    /**
     * Check if history buffer exists
     */
    public boolean hasPingPongBuffer(String name) {
        return this.pingPongBuffers.containsKey(name);
    }

    /**
     * Clean up all history buffers
     */
    public void cleanUp() {
        for (PingPongBuffer buffer : this.pingPongBuffers.values()) {
            buffer.cleanUp();
        }
        this.pingPongBuffers.clear();
    }

    /**
     * Get all history buffer names
     */
    public List<String> getBufferNames() {
        return new ArrayList<>(this.pingPongBuffers.keySet());
    }
}
