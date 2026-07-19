package com.group1.career.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectResult;
import com.group1.career.config.OssConfigProperties;
import com.group1.career.exception.BizException;
import com.group1.career.service.impl.FileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileServiceTest {

    @Mock
    private OssConfigProperties ossConfig;

    @Mock
    private OSS ossClient;

    @Mock
    private MultipartFile mockFile;

    private FileServiceImpl fileService;

    @BeforeEach
    public void setUp() {
        // Inject mocked config
        fileService = spy(new FileServiceImpl(ossConfig));
        
        // Mock OSS client creation
        lenient().doReturn(ossClient).when(fileService).createOssClient();
        
        // Mock OSS client operations
        lenient().when(ossClient.putObject(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(new PutObjectResult());
    }

    @Test
    @DisplayName("Test Upload File - Empty File")
    public void testUploadFile_EmptyFile() {
        when(mockFile.isEmpty()).thenReturn(true);

        assertThrows(BizException.class, () -> {
            fileService.uploadFile(mockFile, "resumes");
        });
    }

    @Test
    @DisplayName("Test Upload File - Success returns object key, not URL")
    public void testUploadFile_Success() throws Exception {
        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(12L);
        when(mockFile.getOriginalFilename()).thenReturn("test-resume.pdf");
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(mockFile.getInputStream()).thenReturn(inputStream);

        String result = fileService.uploadFile(mockFile, "resumes");

        // New contract: returns only the OSS object key. Frontends/clients call
        // FileService#presignedUrl to get a browser-loadable URL on demand.
        assertNotNull(result);
        assertFalse(result.startsWith("https://"), "uploadFile must return key, not URL");
        assertTrue(result.startsWith("resumes/"), "key should be prefixed with the folder");
        assertTrue(result.endsWith(".pdf"));

        verify(ossClient).putObject(anyString(), anyString(), any(InputStream.class));
        verify(ossClient).shutdown();
    }

    @Test
    @DisplayName("Test Upload File - Null Folder Falls Back to 'others'")
    public void testUploadFile_NullFolder() throws Exception {
        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(7L);
        when(mockFile.getOriginalFilename()).thenReturn("file.txt");
        InputStream inputStream = new ByteArrayInputStream("content".getBytes());
        when(mockFile.getInputStream()).thenReturn(inputStream);

        String result = fileService.uploadFile(mockFile, null);

        assertNotNull(result);
        assertTrue(result.startsWith("others/"));
        assertTrue(result.endsWith(".txt"));

        verify(ossClient).putObject(anyString(), anyString(), any(InputStream.class));
        verify(ossClient).shutdown();
    }

    @Test
    @DisplayName("Test Upload File - Extension Handling preserves source extension")
    public void testUploadFile_ExtensionHandling() throws Exception {
        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(11L);
        when(mockFile.getOriginalFilename()).thenReturn("document.docx");
        InputStream inputStream = new ByteArrayInputStream("doc content".getBytes());
        when(mockFile.getInputStream()).thenReturn(inputStream);

        String result = fileService.uploadFile(mockFile, "documents");

        assertNotNull(result);
        assertTrue(result.startsWith("documents/"));
        assertTrue(result.endsWith(".docx"));

        verify(ossClient).putObject(anyString(), anyString(), any(InputStream.class));
        verify(ossClient).shutdown();
    }

    @Test
    @DisplayName("Delete object reports success and always closes the OSS client")
    void deleteObjectSuccessIsObservable() {
        when(ossConfig.getBucketName()).thenReturn("test-bucket");

        assertTrue(fileService.deleteObject("resumes/object.pdf"));

        verify(ossClient).deleteObject("test-bucket", "resumes/object.pdf");
        verify(ossClient).shutdown();
    }

    @Test
    @DisplayName("Delete object reports OSS failure so account purge can retry")
    void deleteObjectFailureIsObservable() {
        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        doThrow(new com.aliyun.oss.ClientException("network unavailable"))
                .when(ossClient).deleteObject("test-bucket", "avatars/object.jpg");

        assertFalse(fileService.deleteObject("avatars/object.jpg"));

        verify(ossClient).shutdown();
    }

    @Test
    @DisplayName("Malformed legacy URL is rejected without opening an OSS client")
    void malformedDeleteReferenceFailsClosed() {
        assertFalse(fileService.deleteObject("https://bucket.example.com"));

        verify(fileService, never()).createOssClient();
    }
}
