package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.memory.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class MemoryDtos {
    private MemoryDtos() { }
    public record MemoryEventResponse(UUID id, MemoryType memoryType, String eventType, String entityType,
            UUID entityId, String subjectKey, Map<String,Object> context, Instant occurredAt) { }
    public record MemoryPatternResponse(UUID id, MemoryType memoryType, String subjectKey,
            String suggestionType, String suggestedValue, long occurrenceCount, long successCount,
            long failureCount, BigDecimal confidenceScore, Instant lastSimilarAt, String explanation) { }
    public record MemorySuggestionResponse(UUID id, String suggestionType, String inputKey,
            String suggestedValue, BigDecimal confidenceScore, boolean lowConfidence,
            String reason, long supportingPreviousEvents, Instant lastSimilarTransaction,
            String warning, boolean humanApprovalRequired) { }
    public record MemoryFeedbackRequest(@NotNull UUID suggestionId, @NotNull MemoryFeedbackAction action,
            @Size(max=1000) String correctedValue, @Size(max=1000) String comment) { }
    public record MemoryFeedbackResponse(UUID id, UUID suggestionId, MemoryFeedbackAction action,
            BigDecimal updatedConfidence, String suggestedValue) { }
    public record MemoryDashboardResponse(long totalEvents, long totalPatterns, long totalSuggestions,
            long feedbackCount, long lowConfidencePatterns, BigDecimal averageConfidence,
            Instant lastRebuiltAt, Map<MemoryType,Long> eventsByType) { }
    public record PatternRebuildResponse(long eventsProcessed, long patternsCreatedOrUpdated,
            Instant rebuiltAt) { }
    public record MemoryExportResponse(UUID companyId, Instant generatedAt,
            List<MemoryEventResponse> events, List<MemoryPatternResponse> patterns) { }
    public record MemoryPurgeRequest(@NotBlank String confirmation) { }
    public record MemoryPurgeResponse(long eventsDeleted, long patternsDeleted,
            long suggestionsDeleted, long feedbackDeleted) { }
}
