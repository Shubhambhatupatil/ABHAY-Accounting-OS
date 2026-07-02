package com.anvritai.abhay.domain.accounting;

import com.anvritai.abhay.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "voucher_types")
public class VoucherType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "system_type", nullable = false)
    private boolean systemType;

    @Column(nullable = false)
    private boolean active;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isSystemType() {
        return systemType;
    }

    public boolean isActive() {
        return active;
    }
}
