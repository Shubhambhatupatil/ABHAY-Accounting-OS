package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentReviewAction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentReviewActionRepository extends JpaRepository<DocumentReviewAction, UUID> {
    long countByCompanyIdAndDocumentIdAndAction(UUID companyId, UUID documentId, String action);
}
