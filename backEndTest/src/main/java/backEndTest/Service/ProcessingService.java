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
    public ProcessedData processMedia(MultipartFile imageFile, MultipartFile audioFile) throws IOException {
        log.info("开始处理文件: 图片={}, 音频={}", imageFile.getOriginalFilename(), audioFile.getOriginalFilename());

        // 1. 保存原始文件
        String imageUrl = saveFile(imageFile, "images");
        String audioUrl = saveFile(audioFile, "audios");

        // 2. TODO: 替换业务逻辑 测试commad
        String command = "cmd /c dir";
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while((line=reader.readLine()) != null){
            System.out.println(line);
        }

        // 3. 假设处理后生成了新文件，并返回新文件的URL
        // 在真实场景中，这里应该是处理后的文件路径
        String processedImageUrl = "http://localhost:8080/Files/processedDir/img1.jpg";
        String processedAudioUrl = "http://localhost:8080/Files/processedDir/audio1.wav";

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
}
