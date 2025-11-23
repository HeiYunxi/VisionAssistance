<template>
  <div class="detection-dashboard">
    <div v-if="!audioEnabled" class="audio-overlay" @click="enableAudio">
      <div class="audio-prompt">
        <h1>🔊 Click to Enable Audio Monitoring</h1>
        <p>Browser policy requires user interaction to play audio alerts</p>
      </div>
    </div>

    <header class="header">
      <div class="header-content">
        <h1>YOLO Vehicle Detection Console</h1>
        <div class="connection-status">
          <div class="status-indicator" :class="{ connected: isConnected }"></div>
          <span>{{ connectionStatus }}</span>
        </div>
      </div>
    </header>

    <main class="main-content">
      <section class="video-section">
        <div class="video-container">
          <img ref="videoStreamRef" :src="streamUrl" alt="Detection Stream" @load="onVideoLoad" @error="onVideoError" />
          <div v-if="!isVideoLoaded" class="video-placeholder">
            <div class="loading-spinner"></div>
            <p>{{ videoStatus }}</p>
          </div>
        </div>
      </section>

      <section class="control-panel">
        <div class="stats-card">
          <h3>🖥️ System Status</h3>
          <div class="system-status">
            <div class="status-item">
              <span class="status-label">Uptime:</span>
              <span class="status-value">{{ formatUptime(systemStatus.uptime) }}</span>
            </div>
          </div>
        </div>

        <div class="stats-card">
          <h3>📊 Real-time Statistics</h3>
          <div class="stats-grid">
            <div class="stat-item">
              <div class="stat-value">{{ stats.vehicleCount }}</div>
              <div class="stat-label">Current Vehicles</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ stats.fps }}</div>
              <div class="stat-label">FPS</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ stats.processingTime }}ms</div>
              <div class="stat-label">Processing Time</div>
            </div>
          </div>

          <div class="alert-status" :class="{
            'level-0': stats.vehicleCount < 2,
            'level-1': stats.vehicleCount >= 2 && stats.vehicleCount <= 7,
            'level-2': stats.vehicleCount > 7
          }">
            <span v-if="stats.vehicleCount > 7">🔴 High Alert: Severe Congestion (>7)</span>
            <span v-else-if="stats.vehicleCount >= 2">🟠 Medium Alert: Increased Traffic (2-7)</span>
            <span v-else>🟢 Normal Traffic (&lt;2)</span>
          </div>
        </div>

        <div class="config-card">
          <h3>⚙️ Detection Parameters</h3>
          <div class="config-item">
            <label>Confidence Threshold: {{ config.confidence }}%</label>
            <input type="range" v-model="config.confidence" min="1" max="100" @change="updateConfig" />
          </div>
          <div class="config-item">
            <label>Area Threshold: {{ config.areaThreshold }}%</label>
            <input type="range" v-model="config.areaThreshold" min="1" max="100" @change="updateConfig" />
          </div>
        </div>

        <div class="history-card">
          <h3>📝 Detection History</h3>
          <div class="history-controls">
            <button @click="clearHistory" class="clear-btn">Clear History</button>
            <span class="history-count">Records: {{ detectionHistory.length }}</span>
          </div>
          <div class="history-list">
            <div v-for="(record, index) in detectionHistory" :key="index" class="history-item">
              <span class="time">{{ record.time }}</span>
              <span class="count">{{ record.count }} Vehicles</span>
              <span class="alert" v-if="record.level === 2">🔴</span>
              <span class="alert" v-else-if="record.level === 1">🟠</span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const API_BASE = 'http://127.0.0.1:5000'

const streamUrl = ref(`${API_BASE}/stream`)
const isConnected = ref(false)
const isVideoLoaded = ref(false)
const videoStatus = ref('Connecting to video stream...')
const connectionStatus = ref('Connecting...')
const audioEnabled = ref(false)

const stats = ref({
  vehicleCount: 0,
  fps: 0,
  processingTime: 0,
  alertLevel: 0
})

const systemStatus = ref({ uptime: 0 })

const config = ref({
  confidence: 25,
  areaThreshold: 30,
  targetWidth: 544,
  targetHeight: 960
})

const detectionHistory = ref([])
const updateTimer = ref(null)
const statusTimer = ref(null)
const retryCount = ref(0)
const maxRetries = 5

