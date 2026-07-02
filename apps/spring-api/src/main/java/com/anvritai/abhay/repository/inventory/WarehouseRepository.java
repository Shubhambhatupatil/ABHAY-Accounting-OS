package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.Warehouse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    List<Warehouse> findAllByCompanyIdOrderByName(UUID companyId);
    Optional<Warehouse> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<Warehouse> findFirstByCompanyIdAndPrimaryWarehouseTrueAndActiveTrue(UUID companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
