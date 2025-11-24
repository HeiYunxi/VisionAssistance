<template>
    <header class="header">
        <div class="header-content">
            <button @click="navigateToYolo" class="nav-button">
                Navigate To Yolo
            </button>
            <h1>AI Vision Assistant</h1>
            <div class="header-actions">

            </div>
        </div>
    </header>

    <div class="page">
        <el-row :gutter="24">
            <!-- left chat -->
            <el-col :span="6" class="chatcol">
                <div class="chatblock">
                    <el-container>
                        <div v-if="chatSession && !isEmpty(chatSession.data)" class="v3ai__chatbot" ref="scrollRef"
                            @scroll="onScroll">
                            <div class="v3ai__chatbot-sessions">
                                <!-- Chat history would go here -->
                            </div>
                        </div>

                        <div v-else class="v3ai__chatbot-intro">
                            <i class="logo iconfont ai-deepseek"></i>
                            <h3 class="name"><span class="txt text-gradient">Hi~ </span></h3>
                            <p class="desc">Welcome~</p>
                            <!-- 显示识别到的文字 -->
                            <p class="desc transcript-text">{{ transcript }}</p>
                        </div>

                        <!-- controls -->
                        <el-footer>
                            <div class="controls">
                                <el-button @click="toggleRecording" :class="{ 'recording': isRecording }" type="primary">
                                    {{ isRecording ? 'Stop Recording' : 'Start Recording' }}
                                </el-button>
                            </div>
                        </el-footer>
                    </el-container>
                </div>
            </el-col>

            <!-- right -->
            <el-col :span="18">
                <!-- video and result display -->
                <el-row class="videoRow" :gutter="24">
                    <!-- inputvideo (left) - 使用Python后端视频流 -->
                    <el-col :span="12">
                        <div class="media-container">
                            <img ref="videoStream" :src="streamUrl" alt="Camera Stream" @load="onVideoLoad" @error="onVideoError" />
                            
                            <!-- 视频加载状态提示 -->
                            <div v-if="!isVideoLoaded" class="video-placeholder">
                                <div class="loading-spinner"></div>
                                <p>{{ videoStatus }}</p>
                            </div>
                        </div>
                    </el-col>
                    <!-- result display (right) -->
                    <el-col :span="12">
                        <div class="media-container">
                            <!-- 显示截图的img -->
                            <img v-show="!isProcessing && resultImageUrl" :src="resultImageUrl" alt="Result Image" />
                            
                            <!-- 初始占位提示 -->
                            <div v-show="!isProcessing && !resultImageUrl" class="result-placeholder">
                                <i class="placeholder-icon">📸</i>
                                <p>Start recording to capture image</p>
                            </div>
                            
                            <!-- 加载状态覆盖层 -->
                            <div v-if="isProcessing" class="processing-overlay">
                                <el-icon class="is-loading"><Loading /></el-icon>
                                <span>Processing...</span>
                            </div>
                        </div>
                    </el-col>
                </el-row>
            </el-col>
        </el-row>
    </div>
    
    <!-- 播放后端返回的音频的隐藏元素 -->
    <audio ref="audioPlayer" @ended="onAudioPlayed"></audio>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { Loading } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router'
const router = useRouter()

// --- Vue Refs ---
const videoStream = ref(null);
const audioPlayer = ref(null);
const transcript = ref('');
const isRecording = ref(false);
const isProcessing = ref(false);

// 系统状态相关
const isSystemOnline = ref(true);
const systemStatus = ref('System Ready');
const isVideoLoaded = ref(false);
const videoStatus = ref('Connecting to camera stream...');
const retryCount = ref(0);
const maxRetries = 5;

// Python后端视频流URL
const streamUrl = ref('http://127.0.0.1:5000/stream/raw') ;

// 存储不同阶段的图片和音频(Url)
const capturedImageUrl = ref('');
const resultImageUrl = ref('');
const resultAudioUrl = ref('');

// --- Media & Recognition instances ---
let audioStream = null;
let mediaRecorder = null;
const audioChunks = [];

