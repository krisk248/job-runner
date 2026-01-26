/**
 * Job Runner - Frontend JavaScript
 * Compatible with modern browsers (ES6+)
 * Note: This is an internal admin tool - all data comes from our own API
 */

const API_BASE = 'api';
let jobs = [];
let apps = {};
let globalConfig = {};
let autoRefreshInterval = null;

// ==================== Initialization ====================

document.addEventListener('DOMContentLoaded', () => {
    loadStatus();
    loadJobs();
    loadApps();
    loadGlobalConfig();
});

// ==================== API Functions ====================

async function apiCall(endpoint, method = 'GET', data = null) {
    const options = {
        method,
        headers: {
            'Content-Type': 'application/json'
        }
    };

    if (data) {
        options.body = JSON.stringify(data);
    }

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, options);
        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message || 'API Error');
        }

        return result;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// ==================== Jobs Management ====================

async function loadJobs() {
    try {
        jobs = await apiCall('/jobs');
        renderJobsTable();
        updateJobSelect();
        updateRunningCount();
    } catch (error) {
        showToast('Failed to load jobs: ' + error.message, 'error');
    }
}

function renderJobsTable() {
    const tbody = document.getElementById('jobsTableBody');
    // Clear existing content safely
    while (tbody.firstChild) {
        tbody.removeChild(tbody.firstChild);
    }

    if (jobs.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.style.textAlign = 'center';
        td.style.padding = '40px';
        td.style.color = 'var(--text-muted)';
        td.textContent = 'No jobs configured. Click "Add Job" to create one.';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }

    jobs.forEach(job => {
        const tr = document.createElement('tr');

        // Name cell
        const nameTd = document.createElement('td');
        const nameStrong = document.createElement('strong');
        nameStrong.textContent = job.name;
        nameTd.appendChild(nameStrong);
        if (job.description) {
            const descBr = document.createElement('br');
            const descSmall = document.createElement('small');
            descSmall.style.color = 'var(--text-muted)';
            descSmall.textContent = job.description;
            nameTd.appendChild(descBr);
            nameTd.appendChild(descSmall);
        }
        tr.appendChild(nameTd);

        // Apps cell
        const appsTd = document.createElement('td');
        job.apps.forEach(appName => {
            const badge = document.createElement('span');
            badge.className = 'type-badge';
            badge.textContent = appName;
            appsTd.appendChild(badge);
            appsTd.appendChild(document.createTextNode(' '));
        });
        tr.appendChild(appsTd);

        // Type cell
        const typeTd = document.createElement('td');
        const typeBadge = document.createElement('span');
        typeBadge.className = 'type-badge';
        typeBadge.textContent = job.type;
        typeTd.appendChild(typeBadge);
        tr.appendChild(typeTd);

        // Status cell
        const statusTd = document.createElement('td');
        const statusSpan = document.createElement('span');
        statusSpan.className = 'status status-' + job.status;
        const dot = document.createElement('span');
        dot.className = 'status-dot';
        dot.style.width = '6px';
        dot.style.height = '6px';
        dot.style.background = 'currentColor';
        dot.style.borderRadius = '50%';
        statusSpan.appendChild(dot);
        statusSpan.appendChild(document.createTextNode(' ' + job.status));
        if (job.pid) {
            statusSpan.appendChild(document.createTextNode(' (PID: ' + job.pid + ')'));
        }
        statusTd.appendChild(statusSpan);
        tr.appendChild(statusTd);

        // Actions cell
        const actionsTd = document.createElement('td');
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'action-buttons';

        // Start/Stop button
        const toggleBtn = document.createElement('button');
        toggleBtn.className = job.status === 'running' ? 'action-btn stop' : 'action-btn start';
        toggleBtn.textContent = job.status === 'running' ? 'Stop' : 'Start';
        toggleBtn.disabled = !job.enabled;
        toggleBtn.onclick = () => job.status === 'running' ? stopJob(job.id) : startJob(job.id);
        actionsDiv.appendChild(toggleBtn);

        // Logs button
        const logsBtn = document.createElement('button');
        logsBtn.className = 'action-btn';
        logsBtn.textContent = 'Logs';
        logsBtn.onclick = () => viewJobLogs(job.id);
        actionsDiv.appendChild(logsBtn);

        // Edit button
        const editBtn = document.createElement('button');
        editBtn.className = 'action-btn';
        editBtn.textContent = 'Edit';
        editBtn.onclick = () => editJob(job.id);
        actionsDiv.appendChild(editBtn);

        // Delete button
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'action-btn';
        deleteBtn.style.color = 'var(--danger-color)';
        deleteBtn.textContent = 'Delete';
        deleteBtn.onclick = () => deleteJob(job.id);
        actionsDiv.appendChild(deleteBtn);

        actionsTd.appendChild(actionsDiv);
        tr.appendChild(actionsTd);

        tbody.appendChild(tr);
    });
}

