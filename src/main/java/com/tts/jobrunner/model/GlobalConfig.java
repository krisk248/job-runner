package com.tts.jobrunner.model;

import java.io.Serializable;

/**
 * Global configuration for all jobs.
 * Compatible with Java 8+
 */
public class GlobalConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String javaHome;
    private String javaOpts;
    private String configDir;
    private String logsDir;

    public GlobalConfig() {
        // Defaults
        this.javaHome = System.getProperty("java.home");
        this.javaOpts = "-Xms256m -Xmx512m";
        this.configDir = "/opt/config";
        this.logsDir = "/opt/logs/jobs";
    }

    // Getters and Setters
    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public String getJavaOpts() {
        return javaOpts;
    }

    public void setJavaOpts(String javaOpts) {
        this.javaOpts = javaOpts;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getLogsDir() {
        return logsDir;
    }

    public void setLogsDir(String logsDir) {
        this.logsDir = logsDir;
    }

    public String getJavaCmd() {
        return javaHome + "/bin/java";
    }
}