// Ensure audio1.mp3, audio2.mp3, audio3.mp3 are in the public root directory
const audioNormal = new Audio('/audio1.mp3')
const audioMid = new Audio('/audio2.mp3')
const audioHigh = new Audio('/audio3.mp3')

audioNormal.loop = true
audioMid.loop = true
audioHigh.loop = true

let lastAlertLevel = -1

// Unlock all audio contexts simultaneously
const enableAudio = async () => {
  try {
    // Play and immediately pause each audio object to unlock browser restrictions
    await audioNormal.play(); audioNormal.pause(); audioNormal.currentTime = 0;
    await audioMid.play();    audioMid.pause();    audioMid.currentTime = 0;
    await audioHigh.play();   audioHigh.pause();   audioHigh.currentTime = 0;

    audioEnabled.value = true;
    console.log("All audio channels unlocked");
    
    // Reset state to trigger immediate evaluation
    lastAlertLevel = -1; 
  } catch (e) {
    console.error("Audio unlock failed, please check file paths:", e);
  }
}

const onVideoLoad = () => {
  isVideoLoaded.value = true
  videoStatus.value = 'Online'
  retryCount.value = 0
}

const onVideoError = () => {
  isVideoLoaded.value = false
  videoStatus.value = 'Stream disconnected, reconnecting...'
  if (retryCount.value < maxRetries) {
    retryCount.value++
    setTimeout(() => {
      streamUrl.value = `${API_BASE}/stream?t=${new Date().getTime()}`
    }, 2000)
  }
}

// Audio alert handler
const handleAudioAlert = (currentLevel) => {
  if (!audioEnabled.value || currentLevel === lastAlertLevel) return

  console.log(`Switching alert level: ${lastAlertLevel} -> ${currentLevel}`);

  // 1. Stop all audio
  audioNormal.pause(); audioNormal.currentTime = 0;
  audioMid.pause();    audioMid.currentTime = 0;
  audioHigh.pause();   audioHigh.currentTime = 0;

  // 2. Play corresponding audio
  try {
    if (currentLevel === 0) {
      console.log("Playing: audioNormal (audio1.mp3)");
      audioNormal.play().catch(e => console.error("Audio1 blocked:", e));
    } else if (currentLevel === 1) {
      console.log("Playing: audioMid (audio2.mp3)");
      audioMid.play().catch(e => console.error("Audio2 blocked:", e));
    } else if (currentLevel === 2) {
      console.log("Playing: audioHigh (audio3.mp3)");
      audioHigh.play().catch(e => console.error("Audio3 blocked:", e));
    }
  } catch (e) {
    console.error("Audio controller error:", e)
  }

  lastAlertLevel = currentLevel
}

const fetchDetectionData = async () => {
  try {
    const response = await fetch(`${API_BASE}/api/detections`)
    if (response.ok) {
      const data = await response.json()

      stats.value.vehicleCount = data.vehicle_count
      stats.value.processingTime = Math.round(data.processing_time)

      // Determine level based on vehicle count
      const count = data.vehicle_count
      let newAlertLevel = 0
      
      if (count > 7) {
        newAlertLevel = 2
      } else if (count >= 2) {
        newAlertLevel = 1
      } else {
        newAlertLevel = 0
      }

      stats.value.alertLevel = newAlertLevel
      handleAudioAlert(newAlertLevel)

      stats.value.fps = data.processing_time > 0 ? Math.round(1000 / data.processing_time) : 0

      isConnected.value = true
      connectionStatus.value = 'System Online'

      if (data.vehicle_count > 0) {
        const lastRecord = detectionHistory.value[0]
        if (!lastRecord || lastRecord.count !== data.vehicle_count || lastRecord.level !== stats.value.alertLevel) {
          addToHistory(data.vehicle_count, stats.value.alertLevel)
        }
      }
    }
  } catch (error) {
    isConnected.value = false
    connectionStatus.value = 'Backend Disconnected'
    console.error('Fetch error:', error)
  }
}

