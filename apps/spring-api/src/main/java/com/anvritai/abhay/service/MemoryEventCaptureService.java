package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.*;
import com.anvritai.abhay.domain.memory.*;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.memory.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MemoryEventCaptureService {
    private static final Set<String> PRIVATE_KEYS = Set.of(
            "password", "token", "secret", "storagekey", "filepath", "localpath", "rawfile");
    private final MemoryEventRepository events;
    private final MemoryProfileRepository profiles;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public MemoryEventCaptureService(
            MemoryEventRepository events, MemoryProfileRepository profiles,
            UserRepository users, ObjectMapper objectMapper) {
        this.events = events; this.profiles = profiles; this.users = users; this.objectMapper = objectMapper;
    }

    public MemoryEvent record(Company company, String eventType, String entityType, UUID entityId,
            Map<String, Object> details) {
        return record(company, null, eventType, entityType, entityId, details);
    }

    public MemoryEvent record(Company company, UUID actorId, String eventType, String entityType, UUID entityId,
            Map<String, Object> details) {
        ensureProfile(company);
        Map<String, Object> safe = sanitize(details);
        MemoryEvent event = new MemoryEvent();
        event.setCompany(company); event.setMemoryType(memoryType(eventType, entityType));
        event.setEventType(eventType); event.setEntityType(entityType); event.setEntityId(entityId);
        if (actorId != null) event.setActor(users.findById(actorId).orElse(null));
        event.setSubjectKey(subject(safe, eventType, entityType));
        event.setOccurredAt(Instant.now());
        try { event.setContextJson(objectMapper.writeValueAsString(safe)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Memory event could not be serialized.", e); }
        return events.save(event);
    }

    private void ensureProfile(Company company) {
        if (profiles.findByCompanyId(company.getId()).isEmpty()) {
            MemoryProfile profile = new MemoryProfile(); profile.setCompany(company); profiles.save(profile);
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> source) {
        Map<String, Object> safe = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT).replace("_", "");
            if (!PRIVATE_KEYS.contains(normalized)) safe.put(key, value);
        });
        return safe;
    }

    private MemoryType memoryType(String eventType, String entityType) {
        String value = (eventType + " " + entityType).toUpperCase(Locale.ROOT);
        if (value.contains("DOCUMENT") && value.contains("CORRECT")) return MemoryType.DOCUMENT_CORRECTION_MEMORY;
        if (value.contains("BANK") || value.contains("RECONCIL")) return MemoryType.BANK_RECONCILIATION_MEMORY;
        if (value.contains("INVENTORY") || value.contains("STOCK")) return MemoryType.INVENTORY_MEMORY;
        if (value.contains("GST")) return MemoryType.GST_TREATMENT_MEMORY;
        if (value.contains("INVOICE_PAYMENT")) return MemoryType.CUSTOMER_MEMORY;
        if (value.contains("INVOICE")) return MemoryType.INVOICE_PATTERN_MEMORY;
        if (value.contains("VOUCHER")) return MemoryType.VOUCHER_PATTERN_MEMORY;
        if (value.contains("LEDGER")) return MemoryType.LEDGER_MAPPING_MEMORY;
        return MemoryType.COMPANY_BEHAVIOR_MEMORY;
    }

    private String subject(Map<String, Object> details, String eventType, String entityType) {
        for (String key : List.of("party", "vendor", "customer", "counterparty", "itemName",
                "description", "narration", "field", "voucherType", "gstTreatment")) {
            Object value = details.get(key);
            if (value != null && !value.toString().isBlank()) return trim(value.toString(), 300);
        }
        return trim(eventType + ":" + entityType, 300);
    }
    private String trim(String value, int max) { String clean = value.trim(); return clean.length() <= max ? clean : clean.substring(0, max); }
}
