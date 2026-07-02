package com.anvritai.abhay.domain.gst;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.FinancialYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "gst_liability")
public class GstLiability extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_id", nullable = false)
    private FinancialYear financialYear;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(name = "output_tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal outputTax = BigDecimal.ZERO;
    @Column(name = "input_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal inputCredit = BigDecimal.ZERO;
    @Column(name = "reverse_charge_tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal reverseChargeTax = BigDecimal.ZERO;
    @Column(name = "cess_liability", nullable = false, precision = 19, scale = 2)
    private BigDecimal cessLiability = BigDecimal.ZERO;
    @Column(name = "net_liability", nullable = false, precision = 19, scale = 2)
    private BigDecimal netLiability = BigDecimal.ZERO;

    public void setCompany(Company value) { this.company = value; }
    public void setFinancialYear(FinancialYear value) { this.financialYear = value; }
    public void setPeriodStart(LocalDate value) { this.periodStart = value; }
    public void setPeriodEnd(LocalDate value) { this.periodEnd = value; }
    public void setOutputTax(BigDecimal value) { this.outputTax = value; }
    public void setInputCredit(BigDecimal value) { this.inputCredit = value; }
    public void setReverseChargeTax(BigDecimal value) { this.reverseChargeTax = value; }
    public void setCessLiability(BigDecimal value) { this.cessLiability = value; }
    public void setNetLiability(BigDecimal value) { this.netLiability = value; }
}
