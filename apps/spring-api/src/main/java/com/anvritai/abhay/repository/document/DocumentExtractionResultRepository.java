package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentExtractionResult;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentExtractionResultRepository extends JpaRepository<DocumentExtractionResult, UUID> {
    Optional<DocumentExtractionResult> findFirstByCompanyIdAndDocumentIdOrderByCreatedAtDesc(UUID companyId, UUID documentId);
}