// --- SpeechRecognition Setup ---
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
recognition.continuous = true;
recognition.interimResults = false;
recognition.lang = 'en-us';

recognition.onstart = () => {
    console.log("语音识别服务已启动。");
    systemStatus.value = 'Listening...';
};
recognition.onend = () => {
    console.log("语音识别服务已结束。");
    systemStatus.value = 'System Ready';
};
recognition.onresult = (event) => {
    const latestResult = event.results[event.results.length - 1];
    if (latestResult.isFinal) {
        const newSentence = latestResult[0].transcript;
        console.log("识别到新句子:", newSentence);
        transcript.value += (transcript.value ? '\n' : '') + newSentence;
    }
};
recognition.onerror = (event) => {
    console.error("语音识别出错:", event.error);
    systemStatus.value = 'Speech Error';
    if (isRecording.value) {
        isRecording.value = false;
        if (mediaRecorder && mediaRecorder.state === 'recording') mediaRecorder.stop();
        recognition.stop();
    }
};

// --- 视频流处理 ---
const onVideoLoad = () => {
    isVideoLoaded.value = true;
    videoStatus.value = 'Camera Connected';
    retryCount.value = 0;
    console.log("视频流加载成功");
};

const onVideoError = () => {
    isVideoLoaded.value = false;
    videoStatus.value = 'Camera stream disconnected, retrying...';
    if (retryCount.value < maxRetries) {
        retryCount.value++;
        setTimeout(() => {
            // 强制重新加载视频流
            streamUrl.value = `http://127.0.0.1:5000/stream?t=${new Date().getTime()}`;
        }, 2000);
    }
};

// --- 捕获初始截图的函数 ---
function captureInitialImage() {
    if (!videoStream.value || !isVideoLoaded.value) {
        console.error("视频流未准备好，无法截图。");
        return;
    }
    
    // 等待图片加载完成
    if (videoStream.value.complete && videoStream.value.naturalWidth > 0) {
        // 创建一个临时图片元素来避免跨域问题
        const tempImg = new Image();
        tempImg.crossOrigin = 'anonymous'; // 设置跨域属性
        
        tempImg.onload = () => {
            const canvas = document.createElement('canvas');
            canvas.width = tempImg.naturalWidth;
            canvas.height = tempImg.naturalHeight;
            const ctx = canvas.getContext('2d');
            
            // 先绘制白色背景
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            
            // 然后绘制图片
            ctx.drawImage(tempImg, 0, 0, canvas.width, canvas.height);
            
            try {
                const imageDataUrl = canvas.toDataURL('image/jpeg');
                capturedImageUrl.value = imageDataUrl;
                resultImageUrl.value = imageDataUrl;
                console.log("已捕获初始截图并显示。尺寸:", canvas.width, "x", canvas.height);
            } catch (error) {
                console.error("Canvas导出失败:", error);
                // 备用方案：直接使用视频流的当前帧
                fallbackCapture();
            }
        };
        
        tempImg.onerror = () => {
            console.error("临时图片加载失败");
            fallbackCapture();
        };
        
        // 设置图片源
        tempImg.src = videoStream.value.src;
    } else {
        console.error("视频流尚未加载完成。");
        alert("视频流未准备好，请稍后再试。");
    }
}
// 备用截图方案
function fallbackCapture() {
    try {
        // 尝试直接从视频流元素截图
        const canvas = document.createElement('canvas');
        canvas.width = videoStream.value.naturalWidth || 640;
        canvas.height = videoStream.value.naturalHeight || 480;
        const ctx = canvas.getContext('2d');
        
        // 绘制黑色背景
        ctx.fillStyle = '#000000';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        
        // 添加文字提示
        ctx.fillStyle = '#ffffff';
        ctx.font = '20px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('Camera Frame Captured', canvas.width / 2, canvas.height / 2);
        
        const imageDataUrl = canvas.toDataURL('image/jpeg');
        capturedImageUrl.value = imageDataUrl;
        resultImageUrl.value = imageDataUrl;
        console.log("使用备用方案生成截图");
    } catch (error) {
        console.error("备用截图方案也失败:", error);
        // 最后的备用方案：使用占位图片
        capturedImageUrl.value = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwA/8A8AF1BQkBAAID//Z';
        resultImageUrl.value = capturedImageUrl.value;
    }
}

