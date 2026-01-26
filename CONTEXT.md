# Job Runner - Project Context & Memory

## Overview

**Job Runner** is a universal background job management web application for Java web applications. It provides a web UI to start, stop, and monitor background Java processes without requiring SSH access - ideal for QA and Dev teams.

## Repository Information

| Item | Value |
|------|-------|
| **Repository** | https://github.com/krisk248/job-runner |
| **Version** | Java 11+ / Tomcat 10+ (Jakarta EE) |
| **Servlet API** | `jakarta.servlet` |
| **Related Repo** | [job-runner-8](https://github.com/krisk248/job-runner-8) (Java 8 / Tomcat 9) |

## Architecture

```
job-runner/
├── src/main/java/com/tts/jobrunner/
│   ├── model/           # Data models (Job, AppConfig, GlobalConfig, JobsConfig)
│   ├── service/         # Business logic (JobManager, ConfigManager)
│   ├── servlet/         # REST API (ApiServlet) + CORS filter
│   └── util/            # TOML parser utility
├── src/main/webapp/
│   ├── index.html       # Single-page web UI
│   ├── css/app.css      # Styling
│   ├── js/app.js        # Frontend JavaScript (polling, API calls)
│   └── WEB-INF/
│       ├── web.xml      # Servlet configuration
│       └── jobs.toml    # Default bundled config
└── pom.xml              # Maven build (Java 11, jakarta.servlet 6.0)
```

## Key Components

### 1. JobManager (service/JobManager.java)
- Manages job lifecycle (start/stop/restart)
- Uses `ProcessBuilder` to spawn Java processes
- Builds classpath from webapp's `WEB-INF/classes` + `WEB-INF/lib/*.jar`
- Tracks running processes with PID
- Handles log file management

### 2. ConfigManager (service/ConfigManager.java)
- Loads configuration from TOML files
- Priority: `/opt/config/jobs.toml` → `WEB-INF/jobs.toml`
- Supports runtime config updates and persistence

### 3. ApiServlet (servlet/ApiServlet.java)
- REST API endpoints for all operations
- No-cache headers prevent browser caching issues
- CORS support via CORSFilter

### 4. Frontend (webapp/js/app.js)
- Auto-polling every 5 seconds for status updates
- Cache-busting fetch requests
- Toast notifications for user feedback

## Configuration (jobs.toml)

```toml
[global]
java_home = "/opt/java/openjdk"
java_opts = "-Xms256m -Xmx512m"
config_dir = "/opt/config"
logs_dir = "/usr/local/tomcat/logs/jobs"

[apps.adxsip]
name = "ADXSIP"
webapp_path = "/usr/local/tomcat/webapps/ADXSIP"

[[jobs]]
id = "notification"
name = "Notification Processor"
app = "adxsip"
main_class = "com.ttsme.sip.jobs.notifications.NotificationSender"
type = "continuous"
enabled = true
description = "Sends general SIP notifications"
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/jobs | List all jobs with status |
| GET | /api/jobs/{id} | Get single job details |
| POST | /api/jobs/{id}/start | Start a job |
| POST | /api/jobs/{id}/stop | Stop a job |
| GET | /api/jobs/{id}/logs | Get job logs |
| POST | /api/jobs | Create new job |
| PUT | /api/jobs/{id} | Update job |
| DELETE | /api/jobs/{id} | Delete job |
| GET | /api/apps | List applications |
| POST | /api/apps | Create application |
| GET | /api/config | Get configuration |
| GET | /api/status | System status |

## Deployment Scenarios

### 1. Docker (Tomcat 11 + JDK 17)

```yaml
# docker-compose.yml
services:
  app:
    image: tomcat:11.0-jdk17-temurin
    volumes:
      - ./webapps:/usr/local/tomcat/webapps
      - ./config:/opt/config:ro
      - ./logs:/usr/local/tomcat/logs
```

Files needed:
- `webapps/job-runner.war`
- `config/jobs.toml`

### 2. Windows Legacy (Tomcat 10+ / JDK 17)

Paths (using forward slashes in TOML):
```toml
[global]
java_home = "C:/TTS/Java/jdk-17"
config_dir = "C:/TTS/REManagement/UAE/SIP-NEW/config"
logs_dir = "C:/TTS/REManagement/UAE/SIP-NEW/Tomcat-9993/logs/jobs"

[apps.adxsip]
webapp_path = "C:/TTS/REManagement/UAE/SIP-NEW/Tomcat-9993/webapps/ADXSIP"
```

### 3. Linux Server

Standard paths:
- WAR: `/usr/local/tomcat/webapps/job-runner.war`
- Config: `/opt/config/jobs.toml`
- Logs: `/usr/local/tomcat/logs/jobs/`

## Build & Release

### Local Build
```bash
# Requires Java 11+
mvn clean package
# Output: target/job-runner.war
```

### GitHub Actions
- Triggers on push to `main` or tags `v*`
- Builds with JDK 17
- Creates GitHub Release on version tags

## Key Decisions & History

### Jakarta EE Migration (2026-01)
- **Problem**: Original code used `javax.servlet` but Tomcat 10+ requires `jakarta.servlet`
- **Solution**: Created two repositories:
  - `job-runner` - Jakarta EE (Tomcat 10+)
  - `job-runner-8` - Java EE (Tomcat 9)

### Auto-Polling Fix (2026-01)
- **Problem**: Job status didn't auto-update when jobs finished
- **Solution**: Added 5-second polling in `app.js` + no-cache headers on server

### Class Path Discovery (2026-01)
- **Problem**: `ClassNotFoundException` for job classes
- **Solution**: Found actual class paths in ADXSIP webapp using `find` command
- **Correct paths**: `com.ttsme.sip.dda.bg.*`, `com.ttsme.sip.common.bg.processor.*`

## ADXSIP Integration

Job Runner is designed to run background processes from the ADXSIP webapp. Key job classes:

| Job Type | Package | Examples |
|----------|---------|----------|
| Notifications | `com.ttsme.sip.jobs.notifications` | NotificationSender, AdminNotificationSender |
| DDA Processing | `com.ttsme.sip.dda.bg` | DDARegistrationProcessor, DDAPaymentProcessor |
| Data Loading | `com.ttsme.sip.common.bg.processor` | LoadBrokerData, LoadINAVFeedData |
| Trade Processing | `com.ttsme.sip.broker.process` | TradeExecutionFileProcessor |

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| "Filter failed to start" | Wrong servlet API version | Use correct WAR for your Tomcat version |
| ClassNotFoundException | Wrong main_class in jobs.toml | Verify class exists in webapp's WEB-INF/classes |
| Status not updating | Browser cache | Hard refresh (Ctrl+F5) or check auto-polling |
| Jobs show "running" forever | Process ended but status not refreshed | API should auto-detect via `Process.isAlive()` |

## Related Files

- `config-examples/windows-legacy-jobs.toml` - Windows configuration template
- `.github/workflows/build.yml` - CI/CD pipeline

## Contact & Maintenance

- **Owner**: krisk248
- **Created**: January 2026
- **Purpose**: QA/Dev tool for ADXSIP background job management
