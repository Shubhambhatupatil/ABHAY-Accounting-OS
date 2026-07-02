package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.PaymentMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
}
