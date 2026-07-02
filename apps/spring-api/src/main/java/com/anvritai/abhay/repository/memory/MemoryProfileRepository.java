package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.MemoryProfile;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryProfileRepository extends JpaRepository<MemoryProfile,UUID>{
 Optional<MemoryProfile> findByCompanyId(UUID companyId); void deleteAllByCompanyId(UUID companyId);
}
