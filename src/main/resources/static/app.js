const BASE_UPLOAD_PART_BYTES = 64 * 1024;
const GAUGE_SWEEP_DEGREES = 330;
const SYSTEM_METRICS_REFRESH_MS = 2_000;
const scriptUrl = document.currentScript && document.currentScript.src ? document.currentScript.src : document.baseURI;
const APP_BASE_URL = new URL('.', scriptUrl);

const state = {
    config: null,
    running: false,
    metricsPollerId: null
};

const ui = {
    startButton: document.getElementById('startButton'),
    durationHint: document.getElementById('durationHint'),
    errorMessage: document.getElementById('errorMessage'),
    phaseValue: document.getElementById('phaseValue'),
    phaseHint: document.getElementById('phaseHint'),
    pingValue: document.getElementById('pingValue'),
    pingHint: document.getElementById('pingHint'),
    downloadValue: document.getElementById('downloadValue'),
    downloadHint: document.getElementById('downloadHint'),
    uploadValue: document.getElementById('uploadValue'),
    uploadHint: document.getElementById('uploadHint'),
    cpuGauge: document.getElementById('cpuGauge'),
    cpuValue: document.getElementById('cpuValue'),
    cpuDetail: document.getElementById('cpuDetail'),
    cpuHint: document.getElementById('cpuHint'),
    memoryGauge: document.getElementById('memoryGauge'),
    memoryValue: document.getElementById('memoryValue'),
    memoryDetail: document.getElementById('memoryDetail'),
    memoryHint: document.getElementById('memoryHint'),
    diskGauge: document.getElementById('diskGauge'),
    diskValue: document.getElementById('diskValue'),
    diskDetail: document.getElementById('diskDetail'),
    diskHint: document.getElementById('diskHint'),
    progressBar: document.getElementById('progressBar'),
    progressText: document.getElementById('progressText'),
    latestPingSample: document.getElementById('latestPingSample'),
    minPingValue: document.getElementById('minPingValue'),
    jitterValue: document.getElementById('jitterValue'),
    downloadBytes: document.getElementById('downloadBytes'),
    uploadBytes: document.getElementById('uploadBytes'),
    updatedAt: document.getElementById('updatedAt')
};

const uploadSeed = createRandomBytes(BASE_UPLOAD_PART_BYTES);

document.addEventListener('DOMContentLoaded', async () => {
    ui.startButton.addEventListener('click', runSpeedTest);
    await Promise.allSettled([loadConfig(), refreshSystemMetrics()]);
    startSystemMetricsPolling();
    resetView();
});

async function loadConfig() {
    try {
        const response = await fetch(appUrl('api/speedtest/config', {t: Date.now()}), {cache: 'no-store'});
        if (!response.ok) {
            throw new Error('Unable to load speed test configuration.');
        }

        state.config = await response.json();
        ui.durationHint.textContent = `About ${state.config.estimatedTotalDurationSeconds}s total.`;
    } catch (error) {
        showError(error.message);
    }
}

async function runSpeedTest() {
    if (state.running || !state.config) {
        return;
    }

    state.running = true;
    ui.startButton.disabled = true;
    ui.startButton.textContent = 'Running…';
    hideError();
    resetView();

    const startedAt = performance.now();
    const totalMs = state.config.estimatedTotalDurationSeconds * 1000;

    try {
        const ping = await runPingPhase(startedAt, totalMs);
        renderPingSummary(ping);

        const download = await runDownloadPhase(startedAt, totalMs);
        renderTransferSummary('download', download);

        const upload = await runUploadPhase(startedAt, totalMs);
        renderTransferSummary('upload', upload);

        setPhase('Complete', 'Test finished successfully.');
        setProgress(100);
        ui.updatedAt.textContent = new Date().toLocaleTimeString();
    } catch (error) {
        setPhase('Error', 'The test did not complete.');
        showError(error.message || 'The speed test failed.');
    } finally {
        state.running = false;
        ui.startButton.disabled = false;
        ui.startButton.textContent = 'Start test';
    }
}

