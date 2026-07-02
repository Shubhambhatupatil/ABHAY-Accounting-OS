package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.VoucherType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherTypeRepository extends JpaRepository<VoucherType, UUID> {
    List<VoucherType> findAllByActiveTrueOrderByName();
    Optional<VoucherType> findByCodeAndActiveTrue(String code);
}
