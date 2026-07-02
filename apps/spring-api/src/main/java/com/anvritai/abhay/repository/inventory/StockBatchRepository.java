package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.StockBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {
}
