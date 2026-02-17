package com.tts.jobrunner.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Job definition model.
 * Compatible with Java 8+
 */
public class Job implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum JobType {
        CONTINUOUS("continuous"),
        ON_DEMAND("on-demand");

        private final String value;

        JobType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static JobType fromString(String text) {
            for (JobType type : JobType.values()) {
                if (type.value.equalsIgnoreCase(text)) {
                    return type;
                }
            }
            return ON_DEMAND;
        }
    }

    public enum JobStatus {
        STOPPED("stopped"),
        RUNNING("running"),
        ERROR("error");

        private final String value;

        JobStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private String id;
    private String name;
    private List<String> apps;  // Can be single app or multiple
    private String mainClass;
    private JobType type;
    private boolean enabled;
    private List<String> params;
    private String description;
    private boolean argsRequired;  // If true, show args modal on start
    private String javaOpts;  // Per-job JVM options (appended after global java_opts)

    // Runtime state (not persisted)
    private transient JobStatus status = JobStatus.STOPPED;
    private transient Long pid;
    private transient Long startTime;

    public Job() {
        this.apps = new ArrayList<>();
        this.params = new ArrayList<>();
        this.type = JobType.ON_DEMAND;
        this.enabled = true;
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

    public List<String> getApps() {
        return apps;
    }

    public void setApps(List<String> apps) {
        this.apps = apps;
    }

    public void setApp(String app) {
        this.apps = new ArrayList<>();
        this.apps.add(app);
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType type) {
        this.type = type;
    }

    public void setType(String type) {
        this.type = JobType.fromString(type);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getParams() {
        return params;
    }

    public void setParams(List<String> params) {
        this.params = params;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArgsRequired() {
        return argsRequired;
    }

    public void setArgsRequired(boolean argsRequired) {
        this.argsRequired = argsRequired;
    }

    public String getJavaOpts() {
        return javaOpts;
    }

    public void setJavaOpts(String javaOpts) {
        this.javaOpts = javaOpts;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public boolean isRunning() {
        return status == JobStatus.RUNNING;
    }
}
