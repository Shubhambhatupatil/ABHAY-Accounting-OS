package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentField;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentFieldRepository extends JpaRepository<DocumentField, UUID> {
    List<DocumentField> findAllByCompanyIdAndDocumentIdOrderByFieldName(UUID companyId, UUID documentId);
    Optional<DocumentField> findByIdAndCompanyIdAndDocumentId(UUID id, UUID companyId, UUID documentId);
    Optional<DocumentField> findByCompanyIdAndDocumentIdAndFieldName(UUID companyId, UUID documentId, String name);
}
