package backEndTest.controller;

import backEndTest.dto.ApiResponse;
import backEndTest.dto.ProcessedData;
import backEndTest.Service.ProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@Slf4j
public class ProcessController {

    @Autowired
    private ProcessingService processingService;

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<ProcessedData>> processFiles(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam("audio") MultipartFile audioFile) {

        // 1. 基本校验
        if (imageFile.isEmpty() || audioFile.isEmpty()) {
            log.warn("请求失败：图片或音频文件为空。");
            ApiResponse<ProcessedData> errorResponse = ApiResponse.error(1001, "图片或音频文件为空");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // 2. 调用服务层处理
        try {
            ProcessedData data = processingService.processMedia(imageFile, audioFile);
            ApiResponse<ProcessedData> successResponse = ApiResponse.success(data);
            return ResponseEntity.ok(successResponse);
        } catch (IOException | InterruptedException e) {
            log.error("文件处理失败", e);
            ApiResponse<ProcessedData> errorResponse = ApiResponse.error(1002, "文件处理失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
