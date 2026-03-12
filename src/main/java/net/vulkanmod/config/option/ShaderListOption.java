package net.vulkanmod.config.option;

import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.render.shader.plugin.ShaderPlugin;
import net.vulkanmod.render.shader.plugin.ShaderPluginManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utilities for creating shader-related options and pages
 */
public abstract class ShaderListOption {

    /**
     * Create an option block containing all shader selection options
     */
    public static OptionBlock[] getShaderOpts() {
        Collection<ShaderPlugin> plugins = ShaderPluginManager.getAllPlugins();
        List<ShaderPlugin> pluginList = new ArrayList<>(plugins);

        // Create array of plugins for cycling option
        ShaderPlugin[] shaderArray = pluginList.toArray(new ShaderPlugin[0]);

        if (shaderArray.length == 0) {
            return new OptionBlock[0]; // No shaders available
        }

        CyclingOption<ShaderPlugin> shaderOption = new CyclingOption<>(
                Component.translatable("vulkanmod.options.shader"),
                shaderArray,
                (shader) -> {
                    ShaderPluginManager.setActivePlugin(shader.getInfo().getId());
                    Options.config.selectedShader = shader.getInfo().getId();
                    Options.config.write();
                },
                () -> {
                    String selectedId = Options.config.selectedShader;
                    ShaderPlugin plugin = ShaderPluginManager.getPlugin(selectedId);
                    return plugin != null ? plugin : (shaderArray.length > 0 ? shaderArray[0] : null);
                }
        );
        
        // Set translator after creation to avoid type casting issues
        shaderOption.setTranslator(shader -> shader != null ? 
                Component.nullToEmpty(shader.getInfo().toString()) : 
                Component.nullToEmpty("Unknown"));

        return new OptionBlock[]{
                new OptionBlock(
                        Component.translatable("vulkanmod.options.shader.title").getString(),
                        new Option<?>[]{shaderOption}
                ),
                new OptionBlock(
                        Component.translatable("vulkanmod.options.shader.info").getString(),
                        new Option<?>[]{}
                )
        };
    }

    /**
     * Get a detailed description of the currently active shader
     */
    public static Component getActiveShaderDescription() {
        ShaderPlugin active = ShaderPluginManager.getActivePlugin();
        if (active == null) {
            return Component.literal("No shader loaded");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(active.getInfo().getName()).append(" v").append(active.getInfo().getVersion()).append("\n");
        sb.append("Author: ").append(active.getInfo().getAuthor()).append("\n");
        sb.append(active.getInfo().getDescription());

        return Component.literal(sb.toString());
    }

    /**
     * Get count of available shaders
     */
    public static int getShaderCount() {
        return ShaderPluginManager.getPluginCount();
    }
}
