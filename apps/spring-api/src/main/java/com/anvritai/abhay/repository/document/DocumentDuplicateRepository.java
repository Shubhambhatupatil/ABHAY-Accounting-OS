package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentDuplicate;
import java.util.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentDuplicateRepository extends JpaRepository<DocumentDuplicate, UUID> {
    @EntityGraph(attributePaths = {"document", "duplicateOfDocument"})
    List<DocumentDuplicate> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    boolean existsByCompanyIdAndDocumentId(UUID companyId, UUID documentId);
}
