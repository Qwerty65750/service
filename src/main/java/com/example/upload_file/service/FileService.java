package com.example.upload_file.service;

import com.example.upload_file.entity.FileMetadata;
import com.example.upload_file.repository.FileMetadataRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
