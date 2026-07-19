package com.group1.career.controller;

import com.group1.career.interceptor.AuthInterceptor;
import com.group1.career.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    public void bypassAuth() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("API Test: Upload File Success")
    public void testUploadFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-resume.pdf", "application/pdf", "test content".getBytes());
        String mockUrl = "https://test-bucket.oss-cn-test.aliyuncs.com/resumes/test-file.pdf";
        when(fileService.uploadFile(any(), anyString())).thenReturn(mockUrl);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("folder", "resumes")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(mockUrl));
        verify(fileService).uploadFile(any(), eq("resumes/7"));
    }

    @Test
    @DisplayName("API Test: Upload File with Default Folder")
    public void testUploadFile_DefaultFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "content".getBytes());
        String mockUrl = "https://test-bucket.oss-cn-test.aliyuncs.com/resumes/doc.pdf";
        when(fileService.uploadFile(any(), anyString())).thenReturn(mockUrl);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isString());
        verify(fileService).uploadFile(any(), eq("resumes/7"));
    }

    @Test
    @DisplayName("API Test: Upload Avatar to Different Folder")
    public void testUploadFile_AvatarFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "image content".getBytes());
        String mockUrl = "https://test-bucket.oss-cn-test.aliyuncs.com/avatars/user-avatar.jpg";
        when(fileService.uploadFile(any(), anyString())).thenReturn(mockUrl);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("folder", "avatars")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(mockUrl));
        verify(fileService).uploadFile(any(), eq("avatars/7"));
    }

    @Test
    @DisplayName("Upload category cannot escape the user's OSS namespace")
    void rejectsArbitraryFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.bin", "application/octet-stream", new byte[]{1});

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("folder", "../avatars/99")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(fileService, never()).uploadFile(any(), anyString());
    }
}
