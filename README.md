# Job Runner (Java 11+ / Tomcat 10+)

Universal Background Job Runner for Java Web Applications.

**This version is for Java 11+ and Tomcat 10/11 (jakarta.servlet API - Jakarta EE).**

For Tomcat 9 (Java EE), use: [job-runner-8](https://github.com/krisk248/job-runner-8)

A simple web UI to manage and run background Java processes for QA and Dev teams - no SSH required.

## Features

- Web UI for starting/stopping background jobs
- Real-time log viewing
- TOML-based configuration
- Support for multiple applications (classpath sources)
- **Java 11+** compatible (Tomcat 10, 11)

## Quick Start

### Build

```bash
mvn clean package
```

### Deploy

Copy `target/job-runner.war` to your Tomcat webapps folder:

```bash
cp target/job-runner.war /usr/local/tomcat/webapps/
```

### Access

Open in browser: `http://localhost:8080/job-runner/`

## Configuration

### Config File Location

The application looks for configuration in this order:
1. `/opt/config/jobs.toml` (external - recommended)
2. `WEB-INF/jobs.toml` (bundled default)

### Example Configuration

```toml
[global]
java_home = "/opt/java/openjdk"
java_opts = "-Xms256m -Xmx512m"
config_dir = "/opt/config"
logs_dir = "/usr/local/tomcat/logs/jobs"

[apps.myapp]
name = "My Application"
webapp_path = "/usr/local/tomcat/webapps/myapp"

[[jobs]]
id = "my-job"
name = "My Background Job"
app = "myapp"
main_class = "com.example.jobs.MyProcessor"
type = "on-demand"
enabled = true
description = "Description of what this job does"
```

### Job Types

- `continuous` - Long-running jobs (shown in "Start All Continuous")
- `on-demand` - One-time execution jobs

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/jobs | List all jobs |
| GET | /api/jobs/{id} | Get job details |
| POST | /api/jobs/{id}/start | Start a job |
| POST | /api/jobs/{id}/stop | Stop a job |
| GET | /api/jobs/{id}/logs | Get job logs |
| POST | /api/jobs | Create new job |
| PUT | /api/jobs/{id} | Update job |
| DELETE | /api/jobs/{id} | Delete job |
| GET | /api/apps | List applications |
| POST | /api/apps | Create application |
| GET | /api/config | Get configuration |

## Docker Integration

Add to your docker-compose.yml:

```yaml
volumes:
  - ./config/jobs.toml:/opt/config/jobs.toml:ro
  - ./job-runner.war:/usr/local/tomcat/webapps/job-runner.war
```

## Requirements

- **Java 11 or higher**
- **Tomcat 10 or higher** (jakarta.servlet)
- Maven 3.6+ (for building)

## License

MIT
