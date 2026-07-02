package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentProcessingJob;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentProcessingJobRepository extends JpaRepository<DocumentProcessingJob, UUID> {
    Optional<DocumentProcessingJob> findFirstByCompanyIdAndDocumentIdOrderByCreatedAtDesc(UUID companyId, UUID documentId);
}