// --- 发送图片和音频到Java后端 ---
async function sendDataToBackend(imageFile, audioBlob) {
    console.log(">>> [API] 准备发送数据到Java后端...");
    console.log(">>> [API] 图片文件:", imageFile);
    console.log(">>> [API] 音频Blob:", audioBlob);
    
    // 创建 FormData 对象
    const formData = new FormData();
    formData.append('image', imageFile);
    formData.append('audio', audioBlob, 'recording.webm');

    try {
        // 发送到Java后端
        const apiResponse = await fetch('http://localhost:8080/api/process', {
            method: 'POST',
            body: formData,
        });

        if (!apiResponse.ok) {
            throw new Error(`HTTP error! status: ${apiResponse.status}`);
        }

        const result = await apiResponse.json();
        console.log(">>> [API] Java后端处理成功，响应:", result);

        if (result && result.data && result.data.imageUrl && result.data.audioUrl) {
            return { imageUrl: result.data.imageUrl, audioUrl: result.data.audioUrl };
        } else {
            throw new Error("Java后端返回的数据格式不正确");
        }
    } catch (error) {
        console.error(">>> [API] 发送数据到Java后端失败:", error);
        throw error;
    }
}

// --- 处理数据的完整流程函数 ---
async function processDataWithBackend() {
    if (!capturedImageUrl.value) {
        console.error("处理失败：没有捕获到图片数据。");
        alert("处理失败：没有图片数据，请检查视频流是否正常。");
        return;
    }
    if (audioChunks.length === 0) {
        console.error("处理失败：没有录制到音频数据。");
        alert("处理失败：未检测到录音，请确保在录制时说话。");
        return;
    }

    isProcessing.value = true;
    systemStatus.value = 'Processing...';
    console.log("--- 流程：开始处理数据 ---");

    try {
        const imageResponse = await fetch(capturedImageUrl.value);
        const imageBlob = await imageResponse.blob();
        const imageFile = new File([imageBlob], "capture.jpg", { type: "image/jpeg" });
        const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
        
        const { imageUrl, audioUrl } = await sendDataToBackend(imageFile, audioBlob);
        
        resultImageUrl.value = imageUrl;
        resultAudioUrl.value = audioUrl;

        console.log("--- [Debug] 准备播放的音频URL是:", resultAudioUrl.value);
        console.log("--- [Debug] 图片URL是:", resultImageUrl.value);

        if (audioPlayer.value) {
            audioPlayer.value.src = resultAudioUrl.value;
            await audioPlayer.value.play();
            console.log("--- 流程：开始播放后端音频 ---");
        }

    } catch (error) {
        console.error("--- 流程：数据处理失败 ---", error);
        alert("处理失败，请稍后重试。");
    } finally {
        isProcessing.value = false;
        systemStatus.value = 'System Ready';
        console.log("--- 流程：处理完成，状态已重置 ---");
    }
}

// --- 音频播放完成后的回调 ---
function onAudioPlayed() {
    console.log("--- 流程：后端音频播放完毕 ---");
}

