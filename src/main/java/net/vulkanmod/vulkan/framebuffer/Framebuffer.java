package net.vulkanmod.vulkan.framebuffer;

import it.unimi.dsi.fastutil.objects.Reference2LongArrayMap;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

import static net.vulkanmod.vulkan.Vulkan.DYNAMIC_RENDERING;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Enhanced Framebuffer supporting Multiple Render Targets (MRT)
 * Capable of writing to multiple color attachments simultaneously
 */
public class Framebuffer {
    public static final int DEFAULT_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    protected int[] colorFormats;
    protected int depthFormat;
    protected int width, height;
    protected boolean linearFiltering;
    protected boolean depthLinearFiltering;
    protected int attachmentCount;

    boolean hasColorAttachments;
    boolean hasDepthAttachment;

    private VulkanImage[] colorAttachments;
    protected VulkanImage depthAttachment;

    private final Reference2LongArrayMap<RenderPass> renderpassToFramebufferMap = new Reference2LongArrayMap<>();

    //SwapChain
    protected Framebuffer() {}

    public Framebuffer(Builder builder) {
        this.colorFormats = builder.colorFormats;
        this.depthFormat = builder.depthFormat;
        this.width = builder.width;
        this.height = builder.height;
        this.linearFiltering = builder.linearFiltering;
        this.depthLinearFiltering = builder.depthLinearFiltering;
        this.hasColorAttachments = builder.hasColorAttachments;
        this.hasDepthAttachment = builder.hasDepthAttachment;

        if (builder.createImages)
            this.createImages();
        else {
            this.colorAttachments = builder.colorAttachments;
            this.depthAttachment = builder.depthAttachment;
        }
    }

    public void createImages() {
        if (this.hasColorAttachments && this.colorFormats != null && this.colorFormats.length > 0) {
            this.colorAttachments = new VulkanImage[this.colorFormats.length];
            
            for (int i = 0; i < this.colorFormats.length; i++) {
                this.colorAttachments[i] = VulkanImage.builder(this.width, this.height)
                        .setFormat(colorFormats[i])
                        .setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                        .setLinearFiltering(linearFiltering)
                        .setClamp(true)
                        .createVulkanImage();
            }
        }

        if (this.hasDepthAttachment) {
            this.depthAttachment = VulkanImage.createDepthImage(depthFormat, this.width, this.height,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                    depthLinearFiltering, true);

            this.attachmentCount++;
        }
    }

    public void resize(int newWidth, int newHeight) {
        this.width = newWidth;
        this.height = newHeight;

        this.cleanUp();

        this.createImages();
    }

    private long createFramebuffer(RenderPass renderPass) {

        try (MemoryStack stack = MemoryStack.stackPush()) {

            LongBuffer attachments;
            
            if (colorAttachments != null && colorAttachments.length > 0 && depthAttachment != null) {
                attachments = stack.mallocLong(colorAttachments.length + 1);
                for (int i = 0; i < colorAttachments.length; i++) {
                    attachments.put(i, colorAttachments[i].getImageView());
                }
                attachments.put(colorAttachments.length, depthAttachment.getImageView());
            } else if (colorAttachments != null && colorAttachments.length > 0) {
                attachments = stack.mallocLong(colorAttachments.length);
                for (int i = 0; i < colorAttachments.length; i++) {
                    attachments.put(i, colorAttachments[i].getImageView());
                }
            } else if (depthAttachment != null) {
                attachments = stack.longs(depthAttachment.getImageView());
            } else {
                throw new IllegalStateException("Framebuffer must have at least one attachment");
            }

            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
            framebufferInfo.sType$Default();
            framebufferInfo.renderPass(renderPass.getId());
            framebufferInfo.width(this.width);
            framebufferInfo.height(this.height);
            framebufferInfo.layers(1);
            framebufferInfo.pAttachments(attachments);

            if (VK10.vkCreateFramebuffer(Vulkan.getVkDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer");
            }

            return pFramebuffer.get(0);
        }
    }

    public void beginRenderPass(VkCommandBuffer commandBuffer, RenderPass renderPass, MemoryStack stack) {
        if (!DYNAMIC_RENDERING) {
            long framebufferId = this.getFramebufferId(renderPass);
            renderPass.beginRenderPass(commandBuffer, framebufferId, stack);
        } else {
            renderPass.beginDynamicRendering(commandBuffer, stack);
        }
    }

    protected long getFramebufferId(RenderPass renderPass) {
        return this.renderpassToFramebufferMap.computeIfAbsent(renderPass, renderPass1 -> createFramebuffer(renderPass));
    }

    public VkViewport.Buffer viewport(MemoryStack stack) {
        VkViewport.Buffer viewport = VkViewport.malloc(1, stack);
        viewport.x(0.0f);
        viewport.y(this.height);
        viewport.width(this.width);
        viewport.height(-this.height);
        viewport.minDepth(0.0f);
        viewport.maxDepth(1.0f);

        return viewport;
    }

    public VkRect2D.Buffer scissor(MemoryStack stack) {
        VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack);
        scissor.offset().set(0, 0);
        scissor.extent().set(this.width, this.height);

        return scissor;
    }

