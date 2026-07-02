package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cash_flow_snapshots")
public class CashFlowSnapshot extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @Column(name = "snapshot_date", nullable = false) private LocalDate snapshotDate;
    @Column(name = "bank_balance", nullable = false, precision = 19, scale = 2) private BigDecimal bankBalance;
    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 2) private BigDecimal cashBalance;
    @Column(name = "total_liquidity", nullable = false, precision = 19, scale = 2) private BigDecimal totalLiquidity;
    @Column(name = "unreconciled_net", nullable = false, precision = 19, scale = 2) private BigDecimal unreconciledNet;
    public Company getCompany() { return company; }
    public void setCompany(Company v) { company = v; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate v) { snapshotDate = v; }
    public BigDecimal getBankBalance() { return bankBalance; }
    public void setBankBalance(BigDecimal v) { bankBalance = v; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal v) { cashBalance = v; }
    public BigDecimal getTotalLiquidity() { return totalLiquidity; }
    public void setTotalLiquidity(BigDecimal v) { totalLiquidity = v; }
    public BigDecimal getUnreconciledNet() { return unreconciledNet; }
    public void setUnreconciledNet(BigDecimal v) { unreconciledNet = v; }
}
