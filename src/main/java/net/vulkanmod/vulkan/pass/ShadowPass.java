package net.vulkanmod.vulkan.pass;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;

import static org.lwjgl.vulkan.VK10.*;

public class ShadowPass {
    public static final int SHADOW_MAP_SIZE = 4096;
    private Framebuffer framebuffer;
    private RenderPass renderPass;
    private VulkanImage shadowMapImage;

    public ShadowPass() {
        create();
    }

    private void create() {
        shadowMapImage = VulkanImage.createDepthImage(
                Vulkan.getDefaultDepthFormat(),
                SHADOW_MAP_SIZE, SHADOW_MAP_SIZE,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                false, true
        );

        framebuffer = Framebuffer.builder(null, shadowMapImage).build();

        RenderPass.Builder builder = RenderPass.builder(framebuffer);
        builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getDepthAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        renderPass = builder.build();
    }

    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        shadowMapImage.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        Renderer.getInstance().beginRenderPass(renderPass, framebuffer);
        Renderer.setViewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, stack);
        VkRect2D.Buffer pScissor = framebuffer.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    public void end(VkCommandBuffer commandBuffer) {
        Renderer.getInstance().endRenderPass(commandBuffer);
    }

    public VulkanImage getShadowMap() {
        return shadowMapImage;
    }

    public void cleanUp() {
        renderPass.cleanUp();
        framebuffer.cleanUp();
    }
}