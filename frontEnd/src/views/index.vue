<template>
    <div class="page">
        <el-row :gutter="15">

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
                            <h3 class="name"><span class="txt text-gradient">嗨~ </span></h3>
                            <p class="desc">请把你的任务交给我吧~</p>
                            <!-- 显示识别到的文字 -->
                            <p class="desc">{{ transcript }}</p>
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
                <el-row class="videoRow" :gutter="10">
                    <!-- inputvideo (left) -->
                    <el-col :span="12">
                        <div class="media-container">
                            <video ref="video" autoplay muted @loadedmetadata="onVideoMetadataLoaded"></video>
                        </div>
                    </el-col>
                    <!-- result display (right) -->
                    <el-col :span="12">
                        <div class="media-container">
                            <!-- 显示截图的img -->
                            <img v-show="!isProcessing && resultImageUrl" :src="resultImageUrl" alt="Result Image" />
                            
                            <!-- 加载状态覆盖层 -->
                            <div v-if="isProcessing" class="processing-overlay">
                                <el-icon class="is-loading"><Loading /></el-icon>
                                <span>Processing...</span>
                            </div>
                        </div>
                    </el-col>
                </el-row>

                <!-- console display -->
                 <!-- <el-row class="outputRow" :gutter="10">
                    <div class="console">
                        <div>
                            row computing process
                        </div>
                    </div>
                </el-row> -->
            </el-col>
        </el-row>
        <!-- 播放后端返回的音频的隐藏元素 -->
        <audio ref="audioPlayer" @ended="onAudioPlayed"></audio>
    </div>
</template>


<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { Loading } from '@element-plus/icons-vue';

// --- Vue Refs ---
const video = ref(null);
const audioPlayer = ref(null);
const transcript = ref('');
const isRecording = ref(false);
const isProcessing = ref(false);

// 存储不同阶段的图片和音频(Url)
const capturedImageUrl = ref('');
const resultImageUrl = ref('');
const resultAudioUrl = ref('');

// --- Media & Recognition instances ---
let videoStream = null;
let audioStream = null;
let mediaRecorder = null;
const audioChunks = [];

// Promise确保视频元数据加载完成
let videoMetadataLoadedPromise = null;

// --- SpeechRecognition Setup ---
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
recognition.continuous = true;
recognition.interimResults = false;
recognition.lang = 'en-us';

recognition.onstart = () => console.log("语音识别服务已启动。");
recognition.onend = () => console.log("语音识别服务已结束。");
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
    if (isRecording.value) {
        isRecording.value = false;
        if (mediaRecorder && mediaRecorder.state === 'recording') mediaRecorder.stop();
        recognition.stop();
    }
};

// --- 捕获初始截图的函数 ---
function captureInitialImage() {
    if (!video.value) {
        console.error("视频元素未找到，无法截图。");
        return;
    }
    if (video.value.videoWidth === 0 || video.value.videoHeight === 0) {
        console.error("视频尺寸为0，无法截图。可能视频流尚未准备好。");
        alert("视频未准备好，请稍后再试。");
        return;
    }

    const canvas = document.createElement('canvas');
    canvas.width = video.value.videoWidth;
    canvas.height = video.value.videoHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video.value, 0, 0, canvas.width, canvas.height);
    const imageDataUrl = canvas.toDataURL('image/jpeg');
    
    capturedImageUrl.value = imageDataUrl;
    resultImageUrl.value = imageDataUrl;
    console.log("已捕获初始截图并显示。尺寸:", canvas.width, "x", canvas.height);
}

// --- 发送图片和音频到后端并接收返回结果的函数 ---
async function sendDataToBackend(imageFile, audioBlob) {
    console.log(">>> [API] 准备发送数据到后端...");
    console.log(">>> [API] 图片文件:", imageFile);
    console.log(">>> [API] 音频Blob:", audioBlob);
    
    // --- 模拟后端处理 ---
    /*
    return new Promise((resolve, reject) => {
        console.log(">>> [API] 模拟网络请求，等待3秒...");
        setTimeout(() => {
            console.log(">>> [API] 模拟后端处理完成，返回结果。");
            resolve({
                imageUrl: `https://picsum.photos/seed/${Date.now()}/640/480.jpg`,
                audioUrl: 'https://www.soundjay.com/misc/sounds/bell-ringing-05.wav'
            });
        }, 3000);
    });
    */
    
    
    // =================================================================
    // ======================= 真实的后端请求代码 =======================
    // =================================================================
    
    // 1. 创建一个 FormData 对象来打包文件数据
    const formData = new FormData();
    
    // 2. 将图片文件添加到 FormData 中
    // 'image' 是后端接口接收图片的字段名，根据后端API进行修改
    formData.append('image', imageFile);
    
    // 3. 将音频 Blob 添加到 FormData 中
    // 'audio' 是后端接口接收音频的字段名，根据后端API进行修改
    // 'recording.wav' 是文件名，后端可能会用到
    formData.append('audio', audioBlob, 'recording.webm');

    try {
        // 4. 使用 fetch 发送 POST 请求到你的后端
        const apiResponse = await fetch('http://localhost:8080/api/process', {
            method: 'POST',
            // 发送FormData，浏览器自动设置Content-Type(multipart/form-data)
            body: formData,
        });

        // 5. 检查响应状态码
        if (!apiResponse.ok) {
            throw new Error(`HTTP error! status: ${apiResponse.status}`);
        }

        // 6. 解析后端返回的 JSON 数据
        const result = await apiResponse.json();
        console.log(">>> [API] 后端处理成功，响应:", result);

        // 7. 检查返回的数据格式是否符合预期
        // 后端返回格式 { success: true, data: { imageUrl: "...", audioUrl: "..." } }
        if (result && result.data && result.data.imageUrl && result.data.audioUrl) {
            return { imageUrl: result.data.imageUrl, audioUrl: result.data.audioUrl };
        } else {
            throw new Error("后端返回的数据格式不正确或未包含必要的URL。");
        }
    } catch (error) {
        // 8. 捕获并处理网络请求或数据处理中发生的错误
        console.error(">>> [API] 发送数据到后端失败:", error);
        throw error;
    }
    
}


