package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.MemoryConfidenceScore;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryConfidenceScoreRepository extends JpaRepository<MemoryConfidenceScore,UUID>{void deleteAllByCompanyId(UUID companyId);}
