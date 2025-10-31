package com.example.upload_file.service;

import com.example.upload_file.entity.FileMetadata;
import com.example.upload_file.repository.FileMetadataRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {
    
    private final MinioClient minioClient;
    private final String bucketName;
    private final FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    public FileService(MinioClient minioClient, String bucketName, FileMetadataRepository fileMetadataRepository) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.fileMetadataRepository = fileMetadataRepository;
    }
    

    public String upload(MultipartFile file) {
        try {
            String storedFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storedFilename)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            
            FileMetadata metadata = new FileMetadata();
            metadata.setOriginalFilename(file.getOriginalFilename());
            metadata.setStoredFilename(storedFilename);
            metadata.setContentType(file.getContentType());
            metadata.setSize(file.getSize());
            fileMetadataRepository.save(metadata);
            
            return storedFilename;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при загрузке файла: " + e.getMessage(), e);
        }
    }

    public List<String> uploadMany(String folder, List<MultipartFile> files) {
        try {
            String normalizedPrefix = normalizeFolder(folder);
            if (!normalizedPrefix.isEmpty()) {
                ensureFolderPrefixExists(normalizedPrefix);
            }

            List<String> storedFilenames = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String namePart = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                String objectKey = normalizedPrefix.isEmpty() ? namePart : normalizedPrefix + "/" + namePart;

                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .stream(file.getInputStream(), file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );

                FileMetadata metadata = new FileMetadata();
                metadata.setOriginalFilename(file.getOriginalFilename());
                metadata.setStoredFilename(objectKey);
                metadata.setContentType(file.getContentType());
                metadata.setSize(file.getSize());
                fileMetadataRepository.save(metadata);

                storedFilenames.add(objectKey);
            }
            return storedFilenames;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при множественной загрузке: " + e.getMessage(), e);
        }
    }

    private String normalizeFolder(String folder) {
        if (folder == null) {
            return "";
        }
        String cleaned = folder.replace("\\", "/").trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private void ensureFolderPrefixExists(String prefix) throws MinioException {
        // В MinIO папка — это префикс. Создадим пустой объект с суффиксом '/'
        String folderMarker = prefix + "/";
        ByteArrayInputStream empty = new ByteArrayInputStream(new byte[0]);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(folderMarker)
                            .stream(empty, 0, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
        } catch (Exception e) {
            // если маркер уже есть/не требуется — игнорируем
        }
    }
    

    public ByteArrayResource download(String storedFilename) {
        try {
            FileMetadata metadata = fileMetadataRepository.findByStoredFilename(storedFilename)
                    .orElseThrow(() -> new RuntimeException("Файл не найден в базе данных"));
            
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storedFilename)
                    .build();
            
            InputStream stream = minioClient.getObject(getObjectArgs);
            byte[] bytes = IOUtils.toByteArray(stream);
            stream.close();
            
            return new ByteArrayResource(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при скачивании файла: " + e.getMessage(), e);
        }
    }
    

    public FileMetadata getFileMetadata(String storedFilename) {
        return fileMetadataRepository.findByStoredFilename(storedFilename)
                .orElseThrow(() -> new RuntimeException("Файл не найден"));
    }
}
