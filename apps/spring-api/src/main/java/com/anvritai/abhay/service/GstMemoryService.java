package com.anvritai.abhay.service;

import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.accounting.AiMemoryEvent;
import com.anvritai.abhay.repository.accounting.AiMemoryEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GstMemoryService {
    private final AiMemoryEventRepository events;
    private final ObjectMapper objectMapper;
    private final MemoryEventCaptureService memoryEvents;

    public GstMemoryService(AiMemoryEventRepository events, ObjectMapper objectMapper,
            MemoryEventCaptureService memoryEvents) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.memoryEvents = memoryEvents;
    }

    public void record(
            Company company, String eventType, String entityType, UUID entityId,
            String reason, BigDecimal confidence, Map<String, Object> details) {
        AiMemoryEvent event = new AiMemoryEvent();
        event.setCompany(company);
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setProcessingStatus("PENDING");
        Map<String, Object> payload = new LinkedHashMap<>(details);
        payload.put("reason", reason);
        payload.put("confidence", confidence);
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("GST memory event could not be serialized.", exception);
        }
        events.save(event);
        memoryEvents.record(company, eventType, entityType, entityId, details);
    }
}
