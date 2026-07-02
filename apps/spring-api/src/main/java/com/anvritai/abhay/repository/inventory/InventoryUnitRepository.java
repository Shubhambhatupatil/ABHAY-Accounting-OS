package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.InventoryUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, UUID> {
    List<InventoryUnit> findAllByCompanyIdOrderByName(UUID companyId);
    Optional<InventoryUnit> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndSymbolIgnoreCase(UUID companyId, String symbol);
}
