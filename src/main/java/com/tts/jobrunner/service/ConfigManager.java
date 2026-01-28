package com.tts.jobrunner.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import com.tts.jobrunner.model.*;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration manager - loads/saves jobs.toml
 * Compatible with Java 8+
 */
public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private static final String CONFIG_FILE_NAME = "jobs.toml";
    private static final String EXTERNAL_CONFIG_PATH = "/opt/config/" + CONFIG_FILE_NAME;

    private static ConfigManager instance;
    private JobsConfig config;
    private String configFilePath;
    private final Gson gson;

    private ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Initialize and load configuration
     */
    public void init(String webappPath) {
        // Try external config first, then fall back to WEB-INF
        File externalConfig = new File(EXTERNAL_CONFIG_PATH);
        File webappConfig = new File(webappPath + "/WEB-INF/" + CONFIG_FILE_NAME);

        if (externalConfig.exists()) {
            configFilePath = EXTERNAL_CONFIG_PATH;
            LOGGER.info("Using external config: " + configFilePath);
        } else if (webappConfig.exists()) {
            configFilePath = webappConfig.getAbsolutePath();
            LOGGER.info("Using webapp config: " + configFilePath);
        } else {
            // Create default config
            configFilePath = EXTERNAL_CONFIG_PATH;
            config = createDefaultConfig();
            saveConfig();
            LOGGER.info("Created default config: " + configFilePath);
            return;
        }

        loadConfig();
    }

    /**
     * Load configuration from TOML file
     */
    public void loadConfig() {
        try {
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                LOGGER.warning("Config file not found: " + configFilePath);
                config = createDefaultConfig();
                return;
            }

            Toml toml = new Toml().read(configFile);
            config = new JobsConfig();

            // Parse global section
            Toml globalToml = toml.getTable("global");
            if (globalToml != null) {
                GlobalConfig global = new GlobalConfig();
                global.setJavaHome(globalToml.getString("java_home", global.getJavaHome()));
                global.setJavaOpts(globalToml.getString("java_opts", global.getJavaOpts()));
                global.setConfigDir(globalToml.getString("config_dir", global.getConfigDir()));
                global.setLogsDir(globalToml.getString("logs_dir", global.getLogsDir()));
                config.setGlobal(global);
            }

            // Parse apps section
            Toml appsToml = toml.getTable("apps");
            if (appsToml != null) {
                Map<String, Object> appsMap = appsToml.toMap();
                for (String appId : appsMap.keySet()) {
                    Toml appToml = appsToml.getTable(appId);
                    if (appToml != null) {
                        AppConfig app = new AppConfig();
                        app.setId(appId);
                        app.setName(appToml.getString("name", appId));
                        app.setWebappPath(appToml.getString("webapp_path", ""));
                        config.addApp(app);
                    }
                }
            }

            // Parse jobs array
            List<Toml> jobsToml = toml.getTables("jobs");
            if (jobsToml != null) {
                for (Toml jobToml : jobsToml) {
                    Job job = new Job();
                    job.setId(jobToml.getString("id"));
                    job.setName(jobToml.getString("name"));
                    job.setMainClass(jobToml.getString("main_class"));
                    job.setType(jobToml.getString("type", "on-demand"));
                    job.setEnabled(jobToml.getBoolean("enabled", true));
                    job.setDescription(jobToml.getString("description", ""));

                    // Handle app (string or array)
                    Object appObj = jobToml.toMap().get("app");
                    if (appObj instanceof String) {
                        job.setApp((String) appObj);
                    } else if (appObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> appList = (List<String>) appObj;
                        job.setApps(appList);
                    }

                    // Handle params
                    List<String> params = jobToml.getList("params");
                    if (params != null) {
                        job.setParams(params);
                    }

                    // Handle args_required flag
                    job.setArgsRequired(jobToml.getBoolean("args_required", false));

                    config.addJob(job);
                }
            }

            LOGGER.info("Loaded " + config.getJobs().size() + " jobs from config");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading config", e);
            config = createDefaultConfig();
        }
    }

    /**
     * Save configuration to TOML file
     */
    public void saveConfig() {
        try {
            File configFile = new File(configFilePath);
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Build TOML content manually for better formatting
            StringBuilder sb = new StringBuilder();
            sb.append("# Job Runner Configuration\n");
            sb.append("# Generated by Job Runner UI\n\n");

            // Global section
            GlobalConfig global = config.getGlobal();
            sb.append("[global]\n");
            sb.append("java_home = \"").append(escapeToml(global.getJavaHome())).append("\"\n");
            sb.append("java_opts = \"").append(escapeToml(global.getJavaOpts())).append("\"\n");
            sb.append("config_dir = \"").append(escapeToml(global.getConfigDir())).append("\"\n");
            sb.append("logs_dir = \"").append(escapeToml(global.getLogsDir())).append("\"\n\n");

            // Apps section
            for (AppConfig app : config.getApps().values()) {
                sb.append("[apps.").append(app.getId()).append("]\n");
                sb.append("name = \"").append(escapeToml(app.getName())).append("\"\n");
                sb.append("webapp_path = \"").append(escapeToml(app.getWebappPath())).append("\"\n\n");
            }

            // Jobs section
            for (Job job : config.getJobs()) {
                sb.append("[[jobs]]\n");
                sb.append("id = \"").append(escapeToml(job.getId())).append("\"\n");
                sb.append("name = \"").append(escapeToml(job.getName())).append("\"\n");

                // App (string or array)
                List<String> apps = job.getApps();
                if (apps.size() == 1) {
                    sb.append("app = \"").append(escapeToml(apps.get(0))).append("\"\n");
                } else {
                    sb.append("app = [");
                    for (int i = 0; i < apps.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append("\"").append(escapeToml(apps.get(i))).append("\"");
                    }
                    sb.append("]\n");
                }

                sb.append("main_class = \"").append(escapeToml(job.getMainClass())).append("\"\n");
                sb.append("type = \"").append(job.getType().getValue()).append("\"\n");
                sb.append("enabled = ").append(job.isEnabled()).append("\n");

                if (job.getDescription() != null && !job.getDescription().isEmpty()) {
                    sb.append("description = \"").append(escapeToml(job.getDescription())).append("\"\n");
                }

                if (job.getParams() != null && !job.getParams().isEmpty()) {
                    sb.append("params = [");
                    for (int i = 0; i < job.getParams().size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append("\"").append(escapeToml(job.getParams().get(i))).append("\"");
                    }
                    sb.append("]\n");
                }

                // Write args_required if true
                if (job.isArgsRequired()) {
                    sb.append("args_required = true\n");
                }

                sb.append("\n");
            }

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(sb.toString());
            }

            LOGGER.info("Saved config to: " + configFilePath);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving config", e);
        }
    }

    private String escapeToml(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Create default configuration
     */
    private JobsConfig createDefaultConfig() {
        JobsConfig config = new JobsConfig();

        // Default global config
        GlobalConfig global = new GlobalConfig();
        global.setJavaHome("/opt/java/openjdk");
        global.setJavaOpts("-Xms256m -Xmx512m");
        global.setConfigDir("/opt/config");
        global.setLogsDir("/usr/local/tomcat/logs/jobs");
        config.setGlobal(global);

        // Default app
        AppConfig adxsip = new AppConfig("adxsip", "ADXSIP", "/usr/local/tomcat/webapps/ADXSIP");
        config.addApp(adxsip);

        return config;
    }

    public JobsConfig getConfig() {
        return config;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public String toJson() {
        return gson.toJson(config);
    }
}