async function startJob(jobId) {
    try {
        const result = await apiCall(`/jobs/${jobId}/start`, 'POST');
        showToast(result.message, result.success ? 'success' : 'error');
        loadJobs();
    } catch (error) {
        showToast('Failed to start job: ' + error.message, 'error');
    }
}

async function stopJob(jobId) {
    try {
        const result = await apiCall(`/jobs/${jobId}/stop`, 'POST');
        showToast(result.message, result.success ? 'success' : 'error');
        loadJobs();
    } catch (error) {
        showToast('Failed to stop job: ' + error.message, 'error');
    }
}

async function startAllJobs() {
    try {
        const result = await apiCall('/jobs/start-all', 'POST');
        if (result.started.length > 0) {
            showToast('Started ' + result.started.length + ' jobs', 'success');
        }
        if (result.failed.length > 0) {
            showToast('Failed to start: ' + result.failed.join(', '), 'error');
        }
        loadJobs();
    } catch (error) {
        showToast('Failed to start jobs: ' + error.message, 'error');
    }
}

async function stopAllJobs() {
    if (!confirm('Are you sure you want to stop all running jobs?')) return;

    try {
        await apiCall('/jobs/stop-all', 'POST');
        showToast('All jobs stopped', 'success');
        loadJobs();
    } catch (error) {
        showToast('Failed to stop jobs: ' + error.message, 'error');
    }
}

function showAddJobModal() {
    document.getElementById('jobModalTitle').textContent = 'Add Job';
    document.getElementById('jobForm').reset();
    document.getElementById('jobId').value = '';
    document.getElementById('jobEnabled').checked = true;
    populateAppSelect();
    openModal('jobModal');
}

function editJob(jobId) {
    const job = jobs.find(j => j.id === jobId);
    if (!job) return;

    document.getElementById('jobModalTitle').textContent = 'Edit Job';
    document.getElementById('jobId').value = job.id;
    document.getElementById('jobName').value = job.name;
    document.getElementById('jobMainClass').value = job.mainClass;
    document.getElementById('jobType').value = job.type;
    document.getElementById('jobDescription').value = job.description || '';
    document.getElementById('jobEnabled').checked = job.enabled;

    populateAppSelect();
    document.getElementById('jobApp').value = job.apps[0] || '';

    openModal('jobModal');
}

async function saveJob(event) {
    event.preventDefault();

    const jobId = document.getElementById('jobId').value;
    const isNew = !jobId;

    const data = {
        id: isNew ? document.getElementById('jobName').value.toLowerCase().replace(/\s+/g, '-') : jobId,
        name: document.getElementById('jobName').value,
        app: document.getElementById('jobApp').value,
        mainClass: document.getElementById('jobMainClass').value,
        type: document.getElementById('jobType').value,
        description: document.getElementById('jobDescription').value,
        enabled: document.getElementById('jobEnabled').checked
    };

    try {
        if (isNew) {
            await apiCall('/jobs', 'POST', data);
            showToast('Job created successfully', 'success');
        } else {
            await apiCall('/jobs/' + jobId, 'PUT', data);
            showToast('Job updated successfully', 'success');
        }
        closeModal('jobModal');
        loadJobs();
    } catch (error) {
        showToast('Failed to save job: ' + error.message, 'error');
    }
}

async function deleteJob(jobId) {
    if (!confirm('Are you sure you want to delete this job?')) return;

    try {
        await apiCall('/jobs/' + jobId, 'DELETE');
        showToast('Job deleted', 'success');
        loadJobs();
    } catch (error) {
        showToast('Failed to delete job: ' + error.message, 'error');
    }
}

// ==================== Logs Management ====================

function updateJobSelect() {
    const select = document.getElementById('logJobSelect');
    const currentValue = select.value;

    // Clear existing options
    while (select.options.length > 1) {
        select.remove(1);
    }

    jobs.forEach(job => {
        const option = document.createElement('option');
        option.value = job.id;
        option.textContent = job.name;
        select.appendChild(option);
    });

    if (currentValue) {
        select.value = currentValue;
    }
}

