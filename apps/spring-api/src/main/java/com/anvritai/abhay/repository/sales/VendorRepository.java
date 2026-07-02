package com.anvritai.abhay.repository.sales;

import com.anvritai.abhay.domain.sales.Vendor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    @EntityGraph(attributePaths = "ledger")
    List<Vendor> findAllByCompanyIdOrderByDisplayName(UUID companyId);
    List<Vendor> findByCompanyIdAndDisplayNameContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths = "ledger")
    Optional<Vendor> findByIdAndCompanyId(UUID id, UUID companyId);
}
