    package com.example.upload_file.repository;

    import com.example.upload_file.entity.FileMetadata;
    import org.springframework.data.jpa.repository.JpaRepository;
    import org.springframework.stereotype.Repository;

    import java.util.Optional;

    @Repository
    public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

        Optional<FileMetadata> findByStoredFilename(String storedFilename);

        boolean existsByStoredFilename(String storedFilename);
    }
