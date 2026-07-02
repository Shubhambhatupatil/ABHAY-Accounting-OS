package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.VoucherAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherAttachmentRepository extends JpaRepository<VoucherAttachment, UUID> {
    List<VoucherAttachment> findAllByCompanyIdAndVoucherId(UUID companyId, UUID voucherId);
}
