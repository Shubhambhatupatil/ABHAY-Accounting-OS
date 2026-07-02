package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.StockTransfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
}
