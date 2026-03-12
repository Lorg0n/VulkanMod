package net.vulkanmod.render.shader.plugin;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;

public class VibrantShaderPlugin implements ShaderPlugin {
    private static final ShaderPluginInfo INFO = new ShaderPluginInfo(
            "vulkanmod:vibrant",
            "Vibrant Visuals",
            "1.0.0",
            "A minimal vibrant shader with improved lighting and saturation.",
            "YourName",
            false
    );

    private GraphicsPipeline terrainShader;
    private GraphicsPipeline terrainShaderEarlyZ;
    // We can reuse the default blit/clouds, or load custom ones
    private GraphicsPipeline cloudsPipeline;

    @Override
    public ShaderPluginInfo getInfo() {
        return INFO;
    }

    @Override
    public void init() {
        // Load custom vibrant terrain shaders
        this.terrainShader = createPipeline("terrain", PipelineManager.terrainVertexFormat, "vibrant");
        this.terrainShaderEarlyZ = createPipeline("terrain_earlyZ", PipelineManager.terrainVertexFormat, "vibrant");

        // We can reuse the built-in clouds or make custom ones
        this.cloudsPipeline = createPipeline("clouds", DefaultVertexFormat.POSITION_COLOR, "vibrant");
    }

    private GraphicsPipeline createPipeline(String configName, VertexFormat vertexFormat, String shaderDir) {
        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, configName);

        // This will look in assets/vulkanmod/shaders/vibrant/
        final String path = ShaderLoadUtil.resolveShaderPath(shaderDir);
        JsonObject config = ShaderLoadUtil.getJsonConfig(path, configName);
        pipelineBuilder.parseBindings(config);

        ShaderLoadUtil.loadShaders(pipelineBuilder, config, configName, path);

        GraphicsPipeline pipeline = pipelineBuilder.createGraphicsPipeline();
        for (var buffer : pipeline.getBuffers()) {
            buffer.setUseGlobalBuffer(true);
        }
        return pipeline;
    }

    @Override
    public GraphicsPipeline getTerrainShader(String renderTypeName) { return terrainShader; }

    @Override
    public GraphicsPipeline getTerrainEarlyZShader(String renderTypeName) { return terrainShaderEarlyZ; }

    @Override
    public GraphicsPipeline getFastBlitPipeline() {
        // Just reuse the built-in fast blit
        return PipelineManager.getFastBlitPipeline();
    }

    @Override
    public GraphicsPipeline getCloudsPipeline() { return cloudsPipeline; }

    @Override
    public void cleanup() {
        if (terrainShader != null) terrainShader.cleanUp();
        if (terrainShaderEarlyZ != null) terrainShaderEarlyZ.cleanUp();
        if (cloudsPipeline != null) cloudsPipeline.cleanUp();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}