    public void cleanUp() {
        cleanUp(true);
    }

    public void cleanUp(boolean cleanImages) {
        if (cleanImages) {
            if (this.colorAttachments != null) {
                for (VulkanImage attachment : this.colorAttachments) {
                    if (attachment != null) {
                        attachment.free();
                    }
                }
            }

            if (this.depthAttachment != null)
                this.depthAttachment.free();
        }

        final VkDevice device = Vulkan.getVkDevice();
        final var ids = renderpassToFramebufferMap.values().toLongArray();

        MemoryManager.getInstance().addFrameOp(
                () -> Arrays.stream(ids).forEach(id ->
                        vkDestroyFramebuffer(device, id, null))
        );

        renderpassToFramebufferMap.clear();
    }

    public long getDepthImageView() {
        return depthAttachment != null ? depthAttachment.getImageView() : 0;
    }

    public VulkanImage getDepthAttachment() {
        return depthAttachment;
    }

    /**
     * Get color attachment by index (for MRT support)
     */
    public VulkanImage getColorAttachment(int index) {
        if (colorAttachments != null && index >= 0 && index < colorAttachments.length) {
            return colorAttachments[index];
        }
        return null;
    }

    /**
     * Get first color attachment (backward compatibility)
     */
    public VulkanImage getColorAttachment() {
        return getColorAttachment(0);
    }

    /**
     * Get all color attachments
     */
    public VulkanImage[] getColorAttachments() {
        return colorAttachments;
    }

