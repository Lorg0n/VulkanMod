package net.vulkanmod.vulkan.framebuffer;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Enhanced RenderPass supporting Multiple Render Targets (MRT)
 */
public class RenderPass {
    Framebuffer framebuffer;
    long id;

    final int attachmentCount;
    AttachmentInfo[] colorAttachmentInfos;
    AttachmentInfo depthAttachmentInfo;

    public RenderPass(Framebuffer framebuffer, AttachmentInfo[] colorAttachmentInfos, AttachmentInfo depthAttachmentInfo) {
        this.framebuffer = framebuffer;
        this.colorAttachmentInfos = colorAttachmentInfos;
        this.depthAttachmentInfo = depthAttachmentInfo;

        int count = 0;
        if (colorAttachmentInfos != null)
            count += colorAttachmentInfos.length;
        if (depthAttachmentInfo != null)
            count++;

        this.attachmentCount = count;

        if (!Vulkan.DYNAMIC_RENDERING) {
            createRenderPass();
        }
    }

    private void createRenderPass() {

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
            VkAttachmentReference.Buffer attachmentRefs = VkAttachmentReference.calloc(attachmentCount, stack);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);

            int attachmentIdx = 0;

            // Color attachments
            int colorAttachmentCount = 0;
            if (colorAttachmentInfos != null && colorAttachmentInfos.length > 0) {
                colorAttachmentCount = colorAttachmentInfos.length;
                
                for (int i = 0; i < colorAttachmentCount; i++) {
                    AttachmentInfo colorInfo = colorAttachmentInfos[i];
                    VkAttachmentDescription colorAttachment = attachments.get(attachmentIdx);
                    colorAttachment.format(colorInfo.format)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .loadOp(colorInfo.loadOp)
                            .storeOp(colorInfo.storeOp)
                            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                            .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                            .finalLayout(colorInfo.finalLayout);

                    VkAttachmentReference colorAttachmentRef = attachmentRefs.get(attachmentIdx)
                            .attachment(attachmentIdx)
                            .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                    attachmentIdx++;
                }
            }

            // Depth-Stencil attachment
            if (depthAttachmentInfo != null) {
                VkAttachmentDescription depthAttachment = attachments.get(attachmentIdx);
                depthAttachment.format(depthAttachmentInfo.format)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(depthAttachmentInfo.loadOp)
                        .storeOp(depthAttachmentInfo.storeOp)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                        .finalLayout(depthAttachmentInfo.finalLayout);

                VkAttachmentReference depthAttachmentRef = attachmentRefs.get(attachmentIdx)
                        .attachment(attachmentIdx)
                        .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

                subpass.pDepthStencilAttachment(depthAttachmentRef);
            }

