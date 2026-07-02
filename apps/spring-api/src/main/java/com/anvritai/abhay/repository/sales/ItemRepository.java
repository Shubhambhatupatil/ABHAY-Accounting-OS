package com.anvritai.abhay.repository.sales;

import com.anvritai.abhay.domain.sales.Item;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findAllByCompanyIdOrderByName(UUID companyId);
    List<Item> findByCompanyIdAndNameContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
    Optional<Item> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
    boolean existsByCompanyIdAndSkuIgnoreCase(UUID companyId, String sku);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"inventoryUnit", "itemCategory"})
    @Query("select item from Item item where item.id = :id and item.company.id = :companyId")
    Optional<Item> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