// --- 切换录制状态 ---
async function toggleRecording() {
    if (isRecording.value) {
        console.log("--- 流程：用户点击停止录制 ---");
        console.log("--- [MediaRecorder] 停止时，音频数据块数量:", audioChunks.length);
        isRecording.value = false;
        if (mediaRecorder && mediaRecorder.state === 'recording') {
            mediaRecorder.stop();
        }
        recognition.stop();

        await processDataWithBackend();

    } else {
        console.log("--- 流程：用户点击开始录制 ---");
        if (!audioStream || audioStream.getTracks().length === 0) {
            console.error("音频流未准备好或无效，无法录制。");
            alert("音频未初始化，请刷新页面并检查麦克风权限。");
            return;
        }

        isRecording.value = true;
        transcript.value = '';

        captureInitialImage();

        audioChunks.length = 0;

        // 检查音频流状态 
        const audioTracks = audioStream.getAudioTracks();
        console.log("--- [MediaRecorder] 音频轨道数量:", audioTracks.length);
        if (audioTracks.length > 0) {
            console.log("--- [MediaRecorder] 第一个音频轨道状态:", audioTracks[0].readyState, "是否启用:", audioTracks[0].enabled);
        }

        // 设置MediaRecorder选项
        let options = {};
        const preferredTypes = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus'];
        for (const type of preferredTypes) {
            if (MediaRecorder.isTypeSupported(type)) {
                options = { mimeType: type };
                console.log(`--- [MediaRecorder] 使用支持的格式: ${type}`);
                break;
            } else {
                console.log(`--- [MediaRecorder] 不支持的格式: ${type}`);
            }
        }
        if (!options.mimeType) {
            console.log("--- [MediaRecorder] 未找到明确支持的格式，将使用浏览器默认格式。");
        }

        mediaRecorder = new MediaRecorder(audioStream, options);
        console.log("--- [MediaRecorder] 已初始化，状态:", mediaRecorder.state, "格式:", mediaRecorder.mimeType);

        // MediaRecorder事件监听
        mediaRecorder.onstart = () => {
            console.log("--- [MediaRecorder] onstart 事件触发，状态:", mediaRecorder.state);
        };
        mediaRecorder.onstop = () => {
            console.log("--- [MediaRecorder] onstop 事件触发，状态:", mediaRecorder.state);
        };
        mediaRecorder.ondataavailable = (event) => {
            console.log("--- [MediaRecorder] ondataavailable 事件触发。数据大小:", event.data.size, "类型:", event.data.type);
            if (event.data && event.data.size > 0) {
                audioChunks.push(event.data);
            }
        };
        mediaRecorder.onerror = (event) => {
            console.error("--- [MediaRecorder] onerror 事件触发:", event.error);
        };

        mediaRecorder.start(100);
        console.log("--- [MediaRecorder] start() 已调用，时间片 100ms");
        recognition.start();
    }
}

// --- 页面导航函数 ---
const navigateToYolo = () => {
    console.log("准备跳转到YOLO页面...");
    
    // 清理音频资源（视频流由Python后端管理）
    if (mediaRecorder && mediaRecorder.state === 'recording') {
        mediaRecorder.stop();
    }
    recognition.stop();
    
    if (audioStream) {
        audioStream.getTracks().forEach(track => track.stop());
    }
    
    // 短暂延迟后跳转
    setTimeout(() => {
        try {
            router.push('/yolo');
            console.log("Vue Router 跳转成功");
        } catch (error) {
            console.error("Vue Router 跳转失败:", error);
            window.location.href = '/yolo';
        }
    }, 100);
}

// --- 生命周期钩子 ---
onMounted(async () => {
    try {
        // 只初始化音频流，视频流使用Python后端的
        await initializeAudioStream();
        console.log("页面加载完成，音频流已初始化，使用Python后端视频流。");
    } catch (error) {
        console.error('初始化音频流失败:', error);
        isSystemOnline.value = false;
        systemStatus.value = 'Audio Error';
        alert("请允许网页访问麦克风以使用语音识别功能。");
    }
});

onUnmounted(() => {
    // 清理音频资源
    if (mediaRecorder && mediaRecorder.state === 'recording') {
        mediaRecorder.stop();
    }
    recognition.stop();
    if (audioStream) {
        audioStream.getTracks().forEach(track => track.stop());
    }
});

// --- 只初始化音频流的函数 ---
async function initializeAudioStream() {
    const constraints = { video: false, audio: true }; // 只要音频
    const mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
    const audioTracks = mediaStream.getAudioTracks();

    if (audioTracks.length === 0) {
        throw new Error("无法访问麦克风");
    }

    audioStream = new MediaStream(audioTracks);
    console.log("音频流已成功初始化。");
}
</script>

<style scoped>
/* 整体页面布局 - 保持原有样式 */
.page {
    padding: 20px 20px;
    box-sizing: border-box;
    min-height: 94.35vh;
    background: #111827;
    display: flex;
}