function startSystemMetricsPolling() {
    if (state.metricsPollerId !== null) {
        window.clearInterval(state.metricsPollerId);
    }

    state.metricsPollerId = window.setInterval(() => {
        void refreshSystemMetrics(true);
    }, SYSTEM_METRICS_REFRESH_MS);
}

async function refreshSystemMetrics(silent = false) {
    try {
        const response = await fetch(appUrl('api/system-metrics', {t: Date.now(), r: Math.random()}), {
            cache: 'no-store'
        });

        if (!response.ok) {
            throw new Error(`System metrics request returned ${response.status}.`);
        }

        const metrics = await response.json();
        renderSystemMetrics(metrics);
    } catch (error) {
        renderSystemMetricsUnavailable(silent ? 'Metrics unavailable' : error.message);
    }
}

async function runPingPhase(startedAt, totalMs) {
    const config = state.config.ping;
    const totalSamples = config.warmupSamples + config.measuredSamples;
    const measurements = [];

    for (let index = 0; index < totalSamples; index += 1) {
        setPhase('Ping', `Sample ${index + 1} of ${totalSamples}`);

        const requestStarted = performance.now();
        const response = await fetch(appUrl('api/speedtest/ping', {t: Date.now(), r: Math.random()}), {
            cache: 'no-store'
        });

        if (!response.ok) {
            throw new Error('Ping request failed.');
        }

        const latency = performance.now() - requestStarted;
        if (index >= config.warmupSamples) {
            measurements.push(latency);
        }

        ui.latestPingSample.textContent = formatMilliseconds(latency);
        if (measurements.length > 0) {
            renderPingSummary(calculatePingStats(measurements));
        }

        setProgress(((performance.now() - startedAt) / totalMs) * 100);
        ui.updatedAt.textContent = new Date().toLocaleTimeString();

        if (index < totalSamples - 1) {
            await sleep(config.intervalMillis);
        }
    }

    return calculatePingStats(measurements);
}

async function runDownloadPhase(startedAt, totalMs) {
    const durationSeconds = state.config.download.durationSeconds;
    setPhase('Download', `Streaming for about ${durationSeconds}s.`);

    const response = await fetch(appUrl('api/speedtest/download', {seconds: durationSeconds, t: Date.now(), r: Math.random()}), {
        cache: 'no-store'
    });

    if (!response.ok || !response.body) {
        throw new Error('Download test failed to start.');
    }

    const reader = response.body.getReader();
    const started = performance.now();
    let receivedBytes = 0;
    let lastUiRefresh = started;

    while (true) {
        const {done, value} = await reader.read();
        if (done) {
            break;
        }

        receivedBytes += value.byteLength;
        const now = performance.now();
        if (now - lastUiRefresh >= 200) {
            renderTransferLive('download', receivedBytes, now - started);
            setProgress(((now - startedAt) / totalMs) * 100);
            lastUiRefresh = now;
        }
    }

    const elapsedMs = performance.now() - started;
    renderTransferLive('download', receivedBytes, elapsedMs);
    ui.updatedAt.textContent = new Date().toLocaleTimeString();

    return {
        bytes: receivedBytes,
        elapsedMs,
        mbps: bytesToMbps(receivedBytes, elapsedMs)
    };
}

