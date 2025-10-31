package com.example.upload_file.controller;

import com.example.upload_file.entity.FileMetadata;
import com.example.upload_file.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    
    private final FileService fileService;
    
    @Autowired
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }
    

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Файл не может быть пустым");
            }
            
            String storedFilename = fileService.upload(file);
            
            return ResponseEntity.ok()
                    .body(new UploadResponse(
                            "Файл успешно загружен",
                            storedFilename,
                            file.getOriginalFilename(),
                            file.getSize()
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при загрузке файла: " + e.getMessage());
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable String filename) {
        try {
            ByteArrayResource resource = fileService.download(filename);
            FileMetadata metadata = fileService.getFileMetadata(filename);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .contentLength(metadata.getSize() != null ? metadata.getSize() : resource.contentLength())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PostMapping("/upload/batch")
    public ResponseEntity<?> uploadFilesBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", required = false) String folder
    ) {
        try {
            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().body("Не переданы файлы");
            }

            List<String> stored = fileService.uploadMany(folder, files);
            return ResponseEntity.ok(Map.of(
                    "message", "Файлы успешно загружены",
                    "folder", folder == null ? "" : folder,
                    "storedFilenames", stored,
                    "count", stored.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при множественной загрузке: " + e.getMessage());
        }
    }

    @GetMapping("/info/{filename}")
    public ResponseEntity<?> getFileInfo(@PathVariable String filename) {
        try {
            FileMetadata metadata = fileService.getFileMetadata(filename);
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Файл не найден: " + e.getMessage());
        }
    }
    

    static class UploadResponse {
        private String message;
        private String storedFilename;
        private String originalFilename;
        private Long size;
        
        public UploadResponse(String message, String storedFilename, String originalFilename, Long size) {
            this.message = message;
            this.storedFilename = storedFilename;
            this.originalFilename = originalFilename;
            this.size = size;
        }
        
 
        public String getMessage() {
            return message;
        }
        
        public String getStoredFilename() {
            return storedFilename;
        }
        
        public String getOriginalFilename() {
            return originalFilename;
        }
        
        public Long getSize() {
            return size;
        }
    }
}
