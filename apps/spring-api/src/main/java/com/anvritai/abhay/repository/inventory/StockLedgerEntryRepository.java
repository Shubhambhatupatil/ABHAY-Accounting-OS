package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.StockLedgerEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerEntryRepository extends JpaRepository<StockLedgerEntry, UUID> {
    @EntityGraph(attributePaths = {"stockMovement", "item", "warehouse"})
    List<StockLedgerEntry> findAllByCompanyIdAndItemIdOrderByEntryDateAscCreatedAtAsc(UUID companyId, UUID itemId);
    @EntityGraph(attributePaths = {"stockMovement", "item", "warehouse"})
    List<StockLedgerEntry> findAllByCompanyIdOrderByEntryDateAscCreatedAtAsc(UUID companyId);
    Optional<StockLedgerEntry> findFirstByCompanyIdAndItemIdAndWarehouseIdOrderByCreatedAtDesc(
            UUID companyId, UUID itemId, UUID warehouseId);
}