    /**
     * Get number of color attachments
     */
    public int getColorAttachmentCount() {
        return colorAttachments != null ? colorAttachments.length : 0;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getFormat() {
        return colorFormats != null && colorFormats.length > 0 ? colorFormats[0] : 0;
    }

    public int[] getColorFormats() {
        return colorFormats;
    }

    public int getDepthFormat() {
        return this.depthFormat;
    }

    public static Builder builder(int width, int height, int colorAttachments, boolean hasDepthAttachment) {
        return new Builder(width, height, colorAttachments, hasDepthAttachment);
    }

    public static Builder builder(int width, int height, int[] colorFormats, boolean hasDepthAttachment) {
        return new Builder(width, height, colorFormats, hasDepthAttachment);
    }

    public static Builder builder(int width, int height, List<Integer> colorFormats, boolean hasDepthAttachment) {
        int[] formats = new int[colorFormats.size()];
        for (int i = 0; i < colorFormats.size(); i++) {
            formats[i] = colorFormats.get(i);
        }
        return new Builder(width, height, formats, hasDepthAttachment);
    }

    public static Builder builder(VulkanImage[] colorAttachments, VulkanImage depthAttachment) {
        return new Builder(colorAttachments, depthAttachment);
    }

    public static Builder builderSingle(VulkanImage colorAttachment, VulkanImage depthAttachment) {
        return new Builder(colorAttachment, depthAttachment);
    }

    public static class Builder {
        final boolean createImages;
        final int width, height;
        int[] colorFormats;
        int depthFormat;

        VulkanImage[] colorAttachments;
        VulkanImage depthAttachment;

        boolean hasColorAttachments;
        boolean hasDepthAttachment;

        boolean linearFiltering;
        boolean depthLinearFiltering;

        /**
         * Single format constructor (backward compatible)
         */
        public Builder(int width, int height, int colorAttachmentCount, boolean hasDepthAttachment) {
            Validate.isTrue(colorAttachmentCount > 0 || hasDepthAttachment, "At least 1 attachment needed");

            this.createImages = true;
            this.colorFormats = new int[colorAttachmentCount];
            for (int i = 0; i < colorAttachmentCount; i++) {
                this.colorFormats[i] = DEFAULT_FORMAT;
            }
            this.depthFormat = Vulkan.getDefaultDepthFormat();
            this.linearFiltering = true;
            this.depthLinearFiltering = false;

            this.width = width;
            this.height = height;
            this.hasColorAttachments = colorAttachmentCount > 0;
            this.hasDepthAttachment = hasDepthAttachment;
        }

        /**
         * Multi-format constructor for MRT
         */
        public Builder(int width, int height, int[] colorFormats, boolean hasDepthAttachment) {
            Validate.isTrue(colorFormats != null && colorFormats.length > 0 || hasDepthAttachment, "At least 1 attachment needed");
            Validate.isTrue(colorFormats == null || colorFormats.length <= 16, "Maximum 16 color attachments supported");

            this.createImages = true;
            this.colorFormats = colorFormats;
            this.depthFormat = Vulkan.getDefaultDepthFormat();
            this.linearFiltering = true;
            this.depthLinearFiltering = false;

            this.width = width;
            this.height = height;
            this.hasColorAttachments = colorFormats != null && colorFormats.length > 0;
            this.hasDepthAttachment = hasDepthAttachment;
        }

        /**
         * Constructor for existing VulkanImage attachments
         */
        public Builder(VulkanImage colorAttachment, VulkanImage depthAttachment) {
            this.createImages = false;
            if (colorAttachment != null) {
                this.colorAttachments = new VulkanImage[] { colorAttachment };
                this.colorFormats = new int[] { colorAttachment.format };
            }
            this.depthAttachment = depthAttachment;

            this.width = colorAttachment != null ? colorAttachment.width : (depthAttachment != null ? depthAttachment.width : 0);
            this.height = colorAttachment != null ? colorAttachment.height : (depthAttachment != null ? depthAttachment.height : 0);
            this.hasColorAttachments = colorAttachment != null;
            this.hasDepthAttachment = depthAttachment != null;

            this.depthFormat = this.hasDepthAttachment ? depthAttachment.format : 0;
            this.linearFiltering = true;
            this.depthLinearFiltering = false;
        }

        /**
         * Constructor for multiple VulkanImage attachments
         */
        public Builder(VulkanImage[] colorAttachments, VulkanImage depthAttachment) {
            this.createImages = false;
            this.colorAttachments = colorAttachments;
            if (colorAttachments != null && colorAttachments.length > 0) {
                this.colorFormats = new int[colorAttachments.length];
                for (int i = 0; i < colorAttachments.length; i++) {
                    this.colorFormats[i] = colorAttachments[i].format;
                }
            }
            this.depthAttachment = depthAttachment;

            this.width = colorAttachments != null && colorAttachments.length > 0 ? colorAttachments[0].width : (depthAttachment != null ? depthAttachment.width : 0);
            this.height = colorAttachments != null && colorAttachments.length > 0 ? colorAttachments[0].height : (depthAttachment != null ? depthAttachment.height : 0);
            this.hasColorAttachments = colorAttachments != null && colorAttachments.length > 0;
            this.hasDepthAttachment = depthAttachment != null;

            this.depthFormat = this.hasDepthAttachment ? depthAttachment.format : 0;
            this.linearFiltering = true;
            this.depthLinearFiltering = false;
        }

        public Framebuffer build() {
            return new Framebuffer(this);
        }

        /**
         * Set format for all color attachments (single format mode)
         */
        public Builder setFormat(int format) {
            if (this.colorFormats != null) {
                for (int i = 0; i < this.colorFormats.length; i++) {
                    this.colorFormats[i] = format;
                }
            }
            return this;
        }

        /**
         * Set format for specific color attachment
         */
        public Builder setColorFormat(int index, int format) {
            if (this.colorFormats != null && index >= 0 && index < this.colorFormats.length) {
                this.colorFormats[index] = format;
            }
            return this;
        }

        public Builder setDepthFormat(int depthFormat) {
            this.depthFormat = depthFormat;
            return this;
        }

        public Builder setLinearFiltering(boolean b) {
            this.linearFiltering = b;
            return this;
        }

        public Builder setDepthLinearFiltering(boolean b) {
            this.depthLinearFiltering = b;
            return this;
        }
    }
}
