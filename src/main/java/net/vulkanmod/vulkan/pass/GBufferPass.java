package net.vulkanmod.vulkan.pass;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/**
 * G-Buffer Pass for Deferred Rendering
 * Outputs geometry information to multiple render targets (G-Buffer textures)
 *
 * Standard G-Buffer Structure:
 * - colortex0: Albedo/Color (RGBA8 or RGBA16F)
 * - colortex1: View-space Normals (RGBA16F or RGBA8_SNORM) + Roughness
 * - colortex2: Lighting/Block light levels (RGBA8) + Sky light + Material IDs
 * - depthAttachment: Standard depth information
 */
public class GBufferPass implements MainPass {

    public static GBufferPass create() {
        return new GBufferPass();
    }

    private final Framebuffer gBufferFramebuffer;
    private final List<GpuTexture> colorTextures = new ArrayList<>();
    private final List<GpuTextureView> colorTextureViews = new ArrayList<>();
    private GpuTexture depthTexture;
    private RenderPass renderPass;

    private static final int[] G_BUFFER_FORMATS = {
            VK_FORMAT_R8G8B8A8_UNORM,      // colortex0: Albedo
            VK_FORMAT_R16G16B16A16_SFLOAT, // colortex1: Normals + Roughness
            VK_FORMAT_R8G8B8A8_UNORM       // colortex2: Lighting + Material IDs
    };

    private static final int DEPTH_FORMAT = VK_FORMAT_D24_UNORM_S8_UINT;

    GBufferPass() {
        // Create G-Buffer framebuffer with multiple color attachments
        this.gBufferFramebuffer = Renderer.getInstance().getSwapChain() != null
                ? createGBufferFramebuffer()
                : null;

        if (this.gBufferFramebuffer != null) {
            createRenderPass();
            createAttachmentTextures();
        }
    }

    private Framebuffer createGBufferFramebuffer() {
        int width = Renderer.getInstance().getSwapChain().getWidth();
        int height = Renderer.getInstance().getSwapChain().getHeight();

        // Create framebuffer with 3 color attachments + depth
        return Framebuffer.builder(width, height, G_BUFFER_FORMATS, true)
                .setDepthFormat(DEPTH_FORMAT)
                .setLinearFiltering(true)
                .build();
    }

    private void createRenderPass() {
        RenderPass.Builder builder = RenderPass.builder(this.gBufferFramebuffer);
        
        // Configure attachments for deferred rendering
        RenderPass.AttachmentInfo[] colorInfos = builder.getColorAttachmentInfos();
        if (colorInfos != null) {
            for (RenderPass.AttachmentInfo info : colorInfos) {
                info.setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE);
                info.setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        }
        
        RenderPass.AttachmentInfo depthInfo = builder.getDepthAttachmentInfo();
        if (depthInfo != null) {
            depthInfo.setOps(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE);
            depthInfo.setFinalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }

        this.renderPass = builder.build();
    }

    private void createAttachmentTextures() {
        VkGpuDevice device = (VkGpuDevice) RenderSystem.getDevice();
        
        colorTextures.clear();
        colorTextureViews.clear();

        // Create GPU textures for all G-Buffer attachments
        VulkanImage[] colorAttachments = this.gBufferFramebuffer.getColorAttachments();
        if (colorAttachments != null) {
            for (VulkanImage attachment : colorAttachments) {
                if (attachment != null) {
                    VkGpuTexture gpuTexture = device.gpuTextureFromVulkanImage(attachment);
                    GpuTextureView textureView = device.createTextureView(gpuTexture);
                    colorTextures.add(gpuTexture);
                    colorTextureViews.add(textureView);
                }
            }
        }

        // Create depth texture
        VulkanImage depthAttachment = this.gBufferFramebuffer.getDepthAttachment();
        if (depthAttachment != null) {
            this.depthTexture = device.gpuTextureFromVulkanImage(depthAttachment);
        }
    }

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        if (this.gBufferFramebuffer == null || this.renderPass == null) {
            return;
        }

        Renderer.getInstance().beginRenderPass(this.renderPass, this.gBufferFramebuffer);

        Renderer.setViewport(0, 0, this.gBufferFramebuffer.getWidth(), this.gBufferFramebuffer.getHeight(), stack);

        VkRect2D.Buffer pScissor = this.gBufferFramebuffer.scissor(stack);
        VK10.vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
        if (this.gBufferFramebuffer == null || this.renderPass == null) {
            return;
        }

        Renderer.getInstance().endRenderPass(commandBuffer);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Transition color attachments to shader read only
            VulkanImage[] colorAttachments = this.gBufferFramebuffer.getColorAttachments();
            if (colorAttachments != null) {
                for (VulkanImage attachment : colorAttachments) {
                    if (attachment != null && attachment.getCurrentLayout() != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                        attachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    }
                }
            }

            // Transition depth to shader read only
            VulkanImage depthAttachment = this.gBufferFramebuffer.getDepthAttachment();
            if (depthAttachment != null && depthAttachment.getCurrentLayout() != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                depthAttachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        }
    }

    @Override
    public void cleanUp() {
        if (this.renderPass != null) {
            this.renderPass.cleanUp();
        }
        if (this.gBufferFramebuffer != null) {
            this.gBufferFramebuffer.cleanUp();
        }
        colorTextures.clear();
        colorTextureViews.clear();
    }

    @Override
    public void onResize() {
        if (this.gBufferFramebuffer != null) {
            int newWidth = Renderer.getInstance().getSwapChain().getWidth();
            int newHeight = Renderer.getInstance().getSwapChain().getHeight();
            this.gBufferFramebuffer.resize(newWidth, newHeight);
            createAttachmentTextures();
        }
    }

    /**
     * Get G-Buffer color attachment by index
     * 0: Albedo (colortex0)
     * 1: Normals + Roughness (colortex1)
     * 2: Lighting/Material (colortex2)
     */
    @Override
    public GpuTexture getColorAttachment(int index) {
        if (index >= 0 && index < colorTextures.size()) {
            return colorTextures.get(index);
        }
        return null;
    }

    @Override
    public GpuTexture getColorAttachment() {
        return getColorAttachment(0);
    }

    /**
     * Get all G-Buffer color attachments
     */
    public List<GpuTexture> getAllColorAttachments() {
        return new ArrayList<>(colorTextures);
    }

    @Override
    public GpuTextureView getColorAttachmentView(int index) {
        if (index >= 0 && index < colorTextureViews.size()) {
            return colorTextureViews.get(index);
        }
        return null;
    }

    public GpuTextureView getColorAttachmentView() {
        return getColorAttachmentView(0);
    }

    @Override
    public GpuTexture getDepthAttachment() {
        return this.depthTexture;
    }

    /**
     * Get the framebuffer
     */
    public Framebuffer getFramebuffer() {
        return this.gBufferFramebuffer;
    }

    /**
     * Get the render pass
     */
    public RenderPass getRenderPass() {
        return this.renderPass;
    }

    /**
     * Get number of G-Buffer color attachments
     */
    public int getColorAttachmentCount() {
        return colorTextures.size();
    }

    /**
     * Bind all G-Buffer textures as sampler inputs for composite passes
     */
    public void bindGBufferTextures() {
        for (int i = 0; i < colorTextures.size(); i++) {
            net.vulkanmod.vulkan.texture.VTextureSelector.bindTexture(
                    i,
                    ((VkGpuTexture) colorTextures.get(i)).getVulkanImage()
                        );
        }
    }
}
