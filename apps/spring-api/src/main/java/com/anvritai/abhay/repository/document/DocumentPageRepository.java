package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentPage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentPageRepository extends JpaRepository<DocumentPage, UUID> { }
