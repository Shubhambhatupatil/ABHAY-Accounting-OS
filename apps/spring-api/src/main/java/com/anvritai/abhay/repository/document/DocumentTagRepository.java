package com.anvritai.abhay.repository.document;
import com.anvritai.abhay.domain.document.DocumentTag;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentTagRepository extends JpaRepository<DocumentTag, UUID> { }