            // Set up multiple color attachments
            if (colorAttachmentCount > 0) {
                VkAttachmentReference.Buffer colorRefs = VkAttachmentReference.malloc(colorAttachmentCount, stack);
                for (int i = 0; i < colorAttachmentCount; i++) {
                    colorRefs.get(i).attachment(i).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                }
                subpass.colorAttachmentCount(colorAttachmentCount);
                subpass.pColorAttachments(colorRefs);
            }

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpass);

            // Layout transition subpass dependency
            if (colorAttachmentCount > 0) {
                AttachmentInfo firstColorInfo = colorAttachmentInfos[0];
                switch (firstColorInfo.finalLayout) {
                    case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> {
                        VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(1, stack);
                        subpassDependencies.get(0)
                                .srcSubpass(VK_SUBPASS_EXTERNAL)
                                .dstSubpass(0)
                                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                                .srcAccessMask(0)
                                .dstAccessMask(0);

                        renderPassInfo.pDependencies(subpassDependencies);
                    }
                    case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                        VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(1, stack);
                        subpassDependencies.get(0)
                                .srcSubpass(0)
                                .dstSubpass(VK_SUBPASS_EXTERNAL)
                                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                                .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

                        renderPassInfo.pDependencies(subpassDependencies);
                    }
                }
            } else if (depthAttachmentInfo != null) {
                if (depthAttachmentInfo.finalLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(1, stack);
                    subpassDependencies.get(0)
                            .srcSubpass(0)
                            .dstSubpass(VK_SUBPASS_EXTERNAL)
                            .srcStageMask(VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                            .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                            .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                    renderPassInfo.pDependencies(subpassDependencies);
                }
            }

            LongBuffer pRenderPass = stack.mallocLong(1);

            if (vkCreateRenderPass(Vulkan.getVkDevice(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass");
            }

            id = pRenderPass.get(0);
        }
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, long framebufferId, MemoryStack stack) {

        if (colorAttachmentInfos != null) {
            for (int i = 0; i < colorAttachmentInfos.length; i++) {
                VulkanImage colorAttachment = framebuffer.getColorAttachment(i);
                if (colorAttachment != null 
                        && colorAttachment.getCurrentLayout() != VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                {
                    colorAttachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                }
            }
        }
        
        if (depthAttachmentInfo != null
                && framebuffer.getDepthAttachment() != null
                && framebuffer.getDepthAttachment().getCurrentLayout() != VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        {
            framebuffer.getDepthAttachment()
                    .transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        }

        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack);
        renderPassInfo.sType$Default();
        renderPassInfo.renderPass(this.id);
        renderPassInfo.framebuffer(framebufferId);

        VkRect2D renderArea = VkRect2D.malloc(stack);
        renderArea.offset().set(0, 0);
        renderArea.extent().set(framebuffer.getWidth(), framebuffer.getHeight());
        renderPassInfo.renderArea(renderArea);

        VkClearValue.Buffer clearValues = VkClearValue.malloc(attachmentCount, stack);

        int i = 0;
        if (colorAttachmentInfos != null) {
            for (AttachmentInfo colorInfo : colorAttachmentInfos) {
                clearValues.get(i).color().float32(VRenderSystem.clearColor);
                i++;
            }
        }
        if (depthAttachmentInfo != null) {
            clearValues.get(i).depthStencil().set(1.0f, 0);
        }

        renderPassInfo.pClearValues(clearValues);

        vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        Renderer.getInstance().setBoundRenderPass(this);
    }

    public void endRenderPass(VkCommandBuffer commandBuffer) {
        if (Vulkan.DYNAMIC_RENDERING) {
            KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (colorAttachmentInfos != null) {
                    for (int i = 0; i < colorAttachmentInfos.length; i++) {
                        VulkanImage colorAttachment = framebuffer.getColorAttachment(i);
                        if (colorAttachment != null 
                                && colorAttachment.getCurrentLayout() != colorAttachmentInfos[i].finalLayout)
                        {
                            colorAttachment.transitionImageLayout(stack, commandBuffer, colorAttachmentInfos[i].finalLayout);
                        }
                    }
                }
                
                if (depthAttachmentInfo != null
                        && framebuffer.getDepthAttachment() != null
                        && framebuffer.getDepthAttachment().getCurrentLayout() != depthAttachmentInfo.finalLayout)
                {
                    framebuffer.getDepthAttachment()
                            .transitionImageLayout(stack, commandBuffer, depthAttachmentInfo.finalLayout);
                }
            }

        }
        else {
            vkCmdEndRenderPass(commandBuffer);

            if (colorAttachmentInfos != null) {
                for (int i = 0; i < colorAttachmentInfos.length; i++) {
                    VulkanImage colorAttachment = framebuffer.getColorAttachment(i);
                    if (colorAttachment != null) {
                        colorAttachment.setCurrentLayout(colorAttachmentInfos[i].finalLayout);
                    }
                }
            }

            if (depthAttachmentInfo != null && framebuffer.getDepthAttachment() != null)
                framebuffer.getDepthAttachment().setCurrentLayout(depthAttachmentInfo.finalLayout);
        }

        Renderer.getInstance().setBoundRenderPass(null);
    }

    public void beginDynamicRendering(VkCommandBuffer commandBuffer, MemoryStack stack) {
        if (colorAttachmentInfos != null) {
            for (int i = 0; i < colorAttachmentInfos.length; i++) {
                VulkanImage colorAttachment = framebuffer.getColorAttachment(i);
                if (colorAttachment != null 
                        && colorAttachment.getCurrentLayout() != VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                {
                    colorAttachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                }
            }
        }
        
        if (depthAttachmentInfo != null
                && framebuffer.getDepthAttachment() != null
                && framebuffer.getDepthAttachment().getCurrentLayout() != VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        {
            framebuffer.getDepthAttachment()
                    .transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        }

        VkRect2D renderArea = VkRect2D.malloc(stack);
        renderArea.offset().set(0, 0);
        renderArea.extent().set(framebuffer.getWidth(), framebuffer.getHeight());

        VkClearValue.Buffer clearValues = VkClearValue.malloc(attachmentCount, stack);
        int i = 0;
        if (colorAttachmentInfos != null) {
            for (AttachmentInfo colorInfo : colorAttachmentInfos) {
                clearValues.get(i).color().float32(VRenderSystem.clearColor);
                i++;
            }
        }
        if (depthAttachmentInfo != null) {
            clearValues.get(i).depthStencil().set(1.0f, 0);
        }

        VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack);
        renderingInfo.sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR);
        renderingInfo.renderArea(renderArea);
        renderingInfo.layerCount(1);

        // Color attachments
        if (colorAttachmentInfos != null && colorAttachmentInfos.length > 0) {
            VkRenderingAttachmentInfo.Buffer colorAttachments = VkRenderingAttachmentInfo.calloc(colorAttachmentInfos.length, stack);
            for (int j = 0; j < colorAttachmentInfos.length; j++) {
                VulkanImage colorAttachment = framebuffer.getColorAttachment(j);
                if (colorAttachment != null) {
                    colorAttachments.get(j)
                            .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                            .imageView(colorAttachment.getImageView())
                            .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                            .loadOp(colorAttachmentInfos[j].loadOp)
                            .storeOp(colorAttachmentInfos[j].storeOp)
                            .clearValue(clearValues.get(j));
                }
            }
            renderingInfo.pColorAttachments(colorAttachments);
        }

        // Depth attachment
        if (depthAttachmentInfo != null && framebuffer.getDepthAttachment() != null) {
            VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack);
            depthAttachment.sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR);
            depthAttachment.imageView(framebuffer.getDepthAttachment().getImageView());
            depthAttachment.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            depthAttachment.loadOp(depthAttachmentInfo.loadOp);
            depthAttachment.storeOp(depthAttachmentInfo.storeOp);

            int depthIdx = colorAttachmentInfos != null ? colorAttachmentInfos.length : 0;
            depthAttachment.clearValue(clearValues.get(depthIdx));

            renderingInfo.pDepthAttachment(depthAttachment);
        }

        KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
    }

    public Framebuffer getFramebuffer() {
        return framebuffer;
    }

    public void cleanUp() {
        if (!Vulkan.DYNAMIC_RENDERING)
            MemoryManager.getInstance().addFrameOp(
                    () -> vkDestroyRenderPass(Vulkan.getVkDevice(), this.id, null));

    }

    public long getId() {
        return id;
    }

    public AttachmentInfo[] getColorAttachmentInfos() {
        return colorAttachmentInfos;
    }

    public AttachmentInfo getDepthAttachmentInfo() {
        return depthAttachmentInfo;
    }

    public static class AttachmentInfo {
        final Type type;
        final int format;
        int finalLayout;
        int loadOp;
        int storeOp;

        public AttachmentInfo(Type type, int format) {
            this.type = type;
            this.format = format;
            this.finalLayout = type.defaultLayout;

            this.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            this.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        }

        public AttachmentInfo setOps(int loadOp, int storeOp) {
            this.loadOp = loadOp;
            this.storeOp = storeOp;

            return this;
        }

        public AttachmentInfo setLoadOp(int loadOp) {
            this.loadOp = loadOp;

            return this;
        }

        public AttachmentInfo setFinalLayout(int finalLayout) {
            this.finalLayout = finalLayout;

            return this;
        }

        public enum Type {
            COLOR(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
            DEPTH(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            final int defaultLayout;

            Type(int layout) {
                defaultLayout = layout;
            }
        }
    }

    public static Builder builder(Framebuffer framebuffer) {
        return new Builder(framebuffer);
    }

    public static class Builder {
        Framebuffer framebuffer;
        AttachmentInfo[] colorAttachmentInfos;
        AttachmentInfo depthAttachmentInfo;

        public Builder(Framebuffer framebuffer) {
            this.framebuffer = framebuffer;

            // Create color attachment infos based on framebuffer color attachments
            // Use colorFormats as fallback to handle cases where colorAttachments array isn't initialized yet (e.g., SwapChain)
            if (framebuffer.hasColorAttachments) {
                int[] formats = framebuffer.getColorFormats();
                if (formats != null && formats.length > 0) {
                    colorAttachmentInfos = new AttachmentInfo[formats.length];
                    for (int i = 0; i < colorAttachmentInfos.length; i++) {
                        colorAttachmentInfos[i] = new AttachmentInfo(AttachmentInfo.Type.COLOR, formats[i])
                                .setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE);
                    }
                }
            }
            
            if (framebuffer.hasDepthAttachment)
                depthAttachmentInfo = new AttachmentInfo(AttachmentInfo.Type.DEPTH, framebuffer.depthFormat)
                        .setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_DONT_CARE);
        }

        public RenderPass build() {
            return new RenderPass(framebuffer, colorAttachmentInfos, depthAttachmentInfo);
        }

        public Builder setLoadOp(int loadOp) {
            if (colorAttachmentInfos != null) {
                for (AttachmentInfo colorInfo : colorAttachmentInfos) {
                    colorInfo.setLoadOp(loadOp);
                }
            }
            if (depthAttachmentInfo != null) {
                depthAttachmentInfo.setLoadOp(loadOp);
            }

            return this;
        }

        public AttachmentInfo[] getColorAttachmentInfos() {
            return colorAttachmentInfos;
        }

        /**
         * Backward compatibility method
         */
        public AttachmentInfo getColorAttachmentInfo() {
            return colorAttachmentInfos != null && colorAttachmentInfos.length > 0 ? colorAttachmentInfos[0] : null;
        }

        public AttachmentInfo getDepthAttachmentInfo() {
            return depthAttachmentInfo;
        }
    }
}
