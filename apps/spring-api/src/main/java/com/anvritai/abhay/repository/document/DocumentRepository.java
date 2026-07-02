package com.anvritai.abhay.repository.document;

import com.anvritai.abhay.domain.document.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {
    @EntityGraph(attributePaths = {"linkedInvoice", "linkedVoucher"})
    Optional<Document> findByIdAndCompanyId(UUID id, UUID companyId);
    @EntityGraph(attributePaths = {"linkedInvoice", "linkedVoucher"})
    List<Document> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    List<Document> findByCompanyIdAndOriginalFileNameContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
    Optional<Document> findFirstByCompanyIdAndFileHashSha256OrderByCreatedAtAsc(UUID companyId, String hash);
    long countByCompanyId(UUID companyId);
    long countByCompanyIdAndStatus(UUID companyId, DocumentStatus status);
    long countByCompanyIdAndDocumentType(UUID companyId, DocumentType type);
}
