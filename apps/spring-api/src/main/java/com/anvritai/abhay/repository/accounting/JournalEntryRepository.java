package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.JournalEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    boolean existsByVoucherId(UUID voucherId);
}
