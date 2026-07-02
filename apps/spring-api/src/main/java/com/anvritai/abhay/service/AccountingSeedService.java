package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.accounting.AccountNature;
import com.anvritai.abhay.domain.accounting.Ledger;
import com.anvritai.abhay.domain.accounting.LedgerGroup;
import com.anvritai.abhay.domain.accounting.LedgerType;
import com.anvritai.abhay.domain.accounting.NormalBalance;
import com.anvritai.abhay.repository.accounting.LedgerGroupRepository;
import com.anvritai.abhay.repository.accounting.LedgerRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AccountingSeedService {

    private final LedgerGroupRepository ledgerGroups;
    private final LedgerRepository ledgers;

    public AccountingSeedService(LedgerGroupRepository ledgerGroups, LedgerRepository ledgers) {
        this.ledgerGroups = ledgerGroups;
        this.ledgers = ledgers;
    }

    public void seedCompany(Company company) {
        Map<String, GroupDefinition> definitions = new LinkedHashMap<>();
        definitions.put("Assets", new GroupDefinition(AccountNature.ASSET, null));
        definitions.put("Current Assets", new GroupDefinition(AccountNature.ASSET, "Assets"));
        definitions.put("Cash-in-Hand", new GroupDefinition(AccountNature.ASSET, "Current Assets"));
        definitions.put("Bank Accounts", new GroupDefinition(AccountNature.ASSET, "Current Assets"));
        definitions.put("Sundry Debtors", new GroupDefinition(AccountNature.ASSET, "Current Assets"));
        definitions.put("Liabilities", new GroupDefinition(AccountNature.LIABILITY, null));
        definitions.put("Current Liabilities", new GroupDefinition(AccountNature.LIABILITY, "Liabilities"));
        definitions.put("Sundry Creditors", new GroupDefinition(AccountNature.LIABILITY, "Current Liabilities"));
        definitions.put("Duties & Taxes", new GroupDefinition(AccountNature.LIABILITY, "Current Liabilities"));
        definitions.put("Income", new GroupDefinition(AccountNature.INCOME, null));
        definitions.put("Sales Accounts", new GroupDefinition(AccountNature.INCOME, "Income"));
        definitions.put("Expenses", new GroupDefinition(AccountNature.EXPENSE, null));
        definitions.put("Purchase Accounts", new GroupDefinition(AccountNature.EXPENSE, "Expenses"));
        definitions.put("Direct Expenses", new GroupDefinition(AccountNature.EXPENSE, "Expenses"));
        definitions.put("Indirect Expenses", new GroupDefinition(AccountNature.EXPENSE, "Expenses"));
        definitions.put("Equity", new GroupDefinition(AccountNature.EQUITY, null));
        definitions.put("Capital Account", new GroupDefinition(AccountNature.EQUITY, "Equity"));

        Map<String, LedgerGroup> groups = new LinkedHashMap<>();
        for (Map.Entry<String, GroupDefinition> item : definitions.entrySet()) {
            LedgerGroup group = ledgerGroups.findAllByCompanyIdOrderByName(company.getId()).stream()
                    .filter(existing -> existing.getName().equalsIgnoreCase(item.getKey()))
                    .findFirst()
                    .orElseGet(() -> {
                        LedgerGroup created = new LedgerGroup();
                        created.setCompany(company);
                        created.setName(item.getKey());
                        created.setAccountNature(item.getValue().nature());
                        created.setParent(item.getValue().parentName() == null
                                ? null
                                : groups.get(item.getValue().parentName()));
                        created.setSystemGroup(true);
                        return ledgerGroups.save(created);
                    });
            groups.put(item.getKey(), group);
        }

        seedLedger(company, groups.get("Cash-in-Hand"), "Cash", "CASH", NormalBalance.DEBIT, LedgerType.CASH);
        seedLedger(company, groups.get("Bank Accounts"), "Primary Bank", "BANK", NormalBalance.DEBIT, LedgerType.BANK);
        seedLedger(company, groups.get("Sales Accounts"), "Sales", "SALES", NormalBalance.CREDIT, LedgerType.GENERAL);
        seedLedger(company, groups.get("Purchase Accounts"), "Purchase", "PURCHASE", NormalBalance.DEBIT, LedgerType.GENERAL);
        seedLedger(company, groups.get("Current Assets"), "GST Input", "GST-IN", NormalBalance.DEBIT, LedgerType.TAX);
        seedLedger(company, groups.get("Duties & Taxes"), "GST Output", "GST-OUT", NormalBalance.CREDIT, LedgerType.TAX);
        seedLedger(company, groups.get("Current Assets"), "CGST Input", "CGST-IN", NormalBalance.DEBIT, LedgerType.TAX);
        seedLedger(company, groups.get("Current Assets"), "SGST Input", "SGST-IN", NormalBalance.DEBIT, LedgerType.TAX);
        seedLedger(company, groups.get("Current Assets"), "IGST Input", "IGST-IN", NormalBalance.DEBIT, LedgerType.TAX);
        seedLedger(company, groups.get("Duties & Taxes"), "CGST Output", "CGST-OUT", NormalBalance.CREDIT, LedgerType.TAX);
        seedLedger(company, groups.get("Duties & Taxes"), "SGST Output", "SGST-OUT", NormalBalance.CREDIT, LedgerType.TAX);
        seedLedger(company, groups.get("Duties & Taxes"), "IGST Output", "IGST-OUT", NormalBalance.CREDIT, LedgerType.TAX);
        seedLedger(company, groups.get("Current Assets"), "CESS Input", "CESS-IN", NormalBalance.DEBIT, LedgerType.TAX);
        seedLedger(company, groups.get("Duties & Taxes"), "CESS Output", "CESS-OUT", NormalBalance.CREDIT, LedgerType.TAX);
    }

    private void seedLedger(
            Company company,
            LedgerGroup group,
            String name,
            String code,
            NormalBalance normalBalance,
            LedgerType ledgerType) {
        if (ledgers.existsByCompanyIdAndNameIgnoreCase(company.getId(), name)) {
            return;
        }
        Ledger ledger = new Ledger();
        ledger.setCompany(company);
        ledger.setLedgerGroup(group);
        ledger.setName(name);
        ledger.setCode(code);
        ledger.setNormalBalance(normalBalance);
        ledger.setLedgerType(ledgerType);
        ledger.setOpeningDebit(BigDecimal.ZERO);
        ledger.setOpeningCredit(BigDecimal.ZERO);
        ledger.setActive(true);
        ledgers.save(ledger);
    }

    private record GroupDefinition(AccountNature nature, String parentName) {
    }
}
