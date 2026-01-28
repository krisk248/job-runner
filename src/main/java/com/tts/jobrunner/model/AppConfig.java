package com.tts.jobrunner.model;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration - defines classpath source.
 * Compatible with Java 8+
 */
public class AppConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String webappPath;

    public AppConfig() {
    }

    public AppConfig(String id, String name, String webappPath) {
        this.id = id;
        this.name = name;
        this.webappPath = webappPath;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebappPath() {
        return webappPath;
    }

    public void setWebappPath(String webappPath) {
        this.webappPath = webappPath;
    }

    /**
     * Get WEB-INF/classes path
     */
    public String getClassesPath() {
        return webappPath + File.separator + "WEB-INF" + File.separator + "classes";
    }

    /**
     * Get WEB-INF/lib path
     */
    public String getLibPath() {
        return webappPath + File.separator + "WEB-INF" + File.separator + "lib";
    }

    /**
     * Build classpath string for this app
     */
    public String buildClasspath(String configDir) {
        StringBuilder cp = new StringBuilder();

        // Add classes directory
        cp.append(getClassesPath());

        // Add all JARs in lib directory
        File libDir = new File(getLibPath());
        if (libDir.exists() && libDir.isDirectory()) {
            cp.append(File.pathSeparator).append(getLibPath()).append("/*");
        }

        // Add config directory
        if (configDir != null && !configDir.isEmpty()) {
            cp.append(File.pathSeparator).append(configDir);
        }

        return cp.toString();
    }

    /**
     * Check if webapp path exists
     */
    public boolean isValid() {
        File webappDir = new File(webappPath);
        File classesDir = new File(getClassesPath());
        return webappDir.exists() && classesDir.exists();
    }
}