// --- 处理数据的完整流程函数 ---
async function processDataWithBackend() {
    if (!capturedImageUrl.value) {
        console.error("处理失败：没有捕获到图片数据。");
        alert("处理失败：没有图片数据，请检查摄像头是否正常工作。");
        return;
    }
    if (audioChunks.length === 0) {
        console.error("处理失败：没有录制到音频数据。");
        alert("处理失败：未检测到录音，请确保在录制时说话，并且录制时间不要太短。");
        return;
    }

    isProcessing.value = true;
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


        console.log("--- 流程：后端图片已更新 ---");

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
        
        if (videoMetadataLoadedPromise) {
            console.log("--- 流程：等待视频元数据加载...");
            await videoMetadataLoadedPromise;
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

        // *** 诊断日志：尝试多种 mimeType ***
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

        // 增加 MediaRecorder 事件监听
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

        mediaRecorder.start(100); // 设置一个较短的时间片(timeslice)
        console.log("--- [MediaRecorder] start() 已调用，时间片 100ms");
        recognition.start();
    }
}

// --- 生命周期钩子 ---
onMounted(async () => {
    try {
        await initializeMediaStream();
        console.log("页面加载完成，媒体流已使用系统默认设备初始化。");
    } catch (error) {
        console.error('初始化媒体流失败:', error);
        alert("请允许网页访问摄像头和麦克风以使用全部功能。");
    }
});

onUnmounted(() => {
    if (videoStream) videoStream.getTracks().forEach(t => t.stop());
    if (audioStream) audioStream.getTracks().forEach(t => t.stop());
    recognition.stop();
});

// --- 简化后的媒体流初始化函数 ---
async function initializeMediaStream() {
    const constraints = { video: true, audio: true };
    const mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
    const videoTracks = mediaStream.getVideoTracks();
    const audioTracks = mediaStream.getAudioTracks();

    if (audioTracks.length === 0) alert("无法访问麦克风...");
    if (videoTracks.length === 0) alert("无法访问摄像头...");

    videoStream = new MediaStream(videoTracks);
    audioStream = new MediaStream(audioTracks);
    if (video.value) {
        video.value.srcObject = videoStream;
        videoMetadataLoadedPromise = new Promise((resolve) => {
            video.value.onloadedmetadata = () => {
                console.log("视频元数据已加载，尺寸:", video.value.videoWidth, "x", video.value.videoHeight);
                resolve();
            };
        });
    }
    console.log("媒体流已成功初始化和分离。");
}

// --- 视频元数据加载完成的事件处理函数 ---
function onVideoMetadataLoaded() {
    console.log("模板中的 onloadedmetadata 事件被触发。");
}
</script>

<style scoped>
.page {
    padding: 20px 20px;
    box-sizing: border-box;
    min-height: 100vh;
    background-color: #575871;
    display: flex;
}

.page>.el-row {
    flex-grow: 1;
}

.chatcol {
    display: flex;
    flex-direction: column;
}

.chatblock {
    width: 100%;
    padding: 10px 20px;
    background-color: #ffffff;
    border-radius: 15px;
    justify-content: center;
    height: 100%;
    box-sizing: border-box;
}

.el-container {
    height: 100%;
    display: flex;
    column-gap: 15px;
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
    justify-content: center; /* 水平居中 */
    align-items: center;     /* 垂直居中 */
}

.media-container video,
.media-container img {
    width: 100%;
    /* height: 100%;
    object-fit: cover; */
      height: auto;
    max-height: 100%;
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
}

.outputRow {
    display: flex;
    width: 100%;
    margin-top: 20px;
    height: calc(50% - 25px);
}

.console {
    width: 100%;
    background-color: #ffffff;
    border-radius: 15px;
    margin: 0 5px;
    padding: 30px 50px;
    height: 100%;
    box-sizing: border-box;
}

.recording {
    background-color: #f56c6c !important;
    border-color: #f56c6c !important;
}

.recording:hover {
    background-color: #f78989 !important;
    border-color: #f78989 !important;
}

.controls {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
}
</style>
