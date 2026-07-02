package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "document_tags")
public class DocumentTag extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @Column(nullable = false, length = 80) private String tag;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private User createdBy;
    public void setCompany(Company v) { company = v; } public void setDocument(Document v) { document = v; }
    public void setTag(String v) { tag = v; } public void setCreatedBy(User v) { createdBy = v; }
}
