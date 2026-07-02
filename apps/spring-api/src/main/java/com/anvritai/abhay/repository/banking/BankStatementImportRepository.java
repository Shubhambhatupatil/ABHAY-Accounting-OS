package com.anvritai.abhay.repository.banking;

import com.anvritai.abhay.domain.banking.BankStatementImport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementImportRepository extends JpaRepository<BankStatementImport, UUID> {
    Optional<BankStatementImport> findByCompanyIdAndBankAccountIdAndFileHash(
            UUID companyId, UUID bankAccountId, String fileHash);
}
