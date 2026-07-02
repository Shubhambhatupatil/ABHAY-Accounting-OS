package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.StockValuationLayer;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockValuationLayerRepository extends JpaRepository<StockValuationLayer, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "stockBatch")
    @Query("select layer from StockValuationLayer layer where layer.company.id = :companyId "
            + "and layer.item.id = :itemId and layer.warehouse.id = :warehouseId "
            + "and layer.remainingQuantity > 0 order by layer.receivedDate, layer.createdAt")
    List<StockValuationLayer> lockAvailableLayers(
            @Param("companyId") UUID companyId, @Param("itemId") UUID itemId,
            @Param("warehouseId") UUID warehouseId);
}
