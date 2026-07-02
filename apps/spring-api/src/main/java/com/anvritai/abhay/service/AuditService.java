package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.AuditLog;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    public void record(
            Company company,
            User actor,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> details) {
        AuditLog log = new AuditLog();
        log.setCompany(company);
        log.setActor(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setCorrelationId(MDC.get("correlationId"));
        try {
            log.setDetailsJson(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit details could not be serialized.", exception);
        }
        auditLogs.save(log);
    }
}
