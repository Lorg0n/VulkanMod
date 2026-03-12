package net.vulkanmod.render.shader.plugin;

import net.vulkanmod.Initializer;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Manages shader plugins, discovery, and switching.
 * Handles plugin lifecycle and provides access to the active shader plugin.
 */
public class ShaderPluginManager {
    private static final Logger LOGGER = Initializer.LOGGER;

    private static final Map<String, ShaderPlugin> PLUGINS = new LinkedHashMap<>();
    private static ShaderPlugin activePlugin;
    private static final ShaderPlugin FALLBACK = new BuiltInShaderPlugin();

    public static void init() {
        LOGGER.info("Initializing shader plugin system...");

        // Register built-in plugin
        registerPlugin(FALLBACK);

        // Set default plugin
        setActivePlugin("vulkanmod:builtin");

        LOGGER.info("Shader plugin system initialized with {} plugins", PLUGINS.size());
    }

    /**
     * Register a new shader plugin
     */
    public static void registerPlugin(ShaderPlugin plugin) {
        if (plugin == null) {
            LOGGER.warn("Attempted to register null plugin");
            return;
        }

        String id = plugin.getInfo().getId();
        if (PLUGINS.containsKey(id)) {
            LOGGER.warn("Plugin with id {} already registered", id);
            return;
        }

        PLUGINS.put(id, plugin);
        LOGGER.debug("Registered shader plugin: {} ({})", plugin.getInfo().getName(), id);
    }

    /**
     * Set the active shader plugin by ID
     */
    public static boolean setActivePlugin(String pluginId) {
        ShaderPlugin plugin = PLUGINS.get(pluginId);
        if (plugin == null) {
            LOGGER.warn("Plugin {} not found, falling back to built-in", pluginId);
            plugin = FALLBACK;
        }

        if (!plugin.isAvailable()) {
            LOGGER.warn("Plugin {} is not available, falling back to built-in", pluginId);
            plugin = FALLBACK;
        }

        // Clean up old plugin
        if (activePlugin != null && activePlugin != plugin) {
            try {
                activePlugin.cleanup();
            } catch (Exception e) {
                LOGGER.error("Error cleaning up plugin {}", activePlugin.getInfo().getId(), e);
            }
        }

        // Initialize new plugin
        try {
            plugin.init();
        } catch (Exception e) {
            LOGGER.error("Error initializing plugin {}", plugin.getInfo().getId(), e);
            plugin = FALLBACK;
            plugin.init();
        }

        activePlugin = plugin;
        LOGGER.info("Active shader plugin: {} ({})", activePlugin.getInfo().getName(), activePlugin.getInfo().getId());
        return true;
    }

    /**
     * Get the currently active shader plugin
     */
    public static ShaderPlugin getActivePlugin() {
        return activePlugin != null ? activePlugin : FALLBACK;
    }

    /**
     * Get a plugin by ID
     */
    public static ShaderPlugin getPlugin(String pluginId) {
        return PLUGINS.get(pluginId);
    }

    /**
     * Get all registered plugins
     */
    public static Collection<ShaderPlugin> getAllPlugins() {
        return Collections.unmodifiableCollection(PLUGINS.values());
    }

    /**
     * Get all plugin IDs
     */
    public static Set<String> getPluginIds() {
        return Collections.unmodifiableSet(PLUGINS.keySet());
    }

    /**
     * Get the plugin count
     */
    public static int getPluginCount() {
        return PLUGINS.size();
    }

    /**
     * Get the active plugin's terrain shader
     */
    public static GraphicsPipeline getActiveTerrainShader(String renderTypeName) {
        try {
            return getActivePlugin().getTerrainShader(renderTypeName);
        } catch (Exception e) {
            LOGGER.error("Error getting terrain shader from plugin {}", getActivePlugin().getInfo().getId(), e);
            return FALLBACK.getTerrainShader(renderTypeName);
        }
    }

    /**
     * Get the active plugin's terrain early-Z shader
     */
    public static GraphicsPipeline getActiveTerrainEarlyZShader(String renderTypeName) {
        try {
            return getActivePlugin().getTerrainEarlyZShader(renderTypeName);
        } catch (Exception e) {
            LOGGER.error("Error getting terrain early-Z shader from plugin {}", getActivePlugin().getInfo().getId(), e);
            return FALLBACK.getTerrainEarlyZShader(renderTypeName);
        }
    }

    /**
     * Get the active plugin's blit pipeline
     */
    public static GraphicsPipeline getActiveFastBlitPipeline() {
        try {
            return getActivePlugin().getFastBlitPipeline();
        } catch (Exception e) {
            LOGGER.error("Error getting blit pipeline from plugin {}", getActivePlugin().getInfo().getId(), e);
            return FALLBACK.getFastBlitPipeline();
        }
    }

    /**
     * Get the active plugin's clouds pipeline
     */
    public static GraphicsPipeline getActiveCloudsPipeline() {
        try {
            return getActivePlugin().getCloudsPipeline();
        } catch (Exception e) {
            LOGGER.error("Error getting clouds pipeline from plugin {}", getActivePlugin().getInfo().getId(), e);
            return FALLBACK.getCloudsPipeline();
        }
    }

    /**
     * Clean up all plugins
     */
    public static void cleanup() {
        LOGGER.info("Cleaning up shader plugins...");
        for (ShaderPlugin plugin : PLUGINS.values()) {
            try {
                plugin.cleanup();
            } catch (Exception e) {
                LOGGER.error("Error cleaning up plugin {}", plugin.getInfo().getId(), e);
            }
        }
        PLUGINS.clear();
        activePlugin = null;
    }
}