.page>.el-row {
    flex-grow: 1;
}

/* Header - Desktop App Bar */
.header {
    height: 64px;
    background-color: #1f2937;
    border-bottom: 1px solid #374151;
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

/* Header Actions Container */
.header-actions {
    display: flex;
    align-items: center;
    gap: 20px;
}

/* Navigation Button Style */
.nav-button {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: none;
    padding: 10px 20px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    display: flex;
    align-items: center;
    gap: 8px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.nav-button:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.nav-button:active {
    transform: translateY(0);
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

.chatcol {
    display: flex;
    flex-direction: column;
}

.chatblock {
    width: 100%;
    padding: 10px 20px;
    background: #1f2937;
    border: 1px solid #374151;
    border-radius: 15px;
    justify-content: center;
    height: 100%;
    box-sizing: border-box;
}

.el-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}

.videoRow {
    height: 100%;
    width: 100%;
    display: flex;
}

.videoRow .el-col {
    height: 100%;
}

.media-container {
    width: 100%;
    height: 100%;
    border-radius: 15px;
    overflow: hidden;
    background-color: #000;
    position: relative;
    display: flex;
    justify-content: center;
    align-items: center;
}

.media-container video,
.media-container img {
    width: 100%;
    height: auto;
    max-height: 100%;
}

/* 视频和结果占位符样式 */
.video-placeholder,
.result-placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: #1f2937;
    color: #9ca3af;
    text-align: center;
}

.placeholder-icon {
    font-size: 48px;
    margin-bottom: 16px;
    opacity: 0.6;
}

.processing-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.7);
    color: white;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    font-size: 18px;
    border-radius: 15px;
}

.processing-overlay .el-icon {
    font-size: 40px;
    margin-bottom: 10px;
    color: #60a5fa;
}

.outputRow {
    display: flex;
    width: 100%;
    margin-top: 20px;
    height: calc(50% - 25px);
}

.console {
    width: 100%;
    background-color: #1f2937;
    border-radius: 15px;
    margin: 0 5px;
    padding: 30px 50px;
    height: 100%;
    box-sizing: border-box;
    color: #e5e7eb;
}

/* 聊天机器人介绍区域 */
.v3ai__chatbot-intro {
    text-align: center;
    padding: 40px 20px;
    color: #e5e7eb;
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
}

.v3ai__chatbot-intro .logo {
    font-size: 48px;
    color: #60a5fa;
    margin-bottom: 16px;
}

.v3ai__chatbot-intro .name {
    font-size: 24px;
    font-weight: 600;
    margin-bottom: 12px;
    color: #f3f4f6;
}

.v3ai__chatbot-intro .desc {
    font-size: 14px;
    color: #9ca3af;
    line-height: 1.6;
    margin-bottom: 8px;
}

.transcript-text {

    border-radius: 6px;
    padding: 12px;
    margin-top: 16px;
    min-height: 60px;
    max-height: 200px;
    overflow-y: auto;
    white-space: pre-wrap;
    font-family: monospace;
    font-size: 12px;
    text-align: left;
}

.text-gradient {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.v3ai__chatbot {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    color: #e5e7eb;
}

.v3ai__chatbot-sessions {
    min-height: 100px;
}

/* 录制按钮样式 */
.controls {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    margin-top: 20px;
}

.controls .el-button {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: none;
    padding: 12px 24px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.controls .el-button:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.controls .el-button:active {
    transform: translateY(0);
}

.recording {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%) !important;
    border-color: #ef4444 !important;
    box-shadow: 0 2px 4px rgba(239, 68, 68, 0.3);
}

.recording:hover {
    background: linear-gradient(135deg, #f87171 0%, #ef4444 100%) !important;
    border-color: #f87171 !important;
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(239, 68, 68, 0.4);
}

/* 加载动画 */
.loading-spinner {
    width: 40px;
    height: 40px;
    border: 3px solid #374151;
    border-top: 3px solid #60a5fa;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: 16px;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}
</style>
