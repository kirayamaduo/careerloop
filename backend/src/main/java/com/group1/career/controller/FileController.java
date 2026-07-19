package com.group1.career.controller;

import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.service.FileService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "File API")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "Upload File to OSS")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "folder", defaultValue = "resumes") String folder) {
        Long uid = SecurityUtil.requireCurrentUserId();
        String kind = folder == null ? "resumes" : folder.trim().toLowerCase();
        if (!"resumes".equals(kind) && !"avatars".equals(kind)) {
            throw new BizException("Unsupported upload category");
        }
        return Result.success(fileService.uploadFile(file, kind + "/" + uid));
    }
}
