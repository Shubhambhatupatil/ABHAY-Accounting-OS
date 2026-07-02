package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryEventRepository extends JpaRepository<MemoryEvent,UUID>{
 List<MemoryEvent> findAllByCompanyIdOrderByOccurredAtDesc(UUID companyId);
 List<MemoryEvent> findByCompanyIdAndSubjectKeyContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
 List<MemoryEvent> findAllByCompanyIdOrderByOccurredAtAsc(UUID companyId);
 long countByCompanyId(UUID companyId);
 long countByCompanyIdAndEventType(UUID companyId,String eventType);
 void deleteAllByCompanyId(UUID companyId);
}
