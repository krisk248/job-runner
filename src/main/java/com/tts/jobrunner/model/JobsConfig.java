package com.tts.jobrunner.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete jobs configuration model.
 * Compatible with Java 8+
 */
public class JobsConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private GlobalConfig global;
    private Map<String, AppConfig> apps;
    private List<Job> jobs;

    public JobsConfig() {
        this.global = new GlobalConfig();
        this.apps = new HashMap<>();
        this.jobs = new ArrayList<>();
    }

    // Getters and Setters
    public GlobalConfig getGlobal() {
        return global;
    }

    public void setGlobal(GlobalConfig global) {
        this.global = global;
    }

    public Map<String, AppConfig> getApps() {
        return apps;
    }

    public void setApps(Map<String, AppConfig> apps) {
        this.apps = apps;
    }

    public void addApp(AppConfig app) {
        this.apps.put(app.getId(), app);
    }

    public AppConfig getApp(String appId) {
        return this.apps.get(appId);
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    public void addJob(Job job) {
        this.jobs.add(job);
    }

    public Job getJob(String jobId) {
        for (Job job : jobs) {
            if (job.getId().equals(jobId)) {
                return job;
            }
        }
        return null;
    }

    public void removeJob(String jobId) {
        jobs.removeIf(job -> job.getId().equals(jobId));
    }

    /**
     * Build complete classpath for a job
     */
    public String buildClasspathForJob(Job job) {
        StringBuilder cp = new StringBuilder();
        String configDir = global.getConfigDir();

        for (String appId : job.getApps()) {
            AppConfig app = apps.get(appId);
            if (app != null) {
                if (cp.length() > 0) {
                    cp.append(java.io.File.pathSeparator);
                }
                cp.append(app.buildClasspath(null)); // Don't add config multiple times
            }
        }

        // Add config dir once at the end
        if (configDir != null && !configDir.isEmpty()) {
            cp.append(java.io.File.pathSeparator).append(configDir);
        }

        return cp.toString();
    }
}
