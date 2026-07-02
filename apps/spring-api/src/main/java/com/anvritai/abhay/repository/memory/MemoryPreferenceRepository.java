package com.anvritai.abhay.repository.memory;
import com.anvritai.abhay.domain.memory.MemoryPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryPreferenceRepository extends JpaRepository<MemoryPreference,UUID>{void deleteAllByCompanyId(UUID companyId);}