function viewJobLogs(jobId) {
    showTab('logs');
    document.getElementById('logJobSelect').value = jobId;
    loadJobLogs();
}

async function loadJobLogs() {
    const jobId = document.getElementById('logJobSelect').value;
    const output = document.getElementById('logsOutput');

    if (!jobId) {
        output.textContent = 'Select a job to view its logs...';
        return;
    }

    try {
        const result = await apiCall('/jobs/' + jobId + '/logs?lines=200');
        output.textContent = result.logs || 'No logs available.';
        output.scrollTop = output.scrollHeight;
    } catch (error) {
        output.textContent = 'Failed to load logs: ' + error.message;
    }
}

async function clearJobLogs() {
    const jobId = document.getElementById('logJobSelect').value;
    if (!jobId) return;

    try {
        await apiCall('/jobs/' + jobId + '/logs/clear', 'POST');
        showToast('Logs cleared', 'success');
        loadJobLogs();
    } catch (error) {
        showToast('Failed to clear logs: ' + error.message, 'error');
    }
}

function toggleAutoRefresh() {
    const checkbox = document.getElementById('autoRefreshLogs');

    if (checkbox.checked) {
        autoRefreshInterval = setInterval(loadJobLogs, 3000);
    } else if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
        autoRefreshInterval = null;
    }
}

// ==================== Apps Management ====================

async function loadApps() {
    try {
        const appsList = await apiCall('/apps');
        apps = {};
        appsList.forEach(app => {
            apps[app.id] = app;
        });
        renderApps();
        populateAppSelect();
    } catch (error) {
        showToast('Failed to load apps: ' + error.message, 'error');
    }
}

function renderApps() {
    const container = document.getElementById('appsContainer');

    // Clear existing content
    while (container.firstChild) {
        container.removeChild(container.firstChild);
    }

    const appKeys = Object.keys(apps);
    if (appKeys.length === 0) {
        const p = document.createElement('p');
        p.style.color = 'var(--text-muted)';
        p.textContent = 'No applications configured.';
        container.appendChild(p);
        return;
    }

    appKeys.forEach(appId => {
        const app = apps[appId];
        const div = document.createElement('div');
        div.className = 'app-card';

        const infoDiv = document.createElement('div');
        infoDiv.className = 'app-info';

        const nameDiv = document.createElement('div');
        nameDiv.className = 'app-name';
        nameDiv.textContent = app.name + ' ';
        const idSmall = document.createElement('small');
        idSmall.style.color = 'var(--text-muted)';
        idSmall.textContent = '(' + app.id + ')';
        nameDiv.appendChild(idSmall);
        infoDiv.appendChild(nameDiv);

        const pathDiv = document.createElement('div');
        pathDiv.className = 'app-path';
        pathDiv.textContent = app.webappPath;
        infoDiv.appendChild(pathDiv);

        div.appendChild(infoDiv);

        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'action-buttons';

        const editBtn = document.createElement('button');
        editBtn.className = 'action-btn';
        editBtn.textContent = 'Edit';
        editBtn.onclick = () => editApp(app.id);
        actionsDiv.appendChild(editBtn);

        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'action-btn';
        deleteBtn.style.color = 'var(--danger-color)';
        deleteBtn.textContent = 'Delete';
        deleteBtn.onclick = () => deleteApp(app.id);
        actionsDiv.appendChild(deleteBtn);

        div.appendChild(actionsDiv);
        container.appendChild(div);
    });
}

function populateAppSelect() {
    const select = document.getElementById('jobApp');

    // Clear existing options
    while (select.firstChild) {
        select.removeChild(select.firstChild);
    }

    Object.values(apps).forEach(app => {
        const option = document.createElement('option');
        option.value = app.id;
        option.textContent = app.name;
        select.appendChild(option);
    });
}

function showAddAppModal() {
    document.getElementById('appModalTitle').textContent = 'Add Application';
    document.getElementById('appForm').reset();
    document.getElementById('appIdField').value = '';
    document.getElementById('appIdInput').disabled = false;
    openModal('appModal');
}

