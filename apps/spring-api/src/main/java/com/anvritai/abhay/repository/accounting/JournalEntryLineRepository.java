package com.anvritai.abhay.repository.accounting;

import com.anvritai.abhay.domain.accounting.JournalEntryLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {

    @Query("""
            select line from JournalEntryLine line
            join fetch line.ledger ledger
            join fetch ledger.ledgerGroup
            join fetch line.journalEntry entry
            join fetch entry.voucher voucher
            join fetch voucher.voucherType
            where line.company.id = :companyId
              and entry.financialYear.id = :financialYearId
            order by entry.entryDate, voucher.voucherNumber, line.lineNumber
            """)
    List<JournalEntryLine> findReportLines(
            @Param("companyId") UUID companyId,
            @Param("financialYearId") UUID financialYearId);

    @Query("""
            select line from JournalEntryLine line
            join fetch line.ledger ledger
            join fetch ledger.ledgerGroup
            join fetch line.journalEntry entry
            join fetch entry.voucher voucher
            join fetch voucher.voucherType
            where line.company.id = :companyId
              and entry.financialYear.id = :financialYearId
              and ledger.id = :ledgerId
            order by entry.entryDate, voucher.voucherNumber, line.lineNumber
            """)
    List<JournalEntryLine> findLedgerStatementLines(
            @Param("companyId") UUID companyId,
            @Param("financialYearId") UUID financialYearId,
            @Param("ledgerId") UUID ledgerId);
}