const fetchSystemInfo = async () => {
  try {
    const [statusRes, configRes] = await Promise.all([
      fetch(`${API_BASE}/api/status`),
      fetch(`${API_BASE}/api/config`)
    ])
    if (statusRes.ok) {
      const sData = await statusRes.json()
      systemStatus.value.uptime = sData.uptime
    }
    if (configRes.ok) {
      const cData = await configRes.json()
      if (Math.abs(config.value.confidence - (cData.confidence * 100)) > 5) {
        config.value.confidence = Math.round(cData.confidence * 100)
      }
      if (Math.abs(config.value.areaThreshold - (cData.area_threshold * 100)) > 5) {
        config.value.areaThreshold = Math.round(cData.area_threshold * 100)
      }
    }
  } catch (e) {
    console.log("System info error")
  }
}

const updateConfig = async () => {
  try {
    await fetch(`${API_BASE}/api/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        confidence: config.value.confidence / 100,
        area_threshold: config.value.areaThreshold / 100
      })
    })
  } catch (error) {
    console.error('Config update failed', error)
  }
}

const addToHistory = (count, level) => {
  const now = new Date()
  detectionHistory.value.unshift({
    time: now.toLocaleTimeString(),
    count: count,
    level: level
  })
  if (detectionHistory.value.length > 20) {
    detectionHistory.value = detectionHistory.value.slice(0, 20)
  }
}

const clearHistory = () => {
  detectionHistory.value = []
}

const formatUptime = (seconds) => {
  if (seconds < 60) return `${Math.round(seconds)}s`
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`
  return `${Math.round(seconds / 3600)}h`
}

onMounted(() => {
  fetchSystemInfo()
  fetchDetectionData()
  updateTimer.value = setInterval(fetchDetectionData, 500)
  statusTimer.value = setInterval(fetchSystemInfo, 5000)
})

onUnmounted(() => {
  clearInterval(updateTimer.value)
  clearInterval(statusTimer.value)
  if (audioNormal) audioNormal.pause()
  if (audioMid) audioMid.pause()
  if (audioHigh) audioHigh.pause()
})
</script>

<style scoped>
/* Modern Desktop Dashboard Design 
  Theme: Dark Monitoring Console (Slate/Blue-Grey)
*/

.detection-dashboard {
  height: 100vh;
  width: 100vw;
  background-color: #111827; /* Tailwind Slate-900 */
  color: #e5e7eb; /* Tailwind Slate-200 */
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  overflow: hidden; /* Prevent body scroll */
}

/* Audio Overlay - Modal Style */
.audio-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(8px);
  z-index: 9999;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
}

.audio-prompt {
  background: #1f2937;
  padding: 3rem;
  border-radius: 12px;
  border: 1px solid #374151;
  text-align: center;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
  animation: fadeIn 0.3s ease-out;
}

.audio-prompt h1 {
  font-size: 1.8rem;
  margin-bottom: 0.5rem;
  color: #60a5fa; /* Blue accent */
}

.audio-prompt p {
  color: #9ca3af;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Header - Desktop App Bar */
.header {
  height: 64px;
  background-color: #1f2937; /* Slate-800 */
  border-bottom: 1px solid #374151; /* Slate-700 */
  display: flex;
  align-items: center;
  padding: 0 24px;
  flex-shrink: 0;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.header-content {
  width: 100%;
  max-width: 1800px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header h1 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #f3f4f6;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #111827;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #374151;
  font-size: 0.85rem;
  font-weight: 500;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #ef4444;
  box-shadow: 0 0 8px #ef4444;
  transition: all 0.3s ease;
}

.status-indicator.connected {
  background-color: #10b981;
  box-shadow: 0 0 8px #10b981;
}

/* Main Layout - Grid for Desktop */
.main-content {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 400px; /* Video Area | Control Sidebar */
  gap: 24px;
  padding: 24px;
  max-width: 1800px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden; /* Prevent internal scroll issues */
}

/* Left Column: Video Feed */
.video-section {
  display: flex;
  flex-direction: column;
  height: 100%;
  justify-content: center;
}

.video-container {
  background-color: #000;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  width: 100%;
  height: 100%;
  max-height: calc(100vh - 112px); /* Viewport height - header - padding */
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #374151;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);
}

.video-container img {
  width: 100%;
  height: 100%;
  object-fit: contain; /* Preserve aspect ratio */
}

.video-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #1f2937;
  color: #9ca3af;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #374151;
  border-top: 3px solid #60a5fa;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

/* Right Column: Control Sidebar */
.control-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto; /* Scroll controls if window is short */
  padding-right: 8px; /* Space for scrollbar */
}

/* Scrollbar styling for sidebar */
.control-panel::-webkit-scrollbar {
  width: 6px;
}
.control-panel::-webkit-scrollbar-track {
  background: #111827;
}
.control-panel::-webkit-scrollbar-thumb {
  background: #374151;
  border-radius: 3px;
}
.control-panel::-webkit-scrollbar-thumb:hover {
  background: #4b5563;
}

/* Cards - General Style */
.stats-card,
.config-card,
.history-card {
  background-color: #1f2937;
  border: 1px solid #374151;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

h3 {
  margin: 0 0 16px 0;
  font-size: 0.95rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #9ca3af; /* Muted text */
  font-weight: 600;
  border-bottom: 1px solid #374151;
  padding-bottom: 8px;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-item {
  background: #111827;
  padding: 12px 8px;
  border-radius: 6px;
  text-align: center;
  border: 1px solid #374151;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: #f3f4f6;
  line-height: 1.2;
}

.stat-label {
  font-size: 0.75rem;
  color: #9ca3af;
  margin-top: 4px;
}

/* Alert Status Banner */
.alert-status {
  padding: 12px;
  border-radius: 6px;
  text-align: center;
  font-weight: 600;
  font-size: 0.9rem;
  border-left: 4px solid transparent;
}

.alert-status.level-0 {
  background: rgba(16, 185, 129, 0.1); /* Green tint */
  color: #34d399;
  border-left-color: #34d399;
}

.alert-status.level-1 {
  background: rgba(245, 158, 11, 0.1); /* Orange tint */
  color: #fbbf24;
  border-left-color: #fbbf24;
}

.alert-status.level-2 {
  background: rgba(239, 68, 68, 0.1); /* Red tint */
  color: #f87171;
  border-left-color: #f87171;
  animation: borderPulse 2s infinite;
}

@keyframes borderPulse {
  0% { border-left-color: #f87171; background: rgba(239, 68, 68, 0.1); }
  50% { border-left-color: #ef4444; background: rgba(239, 68, 68, 0.25); }
  100% { border-left-color: #f87171; background: rgba(239, 68, 68, 0.1); }
}

/* Config Section */
.config-item {
  margin-bottom: 16px;
}

.config-item:last-child {
  margin-bottom: 0;
}

.config-item label {
  display: block;
  margin-bottom: 8px;
  font-size: 0.85rem;
  color: #d1d5db;
}

/* Custom Range Input Style */
input[type="range"] {
  width: 100%;
  height: 6px;
  background: #374151;
  border-radius: 3px;
  outline: none;
  -webkit-appearance: none;
}

input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #60a5fa; /* Blue thumb */
  cursor: pointer;
  transition: background 0.2s;
}

input[type="range"]::-webkit-slider-thumb:hover {
  background: #3b82f6;
}

/* History Section */
.history-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 0.8rem;
  color: #9ca3af;
}

.clear-btn {
  background: transparent;
  border: 1px solid #4b5563;
  color: #9ca3af;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: #374151;
  color: #fff;
  border-color: #6b7280;
}

.history-list {
  max-height: 250px;
  overflow-y: auto;
  padding-right: 4px;
}

/* Scrollbar for history list */
.history-list::-webkit-scrollbar {
  width: 4px;
}
.history-list::-webkit-scrollbar-track {
  background: transparent;
}
.history-list::-webkit-scrollbar-thumb {
  background: #4b5563;
  border-radius: 2px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-bottom: 1px solid #374151;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.history-item:last-child {
  border-bottom: none;
}

.history-item:hover {
  background: #111827;
}

.history-item .time {
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
}

.history-item .count {
  font-weight: 600;
  color: #e5e7eb;
}

/* System Status (System Status Card content) */
.system-status {
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
}

.status-value {
  font-family: monospace;
  color: #10b981;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Responsive adjustments for smaller laptop screens */
@media (max-width: 1280px) {
  .main-content {
    grid-template-columns: 1fr 320px; /* Shrink sidebar slightly */
  }
}

/* Fallback for very small screens/tablets */
@media (max-width: 900px) {
  .main-content {
    grid-template-columns: 1fr;
    overflow-y: auto;
    height: auto;
  }
  .detection-dashboard {
    height: auto;
    overflow: auto;
  }
  .video-container {
    max-height: 50vh;
  }
}
</style>