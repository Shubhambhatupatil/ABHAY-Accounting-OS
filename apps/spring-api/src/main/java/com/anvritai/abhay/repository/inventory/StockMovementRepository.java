package com.anvritai.abhay.repository.inventory;

import com.anvritai.abhay.domain.inventory.StockMovement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    @EntityGraph(attributePaths = {"item", "warehouse"})
    List<StockMovement> findAllByCompanyIdAndReferenceTypeAndReferenceIdOrderByCreatedAt(
            UUID companyId, String referenceType, UUID referenceId);
    boolean existsByCompanyIdAndReferenceTypeAndReferenceId(UUID companyId, String referenceType, UUID referenceId);
    Optional<StockMovement> findByIdAndCompanyId(UUID id, UUID companyId);
}
