package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "document_review_actions")
public class DocumentReviewAction extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_field_id") private DocumentField documentField;
    @Column(nullable = false, length = 30) private String action;
    @Column(name = "old_value", length = 2000) private String oldValue;
    @Column(name = "new_value", length = 2000) private String newValue;
    @Column(length = 1000) private String comment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewed_by") private User reviewedBy;
    public void setCompany(Company v) { company = v; } public void setDocument(Document v) { document = v; }
    public void setDocumentField(DocumentField v) { documentField = v; } public void setAction(String v) { action = v; }
    public void setOldValue(String v) { oldValue = v; } public void setNewValue(String v) { newValue = v; }
    public void setComment(String v) { comment = v; } public void setReviewedBy(User v) { reviewedBy = v; }
}
