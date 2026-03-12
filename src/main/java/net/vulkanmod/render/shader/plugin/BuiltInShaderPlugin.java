package net.vulkanmod.render.shader.plugin;

import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.render.PipelineManager;

/**
 * The built-in shader plugin that wraps the original hardcoded pipelines.
 * This ensures backward compatibility and serves as the fallback shader.
 */
public class BuiltInShaderPlugin implements ShaderPlugin {
    private static final ShaderPluginInfo INFO = new ShaderPluginInfo(
            "vulkanmod:builtin",
            "Built-in Shaders",
            "1.0.0",
            "The original VulkanMod shader implementation",
            "xcollateral",
            true
    );

    @Override
    public ShaderPluginInfo getInfo() {
        return INFO;
    }

    @Override
    public void init() {
        // Built-in shaders are already initialized by PipelineManager
    }

    @Override
    public GraphicsPipeline getTerrainShader(String renderTypeName) {
        return PipelineManager.getDefaultTerrainShader();
    }

    @Override
    public GraphicsPipeline getTerrainEarlyZShader(String renderTypeName) {
        return PipelineManager.getDefaultTerrainEarlyZShader();
    }

    @Override
    public GraphicsPipeline getFastBlitPipeline() {
        return PipelineManager.getDefaultFastBlitPipeline();
    }

    @Override
    public GraphicsPipeline getCloudsPipeline() {
        return PipelineManager.getDefaultCloudsPipeline();
    }

    @Override
    public void cleanup() {
        // Don't clean up built-in shaders - they're always available
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
