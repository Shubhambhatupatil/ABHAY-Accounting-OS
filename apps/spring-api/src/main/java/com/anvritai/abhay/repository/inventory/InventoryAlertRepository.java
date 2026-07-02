package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.InventoryAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAlertRepository extends JpaRepository<InventoryAlert, UUID> {
    @EntityGraph(attributePaths = {"item", "warehouse"})
    List<InventoryAlert> findAllByCompanyIdAndResolvedFalseOrderByCreatedAtDesc(UUID companyId);
    Optional<InventoryAlert> findFirstByCompanyIdAndItemIdAndWarehouseIdAndResolvedFalse(
            UUID companyId, UUID itemId, UUID warehouseId);
}