function editApp(appId) {
    const app = apps[appId];
    if (!app) return;

    document.getElementById('appModalTitle').textContent = 'Edit Application';
    document.getElementById('appIdField').value = app.id;
    document.getElementById('appIdInput').value = app.id;
    document.getElementById('appIdInput').disabled = true;
    document.getElementById('appDisplayName').value = app.name;
    document.getElementById('appWebappPath').value = app.webappPath;

    openModal('appModal');
}

async function saveApp(event) {
    event.preventDefault();

    const existingId = document.getElementById('appIdField').value;
    const isNew = !existingId;

    const data = {
        id: document.getElementById('appIdInput').value,
        name: document.getElementById('appDisplayName').value,
        webappPath: document.getElementById('appWebappPath').value
    };

    try {
        if (isNew) {
            await apiCall('/apps', 'POST', data);
            showToast('Application added', 'success');
        } else {
            await apiCall('/apps/' + existingId, 'PUT', data);
            showToast('Application updated', 'success');
        }
        closeModal('appModal');
        loadApps();
    } catch (error) {
        showToast('Failed to save application: ' + error.message, 'error');
    }
}

async function deleteApp(appId) {
    if (!confirm('Are you sure you want to delete this application?')) return;

    try {
        await apiCall('/apps/' + appId, 'DELETE');
        showToast('Application deleted', 'success');
        loadApps();
    } catch (error) {
        showToast('Failed to delete application: ' + error.message, 'error');
    }
}

// ==================== Settings Management ====================

async function loadGlobalConfig() {
    try {
        const config = await apiCall('/config');
        globalConfig = config.global;

        document.getElementById('javaHome').value = globalConfig.javaHome || '';
        document.getElementById('javaOpts').value = globalConfig.javaOpts || '';
        document.getElementById('configDir').value = globalConfig.configDir || '';
        document.getElementById('logsDir').value = globalConfig.logsDir || '';

    } catch (error) {
        showToast('Failed to load config: ' + error.message, 'error');
    }
}

async function saveGlobalSettings(event) {
    event.preventDefault();

    const data = {
        javaHome: document.getElementById('javaHome').value,
        javaOpts: document.getElementById('javaOpts').value,
        configDir: document.getElementById('configDir').value,
        logsDir: document.getElementById('logsDir').value
    };

    try {
        await apiCall('/config/global', 'PUT', data);
        showToast('Global settings saved', 'success');
    } catch (error) {
        showToast('Failed to save settings: ' + error.message, 'error');
    }
}

async function reloadConfig() {
    try {
        await apiCall('/config/reload', 'POST');
        showToast('Configuration reloaded', 'success');
        loadJobs();
        loadApps();
        loadGlobalConfig();
    } catch (error) {
        showToast('Failed to reload config: ' + error.message, 'error');
    }
}

// ==================== Status ====================

async function loadStatus() {
    try {
        const status = await apiCall('/status');
        document.getElementById('configFilePath').textContent = status.configFile;
        updateRunningCount(status.running);
    } catch (error) {
        console.error('Failed to load status:', error);
    }
}

function updateRunningCount(count) {
    if (count === undefined) {
        count = jobs.filter(j => j.status === 'running').length;
    }
    document.getElementById('runningCount').textContent = count;
}

// ==================== UI Helpers ====================

function showTab(tabName) {
    // Update tab buttons
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.tab === tabName);
    });

    // Update content
    document.getElementById('jobsTab').classList.toggle('hidden', tabName !== 'jobs');
    document.getElementById('logsTab').classList.toggle('hidden', tabName !== 'logs');
    document.getElementById('settingsTab').classList.toggle('hidden', tabName !== 'settings');

    // Clear auto-refresh when leaving logs tab
    if (tabName !== 'logs' && autoRefreshInterval) {
        document.getElementById('autoRefreshLogs').checked = false;
        clearInterval(autoRefreshInterval);
        autoRefreshInterval = null;
    }
}

function openModal(modalId) {
    document.getElementById(modalId).classList.add('show');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('show');
}

function refreshJobs() {
    loadJobs();
    loadStatus();
}

function showSettings() {
    showTab('settings');
}

function showToast(message, type) {
    type = type || 'info';
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = 'toast ' + type;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(function() {
        toast.remove();
    }, 4000);
}

// Close modals on outside click
document.addEventListener('click', function(e) {
    if (e.target.classList.contains('modal')) {
        e.target.classList.remove('show');
    }
});

// Keyboard shortcuts
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal.show').forEach(function(modal) {
            modal.classList.remove('show');
        });
    }
});
