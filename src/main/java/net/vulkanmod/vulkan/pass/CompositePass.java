package net.vulkanmod.vulkan.pass;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Abstract base class for full-screen composite passes (post-processing)
 * Used for lighting, bloom, fog, tonemapping, TAA, and other deferred effects
 *
 * Composite passes read from G-Buffer textures and output to intermediate or final framebuffer
 */
public abstract class CompositePass {

    protected Framebuffer outputFramebuffer;
    protected RenderPass renderPass;
    protected GraphicsPipeline pipeline;

    protected int outputWidth;
    protected int outputHeight;

    protected boolean enabled = true;
    protected boolean useFullScreenTriangle = true;

    // Input samplers
    protected final List<VulkanImage> inputTextures = new ArrayList<>();

    protected CompositePass(Builder builder) {
        this.outputFramebuffer = builder.framebuffer;
        this.outputWidth = builder.width;
        this.outputHeight = builder.height;
        this.useFullScreenTriangle = builder.useFullScreenTriangle;
        this.enabled = builder.enabled;

        if (builder.framebuffer != null) {
            createRenderPass();
        }
    }

    /**
     * Create the render pass for this composite pass
     */
    protected void createRenderPass() {
        RenderPass.Builder builder = RenderPass.builder(this.outputFramebuffer);

        // Configure for shader read-only output
        RenderPass.AttachmentInfo[] colorInfos = builder.getColorAttachmentInfos();
        if (colorInfos != null) {
            for (RenderPass.AttachmentInfo info : colorInfos) {
                info.setOps(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_STORE);
                info.setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        }

        RenderPass.AttachmentInfo depthInfo = builder.getDepthAttachmentInfo();
        if (depthInfo != null) {
            depthInfo.setOps(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_DONT_CARE);
            depthInfo.setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }

        this.renderPass = builder.build();
    }

    /**
     * Begin the composite pass
     */
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        if (!this.enabled || this.outputFramebuffer == null || this.renderPass == null) {
            return;
        }

        Renderer.getInstance().beginRenderPass(this.renderPass, this.outputFramebuffer);

        Renderer.setViewport(0, 0, this.outputWidth, this.outputHeight, stack);

        VkRect2D.Buffer pScissor = this.outputFramebuffer.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);

        // Bind pipeline
        if (this.pipeline != null) {
            this.pipeline.bind(commandBuffer, PipelineState.DEFAULT);
        }
    }

    /**
     * End the composite pass
     */
    public void end(VkCommandBuffer commandBuffer) {
        if (!this.enabled || this.outputFramebuffer == null || this.renderPass == null) {
            return;
        }

        Renderer.getInstance().endRenderPass(commandBuffer);
    }

    /**
     * Render full-screen geometry (triangle or quad)
     */
    public void renderFullScreen(VkCommandBuffer commandBuffer) {
        if (this.useFullScreenTriangle) {
            // Render single large triangle (most efficient)
            vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        } else {
            // Render full-screen quad (6 vertices)
            vkCmdDraw(commandBuffer, 6, 1, 0, 0);
        }
    }

    /**
     * Add input texture to sampler list
     */
    public void addInputTexture(VulkanImage texture) {
        this.inputTextures.add(texture);
    }

    /**
     * Clear input textures
     */
    public void clearInputTextures() {
        this.inputTextures.clear();
    }

    /**
     * Enable/disable this pass
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Resize the output framebuffer
     */
    public void resize(int newWidth, int newHeight) {
        this.outputWidth = newWidth;
        this.outputHeight = newHeight;
        if (this.outputFramebuffer != null) {
            this.outputFramebuffer.resize(newWidth, newHeight);
        }
    }

    /**
     * Clean up resources
     */
    public void cleanUp() {
        if (this.renderPass != null) {
            this.renderPass.cleanUp();
        }
        if (this.outputFramebuffer != null) {
            this.outputFramebuffer.cleanUp();
        }
        if (this.pipeline != null) {
            this.pipeline.cleanUp();
        }
        this.inputTextures.clear();
    }

    /**
     * Get output framebuffer
     */
    public Framebuffer getOutputFramebuffer() {
        return this.outputFramebuffer;
    }

    /**
     * Get render pass
     */
    public RenderPass getRenderPass() {
        return this.renderPass;
    }

    /**
     * Get pipeline
     */
    public GraphicsPipeline getPipeline() {
        return this.pipeline;
    }

    /**
     * Abstract method for subclasses to define their specific rendering logic
     */
    public abstract void render(VkCommandBuffer commandBuffer, MemoryStack stack);

    /**
     * Builder pattern for CompositePass configuration
     */
    protected static abstract class Builder {
        protected Framebuffer framebuffer;
        protected int width, height;
        protected boolean useFullScreenTriangle = true;
        protected boolean enabled = true;

        public Builder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public Builder setFramebuffer(Framebuffer framebuffer) {
            this.framebuffer = framebuffer;
            return this;
        }

        public Builder useFullScreenTriangle(boolean useTriangle) {
            this.useFullScreenTriangle = useTriangle;
            return this;
        }

        public Builder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public abstract CompositePass build();
    }
}
