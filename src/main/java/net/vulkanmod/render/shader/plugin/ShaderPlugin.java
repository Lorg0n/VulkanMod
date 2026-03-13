package net.vulkanmod.render.shader.plugin;

import net.vulkanmod.vulkan.shader.GraphicsPipeline;

/**
 * Base interface for shader plugins.
 * Implementations can provide custom shader pipelines to replace the default ones.
 */
public interface ShaderPlugin {
    /**
     * Get metadata about this plugin
     */
    ShaderPluginInfo getInfo();

    /**
     * Initialize the plugin. Called when VulkanMod starts.
     */
    void init();

    /**
     * Get the terrain shader for a specific render type
     */
    GraphicsPipeline getTerrainShader(String renderTypeName);

    /**
     * Get the terrain early-Z shader for translucent rendering
     */
    GraphicsPipeline getTerrainEarlyZShader(String renderTypeName);

    GraphicsPipeline getTerrainShadowShader();

    /**
     * Get the fast blit pipeline
     */
    GraphicsPipeline getFastBlitPipeline();

    /**
     * Get the clouds rendering pipeline
     */
    GraphicsPipeline getCloudsPipeline();

    /**
     * Called when the plugin is unloaded or switched away from.
     * Clean up resources here.
     */
    void cleanup();

    /**
     * Check if this plugin is available/loadable
     */
    boolean isAvailable();
}
