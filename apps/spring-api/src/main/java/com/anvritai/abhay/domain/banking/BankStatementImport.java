package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "bank_statement_imports")
public class BankStatementImport extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id")
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(name = "file_hash", nullable = false, length = 64) private String fileHash;
    @Column(name = "imported_rows", nullable = false) private int importedRows;
    @Column(name = "duplicate_rows", nullable = false) private int duplicateRows;
    @Column(nullable = false, length = 20) private String status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "imported_by")
    private User importedBy;
    public Company getCompany() { return company; }
    public void setCompany(Company value) { company = value; }
    public BankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(BankAccount value) { bankAccount = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { fileName = value; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long value) { fileSize = value; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String value) { fileHash = value; }
    public int getImportedRows() { return importedRows; }
    public void setImportedRows(int value) { importedRows = value; }
    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int value) { duplicateRows = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public User getImportedBy() { return importedBy; }
    public void setImportedBy(User value) { importedBy = value; }
}
