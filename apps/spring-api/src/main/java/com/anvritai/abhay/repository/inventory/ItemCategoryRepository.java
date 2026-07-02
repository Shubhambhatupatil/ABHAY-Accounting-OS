package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.ItemCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {
    @EntityGraph(attributePaths = "parent")
    List<ItemCategory> findAllByCompanyIdOrderByName(UUID companyId);
    @EntityGraph(attributePaths = "parent")
    Optional<ItemCategory> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
