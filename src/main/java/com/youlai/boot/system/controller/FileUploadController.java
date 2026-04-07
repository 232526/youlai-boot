package com.youlai.boot.system.controller;

import com.youlai.boot.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传读取控制层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "08.文件上传接口")
@RequestMapping("/api/v1/files")
public class FileUploadController {

    @Operation(summary = "上传并读取文件内容")
    @PostMapping("/upload-read")
    public Result<List<String>> uploadAndReadFile(
        @Parameter(
            name = "file",
            description = "CSV或TXT文件",
            required = true,
            schema = @Schema(type = "string", format = "binary")
        )
        @RequestParam("file") MultipartFile file
    ) {
        try {
            // 校验文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.endsWith(".csv") && !originalFilename.endsWith(".txt"))) {
                return Result.failed("只支持上传CSV或TXT格式的文件");
            }

            // 读取文件内容
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 跳过空行
                    line = line.trim();
                    if (!line.isEmpty()) {
                        // 如果一行中包含问号，按问号分割
                        if (line.contains("?")) {
                            String[] parts = line.split("\\?");
                            for (String part : parts) {
                                part = part.trim();
                                if (!part.isEmpty()) {
                                    lines.add(part);
                                }
                            }
                        } else {
                            lines.add(line);
                        }
                    }
                }
            }

            log.info("文件上传读取成功，文件名：{}，读取行数：{}", originalFilename, lines.size());
            return Result.success(lines);

        } catch (IOException e) {
            log.error("文件上传读取失败", e);
            return Result.failed("文件读取失败：" + e.getMessage());
        }
    }

}
