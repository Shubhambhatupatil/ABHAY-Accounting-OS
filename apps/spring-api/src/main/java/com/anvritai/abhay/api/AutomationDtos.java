package com.anvritai.abhay.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class AutomationDtos {
    private AutomationDtos() {}
    public record CreateRuleRequest(
            @NotBlank @Size(max=160) String name,
            @NotBlank @Schema(example="BANK_UNRECONCILED") String ruleType,
            @NotBlank @Schema(example="DAILY") String scheduleType,
            Map<String,Object> parameters,
            @NotBlank @Schema(example="IN_APP") String notificationChannel,
            Boolean active) {}
    public record RuleResponse(UUID id,String name,String ruleType,String scheduleType,Map<String,Object> parameters,
            String notificationChannel,boolean active,Instant nextRunAt,Instant lastRunAt) {}
    public record RunResponse(UUID id,UUID ruleId,String status,int matchedCount,String summary,String correlationId,
            Instant startedAt,Instant completedAt) {}
    public record NotificationResponse(UUID id,String channel,String type,String title,String message,String deliveryStatus,
            Instant readAt,Instant createdAt) {}
    public record TemplateResponse(UUID id,String code,String name,String description,String ruleType,String scheduleType,
            Map<String,Object> parameters) {}
    public record AutomationDashboard(long activeRules,long totalRuns,long unreadNotifications,long failedRuns) {}
}