async function runUploadPhase(startedAt, totalMs) {
    const config = state.config.upload;
    const durationMs = config.durationSeconds * 1000;
    const phaseStarted = performance.now();
    let uploadedBytes = 0;
    let nextChunkBytes = config.initialChunkBytes;

    setPhase('Upload', `Uploading for about ${config.durationSeconds}s.`);

    while (uploadedBytes === 0 || (performance.now() - phaseStarted) < durationMs) {
        const chunkSize = resolveNextChunkSize(uploadedBytes, performance.now() - phaseStarted, nextChunkBytes, config);
        await uploadChunk(chunkSize, (loaded) => {
            const elapsedMs = performance.now() - phaseStarted;
            renderTransferLive('upload', uploadedBytes + loaded, elapsedMs);
            setProgress(((performance.now() - startedAt) / totalMs) * 100);
        });

        uploadedBytes += chunkSize;
        const elapsedMs = performance.now() - phaseStarted;
        renderTransferLive('upload', uploadedBytes, elapsedMs);
        ui.updatedAt.textContent = new Date().toLocaleTimeString();
        nextChunkBytes = resolveNextChunkSize(uploadedBytes, elapsedMs, nextChunkBytes, config);
    }

    const elapsedMs = performance.now() - phaseStarted;
    return {
        bytes: uploadedBytes,
        elapsedMs,
        mbps: bytesToMbps(uploadedBytes, elapsedMs)
    };
}

function uploadChunk(chunkSize, onProgress) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        const payload = buildUploadBlob(chunkSize);

        xhr.open('POST', appUrl('api/speedtest/upload', {t: Date.now(), r: Math.random()}));
        xhr.timeout = 60_000;
        xhr.responseType = 'json';
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        xhr.setRequestHeader('Cache-Control', 'no-store');

        xhr.upload.onprogress = (event) => {
            onProgress(Math.min(event.loaded, chunkSize));
        };

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve(xhr.response);
                return;
            }

            reject(new Error(`Upload request returned ${xhr.status}.`));
        };

        xhr.onerror = () => reject(new Error('Upload request failed.'));
        xhr.ontimeout = () => reject(new Error('Upload request timed out.'));
        xhr.onabort = () => reject(new Error('Upload request was aborted.'));
        xhr.send(payload);
    });
}

function appUrl(path, params = {}) {
    const normalizedPath = path.replace(/^\/+/, '');
    const url = new URL(normalizedPath, APP_BASE_URL);
    Object.entries(params).forEach(([name, value]) => {
        if (value !== undefined && value !== null) {
            url.searchParams.set(name, String(value));
        }
    });
    return url.href;
}

function resolveNextChunkSize(uploadedBytes, elapsedMs, fallbackBytes, config) {
    if (uploadedBytes === 0 || elapsedMs <= 0) {
        return alignChunkSize(fallbackBytes, config);
    }

    const bytesPerMs = uploadedBytes / elapsedMs;
    const adaptiveTarget = bytesPerMs * config.adaptiveTargetMillis;
    if (!Number.isFinite(adaptiveTarget) || adaptiveTarget <= 0) {
        return alignChunkSize(fallbackBytes, config);
    }

    return alignChunkSize(adaptiveTarget, config);
}

function alignChunkSize(chunkBytes, config) {
    const clamped = clamp(chunkBytes, config.minChunkBytes, config.maxChunkBytes);
    return Math.max(
        BASE_UPLOAD_PART_BYTES,
        Math.round(clamped / BASE_UPLOAD_PART_BYTES) * BASE_UPLOAD_PART_BYTES
    );
}

function renderPingSummary(result) {
    ui.pingValue.textContent = formatMilliseconds(result.median);
    ui.pingHint.textContent = 'Median HTTP RTT';
    ui.minPingValue.textContent = formatMilliseconds(result.minimum);
    ui.jitterValue.textContent = formatMilliseconds(result.jitter);
}

function renderTransferLive(kind, bytes, elapsedMs) {
    const speed = bytesToMbps(bytes, elapsedMs);
    if (kind === 'download') {
        ui.downloadValue.textContent = formatMbps(speed);
        ui.downloadHint.textContent = 'Live Mbps';
        ui.downloadBytes.textContent = formatBytes(bytes);
    } else {
        ui.uploadValue.textContent = formatMbps(speed);
        ui.uploadHint.textContent = 'Live Mbps';
        ui.uploadBytes.textContent = formatBytes(bytes);
    }
}

