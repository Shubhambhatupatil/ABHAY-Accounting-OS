package com.anvritai.abhay.repository.sales;

import com.anvritai.abhay.domain.sales.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    @EntityGraph(attributePaths = "ledger")
    List<Customer> findAllByCompanyIdOrderByDisplayName(UUID companyId);
    List<Customer> findByCompanyIdAndDisplayNameContainingIgnoreCase(UUID companyId,String query,org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths = "ledger")
    Optional<Customer> findByIdAndCompanyId(UUID id, UUID companyId);
}
