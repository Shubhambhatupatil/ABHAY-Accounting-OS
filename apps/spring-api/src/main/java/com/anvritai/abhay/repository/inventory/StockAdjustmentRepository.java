package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.StockAdjustment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
}