function renderTransferSummary(kind, result) {
    renderTransferLive(kind, result.bytes, result.elapsedMs);
    if (kind === 'download') {
        ui.downloadHint.textContent = `Average over ${(result.elapsedMs / 1000).toFixed(1)}s`;
    } else {
        ui.uploadHint.textContent = `Average over ${(result.elapsedMs / 1000).toFixed(1)}s`;
    }
}

function renderSystemMetrics(metrics) {
    if (typeof metrics.cpuUsagePercent === 'number') {
        const cpuPercent = clamp(metrics.cpuUsagePercent, 0, 100);
        setGaugePercentage(ui.cpuGauge, cpuPercent);
        ui.cpuValue.textContent = formatPercent(cpuPercent, 1);
        ui.cpuDetail.textContent = 'Across all cores';
        ui.cpuHint.textContent = 'Current server CPU usage';
    } else {
        renderGaugeUnavailable(
            ui.cpuGauge,
            ui.cpuValue,
            ui.cpuDetail,
            ui.cpuHint,
            'Server CPU usage',
            'Metric unavailable'
        );
    }

    if (typeof metrics.memoryUsedBytes === 'number' && typeof metrics.memoryTotalBytes === 'number' && metrics.memoryTotalBytes > 0) {
        const memoryPercent = calculateUsagePercent(metrics.memoryUsedBytes, metrics.memoryTotalBytes);
        setGaugePercentage(ui.memoryGauge, memoryPercent);
        ui.memoryValue.textContent = formatPercent(memoryPercent, 0);
        ui.memoryDetail.textContent = 'Used RAM';
        ui.memoryHint.textContent = `${formatMiB(metrics.memoryUsedBytes)} / ${formatMiB(metrics.memoryTotalBytes)}`;
    } else {
        renderGaugeUnavailable(
            ui.memoryGauge,
            ui.memoryValue,
            ui.memoryDetail,
            ui.memoryHint,
            'Used RAM',
            'Metric unavailable'
        );
    }

    if (typeof metrics.diskUsedBytes === 'number' && typeof metrics.diskTotalBytes === 'number' && metrics.diskTotalBytes > 0) {
        const diskPath = metrics.diskPath || '/';
        const diskPercent = calculateUsagePercent(metrics.diskUsedBytes, metrics.diskTotalBytes);
        setGaugePercentage(ui.diskGauge, diskPercent);
        ui.diskValue.textContent = formatPercent(diskPercent, 0);
        ui.diskDetail.textContent = diskPath;
        ui.diskHint.textContent = `${formatGiB(metrics.diskUsedBytes)} / ${formatGiB(metrics.diskTotalBytes)}`;
    } else {
        renderGaugeUnavailable(
            ui.diskGauge,
            ui.diskValue,
            ui.diskDetail,
            ui.diskHint,
            'Root filesystem',
            'Metric unavailable'
        );
    }
}

function renderSystemMetricsUnavailable(message) {
    renderGaugeUnavailable(ui.cpuGauge, ui.cpuValue, ui.cpuDetail, ui.cpuHint, 'Server CPU usage', message);
    renderGaugeUnavailable(ui.memoryGauge, ui.memoryValue, ui.memoryDetail, ui.memoryHint, 'Used RAM', message);
    renderGaugeUnavailable(ui.diskGauge, ui.diskValue, ui.diskDetail, ui.diskHint, 'Root filesystem', message);
}

function setGaugePercentage(gaugeElement, percent) {
    const normalized = clamp(percent, 0, 100);
    const fillSweep = (normalized / 100) * GAUGE_SWEEP_DEGREES;
    gaugeElement.style.setProperty('--fill-sweep', `${fillSweep}deg`);
    gaugeElement.classList.remove('is-unavailable');
}

function renderGaugeUnavailable(gaugeElement, valueElement, detailElement, summaryElement, detailText, summaryText) {
    gaugeElement.style.setProperty('--fill-sweep', '0deg');
    gaugeElement.classList.add('is-unavailable');
    valueElement.textContent = '—';
    detailElement.textContent = detailText;
    summaryElement.textContent = summaryText;
}

