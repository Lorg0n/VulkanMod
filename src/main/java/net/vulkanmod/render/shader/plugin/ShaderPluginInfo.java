package net.vulkanmod.render.shader.plugin;

/**
 * Metadata information about a shader plugin
 */
public class ShaderPluginInfo {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final boolean isBuiltIn;

    public ShaderPluginInfo(String id, String name, String version, String description, String author, boolean isBuiltIn) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.isBuiltIn = isBuiltIn;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    @Override
    public String toString() {
        return name + " v" + version;
    }
}
