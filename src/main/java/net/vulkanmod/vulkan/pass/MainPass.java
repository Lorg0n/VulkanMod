package net.vulkanmod.vulkan.pass;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public interface MainPass {

    void begin(VkCommandBuffer commandBuffer, MemoryStack stack);

    void end(VkCommandBuffer commandBuffer);

    void cleanUp();

    void onResize();

    default void mainTargetBindWrite() {}

    default void mainTargetUnbindWrite() {}

    default void rebindMainTarget() {}

    default void bindAsTexture() {}

    /**
     * Get color attachment by index (for MRT support)
     */
    default GpuTexture getColorAttachment(int index) {
        if (index == 0) {
            return getColorAttachment();
        }
        return null;
    }

    default GpuTexture getColorAttachment() {
        return null;
    }

    /**
     * Get color attachment view by index (for MRT support)
     */
    default GpuTextureView getColorAttachmentView(int index) {
        if (index == 0) {
            return getColorAttachmentView();
        }
        return null;
    }

    default GpuTextureView getColorAttachmentView() {
        return null;
    }

    default GpuTexture getDepthAttachment() {
        return null;
    }

}