function calculatePingStats(samples) {
    const sorted = [...samples].sort((left, right) => left - right);
    const average = samples.reduce((sum, value) => sum + value, 0) / samples.length;
    const variance = samples.reduce((sum, value) => sum + ((value - average) ** 2), 0) / samples.length;
    const midpoint = Math.floor(sorted.length / 2);
    const median = sorted.length % 2 === 0
        ? (sorted[midpoint - 1] + sorted[midpoint]) / 2
        : sorted[midpoint];

    return {
        minimum: sorted[0],
        median,
        jitter: Math.sqrt(variance)
    };
}

function setPhase(title, hint) {
    ui.phaseValue.textContent = title;
    ui.phaseHint.textContent = hint;
}

function setProgress(value) {
    const normalized = clamp(value, 0, 100);
    ui.progressBar.style.width = `${normalized.toFixed(1)}%`;
    ui.progressText.textContent = `${Math.round(normalized)}%`;
}

function resetView() {
    setPhase('Idle', 'Ready to run');
    setProgress(0);
    ui.pingValue.textContent = '—';
    ui.pingHint.textContent = 'Median HTTP RTT';
    ui.downloadValue.textContent = '—';
    ui.downloadHint.textContent = 'Average throughput';
    ui.uploadValue.textContent = '—';
    ui.uploadHint.textContent = 'Average throughput';
    ui.latestPingSample.textContent = '—';
    ui.minPingValue.textContent = '—';
    ui.jitterValue.textContent = '—';
    ui.downloadBytes.textContent = '—';
    ui.uploadBytes.textContent = '—';
    ui.updatedAt.textContent = 'Never';
}

function showError(message) {
    ui.errorMessage.hidden = false;
    ui.errorMessage.textContent = message;
}

function hideError() {
    ui.errorMessage.hidden = true;
    ui.errorMessage.textContent = '';
}

function createRandomBytes(size) {
    const bytes = new Uint8Array(size);
    for (let offset = 0; offset < size; offset += 65_536) {
        crypto.getRandomValues(bytes.subarray(offset, Math.min(offset + 65_536, size)));
    }
    return bytes;
}

function buildUploadBlob(size) {
    const parts = [];
    let remaining = size;
    while (remaining > 0) {
        const sliceLength = Math.min(remaining, uploadSeed.byteLength);
        parts.push(uploadSeed.subarray(0, sliceLength));
        remaining -= sliceLength;
    }
    return new Blob(parts, {type: 'application/octet-stream'});
}

function bytesToMbps(bytes, elapsedMs) {
    if (!elapsedMs || elapsedMs <= 0) {
        return 0;
    }
    return (bytes * 8) / (elapsedMs / 1000) / 1_000_000;
}

function formatMilliseconds(value) {
    return `${value.toFixed(1)} ms`;
}

function formatMbps(value) {
    return `${value.toFixed(2)} Mbps`;
}

function formatPercent(value, digits = 0) {
    return `${value.toFixed(digits)}%`;
}

function formatMiB(bytes) {
    return `${Math.round(bytes / (1024 * 1024)).toLocaleString()} MiB`;
}

function formatGiB(bytes) {
    return `${Math.round(bytes / (1024 * 1024 * 1024)).toLocaleString()} GiB`;
}

function formatBytes(bytes) {
    if (!bytes && bytes !== 0) {
        return '—';
    }

    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
    }
    return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function formatUsedTotal(usedBytes, totalBytes) {
    return `${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}`;
}

function calculateUsagePercent(usedBytes, totalBytes) {
    if (!totalBytes || totalBytes <= 0) {
        return 0;
    }
    return (usedBytes / totalBytes) * 100;
}

function clamp(value, minimum, maximum) {
    return Math.max(minimum, Math.min(value, maximum));
}

function sleep(milliseconds) {
    return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}
