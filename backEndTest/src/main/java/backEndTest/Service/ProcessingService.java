package backEndTest.Service;

import backEndTest.dto.ProcessedData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j // 使用 Lombok 提供的日志功能
public class ProcessingService {

    // 从配置文件中读取文件存储路径，如果未配置则使用默认值
    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 处理上传的图片和音频文件
     * @param imageFile 图片文件
     * @param audioFile 音频文件
     * @return ProcessedData 包含处理后文件URL的对象
     * @throws IOException 如果文件读写失败
     */
    public ProcessedData processMedia(MultipartFile imageFile, MultipartFile audioFile) throws IOException, InterruptedException {
        log.info("开始处理文件: 图片={}, 音频={}", imageFile.getOriginalFilename(), audioFile.getOriginalFilename());

        // 1. 保存原始文件
        String imageUrl = saveFile(imageFile, "images");
        String audioUrl = saveFile(audioFile, "audios");
        String wavAudioUrl = saveAndConvertAudioFile(audioFile, "audios");

        // 2. TODO: 替换业务逻辑 测试commad
//        String command = "cmd /c dir";
//        Process process = Runtime.getRuntime().exec(command);
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        String line;
//        while((line=reader.readLine()) != null){
//            System.out.println(line);
//        }

        // 3. 假设处理后生成了新文件，并返回新文件的URL
        // 在真实场景中，这里应该是处理后的文件路径
        String processedImageUrl = "http://localhost:8080/Files/processedDir/img1.jpg";
//        String processedAudioUrl = "http://localhost:8080/Files/processedDir/audio1.wav"+audioUrl;
        String processedAudioUrl = "http://localhost:8080/uploads/"+wavAudioUrl;

        log.info("处理完成: 图片URL={}, 音频URL={}", processedImageUrl, processedAudioUrl);

        return new ProcessedData(processedImageUrl, processedAudioUrl);
    }

    /**
     * 保存上传的文件到本地目录
     */
    private String saveFile(MultipartFile file, String subDir) throws IOException {
        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir, subDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名，防止冲突
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // 保存文件
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath);

        // 返回相对路径，用于后续生成URL
        return subDir + "/" + uniqueFilename;
    }

    /**
     * 根据文件路径生成可公开访问的URL
     * 注意：这是一个简单的示例，生产环境需要考虑域名、端口、CDN等
     */
    private String getPublicUrl(String filePath) {
        // 假设你的应用运行在 http://localhost:8080
        // 并且配置了静态资源访问路径
        return "http://localhost:8080/Files/" + filePath;
    }


    private String saveAndConvertAudioFile(MultipartFile file, String subDir) throws IOException, InterruptedException {
        // 1. 准备目录
        Path uploadPath = Paths.get("uploads", subDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        System.out.println(uploadPath);

        // 2. 生成基础文件名（UUID）
        String uuid = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        // 获取原始后缀 (例如 .webm)
//        String originalExtension = originalFilename != null && originalFilename.contains(".")
//                ? originalFilename.substring(originalFilename.lastIndexOf("."))
//                : ".webm";

        // 3. 定义原始文件路径和目标 WAV 文件路径
        // 使用绝对路径以确保 FFmpeg 能正确找到文件
        Path inputFilePath = uploadPath.resolve(uuid + ".webm").toAbsolutePath();
        Path outputFilePath = uploadPath.resolve(uuid + ".wav").toAbsolutePath();

        // 4. 保存原始文件到磁盘
        log.info("正在保存原始音频文件: {}", inputFilePath);
        file.transferTo(inputFilePath.toFile());

        // 5. 构建 FFmpeg 命令
        // 命令格式: ffmpeg -y -i "input.webm" -acodec pcm_s16le -ar 16000 -ac 1 "output.wav"
        // 参数解释:
        // -y: 覆盖输出文件（如果存在）
        // -i: 输入文件
        // -acodec pcm_s16le: 编码为 PCM 16-bit (WAV 标准编码)
        // -ar 16000: 采样率 16kHz (根据你的需求调整，如 44100)
        // -ac 1: 声道数 (1=单声道, 2=立体声，语音识别通常用单声道)
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-i");
        command.add(inputFilePath.toString());
        command.add("-acodec");
        command.add("pcm_s16le"); // WAV 标准编码
//        command.add("-ar");
//        command.add("16000");     // 如果用于语音识别，建议强制转为 16k
//        command.add("-ac");
//        command.add("1");         // 强制转为单声道
        command.add(outputFilePath.toString());

        log.info("开始执行 FFmpeg 转换: {}", command);

        // 6. 执行命令
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // 合并标准输出和错误输出
        Process process = processBuilder.start();

        // 读取 FFmpeg 的输出日志（用于调试）
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }
        }

        // 等待进程结束
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("音频转换失败，退出码: {}", exitCode);
            // 可以在这里选择删除原始文件或抛出异常
            throw new IOException("FFmpeg 转换音频失败");
        }

        log.info("音频转换成功: {}", outputFilePath);

        // 7. (可选) 删除原始的 .webm 文件以节省空间
        Files.deleteIfExists(inputFilePath);

        // 8. 返回 WAV 文件的相对路径 (用于前端访问)
        return subDir + "/" + uuid + ".wav";
    }

}
