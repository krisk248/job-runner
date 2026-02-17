package com.tts.jobrunner.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tts.jobrunner.model.*;
import com.tts.jobrunner.service.ConfigManager;
import com.tts.jobrunner.service.JobManager;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST API Servlet for Job Runner.
 * Handles all API endpoints.
 * Compatible with Java 8+
 */
@WebServlet(urlPatterns = {"/api/*"}, loadOnStartup = 1)
public class ApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ApiServlet.class.getName());
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void init() throws ServletException {
        super.init();
        String webappPath = getServletContext().getRealPath("/");
        ConfigManager.getInstance().init(webappPath);
        LOGGER.info("Job Runner API initialized");
    }

    @Override
    public void destroy() {
        JobManager.getInstance().shutdown();
        LOGGER.info("Job Runner API shutdown");
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo.equals("/jobs") || pathInfo.equals("/jobs/")) {
                // GET /api/jobs - List all jobs with status
                handleListJobs(req, resp);

            } else if (pathInfo.matches("/jobs/[^/]+/logs")) {
                // GET /api/jobs/{id}/logs - Get job logs
                String jobId = pathInfo.split("/")[2];
                handleGetLogs(jobId, req, resp);

            } else if (pathInfo.matches("/jobs/[^/]+")) {
                // GET /api/jobs/{id} - Get single job
                String jobId = pathInfo.split("/")[2];
                handleGetJob(jobId, resp);

            } else if (pathInfo.equals("/config") || pathInfo.equals("/config/")) {
                // GET /api/config - Get full config
                handleGetConfig(resp);

            } else if (pathInfo.equals("/apps") || pathInfo.equals("/apps/")) {
                // GET /api/apps - List all apps
                handleListApps(resp);

            } else if (pathInfo.equals("/status") || pathInfo.equals("/status/")) {
                // GET /api/status - System status
                handleStatus(resp);

            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + pathInfo);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling GET " + pathInfo, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo.matches("/jobs/[^/]+/start")) {
                // POST /api/jobs/{id}/start - Start a job (with optional runtime args)
                String jobId = pathInfo.split("/")[2];
                handleStartJob(jobId, req, resp);

            } else if (pathInfo.matches("/jobs/[^/]+/stop")) {
                // POST /api/jobs/{id}/stop - Stop a job
                String jobId = pathInfo.split("/")[2];
                handleStopJob(jobId, resp);

            } else if (pathInfo.matches("/jobs/[^/]+/restart")) {
                // POST /api/jobs/{id}/restart - Restart a job
                String jobId = pathInfo.split("/")[2];
                handleRestartJob(jobId, resp);

            } else if (pathInfo.matches("/jobs/[^/]+/logs/clear")) {
                // POST /api/jobs/{id}/logs/clear - Clear job logs
                String jobId = pathInfo.split("/")[2];
                handleClearLogs(jobId, resp);

            } else if (pathInfo.equals("/jobs") || pathInfo.equals("/jobs/")) {
                // POST /api/jobs - Create new job
                handleCreateJob(req, resp);

            } else if (pathInfo.equals("/apps") || pathInfo.equals("/apps/")) {
                // POST /api/apps - Create new app
                handleCreateApp(req, resp);

            } else if (pathInfo.equals("/config/reload")) {
                // POST /api/config/reload - Reload config
                handleReloadConfig(resp);

            } else if (pathInfo.equals("/jobs/start-all")) {
                // POST /api/jobs/start-all - Start all continuous jobs
                handleStartAllJobs(resp);

            } else if (pathInfo.equals("/jobs/stop-all")) {
                // POST /api/jobs/stop-all - Stop all jobs
                handleStopAllJobs(resp);

            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + pathInfo);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling POST " + pathInfo, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo.matches("/jobs/[^/]+")) {
                // PUT /api/jobs/{id} - Update job
                String jobId = pathInfo.split("/")[2];
                handleUpdateJob(jobId, req, resp);

            } else if (pathInfo.matches("/apps/[^/]+")) {
                // PUT /api/apps/{id} - Update app
                String appId = pathInfo.split("/")[2];
                handleUpdateApp(appId, req, resp);

            } else if (pathInfo.equals("/config/global")) {
                // PUT /api/config/global - Update global config
                handleUpdateGlobalConfig(req, resp);

            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + pathInfo);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling PUT " + pathInfo, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo.matches("/jobs/[^/]+")) {
                // DELETE /api/jobs/{id} - Delete job
                String jobId = pathInfo.split("/")[2];
                handleDeleteJob(jobId, resp);

            } else if (pathInfo.matches("/apps/[^/]+")) {
                // DELETE /api/apps/{id} - Delete app
                String appId = pathInfo.split("/")[2];
                handleDeleteApp(appId, resp);

            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + pathInfo);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling DELETE " + pathInfo, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ==================== Handler Methods ====================

    private void handleListJobs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JobManager jobManager = JobManager.getInstance();
        jobManager.refreshAllJobStatus();

        JobsConfig config = ConfigManager.getInstance().getConfig();
        List<Map<String, Object>> jobList = new ArrayList<>();

        for (Job job : config.getJobs()) {
            Map<String, Object> jobMap = new LinkedHashMap<>();
            jobMap.put("id", job.getId());
            jobMap.put("name", job.getName());
            jobMap.put("apps", job.getApps());
            jobMap.put("mainClass", job.getMainClass());
            jobMap.put("type", job.getType().getValue());
            jobMap.put("enabled", job.isEnabled());
            jobMap.put("status", job.getStatus().getValue());
            jobMap.put("pid", job.getPid());
            jobMap.put("startTime", job.getStartTime());
            jobMap.put("description", job.getDescription());
            jobMap.put("argsRequired", job.isArgsRequired());
            jobMap.put("javaOpts", job.getJavaOpts());
            jobList.add(jobMap);
        }

        sendJson(resp, jobList);
    }

    private void handleGetJob(String jobId, HttpServletResponse resp) throws IOException {
        Job job = ConfigManager.getInstance().getConfig().getJob(jobId);
        if (job == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Job not found: " + jobId);
            return;
        }

        JobManager.getInstance().getJobStatus(jobId); // Refresh status
        sendJson(resp, job);
    }

    private void handleGetLogs(String jobId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String linesParam = req.getParameter("lines");
        int lines = linesParam != null ? Integer.parseInt(linesParam) : 100;

        String logs = JobManager.getInstance().getJobLogs(jobId, lines);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("logs", logs);
        result.put("lines", lines);

        sendJson(resp, result);
    }

    private void handleStartJob(String jobId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Parse optional runtime arguments from request body
        List<String> runtimeArgs = parseRuntimeArgs(req);

        JobManager.JobResult result = JobManager.getInstance().startJob(jobId, runtimeArgs);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("pid", result.getPid());

        resp.setStatus(result.isSuccess() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST);
        sendJson(resp, response);
    }

    private void handleStopJob(String jobId, HttpServletResponse resp) throws IOException {
        JobManager.JobResult result = JobManager.getInstance().stopJob(jobId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());

        resp.setStatus(result.isSuccess() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST);
        sendJson(resp, response);
    }

    private void handleRestartJob(String jobId, HttpServletResponse resp) throws IOException {
        JobManager jobManager = JobManager.getInstance();
        jobManager.stopJob(jobId);

        // Wait a bit for clean shutdown
        try { Thread.sleep(1000); } catch (InterruptedException e) { /* ignore */ }

        JobManager.JobResult result = jobManager.startJob(jobId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", "Restart: " + result.getMessage());
        response.put("pid", result.getPid());

        sendJson(resp, response);
    }

    private void handleClearLogs(String jobId, HttpServletResponse resp) throws IOException {
        JobManager.getInstance().clearJobLogs(jobId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Logs cleared for job: " + jobId);

        sendJson(resp, response);
    }

    private void handleCreateJob(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        Job job = new Job();
        job.setId(json.get("id").getAsString());
        job.setName(json.get("name").getAsString());
        job.setMainClass(json.get("mainClass").getAsString());

        if (json.has("app")) {
            if (json.get("app").isJsonArray()) {
                List<String> apps = new ArrayList<>();
                json.get("app").getAsJsonArray().forEach(e -> apps.add(e.getAsString()));
                job.setApps(apps);
            } else {
                job.setApp(json.get("app").getAsString());
            }
        }

        if (json.has("type")) {
            job.setType(json.get("type").getAsString());
        }
        if (json.has("enabled")) {
            job.setEnabled(json.get("enabled").getAsBoolean());
        }
        if (json.has("description")) {
            job.setDescription(json.get("description").getAsString());
        }
        if (json.has("params")) {
            List<String> params = new ArrayList<>();
            json.get("params").getAsJsonArray().forEach(e -> params.add(e.getAsString()));
            job.setParams(params);
        }
        if (json.has("javaOpts") && !json.get("javaOpts").isJsonNull()) {
            job.setJavaOpts(json.get("javaOpts").getAsString());
        }

        ConfigManager configManager = ConfigManager.getInstance();
        configManager.getConfig().addJob(job);
        configManager.saveConfig();

        resp.setStatus(HttpServletResponse.SC_CREATED);
        sendJson(resp, job);
    }

    private void handleUpdateJob(String jobId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ConfigManager configManager = ConfigManager.getInstance();
        Job job = configManager.getConfig().getJob(jobId);

        if (job == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Job not found: " + jobId);
            return;
        }

        String body = readBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        if (json.has("name")) job.setName(json.get("name").getAsString());
        if (json.has("mainClass")) job.setMainClass(json.get("mainClass").getAsString());
        if (json.has("type")) job.setType(json.get("type").getAsString());
        if (json.has("enabled")) job.setEnabled(json.get("enabled").getAsBoolean());
        if (json.has("description")) job.setDescription(json.get("description").getAsString());
        if (json.has("javaOpts")) {
            String opts = json.get("javaOpts").isJsonNull() ? "" : json.get("javaOpts").getAsString();
            job.setJavaOpts(opts.isEmpty() ? null : opts);
        }

        if (json.has("app")) {
            if (json.get("app").isJsonArray()) {
                List<String> apps = new ArrayList<>();
                json.get("app").getAsJsonArray().forEach(e -> apps.add(e.getAsString()));
                job.setApps(apps);
            } else {
                job.setApp(json.get("app").getAsString());
            }
        }

        configManager.saveConfig();
        sendJson(resp, job);
    }

    private void handleDeleteJob(String jobId, HttpServletResponse resp) throws IOException {
        ConfigManager configManager = ConfigManager.getInstance();

        // Stop job if running
        JobManager.getInstance().stopJob(jobId);

        configManager.getConfig().removeJob(jobId);
        configManager.saveConfig();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Job deleted: " + jobId);

        sendJson(resp, response);
    }

    private void handleListApps(HttpServletResponse resp) throws IOException {
        Map<String, AppConfig> apps = ConfigManager.getInstance().getConfig().getApps();
        sendJson(resp, apps.values());
    }

    private void handleCreateApp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        AppConfig app = new AppConfig();
        app.setId(json.get("id").getAsString());
        app.setName(json.get("name").getAsString());
        app.setWebappPath(json.get("webappPath").getAsString());

        ConfigManager configManager = ConfigManager.getInstance();
        configManager.getConfig().addApp(app);
        configManager.saveConfig();

        resp.setStatus(HttpServletResponse.SC_CREATED);
        sendJson(resp, app);
    }

    private void handleUpdateApp(String appId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ConfigManager configManager = ConfigManager.getInstance();
        AppConfig app = configManager.getConfig().getApp(appId);

        if (app == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "App not found: " + appId);
            return;
        }

        String body = readBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        if (json.has("name")) app.setName(json.get("name").getAsString());
        if (json.has("webappPath")) app.setWebappPath(json.get("webappPath").getAsString());

        configManager.saveConfig();
        sendJson(resp, app);
    }

    private void handleDeleteApp(String appId, HttpServletResponse resp) throws IOException {
        ConfigManager configManager = ConfigManager.getInstance();
        configManager.getConfig().getApps().remove(appId);
        configManager.saveConfig();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "App deleted: " + appId);

        sendJson(resp, response);
    }

    private void handleGetConfig(HttpServletResponse resp) throws IOException {
        sendJson(resp, ConfigManager.getInstance().getConfig());
    }

    private void handleUpdateGlobalConfig(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        ConfigManager configManager = ConfigManager.getInstance();
        GlobalConfig global = configManager.getConfig().getGlobal();

        if (json.has("javaHome")) global.setJavaHome(json.get("javaHome").getAsString());
        if (json.has("javaOpts")) global.setJavaOpts(json.get("javaOpts").getAsString());
        if (json.has("configDir")) global.setConfigDir(json.get("configDir").getAsString());
        if (json.has("logsDir")) global.setLogsDir(json.get("logsDir").getAsString());

        configManager.saveConfig();
        sendJson(resp, global);
    }

    private void handleReloadConfig(HttpServletResponse resp) throws IOException {
        ConfigManager.getInstance().loadConfig();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Configuration reloaded");

        sendJson(resp, response);
    }

    private void handleStartAllJobs(HttpServletResponse resp) throws IOException {
        JobsConfig config = ConfigManager.getInstance().getConfig();
        List<String> started = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Job job : config.getJobs()) {
            if (job.isEnabled() && job.getType() == Job.JobType.CONTINUOUS) {
                JobManager.JobResult result = JobManager.getInstance().startJob(job.getId());
                if (result.isSuccess()) {
                    started.add(job.getId());
                } else {
                    failed.add(job.getId() + ": " + result.getMessage());
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", failed.isEmpty());
        response.put("started", started);
        response.put("failed", failed);

        sendJson(resp, response);
    }

    private void handleStopAllJobs(HttpServletResponse resp) throws IOException {
        JobManager.getInstance().stopAllJobs();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "All jobs stopped");

        sendJson(resp, response);
    }

    private void handleStatus(HttpServletResponse resp) throws IOException {
        JobManager jobManager = JobManager.getInstance();
        jobManager.refreshAllJobStatus();

        JobsConfig config = ConfigManager.getInstance().getConfig();

        int running = 0;
        int stopped = 0;
        int error = 0;

        for (Job job : config.getJobs()) {
            switch (job.getStatus()) {
                case RUNNING: running++; break;
                case STOPPED: stopped++; break;
                case ERROR: error++; break;
            }
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("totalJobs", config.getJobs().size());
        status.put("running", running);
        status.put("stopped", stopped);
        status.put("error", error);
        status.put("totalApps", config.getApps().size());
        status.put("configFile", ConfigManager.getInstance().getConfigFilePath());
        status.put("javaVersion", System.getProperty("java.version"));

        sendJson(resp, status);
    }

    // ==================== Utility Methods ====================

    private void sendJson(HttpServletResponse resp, Object data) throws IOException {
        // Prevent browser caching of API responses
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Expires", "0");

        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(data));
        out.flush();
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", true);
        error.put("message", message);
        sendJson(resp, error);
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Parse runtime arguments from request body.
     * Expects JSON: { "args": ["arg1", "arg2", ...] }
     */
    private List<String> parseRuntimeArgs(HttpServletRequest req) {
        try {
            String body = readBody(req);
            if (body == null || body.trim().isEmpty()) {
                return null;
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("args") && json.get("args").isJsonArray()) {
                List<String> args = new ArrayList<>();
                for (int i = 0; i < json.get("args").getAsJsonArray().size(); i++) {
                    args.add(json.get("args").getAsJsonArray().get(i).getAsString());
                }
                return args.isEmpty() ? null : args;
            }
        } catch (Exception e) {
            LOGGER.fine("Could not parse runtime args: " + e.getMessage());
        }
        return null;
    }
}
