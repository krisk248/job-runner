package com.tts.jobrunner.service;

import com.tts.jobrunner.model.*;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Job execution manager - runs and stops background jobs.
 * Compatible with Java 8+
 */
public class JobManager {
    private static final Logger LOGGER = Logger.getLogger(JobManager.class.getName());
    private static JobManager instance;

    private final Map<String, Process> runningProcesses;
    private final Map<String, StringBuilder> jobLogs;
    private final Map<String, Thread> logReaderThreads;
    private final ExecutorService executorService;
    private final int maxLogLines = 1000;

    private JobManager() {
        this.runningProcesses = new ConcurrentHashMap<>();
        this.jobLogs = new ConcurrentHashMap<>();
        this.logReaderThreads = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();

        // Register shutdown hook to kill all processes when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("JVM shutdown detected, stopping all jobs...");
            for (String jobId : new ArrayList<>(runningProcesses.keySet())) {
                try {
                    Process process = runningProcesses.get(jobId);
                    if (process != null && process.isAlive()) {
                        LOGGER.info("Killing job process: " + jobId);
                        process.destroyForcibly();
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error killing job " + jobId + ": " + e.getMessage());
                }
            }
        }, "JobRunner-ShutdownHook"));
    }

    public static synchronized JobManager getInstance() {
        if (instance == null) {
            instance = new JobManager();
        }
        return instance;
    }

    /**
     * Start a job (without runtime arguments)
     */
    public synchronized JobResult startJob(String jobId) {
        return startJob(jobId, null);
    }

    /**
     * Start a job with optional runtime arguments
     * @param jobId The job ID to start
     * @param runtimeArgs Optional runtime arguments (passed after configured params)
     */
    public synchronized JobResult startJob(String jobId, List<String> runtimeArgs) {
        ConfigManager configManager = ConfigManager.getInstance();
        JobsConfig config = configManager.getConfig();
        Job job = config.getJob(jobId);

        if (job == null) {
            return new JobResult(false, "Job not found: " + jobId);
        }

        if (!job.isEnabled()) {
            return new JobResult(false, "Job is disabled: " + jobId);
        }

        if (runningProcesses.containsKey(jobId)) {
            return new JobResult(false, "Job is already running: " + jobId);
        }

        try {
            // Build command with optional runtime args
            List<String> command = buildCommand(job, config, runtimeArgs);
            LOGGER.info("Starting job: " + jobId + " with command: " + String.join(" ", command));

            // Create process builder
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            // Set working directory to logs dir
            File logsDir = new File(config.getGlobal().getLogsDir());
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            pb.directory(logsDir);

            // Set environment
            Map<String, String> env = pb.environment();
            env.put("JAVA_HOME", config.getGlobal().getJavaHome());

            // Start process
            Process process = pb.start();
            runningProcesses.put(jobId, process);

            // Initialize log buffer
            jobLogs.put(jobId, new StringBuilder());

            // Start log reader thread
            startLogReader(jobId, process);

            // Update job status
            job.setStatus(Job.JobStatus.RUNNING);
            job.setPid(getPid(process));
            job.setStartTime(System.currentTimeMillis());

            LOGGER.info("Job started: " + jobId + " (PID: " + job.getPid() + ")");
            return new JobResult(true, "Job started successfully", job.getPid());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting job: " + jobId, e);
            job.setStatus(Job.JobStatus.ERROR);
            return new JobResult(false, "Error starting job: " + e.getMessage());
        }
    }

    /**
     * Stop a job
     */
    public synchronized JobResult stopJob(String jobId) {
        ConfigManager configManager = ConfigManager.getInstance();
        Job job = configManager.getConfig().getJob(jobId);

        if (job == null) {
            return new JobResult(false, "Job not found: " + jobId);
        }

        Process process = runningProcesses.get(jobId);
        if (process == null) {
            job.setStatus(Job.JobStatus.STOPPED);
            return new JobResult(false, "Job is not running: " + jobId);
        }

        try {
            // Try graceful shutdown first
            process.destroy();

            // Wait a bit for graceful shutdown
            boolean terminated = process.waitFor(5, TimeUnit.SECONDS);

            if (!terminated) {
                // Force kill
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }

            // Cleanup
            runningProcesses.remove(jobId);
            Thread logThread = logReaderThreads.remove(jobId);
            if (logThread != null) {
                logThread.interrupt();
            }

            // Update job status
            job.setStatus(Job.JobStatus.STOPPED);
            job.setPid(null);
            job.setStartTime(null);

            LOGGER.info("Job stopped: " + jobId);
            return new JobResult(true, "Job stopped successfully");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping job: " + jobId, e);
            return new JobResult(false, "Error stopping job: " + e.getMessage());
        }
    }

    /**
     * Get job status
     */
    public Job.JobStatus getJobStatus(String jobId) {
        Process process = runningProcesses.get(jobId);
        if (process == null) {
            return Job.JobStatus.STOPPED;
        }

        if (process.isAlive()) {
            return Job.JobStatus.RUNNING;
        } else {
            // Process ended, cleanup
            runningProcesses.remove(jobId);
            ConfigManager configManager = ConfigManager.getInstance();
            Job job = configManager.getConfig().getJob(jobId);
            if (job != null) {
                int exitCode = process.exitValue();
                job.setStatus(exitCode == 0 ? Job.JobStatus.STOPPED : Job.JobStatus.ERROR);
                job.setPid(null);
            }
            return Job.JobStatus.STOPPED;
        }
    }

    /**
     * Get job logs
     */
    public String getJobLogs(String jobId, int lastNLines) {
        StringBuilder logs = jobLogs.get(jobId);
        if (logs == null) {
            // Try to read from log file
            return readLogFile(jobId, lastNLines);
        }

        String logContent = logs.toString();
        if (lastNLines > 0) {
            String[] lines = logContent.split("\n");
            int start = Math.max(0, lines.length - lastNLines);
            StringBuilder result = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                result.append(lines[i]).append("\n");
            }
            return result.toString();
        }

        return logContent;
    }

    /**
     * Clear job logs
     */
    public void clearJobLogs(String jobId) {
        StringBuilder logs = jobLogs.get(jobId);
        if (logs != null) {
            logs.setLength(0);
        }
    }

    /**
     * Check all running jobs and update status
     */
    public void refreshAllJobStatus() {
        ConfigManager configManager = ConfigManager.getInstance();
        for (Job job : configManager.getConfig().getJobs()) {
            Job.JobStatus status = getJobStatus(job.getId());
            job.setStatus(status);
        }
    }

    /**
     * Stop all running jobs
     */
    public void stopAllJobs() {
        for (String jobId : new ArrayList<>(runningProcesses.keySet())) {
            stopJob(jobId);
        }
    }

    /**
     * Shutdown the job manager
     */
    public void shutdown() {
        stopAllJobs();
        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    // ==================== Private Methods ====================

    private List<String> buildCommand(Job job, JobsConfig config, List<String> runtimeArgs) {
        List<String> command = new ArrayList<>();

        // Java executable
        command.add(config.getGlobal().getJavaCmd());

        // Global JVM options
        String javaOpts = config.getGlobal().getJavaOpts();
        if (javaOpts != null && !javaOpts.isEmpty()) {
            for (String opt : javaOpts.split("\\s+")) {
                if (!opt.isEmpty()) {
                    command.add(opt);
                }
            }
        }

        // Per-job JVM options (appended after global)
        String jobJavaOpts = job.getJavaOpts();
        if (jobJavaOpts != null && !jobJavaOpts.isEmpty()) {
            for (String opt : jobJavaOpts.split("\\s+")) {
                if (!opt.isEmpty()) {
                    command.add(opt);
                }
            }
        }

        // Classpath
        String classpath = config.buildClasspathForJob(job);
        command.add("-classpath");
        command.add(classpath);

        // Main class
        command.add(job.getMainClass());

        // Configured parameters (from jobs.toml)
        if (job.getParams() != null) {
            command.addAll(job.getParams());
        }

        // Runtime arguments (from UI input, added after configured params)
        if (runtimeArgs != null && !runtimeArgs.isEmpty()) {
            command.addAll(runtimeArgs);
        }

        return command;
    }

    private void startLogReader(String jobId, Process process) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder logs = jobLogs.get(jobId);
                File logFile = getLogFile(jobId);

                try (PrintWriter fileWriter = new PrintWriter(new FileWriter(logFile, true))) {
                    while ((line = reader.readLine()) != null) {
                        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(new java.util.Date());
                        String logLine = timestamp + " " + line + "\n";

                        // Write to file
                        fileWriter.print(logLine);
                        fileWriter.flush();

                        // Keep in memory (with limit)
                        if (logs != null) {
                            synchronized (logs) {
                                logs.append(logLine);
                                // Trim if too large
                                if (logs.length() > 500000) {
                                    logs.delete(0, 100000);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    LOGGER.log(Level.WARNING, "Error reading job output: " + jobId, e);
                }
            }
        }, "JobLogReader-" + jobId);

        thread.setDaemon(true);
        thread.start();
        logReaderThreads.put(jobId, thread);
    }

    private File getLogFile(String jobId) {
        ConfigManager configManager = ConfigManager.getInstance();
        String logsDir = configManager.getConfig().getGlobal().getLogsDir();
        return new File(logsDir, jobId + ".log");
    }

    private String readLogFile(String jobId, int lastNLines) {
        File logFile = getLogFile(jobId);
        if (!logFile.exists()) {
            return "";
        }

        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            int start = Math.max(0, lines.size() - lastNLines);
            StringBuilder result = new StringBuilder();
            for (int i = start; i < lines.size(); i++) {
                result.append(lines.get(i)).append("\n");
            }
            return result.toString();

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading log file: " + logFile, e);
            return "";
        }
    }

    /**
     * Get PID of process (Java 8 compatible)
     */
    private Long getPid(Process process) {
        try {
            // Try Java 9+ method first
            Method pidMethod = process.getClass().getMethod("pid");
            return (Long) pidMethod.invoke(process);
        } catch (Exception e) {
            // Fall back to reflection for Java 8
            try {
                if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
                    Field pidField = process.getClass().getDeclaredField("pid");
                    pidField.setAccessible(true);
                    return ((Integer) pidField.get(process)).longValue();
                }
            } catch (Exception ex) {
                // Ignore
            }
        }
        return null;
    }

    // ==================== Result Class ====================

    public static class JobResult {
        private final boolean success;
        private final String message;
        private final Long pid;

        public JobResult(boolean success, String message) {
            this(success, message, null);
        }

        public JobResult(boolean success, String message, Long pid) {
            this.success = success;
            this.message = message;
            this.pid = pid;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Long getPid() {
            return pid;
        }
    }
}
