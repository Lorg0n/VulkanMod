package net.vulkanmod.render.shader.plugin;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;

public class VibrantShaderPlugin implements ShaderPlugin {
    private static final ShaderPluginInfo INFO = new ShaderPluginInfo(
            "vulkanmod:vibrant",
            "Vibrant Visuals",
            "2.0.0",
            "A realistic shader with true shadows, PBR water, waving foliage and ACES Filmic tonemap.",
            "Collateral",
            false
    );

    private GraphicsPipeline terrainShader;
    private GraphicsPipeline terrainShaderEarlyZ;
    private GraphicsPipeline terrainShadow;
    private GraphicsPipeline cloudsPipeline;

    @Override
    public ShaderPluginInfo getInfo() { return INFO; }

    @Override
    public void init() {
        this.terrainShader = createPipeline("terrain", PipelineManager.terrainVertexFormat, "vibrant");
        this.terrainShaderEarlyZ = createPipeline("terrain_earlyZ", PipelineManager.terrainVertexFormat, "vibrant");
        this.terrainShadow = createPipeline("terrain_shadow", PipelineManager.terrainVertexFormat, "vibrant");
        this.cloudsPipeline = createPipeline("clouds", DefaultVertexFormat.POSITION_COLOR, "vibrant");
    }

    private GraphicsPipeline createPipeline(String configName, VertexFormat vertexFormat, String shaderDir) {
        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, configName);

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
    public GraphicsPipeline getTerrainShadowShader() { return terrainShadow; }

    @Override
    public GraphicsPipeline getFastBlitPipeline() { return PipelineManager.getFastBlitPipeline(); }

    @Override
    public GraphicsPipeline getCloudsPipeline() { return cloudsPipeline; }

    @Override
    public void cleanup() {
        if (terrainShader != null) terrainShader.cleanUp();
        if (terrainShaderEarlyZ != null) terrainShaderEarlyZ.cleanUp();
        if (terrainShadow != null) terrainShadow.cleanUp();
        if (cloudsPipeline != null) cloudsPipeline.cleanUp();
    }

    @Override
    public boolean isAvailable() { return true; }
}